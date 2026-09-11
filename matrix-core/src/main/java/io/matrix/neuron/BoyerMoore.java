package io.matrix.neuron;

/**
 * RUN 423 — Boyer-Moore (bad-character-shift) substring search on text.
 * <p>O(n/m) average, O(n+m) preprocessing. Pure function. The
 * original Boyer-Moore rule with the simplified bad-character heuristic
 * (skipping the strong-good-suffix part for clarity). CONSTITUTION I-safe.
 *
 * <p>Returns the FIRST occurrence index, or {@code -1} if absent.
 */
public final class BoyerMoore {

    private BoyerMoore() {}

    /**
     * @param haystack    search text (must be non-null)
     * @param needle      pattern to find (must be non-empty)
     * @param startFrom   index in haystack to begin searching
     */
    public static int search(byte[] haystack, byte[] needle, int startFrom) {
        if (needle.length == 0) return startFrom;
        if (haystack.length - startFrom < needle.length) return -1;
        // Pre-processing: bad-character table.
        int[] bad = new int[256];
        for (int i = 0; i < bad.length; i++) bad[i] = -1;
        for (int i = 0; i < needle.length; i++) bad[needle[i] & 0xFF] = i;
        int m = needle.length;
        int n = haystack.length;
        int s = startFrom;  // current alignment
        while (s <= n - m) {
            int j = m - 1;
            while (j >= 0 && needle[j] == haystack[s + j]) j--;
            if (j < 0) return s;  // match
            int shift = j - bad[haystack[s + j] & 0xFF];
            s += Math.max(1, shift);
        }
        return -1;
    }

    /** Default starting index = 0. */
    public static int search(byte[] haystack, byte[] needle) {
        return search(haystack, needle, 0);
    }

    /** Convenience: count ALL non-overlapping occurrences. */
    public static int countAll(byte[] haystack, byte[] needle) {
        int count = 0;
        int pos = 0;
        while (true) {
            int idx = search(haystack, needle, pos);
            if (idx < 0) break;
            count++;
            pos = idx + needle.length;
        }
        return count;
    }
}
