package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 458 — BitNet RoPE rotary embeddings (DESIGN-54 §11).
 *
 * <p>Pure-Java implementation of vanilla RoPE matching the PyTorch reference:
 * <pre>
 *   inv_freq[i] = 1 / (rope_theta ** (i / head_dim)) for i in 0,2,4,...,head_dim-2
 *   emb[t, d] = inv_freq[d%64] * position_ids[t]
 *   cos[t, d] = cos(emb[t, d])
 *   sin[t, d] = sin(emb[t, d])
 *   rotate_half(x) = cat([-x[head_dim/2:], x[:head_dim/2]])
 *   q_embed = q * cos + rotate_half(q) * sin
 *   k_embed = k * cos + rotate_half(k) * sin
 * </pre>
 *
 * <p>For microsoft/bitnet-b1.58-2B-4T: head_dim=128, rope_theta=500000.0,
 * max_position_embeddings=4096.
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock. Deterministic given inputs.
 */
public final class BitNetRope {

    /** Default rope_theta for BitNet b1.58. */
    public static final double DEFAULT_ROPE_THETA = 500000.0;

    /** Default head_dim for BitNet b1.58 (hidden_size / num_attention_heads). */
    public static final int DEFAULT_HEAD_DIM = 128;

    private final float[] cosTable; // [max_seq_len × head_dim]
    private final float[] sinTable;
    private final int headDim;

    /**
     * Precompute cos/sin tables for a given max sequence length.
     *
     * @param maxSeqLen maximum sequence length to precompute
     * @param headDim head dimension (default 128 for BitNet)
     * @param ropeTheta base for frequency computation (default 500000)
     */
    public BitNetRope(int maxSeqLen, int headDim, double ropeTheta) {
        if (maxSeqLen < 1) throw new IllegalArgumentException("maxSeqLen < 1");
        if (headDim % 2 != 0) throw new IllegalArgumentException("headDim must be even");
        this.headDim = headDim;
        int halfDim = headDim / 2;
        // Compute inv_freq
        float[] invFreq = new float[halfDim];
        for (int i = 0; i < halfDim; i++) {
            double exponent = (double) (2 * i) / headDim;
            invFreq[i] = (float) (1.0 / Math.pow(ropeTheta, exponent));
        }
        // Precompute cos/sin for all positions up to maxSeqLen
        this.cosTable = new float[maxSeqLen * headDim];
        this.sinTable = new float[maxSeqLen * headDim];
        for (int t = 0; t < maxSeqLen; t++) {
            for (int i = 0; i < halfDim; i++) {
                double angle = invFreq[i] * t;
                int idx = t * headDim + i;
                int dupIdx = t * headDim + (i + halfDim); // duplicate for second half
                cosTable[idx] = (float) Math.cos(angle);
                sinTable[idx] = (float) Math.sin(angle);
                cosTable[dupIdx] = cosTable[idx]; // duplicate
                sinTable[dupIdx] = sinTable[idx];
            }
        }
    }

    public BitNetRope(int maxSeqLen) {
        this(maxSeqLen, DEFAULT_HEAD_DIM, DEFAULT_ROPE_THETA);
    }

    public int headDim() {
        return headDim;
    }

    /**
     * Apply RoPE rotation to query and key tensors in-place.
     *
     * @param qk query or key tensor [batch, n_heads, seq, head_dim] (modified)
     * @param batch batch size
     * @param n_heads number of attention heads
     * @param seq sequence length
     * @param positionIds [batch, seq] position indices (use 0..seq-1 for fresh)
     */
    public void applyInPlace(float[] qk, int batch, int n_heads, int seq, int[] positionIds) {
        if (qk == null) throw new IllegalArgumentException("null qk");
        if (positionIds == null) throw new IllegalArgumentException("null positionIds");
        if (positionIds.length != batch * seq) {
            throw new IllegalArgumentException("positionIds length mismatch");
        }
        int halfDim = headDim / 2;
        for (int b = 0; b < batch; b++) {
            for (int h = 0; h < n_heads; h++) {
                for (int s = 0; s < seq; s++) {
                    int pos = positionIds[b * seq + s];
                    int base = ((b * n_heads + h) * seq + s) * headDim;
                    int cosBase = pos * headDim;
                    for (int d = 0; d < halfDim; d++) {
                        float c = cosTable[cosBase + d];
                        float s_val = sinTable[cosBase + d];
                        float x1 = qk[base + d];
                        float x2 = qk[base + d + halfDim];
                        // rotate_half: [-x2, x1]
                        qk[base + d] = x1 * c - x2 * s_val;
                        qk[base + d + halfDim] = x2 * c + x1 * s_val;
                    }
                }
            }
        }
    }

    /**
     * Create standard position_ids [0, 1, ..., seq-1] for each batch.
     */
    public static int[] defaultPositionIds(int batch, int seq) {
        int[] ids = new int[batch * seq];
        for (int b = 0; b < batch; b++) {
            for (int s = 0; s < seq; s++) {
                ids[b * seq + s] = s;
            }
        }
        return ids;
    }
}
