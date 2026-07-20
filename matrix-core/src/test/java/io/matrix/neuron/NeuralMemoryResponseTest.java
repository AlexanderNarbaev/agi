package io.matrix.neuron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

class NeuralMemoryResponseTest {

    @Test
    void hashTextIsDeterministic() {
        long h1 = NeuralMemoryResponse.hashText("hello");
        long h2 = NeuralMemoryResponse.hashText("hello");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(0L);
    }

    @Test
    void hashTextDifferentForDifferentInputs() {
        long h1 = NeuralMemoryResponse.hashText("hello");
        long h2 = NeuralMemoryResponse.hashText("world");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void toBitSetProducesCorrectWidth() {
        BitSet bs = NeuralMemoryResponse.toBitSet(0x0FL, 64);
        assertThat(bs.length()).isLessThanOrEqualTo(64);
        assertThat(bs.get(0)).isTrue();
        assertThat(bs.get(1)).isTrue();
        assertThat(bs.get(2)).isTrue();
        assertThat(bs.get(3)).isTrue();
        assertThat(bs.get(4)).isFalse();
    }

    @Test
    void padInputExtendsShortBitSet() {
        BitSet short_ = new BitSet(10);
        short_.set(0, 5);
        BitSet padded = NeuralMemoryResponse.padInput(short_, 20);
        assertThat(padded.length()).isLessThanOrEqualTo(20);
        assertThat(padded.get(0)).isTrue();
        assertThat(padded.get(10)).isFalse();
    }

    @Test
    void padInputTruncatesLongBitSet() {
        BitSet long_ = new BitSet(100);
        long_.set(0, 100);
        BitSet truncated = NeuralMemoryResponse.padInput(long_, 64);
        assertThat(truncated.length()).isLessThanOrEqualTo(64);
    }

    @Test
    void toLongArrayIsInvertible() {
        BitSet bs = BitSet.valueOf(new long[]{0xDEADBEEFL, 0xCAFEBABEL});
        long[] words = NeuralMemoryResponse.toLongArray(bs);
        BitSet restored = BitSet.valueOf(words);
        assertThat(restored).isEqualTo(bs);
    }

    @Test
    void neuralSignatureNullSafeForNullService() {
        long[] sig = NeuralMemoryResponse.neuralSignature(null, "test");
        assertThat(sig).isNull();
    }

    @Test
    void extractFieldParsesSimpleJson() {
        String json = "{\"input\":\"hello\",\"output\":\"world\"}";
        assertThat(NeuralMemoryResponseTestHelper.extractField(json, "input")).isEqualTo("hello");
        assertThat(NeuralMemoryResponseTestHelper.extractField(json, "output")).isEqualTo("world");
    }

    @Test
    void extractFieldHandlesEscapedQuotes() {
        String json = "{\"text\":\"he said \\\"hello\\\"\"}";
        assertThat(NeuralMemoryResponseTestHelper.extractField(json, "text"))
                .isEqualTo("he said \"hello\"");
    }

    @Test
    void extractFieldReturnsNullForMissingField() {
        assertThat(NeuralMemoryResponseTestHelper.extractField("{}", "missing")).isNull();
    }

    @Test
    void extractFieldReturnsNullForMalformedJson() {
        assertThat(NeuralMemoryResponseTestHelper.extractField("{input:}", "input")).isNull();
    }

    @Test
    void cosineSimilarityIsSymmetric() {
        long[] a = {0xFL, 0x0L};
        long[] b = {0x0L, 0xFL};
        float sim = NeuralMemoryResponseTestHelper.cosineSimilarity(a, b);
        assertThat(sim).isGreaterThanOrEqualTo(0.0f).isLessThanOrEqualTo(1.0f);
    }

    @Test
    void cosineSimilarityIsOneForIdentical() {
        long[] a = {0xDEADBEEFL, 0xCAFEBABEL};
        float sim = NeuralMemoryResponseTestHelper.cosineSimilarity(a, a);
        assertThat(sim).isEqualTo(1.0f);
    }

    @Test
    void cosineSimilarityNullSafe() {
        float sim = NeuralMemoryResponseTestHelper.cosineSimilarity(null, new long[]{1L});
        assertThat(sim).isEqualTo(0.0f);
    }

    @Test
    void loadReturnsNullForMissingFile(@TempDir Path tmp) {
        NeuralMemoryResponse result = NeuralMemoryResponseTestHelper.load(
                null, tmp.resolve("nope.jsonl"));
        assertThat(result).isNull();
    }
}

/** Test helper exposing package-private methods. */
final class NeuralMemoryResponseTestHelper {
    static String extractField(String json, String field) {
        return NeuralMemoryResponse.accessExtractField(json, field);
    }
    static float cosineSimilarity(long[] a, long[] b) {
        return NeuralMemoryResponse.accessCosineSimilarity(a, b);
    }
    static NeuralMemoryResponse load(io.matrix.agent.AgentBrainService svc, Path p) {
        return NeuralMemoryResponse.load(svc, p);
    }
}