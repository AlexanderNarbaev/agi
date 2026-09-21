package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdcAsLlmPreprocessorTest {

    @Test
    void constructorRejectsNullRng() {
        assertThatThrownBy(() -> new HdcAsLlmPreprocessor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeProducesCodeOfCorrectDimension() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        long[] code = prep.encode("hello world");
        assertThat(code).hasSize(HdcEncoding.WORDS);
    }

    @Test
    void encodeIsDeterministic() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        long[] a = prep.encode("hello world");
        long[] b = prep.encode("hello world");
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void encodeDifferentTextsDifferentCodes() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        long[] a = prep.encode("hello");
        long[] b = prep.encode("world");
        // Should be different (random, so ~DIM/2 distance)
        int d = HdcEncoding.hamming(a, b);
        assertThat(d).isGreaterThan(400);
    }

    @Test
    void encodeRejectsNull() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        assertThatThrownBy(() -> prep.encode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeFreshIgnoresCache() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        long[] a = prep.encodeFresh("test");
        long[] b = prep.encodeFresh("test");
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void encodeTokensProducesCodeOfCorrectDimension() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        int[] tokens = {1, 2, 3, 5, 8};
        long[] code = prep.encodeTokens(tokens);
        assertThat(code).hasSize(HdcEncoding.WORDS);
    }

    @Test
    void encodeTokensIsDeterministic() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        int[] tokens = {1, 2, 3};
        long[] a = prep.encodeTokens(tokens);
        long[] b = prep.encodeTokens(tokens);
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void encodeTokensDifferentSequences() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        int[] t1 = {1, 2, 3};
        int[] t2 = {1, 2, 4};
        long[] a = prep.encodeTokens(t1);
        long[] b = prep.encodeTokens(t2);
        // Different last token → different codes
        int d = HdcEncoding.hamming(a, b);
        assertThat(d).isGreaterThan(100);
    }

    @Test
    void encodeTokensRejectsNull() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        assertThatThrownBy(() -> prep.encodeTokens(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexStoresCode() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        prep.index("hello");
        assertThat(prep.size()).isEqualTo(1);
        assertThat(prep.getCode("hello")).isNotNull();
    }

    @Test
    void nearestNeighborsFindsIndexedText() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        prep.index("hello world");
        prep.index("foo bar");
        prep.index("baz qux");
        List<Map.Entry<String, Integer>> top = prep.nearestNeighbors("hello world", 1);
        assertThat(top).hasSize(1);
        assertThat(top.get(0).getKey()).isEqualTo("hello world");
        assertThat(top.get(0).getValue()).isEqualTo(0);
    }

    @Test
    void nearestNeighborsReturnsTopK() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        prep.index("a");
        prep.index("b");
        prep.index("c");
        prep.index("d");
        List<Map.Entry<String, Integer>> top2 = prep.nearestNeighbors("a", 2);
        assertThat(top2).hasSize(2);
        assertThat(top2.get(0).getKey()).isEqualTo("a");
    }

    @Test
    void nearestNeighborsRejectsBadArgs() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        prep.index("x");
        assertThatThrownBy(() -> prep.nearestNeighbors(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(prep.nearestNeighbors("x", 0)).isEmpty();
        assertThat(prep.nearestNeighbors("x", -1)).isEmpty();
    }

    @Test
    void nearestNeighborsOnEmptyCodebook() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        assertThat(prep.nearestNeighbors("anything", 5)).isEmpty();
    }

    @Test
    void clearRemovesAllEntries() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        prep.index("a");
        prep.index("b");
        prep.clear();
        assertThat(prep.size()).isEqualTo(0);
        assertThat(prep.getCode("a")).isNull();
    }

    @Test
    void estimatedMemoryBytesScalesWithEntries() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        assertThat(prep.estimatedMemoryBytes()).isEqualTo(0L);
        prep.index("hello");
        long mem = prep.estimatedMemoryBytes();
        assertThat(mem).isGreaterThan(0L);
        // Should be ~128 bytes + label overhead
        assertThat(mem).isLessThan(1000L);
    }

    @Test
    void indexRejectsNull() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        assertThatThrownBy(() -> prep.index(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeEmptyStringProducesZero() {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(1));
        long[] code = prep.encode("");
        // Empty XOR loop → result remains zero vector
        for (long word : code) {
            assertThat(word).isEqualTo(0L);
        }
    }
}
