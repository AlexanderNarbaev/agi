package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 464 — KV cache for autoregressive generation (DESIGN-54 §14).
 *
 * <p>Stores per-layer K and V tensors across tokens to avoid recomputation.
 * Standard transformer KV cache layout:
 * <pre>
 *   shape per layer: [batch, n_kv_heads, max_seq_len, head_dim]
 *   dtype: float32 (we use float for Java; bf16 in real inference)
 * </pre>
 *
 * <h2>Memory budget (BitNet b1.58-2B-4T, batch=1)</h2>
 * <pre>
 *   per layer: 2 (K+V) × 5 (kv_heads) × 4096 (max_seq) × 128 (head_dim) × 4 bytes = 20 MB
 *   × 30 layers = 600 MB
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 *   KvCache cache = new KvCache(30, 5, 128, 4096);
 *   // For each layer: get K, V slots
 *   float[] kSlot = cache.kSlot(layerIdx, position);
 *   float[] vSlot = cache.vSlot(layerIdx, position);
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure data structure. No RNG, no wall-clock. Caller manages state.
 */
public final class KvCache {

    /** Number of decoder layers. */
    public final int numLayers;
    /** Number of key-value heads (5 for BitNet GQA). */
    public final int numKvHeads;
    /** Dimension of each head (128 for BitNet). */
    public final int headDim;
    /** Maximum sequence length to pre-allocate. */
    public final int maxSeqLen;

    /**
     * Per-layer K, V storage. Layout: storage[layer][kvIdx] → float[maxSeqLen * headDim]
     * (kvIdx = 0 for K, 1 for V).
     */
    private final float[][][] storage;

    /** Current sequence length filled (cached positions: 0..seqLength-1). */
    private int seqLength;

    public KvCache(int numLayers, int numKvHeads, int headDim, int maxSeqLen) {
        if (numLayers < 1 || numKvHeads < 1 || headDim < 1 || maxSeqLen < 1) {
            throw new IllegalArgumentException("invalid dimensions");
        }
        this.numLayers = numLayers;
        this.numKvHeads = numKvHeads;
        this.headDim = headDim;
        this.maxSeqLen = maxSeqLen;
        // layers × (K, V) × maxSeqLen × headDim
        this.storage = new float[numLayers][2][maxSeqLen * headDim];
        this.seqLength = 0;
    }

    /**
     * Get the K buffer for a specific layer and sequence position. Returns a
     * slice of the pre-allocated storage (length = headDim) where the caller
     * writes K values for this position.
     *
     * @param layerIdx layer index [0, numLayers)
     * @param position sequence position to write K for
     * @return slice of headDim floats (caller writes here)
     */
    public float[] kSlot(int layerIdx, int position) {
        checkPosition(position);
        int offset = position * headDim;
        return java.util.Arrays.copyOfRange(
                storage[layerIdx][0], offset, offset + headDim);
    }

    /**
     * Get the V buffer for a specific layer and sequence position.
     */
    public float[] vSlot(int layerIdx, int position) {
        checkPosition(position);
        int offset = position * headDim;
        return java.util.Arrays.copyOfRange(
                storage[layerIdx][1], offset, offset + headDim);
    }

    /**
     * Append K and V for a new position. Returns the new sequence length.
     */
    public int append(int layerIdx, float[] k, float[] v) {
        if (k == null || v == null) throw new IllegalArgumentException("null k/v");
        if (k.length != headDim || v.length != headDim) {
            throw new IllegalArgumentException("k/v must be length " + headDim);
        }
        int pos = seqLength;
        if (pos >= maxSeqLen) {
            throw new IllegalStateException("cache full at seqLength=" + pos);
        }
        System.arraycopy(k, 0, storage[layerIdx][0], pos * headDim, headDim);
        System.arraycopy(v, 0, storage[layerIdx][1], pos * headDim, headDim);
        seqLength++;
        return seqLength;
    }

    /**
     * Append to all layers at once with the same K, V (rare — usually each
     * layer has different K, V). Useful for testing.
     */
    public int appendAll(float[][] kPerLayer, float[][] vPerLayer) {
        if (kPerLayer.length != numLayers || vPerLayer.length != numLayers) {
            throw new IllegalArgumentException("need one K/V per layer");
        }
        int pos = seqLength;
        if (pos >= maxSeqLen) throw new IllegalStateException("cache full");
        for (int l = 0; l < numLayers; l++) {
            System.arraycopy(kPerLayer[l], 0, storage[l][0], pos * headDim, headDim);
            System.arraycopy(vPerLayer[l], 0, storage[l][1], pos * headDim, headDim);
        }
        seqLength++;
        return seqLength;
    }

    /**
     * Get all cached K positions for a layer (for attention computation).
     * Returns a list of float arrays, one per position up to seqLength.
     */
    public List<float[]> cachedK(int layerIdx) {
        List<float[]> result = new ArrayList<>(seqLength);
        for (int p = 0; p < seqLength; p++) {
            float[] k = new float[headDim];
            System.arraycopy(storage[layerIdx][0], p * headDim, k, 0, headDim);
            result.add(k);
        }
        return result;
    }

    public List<float[]> cachedV(int layerIdx) {
        List<float[]> result = new ArrayList<>(seqLength);
        for (int p = 0; p < seqLength; p++) {
            float[] v = new float[headDim];
            System.arraycopy(storage[layerIdx][1], p * headDim, v, 0, headDim);
            result.add(v);
        }
        return result;
    }

    /**
     * Reset the cache (start new sequence).
     */
    public void reset() {
        seqLength = 0;
    }

    public int seqLength() {
        return seqLength;
    }

    public int maxSeqLen() {
        return maxSeqLen;
    }

    public float[] rawK(int layerIdx) {
        return storage[layerIdx][0];
    }

    public float[] rawV(int layerIdx) {
        return storage[layerIdx][1];
    }

    private void checkPosition(int position) {
        if (position < 0 || position >= maxSeqLen) {
            throw new IllegalArgumentException("position " + position + " out of range [0, " + maxSeqLen + ")");
        }
    }

    /**
     * Memory usage in bytes for the cache.
     */
    public long memoryBytes() {
        return (long) numLayers * 2 * maxSeqLen * headDim * 4;
    }
}
