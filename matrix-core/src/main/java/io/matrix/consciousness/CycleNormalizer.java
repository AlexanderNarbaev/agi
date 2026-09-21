package io.matrix.consciousness;

/**
 * RUN 283 — CycleNormalizer (input normalization).
 *
 * <p>Normalizes inputs to a standard format before processing.
 */
public final class CycleNormalizer {

    public static String normalize(String input) {
        if (input == null) return "";
        // Trim
        String result = input.strip();
        // Collapse multiple spaces
        result = result.replaceAll("\\s+", " ");
        // Remove control characters
        result = result.replaceAll("[\\p{Cntrl}]", "");
        return result;
    }

    /** Normalize to lowercase. */
    public static String normalizeLower(String input) {
        return normalize(input).toLowerCase();
    }

    /** Normalize to a fixed max length. */
    public static String normalizeTruncate(String input, int max) {
        String n = normalize(input);
        return n.length() <= max ? n : n.substring(0, max);
    }
}
