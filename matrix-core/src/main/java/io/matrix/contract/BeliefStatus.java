package io.matrix.contract;

/**
 * W51 — the seven epistemic states a proposition can be in.
 *
 * <p><b>What this replaces.</b> The repository already carried the distinction, but in two
 * places that did not meet: {@code io.matrix.knowledge.ClaimStatus} for the workbench's claims
 * and a second, looser vocabulary in the cognitive kernel. The W50 defect list named the
 * consequence: "candidate claims could enter answers without adequate status filtering", and
 * "the system could answer where it should abstain". A filter cannot be written correctly when
 * the thing being filtered has two vocabularies.</p>
 *
 * <p><b>The seven states the mission names, and what each one forbids.</b></p>
 * <ol>
 *   <li>{@link #OBSERVED} — <b>an observed fact.</b> It was seen, in this context, at this
 *       time. It is not thereby true beyond the observation, and it carries no claim about
 *       generalisation.</li>
 *   <li>{@link #ASSERTED} — <b>a source assertion.</b> Somebody said it. The record of the
 *       saying is evidence about the assertion's existence, not about its truth.</li>
 *   <li>{@link #CANDIDATE} — <b>a candidate belief.</b> The system proposes it. It is not an
 *       answer, and a retrieval score must never promote it to one.</li>
 *   <li>{@link #SUPPORTED} — <b>a supported belief.</b> At least one piece of evidence supports
 *       it and no known evidence contradicts it.</li>
 *   <li>{@link #REFUTED} — <b>a refuted belief.</b> The evidence contradicts it. The record
 *       stays, because "we were wrong and here is why" is a fact worth keeping.</li>
 *   <li>{@link #UNRESOLVED} — <b>an unknown or unresolved proposition.</b> Either nothing was
 *       looked at, or what was looked at did not decide. These are different facts and the
 *       difference is carried by {@link #evidenceState()}.</li>
 *   <li>{@link #SUPERSEDED} — <b>an outdated or superseded belief.</b> It was once the
 *       accepted account and a later, evidenced revision replaced it. The earlier record stays
 *       readable.</li>
 * </ol>
 *
 * <p><b>Two things this enum refuses to be.</b> It is not a truth value: {@link #CONTESTED} is
 * the state of a proposition whose sources disagree, and neither true nor false is available for
 * it. And it is not a score: a belief with a high confidence and no supporting evidence is
 * still a candidate, because confidence records the system's stance and the status records the
 * evidential state.</p>
 */
public enum BeliefStatus {

    /** It was seen, in this context, at this time. */
    OBSERVED(EvidenceState.NONE, false),

    /** Somebody asserted it; the assertion is on record, its truth is not. */
    ASSERTED(EvidenceState.NONE, false),

    /** The system proposes it and no procedure has checked it. */
    CANDIDATE(EvidenceState.NONE, false),

    /** Evidence supports it and no known evidence contradicts it. */
    SUPPORTED(EvidenceState.SUFFICIENT, true),

    /** Credible sources disagree; the disagreement is preserved. */
    CONTESTED(EvidenceState.CONFLICTING, false),

    /** The evidence contradicts it. */
    REFUTED(EvidenceState.CONTRADICTING, false),

    /** Nothing decided it, or what was looked at did not decide it. */
    UNRESOLVED(EvidenceState.INSUFFICIENT, false),

    /** A later, evidenced revision replaced it. */
    SUPERSEDED(EvidenceState.SUFFICIENT, false);

    /** What the evidence looks like for a proposition in this state. */
    public enum EvidenceState {
        /** No evidence was gathered. */
        NONE,
        /** Evidence was gathered and it supports the proposition. */
        SUFFICIENT,
        /** Evidence was gathered and it contradicts the proposition. */
        CONTRADICTING,
        /** Evidence was gathered and it points both ways. */
        CONFLICTING,
        /** Evidence was gathered and it did not decide. */
        INSUFFICIENT
    }

    private final EvidenceState evidenceState;
    private final boolean established;

    BeliefStatus(EvidenceState evidenceState, boolean established) {
        this.evidenceState = evidenceState;
        this.established = established;
    }

    /** What the evidence looks like for a proposition in this state. */
    public EvidenceState evidenceState() {
        return evidenceState;
    }

    /**
     * True when the proposition may be presented as an established fact.
     *
     * <p>Only {@link #SUPPORTED} may. A contested, candidate, asserted, observed, unresolved or
     * superseded proposition must be presented with its status, or not at all.</p>
     */
    public boolean established() {
        return established;
    }

    /**
     * True when the proposition may be presented as an answer at all.
     *
     * <p>A refuted proposition is not an answer, but it is a legitimate thing to answer ABOUT —
     * "this was believed and here is why it was abandoned" is a fact. A superseded one is the
     * same. What neither may be is presented as the current account.</p>
     */
    public boolean answerable() {
        return this != REFUTED;
    }

    /**
     * The status a wire name names, if it names one at all.
     *
     * <p>An unknown name is not an error to be repaired: it is a name this reader does not
     * know, and the caller decides what to do with that. The method therefore returns an
     * {@link java.util.Optional} rather than throwing.</p>
     *
     * @param wireName the name as it appears on the wire
     * @return the status it names, or empty when it names none
     */
    public static java.util.Optional<BeliefStatus> parse(String wireName) {
        if (wireName == null) {
            return java.util.Optional.empty();
        }
        for (BeliefStatus status : values()) {
            if (status.name().equalsIgnoreCase(wireName.trim())) {
                return java.util.Optional.of(status);
            }
        }
        return java.util.Optional.empty();
    }
}
