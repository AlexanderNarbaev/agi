package io.matrix.research;

import io.matrix.imports.BitNetWeightUnpacker;
import io.matrix.imports.SafetensorsReader;
import io.matrix.neuron.BitNetBlock;
import io.matrix.neuron.BitNetRope;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 48: Real BitNet b1.58-4T one decoder layer forward with real weights.
 *
 * <p>Loads actual weights from layer 0 of microsoft/bitnet-b1.58-2B-4T,
 * unpacks via BitNetWeightUnpacker, and runs forward through BitNetBlock
 * (single-token inference, no causal mask needed).
 *
 * <p>This validates end-to-end:
 * - SafetensorsReader reads real bf16 / uint8 tensors
 * - BitNetWeightUnpacker dequantizes uint8 → FP32
 * - BitNetBlock runs RMSNorm + attention (with GQA) + MLP (with relu2)
 */
class BitNetBlockRealForwardTest {

    private static final String MODEL_PATH =
            "/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/"
                    + "04c3b9ad9361b824064a1f25ea60a8be9599b127/model.safetensors";

    @Test
    void forwardSingleLayer0WithRealWeights() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        try (FileChannel ch = FileChannel.open(Path.of(MODEL_PATH),
                StandardOpenOption.READ)) {
            SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));

            // Load all 10 weights for layer 0 (bf16 stored in float[] via Tensor.data)
            // For uint8 tensors (BitLinear weights), use loadTensorBytes + unpack
            // For bf16 tensors (layernorm weights), loadTensor returns float[]

            // Layer norms (bf16)
            float[] inputNorm = reader.loadTensor(ch, header,
                    "model.layers.0.input_layernorm.weight").data();
            float[] postNorm = reader.loadTensor(ch, header,
                    "model.layers.0.post_attention_layernorm.weight").data();
            float[] attnSubNorm = reader.loadTensor(ch, header,
                    "model.layers.0.self_attn.attn_sub_norm.weight").data();
            float[] ffnSubNorm = reader.loadTensor(ch, header,
                    "model.layers.0.mlp.ffn_sub_norm.weight").data();

            // BitLinear weights (uint8 packed + bf16 scale)
            float[] qWeight = unpackLayer0(reader, ch, header, "self_attn.q_proj");
            float[] kWeight = unpackLayer0(reader, ch, header, "self_attn.k_proj");
            float[] vWeight = unpackLayer0(reader, ch, header, "self_attn.v_proj");
            float[] oWeight = unpackLayer0(reader, ch, header, "self_attn.o_proj");
            float[] gateWeight = unpackLayer0(reader, ch, header, "mlp.gate_proj");
            float[] upWeight = unpackLayer0(reader, ch, header, "mlp.up_proj");
            float[] downWeight = unpackLayer0(reader, ch, header, "mlp.down_proj");

            System.out.printf("[BitNet block] input_norm: %d elements%n", inputNorm.length);
            System.out.printf("[BitNet block] q_weight: %d, k_weight: %d, v_weight: %d, o_weight: %d%n",
                    qWeight.length, kWeight.length, vWeight.length, oWeight.length);
            System.out.printf("[BitNet block] gate: %d, up: %d, down: %d%n",
                    gateWeight.length, upWeight.length, downWeight.length);

            // Construct BitNetBlock (BitNet b1.58-2B-4T config: 20 heads, 5 kv, head_dim 128, intermediate 6912)
            BitNetBlock block = new BitNetBlock(
                    20, 5, 128, 6912,
                    inputNorm, postNorm, attnSubNorm, ffnSubNorm,
                    qWeight, kWeight, vWeight, oWeight,
                    gateWeight, upWeight, downWeight);

            // Single-token input (e.g., embedding for token 0)
            float[] input = new float[2560];
            java.util.Random rng = new java.util.Random(42);
            for (int i = 0; i < 2560; i++) {
                input[i] = (float) rng.nextGaussian() * 0.1f;
            }

            BitNetRope rope = new BitNetRope(4096);

            long start = System.nanoTime();
            float[] output = block.forwardSingle(input, rope);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            System.out.printf("[BitNet block] forward: %d ms, output[0..2]=[%.4f, %.4f, %.4f]%n",
                    elapsedMs, output[0], output[1], output[2]);

            assertThat(output).hasSize(2560);
            for (float v : output) {
                assertThat(Float.isFinite(v)).isTrue();
            }
        }
    }

    /**
     * Helper: unpack one BitLinear weight (uint8 + bf16 scale).
     */
    private static float[] unpackLayer0(SafetensorsReader reader, FileChannel ch,
                                          SafetensorsReader.Header header,
                                          String name) throws IOException {
        String fullName = "model.layers.0." + name + ".weight";
        String scaleName = "model.layers.0." + name + ".weight_scale";
        byte[] packed = reader.loadTensorBytes(ch, header, fullName);
        float scale = reader.loadBf16Scale(ch, header, scaleName);
        System.out.printf("[debug unpack] %s: %d packed bytes, scale=%.4f%n",
                name, packed.length, scale);
        // Determine output dim from packed length and an expected in_dim
        // For layer 0: q/o/intermediate = 2560 in, gate/up = 2560 in
        // Output dims: q=2560, k=v=640, o=2560, gate=up=6912, down=2560
        int outDim;
        int inDim = 2560;
        if (name.contains("k_proj") || name.contains("v_proj")) {
            outDim = 640;
        } else if (name.contains("gate_proj") || name.contains("up_proj")) {
            outDim = 6912;
        } else if (name.contains("down_proj")) {
            outDim = 2560;
            inDim = 6912;
        } else {
            outDim = 2560; // q, o
        }
        return BitNetWeightUnpacker.unpack(packed, scale, outDim, inDim);
    }
}
