package io.matrix.noosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.TruthTable;

/**
 * DESIGN-22 §INV-FNL-ONE — Single source of truth for ALL distilled
 * neurons in the process. Regardless of source model (Qwen2.5-0.5B,
 * Llama-3.2-1B, Mistral-7B, GPT-2, etc.), every distilled neuron is
 * appended to this one registry.
 *
 * <p>INV-FNL-ONE (CONSTITUTIONAL-level, enforced 2026-09-11):
 *   - All distillation goes to ONE matrix.
 *   - No per-model 'model-like' files or archives.
 *   - Provenance = metadata (string), not file path.
 *
 * <p>Thread-safe (read-write lock). Append-only in production; only
 * tests may clear().
 */
public final class FnlRegistry {

    private static final FnlRegistry INSTANCE = new FnlRegistry();

    public static FnlRegistry getInstance() { return INSTANCE; }

    private final ConcurrentMap<UUID, FnlEntry> pool = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong appendCount = new AtomicLong();

    private FnlRegistry() {}

    /**
     * Append a single distilled neuron to the pool. Returns the
     * assigned UUID.
     *
     * <p>Required: {@code entry.provenance} must be non-blank
     * (per INV-FNL-ONE, every neuron is traceable to its source model).
     */
    public UUID append(FnlEntry entry) {
        if (entry == null) throw new IllegalArgumentException("entry");
        if (entry.provenance() == null || entry.provenance().isBlank()) {
            throw new IllegalArgumentException(
                    "INV-FNL-ONE: provenance must be set (no per-model files); "
                            + "got blank/null for entry " + entry.id());
        }
        UUID id = entry.id() != null ? entry.id() : UUID.randomUUID();
        FnlEntry stored = entry.id() == null
                ? entry.withId(id)
                : entry;
        lock.writeLock().lock();
        try {
            pool.put(id, stored);
            appendCount.incrementAndGet();
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Bulk append: multiple neurons from the same distillation run.
     * Each entry must have provenance set.
     */
    public List<UUID> appendAll(List<FnlEntry> entries) {
        if (entries == null) throw new IllegalArgumentException("entries");
        List<UUID> ids = new ArrayList<>(entries.size());
        for (FnlEntry e : entries) ids.add(append(e));
        return ids;
    }

    public FnlEntry get(UUID id) {
        lock.readLock().lock();
        try {
            return pool.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** All neurons from a specific source model (e.g. "Qwen2.5-0.5B"). */
    public List<FnlEntry> byProvenance(String modelName) {
        if (modelName == null) throw new IllegalArgumentException("modelName");
        lock.readLock().lock();
        try {
            List<FnlEntry> result = new ArrayList<>();
            for (FnlEntry e : pool.values()) {
                if (modelName.equals(e.provenance())) result.add(e);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** All neurons in the pool (snapshot copy). */
    public List<FnlEntry> all() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(pool.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Distinct provenance strings (which models have contributed). */
    public List<String> provenances() {
        lock.readLock().lock();
        try {
            return pool.values().stream()
                    .map(FnlEntry::provenance)
                    .distinct()
                    .sorted()
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Number of neurons in the pool (across all provenances). */
    public int size() {
        return pool.size();
    }

    /** Total number of successful appends (lifetime counter). */
    public long totalAppends() {
        return appendCount.get();
    }

    /** Per-provenance counts: e.g. {Qwen2.5-0.5B=21960, Llama-3.2-1B=30000}. */
    public java.util.Map<String, Long> countsByProvenance() {
        java.util.Map<String, Long> result = new java.util.TreeMap<>();
        lock.readLock().lock();
        try {
            for (FnlEntry e : pool.values()) {
                result.merge(e.provenance(), 1L, Long::sum);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Reset (test-only). */
    public void clear() {
        lock.writeLock().lock();
        try {
            pool.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
