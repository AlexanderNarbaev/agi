package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CRJ-01 / COMPETENCE-REACH — how a competence's scope is judged adequate for a question.
 *
 * <p><b>What this decides.</b> A competence of the knowledge-report family answers a question
 * from its <b>scope</b> (the set of sources it may cite). Whether it may serve a question is
 * therefore a question about the EVIDENCE, and the question this milestone answers is: <b>which
 * evidence rule?</b> The ADAPTIVE-COMPETENCE milestone put one rule in place — the
 * <b>anchor</b> — and measured that it works at parity with the stateless reference. It did not
 * compare the rule with a competitor, and §9 of that milestone named the comparison as the open
 * decision. This enum holds the two rules that comparison is drawn between.</p>
 *
 * <p><b>Why there are exactly two.</b> One is the incumbent — the rule the served path already
 * applies — and one is an <b>alternative that is justified by a defect the incumbent has</b>:
 * the anchor asks whether the scope holds <b>the claim the workspace's own reading would cite
 * first</b>, so a question whose answer needs a <b>second</b> claim can be admitted on the
 * first one alone. The alternative asks whether the scope holds <b>every claim the workspace's
 * own reading would cite</b>. It is not a straw man: it is the criterion under which the
 * restricted reading and the unrestricted reading are guaranteed to stand on the same evidence,
 * which is the property the restricted reading was validated for.</p>
 *
 * <p><b>What both rules are computed from.</b> The two readings' OWN numbers: the same
 * retriever, the same statistics view, the same question, the same coverage floor and the same
 * weakened bindings. Nothing here knows a word, a concept or a domain; a rule compares two sets
 * of claim identifiers and two doubles that the retrieval itself produced.</p>
 *
 * <p><b>What the rules do NOT decide.</b> They do not decide whether the scope is
 * <i>present</i> — that is a statement about the workspace and it is checked separately. They do
 * not decide whether the question is answerable at all. And they do not decide how the scope
 * came to be — that is the <b>scope strategy</b>, and it is a different question, measured
 * separately.</p>
 *
 * <p><b>Why the alternative is not simply "better".</b> Requiring every cited claim makes the
 * rule STRICTLY STRONGER than the anchor: every scope the alternative admits, the anchor admits
 * too (the first cited claim is one of them), and the alternative admits fewer. A stronger rule
 * cannot admit a degraded answer, but it can refuse an answer the scope was able to give — and
 * the refusal is not free, because the caller falls back to a path that reads the whole
 * workspace. Which of the two losses is larger is a <b>measurement</b>, and this milestone
 * measures it rather than deciding it.</p>
 */
public enum ScopeCriterion {

    /**
     * The incumbent rule: the scope must hold <b>the claim the workspace's own reading would
     * cite first</b>.
     *
     * <p><b>Why the claim and not the number.</b> A scope's best claim may reach exactly the
     * workspace's best coverage and still be a <b>different</b> claim — and then the restricted
     * answer is anchored on other evidence while looking equally relevant. The ADAPTIVE milestone
     * measured that: with the tie rule, the out-of-support control was still served by the
     * procedure. A tie in the number is not a tie in the evidence, so the criterion is the
     * evidence.</p>
     */
    ANCHOR {
        @Override
        public Decision judge(KnowledgeRetriever.Relevance workspace,
                KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor,
                TaskPrerequisites prerequisites, Set<String> scopeSourceIds) {
            String best = workspace.bestClaimId();
            if (best == null) {
                return Decision.refused("the workspace's own reading of this question reaches no "
                        + "claim that may be cited, so no support set can be shown to carry its "
                        + "evidence", List.of());
            }
            if (support.relevantClaimIds().contains(best)) {
                return Decision.allowed("the support set carries the workspace's own evidence for this "
                        + "question: the workspace's reading would cite " + best
                        + " (coverage " + SupportAdequacy.round(workspace.bestCoverage())
                        + ") and the support set holds that claim"
                        + (supportBestCoverage > 0.0
                                ? " and reaches " + SupportAdequacy.round(supportBestCoverage)
                                : ""), List.of());
            }
            return Decision.refused("the support set does not carry the workspace's own evidence for "
                    + "this question: the workspace's reading would cite " + best
                    + " (coverage " + SupportAdequacy.round(workspace.bestCoverage())
                    + ") and the support set's best evidence reaches "
                    + SupportAdequacy.round(supportBestCoverage)
                    + " (the coverage floor is " + SupportAdequacy.round(floor) + ")",
                    List.of(best));
        }
    },

