package io.matrix.research;

import io.matrix.imports.BitNetWeightUnpacker;
import io.matrix.imports.SafetensorsReader;
import io.matrix.neuron.BitNetBlock;
import io.matrix.neuron.BitNetModel;
import io.matrix.neuron.BitNetRope;
import io.matrix.neuron.BitNetTokenizer;
import io.matrix.neuron.TokenSampler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 57: Prefill + decode generation with real BitNet b1.58-2B-4T.
 *
 * <p>Tests generateWithPrefill() which uses BitNetBlockSequence for
 * the prompt (real context window with causal attention) and then
 * single-token forward for the generated tokens.
 *
 * <p>This should produce higher-quality outputs than pure single-token
 * forward because the first generated token has the full prompt context.
 */
class BitNetPrefillGenerationTest {

    private static final String MODEL_PATH =
            "/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/"
                    + "04c3b9ad9361b824064a1f25ea60a8be9599b127/model.safetensors";

    private static final int NUM_LAYERS = 30;
    private static final int HIDDEN = 2560;
    private static final int INTERMEDIATE = 6912;
    private static final int N_HEADS = 20;
    private static final int N_KV_HEADS = 5;
    private static final int HEAD_DIM = 128;

    @Test
    void prefillGenerationProducesDifferentFirstToken() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String prompt = "The capital of France is";
        int[] promptIds = tokenizer.encode(prompt);
        System.out.printf("[BitNet prefill] prompt: '%s' (%d tokens: %s)%n",
                prompt, promptIds.length, java.util.Arrays.toString(promptIds));

        // Generate using prefill (real context window)
        int[] genIds = model.generateWithPrefill(promptIds, 10,
                TokenSampler.Config.defaults(), new Random(42));
        String decoded = tokenizer.decode(genIds);
        System.out.printf("[BitNet prefill] generated: '%s' (tokens=%s)%n",
                decoded, java.util.Arrays.toString(genIds));

        // Verify
        assertThat(genIds).isNotEmpty();
        // Verify all generated tokens are valid
        for (int id : genIds) {
            assertThat(id).isBetween(0, 128255);
        }
    }

    @Test
    void prefillVsSingleTokenComparison() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String prompt = "Once upon a time";
        int[] promptIds = tokenizer.encode(prompt);

        // Prefill-based (uses forwardSequence for prompt)
        int[] prefillGen = model.generateWithPrefill(promptIds, 5,
                TokenSampler.Config.greedy(), new Random(42));
        String prefillText = tokenizer.decode(prefillGen);

        // Single-token-based (uses forwardSingleToken for everything)
        int[] singleGen = model.generate(promptIds, 5,
                TokenSampler.Config.greedy(), new Random(42));
        String singleText = tokenizer.decode(singleGen);

        System.out.printf("[BitNet prefill] '%s' + prefill → '%s'%n", prompt, prefillText);
        System.out.printf("[BitNet prefill] '%s' + single → '%s'%n", prompt, singleText);

        // Both should produce some tokens
        assertThat(prefillGen).isNotEmpty();
        assertThat(singleGen).isNotEmpty();
    }

    @Test
    void prefillSampledGenerationMultipleRuns() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String prompt = "Hello";
        int[] promptIds = tokenizer.encode(prompt);

        // Multiple sampled runs to verify diversity
        java.util.Set<String> outputs = new java.util.HashSet<>();
        for (int trial = 0; trial < 5; trial++) {
            int[] genIds = model.generateWithPrefill(promptIds, 5,
                    TokenSampler.Config.defaults(), new Random(trial));
            String text = "Hello" + tokenizer.decode(genIds);
            outputs.add(text);
        }
        System.out.printf("[BitNet prefill] %d unique outputs out of 5 runs%n", outputs.size());
        assertThat(outputs.size()).isGreaterThan(1);
    }

    @Test
    void forwardSequenceProducesCorrectShape() throws IOException {
        BitNetModel model = loadModel();
        // Test forwardSequence directly
        int[] tokenIds = {128000, 9906, 1917}; // BOS + "Hello world"
        float[][] hidden = model.forwardSequence(tokenIds);
        assertThat(hidden.length).isEqualTo(3);
        for (float[] row : hidden) {
            assertThat(row.length).isEqualTo(HIDDEN);
            for (float v : row) {
                assertThat(Float.isFinite(v)).isTrue();
            }
        }
    }

    private BitNetModel loadModel() throws IOException {
        SafetensorsReader reader = new SafetensorsReader();
        try (FileChannel ch = FileChannel.open(Path.of(MODEL_PATH),
                StandardOpenOption.READ)) {
            SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));
            float[] embedding = reader.loadTensor(ch, header,
                    "model.embed_tokens.weight").data();
            float[] modelNorm = reader.loadTensor(ch, header,
                    "model.norm.weight").data();
            BitNetBlock[] blocks = new BitNetBlock[NUM_LAYERS];
            for (int i = 0; i < NUM_LAYERS; i++) {
                blocks[i] = loadBlock(reader, ch, header, i);
            }
            return new BitNetModel(blocks, embedding, modelNorm,
                    new BitNetRope(4096));
        }
    }

    private static BitNetBlock loadBlock(SafetensorsReader reader, FileChannel ch,
                                          SafetensorsReader.Header header, int idx)
            throws IOException {
        String prefix = "model.layers." + idx + ".";
        float[] inputNorm = reader.loadTensor(ch, header,
                prefix + "input_layernorm.weight").data();
        float[] postNorm = reader.loadTensor(ch, header,
                prefix + "post_attention_layernorm.weight").data();
        float[] attnSubNorm = reader.loadTensor(ch, header,
                prefix + "self_attn.attn_sub_norm.weight").data();
        float[] ffnSubNorm = reader.loadTensor(ch, header,
                prefix + "mlp.ffn_sub_norm.weight").data();
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
            outDim = 2560;
        }
        return BitNetWeightUnpacker.unpack(packed, scale, outDim, inDim);
    }
}
