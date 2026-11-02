package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmCapability;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.microworld.RuleHypothesis;

import java.util.List;
import java.util.Set;

/**
 * W44 — the FAST PATH: read the answer off the single surviving hypothesis.
 *
 * <p>This is the method that makes the W44 §22 sequence real. The expensive reference
 * ({@link MicroWorldReferenceMethod}) re-derives the surviving hypotheses from every recorded
 * case. Once the model has settled — exactly one hypothesis survives — that derivation is
 * redundant, and the answer can be read off the survivor directly. The saving is not a
 * hand-written shortcut; it is a procedure that was validated against the reference on held-out
 * cases and only then promoted.</p>
 *
 * <p>Three guards, all of which must hold before this method answers:</p>
 * <ol>
 *   <li>the model has settled — exactly one surviving hypothesis;</li>
 *   <li>a procedure has been PROMOTED, and it is still active;</li>
 *   <li>the request's facts satisfy the procedure's applicability domain.</li>
 * </ol>
 *
 * <p>When any of them fails, this method refuses and the selector's fallback — the reference
 * method — takes over. The fast path is never a trap.</p>
 *
 * <p>The capability declares {@code MODEL_SETTLED} as its precondition, so the selector cannot
 * admit this method when the model has not settled, and the method checks the same condition
 * itself before it computes anything.</p>
 */
public final class MicroWorldSettledModelMethod implements AlgorithmMethod<MicroWorldComputation, Boolean> {

    /** The id under which this method is registered and selected. */
    public static final String ID = "micro-world-settled-model";

    /** The version of this method's contract. */
    public static final String VERSION = "v1";

    /** The fact that must hold for the fast path to be applicable. */
    public static final String PRECONDITION_MODEL_SETTLED = "MODEL_SETTLED";

    /** The id of the method this one falls back to. */
    public static final String FALLBACK_ID = MicroWorldReferenceMethod.ID;

    private final AlgorithmCapability capability;

    /** Creates the method with its declared capability. */
    public MicroWorldSettledModelMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(CognitiveKernel.FAMILY_MICRO_WORLD),
                Set.of(PRECONDITION_MODEL_SETTLED),
                Set.of("micro-world-assignment"),
                Set.of("boolean"),
                new AlgorithmCapability.ComplexityProfile("O(1) once the model has settled", "O(1)"),
                new AlgorithmCapability.ResourceProfile("O(1)", true, false,
                        "refuses when the model has not settled"),
                new AlgorithmCapability.AccuracyProfile(
                        "agreement with the reference method on held-out cases",
                        1.0, 0, "the held-out cases of the promoted procedure", "measured at promotion"),
                new AlgorithmCapability.DeterminismProfile(true, "none"),
                new AlgorithmCapability.ReliabilityProfile(
                        "refusal when the applicability domain does not hold", false,
                        "the reference method takes over"),
                new AlgorithmCapability.VerificationStrategy(
                        "agreement with the reference method", "MicroWorldSettledModelMethod"),
                FALLBACK_ID);
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(MicroWorldComputation computation) {
        if (computation == null || computation.session() == null) {
            return false;
        }
        if (!computation.fact(PRECONDITION_MODEL_SETTLED)) {
            return false;
        }
        Procedure procedure = computation.procedure();
        if (procedure == null || !procedure.active()) {
            return false;
        }
        return procedure.applicable(computation.facts());
    }

    @Override
    public Boolean execute(MicroWorldComputation computation, ComputeBudget budget) {
        if (!preconditionsHold(computation)) {
            throw new IllegalStateException(
                    "the fast path is not applicable; the reference method must answer");
        }
        List<RuleHypothesis> surviving = computation.session().survivingHypotheses();
        if (surviving.size() != 1) {
            throw new IllegalStateException(
                    "the model has not settled: " + surviving.size() + " hypotheses survive");
        }
        return surviving.get(0).evaluate(computation.input());
    }
}
