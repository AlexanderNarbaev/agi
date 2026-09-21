package io.matrix.neuron;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RUN 464 — KV cache for autoregressive generation (DESIGN-54 §14).
 *
 * <p>Stores per-layer K and V tensors across tokens to avoid recomputation.
 * Each layer's storage is shaped as [maxSeqLen × (numKvHeads × headDim)]
 * per position, supporting multi-head KV (e.g., GQA in BitNet).
 *
 * <h2>Memory budget (BitNet b1.58-2B-4T, batch=1)</h2>
 * <pre>
 *   per layer: 2 (K+V) × 4096 × 5 × 128 × 4 bytes = 20 MB
 *   × 30 layers = 600 MB
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure data structure. No RNG, no wall-clock.
 */
public final class KvCache {

    public final int numLayers;
    public final int numKvHeads;
    public final int headDim;
    public final int maxSeqLen;

    /** Per-layer storage: storage[layer][kvIdx] → float[maxSeqLen × numKvHeads × headDim]. */
    private final float[][][] storage;

    private int seqLength;

    public KvCache(int numLayers, int numKvHeads, int headDim, int maxSeqLen) {
        if (numLayers < 1 || numKvHeads < 1 || headDim < 1 || maxSeqLen < 1) {
            throw new IllegalArgumentException("invalid dimensions");
        }
        this.numLayers = numLayers;
        this.numKvHeads = numKvHeads;
        this.headDim = headDim;
        this.maxSeqLen = maxSeqLen;
        this.storage = new float[numLayers][2][maxSeqLen * numKvHeads * headDim];
        this.seqLength = 0;
    }

    private int perPos() {
        return numKvHeads * headDim;
    }

    public float[] kSlot(int layerIdx, int position) {
        checkPosition(position);
        int offset = position * perPos();
        return java.util.Arrays.copyOfRange(
                storage[layerIdx][0], offset, offset + perPos());
    }

    public float[] vSlot(int layerIdx, int position) {
        checkPosition(position);
        int offset = position * perPos();
        return java.util.Arrays.copyOfRange(
                storage[layerIdx][1], offset, offset + perPos());
    }

    public int append(int layerIdx, float[] k, float[] v) {
        if (k == null || v == null) throw new IllegalArgumentException("null k/v");
        int p = perPos();
        if (k.length != p || v.length != p) {
            throw new IllegalArgumentException("k/v must be length " + p
                    + " (got k=" + k.length + ", v=" + v.length + ")");
        }
        if (layerIdx < 0 || layerIdx >= numLayers) {
            throw new IllegalArgumentException("layerIdx " + layerIdx
                    + " out of range [0, " + numLayers + ")");
        }
        int pos = seqLength;
        if (pos >= maxSeqLen) {
            throw new IllegalStateException("cache full at seqLength=" + pos);
        }
        System.arraycopy(k, 0, storage[layerIdx][0], pos * p, p);
        System.arraycopy(v, 0, storage[layerIdx][1], pos * p, p);
        seqLength++;
        return seqLength;
    }

    public int appendAll(float[][] kPerLayer, float[][] vPerLayer) {
        if (kPerLayer.length != numLayers || vPerLayer.length != numLayers) {
            throw new IllegalArgumentException("need one K/V per layer");
        }
        int pos = seqLength;
        if (pos >= maxSeqLen) throw new IllegalStateException("cache full");
        int p = perPos();
        for (int l = 0; l < numLayers; l++) {
            System.arraycopy(kPerLayer[l], 0, storage[l][0], pos * p, p);
            System.arraycopy(vPerLayer[l], 0, storage[l][1], pos * p, p);
        }
        seqLength++;
        return seqLength;
    }

    public List<float[]> cachedK(int layerIdx) {
        List<float[]> result = new ArrayList<>(seqLength);
        for (int p = 0; p < seqLength; p++) {
            float[] k = new float[perPos()];
            System.arraycopy(storage[layerIdx][0], p * perPos(), k, 0, perPos());
            result.add(k);
        }
        return result;
    }

    public List<float[]> cachedV(int layerIdx) {
        List<float[]> result = new ArrayList<>(seqLength);
        for (int p = 0; p < seqLength; p++) {
            float[] v = new float[perPos()];
            System.arraycopy(storage[layerIdx][1], p * perPos(), v, 0, perPos());
            result.add(v);
        }
        return result;
    }

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

    public long memoryBytes() {
        return (long) numLayers * 2 * maxSeqLen * numKvHeads * headDim * 4;
    }
}
