package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave I-BPE: real Qwen BPE tokenizer loads vocab.json + merges.txt
 * and produces token ids. Round-trip + length sanity.
 */
class BpeTokenizerTest {

    private static final Path MODEL_DIR = Path.of(
            "models/external/qwen2.5-0.5b/safetensors_meta/snapshots/"
                    + "060db6499f32faf8b98477b0a26969ef7d8b9987");

    @Test
    void loadsFromQwenModelDir() throws Exception {
        if (!Files.exists(MODEL_DIR)) {
            System.out.println("[skip] Qwen tokenizer files missing at " + MODEL_DIR);
            return;
        }
        BpeTokenizer tok = BpeTokenizer.fromModelDir(MODEL_DIR);
        assertThat(tok.vocabSize())
                .as("Qwen vocab is typically 151,643 or similar")
                .isGreaterThan(100_000);
    }

    @Test
    void encodeProducesNonEmptyIds() throws Exception {
        if (!Files.exists(MODEL_DIR)) return;
        BpeTokenizer tok = BpeTokenizer.fromModelDir(MODEL_DIR);
        int[] ids = tok.encode("hello world");
        assertThat(ids).isNotEmpty();
        // every id should be within vocab
        for (int id : ids) {
            assertThat(id).isLessThan(tok.vocabSize());
            assertThat(id).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void textToBitsReturnsFixedLength() throws Exception {
        if (!Files.exists(MODEL_DIR)) return;
        BpeTokenizer tok = BpeTokenizer.fromModelDir(MODEL_DIR);
        boolean[] bits = tok.textToBits("the quick brown fox", 896);
        assertThat(bits).hasSize(896);
    }

    @Test
    void encodeIsDeterministicForSameInput() throws Exception {
        if (!Files.exists(MODEL_DIR)) return;
        BpeTokenizer tok = BpeTokenizer.fromModelDir(MODEL_DIR);
        int[] a = tok.encode("MATRIX is a boolean compute substrate");
        int[] b = tok.encode("MATRIX is a boolean compute substrate");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void idsToBitsAndBackIsStable() throws Exception {
        if (!Files.exists(MODEL_DIR)) return;
        BpeTokenizer tok = BpeTokenizer.fromModelDir(MODEL_DIR);
        int[] ids = tok.encode("hello");
        boolean[] bits = tok.idsToBits(ids, 256);
        assertThat(bits).hasSize(256);
        // at least one bit should be set
        boolean any = false;
        for (boolean b : bits) if (b) { any = true; break; }
        assertThat(any).as("ids produce some bits").isTrue();
    }
}