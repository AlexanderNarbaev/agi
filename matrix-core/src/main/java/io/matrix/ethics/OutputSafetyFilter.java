package io.matrix.ethics;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Constrained-decoding output safety filter (RUN 27).
 *
 * <p>Wraps the {@link EthicalFilter} to also constrain the
 * GENERATED token stream during chain decoding. While
 * {@link EthicalFilter} gates input prompts, this class gates
 * generated outputs.
 *
 * <p>It exposes:
 * <ul>
 *   <li>{@link #isTokenForbidden(int)} — checks a single token ID
 *       against the forbidden-token list (byte-level blacklist for
 *       control chars and dangerous byte sequences).</li>
 *   <li>{@link #isStringAllowed(String)} — checks a candidate
 *       string (post-tokenization) for forbidden patterns.</li>
 *   <li>{@link #filter(int[])} — filters a token array, replacing
 *       forbidden tokens with a safe replacement (currently
 *       dropping them).</li>
 * </ul>
 *
 * <p>This is a deterministic, lightweight check (no LLM call,
 * no random source, no wall-clock) so it can be applied on every
 * generated token without adding latency to the hot path.
 */
public class OutputSafetyFilter {

    /** Control characters that should never appear in generated text. */
    private static final int[] FORBIDDEN_CONTROL_BYTES = {
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x0B, 0x0C, 0x0E, 0x0F,
            0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
            0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x7F
    };

    /** Tokens that encode obviously harmful intent in user prompts. */
    private static final Set<String> FORBIDDEN_PHRASES = new HashSet<>(Arrays.asList(
            "kill", "murder", "torture", "assault", "bomb", "attack",
            "убить", "убийство", "пытка", "взрыв", "напасть"
    ));

    private final Set<Integer> forbiddenTokenSet;

    public OutputSafetyFilter() {
        Set<Integer> s = new HashSet<>();
        for (int b : FORBIDDEN_CONTROL_BYTES) s.add(b);
        // Also forbid some "obvious garbage" tokens (e.g., surrogate half-pairs)
        for (int i = 0xD800; i <= 0xDFFF; i++) s.add(i);
        this.forbiddenTokenSet = Set.copyOf(s);
    }

    /** Check whether a single token ID is forbidden. */
    public boolean isTokenForbidden(int token) {
        return forbiddenTokenSet.contains(token);
    }

    /**
     * Check whether a candidate string is safe to emit. Uses simple
     * case-insensitive substring matching against forbidden phrases.
     *
     * <p>This is a SAFETY LAYER, not a comprehensive check. It catches
     * obvious cases and is fast enough for the hot path. Comprehensive
     * safety still requires the full {@link EthicalFilter} pipeline.
     */
    public boolean isStringAllowed(String text) {
        if (text == null || text.isEmpty()) return true;
        String lower = text.toLowerCase();
        for (String phrase : FORBIDDEN_PHRASES) {
            if (lower.contains(phrase)) return false;
        }
        return true;
    }

    /**
     * Filter a token array, dropping forbidden tokens. Returns the
     * filtered array (possibly shorter than the input).
     */
    public int[] filter(int[] tokens) {
        if (tokens == null || tokens.length == 0) return tokens;
        int kept = 0;
        for (int t : tokens) if (!isTokenForbidden(t)) kept++;
        if (kept == tokens.length) return tokens;  // no-op fast path
        int[] out = new int[kept];
        int idx = 0;
        for (int t : tokens) {
            if (!isTokenForbidden(t)) out[idx++] = t;
        }
        return out;
    }

    /** Forbidden token count (for diagnostics). */
    public int forbiddenTokenCount() {
        return forbiddenTokenSet.size();
    }

    /** Forbidden phrase list (defensive copy). */
    public List<String> forbiddenPhrases() {
        return List.copyOf(FORBIDDEN_PHRASES);
    }
}
