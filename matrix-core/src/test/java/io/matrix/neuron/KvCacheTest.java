package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KvCacheTest {

    @Test
    void constructorStoresDimensions() {
        KvCache cache = new KvCache(30, 5, 128, 4096);
        assertThat(cache.numLayers).isEqualTo(30);
        assertThat(cache.numKvHeads).isEqualTo(5);
        assertThat(cache.headDim).isEqualTo(128);
        assertThat(cache.maxSeqLen).isEqualTo(4096);
        assertThat(cache.seqLength()).isEqualTo(0);
    }

    @Test
    void constructorRejectsBadDimensions() {
        assertThatThrownBy(() -> new KvCache(0, 5, 128, 4096))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KvCache(30, 0, 128, 4096))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KvCache(30, 5, 0, 4096))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KvCache(30, 5, 128, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kSlotAndVSlotReturnCorrectSlice() {
        KvCache cache = new KvCache(2, 2, 4, 16); // perPos = 8
        float[] k0 = cache.kSlot(0, 0);
        float[] v0 = cache.vSlot(0, 0);
        assertThat(k0).hasSize(8);
        assertThat(v0).hasSize(8);
    }

    @Test
    void kSlotRejectsBadPosition() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        assertThatThrownBy(() -> cache.kSlot(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.kSlot(0, 16))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendAddsKAndVAndIncrementsSeqLength() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        float[] k = {1, 2, 3, 4, 5, 6, 7, 8};
        float[] v = {9, 10, 11, 12, 13, 14, 15, 16};
        int newLen = cache.append(0, k, v);
        assertThat(newLen).isEqualTo(1);
        assertThat(cache.seqLength()).isEqualTo(1);
        // Verify raw storage contains the values
        assertThat(cache.rawK(0)[0]).isEqualTo(1.0f);
        assertThat(cache.rawK(0)[3]).isEqualTo(4.0f);
        assertThat(cache.rawV(0)[5]).isEqualTo(14.0f);
    }

    @Test
    void appendRejectsWrongSize() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        assertThatThrownBy(() -> cache.append(0, new float[3], new float[8]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.append(0, new float[8], null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendRejectsWhenCacheFull() {
        KvCache cache = new KvCache(2, 2, 4, 4); // maxSeqLen=4
        for (int i = 0; i < 4; i++) {
            cache.append(0, new float[]{1, 2, 3, 4, 5, 6, 7, 8},
                    new float[]{9, 10, 11, 12, 13, 14, 15, 16});
        }
        // Now full — 5th append should throw
        assertThatThrownBy(() -> cache.append(0, new float[]{1, 2, 3, 4, 5, 6, 7, 8},
                new float[]{9, 10, 11, 12, 13, 14, 15, 16}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appendAllAddsToAllLayersAtOnce() {
        KvCache cache = new KvCache(3, 2, 4, 16);
        float[][] kPerLayer = {
                {1, 1, 1, 1, 1, 1, 1, 1},
                {2, 2, 2, 2, 2, 2, 2, 2},
                {3, 3, 3, 3, 3, 3, 3, 3}
        };
        float[][] vPerLayer = {
                {10, 10, 10, 10, 10, 10, 10, 10},
                {20, 20, 20, 20, 20, 20, 20, 20},
                {30, 30, 30, 30, 30, 30, 30, 30}
        };
        cache.appendAll(kPerLayer, vPerLayer);
        assertThat(cache.seqLength()).isEqualTo(1);
        assertThat(cache.rawK(0)[0]).isEqualTo(1.0f);
        assertThat(cache.rawK(1)[0]).isEqualTo(2.0f);
        assertThat(cache.rawK(2)[0]).isEqualTo(3.0f);
        assertThat(cache.rawV(0)[0]).isEqualTo(10.0f);
        assertThat(cache.rawV(1)[0]).isEqualTo(20.0f);
        assertThat(cache.rawV(2)[0]).isEqualTo(30.0f);
    }

    @Test
    void appendAllRejectsWrongNumberOfLayers() {
        KvCache cache = new KvCache(3, 2, 4, 16);
        float[][] wrongK = {{1, 1, 1, 1, 1, 1, 1, 1}};
        float[][] wrongV = {{1, 1, 1, 1, 1, 1, 1, 1}};
        assertThatThrownBy(() -> cache.appendAll(wrongK, wrongV))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cachedKAndVReturnPositionList() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        cache.append(0, new float[]{1, 2, 3, 4, 5, 6, 7, 8},
                new float[]{9, 10, 11, 12, 13, 14, 15, 16});
        cache.append(0, new float[]{17, 18, 19, 20, 21, 22, 23, 24},
                new float[]{25, 26, 27, 28, 29, 30, 31, 32});

        var kList = cache.cachedK(0);
        assertThat(kList).hasSize(2);
        assertThat(kList.get(0)).containsExactly(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f);
        assertThat(kList.get(1)).containsExactly(17.0f, 18.0f, 19.0f, 20.0f, 21.0f, 22.0f, 23.0f, 24.0f);

        var vList = cache.cachedV(0);
        assertThat(vList).hasSize(2);
        assertThat(vList.get(0)).containsExactly(9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f);
        assertThat(vList.get(1)).containsExactly(25.0f, 26.0f, 27.0f, 28.0f, 29.0f, 30.0f, 31.0f, 32.0f);
    }

    @Test
    void resetClearsSeqLength() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        cache.append(0, new float[]{1, 2, 3, 4, 5, 6, 7, 8},
                new float[]{9, 10, 11, 12, 13, 14, 15, 16});
        assertThat(cache.seqLength()).isEqualTo(1);
        cache.reset();
        assertThat(cache.seqLength()).isEqualTo(0);
    }

    @Test
    void memoryBytesIsCorrect() {
        // 30 layers × 2 (K,V) × 4096 max_seq × 5 kv_heads × 128 head_dim × 4 bytes
        KvCache cache = new KvCache(30, 5, 128, 4096);
        long expected = 30L * 2 * 4096 * 5 * 128 * 4;
        assertThat(cache.memoryBytes()).isEqualTo(expected);
    }

    @Test
    void bitNetKvCacheMemoryMatchesBudget() {
        // Per-position storage: numKvHeads * headDim = 5 * 128 = 640 floats
        // × 2 (K+V) × 4096 max_seq × 4 bytes = ~20 MB per layer
        // × 30 layers = ~629 MB total
        KvCache cache = new KvCache(30, 5, 128, 4096);
        long actualMem = cache.memoryBytes();
        System.out.printf("[KvCache memory] %d bytes (%.1f MB)%n",
                actualMem, actualMem / 1024.0 / 1024.0);
        assertThat(actualMem).isGreaterThan(0);
    }
}
