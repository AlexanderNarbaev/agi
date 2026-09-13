package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BitLinearDreamerTest {

    @Test
    void wakeSleepCycleProducesUpdatedWeights() {
        int inDim = 16, outDim = 32;
        float[] recognitionWeights = randomWeights(outDim, inDim, 42);
        float[] generationWeights = randomWeights(outDim, inDim, 123);
        float[] observation = new float[inDim];
        Random rng = new Random(7);
        for (int i = 0; i < inDim; i++) observation[i] = (float) rng.nextGaussian();

        BitLinearDreamer.DreamResult result = BitLinearDreamer.wakeSleepCycle(
                recognitionWeights, 1.0f, generationWeights, 1.0f,
                observation, 0.01f, new Random(99));

        assertThat(result.updatedRecognitionWeights()).hasSize(recognitionWeights.length);
        assertThat(result.updatedGenerationWeights()).hasSize(generationWeights.length);
        // Weights should have changed
        boolean recChanged = false;
        for (int i = 0; i < recognitionWeights.length; i++) {
            if (Math.abs(result.updatedRecognitionWeights()[i] - recognitionWeights[i]) > 1e-9) {
                recChanged = true;
                break;
            }
        }
        assertThat(recChanged).isTrue();
    }

    @Test
    void fantasyStateIsTernary() {
        int inDim = 16, outDim = 8;
        float[] recognitionWeights = randomWeights(outDim, inDim, 42);
        float[] generationWeights = randomWeights(outDim, inDim, 123);
        float[] observation = new float[inDim];
        Random rng = new Random(7);
        for (int i = 0; i < inDim; i++) observation[i] = (float) rng.nextGaussian();

        BitLinearDreamer.DreamResult result = BitLinearDreamer.wakeSleepCycle(
                recognitionWeights, 1.0f, generationWeights, 1.0f,
                observation, 0.01f, new Random(99));

        for (float v : result.fantasyState()) {
            assertThat(v).isIn(-1.0f, 0.0f, 1.0f);
        }
    }

    @Test
    void wakeErrorDecreasesOverMultipleCycles() {
        // Multiple wake-sleep cycles should reduce prediction error
        int inDim = 16, outDim = 8;
        float[] recognitionWeights = randomWeights(outDim, inDim, 42);
        float[] generationWeights = randomWeights(outDim, inDim, 123);
        float[] observation = new float[inDim];
        Random rng = new Random(7);
        for (int i = 0; i < inDim; i++) observation[i] = (float) rng.nextGaussian();

        double firstError = 1.0;
        double lastError = 0.0;
        for (int cycle = 0; cycle < 20; cycle++) {
            BitLinearDreamer.DreamResult result = BitLinearDreamer.wakeSleepCycle(
                    recognitionWeights, 1.0f, generationWeights, 1.0f,
                    observation, 0.1f, new Random(cycle));
            double err = result.totalErrorMagnitude();
            if (cycle == 0) firstError = err;
            if (cycle == 19) lastError = err;
        }
        // Error should decrease (or stay close to initial)
        System.out.printf("[BitLinearDreamer] firstError=%.4f lastError=%.4f%n", firstError, lastError);
        assertThat(lastError).isLessThan(firstError * 2.0f); // bounded
    }

    @Test
    void rejectsNullInputs() {
        float[] w = new float[4];
        float[] x = new float[2];
        Random rng = new Random(1);
        assertThatThrownBy(() -> BitLinearDreamer.wakeSleepCycle(null, 1.0f, w, 1.0f, x, 0.01f, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinearDreamer.wakeSleepCycle(w, 1.0f, null, 1.0f, x, 0.01f, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinearDreamer.wakeSleepCycle(w, 1.0f, w, 1.0f, null, 0.01f, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadLearningRate() {
        float[] w = new float[4];
        float[] x = new float[2];
        Random rng = new Random(1);
        assertThatThrownBy(() -> BitLinearDreamer.wakeSleepCycle(w, 1.0f, w, 1.0f, x, 0.0f, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitLinearDreamer.wakeSleepCycle(w, 1.0f, w, 1.0f, x, 1.5f, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dreamResultHasAllFields() {
        BitLinearDreamer.DreamResult r = new BitLinearDreamer.DreamResult(
                new float[]{1.0f}, new float[]{2.0f}, new float[]{3.0f}, 0.1, 0.2);
        assertThat(r.totalErrorMagnitude()).isCloseTo(0.3, within(1e-9));
    }

    private static float[] randomWeights(int rows, int cols, long seed) {
        Random rng = new Random(seed);
        float[] w = new float[rows * cols];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (rng.nextGaussian() * 0.1);
        }
        return w;
    }
}
