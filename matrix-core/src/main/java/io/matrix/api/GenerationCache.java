package io.matrix.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 101 — LRU cache for prompt → generation results.
 *
 * <p>Identical prompts produce identical outputs (with greedy decoding),
 * so we can cache them. Implements a simple LRU eviction policy.
 *
 * <p>This is a deterministic cache — it works ONLY with greedy/sampling
 * that produces consistent outputs for the same input.
 */
public final class GenerationCache {

    private final long capacity;
    private final LinkedHashMap<String, String> map;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public GenerationCache(long capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                boolean evict = size() > GenerationCache.this.capacity;
                if (evict) evictions.incrementAndGet();
                return evict;
            }
        };
    }

    /** Get cached result for prompt, or null. */
    public String get(String prompt) {
        String result = map.get(prompt);
        if (result != null) hits.incrementAndGet();
        else misses.incrementAndGet();
        return result;
    }

    /** Cache a result. */
    public void put(String prompt, String result) {
        if (prompt == null || result == null) return;
        map.put(prompt, result);
    }

    /** Get-or-compute pattern. */
    public String getOrCompute(String prompt, java.util.function.Function<String, String> fn) {
        String cached = get(prompt);
        if (cached != null) return cached;
        String result = fn.apply(prompt);
        if (result != null) put(prompt, result);
        return result;
    }

    /** Clear the cache. */
    public void clear() {
        map.clear();
    }

    public long size() { return map.size(); }
    public long capacity() { return capacity; }
    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public long evictions() { return evictions.get(); }
    public double hitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }
}
