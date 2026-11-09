package io.matrix.knowledge;

import java.util.EnumSet;
import java.util.Set;

/**
 * W51 §6 — the rules that decide what a claim may be cited for, and what an
 * answer may rest on.
 *
 * <p><b>Why this class exists.</b> The W50 evaluation measured three defects
 * that are the same defect seen from three sides: a claim the workspace had
 * never established was cited as evidence, a claim the workspace had
 * withdrawn was cited as evidence, and the answer was given where the
 * workspace should have refused. All three were decided by conditions that
 * were written where they were used — {@code if (status == ...)} inside a
 * scoring loop, a marker list inside a revision rule — so there was no one
 * place where the rule could be read, changed or tested.
 *
 * <p>This class is that place. It holds:
 *
 * <ul>
 *   <li><b>{@link #CITABLE_STATUSES}</b> — the statuses whose claims may be
 *       cited as evidence for an answer;</li>
 *   <li><b>{@link #ESTABLISHED_STATUSES}</b> — the statuses that carry a
 *       claim's weight in the numeric support score;</li>
 *   <li><b>{@link #WITHDRAWN_STATUSES}</b> — the statuses that record a
 *       resolution and are therefore never evidence;</li>
 *   <li><b>{@link #MINIMUM_QUANTITY_EVIDENCE}</b> — the abstention boundary
 *       for a question that asks for a number.</li>
 * </ul>
 *
 * <p><b>What a "citable" claim is.</b> A claim is evidence when the
 * workspace has established it: {@link ClaimStatus#SUPPORTED} (at least one
 * source supports it), {@link ClaimStatus#VERIFIED} (the evidence met the
 * acceptance criteria of the domain), or {@link ClaimStatus#CONTESTED}
 * (credible sources disagree — the claim is real, and it is contested, and
 * an answer that cites it says so).
 *
 * <p><b>What a citable claim is NOT.</b> A {@link ClaimStatus#CANDIDATE} is
 * an extraction that no procedure has validated; a
 * {@link ClaimStatus#HYPOTHESIS} is an assertion on record with nothing
 * behind it; a {@link ClaimStatus#DRAFT} is the system's own proposal; an
 * {@link ClaimStatus#UNKNOWN} or {@link ClaimStatus#INSUFFICIENT_EVIDENCE}
 * says that the evidence was sought and did not decide. None of them is
 * evidence, and an answer that cites one presents the system's reading of a
 * text as a fact.
 *
 * <p><b>What a withdrawn claim is.</b> {@link ClaimStatus#SUPERSEDED} and
 * {@link ClaimStatus#REFUTED} record a resolution: the workspace withdrew
 * the claim or the evidence contradicts it. The record is kept — the history
 * must stay inspectable — but it is never cited as evidence, and it is not
 * part of the model a new claim may revise.
 *
 * <p><b>Determinism.</b> There is no clock, no randomness and no I/O in this
 * class. Every decision it makes is a function of the record it is given.
 */
public final class EpistemicPolicy {

    /**
     * The statuses whose claims may be cited as evidence for an answer.
     *
     * <p>The set is named, not written inline at each use, because the rule
     * has to be readable in one place: the W50 defect was that the rule was
     * implicit, and an implicit rule cannot be tightened without finding
     * every place it was written.
     */
    public static final Set<ClaimStatus> CITABLE_STATUSES = EnumSet.of(
            ClaimStatus.SUPPORTED,
            ClaimStatus.VERIFIED,
            ClaimStatus.CONTESTED);

    /**
     * The statuses that carry a claim's weight in the numeric support score.
     *
     * <p>A contested claim is citable but it does not carry weight: the
     * sources disagree, and a number computed from it would claim a
     * precision the workspace does not have.
     */
    public static final Set<ClaimStatus> ESTABLISHED_STATUSES = EnumSet.of(
            ClaimStatus.SUPPORTED,
            ClaimStatus.VERIFIED);

    /**
     * The statuses that record a resolution and are therefore never evidence.
     */
    public static final Set<ClaimStatus> WITHDRAWN_STATUSES = EnumSet.of(
            ClaimStatus.SUPERSEDED,
            ClaimStatus.REFUTED);

    /**
     * The number of cited claims that must state a number when the question
     * asks for one.
     *
     * <p><b>Why the boundary is here and not in the coverage floor.</b> The
     * W50 evaluation measured that the coverage floor cannot separate the
     * two families: the unanswerable case "How many tonnes of basalt are
     * produced each year by the Mid-Atlantic Ridge?" scored 0.288 against a
     * floor of 0.25, while the answerable case "How does subduction at a
     * convergent boundary produce deep earthquakes and volcanoes?" scored
     * 0.286 and had to be answered. The floor is a relevance floor; the
     * boundary that separates "the workspace does not hold this" from "the
     * workspace holds something adjacent" is a question about WHAT is
     * asked for.
     *
     * <p>A question that asks for a number needs a claim that states a
     * number. A claim that describes the subject without stating the
     * quantity asked for is not an answer to it, and answering it anyway is
     * how a workspace ends up confident about a tonnage it never read.
     *
     * <p>One is the minimum: a single claim that states the demanded
     * quantity is enough to answer, and no answer may be given without one.
     */
    public static final int MINIMUM_QUANTITY_EVIDENCE = 1;

    private EpistemicPolicy() {
    }

    /**
     * Whether a claim of this status may be cited as evidence.
     *
     * @param status the status of the claim
     * @return whether the claim may be cited
     */
    public static boolean isCitable(ClaimStatus status) {
        return status != null && CITABLE_STATUSES.contains(status);
    }

    /**
     * Whether a claim of this status carries weight in the support score.
     *
     * @param status the status of the claim
     * @return whether the claim carries weight
     */
    public static boolean isEstablished(ClaimStatus status) {
        return status != null && ESTABLISHED_STATUSES.contains(status);
    }

    /**
     * Whether a claim of this status records a resolution and is therefore
     * never evidence.
     *
     * @param status the status of the claim
     * @return whether the claim is withdrawn
     */
    public static boolean isWithdrawn(ClaimStatus status) {
        return status != null && WITHDRAWN_STATUSES.contains(status);
    }

    /**
     * Whether a claim of this status is part of the model a new claim may
     * revise.
     *
     * <p>The rule is the citability rule: a claim that may not be cited as
     * evidence may not be superseded either, because superseding it would
     * replace something the answer never rested on, and the "before" of the
     * revision would be a claim nobody ever believed was established.
     *
     * @param status the status of the claim
     * @return whether the claim is in force
     */
    public static boolean isInForce(ClaimStatus status) {
        return isCitable(status);
    }

    /**
     * Whether a claim carries a numeric confidence that was authored or
     * measured.
     *
     * <p>The W50 evaluation measured that every answer of the demonstrator
     * carried a support value of {@code 0.0}, because an imported claim
     * carries no authored confidence — and a {@code 0.0} was
     * indistinguishable from "never authored". This predicate draws that
     * line: a claim whose confidence is zero has no numeric support behind
     * it, and the answer says so rather than presenting a zero as a
     * measurement.
     *
     * @param claim the claim
     * @return whether the claim carries a numeric support
     */
    public static boolean carriesNumericSupport(Claim claim) {
        return claim != null && claim.confidence() > 0.0;
    }
}
