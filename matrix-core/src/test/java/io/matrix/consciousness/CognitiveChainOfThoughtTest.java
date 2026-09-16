package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveChainOfThoughtTest {

    @Test
    void nullInputReturnsEmpty() {
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(null, 3, null);
        assertThat(r.steps()).isEmpty();
        assertThat(r.finalOutput()).isNull();
    }

    @Test
    void zeroStepsReturnsInput() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(p, 0, null);
        assertThat(r.steps()).isEmpty();
        assertThat(r.finalOutput()).isEqualTo(p);
    }

    @Test
    void reasonProducesSteps() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(p, 3, null);
        assertThat(r.steps().size()).isEqualTo(3);
        assertThat(r.finalOutput()).isNotNull();
    }

    @Test
    void customStepFunctionApplied() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveChainOfThought.StepFunction custom = (input, idx) ->
            new CognitiveGenesisProfile(
                0.9, 0.9, 0.9, 0.9,
                input.interAgentPhi(), input.stabilityPhi(), input.crossLevelPhi(),
                input.kolmogorovK(),
                input.analogicalSimilarity(), input.conceptualExclusion(),
                input.nkEdgeOfChaosK(), input.memristorConductance(), input.lSystemComplexityRatio()
            );
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(p, 2, custom);
        assertThat(r.steps().size()).isEqualTo(2);
        assertThat(r.finalOutput().phiBinary()).isEqualTo(0.9);
    }

    @Test
    void nullStepReturnsNullBreaksChain() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveChainOfThought.StepFunction breakFunc = (input, idx) -> null;
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(p, 5, breakFunc);
        // Should stop at first null
        assertThat(r.steps().size()).isEqualTo(0);
    }

    @Test
    void stepDescriptionsSet() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(p, 2, null);
        for (CognitiveChainOfThought.ThoughtStep step : r.steps()) {
            assertThat(step.description()).isNotEmpty();
        }
    }

    @Test
    void stepIndicesSequential() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(p, 3, null);
        for (int i = 0; i < r.steps().size(); i++) {
            assertThat(r.steps().get(i).stepIndex()).isEqualTo(i);
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
