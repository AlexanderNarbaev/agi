package io.matrix.neuron;

import io.matrix.consciousness.CognitiveGenesisProfile;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConsciousBrainLLMArchitectureTest {

    @Test
    void brainHasApplyRoPE() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        double[] result = brain.applyRoPEToProfile(p, 0);
        assertThat(result).isNotNull();
        assertThat(result.length).isEqualTo(64);
    }

    @Test
    void nullProfileRoPEReturnsNull() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        assertThat(brain.applyRoPEToProfile(null, 0)).isNull();
    }

    @Test
    void brainHasApplySwiGLU() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        double[] result = brain.applySwiGLUToProfile(p);
        assertThat(result).isNotNull();
        assertThat(result.length).isEqualTo(64);
    }

    @Test
    void nullProfileSwiGLUReturnsNull() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        assertThat(brain.applySwiGLUToProfile(null)).isNull();
    }

    @Test
    void brainHasApplyGQAReturnsNullForEmptyHistory() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        // No history yet, GQA returns null
        assertThat(brain.applyGQAToProfile(p)).isNull();
    }

    @Test
    void nullProfileGQAReturnsNull() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        assertThat(brain.applyGQAToProfile(null)).isNull();
    }

    @Test
    void ropePositionChangesResult() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        double[] r0 = brain.applyRoPEToProfile(p, 0);
        double[] r1 = brain.applyRoPEToProfile(p, 1);
        // Position 0 returns original, position 1 is different
        boolean different = false;
        for (int i = 0; i < r0.length; i++) {
            if (Math.abs(r0[i] - r1[i]) > 1e-6) {
                different = true;
                break;
            }
        }
        assertThat(different).isTrue();
    }
}