    /**
     * The alternative rule: the scope must hold <b>every claim the workspace's own reading
     * would cite</b> for this question.
     *
     * <p><b>What it adds.</b> The anchor can be satisfied by the first citation while a second
     * necessary claim is missing — and then the restricted answer is INCOMPLETE while the rule
     * says the scope is adequate. This rule closes that gap by construction: the scope's
     * citable claims must cover the workspace's citable claims, so the restricted reading
     * stands on the same evidence the reference would have stood on.</p>
     *
     * <p><b>What it costs.</b> It refuses a scope that could have answered with the claim it
     * did hold, and every refusal falls back to the reference — the path that reads the whole
     * workspace. The refusal is therefore not free, and the milestone measures it with a
     * denominator of its own rather than folding it into the admission count.</p>
     *
     * <p><b>What it does about a workspace that reaches nothing.</b> It refuses, for the same
     * reason the anchor refuses: a workspace whose own reading reaches no claim is answered by
     * the concept bridge, and the bridge reaches a concept's claims regardless of their own
     * coverage — so no scope can be shown to carry that evidence.</p>
     */
    CLAIM_SUFFICIENCY {
        @Override
        public Decision judge(KnowledgeRetriever.Relevance workspace,
                KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor,
                TaskPrerequisites prerequisites, Set<String> scopeSourceIds) {
            List<String> cited = workspace.relevantClaimIds();
            if (cited.isEmpty()) {
                return Decision.refused("the workspace's own reading of this question reaches no "
                        + "claim that may be cited, so no support set can be shown to carry its "
                        + "evidence", List.of());
            }
            Set<String> carried = new LinkedHashSet<>(support.relevantClaimIds());
            List<String> missing = new ArrayList<>();
            for (String claimId : cited) {
                if (!carried.contains(claimId)) {
                    missing.add(claimId);
                }
            }
            if (missing.isEmpty()) {
                return Decision.allowed("the support set carries every claim the workspace's reading "
                        + "would cite for this question: " + cited.size() + " claim(s), the "
                        + "first of them " + cited.get(0) + " (coverage "
                        + SupportAdequacy.round(workspace.bestCoverage()) + ")", List.of());
            }
            return Decision.refused("the support set does not carry every claim the workspace's reading "
                    + "would cite for this question: it is missing " + missing.size() + " of "
                    + cited.size() + " claim(s) (" + String.join(", ", missing) + ")", missing);
        }
    },

    /**
     * The third rule: the scope must carry <b>a sufficient set of the task's necessary
     * evidence</b>.
     *
     * <p><b>What it adds to the second rule.</b> The claim-sufficiency rule demands that the
     * scope carry <b>every</b> claim the workspace's reading would cite. That is strictly
     * stronger than it needs to be: a claim the reading cites may be <b>redundant</b> — another
     * source states the same thing — and a scope that carries the other source already carries
     * what the task needs. This rule asks the narrower question: which evidence does the task
     * <b>not do without</b>, and does the scope carry a set that does the job?</p>
     *
     * <p><b>Where the necessary evidence comes from.</b> From the workspace itself, by
     * <b>leave-one-source-out ablation</b> of the workspace's own reading of the task: a source
     * is necessary when the reading cannot reproduce the answer without it. Nothing here knows a
     * word, a concept, a domain or an oracle label — the rule compares a derived set of claims
     * with the claims a scope carries.</p>
     *
     * <p><b>Why the set is not stored in the competence.</b> A prerequisite belongs to a
     * <b>task</b>, not to a competence: the same competence serves many tasks, and what each
     * task needs is a fact about the task and the workspace as it is now. A rule that used a
     * stored set would answer a different question — "what did this competence need when it was
     * promoted" — and would keep answering it after the workspace moved.</p>
     *
     * <p><b>What it costs.</b> The derivation reads the workspace's own reading once per
     * candidate source. The refusal it produces falls back to the reference path, exactly as
     * the second rule's refusal does.</p>
     */
    NECESSARY_PREREQUISITE {
        @Override
        public Decision judge(KnowledgeRetriever.Relevance workspace,
                KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor,
                TaskPrerequisites prerequisites, Set<String> scopeSourceIds) {
            if (prerequisites == null || prerequisites.necessary().isEmpty()) {
                // NO DERIVATION — the rule cannot decide, and it says so instead of silently
                // answering for another rule. The incumbent's verdict is the only one
                // available, so it stands, and the reason says which verdict it is.
                //
                // This is also the shape of the RED reproduction: with the derivation switched
                // off, this rule refuses nothing, and the missing prerequisite is not visible.
                Decision incumbent = ANCHOR.judge(workspace, support, supportBestCoverage, floor,
                        null, scopeSourceIds);
                return new Decision(incumbent.applicable(),
                        "the task's necessary evidence was NOT derived, so this rule could not "
                                + "decide and the incumbent's verdict stands: "
                                + incumbent.reason(), incumbent.missingClaimIds());
            }
            PrerequisiteAssessment assessment = PrerequisiteAssessment.of(
                    prerequisites, scopeSourceIds);
            if (assessment.satisfied()) {
                return Decision.allowed("the support set carries a sufficient set of the evidence "
                        + "the task needs: " + assessment.satisfiedBy().size() + " requirement(s) "
                        + "of the " + prerequisites.necessary().size() + " the workspace's own "
                        + "reading cannot do without", List.of());
            }
            List<String> missing = assessment.missing().stream()
                    .map(EvidenceRequirement::claimId).toList();
            return Decision.refused("the support set does not carry a sufficient set of the "
                    + "evidence the task needs: it is missing " + missing.size() + " requirement(s) "
                    + "of the " + prerequisites.necessary().size() + " the workspace's own reading "
                    + "cannot do without (" + String.join(", ", missing) + ")", missing);
        }
    };

