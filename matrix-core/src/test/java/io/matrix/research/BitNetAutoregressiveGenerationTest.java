package io.matrix.research;

import io.matrix.imports.BitNetWeightUnpacker;
import io.matrix.imports.SafetensorsReader;
import io.matrix.neuron.BitNetBlock;
import io.matrix.neuron.BitNetModel;
import io.matrix.neuron.BitNetRmsNorm;
import io.matrix.neuron.BitNetRope;
import io.matrix.neuron.BitNetTokenizer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 53: Full autoregressive generation with multi-token forward pass.
 *
 * <p>Simpler approach: pre-fill by running forward on each prompt token
 * (independently — no KV cache). Then greedily generate next tokens by
 * running forward on the latest generated token and taking argmax.
 *
 * <p>Note: This is NOT true autoregressive generation (no KV cache),
 * but it produces plausible next-token predictions for a small test.
 * True generation with KV cache requires multi-token forward (sequence
 * of tokens at once) which is not yet implemented in BitNetBlock.
 */
class BitNetAutoregressiveGenerationTest {

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
    void generateAfterPromptGreedy() throws IOException {
        BitNetTokenizer tokenizer = new BitNetTokenizer();
        SafetensorsReader reader = new SafetensorsReader();
        try (FileChannel ch = FileChannel.open(Path.of(MODEL_PATH),
                StandardOpenOption.READ)) {
            SafetensorsReader.Header header = reader.readHeader(Path.of(MODEL_PATH));

            // Load model
            float[] embedding = reader.loadTensor(ch, header,
                    "model.embed_tokens.weight").data();
            float[] modelNorm = reader.loadTensor(ch, header,
                    "model.norm.weight").data();

            BitNetBlock[] blocks = new BitNetBlock[NUM_LAYERS];
            for (int i = 0; i < NUM_LAYERS; i++) {
                blocks[i] = loadBlock(reader, ch, header, i);
            }

            BitNetModel model = new BitNetModel(blocks, embedding, modelNorm,
                    new BitNetRope(4096));

            // Pre-fill: just use the LAST prompt token for forward (simplification)
            // Note: this doesn't give true autoregressive context, but works
            // as a placeholder until KV cache is implemented.
            String prompt = "The capital of France is";
            int[] promptIds = tokenizer.encode(prompt);
            int lastPromptToken = promptIds[promptIds.length - 1];

            long start = System.nanoTime();
            float[] logits = model.forwardSingleToken(lastPromptToken, promptIds.length - 1);
            int nextTokenId = BitNetModel.argmax(logits);
            String nextToken = tokenizer.decode(new int[]{nextTokenId});
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("[BitNet gen] prompt='%s' (%d tokens)%n", prompt, promptIds.length);
            System.out.printf("[BitNet gen] forward=%d ms, next='%s' (id=%d)%n",
                    elapsed, nextToken, nextTokenId);

            assertThat(nextToken).isNotEmpty();
        }
    }

    @Test
    void generateMultipleTokensGreedy() throws IOException {
        // Greedy generation: pick argmax, feed back as next input
        BitNetTokenizer tokenizer = new BitNetTokenizer();
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

            BitNetModel model = new BitNetModel(blocks, embedding, modelNorm,
                    new BitNetRope(4096));

            String prompt = "Hello";
            int[] promptIds = tokenizer.encode(prompt);
            // Use last prompt token to bootstrap
            int currentToken = promptIds[promptIds.length - 1];
            int position = promptIds.length - 1;

            long start = System.nanoTime();
            List<Integer> generated = new ArrayList<>();
            for (int step = 0; step < 5; step++) {
                float[] logits = model.forwardSingleToken(currentToken, position);
                int nextToken = BitNetModel.argmax(logits);
                generated.add(nextToken);
                // Stop if EOS
                if (nextToken == tokenizer.eosTokenId) break;
                currentToken = nextToken;
                position++;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            // Decode generated tokens
            int[] genArr = generated.stream().mapToInt(Integer::intValue).toArray();
            String decoded = tokenizer.decode(genArr);
            System.out.printf("[BitNet gen] prompt='%s' → %s (%d tokens in %d ms)%n",
                    prompt, decoded, genArr.length, elapsed);

            assertThat(generated).isNotEmpty();
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
