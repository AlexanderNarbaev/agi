package io.matrix.api;

import java.util.EnumMap;
import java.util.Map;

/**
 * RUN 136 — TokenAnalyzer.
 *
 * <p>Analyzes a generated sequence and produces counts of each
 * TokenType along with simple statistics.
 */
public final class TokenAnalyzer {

    public record AnalysisResult(int totalTokens,
                                  Map<TokenType, Integer> counts,
                                  double specialRatio,
                                  double punctuationRatio) {}

    public AnalysisResult analyze(int[] tokenIds, BpeTokenizer tokenizer) {
        Map<TokenType, Integer> counts = new EnumMap<>(TokenType.class);
        for (TokenType t : TokenType.values()) {
            counts.put(t, 0);
        }
        for (int id : tokenIds) {
            String tok = tokenizer.reverseToken(id);
            TokenType type = TokenType.classify(tok);
            counts.merge(type, 1, Integer::sum);
        }
        int total = tokenIds.length;
        int special = counts.get(TokenType.SPECIAL);
        int punct = counts.get(TokenType.PUNCTUATION);
        return new AnalysisResult(total, counts,
                total == 0 ? 0.0 : (double) special / total,
                total == 0 ? 0.0 : (double) punct / total);
    }
}
