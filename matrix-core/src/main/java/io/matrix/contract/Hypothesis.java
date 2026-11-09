package io.matrix.contract;

import java.util.List;

/**
 * W51 — a competing explanation the system is holding open.
 *
 * <p><b>A hypothesis is not a belief.</b> A belief is something the system holds; a hypothesis
 * is something the system is <i>testing</i>. It is not established, it is not refuted, and it is
 * not a retrieval hit — it is a candidate explanation that a prediction is made against, so
 * that the outcome can discriminate between it and its competitors.</p>
 *
 * <p><b>Competing hypotheses are named.</b> {@link #competingWith()} lists the identifiers of
 * the other hypotheses this one is being weighed against. A hypothesis with no competitor is
 * not a hypothesis; it is a belief, and it should be recorded as one.</p>
 *
 * <p><b>The evidence that decided it is kept.</b> {@link #resolvedBy()} names the observation
 * that eliminated it, and {@link #resolution()} says how. The W50 lesson applies: a rejected
 * hypothesis stays on record, because "this explanation was wrong, and this is what showed it"
 * is the evidence that the search was real.</p>
 *
 * @param id           the identity of the hypothesis
 * @param proposition  the explanation the hypothesis proposes
 * @param status       the epistemic state of the hypothesis
 * @param confidence   how much the system currently believes it
 * @param competingWith the identifiers of the hypotheses it is weighed against
 * @param rationale    why the system proposes this explanation
 * @param resolvedBy   the observation that decided the hypothesis, or {@code null}
 * @param resolution   how the hypothesis was decided, or {@code null}
 * @param provenance   where the hypothesis came from
 */
public record Hypothesis(
        String id,
        String proposition,
        BeliefStatus status,
        Confidence confidence,
        List<String> competingWith,
        String rationale,
        String resolvedBy,
        String resolution,
        Provenance provenance) {

    /** How a hypothesis was decided. */
    public enum Resolution {
        /** An observation contradicted it. */
        ELIMINATED,
        /** An observation supported it and its competitors were eliminated. */
        CONFIRMED,
        /** Nothing decided it; the search continues. */
        OPEN,
        /** The instrument contradicted itself, so the question stays open. */
        CONTESTED
    }

    public Hypothesis {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("hypothesis id must not be blank");
        }
        if (proposition == null || proposition.isBlank()) {
            throw new IllegalArgumentException("hypothesis proposition must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("hypothesis status must not be null");
        }
        if (confidence == null) {
            confidence = Confidence.UNESTABLISHED;
        }
        competingWith = competingWith == null ? List.of() : List.copyOf(competingWith);
    }

    /** A hypothesis the system is still testing. */
    public static Hypothesis open(String id, String proposition, String rationale,
                                  List<String> competitors, Provenance provenance) {
        return new Hypothesis(id, proposition, BeliefStatus.CANDIDATE, Confidence.UNESTABLISHED,
                competitors, rationale, null, Resolution.OPEN.name(), provenance);
    }

    /** True when the hypothesis is still under test. */
    public boolean open() {
        return Resolution.OPEN.name().equals(resolution);
    }

    /** True when the hypothesis is in competition with at least one other. */
    public boolean competing() {
        return !competingWith.isEmpty();
    }

    @Override
    public String toString() {
        return "Hypothesis[" + id + " " + status + " " + proposition + "]";
    }
}
