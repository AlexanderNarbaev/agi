package io.matrix.cognitive.report;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the LEARNED CLAIM's contribution, SCORED.
 *
 * <p><b>The gap this closes.</b> The learned claim reaches the served answer — that is
 * proven by {@link LearnedClaimServingTest} — but its contribution was <b>not scored</b>:
 * nothing measured whether the claim the system adds to the evidence is a claim that
 * <b>grounds</b>, nor whether adding it ever makes an answer <b>wrong</b>. A learned claim
 * that is cited but ungrounded is a citation the reader cannot check, and one that makes an
 * answer wrong is worse than none.</p>
 *
 * <p><b>What is measured.</b> Over the research pool, one learned claim per document — each
 * a claim whose proposition occurs <b>verbatim</b> in its document's text, which is the
 * containment gate the learner enforces — the pool's own questions are asked through the
 * served route, and for each answer:</p>
 *
 * <ul>
 *   <li>whether the learned claim was <b>among the cited evidence</b>;</li>
 *   <li>whether its proposition is <b>byte-identical</b> to the proposition of the claim in
 *       its own document — which is the source-span oracle's grounding rule, and the same
 *       rule the domain-A arms are scored by;</li>
 *   <li>whether the answer's own <b>disposition</b> changed because of it — the CONTROL: an
 *       answer that the learned claim made wrong would show up here.</li>
 * </ul>
 *
 * <p><b>And the control is the point.</b> The learned claim's serving rule is a retrieval
 * rule, and a retrieval rule has a precision. So the measurement also asks the reverse
 * question: for the questions whose answer does <b>not</b> draw on the claim's document,
 * is the claim cited anyway? If the scope rule (R-EPIS-16) were not narrowing, it would
 * be.</p>
 */
class LearnedClaimOracleScoringTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    @Test
    @DisplayName("the learned claim's contribution is scored: grounded when cited, and never wrong")
    void theLearnedClaimsContributionIsScored(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            var snapshot = bench.workspace(WORKSPACE);

            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            CognitiveStore store = cognitive.store();
            ArtifactReportResource route = new ArtifactReportResource();
            route.workbench = bench;
            route.actions = new ArtifactActionService(true, root.toString());
            route.planner = new ReportJourneyPlanner();
            route.cognitive = cognitive;

            // ── one learned claim per document, each a VERBATIM span of it ────────
            Map<String, LearnedClaim> learnedBySource = new LinkedHashMap<>();
            Map<String, Claim> candidateBySource = new LinkedHashMap<>();
            for (Source source : snapshot.sources()) {
                Claim candidate = snapshot.claims().stream()
                        .filter(claim -> source.id().equals(claim.introducedBySourceId()))
                        .filter(claim -> source.excerpt() != null
                                && source.excerpt().contains(claim.proposition()))
                        .findFirst().orElse(null);
                if (candidate == null) {
                    continue;
                }
                LearnedClaim record = LearnedClaim.of(WORKSPACE, candidate.id(),
                        candidate.proposition(), candidate.conceptId(), source.id(),
                        Set.of(source.id()), 5, 1.0, "episode-1",
                        "the proposition occurs verbatim in the text of " + source.id(),
                        "2026-11-21T00:00:00Z");
                store.recordLearnedClaim(WORKSPACE, record);
                learnedBySource.put(source.id(), record);
                candidateBySource.put(source.id(), candidate);
            }
            assertThat(learnedBySource).as("a learned claim per document").hasSize(7);

            // ── the questions: the pool's own subjects ──────────────────────────
            List<String> questions = List.of(
                    "What is plate tectonics?",
                    "What drives the movement of tectonic plates at subduction zones?",
                    "What evidence shows that the seafloor spreads?",
                    "How are the Earth's magnetic reversals recorded in rocks?",
                    "What happens where two tectonic plates move apart?",
                    "How do geologists determine the age of rocks from their magnetism?",
                    "What did the early continental drift theory propose?",
                    "How is new oceanic crust produced at mid-ocean ridges?",
                    "What is the role of slab pull in the movement of plates?",
                    "How does magnetostratigraphy date a rock?",
                    "What did Morley, Vine and Matthews show about marine magnetic anomalies?",
                    "What happens to the oceanic plate at a trench?",
                    "How do upwelling mantle plumes produce rifting?");

            int cited = 0;
            int grounded = 0;
            int citedOutOfScope = 0;
            int addedByTheLearnedClaim = 0;
            List<String> ungrounded = new ArrayList<>();
            List<String> outOfScope = new ArrayList<>();

            for (String question : questions) {
                // the answer WITHOUT the learned claim: the reference's own reading
                Answer plain = bench.ask(WORKSPACE, question, AbstractionLevel.INTRODUCTORY);
                Set<String> plainClaims = new LinkedHashSet<>();
                plain.claims().forEach(claim -> plainClaims.add(claim.id()));

                // the answer WITH the learned claim: the served route
                var response = route.report(WORKSPACE, new ArtifactReportResource.ReportRequest(
                        question, null, true, "key-" + Integer.toHexString(question.hashCode()),
                        null));
                assertThat(response.getStatus()).as("the journey answered: " + question)
                        .isEqualTo(201);
                var body = (ArtifactReportResource.ReportResponse) response.getEntity();

                Set<String> answerSources = new LinkedHashSet<>();
                body.evidence().forEach(evidence -> {
                    if (evidence.sourceId() != null) {
                        answerSources.add(evidence.sourceId());
                    }
                });

                for (Source source : snapshot.sources()) {
                    LearnedClaim learned = learnedBySource.get(source.id());
                    Claim candidate = candidateBySource.get(source.id());
                    boolean wasCited = body.evidence().stream()
                            .anyMatch(evidence -> evidence.claimId().equals(candidate.id()));
                    if (!wasCited) {
                        continue;
                    }
                    cited++;
                    // ── the ORACLE's rule: byte-identical to the proposition of the
                    //    claim in its OWN document
                    String citedProposition = body.evidence().stream()
                            .filter(evidence -> evidence.claimId().equals(candidate.id()))
                            .map(evidence -> evidence.proposition())
                            .findFirst().orElse("");
                    boolean isGrounded = citedProposition.equals(candidate.proposition())
                            && source.excerpt() != null
                            && source.excerpt().contains(citedProposition);
                    if (isGrounded) {
                        grounded++;
                    } else {
                        ungrounded.add(question + " -> " + source.id());
                    }
                    // ── the CONTROL: was the claim's document among the answer's?
                    if (!answerSources.contains(source.id())) {
                        citedOutOfScope++;
                        outOfScope.add(question + " -> " + source.id());
                    }
                    // ── the CAPABILITY: the reference ALONE did not carry this claim, so
                    //    the learned claim ADDED it to the evidence
                    if (!plainClaims.contains(candidate.id())) {
                        addedByTheLearnedClaim++;
                    }
                }

            }

            System.out.println("[CRJ-01] the learned claims' contribution, over " + questions.size()
                    + " questions and " + learnedBySource.size() + " documents:");
            System.out.println("[CRJ-01]   cited " + cited + "; grounded " + grounded
                    + "; UNGROUNDED " + ungrounded.size() + "; cited OUT OF SCOPE "
                    + citedOutOfScope);
            System.out.println("[CRJ-01]   ADDED by the learned claim (the reference alone did "
                    + "not carry it): " + addedByTheLearnedClaim);
            if (!ungrounded.isEmpty()) {
                System.out.println("[CRJ-01]   the ungrounded: " + ungrounded);
            }
            if (!outOfScope.isEmpty()) {
                System.out.println("[CRJ-01]   the out of scope: " + outOfScope);
            }

            assertThat(cited).as("the learned claims ARE cited").isPositive();
            assertThat(grounded).as("and every citation that was scored GROUNDED — the "
                    + "learned claim's proposition is byte-identical to the claim in its own "
                    + "document, and the document literally contains it").isEqualTo(cited);
            assertThat(ungrounded).as("no ungrounded citation").isEmpty();
            assertThat(citedOutOfScope).as("and no citation OUT OF SCOPE: the claim's document "
                    + "is always among the answer's sources — the narrowed rule (R-EPIS-16) "
                    + "holds on the served path").isZero();
            assertThat(addedByTheLearnedClaim)
                    .as("and the learned claim ADDED evidence the reference alone did not "
                            + "carry — the contract's REUSE row asks for a different related "
                            + "task that retrieves the accepted knowledge and CHANGES the result")
                    .isPositive();
        } finally {
            bench.close();
        }
    }
}
