package io.matrix.contract;

import java.util.List;

/**
 * W51 — how much the system believes something, and what that belief rests on.
 *
 * <p><b>What this replaces.</b> Before W51 the repository carried confidence as a bare
 * {@code double} on several unrelated records: {@code Claim.confidence}, the {@code support}
 * value of an answer, the score of a retrieval hit. Three consequences followed. A retrieval
 * score of {@code 0.83} and a confidence of {@code 0.83} meant different things and looked
 * identical; a claim could hold {@code 0.0} and still be presented as an answer, because
 * {@code 0.0} was indistinguishable from "never authored"; and nothing recorded WHY the number
 * was what it was, so a confidence could not be re-examined when the evidence changed.</p>
 *
 * <p><b>The separation this record enforces.</b> Confidence is about the system's epistemic
 * stance towards a proposition. It is NOT association strength, NOT frequency, NOT causal
 * relevance and NOT a similarity score. Those are different quantities and they are carried by
 * different records: a relation's association strength belongs to the relation, an observation's
 * frequency belongs to the observation, and a retrieval score belongs to the retrieval
 * decision. A similarity score must never be copied into a confidence field, because a
 * high similarity between a question and a candidate claim is a statement about the QUESTION,
 * not about the claim.</p>
 *
 * <p><b>The basis is part of the confidence.</b> {@link Basis#DECLARED} means a human or a
 * policy asserted the number. {@link Basis#MEASURED} means the system computed it from
 * outcomes it recorded. {@link Basis#UNESTABLISHED} means there is no basis at all, which is
 * the honest state of an imported claim that no procedure has ever examined — and the W50
 * demonstrator's answers carried exactly that, with a support value of {@code 0.0}.</p>
 *
 * <p><b>Determinism.</b> This record has no clock and no randomness. The recorded counts and
 * the value are what the system knew at the moment the record was made.</p>
 *
 * @param value               the confidence in {@code [0,1]}; zero means "no confidence", NOT
 *                            "unknown" — the basis says which of the two it is
 * @param basis               where the number came from
 * @param supportingCount     how many pieces of evidence support the proposition
 * @param contradictingCount  how many pieces of evidence contradict it
 * @param method              the procedure that produced the value, or {@code null} when the
 *                            value was declared rather than computed
 * @param reasons             the recorded reasons the value is what it is
 */
public record Confidence(
        double value,
        Basis basis,
        int supportingCount,
        int contradictingCount,
        String method,
        List<String> reasons) {

    /** Where a confidence value came from. */
    public enum Basis {
        /** A human, a policy or an import asserted the value. */
        DECLARED,
        /** The system computed the value from recorded outcomes. */
        MEASURED,
        /** There is no basis for the value: it is a placeholder, and says so. */
        UNESTABLISHED
    }

    /** The confidence of a proposition nobody has examined. */
    public static final Confidence UNESTABLISHED =
            new Confidence(0.0, Basis.UNESTABLISHED, 0, 0, null, List.of());

    public Confidence {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("confidence value must be within [0,1]");
        }
        if (basis == null) {
            throw new IllegalArgumentException("confidence basis must not be null");
        }
        if (supportingCount < 0 || contradictingCount < 0) {
            throw new IllegalArgumentException("evidence counts must not be negative");
        }
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    /** A confidence the system computed itself. */
    public static Confidence measured(double value, int supporting, int contradicting,
                                      String method, List<String> reasons) {
        return new Confidence(value, Basis.MEASURED, supporting, contradicting, method, reasons);
    }

    /** A confidence somebody asserted, without a procedure behind it. */
    public static Confidence declared(double value, List<String> reasons) {
        return new Confidence(value, Basis.DECLARED, 0, 0, null, reasons);
    }

    /**
     * True when the number rests on nothing at all.
     *
     * <p>An answer must not be presented as established on the strength of such a value, and a
     * record that carries one must say so rather than let a reader infer support from a
     * non-null number.</p>
     */
    public boolean unestablished() {
        return basis == Basis.UNESTABLISHED;
    }

    /** True when at least one piece of evidence contradicts the proposition. */
    public boolean contested() {
        return contradictingCount > 0;
    }

    /** True when at least one piece of evidence supports the proposition. */
    public boolean supported() {
        return supportingCount > 0;
    }

    @Override
    public String toString() {
        return "Confidence[" + value + " " + basis + " +" + supportingCount + "/-" + contradictingCount + "]";
    }
}
