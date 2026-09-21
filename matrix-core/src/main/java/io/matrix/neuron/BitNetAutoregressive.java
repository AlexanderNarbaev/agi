package io.matrix.neuron;

/**
 * RUN 466 — Autoregressive generation with KV cache reuse (DESIGN-54 §16).
 *
 * <p>Extends BitNetBlockSequence to write K, V into a shared {@link KvCache}
 * during sequence forward. For incremental decoding (single-token forward
 * attending to all cached positions), we use the cached K, V instead of
 * recomputing them.
 *
 * <h2>Algorithm</h2>
 * <pre>
 *   Prefill (multi-token forward):
 *     1. Forward Q, K, V projections (full sequence)
 *     2. Apply RoPE
 *     3. Write K, V to cache at positions [positionStart..positionStart+seqLen]
 *     4. Causal attention (each position attends to [0..i])
 *     5. Continue with MLP, residuals, etc.
 *
 *   Decode (single-token forward with cached K, V):
 *     1. Forward Q, K, V projections for the new token only
 *     2. Apply RoPE
 *     3. Append K, V to cache at position positionStart
 *     4. Attention: new Q attends to all cached K, V (causal: only [0..positionStart])
 *     5. Continue with MLP, residuals, etc.
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock.
 */
public final class BitNetAutoregressive {

    private BitNetAutoregressive() {}

    /**
     * Decode one token at position {@code pos}, attending to all previously
     * cached K/V positions [0..pos-1]. Writes the new token's K/V to the cache.
     *
     * @return hidden state [hidden_size] for the new position
     */
    public static float[] decodeStep(
            BitNetBlock block,
            float[] hiddenState, // [hidden_size] pre-norm input
            KvCache cache,
            int layerIdx,
            BitNetRope rope,
            int pos) {
        int hiddenSize = block.hiddenSize;
        int intermediateSize = block.intermediateSize;
        int nHeads = block.numHeads;
        int nKvHeads = block.numKvHeads;
        int headDim = block.headDim;
        int kvGroupSize = nHeads / nKvHeads;

        // ===== Block 1: Self-Attention =====
        float[] residual = hiddenState.clone();
        // RMSNorm (input_layernorm)
        BitNetRmsNorm inputNorm = new BitNetRmsNorm(block.inputLayernormWeight, 1e-5f);
        float[] normed = inputNorm.forward(hiddenState, 1, 1, hiddenSize);

        // Q/K/V projections (single token)
        float[] q = matmul(block.qWeight, normed, nHeads * headDim);
        float[] k = matmul(block.kWeight, normed, nKvHeads * headDim);
        float[] v = matmul(block.vWeight, normed, nKvHeads * headDim);
        if (k.length != nKvHeads * headDim) {
            throw new IllegalStateException("K length " + k.length
                    + " != expected " + (nKvHeads * headDim)
                    + "; kvWeight length=" + block.kWeight.length
                    + ", inDim=" + normed.length
                    + ", numKvHeads=" + nKvHeads + ", headDim=" + headDim);
        }

        // Apply RoPE for position pos
        int[] posIds = {pos};
        rope.applyInPlace(q, 1, 1, 1, posIds);
        rope.applyInPlace(k, 1, 1, 1, posIds);
        rope.applyInPlace(v, 1, 1, 1, posIds);

        // Write K, V to cache
        if (k.length != cache.numKvHeads * cache.headDim) {
            throw new IllegalStateException("K length " + k.length
                    + " != expected " + (cache.numKvHeads * cache.headDim));
        }
        cache.append(layerIdx, k, v);

        // Attention: new Q attends to all cached K, V (positions 0..cache.seqLength-1)
        float[] headOut = new float[nHeads * headDim];
        float scale = (float) (1.0 / Math.sqrt(headDim));
        int cacheLen = cache.seqLength();

        for (int h = 0; h < nHeads; h++) {
            // Compute scores vs all cached positions
            float[] scores = new float[cacheLen];
            float maxScore = -Float.MAX_VALUE;
            for (int kPos = 0; kPos < cacheLen; kPos++) {
                float[] kPos_vec = cache.kSlot(layerIdx, kPos);
                int kvIdx = h / kvGroupSize;
                float dot = 0;
                for (int d = 0; d < headDim; d++) {
                    dot += q[h * headDim + d] * kPos_vec[d];
                }
                dot *= scale;
                scores[kPos] = dot;
                if (dot > maxScore) maxScore = dot;
            }
            // Softmax
            float sum = 0;
            for (int i = 0; i < cacheLen; i++) {
                scores[i] = (float) Math.exp(scores[i] - maxScore);
                sum += scores[i];
            }
            float invSum = 1.0f / sum;
            for (int i = 0; i < cacheLen; i++) scores[i] *= invSum;
            // Weighted sum of V
            float[] headV = new float[headDim];
            for (int vPos = 0; vPos < cacheLen; vPos++) {
                float[] vPos_vec = cache.vSlot(layerIdx, vPos);
                for (int d = 0; d < headDim; d++) {
                    headV[d] += scores[vPos] * vPos_vec[d];
                }
            }
            System.arraycopy(headV, 0, headOut, h * headDim, headDim);
        }

        // attn_sub_norm + o_proj + residual
        BitNetRmsNorm attnSubNorm = new BitNetRmsNorm(block.attnSubNormWeight, 1e-5f);
        float[] subNormed = attnSubNorm.forward(headOut, 1, 1, hiddenSize);
        float[] oProj = matmul(block.oWeight, subNormed, hiddenSize);
        for (int d = 0; d < hiddenSize; d++) {
            oProj[d] += residual[d];
        }

        // ===== Block 2: MLP =====
        float[] residual2 = oProj.clone();
        BitNetRmsNorm postNorm = new BitNetRmsNorm(block.postLayernormWeight, 1e-5f);
        float[] normed2 = postNorm.forward(oProj, 1, 1, hiddenSize);

        float[] gate = matmul(block.gateWeight, normed2, intermediateSize);
        float[] up = matmul(block.upWeight, normed2, intermediateSize);
        float[] hidden = new float[intermediateSize];
        for (int d = 0; d < intermediateSize; d++) {
            float g = Math.max(0.0f, gate[d]);
            hidden[d] = g * g * up[d];
        }
        BitNetRmsNorm ffnSubNorm = new BitNetRmsNorm(block.ffnSubNormWeight, 1e-5f);
        float[] subNormed2 = ffnSubNorm.forward(hidden, 1, 1, intermediateSize);
        float[] downProj = matmul(block.downWeight, subNormed2, hiddenSize);
        for (int d = 0; d < hiddenSize; d++) {
            downProj[d] += residual2[d];
        }
        return downProj;
    }

    private static float[] matmul(float[] weights, float[] x, int outDim) {
        int inDim = x.length;
        float[] y = new float[outDim];
        for (int i = 0; i < outDim; i++) {
            float sum = 0;
            int rowBase = i * inDim;
            for (int j = 0; j < inDim; j++) {
                sum += weights[rowBase + j] * x[j];
            }
            y[i] = sum;
        }
        return y;
    }
}
