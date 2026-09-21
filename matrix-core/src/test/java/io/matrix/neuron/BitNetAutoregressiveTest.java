package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BitNetAutoregressiveTest {

    @Test
    void decodeStepProducesCorrectOutputShape() {
        int hidden = 16;
        int intermediate = 32;
        int nHeads = 2;
        int nKvHeads = 1;
        int headDim = 8;

        BitNetBlock block = makeBlock(hidden, intermediate, nHeads, nKvHeads, headDim);
        KvCache cache = new KvCache(1, nKvHeads, headDim, 16);
        BitNetRope rope = new BitNetRope(16, headDim, 10000.0);

        float[] hiddenState = new float[hidden];
        Random rng = new Random(42);
        for (int i = 0; i < hidden; i++) hiddenState[i] = (float) rng.nextGaussian();

        float[] output = BitNetAutoregressive.decodeStep(block, hiddenState, cache, 0, rope, 0);
        assertThat(output).hasSize(hidden);
        assertThat(cache.seqLength()).isEqualTo(1); // K, V appended
        for (float v : output) {
            assertThat(Float.isFinite(v)).isTrue();
        }
    }

    @Test
    void multipleDecodeStepsAccumulateCache() {
        int hidden = 16;
        int intermediate = 32;
        int nHeads = 2;
        int nKvHeads = 1;
        int headDim = 8;

        BitNetBlock block = makeBlock(hidden, intermediate, nHeads, nKvHeads, headDim);
        KvCache cache = new KvCache(1, nKvHeads, headDim, 16);
        BitNetRope rope = new BitNetRope(16, headDim, 10000.0);

        Random rng = new Random(1);
        for (int step = 0; step < 5; step++) {
            float[] h = new float[hidden];
            for (int i = 0; i < hidden; i++) h[i] = (float) rng.nextGaussian();
            BitNetAutoregressive.decodeStep(block, h, cache, 0, rope, step);
        }
        assertThat(cache.seqLength()).isEqualTo(5);
    }

    @Test
    void cacheIsFullOfValidKV() {
        int hidden = 8;
        int intermediate = 16;
        int nHeads = 2;
        int nKvHeads = 1;
        int headDim = 4;

        BitNetBlock block = makeBlock(hidden, intermediate, nHeads, nKvHeads, headDim);
        KvCache cache = new KvCache(1, nKvHeads, headDim, 16);
        BitNetRope rope = new BitNetRope(16, headDim, 10000.0);

        float[] h = new float[hidden];
        java.util.Arrays.fill(h, 1.0f);
        BitNetAutoregressive.decodeStep(block, h, cache, 0, rope, 0);

        // K and V should be written (not all zero)
        var kList = cache.cachedK(0);
        assertThat(kList).hasSize(1);
        float[] kPos = kList.get(0);
        boolean hasNonZero = false;
        for (float v : kPos) if (v != 0.0f) hasNonZero = true;
        assertThat(hasNonZero).isTrue();
    }

    private static BitNetBlock makeBlock(int hidden, int intermediate,
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
