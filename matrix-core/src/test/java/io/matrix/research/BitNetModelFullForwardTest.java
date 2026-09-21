package io.matrix.research;

import io.matrix.imports.BitNetWeightUnpacker;
import io.matrix.imports.SafetensorsReader;
import io.matrix.neuron.BitNetBlock;
import io.matrix.neuron.BitNetModel;
import io.matrix.neuron.BitNetRope;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 49: Real BitNet 2B FULL MODEL forward — all 30 layers with real weights.
 *
 * <p>Loads all 30 decoder layers + embedding + final norm from
 * microsoft/bitnet-b1.58-2B-4T and runs end-to-end forward for one token.
 *
 * <p>This is the closest we can get to real inference without implementing
 * autoregressive generation. The single-token forward computes:
 * - token embedding lookup
 * - 30 decoder layers (each: RMSNorm + attention + MLP with sub_norms)
 * - final RMSNorm
 * - lm_head (tied to embeddings)
 * - logits [vocab_size=128256]
 */
class BitNetModelFullForwardTest {

    private static final String MODEL_PATH =
            "/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/"
                    + "04c3b9ad9361b824064a1f25ea60a8be9599b127/model.safetensors";

    private static final int NUM_LAYERS = 30;
    private static final int HIDDEN = 2560;
    private static final int INTERMEDIATE = 6912;
    private static final int N_HEADS = 20;
    private static final int N_KV_HEADS = 5;
    private static final int HEAD_DIM = 128;
    private static final int VOCAB_SIZE = 128256;

    @Test
    void fullModelForwardSingleToken() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        try (FileChannel ch = FileChannel.open(Path.of(MODEL_PATH),
                StandardOpenOption.READ)) {
            SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));

            // Load embeddings (bf16) — also serves as lm_head
            float[] embedding = reader.loadTensor(ch, header,
                    "model.embed_tokens.weight").data();
            assertThat(embedding).hasSize(VOCAB_SIZE * HIDDEN);

            // Load final RMSNorm weight (bf16)
            float[] modelNorm = reader.loadTensor(ch, header,
                    "model.norm.weight").data();
            assertThat(modelNorm).hasSize(HIDDEN);

            // Build 30 decoder blocks
            BitNetBlock[] blocks = new BitNetBlock[NUM_LAYERS];
            for (int i = 0; i < NUM_LAYERS; i++) {
                blocks[i] = loadBlock(reader, ch, header, i);
                System.out.printf("[BitNet model] loaded layer %d%n", i);
            }

            BitNetModel model = new BitNetModel(blocks, embedding, modelNorm,
                    new BitNetRope(4096));
            System.out.printf("[BitNet model] %d layers, vocab=%d, hidden=%d%n",
                    model.layerCount(), VOCAB_SIZE, HIDDEN);

            // Forward a single token (token 0 = BOS per config)
            long start = System.nanoTime();
            float[] logits = model.forwardSingleToken(128000, 0);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(logits).hasSize(VOCAB_SIZE);
            int predicted = BitNetModel.argmax(logits);
            System.out.printf("[BitNet model] forward: %d ms, argmax=%d (logit=%.4f)%n",
                    elapsedMs, predicted, logits[predicted]);

            // Verify output is non-trivial
            for (float v : logits) {
                assertThat(Float.isFinite(v)).isTrue();
            }
            assertThat(Math.abs(logits[predicted])).isGreaterThan(0.0f);
        }
    }

    /**
     * Helper: load one BitNetBlock with all 10 weights.
     */
    private static BitNetBlock loadBlock(SafetensorsReader reader, FileChannel ch,
                                          SafetensorsReader.Header header, int idx)
            throws IOException {
        String prefix = "model.layers." + idx + ".";

        // Layer norms (bf16)
        float[] inputNorm = reader.loadTensor(ch, header,
                prefix + "input_layernorm.weight").data();
        float[] postNorm = reader.loadTensor(ch, header,
                prefix + "post_attention_layernorm.weight").data();
        float[] attnSubNorm = reader.loadTensor(ch, header,
                prefix + "self_attn.attn_sub_norm.weight").data();
        float[] ffnSubNorm = reader.loadTensor(ch, header,
                prefix + "mlp.ffn_sub_norm.weight").data();

        // BitLinear weights (uint8 + bf16 scale)
        float[] qWeight = unpack(reader, ch, header, prefix + "self_attn.q_proj");
        float[] kWeight = unpack(reader, ch, header, prefix + "self_attn.k_proj");
        float[] vWeight = unpack(reader, ch, header, prefix + "self_attn.v_proj");
        float[] oWeight = unpack(reader, ch, header, prefix + "self_attn.o_proj");
        float[] gateWeight = unpack(reader, ch, header, prefix + "mlp.gate_proj");
        float[] upWeight = unpack(reader, ch, header, prefix + "mlp.up_proj");
        float[] downWeight = unpack(reader, ch, header, prefix + "mlp.down_proj");

        return new BitNetBlock(
                N_HEADS, N_KV_HEADS, HEAD_DIM, INTERMEDIATE,
                inputNorm, postNorm, attnSubNorm, ffnSubNorm,
                qWeight, kWeight, vWeight, oWeight,
                gateWeight, upWeight, downWeight);
    }

    private static float[] unpack(SafetensorsReader reader, FileChannel ch,
                                   SafetensorsReader.Header header, String baseName)
            throws IOException {
        byte[] packed = reader.loadTensorBytes(ch, header, baseName + ".weight");
        float scale = reader.loadBf16Scale(ch, header, baseName + ".weight_scale");
        // Determine outDim from baseName pattern
        int outDim;
        int inDim = 2560;
        if (baseName.contains("k_proj") || baseName.contains("v_proj")) {
            outDim = 640;
        } else if (baseName.contains("gate_proj") || baseName.contains("up_proj")) {
            outDim = 6912;
        } else if (baseName.contains("down_proj")) {
            outDim = 2560;
            inDim = 6912;
        } else {
            outDim = 2560; // q, o
        }
        return BitNetWeightUnpacker.unpack(packed, scale, outDim, inDim);
    }
}
