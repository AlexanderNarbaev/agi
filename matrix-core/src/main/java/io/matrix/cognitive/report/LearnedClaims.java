package io.matrix.cognitive.report;

import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeWorkbench;

import java.util.ArrayList;
import java.util.List;

/**
 * The serving side of a learned claim: the evidence it adds to an answer.
 *
 * <p><b>What this does.</b> The retrieval decides which claims a question's <b>words</b> point
 * at. A learned claim is a claim the system has already <b>validated</b> — its proposition was
 * checked to be a verbatim span of its source's text, and the reference was checked to carry
 * it on held-out questions it was never proposed from. So it belongs in the evidence of a
 * question about <b>its concept</b>, even when the words did not point at it.</p>
 *
 * <p><b>Why the concept and not the words.</b> A rule that matched on the question's tokens
 * would be the retriever's own rule, and the CRJ-01 protocol names that confound exactly:
 * "the gain is retrieval by overlap" (A3, REJECTED for transfer). The concept is the
 * workspace's own semantic anchor — the thing the question was resolved to be about — so a
 * learned claim applies to a <b>different</b> question about the same thing, which is what
 * makes it learned competence rather than a cache.</p>
 *
 * <p><b>What it may not do.</b> It may not add a claim whose source is no longer in the
 * workspace: a learned claim is a fact about <b>this</b> workspace, and a claim whose
 * document is gone is a claim about nothing. And it may not add a claim the answer already
 * carries.</p>
 *
 * <p><b>And the applicability is not the concept ALONE (R-EPIS-16).</b> A concept match
 * alone is too coarse: it gave a question about a concept <b>every</b> learned claim of that
 * concept, from <b>any</b> document — including a claim validated against a document the
 * question's evidence never touches, and therefore about a different part of the concept
 * altogether. The rule now requires the concept match <b>and</b> a common document: the
 * answer's evidence must draw on one of the sources the claim was <b>validated against</b>
 * (its own source, and the sources observed with it).</p>
 *
 * <p><b>Why that is the right scope and not a weaker one.</b> The learner's agreement gate
 * established that the reference carried the claim on <b>every</b> held-out question, and
 * those questions' evidence came from exactly those sources. So the claim's validated scope
 * IS "what the workspace says about this concept, drawn from these documents", and the
 * narrowed rule applies the claim exactly where that validation speaks. It can only
 * <b>extend</b> the coverage of a body of evidence the answer already rests on; it can never
 * import a document's subject into a question that never touched it.</p>
 *
 * <p><b>And it fails CLOSED.</b> An answer with no sources, or a claim with no support, has
 * no common document — so nothing is added. The rule may not be defeated by missing
 * data.</p>
 */
public final class LearnedClaims {

    private LearnedClaims() {
    }

    /**
     * The learned claims that apply to an answer.
     *
     * @param answer   the answer the question resolved to
     * @param learned  the workspace's learned claims
     * @return the applicable ones, in the order they were given
     */
    public static List<LearnedClaim> applicableTo(Answer answer, List<LearnedClaim> learned) {
        if (answer == null || learned == null || learned.isEmpty()) {
            return List.of();
        }
        List<String> conceptIds = new ArrayList<>();
        for (Concept concept : answer.concepts()) {
            conceptIds.add(concept.id());
        }
        if (conceptIds.isEmpty()) {
            return List.of();
        }
        List<String> sourceIds = new ArrayList<>();
        for (io.matrix.knowledge.Source source : answer.sources()) {
            sourceIds.add(source.id());
        }
        return learned.stream()
                .filter(claim -> conceptIds.contains(claim.conceptId()))
                .filter(claim -> sharesADocument(claim, sourceIds))
                .toList();
    }

    /**
     * Whether the answer's evidence and the claim's validated support draw on a COMMON
     * document.
     *
     * @param claim         the learned claim
     * @param answerSources the sources the answer's evidence comes from
     * @return true when they share at least one document
     */
    private static boolean sharesADocument(LearnedClaim claim, List<String> answerSources) {
        if (claim.support().isEmpty() || answerSources.isEmpty()) {
            // nothing to compare, so nothing is shown to be in scope: the rule fails closed
            return false;
        }
        for (String sourceId : claim.support()) {
            if (answerSources.contains(sourceId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The claims the learned claims add to an answer's evidence.
     *
     * @param workbench    the workspace the claims are read from
     * @param workspaceId  the workspace
     * @param answer       the answer
     * @param learned      the workspace's learned claims
     * @return the claims to add; empty when none applies
     */
    public static List<Claim> additions(KnowledgeWorkbench workbench, String workspaceId,
                                        Answer answer, List<LearnedClaim> learned) {
        return workbench == null ? List.of() : additions(
                io.matrix.knowledge.SourceRevisionView.project(workbench.workspace(workspaceId), null),
                answer, learned);
    }

    static List<Claim> additions(io.matrix.knowledge.KnowledgeSnapshot snapshot,
            Answer answer, List<LearnedClaim> learned) {
        List<LearnedClaim> applicable = applicableTo(answer, learned);
        if (applicable.isEmpty()) {
            return List.of();
        }
        List<String> sourceIds = snapshot.sources().stream().map(s -> s.id()).toList();
        List<Claim> additions = new ArrayList<>();
        for (LearnedClaim claim : applicable) {
            // the document must still be there: a learned claim is a fact about this
            // workspace, and a claim whose source is gone is a claim about nothing
            if (!sourceIds.contains(claim.sourceId())) {
                continue;
            }
            for (Claim workspaceClaim : snapshot.claims()) {
                if (workspaceClaim.id().equals(claim.claimId())) {
                    additions.add(workspaceClaim);
                    break;
                }
            }
        }
        return additions;
    }

    /**
     * The answer with the learned claims' evidence in it.
     *
     * @param workbench   the workspace the claims are read from
     * @param workspaceId the workspace
     * @param answer      the answer
     * @param learned     the workspace's learned claims
     * @return the answer, with the applicable learned claims' claims in its evidence
     */
    public static Answer apply(KnowledgeWorkbench workbench, String workspaceId, Answer answer,
                                List<LearnedClaim> learned) {
        return answer.withAdditionalClaims(additions(workbench, workspaceId, answer, learned));
    }

    /**
     * Whether the answer carries a learned claim.
     *
     * @param answer  the answer
     * @param learned the workspace's learned claims
     * @return whether a learned claim contributed
     */
    public static boolean contributed(Answer answer, List<LearnedClaim> learned) {
        if (answer == null || learned == null || answer.claims().isEmpty()) {
            return false;
        }
        List<String> claimIds = learned.stream().map(LearnedClaim::claimId).toList();
        return answer.claims().stream().anyMatch(claim -> claimIds.contains(claim.id()));
    }
}
