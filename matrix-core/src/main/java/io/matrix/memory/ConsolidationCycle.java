package io.matrix.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 162 — Consolidation cycle (TR + REM).
 *
 * <p>Two-stage memory consolidation per DESIGN-19:
 * <ul>
 *   <li><b>TR</b> (trigger): M2 → M1 — transfers episodic memories
 *       from long-term into working range</li>
 *   <li><b>REM</b>: M1 → M0 — promotes frequently-accessed items
 *       into working memory</li>
 * </ul>
 *
 * <p>The consolidation is fully deterministic. Frequency is a
 * simple access counter (no time-decay). Memories are
 * {@link MemoryEntry} records that move between tiers.
 */
public final class ConsolidationCycle {

    public record MemoryEntry(String key, byte[] payload,
                              MemoryHierarchyTier tier, int accessCount) {}

    public enum Stage { TR, REM }

    private final List<MemoryEntry> m0 = new ArrayList<>();
    private final List<MemoryEntry> m1 = new ArrayList<>();
    private final List<MemoryEntry> m2 = new ArrayList<>();

    private int trSteps = 0;
    private int remSteps = 0;

    /** Add or replace a memory in M2. */
    public void store(String key, byte[] payload) {
        MemoryEntry e = new MemoryEntry(key, payload, MemoryHierarchyTier.M2, 0);
        replace(m2, e);
    }

    /** One full cycle (TR + REM). */
    public void tick() {
        tr();
        rem();
    }

    /** TR: M2 → M1 for any M2 entry that has an incoming request key. */
    public int tr() {
        for (MemoryEntry e : new ArrayList<>(m2)) {
            // Promote to M1 (TR consolidation)
            m2.remove(e);
            MemoryEntry promoted = new MemoryEntry(
                    e.key(), e.payload(), MemoryHierarchyTier.M1, e.accessCount());
            replace(m1, promoted);
            trSteps++;
        }
        return trSteps;
    }

    /** REM: high-frequency M1 → M0. */
    public int rem() {
        for (MemoryEntry e : new ArrayList<>(m1)) {
            if (e.accessCount() >= 1) {
                m1.remove(e);
                replace(m0, new MemoryEntry(
                        e.key(), e.payload(), MemoryHierarchyTier.M0, e.accessCount()));
                remSteps++;
            }
        }
        return remSteps;
    }

    /** Mark an M0/M1/M2 entry as accessed (increments counter). */
    public void access(String key) {
        for (MemoryEntry e : m0) if (e.key().equals(key)) { incr(e, m0); return; }
        for (MemoryEntry e : m1) if (e.key().equals(key)) { incr(e, m1); return; }
        for (MemoryEntry e : m2) if (e.key().equals(key)) { incr(e, m2); return; }
    }

    private void incr(MemoryEntry e, List<MemoryEntry> list) {
        int idx = list.indexOf(e);
        if (idx >= 0) {
            list.set(idx, new MemoryEntry(
                    e.key(), e.payload(), e.tier(), e.accessCount() + 1));
        }
    }

    private void replace(List<MemoryEntry> list, MemoryEntry e) {
        list.removeIf(x -> x.key().equals(e.key()));
        list.add(e);
    }

    public int m0Size() { return m0.size(); }
    public int m1Size() { return m1.size(); }
    public int m2Size() { return m2.size(); }
    public int trSteps() { return trSteps; }
    public int remSteps() { return remSteps; }

    public byte[] get(String key) {
        for (MemoryEntry e : m0) if (e.key().equals(key)) return e.payload();
        for (MemoryEntry e : m1) if (e.key().equals(key)) return e.payload();
        for (MemoryEntry e : m2) if (e.key().equals(key)) return e.payload();
        return null;
    }
}
