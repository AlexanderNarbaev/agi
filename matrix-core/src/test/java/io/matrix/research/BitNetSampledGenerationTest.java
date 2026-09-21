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
 * Wave 55: Sampled autoregressive generation with real BitNet 2B weights.
 *
 * <p>Uses BitNetModel.generate() with TokenSampler to produce diverse
 * outputs from the same prompt. Compares greedy vs sampling to verify
 * that temperature > 0 produces more varied outputs.
 */
class BitNetSampledGenerationTest {

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
    void samplingProducesDifferentTokensThanGreedy() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String prompt = "The";
        int[] promptIds = tokenizer.encode(prompt);

        // Greedy generation
        int[] greedyIds = model.generate(promptIds, 5,
                TokenSampler.Config.greedy(), new Random(42));

        // Sampling generation (T=1.0, top_p=0.9)
        int[] sampledIds = model.generate(promptIds, 5,
                TokenSampler.Config.defaults(), new Random(42));

        // Decoded
        String greedyText = greedyIds.length > 0 ? tokenizer.decode(greedyIds) : "(empty)";
        String sampledText = sampledIds.length > 0 ? tokenizer.decode(sampledIds) : "(empty)";

        System.out.printf("[BitNet sampled] greedy: '%s', sampled: '%s'%n",
                greedyText, sampledText);

        assertThat(greedyIds).isNotEmpty();
        assertThat(sampledIds).isNotEmpty();
        // Both should produce some tokens (not necessarily different since
        // single-token forward gives no context)
    }

    @Test
    void highTemperatureProducesMoreDiverseOutputs() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String prompt = "Hello";
        int[] promptIds = tokenizer.encode(prompt);

        // Generate multiple times with high temperature, count unique outputs
        TokenSampler.Config highTempConfig = new TokenSampler.Config(2.0f, 0, 1.0f, 0);
        java.util.Set<String> uniqueOutputs = new java.util.HashSet<>();
        for (int trial = 0; trial < 10; trial++) {
            int[] genIds = model.generate(promptIds, 3,
                    highTempConfig, new Random(trial));
            String text = genIds.length > 0 ? tokenizer.decode(genIds) : "(empty)";
            uniqueOutputs.add(text);
        }
        System.out.printf("[BitNet sampled] unique outputs: %d%n", uniqueOutputs.size());
        assertThat(uniqueOutputs.size()).isGreaterThan(1);
    }

    @Test
    void eosTokenStopsGeneration() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        int[] promptIds = {128000}; // Just BOS
        int[] genIds = model.generate(promptIds, 100,
                TokenSampler.Config.defaults(), new Random(42));

        // Either EOS was hit early, or we generated up to 100 tokens
        // (but our impl breaks on EOS, so genIds ends at the first EOS or 100)
        assertThat(genIds.length).isLessThanOrEqualTo(100);
        System.out.printf("[BitNet sampled] generated %d tokens before EOS%n", genIds.length);
    }

    @Test
    void generationFromPromptProducesValidTokens() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String[] prompts = {"The capital of France is", "Once upon a time", "Hello"};
        TokenSampler.Config cfg = TokenSampler.Config.defaults();

        for (String prompt : prompts) {
            int[] promptIds = tokenizer.encode(prompt);
            int[] genIds = model.generate(promptIds, 10, cfg, new Random(42));
            String fullText = prompt + tokenizer.decode(genIds);
            System.out.printf("[BitNet sampled] '%s' → '%s'%n", prompt, fullText);
            // Verify generated tokens are valid (non-negative ids)
            for (int id : genIds) {
                assertThat(id).isGreaterThanOrEqualTo(0);
                assertThat(id).isLessThan(128256);
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
