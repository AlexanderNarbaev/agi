package io.matrix.neuron;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RUN 440 — Cleanup memory for HDC codes (DESIGN-54 §2.3).
 *
 * <p>Bounded associative memory mapping string IDs to bipolar vectors.
 * Query returns the ID whose stored vector has the smallest Hamming
 * distance to the query vector. Used for noisy/partial pattern cleanup
 * (Kanerva 1988 §6.4).
 *
 * <h2>Implementation</h2>
 * Linear scan over the codebook, computing Hamming distance per entry via
 * {@link HdcEncoding#hamming} (which uses {@code HammingNative} when
 * available). For N ≤ ~10K entries this is faster than a BK-tree on CPU
 * due to cache locality and SIMD popcount. For larger N, swap to a
 * BK-tree (Araya 1999) or multi-index hashing (Norouzi 2012).
 *
 * <h2>Bounded capacity</h2>
 * Insertions after {@link #maxCapacity} triggers LRU eviction. Iteration
 * order is access-order so the least-recently-used entry is evicted first.
 *
 * <h2>CONSTITUTION I</h2>
 * Pure mapping (no RNG). Caller provides the codes and queries
 * deterministically.
 */
public final class CodebookMemory {

    private final int maxCapacity;
    private final LinkedHashMap<String, long[]> store;

    /**
     * Create a new memory with bounded capacity. Capacity must be ≥ 1.
     */
    public CodebookMemory(int maxCapacity) {
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("capacity must be ≥ 1");
        }
        this.maxCapacity = maxCapacity;
        this.store = new LinkedHashMap<>(16, 0.75f, true /* access order */);
    }

    /**
     * Store a vector under the given ID. If the ID already exists, replace
     * its value. If the capacity is exceeded, evict the LRU entry.
     */
    public void store(String id, long[] vector) {
        if (id == null) throw new IllegalArgumentException("null id");
        if (vector == null || vector.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("vector wrong length");
        }
        if (store.size() >= maxCapacity && !store.containsKey(id)) {
            // LinkedHashMap with access-order evicts eldest on removeEldestEntry
            // via override; here we just call remove on the eldest key.
            String eldest = store.keySet().iterator().next();
            store.remove(eldest);
        }
        store.put(id, vector);
    }

    /**
     * Query for the nearest entry by Hamming distance.
     * @return {@link Result} with id, distance, and stored vector;
     *         null if the memory is empty.
     */
    public Result query(long[] queryVector) {
        if (queryVector == null || queryVector.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("vector wrong length");
        }
        if (store.isEmpty()) {
            return null;
        }
        String bestId = null;
        long[] bestVec = null;
        int bestDist = Integer.MAX_VALUE;
        for (Map.Entry<String, long[]> e : store.entrySet()) {
            int d = HdcEncoding.hamming(queryVector, e.getValue());
            if (d < bestDist) {
                bestDist = d;
                bestId = e.getKey();
                bestVec = e.getValue();
            }
        }
        return new Result(bestId, bestDist, bestVec);
    }

    /**
     * Query for top-K nearest entries sorted by Hamming distance ascending.
     * Returns at most K results; if fewer than K entries stored, returns all.
     */
    public java.util.List<Result> queryTopK(long[] queryVector, int k) {
        if (queryVector == null || queryVector.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("vector wrong length");
        }
        if (k <= 0 || store.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Result> all = new java.util.ArrayList<>(store.size());
        for (Map.Entry<String, long[]> e : store.entrySet()) {
            int d = HdcEncoding.hamming(queryVector, e.getValue());
            all.add(new Result(e.getKey(), d, e.getValue()));
        }
        all.sort((a, b) -> Integer.compare(a.distance, b.distance));
        if (all.size() > k) return all.subList(0, k);
        return all;
    }

    /**
     * Whether the memory contains the given ID.
     */
    public boolean contains(String id) {
        return store.containsKey(id);
    }

    /**
     * Get the stored vector for an ID, or null if not present.
     */
    public long[] get(String id) {
        return store.get(id);
    }

    /**
     * Remove an entry. Returns true if removed.
     */
    public boolean remove(String id) {
        return store.remove(id) != null;
    }

    /**
     * Current number of entries.
     */
    public int size() {
        return store.size();
    }

    /**
     * Maximum capacity (after which LRU eviction triggers on insert).
     */
    public int capacity() {
        return maxCapacity;
    }

    /**
     * Whether the memory is empty.
     */
    public boolean isEmpty() {
        return store.isEmpty();
    }

    /**
     * Clear all entries.
     */
    public void clear() {
        store.clear();
    }

    /**
     * All IDs in LRU order (least-recently-used first).
     */
    public java.util.Set<String> ids() {
        return new java.util.LinkedHashSet<>(store.keySet());
    }

    /**
     * Query result. Carries the matched ID, Hamming distance, and the
     * stored vector (so callers can do further operations without a
     * second lookup).
     */
    public static final class Result {
        public final String id;
        public final int distance;
        public final long[] vector;

        public Result(String id, int distance, long[] vector) {
            this.id = id;
            this.distance = distance;
            this.vector = vector;
        }

        /** Bipolar similarity in [-1.0, +1.0]. */
        public double similarity() {
            return 1.0 - 2.0 * distance / (double) HdcEncoding.DIM;
        }

        @Override
        public String toString() {
            return "Result{id=" + id + ", dist=" + distance
                    + ", sim=" + String.format("%.4f", similarity()) + "}";
        }
    }
}
