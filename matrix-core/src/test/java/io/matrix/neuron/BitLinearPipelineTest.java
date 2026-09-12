package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BitLinearPipelineTest {

    @Test
    void constructorRejectsBadArgs() {
        assertThatThrownBy(() -> new BitLinearPipeline(null, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BitLinearPipeline(new int[]{8}, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BitLinearPipeline(new int[]{8, 4}, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void threeLayerPipelineHasCorrectDimensions() {
        BitLinearPipeline pipe = new BitLinearPipeline(new int[]{16, 32, 16, 4}, new Random(1));
        assertThat(pipe.layerCount()).isEqualTo(3);
        assertThat(pipe.inputDim()).isEqualTo(16);
        assertThat(pipe.outputDim()).isEqualTo(4);
        assertThat(pipe.parameterCount()).isEqualTo(16 * 32 + 32 * 16 + 16 * 4);
    }

    @Test
    void forwardProducesOutputOfExpectedDimension() {
        BitLinearPipeline pipe = new BitLinearPipeline(new int[]{8, 16, 4}, new Random(1));
        float[] input = new float[8];
        for (int i = 0; i < 8; i++) input[i] = (float) (i * 0.1);
        float[] output = pipe.forward(input);
        assertThat(output).hasSize(4);
    }

    @Test
    void forwardProducesFiniteValues() {
        BitLinearPipeline pipe = new BitLinearPipeline(new int[]{8, 16, 8}, new Random(1));
        float[] input = new float[8];
        for (int i = 0; i < 8; i++) input[i] = (float) (i * 0.1);
        float[] output = pipe.forward(input);
        for (float v : output) {
            assertThat(Float.isFinite(v)).isTrue();
        }
    }

    @Test
    void forwardIsDeterministicGivenSeed() {
        BitLinearPipeline pipe1 = new BitLinearPipeline(new int[]{8, 16, 4}, new Random(42));
        BitLinearPipeline pipe2 = new BitLinearPipeline(new int[]{8, 16, 4}, new Random(42));
        float[] input = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
        float[] out1 = pipe1.forward(input);
        float[] out2 = pipe2.forward(input);
        for (int i = 0; i < out1.length; i++) {
            assertThat(out1[i]).isEqualTo(out2[i]);
        }
    }

    @Test
    void forwardWithIntermediatesReturnsAllLayers() {
        BitLinearPipeline pipe = new BitLinearPipeline(new int[]{8, 16, 8, 4}, new Random(1));
        float[] input = new float[8];
        for (int i = 0; i < 8; i++) input[i] = (float) (i * 0.1);
        List<float[]> intermediates = pipe.forwardWithIntermediates(input);
        // 1 input + 3 layer outputs = 4 elements
        assertThat(intermediates).hasSize(4);
        assertThat(intermediates.get(0)).hasSize(8); // input
        assertThat(intermediates.get(1)).hasSize(16); // after layer 1
        assertThat(intermediates.get(2)).hasSize(8);  // after layer 2
        assertThat(intermediates.get(3)).hasSize(4);  // after layer 3
    }

    @Test
    void forwardRejectsNullInput() {
        BitLinearPipeline pipe = new BitLinearPipeline(new int[]{8, 4}, new Random(1));
        assertThatThrownBy(() -> pipe.forward(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forwardWithWrongInputDimensionThrows() {
        BitLinearPipeline pipe = new BitLinearPipeline(new int[]{8, 4}, new Random(1));
        float[] wrong = new float[16]; // wrong size
        assertThatThrownBy(() -> pipe.forward(wrong))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
