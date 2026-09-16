package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveReflexionTest {

    @Test
    void nullReturnsEmpty() {
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(null, 3, null);
        assertThat(r.reflections()).isEmpty();
    }

    @Test
    void zeroIterationsReturnsInput() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(p, 0, null);
        assertThat(r.reflections()).isEmpty();
        assertThat(r.finalProfile()).isEqualTo(p);
    }

    @Test
    void reflectProducesEntries() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(p, 3, null);
        assertThat(r.reflections().size()).isEqualTo(3);
    }

    @Test
    void customGenerator() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReflexion.ReflectionGenerator custom = (state, score, iter) ->
            "custom at " + iter;
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(p, 2, custom);
        assertThat(r.reflections().get(0).reflection()).startsWith("custom");
    }

    @Test
    void finalScoreComputed() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(p, 2, null);
        assertThat(r.finalScore()).isBetween(0.0, 1.0 + 1e-9);
    }

    @Test
    void reflectionStepsSequential() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(p, 3, null);
        for (int i = 0; i < r.reflections().size(); i++) {
            assertThat(r.reflections().get(i).iteration()).isEqualTo(i);
        }
    }

    @Test
    void stateBeforeAndAfterDifferent() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(p, 2, null);
        // Each reflection should change state slightly
        assertThat(r.reflections().get(0).stateBefore().phiBinary())
            .isLessThan(r.reflections().get(0).stateAfter().phiBinary());
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
