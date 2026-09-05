package io.matrix.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 115 — Generation history per user/session.
 *
 * <p>Records every generation request and response for audit,
 * replay, or user-facing "history" view. Bounded by capacity per
 * key.
 */
public final class GenerationHistory {

    private final int maxPerKey;
    private final ConcurrentHashMap<String, List<Entry>> history = new ConcurrentHashMap<>();
    private final AtomicLong totalEntries = new AtomicLong();
    private final AtomicLong totalEvictions = new AtomicLong();

    public GenerationHistory(int maxPerKey) {
        if (maxPerKey <= 0) {
            throw new IllegalArgumentException("max must be > 0");
        }
        this.maxPerKey = maxPerKey;
    }

    /** Record a generation. */
    public void record(String key, String prompt, String response, long latencyMs) {
        if (key == null) return;
        List<Entry> entries = history.computeIfAbsent(key,
                k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (entries) {
            // Evict oldest if at capacity
            while (entries.size() >= maxPerKey) {
                entries.remove(0);
                totalEvictions.incrementAndGet();
            }
            entries.add(new Entry(prompt, response, latencyMs,
                    System.currentTimeMillis()));
            totalEntries.incrementAndGet();
        }
    }

    /** Get all entries for a key (newest last). */
    public List<Entry> get(String key) {
        List<Entry> entries = history.get(key);
        if (entries == null) return List.of();
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    /** Clear history for a key. */
    public void clear(String key) {
        List<Entry> removed = history.remove(key);
        if (removed != null) {
            totalEntries.addAndGet(-removed.size());
        }
    }

    /** Clear all history. */
    public void clearAll() {
        long removed = 0;
        for (List<Entry> e : history.values()) removed += e.size();
        history.clear();
        totalEntries.addAndGet(-removed);
    }

    public int keyCount() { return history.size(); }
    public long totalEntries() { return totalEntries.get(); }
    public long totalEvictions() { return totalEvictions.get(); }
    public int maxPerKey() { return maxPerKey; }

    /** A single generation record. */
    public record Entry(String prompt, String response, long latencyMs,
                        long timestampMs) {}
}
