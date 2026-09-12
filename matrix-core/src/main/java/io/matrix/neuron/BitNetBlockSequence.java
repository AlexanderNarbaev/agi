package io.matrix.neuron;

import java.util.List;

/**
 * RUN 465 — Sequence forward with causal mask (DESIGN-54 §15).
 *
 * <p>Multi-token forward pass through one BitNetBlock. Handles proper
 * causal attention mask: position i can only attend to positions [0, i].
 *
 * <h2>Algorithm</h2>
 * <pre>
 *   Input: hidden_states [seq_len, hidden_size]
 *   1. RMSNorm (input_layernorm): output [seq_len, hidden_size]
 *   2. Q/K/V projections: Q [seq_len, n_heads*head_dim], K/V [seq_len, n_kv_heads*head_dim]
 *   3. Reshape: Q [n_heads, seq_len, head_dim], K/V [n_kv_heads, seq_len, head_dim]
 *   4. Apply RoPE
 *   5. GQA repeat: K/V [n_heads, seq_len, head_dim]
 *   6. Attention: for each query position i, compute scores vs keys [0..i],
 *      apply softmax, weighted sum of values [0..i]
 *   7. Reshape to [seq_len, n_heads*head_dim]
 *   8. attn_sub_norm + o_proj → output [seq_len, hidden_size]
 *   9. Residual add
 *  10. RMSNorm (post_attention_layernorm) + MLP → output [seq_len, hidden_size]
 *  11. Residual add
 *  12. Return output
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock.
 */
public final class BitNetBlockSequence {

