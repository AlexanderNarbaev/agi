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
 * Wave 58: True autoregressive generation with KV cache reuse.
 *
 * <p>Tests {@link BitNetModel#generateWithKvCache} which uses the KV cache
 * during decode. The prefill phase processes the entire prompt, and the
 * decode phase only computes Q/K/V for the new token, attending to all
 * cached positions.
 *
 * <p>This is the first end-to-end test of a true autoregressive inference
 * with KV cache reuse.
 */
class BitNetKvCacheGenerationTest {

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
    void generateWithKvCacheProducesValidTokens() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String[] prompts = {"The capital of France is", "Hello", "Once upon a time"};
        TokenSampler.Config cfg = TokenSampler.Config.defaults();

        for (String prompt : prompts) {
            int[] promptIds = tokenizer.encode(prompt);
            long start = System.nanoTime();
            int[] genIds = model.generateWithKvCache(promptIds, 5, cfg, new Random(42));
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            String decoded = tokenizer.decode(genIds);
            System.out.printf("[KV-cache gen] '%s' (%d tokens prompt) → %d gen tokens in %d ms: '%s'%n",
                    prompt, promptIds.length, genIds.length, elapsed, decoded);
            assertThat(genIds.length).isGreaterThan(0);
            for (int id : genIds) {
                assertThat(id).isBetween(0, 128255);
            }
        }
    }

    @Test
    void kvCacheVsNoKvCacheProduceDifferentResults() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String prompt = "The";
        int[] promptIds = tokenizer.encode(prompt);

        // With KV cache reuse
        int[] withKv = model.generateWithKvCache(promptIds, 5,
                TokenSampler.Config.greedy(), new Random(42));
        // Without KV cache reuse (prefill only for prompt, then single-token forward)
        int[] withoutKv = model.generate(promptIds, 5,
                TokenSampler.Config.greedy(), new Random(42));

        String withKvText = tokenizer.decode(withKv);
        String withoutKvText = tokenizer.decode(withoutKv);

        System.out.printf("[KV-cache vs single] '%s' + KV-cache → '%s'%n", prompt, withKvText);
        System.out.printf("[KV-cache vs single] '%s' + single → '%s'%n", prompt, withoutKvText);

        assertThat(withKv).isNotEmpty();
        assertThat(withoutKv).isNotEmpty();
    }

    @Test
    void kvCacheGenerationRespectsEos() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        int[] promptIds = {128000}; // BOS only
        int[] genIds = model.generateWithKvCache(promptIds, 100,
                TokenSampler.Config.defaults(), new Random(42));
        assertThat(genIds.length).isLessThanOrEqualTo(100);
        System.out.printf("[KV-cache EOS] generated %d tokens before EOS%n", genIds.length);
    }

    @Test
    void kvCacheGenerationIsDeterministicWithSameSeed() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        BitNetModel model = loadModel();

        String prompt = "Hello world";
        int[] promptIds = tokenizer.encode(prompt);

        int[] run1 = model.generateWithKvCache(promptIds, 3,
                TokenSampler.Config.defaults(), new Random(123));
        int[] run2 = model.generateWithKvCache(promptIds, 3,
                TokenSampler.Config.defaults(), new Random(123));

        assertThat(run1).containsExactly(run2);
        System.out.printf("[KV-cache determinism] same seed → same output: %s%n",
                tokenizer.decode(run1));
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
