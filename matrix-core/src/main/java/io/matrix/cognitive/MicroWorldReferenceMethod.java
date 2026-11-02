package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmCapability;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.microworld.HypothesisSpace;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.RuleHypothesis;

import java.util.List;
import java.util.Set;

/**
 * W44 — the REFERENCE method for the micro-world: re-derive the surviving hypotheses from the
 * raw observations, then read the answer off them.
 *
 * <p>This is the expensive path, and it is expensive on purpose. It enumerates the whole
 * hypothesis space and filters it against every recorded observation, so its cost grows with
 * the number of cases and with the size of the space. It is always correct within the declared
 * space — it is the definition of the answer — which is exactly why it is the thing a cheaper
 * procedure has to be validated against.</p>
 *
 * <p>The method is STATELESS: the session arrives in the payload. That is what lets the same
 * registered method serve every cycle and every workspace.</p>
 *
 * <p>When the surviving hypotheses disagree, the method REFUSES. It does not pick the majority
 * and it does not average: an undecided model is reported as undecided, because a confident
 * answer from an undecided model is the failure mode this whole wave exists to prevent.</p>
 */
public final class MicroWorldReferenceMethod implements AlgorithmMethod<MicroWorldComputation, Boolean> {

    /** The id under which this method is registered and selected. */
    public static final String ID = "micro-world-hypothesis-enumeration";

    /** The version of this method's contract. */
    public static final String VERSION = "v1";

    /** The size of the hypothesis space for the micro-world's two variables. */
    public static final int HYPOTHESIS_SPACE_SIZE = 16;

    private final AlgorithmCapability capability;

    /** Creates the method with its declared capability. */
    public MicroWorldReferenceMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(CognitiveKernel.FAMILY_MICRO_WORLD),
                Set.of(),
                Set.of("micro-world-assignment"),
                Set.of("boolean"),
                new AlgorithmCapability.ComplexityProfile(
                        "O(2^v * m) where v is the number of variables and m the number of cases",
                        "O(2^v)"),
                new AlgorithmCapability.ResourceProfile("O(2^v)", true, false,
                        "refuses when the surviving hypotheses disagree"),
                new AlgorithmCapability.AccuracyProfile(
                        "exhaustive over the declared hypothesis space",
                        1.0, HYPOTHESIS_SPACE_SIZE, "the micro-world hypothesis space", "construction"),
                new AlgorithmCapability.DeterminismProfile(true, "none"),
                new AlgorithmCapability.ReliabilityProfile(
                        "refusal when the model does not settle", false, "none"),
                new AlgorithmCapability.VerificationStrategy(
                        "agreement with the recorded cases", "MicroWorldReferenceMethod"),
                null);
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(MicroWorldComputation input) {
        return input != null && input.input() != null && input.session() != null;
    }

    @Override
    public Boolean execute(MicroWorldComputation computation, ComputeBudget budget) {
        if (!preconditionsHold(computation)) {
            throw new IllegalStateException("the reference method needs an assignment and a session");
        }
        List<Observation> observations = computation.session().observations();
        List<RuleHypothesis> consistent = HypothesisSpace
                .allBooleanFunctionsOfTwoVariables()
                .stream()
                .filter(hypothesis -> fitsEvery(hypothesis, observations))
                .toList();
        if (consistent.isEmpty()) {
            throw new IllegalStateException("no hypothesis fits the recorded observations");
        }
        RuleHypothesis first = consistent.get(0);
        boolean value = first.evaluate(computation.input());
        for (RuleHypothesis hypothesis : consistent) {
            if (hypothesis.evaluate(computation.input()) != value) {
                throw new IllegalStateException(
                        "the surviving hypotheses disagree; the reference method refuses to answer");
            }
        }
        return value;
    }

    private static boolean fitsEvery(RuleHypothesis hypothesis, List<Observation> observations) {
        for (Observation observation : observations) {
            if (hypothesis.evaluate(observation.input()) != observation.lamp()) {
                return false;
            }
        }
        return true;
    }
}
