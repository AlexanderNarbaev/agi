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
                KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor) {
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
                KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor) {
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
    public abstract Decision judge(KnowledgeRetriever.Relevance workspace,
            KnowledgeRetriever.Relevance support, double supportBestCoverage, double floor);

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
