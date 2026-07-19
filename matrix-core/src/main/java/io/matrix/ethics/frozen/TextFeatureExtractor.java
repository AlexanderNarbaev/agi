package io.matrix.ethics.frozen;

import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Text → feature-bit extractor feeding the FROZEN ethical FNL network.
 *
 * <p>Implements the "Adaptive Quantizer" half of the L7 §2.3 pipeline:
 * input text is mapped to a deterministic {@code k}-bit feature vector by
 * combining a small set of robust features (presence flags for known
 * red-flag tokens, length bucket, character hash, punctuation flags).
 *
 * <p>The mapping is intentionally stable and reproducible — the same input
 * text always yields the same feature bits so that the frozen neurons give
 * the same verdict across processes and time. No ML, no randomness, no
 * reflective lookups.
 *
 * <p>Bit layout (LSB-first, configurable width):
 * <ol>
 *   <li>bit 0: contains a kill trigger</li>
 *   <li>bit 1: contains a torture trigger</li>
 *   <li>bit 2: contains an enslavement trigger</li>
 *   <li>bit 3: contains an autonomous-weapons trigger</li>
 *   <li>bit 4: contains a deception trigger</li>
 *   <li>bit 5: contains a privacy trigger</li>
 *   <li>bit 6: input ends with a question mark</li>
 *   <li>bit 7: input ends with an exclamation mark</li>
 *   <li>bits 8..11: length bucket (0=0..3, 1=4..15, 2=16..63, 3=64..255, 4=256+)</li>
 *   <li>bits 12..15: ASCII sum mod 16 (lightweight content fingerprint)</li>
 *   <li>bits 16..k-1: zero (reserved for future quantizers)</li>
 * </ol>
 */
public final class TextFeatureExtractor {

    public static final int DEFAULT_K = 16;

    /** Bit slot index per category (matches the bit layout above). */
    public static final int BIT_KILL = 0;
    public static final int BIT_TORTURE = 1;
    public static final int BIT_ENSLAVEMENT = 2;
    public static final int BIT_WEAPONS = 3;
    public static final int BIT_DECEPTION = 4;
    public static final int BIT_PRIVACY = 5;
    public static final int BIT_QUESTION = 6;
    public static final int BIT_EXCLAIM = 7;

    /** Category → list of trigger phrases. Final, never mutated. */
    public static final Map<Integer, List<String>> TRIGGERS = Map.ofEntries(
            Map.entry(BIT_KILL, List.of("kill", "murder", "assassinate", "massacre",
                    "slaughter", "execute person", "destroy life")),
            Map.entry(BIT_TORTURE, List.of("torture", "inflict pain", "torment",
                    "brutalize", "suffer")),
            Map.entry(BIT_ENSLAVEMENT, List.of("enslave", "subjugate", "control mind",
                    "bondage", "human trafficking")),
            Map.entry(BIT_WEAPONS, List.of("autonomous weapon", "laws",
                    "kill without human", "killer robot")),
            Map.entry(BIT_DECEPTION, List.of("lie about", "deceive people", "fake news",
                    "disinformation campaign", "false testimony", "propaganda spread")),
            Map.entry(BIT_PRIVACY, List.of("leak personal data", "expose private information",
                    "dox", "stalker", "doxxing"))
    );

    private final int k;

    /** Construct a default 16-bit extractor. */
    public TextFeatureExtractor() { this(DEFAULT_K); }

    /** Construct with a custom bit width. */
    public TextFeatureExtractor(int k) {
        if (k < 8 || k > TruthTableUtil.MAX_K) {
            throw new IllegalArgumentException("k must be in [8, " + TruthTableUtil.MAX_K + "]");
        }
        this.k = k;
    }

    public int k() { return k; }

    /**
     * Extracts the feature bits for the given text. Always returns a non-null
     * BitSet of width {@code k}; empty/null inputs collapse to a zero bit-set.
     */
    public BitSet extract(String text) {
        BitSet out = new BitSet(k);
        if (text == null || text.isEmpty()) return out;
        String lower = text.toLowerCase(Locale.ROOT);

        // Each category's trigger phrases are checked as whole-word matches when
        // they are single tokens, or as literal substrings when they contain a
        // space. This way "kill the target" fires NO_KILLING, but
        // "improve my skill at cooking" does not (substring "kill" inside "skill"
        // is rejected because "kill" is not a whole word).
        for (Map.Entry<Integer, List<String>> e : TRIGGERS.entrySet()) {
            int bit = e.getKey();
            for (String phrase : e.getValue()) {
                if (phrase.indexOf(' ') >= 0) {
                    // Multi-word phrase → substring match is safe.
                    if (lower.contains(phrase)) { out.set(bit); break; }
                } else {
                    // Single token → whole-word match.
                    if (containsWholeWord(lower, phrase)) { out.set(bit); break; }
                }
            }
        }

        if (text.endsWith("?")) out.set(BIT_QUESTION);
        if (text.endsWith("!")) out.set(BIT_EXCLAIM);

        int lenBucket = lengthBucket(lower.length());
        for (int b = 0; b < 4; b++) {
            if ((lenBucket & (1 << b)) != 0) out.set(8 + b);
        }

        int asciiSumMod = (int) (lower.chars().sum() & 0xF);
        for (int b = 0; b < 4; b++) {
            if ((asciiSumMod & (1 << b)) != 0) out.set(12 + b);
        }
        return out;
    }

    /** Pack the {@link BitSet} into a long (low {@code k} bits used). */
    public long toLong(BitSet bits) {
        long out = 0L;
        for (int i = 0; i < Math.min(k, 64); i++) {
            if (bits.get(i)) out |= (1L << i);
        }
        return out;
    }

    private static int lengthBucket(int len) {
        if (len <= 3) return 0;
        if (len <= 15) return 1;
        if (len <= 63) return 2;
        if (len <= 255) return 3;
        return 4;
    }

    /**
     * True if {@code word} appears in {@code text} bounded by non-letter
     * characters (or string boundaries). Caller passes already-lowercased input.
     */
    private static boolean containsWholeWord(String text, String word) {
        if (word.isEmpty()) return false;
        int from = 0;
        while (true) {
            int idx = text.indexOf(word, from);
            if (idx < 0) return false;
            boolean leftBoundary = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int end = idx + word.length();
            boolean rightBoundary = (end == text.length()) || !Character.isLetterOrDigit(text.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            from = idx + 1;
        }
    }
}
