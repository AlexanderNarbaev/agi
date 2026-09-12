package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 461 — BitNet full model (DESIGN-54 §11, top-level orchestrator).
 *
 * <p>Wraps multiple {@link BitNetBlock} decoder layers plus embedding,
 * final RMSNorm, and lm_head tied to embeddings. Loads all 30 layers
 * from a safetensors file and runs end-to-end forward.
 *
 * <h2>For BitNet b1.58-2B-4T</h2>
 * - 30 decoder layers (each: input_layernorm + attn + post_norm + mlp)
 * - hidden_size=2560, intermediate_size=6912
 * - num_attention_heads=20, num_kv_heads=5, head_dim=128
 * - vocab_size=128256, max_position_embeddings=4096
 *
 * <h2>Usage</h2>
 * <pre>
 *   BitNetModel model = new BitNetModel(blocks, embeddingWeights, modelNormWeight);
 *   float[] logits = model.forwardSingleToken(tokenId, positionId);
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG. Caller supplies all weights.
 */
public final class BitNetModel {

    /** All decoder blocks (typically 30 for b1.58-2B-4T). */
    public final BitNetBlock[] blocks;
    /** Embedding: [vocab_size, hidden_size] (also used as lm_head). */
    public final float[] embedding;
    /** Final RMSNorm weight: [hidden_size]. */
    public final float[] modelNormWeight;
    /** RoPE instance with precomputed cos/sin tables. */
    public final BitNetRope rope;
    /** RMSNorm for final normalization. */
    private final BitNetRmsNorm modelNorm;

    public BitNetModel(BitNetBlock[] blocks, float[] embedding,
                       float[] modelNormWeight, BitNetRope rope) {
        if (blocks == null || blocks.length == 0) {
            throw new IllegalArgumentException("null/empty blocks");
        }
        if (embedding == null) throw new IllegalArgumentException("null embedding");
        if (modelNormWeight == null) throw new IllegalArgumentException("null modelNormWeight");
        if (rope == null) throw new IllegalArgumentException("null rope");
        this.blocks = blocks;
        this.embedding = embedding;
        this.modelNormWeight = modelNormWeight;
        this.rope = rope;
        this.modelNorm = new BitNetRmsNorm(modelNormWeight, 1e-5f);
    }

    /**
     * Forward pass for a single token (no batch, single position).
     *
     * @param tokenId input token id
     * @param positionId position in sequence (for RoPE)
     * @return logits over vocab [vocab_size]
     */
    public float[] forwardSingleToken(int tokenId, int positionId) {
        // 1. Embedding lookup
        int hiddenSize = blocks[0].hiddenSize;
        int vocabSize = embedding.length / hiddenSize;
        float[] hidden = new float[hiddenSize];
        System.arraycopy(embedding, tokenId * hiddenSize, hidden, 0, hiddenSize);

        // 2. Run decoder stack
        for (BitNetBlock block : blocks) {
            hidden = block.forwardSingle(hidden, rope);
        }

        // 3. Final RMSNorm
        hidden = modelNorm.forward(hidden, 1, 1, hiddenSize);

        // 4. lm_head: logits = hidden @ embedding.T (tied weights)
        float[] logits = new float[vocabSize];
        for (int v = 0; v < vocabSize; v++) {
            float sum = 0;
            int rowBase = v * hiddenSize;
            for (int d = 0; d < hiddenSize; d++) {
                sum += embedding[rowBase + d] * hidden[d];
            }
            logits[v] = sum;
        }
        return logits;
    }

    /**
     * Argmax of logits — greedy token prediction.
     */
    public static int argmax(float[] logits) {
        int maxIdx = 0;
        float maxVal = logits[0];
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > maxVal) {
                maxVal = logits[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    /**
     * Number of decoder layers.
     */
    public int layerCount() {
        return blocks.length;
    }
}