    /**
     * The decision, with the evidence it was made from.
     *
     * @param applicable     whether the scope may serve the question
     * @param reason         the sentence a reader can act on
     * @param missingClaimIds the claims the workspace's reading would cite and the scope does
     *                        not carry
     */
    public record Decision(boolean applicable, String reason, List<String> missingClaimIds) {

        public Decision {
            missingClaimIds = List.copyOf(missingClaimIds == null ? List.of() : missingClaimIds);
        }

        /** A decision that admits the scope. */
        static Decision allowed(String reason, List<String> missing) {
            return new Decision(true, reason, missing);
        }

        /** A decision that refuses the scope. */
        static Decision refused(String reason, List<String> missing) {
            return new Decision(false, reason, missing);
        }
    }

    /**
     * Judges a scope against the workspace's own reading of a question.
     *
     * @param workspace          the workspace's own relevance for the question
     * @param support            the scope's relevance for the same question, over the same
     *                           statistics and the same bindings
     * @param supportBestCoverage the best coverage the scope carries for the question
     * @param floor              the coverage floor the readings applied
     * @return the decision
     */
    public Decision judge(KnowledgeRetriever.Relevance workspace,
            KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor) {
        return judge(workspace, support, supportBestCoverage, floor, null, Set.of());
    }

    /**
     * Judges a scope against the workspace's own reading of a question, with the task's derived
     * prerequisites and the scope's own holdings in hand.
     *
     * <p>The prerequisites are <b>null</b> for the rules that do not use them, and the rule that
     * does use them says so in its reason when they are absent — a rule that silently fell back
     * to another rule would report a verdict it did not make.</p>
     *
     * @param workspace          the workspace's own relevance for the question
     * @param support            the scope's relevance for the same question, over the same
     *                           statistics and the same bindings
     * @param supportBestCoverage the best coverage the scope carries for the question
     * @param floor              the coverage floor the readings applied
     * @param prerequisites      the task's necessary evidence, or null
     * @param scopeSourceIds     the sources the scope holds — what the scope CARRIES
     * @return the decision
     */
    public abstract Decision judge(KnowledgeRetriever.Relevance workspace,
            KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor,
            TaskPrerequisites prerequisites, Set<String> scopeSourceIds);

    /**
     * Whether the rule needs the task's derived prerequisites.
     *
     * <p><b>Why this is asked before the work is done.</b> The derivation reads the workspace's
     * own reading once per candidate source. The incumbent rule and the claim-sufficiency rule
     * do not need it, so it is not paid for when they are the rule that was asked for — the
     * served path's behaviour and its cost are exactly what they were.</p>
     *
     * @return whether the rule needs the prerequisites
     */
    public boolean needsPrerequisites() {
        return this == NECESSARY_PREREQUISITE;
    }

    /**
     * The criterion a name stands for.
     *
     * <p>An unknown or absent name is the <b>incumbent</b>: the served path's behaviour must not
     * change because a record was written by an older writer that knew nothing of this rule.</p>
     *
     * @param name the name, or null
     * @return the criterion
     */
    public static ScopeCriterion of(String name) {
        if (name == null || name.isBlank()) {
            return ANCHOR;
        }
        for (ScopeCriterion criterion : values()) {
            if (criterion.name().equalsIgnoreCase(name.strip())) {
                return criterion;
            }
        }
        return ANCHOR;
    }
}
