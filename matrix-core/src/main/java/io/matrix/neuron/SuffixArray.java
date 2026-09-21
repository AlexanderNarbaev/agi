package io.matrix.neuron;

import java.util.Arrays;

/**
 * RUN 433 — Suffix array via simple doubling sort.
 * <p>O(n log² n) — sorts suffixes by {@code (rank[i], rank[i + k])} pairs
 * with k doubling each round. Acceptable for typical N (≤10⁵).
 * Pure function. CONSTITUTION I-safe.
 *
 * <p>Note: this is a teaching-quality suffix array, not the linear-time
 * DC3 / SA-IS; use {@code libsais} for production.
 */
public final class SuffixArray {

    private SuffixArray() {}

    /**
     * Build a suffix array; returns the sorted list of start positions.
     */
    public static int[] build(String s) {
        if (s.isEmpty()) return new int[0];
        int n = s.length();
        Integer[] sa = new Integer[n];
        for (int i = 0; i < n; i++) sa[i] = i;
        int k = 1;
        // Initial rank by character
        int[] rankArr = new int[n];
        for (int i = 0; i < n; i++) rankArr[i] = s.charAt(i);
        // Doubling sort
        while (true) {
            final int kk = k;
            final int[] capturedRank = rankArr;
            Arrays.sort(sa, (a, b) -> {
                if (capturedRank[a] != capturedRank[b]) return Integer.compare(capturedRank[a], capturedRank[b]);
                int ra = (a + kk < n) ? capturedRank[a + kk] : -1;
                int rb = (b + kk < n) ? capturedRank[b + kk] : -1;
                return Integer.compare(ra, rb);
            });
            // Re-rank
            int[] newRank = new int[n];
            int r = 0;
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    int prev = sa[i - 1];
                    int cur = sa[i];
                    if (capturedRank[cur] != capturedRank[prev] ||
                            (cur + kk < n ? capturedRank[cur + kk] : -1) !=
                            (prev + kk < n ? capturedRank[prev + kk] : -1)) {
                        r++;
                    }
                }
                newRank[sa[i]] = r;
            }
            rankArr = newRank;
            if (r == n - 1) break;  // fully ranked
            k <<= 1;
        }
        int[] result = new int[n];
        for (int i = 0; i < n; i++) result[i] = sa[i];
        return result;
    }

    /** Binary search: smallest index i such that suffixes match prefix. */
    public static int firstSuffixMatching(String s, int[] sa, String pattern) {
        int lo = 0, hi = sa.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            String suffix = s.substring(sa[mid], Math.min(s.length(), sa[mid] + pattern.length()));
            int cmp = suffix.compareTo(pattern);
            if (cmp < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
