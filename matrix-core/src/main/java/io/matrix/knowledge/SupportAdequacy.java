package io.matrix.knowledge;

import java.util.List;

/**
 * CRJ-01 / ADAPTIVE-COMPETENCE — whether a support set can answer a question.
 *
 * <p><b>What this decides.</b> A procedure of the knowledge-report family answers a question
 * from its validated <b>support set</b>: the workspace is read as the sources the validation
 * covered, and everything after the reading is the asking path's own. Whether the procedure
 * may serve a question is therefore a question about the EVIDENCE, and the H2 milestone
 * measured what happens when it is not asked: the applicability domain of the promoted
 * procedures was the <b>presence</b> of the support set — a statement about the workspace,
 * not about the request — so a procedure was offered for a question its support could not
 * answer, and the served answer was the restricted reading. On the H2 controls the restricted
 * reading cited <b>one</b> claim where the reference cited <b>three</b> and said
 * {@code ANSWERED}, and on the other it cited <b>none</b> and abstained where the reference
 * answered. Both are degradations of the served answer, and neither is visible to a check
 * that only asks whether the answer abstained.</p>
 *
 * <p><b>What the criterion is.</b> The support set is <b>adequate</b> for a question when it
 * holds <b>the claim the workspace's own reading would cite first</b> — the anchor of the
 * answer. The comparison is made on the retrieval's own numbers (the coverage a claim
 * reaches), and the workspace's numbers are the ones the reference reading would use,
 * because the support set decides <i>what may be cited</i> and the workspace's statistics
 * decide <i>how relevant a claim is</i>.</p>
 *
 * <p><b>Why the anchor and not a comparison of the numbers.</b> A restricted reading's best
 * claim may reach <b>exactly</b> the workspace's best coverage and still be a
 * <b>different</b> claim — and then the restricted answer is anchored on other evidence
 * while looking equally relevant. That is the degradation the H2 milestone measured: on the
 * out-of-support controls the restricted reading cited <b>one</b> claim where the reference
 * cited <b>three</b>, and the answer still said {@code ANSWERED}. A tie in the number is
 * not a tie in the evidence, so the criterion is the evidence.</p>
 *
 * <p><b>Why this is not a lexical rule.</b> Nothing here knows any word, any concept or any
 * domain. The verdict is a comparison of two numbers the retrieval itself produced, over the
 * claims the retrieval itself reached. A question that names a term no claim carries reaches
 * no claim on either side, and both sides are empty — the verdict then says the support set
 * carries nothing, which is what it means.</p>
 *
 * <p><b>What this does NOT decide.</b> It does not decide whether the question is answerable
 * at all: an empty workspace and an empty support set agree, and the verdict then says the
 * support set carries nothing. It does not decide whether the procedure should be preferred
 * on cost — that is the selector's work. It does not decide whether the support set is
 * <i>present</i>: that is a statement about the workspace, and it is checked separately.</p>
 *
 * @param applicable        whether the support set carries evidence as relevant as the
 *                          workspace's own
 * @param reason            the sentence a reader can act on, naming the evidence and the
 *                          verdict
 * @param bestClaimId       the claim the unrestricted reading would cite first, or null when
 *                          no claim reaches the coverage floor
 * @param bestCoverage      the coverage of that claim
 * @param supportBestCoverage the best coverage the support set carries for the question
 * @param supportClaimIds   the claims the support set holds
 * @param supportCitedIds   the claims the restricted reading would cite
 */
public record SupportAdequacy(
        boolean applicable,
        String reason,
        String bestClaimId,
        double bestCoverage,
        double supportBestCoverage,
        List<String> supportClaimIds,
        List<String> supportCitedIds) {

    /**
     * The tolerance of the coverage comparison.
     *
     * <p>Two readings of the same question compute the same coverage for the same claim, so
     * the comparison is exact in principle; the tolerance is there for the case where the two
     * numbers were computed by two different instances and the last bit differs. It is far
     * below any difference that could change a ranking.</p>
     */
    public static final double COVERAGE_TOLERANCE = 1e-9;

    public SupportAdequacy {
        supportClaimIds = List.copyOf(supportClaimIds == null ? List.of() : supportClaimIds);
        supportCitedIds = List.copyOf(supportCitedIds == null ? List.of() : supportCitedIds);
    }

    /**
     * A coverage, rounded for a reader.
     *
     * @param coverage the coverage
     * @return the coverage, to three decimals
     */
    public static String round(double coverage) {
        return String.format(java.util.Locale.ROOT, "%.3f", coverage);
    }

    /**
     * The verdict for a support set that holds nothing for the question.
     *
     * @param reason the sentence a reader can act on
     * @return the verdict
     */
    public static SupportAdequacy none(String reason) {
        return new SupportAdequacy(false, reason, null, 0.0, 0.0, List.of(), List.of());
    }
}
