package io.matrix.neuron;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 438 — High-level HDC binding primitives (DESIGN-54 §2.2).
 *
 * <p>Builds on {@link HdcEncoding} with the standard Kanerva/Plate
 * operations: role-filler pairs, sequence memory, records, n-grams.
 *
 * <h2>Operations</h2>
 * <ul>
 *   <li>{@link #bind(long[], long[])} — XOR binding (= multiplication)
 *       (delegated to {@link HdcEncoding#xor}).</li>
 *   <li>{@link #unbind(long[], long[])} — XOR unbind (same as bind;
 *       XOR is self-inverse).</li>
 *   <li>{@link #sequence(long[], int)} — Kanerva position encoding:
 *       {@code permute(x, n)} puts item {@code x} at role "n".</li>
 *   <li>{@link #record(java.util.Map)} — record of role→filler pairs,
 *       each role-filler position-encoded then bundled together.</li>
 *   <li>{@link #ngram(java.util.List)} — bind consecutive items,
 *       shifting each by its position. Used for compositional sequences.</li>
 *   <li>{@link #cleanup(long[], java.util.Map)} — find nearest item
 *       in a codebook by Hamming distance.</li>
 * </ul>
 *
 * <h2>Why record-style</h2>
 * A record of (role→filler) pairs is the canonical HDC data structure for
 * episodic memory (Kanerva 1988 §6). Two records with overlapping
 * role-filler pairs will share filler-level structure, enabling
 * compositional generalization.
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies any RNG. All methods are pure functions.
 */
public final class HdcBinding {

    private HdcBinding() {}

    /**
     * XOR binding (synonym for {@link HdcEncoding#xor}).
     * Self-inverse: {@code unbind(bind(x, y), y) == x}.
     */
    public static long[] bind(long[] a, long[] b) {
        return HdcEncoding.xor(a, b);
    }

    /**
     * XOR unbind (synonym for {@link HdcEncoding#xor}, since XOR is self-inverse).
     */
    public static long[] unbind(long[] composite, long[] roleOrFiller) {
        return HdcEncoding.xor(composite, roleOrFiller);
    }

    /**
     * Position encoding: puts item {@code x} at role "position" by
     * circular shift. Two items at different positions are near-orthogonal.
     *
     * <p>Used as the basic block for sequence memory.
     */
    public static long[] sequence(long[] item, int position) {
        return HdcEncoding.permute(item, position);
    }

    /**
     * Build a record from a map of role-name to filler-vector.
     *
     * <p>Each pair is bound: {@code bind(sequence(roleCode, roleHash), filler)},
     * then all pairs are bundled. This produces a single vector that
     * approximates a "bag of role-filler" semantic.
     *
     * @param roles   map of role-name string to role-code vector
     * @param fillers map of role-name string to filler vector
     */
    public static long[] record(Map<String, long[]> roles,
                                Map<String, long[]> fillers) {
        if (roles == null || fillers == null) {
            throw new IllegalArgumentException("null map");
        }
        // Compute per-role position from role hash
        long[][] bindings = new long[roles.size()][];
        int idx = 0;
        for (Map.Entry<String, long[]> e : roles.entrySet()) {
            String roleName = e.getKey();
            long[] roleCode = e.getValue();
            long[] filler = fillers.get(roleName);
            if (filler == null) {
                throw new IllegalArgumentException("missing filler for role: " + roleName);
            }
            int position = roleName.hashCode() & 0x7FFFFFFF; // non-negative
            long[] positioned = sequence(roleCode, position % HdcEncoding.DIM);
            bindings[idx++] = bind(positioned, filler);
        }
        return HdcEncoding.bundle(bindings);
    }

    /**
     * Build a record where roles are themselves strings encoded into HDC
     * vectors using a deterministic codebook seeded by {@code roleRng}.
     *
     * <p>Convenience overload for the common case where roles are
     * symbolic identifiers.
     */
    public static long[] recordFromStrings(Map<String, long[]> roleFillers,
                                            Random roleRng) {
        if (roleFillers == null || roleRng == null) {
            throw new IllegalArgumentException("null input");
        }
        // Build role codebook from names
        Map<String, long[]> roleCodes = new HashMap<>();
        for (String name : roleFillers.keySet()) {
            roleCodes.put(name, stringToCode(name, roleRng));
        }
        return record(roleCodes, roleFillers);
    }

    /**
     * Deterministic string → HDC code. Each character contributes a
     * role-coded shift; XOR bundle yields a quasi-unique identifier.
     *
     * <p>Not meant for high-cardinality codebooks; for those use
     * random codes per string. This is for human-readable roles
     * like "agent", "action", "object".
     */
    public static long[] stringToCode(String s, Random rng) {
        if (s == null) throw new IllegalArgumentException("null string");
        if (rng == null) throw new IllegalArgumentException("null rng");
        long[] out = HdcEncoding.zero();
        // Per-char seed: take character index as a "name" and bind via
        // a random per-position code.
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Derive a stable code for this character + position
            Random perChar = new Random(rng.nextLong() ^ (long) c ^ ((long) i << 32));
            long[] charCode = HdcEncoding.random(perChar);
            long[] positioned = HdcEncoding.permute(charCode, i);
            out = HdcEncoding.xor(out, positioned);
        }
        return out;
    }

    /**
     * Build an n-gram representation of a sequence. Each item at position i
     * is XOR-bound with a position-shift. The result is a single vector
     * representing the entire sequence.
     *
     * <p>Used for compositional encoding of time-series data.
     */
    public static long[] ngram(List<long[]> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("empty sequence");
        }
        long[] out = HdcEncoding.zero();
        for (int i = 0; i < items.size(); i++) {
            long[] item = items.get(i);
            if (item == null || item.length != HdcEncoding.WORDS) {
                throw new IllegalArgumentException("bad item at " + i);
            }
            long[] positioned = sequence(item, i);
            out = HdcEncoding.xor(out, positioned);
        }
        return out;
    }

    /**
     * Find the entry in {@code codebook} nearest to {@code query} by
     * Hamming distance. Returns null if codebook is empty.
     *
     * <p>This is the cleanup operation — when a noisy or partial query
     * comes in, we snap it to the closest known code.
     */
    public static <T> Map.Entry<T, long[]> cleanup(long[] query,
                                                    Map<T, long[]> codebook) {
        if (query == null || query.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("bad query");
        }
        if (codebook == null || codebook.isEmpty()) {
            return null;
        }
        Map.Entry<T, long[]> best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Map.Entry<T, long[]> e : codebook.entrySet()) {
            long[] code = e.getValue();
            if (code == null || code.length != HdcEncoding.WORDS) continue;
            int d = HdcEncoding.hamming(query, code);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * Top-K nearest entries by Hamming distance. Sorted ascending by distance.
     */
    public static <T> java.util.List<Map.Entry<T, Integer>> cleanupTopK(
            long[] query, Map<T, long[]> codebook, int k) {
        if (query == null || query.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("bad query");
        }
        if (k <= 0 || codebook == null || codebook.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Map.Entry<T, Integer>> all = new java.util.ArrayList<>();
        for (Map.Entry<T, long[]> e : codebook.entrySet()) {
            long[] code = e.getValue();
            if (code == null || code.length != HdcEncoding.WORDS) continue;
            int d = HdcEncoding.hamming(query, code);
            all.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), d));
        }
        all.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));
        if (all.size() > k) return all.subList(0, k);
        return all;
    }
}