    /** Run one decoder block on a sequence of [seq_len, hidden_size] tokens. */
    public static float[][] forwardSequence(
            BitNetBlock block,
            float[][] hiddenStates, // [seq_len][hidden_size]
            BitNetRope rope,
            int positionStart) {
        if (block == null) throw new IllegalArgumentException("null block");
        if (hiddenStates == null) throw new IllegalArgumentException("null hiddenStates");
        if (hiddenStates.length == 0) throw new IllegalArgumentException("empty hiddenStates");

        int seqLen = hiddenStates.length;
        int hiddenSize = block.hiddenSize;
        int intermediateSize = block.intermediateSize;
        int nHeads = block.numHeads;
        int nKvHeads = block.numKvHeads;
        int headDim = block.headDim;
        int kvGroupSize = nHeads / nKvHeads;

        // ===== Block 1: Self-Attention =====
        float[][] residual = clone2d(hiddenStates);
        // RMSNorm
        BitNetRmsNorm inputNorm = new BitNetRmsNorm(block.inputLayernormWeight, 1e-5f);
        float[][] normed = inputNorm.forwardSeq(hiddenStates);

        // Q/K/V projections
        float[][] qAll = matmulBatch(block.qWeight, normed, hiddenSize);
        float[][] kAll = matmulBatch(block.kWeight, normed, nKvHeads * headDim);
        float[][] vAll = matmulBatch(block.vWeight, normed, nKvHeads * headDim);

        // Reshape to (n_heads, seq_len, head_dim) for Q, (n_kv_heads, seq_len, head_dim) for K/V
        float[][][] qHeads = reshape(qAll, nHeads, seqLen, headDim);
        float[][][] kvHeads = reshape(kAll, nKvHeads, seqLen, headDim);
        float[][][] vHeads = reshape(vAll, nKvHeads, seqLen, headDim);

        // Apply RoPE
        int[] posIds = BitNetRope.defaultPositionIds(1, seqLen);
        for (int h = 0; h < nHeads; h++) {
            // Apply per-head: flatten, apply, unflatten
            float[] flat = new float[seqLen * headDim];
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(qHeads[h][s], 0, flat, s * headDim, headDim);
            }
            rope.applyInPlace(flat, 1, 1, seqLen, posIds);
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(flat, s * headDim, qHeads[h][s], 0, headDim);
            }
        }
        for (int h = 0; h < nKvHeads; h++) {
            float[] flatK = new float[seqLen * headDim];
            float[] flatV = new float[seqLen * headDim];
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(kvHeads[h][s], 0, flatK, s * headDim, headDim);
                System.arraycopy(vHeads[h][s], 0, flatV, s * headDim, headDim);
            }
            rope.applyInPlace(flatK, 1, 1, seqLen, posIds);
            rope.applyInPlace(flatV, 1, 1, seqLen, posIds);
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(flatK, s * headDim, kvHeads[h][s], 0, headDim);
                System.arraycopy(flatV, s * headDim, vHeads[h][s], 0, headDim);
            }
        }

        // GQA: expand K/V from n_kv_heads to n_heads
        float[][][] kFull = new float[nHeads][seqLen][headDim];
        float[][][] vFull = new float[nHeads][seqLen][headDim];
        for (int h = 0; h < nHeads; h++) {
            int kvIdx = h / kvGroupSize;
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(kvHeads[kvIdx][s], 0, kFull[h][s], 0, headDim);
                System.arraycopy(vHeads[kvIdx][s], 0, vFull[h][s], 0, headDim);
            }
        }

        // Attention with causal mask
        float[][] attnOut = new float[seqLen][hiddenSize]; // [seq_len][n_heads*head_dim]
        float scale = (float) (1.0 / Math.sqrt(headDim));
        for (int s = 0; s < seqLen; s++) {
            // For each head
            float[] headOut = new float[nHeads * headDim];
            for (int h = 0; h < nHeads; h++) {
                // Compute attention weights for position s
                float[] scores = new float[s + 1]; // attend to positions 0..s
                float maxScore = -Float.MAX_VALUE;
                for (int k = 0; k <= s; k++) {
                    float dot = 0;
                    for (int d = 0; d < headDim; d++) {
                        dot += qHeads[h][s][d] * kFull[h][k][d];
                    }
                    dot *= scale;
                    scores[k] = dot;
                    if (dot > maxScore) maxScore = dot;
                }
                // Softmax
                float sum = 0;
                for (int k = 0; k <= s; k++) {
                    scores[k] = (float) Math.exp(scores[k] - maxScore);
                    sum += scores[k];
                }
                float invSum = 1.0f / sum;
                for (int k = 0; k <= s; k++) scores[k] *= invSum;
                // Weighted sum of V
                float[] headV = new float[headDim];
                for (int k = 0; k <= s; k++) {
                    for (int d = 0; d < headDim; d++) {
                        headV[d] += scores[k] * vFull[h][k][d];
                    }
                }
                System.arraycopy(headV, 0, headOut, h * headDim, headDim);
            }
            attnOut[s] = headOut;
        }

        // attn_sub_norm + o_proj + residual
        BitNetRmsNorm attnSubNorm = new BitNetRmsNorm(block.attnSubNormWeight, 1e-5f);
        float[][] subNormed = attnSubNorm.forwardSeq(attnOut);
        float[][] projected = matmulBatch(block.oWeight, subNormed, hiddenSize);
        for (int s = 0; s < seqLen; s++) {
            for (int d = 0; d < hiddenSize; d++) {
                projected[s][d] += residual[s][d];
            }
        }

        // ===== Block 2: MLP =====
        float[][] residual2 = clone2d(projected);
        BitNetRmsNorm postNorm = new BitNetRmsNorm(block.postLayernormWeight, 1e-5f);
        float[][] normed2 = postNorm.forwardSeq(projected);

        float[][] gate = matmulBatch(block.gateWeight, normed2, intermediateSize);
        float[][] up = matmulBatch(block.upWeight, normed2, intermediateSize);
        // relu2
        float[][] hidden = new float[seqLen][intermediateSize];
        for (int s = 0; s < seqLen; s++) {
            for (int d = 0; d < intermediateSize; d++) {
                float g = Math.max(0.0f, gate[s][d]);
                hidden[s][d] = g * g * up[s][d];
            }
        }
        BitNetRmsNorm ffnSubNorm = new BitNetRmsNorm(block.ffnSubNormWeight, 1e-5f);
        float[][] subNormed2 = ffnSubNorm.forwardSeq(hidden);
        float[][] mlpOut = matmulBatch(block.downWeight, subNormed2, hiddenSize);
        for (int s = 0; s < seqLen; s++) {
            for (int d = 0; d < hiddenSize; d++) {
                mlpOut[s][d] += residual2[s][d];
            }
        }
        return mlpOut;
    }

    private static float[][] clone2d(float[][] a) {
        float[][] c = new float[a.length][];
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i].clone();
        }
        return c;
    }

    /**
     * y = W @ X for batched X [seq_len][in_dim]. W is [out, in] row-major.
     */
    private static float[][] matmulBatch(float[] weights, float[][] x, int outDim) {
        int seqLen = x.length;
        int inDim = x[0].length;
        float[][] y = new float[seqLen][outDim];
        for (int s = 0; s < seqLen; s++) {
            for (int i = 0; i < outDim; i++) {
                float sum = 0;
                int rowBase = i * inDim;
                for (int j = 0; j < inDim; j++) {
                    sum += weights[rowBase + j] * x[s][j];
                }
                y[s][i] = sum;
            }
        }
        return y;
    }

    /**
     * Reshape [seq_len][features] to [heads][seq_len][head_dim].
     */
    private static float[][][] reshape(float[][] x, int heads, int seqLen, int headDim) {
        float[][][] result = new float[heads][seqLen][headDim];
        for (int h = 0; h < heads; h++) {
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(x[s], h * headDim, result[h][s], 0, headDim);
            }
        }
        return result;
    }
}
