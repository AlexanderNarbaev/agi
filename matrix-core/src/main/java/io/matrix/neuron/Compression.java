package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 421 — Classical compression / encoding utilities.
 * <p>Two pure-function primitives commonly needed for I/O throughput:
 * <ul>
 *   <li>{@link #rleEncode} / {@link #rleDecode} — Run-length encoding</li>
 *   <li>{@link #levenshtein} — edit-distance on UTF-16 streams</li>
 * </ul>
 * CONSTITUTION I-safe: no Random, no wall-clock.
 */
public final class Compression {

    private Compression() {}

    // ====================================================================
    //                                RLE
    // ====================================================================

    public record RlePair(byte value, int runLength) {}

    /** Naive run-length encoder for byte streams. */
    public static List<RlePair> rleEncode(byte[] data) {
        List<RlePair> out = new ArrayList<>();
        if (data.length == 0) return out;
        byte prev = data[0];
        int run = 1;
        for (int i = 1; i < data.length; i++) {
            if (data[i] == prev && run < Integer.MAX_VALUE) {
                run++;
            } else {
                out.add(new RlePair(prev, run));
                prev = data[i];
                run = 1;
            }
        }
        out.add(new RlePair(prev, run));
        return out;
    }

    public static byte[] rleDecode(List<RlePair> pairs) {
        int total = pairs.stream().mapToInt(RlePair::runLength).sum();
        byte[] out = new byte[total];
        int pos = 0;
        for (RlePair p : pairs) {
            for (int i = 0; i < p.runLength(); i++) out[pos++] = p.value();
        }
        return out;
    }

    // ====================================================================
    //                             Levenshtein
    // ====================================================================

    /**
     * Levenshtein edit-distance on UTF-16 code-unit arrays (Wagner–Fischer
     * dynamic programming). O(n*m) time, O(min(n,m)) memory.
     */
    public static int levenshtein(char[] a, char[] b) {
        int n = a.length, m = b.length;
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }
}
