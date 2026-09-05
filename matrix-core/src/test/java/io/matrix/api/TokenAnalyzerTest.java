package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 136 — TokenAnalyzer unit tests. */
class TokenAnalyzerTest {

    @Test
    void emptyTokensReturnsZero() {
        BpeTokenizer stub = new BpeTokenizer(java.util.Map.of(), java.util.List.of());
        TokenAnalyzer a = new TokenAnalyzer();
        var result = a.analyze(new int[0], stub);
        assertThat(result.totalTokens()).isZero();
        assertThat(result.specialRatio()).isZero();
        assertThat(result.punctuationRatio()).isZero();
    }

    @Test
    void countsAllTokenTypes() {
        BpeTokenizer stub = new BpeTokenizer(java.util.Map.of(
                "hello", 100,
                ".", 101,
                "42", 102,
                "<|im_end|>", 200
        ), java.util.List.of());
        TokenAnalyzer a = new TokenAnalyzer();
        var result = a.analyze(new int[]{100, 101, 102, 200}, stub);
        assertThat(result.totalTokens()).isEqualTo(4);
        Map<TokenType, Integer> counts = result.counts();
        assertThat(counts.get(TokenType.WORD)).isEqualTo(1);
        assertThat(counts.get(TokenType.PUNCTUATION)).isEqualTo(1);
        assertThat(counts.get(TokenType.NUMBER)).isEqualTo(1);
        assertThat(counts.get(TokenType.SPECIAL)).isEqualTo(1);
    }

    @Test
    void specialRatioCalculation() {
        BpeTokenizer stub = new BpeTokenizer(java.util.Map.of(
                "<|im_end|>", 200,
                "hello", 100
        ), java.util.List.of());
        TokenAnalyzer a = new TokenAnalyzer();
        var result = a.analyze(new int[]{200, 100, 100, 100}, stub);
        // 1 special out of 4 tokens
        assertThat(result.specialRatio()).isEqualTo(0.25);
    }

    @Test
    void punctuationRatioCalculation() {
        BpeTokenizer stub = new BpeTokenizer(java.util.Map.of(
                ".", 101,
                "hello", 100
        ), java.util.List.of());
        TokenAnalyzer a = new TokenAnalyzer();
        var result = a.analyze(new int[]{101, 100, 101}, stub);
        // 2 punct out of 3
        assertThat(result.punctuationRatio()).isCloseTo(0.667,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void allTokenTypesInitialized() {
        BpeTokenizer stub = new BpeTokenizer(java.util.Map.of(), java.util.List.of());
        TokenAnalyzer a = new TokenAnalyzer();
        var result = a.analyze(new int[0], stub);
        for (TokenType t : TokenType.values()) {
            assertThat(result.counts()).containsKey(t);
            assertThat(result.counts().get(t)).isZero();
        }
    }
}
