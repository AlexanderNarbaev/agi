package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveTokenizerTest {

    @Test
    void constructValid() {
        CognitiveTokenizer tok = new CognitiveTokenizer(64, 42L);
        assertThat(tok.targetVocabSize()).isEqualTo(64);
    }

    @Test
    void invalidVocabSizeThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveTokenizer(0, 1L)
        );
    }

    @Test
    void tokenizeSimpleString() {
        CognitiveTokenizer tok = new CognitiveTokenizer(64, 42L);
        int[] tokens = tok.tokenize("hello");
        assertThat(tokens.length).isGreaterThan(0);
        assertThat(tokens.length).isLessThanOrEqualTo(5);
    }

    @Test
    void nullTokenizeReturnsEmpty() {
        CognitiveTokenizer tok = new CognitiveTokenizer(64, 42L);
        assertThat(tok.tokenize(null)).isEmpty();
    }

    @Test
    void roundTripEncodeDecode() {
        CognitiveTokenizer tok = new CognitiveTokenizer(64, 42L);
        int[] tokens = tok.tokenize("hello");
        String decoded = tok.decode(tokens);
        assertThat(decoded.toLowerCase()).isEqualTo("hello");
    }

    @Test
    void decodeEmptyReturnsEmpty() {
        CognitiveTokenizer tok = new CognitiveTokenizer(64, 42L);
        assertThat(tok.decode(null)).isEqualTo("");
        assertThat(tok.decode(new int[0])).isEqualTo("");
    }

    @Test
    void vocabGrowsWithMerge() {
        CognitiveTokenizer tok = new CognitiveTokenizer(64, 42L);
        int initialSize = tok.vocabSize();
        String[] corpus = {"hello", "help", "helm"};
        tok.mergeMostFrequent(corpus);
        assertThat(tok.vocabSize()).isGreaterThanOrEqualTo(initialSize);
    }

    @Test
    void mergeEmptyCorpusNoOp() {
        CognitiveTokenizer tok = new CognitiveTokenizer(64, 42L);
        int initialSize = tok.vocabSize();
        tok.mergeMostFrequent(new String[0]);
        assertThat(tok.vocabSize()).isEqualTo(initialSize);
    }

    @Test
    void vocabSizeBounded() {
        CognitiveTokenizer tok = new CognitiveTokenizer(2, 42L);
        // Even with many merges, vocabSize shouldn't exceed target
        for (int i = 0; i < 100; i++) {
            tok.mergeMostFrequent(new String[]{"hello", "world"});
        }
        assertThat(tok.vocabSize()).isLessThanOrEqualTo(2);
    }
}
