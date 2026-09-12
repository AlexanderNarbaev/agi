package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BitNetBlockSequenceTest {

    @Test
    void forwardSequenceProducesCorrectOutputShape() {
        int hidden = 8;
        int intermediate = 16;
        int nHeads = 2;
        int nKvHeads = 1;
        int headDim = 4;

        BitNetBlock block = makeSimpleBlock(hidden, intermediate, nHeads, nKvHeads, headDim);
        int seqLen = 3;
        float[][] input = new float[seqLen][hidden];
        Random rng = new Random(42);
        for (int s = 0; s < seqLen; s++) {
            for (int d = 0; d < hidden; d++) {
                input[s][d] = (float) rng.nextGaussian() * 0.1f;
            }
        }
        BitNetRope rope = new BitNetRope(8, headDim, 10000.0);
        float[][] output = BitNetBlockSequence.forwardSequence(block, input, rope, 0);

        assertThat(output.length).isEqualTo(seqLen);
        for (float[] row : output) {
            assertThat(row.length).isEqualTo(hidden);
            for (float val : row) {
                assertThat(Float.isFinite(val)).isTrue();
            }
        }
    }

    @Test
    void forwardSequenceWithSingleToken() {
        BitNetBlock block = makeSimpleBlock(8, 16, 2, 1, 4);
        float[][] input = new float[1][8];
        java.util.Arrays.fill(input[0], 1.0f);
        BitNetRope rope = new BitNetRope(8, 4, 10000.0);
        float[][] output = BitNetBlockSequence.forwardSequence(block, input, rope, 0);
        assertThat(output.length).isEqualTo(1);
        assertThat(output[0].length).isEqualTo(8);
    }

    @Test
    void forwardSequenceEmptyThrows() {
        BitNetBlock block = makeSimpleBlock(8, 16, 2, 1, 4);
        BitNetRope rope = new BitNetRope(8, 4, 10000.0);
        assertThatThrownBy(() -> BitNetBlockSequence.forwardSequence(block, new float[0][], rope, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forwardSequenceNullArgsThrow() {
        BitNetBlock block = makeSimpleBlock(8, 16, 2, 1, 4);
        BitNetRope rope = new BitNetRope(8, 4, 10000.0);
        assertThatThrownBy(() -> BitNetBlockSequence.forwardSequence(null, new float[1][8], rope, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BitNetBlock makeSimpleBlock(int hidden, int intermediate,
                                                int nHeads, int nKvHeads, int headDim) {
        float[] onesHidden = new float[hidden];
        float[] onesInter = new float[intermediate];
        java.util.Arrays.fill(onesHidden, 1.0f);
        java.util.Arrays.fill(onesInter, 1.0f);
        Random rng = new Random(1);
        return new BitNetBlock(nHeads, nKvHeads, headDim, intermediate,
                onesHidden, onesHidden, onesHidden, onesInter,
                randomWeights(nHeads * headDim, hidden, rng),
                randomWeights(nKvHeads * headDim, hidden, rng),
                randomWeights(nKvHeads * headDim, hidden, rng),
                randomWeights(hidden, nHeads * headDim, rng),
                randomWeights(intermediate, hidden, rng),
                randomWeights(intermediate, hidden, rng),
                randomWeights(hidden, intermediate, rng));
    }

    private static float[] randomWeights(int outDim, int inDim, Random rng) {
        float[] w = new float[outDim * inDim];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (rng.nextGaussian() * 0.05);
        }
        return w;
    }
}
