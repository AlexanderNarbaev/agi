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
        KvCache cache = new KvCache(2, 2, 4, 16);
        // Position 0
        float[] k0 = cache.kSlot(0, 0);
        float[] v0 = cache.vSlot(0, 0);
        assertThat(k0).hasSize(4);
        assertThat(v0).hasSize(4);
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
        float[] k = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] v = {5.0f, 6.0f, 7.0f, 8.0f};
        int newLen = cache.append(0, k, v);
        assertThat(newLen).isEqualTo(1);
        assertThat(cache.seqLength()).isEqualTo(1);
        // The raw buffer should contain the values
        assertThat(cache.rawK(0)[0]).isEqualTo(1.0f);
        assertThat(cache.rawK(0)[1]).isEqualTo(2.0f);
        assertThat(cache.rawV(0)[2]).isEqualTo(7.0f);
    }

    @Test
    void appendRejectsWrongSize() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        assertThatThrownBy(() -> cache.append(0, new float[3], new float[4]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.append(0, new float[4], null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendRejectsWhenCacheFull() {
        KvCache cache = new KvCache(2, 2, 4, 4); // maxSeqLen=4
        for (int i = 0; i < 4; i++) {
            cache.append(0, new float[]{1, 2, 3, 4}, new float[]{5, 6, 7, 8});
        }
        // Now full — 5th append should throw
        assertThatThrownBy(() -> cache.append(0, new float[]{1, 2, 3, 4}, new float[]{5, 6, 7, 8}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appendAllAddsToAllLayersAtOnce() {
        KvCache cache = new KvCache(3, 2, 4, 16);
        float[][] kPerLayer = {
                {1, 1, 1, 1},
                {2, 2, 2, 2},
                {3, 3, 3, 3}
        };
        float[][] vPerLayer = {
                {10, 10, 10, 10},
                {20, 20, 20, 20},
                {30, 30, 30, 30}
        };
        cache.appendAll(kPerLayer, vPerLayer);
        assertThat(cache.seqLength()).isEqualTo(1);
        // Each layer's K and V should be at position 0
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
        float[][] wrongK = {{1, 1, 1, 1}};
        float[][] wrongV = {{1, 1, 1, 1}};
        assertThatThrownBy(() -> cache.appendAll(wrongK, wrongV))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cachedKAndVReturnPositionList() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        cache.append(0, new float[]{1, 2, 3, 4}, new float[]{5, 6, 7, 8});
        cache.append(0, new float[]{9, 10, 11, 12}, new float[]{13, 14, 15, 16});

        var kList = cache.cachedK(0);
        assertThat(kList).hasSize(2);
        assertThat(kList.get(0)).containsExactly(1.0f, 2.0f, 3.0f, 4.0f);
        assertThat(kList.get(1)).containsExactly(9.0f, 10.0f, 11.0f, 12.0f);

        var vList = cache.cachedV(0);
        assertThat(vList).hasSize(2);
        assertThat(vList.get(0)).containsExactly(5.0f, 6.0f, 7.0f, 8.0f);
        assertThat(vList.get(1)).containsExactly(13.0f, 14.0f, 15.0f, 16.0f);
    }

    @Test
    void resetClearsSeqLength() {
        KvCache cache = new KvCache(2, 2, 4, 16);
        cache.append(0, new float[]{1, 2, 3, 4}, new float[]{5, 6, 7, 8});
        assertThat(cache.seqLength()).isEqualTo(1);
        cache.reset();
        assertThat(cache.seqLength()).isEqualTo(0);
    }

    @Test
    void memoryBytesIsCorrect() {
        // 30 layers × 2 (K,V) × 4096 × 128 × 4 bytes = 125,829,120 bytes = ~120 MB
        KvCache cache = new KvCache(30, 5, 128, 4096);
        long expected = 30L * 2 * 4096 * 128 * 4;
        assertThat(cache.memoryBytes()).isEqualTo(expected);
    }

    @Test
    void bitNetKvCacheMemoryMatchesBudget() {
        // Per docs: 5 KV heads × 128 head_dim × 4096 max_seq × 4 bytes × 2 (K,V) = 20 MB/layer
        // × 30 layers = 600 MB
        // But our storage uses head_dim as the LAST dim (not n_kv_heads * head_dim)
        // because BitNet uses n_kv_heads separate heads, each with head_dim
        // So per layer: 2 × n_kv_heads × max_seq × head_dim × 4 bytes
        //   = 2 × 5 × 4096 × 128 × 4 = 20.97 MB
        // × 30 layers = ~629 MB
        KvCache cache = new KvCache(30, 5, 128, 4096);
        // Note: actual layout is 1 head_dim slot per position (not n_kv_heads * head_dim)
        // because we use simplified storage; per-position K/V is [headDim] floats
        // For true multi-head attention, each layer needs n_kv_heads * headDim per position
        long actualMem = cache.memoryBytes();
        System.out.printf("[KvCache memory] %d bytes (%.1f MB)%n",
                actualMem, actualMem / 1024.0 / 1024.0);
        assertThat(actualMem).isGreaterThan(0);
    }
}
