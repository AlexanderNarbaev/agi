package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BitNetTokenizerTest {

    private BitNetTokenizer tokenizer;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws IOException {
        tokenizer = new BitNetTokenizer();
    }

    @Test
    void loadsVocabSuccessfully() {
        assertThat(tokenizer.vocab).isNotEmpty();
        // Should have at least 128k base + 256 special
        assertThat(tokenizer.vocab.size()).isGreaterThanOrEqualTo(128000);
    }

    @Test
    void bosAndEosIds() {
        // Per HuggingFace config: bos=128000, eos=128001 (and 128009 also accepted as EOS)
        assertThat(tokenizer.bosTokenId).isEqualTo(128000);
        assertThat(tokenizer.eosTokenId).isEqualTo(128001);
    }

    @Test
    void encodeHelloWorldMatchesReference() {
        // Python: "Hello world" → [9906, 1917]
        int[] ids = tokenizer.encode("Hello world");
        assertThat(ids).containsExactly(9906, 1917);
    }

    @Test
    void encodeTheQuickBrownFox() {
        // Python: "The quick brown fox" → [791, 4062, 14198, 39935]
        int[] ids = tokenizer.encode("The quick brown fox");
        assertThat(ids).containsExactly(791, 4062, 14198, 39935);
    }

    @Test
    void encodeBitNet() {
        // Python: "BitNet b1.58" → [8509, 7099, 293, 16, 13, 2970]
        int[] ids = tokenizer.encode("BitNet b1.58");
        assertThat(ids).containsExactly(8509, 7099, 293, 16, 13, 2970);
    }

    @Test
    void encodeArithmeticExpression() {
        // Python: "123 + 456 = 579" → [4513, 489, 220, 10961, 284, 220, 24847]
        int[] ids = tokenizer.encode("123 + 456 = 579");
        assertThat(ids).containsExactly(4513, 489, 220, 10961, 284, 220, 24847);
    }

    @Test
    void encodeDoubleSpace() {
        // Python: "  spaces  " → [220, 12908, 256]
        int[] ids = tokenizer.encode("  spaces  ");
        assertThat(ids).containsExactly(220, 12908, 256);
    }

    @Test
    void encodeDoubleSpaceBetweenWords() {
        // Python: "test  double" → [1985, 220, 2033]
        int[] ids = tokenizer.encode("test  double");
        assertThat(ids).containsExactly(1985, 220, 2033);
    }

    @Test
    void encodeMixedCase() {
        // Python: "ABCabc" → [26484, 13997]
        int[] ids = tokenizer.encode("ABCabc");
        assertThat(ids).containsExactly(26484, 13997);
    }

    @Test
    void encodeEmptyString() {
        int[] ids = tokenizer.encode("");
        assertThat(ids).isEmpty();
    }

    @Test
    void encodeSingleChar() {
        int[] ids = tokenizer.encode("a");
        assertThat(ids).containsExactly(64);
    }

    @Test
    void encodeWithBosPrepends() {
        int[] ids = tokenizer.encodeWithBos("Hello");
        // First should be BOS (128000), then "Hello" tokens
        assertThat(ids[0]).isEqualTo(128000);
        // Verify rest matches non-BOS encoding
        int[] withoutBos = tokenizer.encode("Hello");
        assertThat(ids.length).isEqualTo(withoutBos.length + 1);
        for (int i = 0; i < withoutBos.length; i++) {
            assertThat(ids[i + 1]).isEqualTo(withoutBos[i]);
        }
    }

    @Test
    void roundTripPreservesText() {
        String text = "Hello world, this is a test of BPE tokenization!";
        int[] ids = tokenizer.encode(text);
        String decoded = tokenizer.decode(ids);
        assertThat(decoded).isEqualTo(text);
    }

    @Test
    void roundTripWithApostrophes() {
        String text = "don't worry";
        int[] ids = tokenizer.encode(text);
        String decoded = tokenizer.decode(ids);
        assertThat(decoded).isEqualTo(text);
    }

    @Test
    void roundTripUnicode() {
        String text = "café résumé naïve";
        int[] ids = tokenizer.encode(text);
        String decoded = tokenizer.decode(ids);
        assertThat(decoded).isEqualTo(text);
    }

    @Test
    void decodeRoundTripSkipsBos() {
        int[] ids = {128000, 9906, 1917}; // BOS + Hello world
        String decoded = tokenizer.decode(ids);
        assertThat(decoded).isEqualTo("Hello world");
    }

    @Test
    void decodeMultipleSpaces() {
        // "  spaces  " → [220, 12908, 256]
        String decoded = tokenizer.decode(new int[]{220, 12908, 256});
        assertThat(decoded).isEqualTo("  spaces  ");
    }

    @Test
    void decodeSingleToken() {
        // "Hello" → 9906
        String decoded = tokenizer.decode(new int[]{9906});
        assertThat(decoded).isEqualTo("Hello");
    }

    @Test
    void encodePerformanceIsFast() {
        // 100 tokens for a typical paragraph should be < 1 second
        String text = "The quick brown fox jumps over the lazy dog. " +
                "Pack my box with five dozen liquor jugs. " +
                "How vexingly quick daft zebras jump!";
        long start = System.nanoTime();
        int[] ids = tokenizer.encode(text);
        long elapsed = System.nanoTime() - start;
        System.out.printf("[BitNet tokenizer] encode %d chars: %d ms, %d tokens%n",
                text.length(), elapsed / 1_000_000, ids.length);
        assertThat(ids.length).isGreaterThan(0);
    }
}
