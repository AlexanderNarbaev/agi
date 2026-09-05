package io.matrix.api;

/**
 * RUN 135 — TokenType classification.
 *
 * <p>Categorizes decoded tokens into types for downstream filtering
 * or analysis.
 */
public enum TokenType {
    /** Regular word content token. */
    WORD,
    /** Punctuation or special character. */
    PUNCTUATION,
    /** Numeric token. */
    NUMBER,
    /** Whitespace-only token. */
    WHITESPACE,
    /** Special token (<|im_start|>, <|im_end|>, <|endoftext|>). */
    SPECIAL,
    /** Unknown / unrecognized. */
    UNKNOWN;

    public static TokenType classify(String token) {
        if (token == null || token.isEmpty()) return UNKNOWN;
        if (token.startsWith("<|") && token.endsWith("|>")) return SPECIAL;
        if (token.matches("^[\\p{Punct}]+$")) return PUNCTUATION;
        if (token.matches("^[\\p{N}]+$")) return NUMBER;
        if (token.matches("^\\s+$")) return WHITESPACE;
        return WORD;
    }
}
