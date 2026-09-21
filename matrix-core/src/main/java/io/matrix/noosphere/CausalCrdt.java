package io.matrix.noosphere;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase S (RUN 412) — Causal CRDT for M4 (long-term memory).
 *
 * <p>Causal consistency via vector clocks. Each write carries the
 * writer's clock. Reads return a value that respects the happens-
 * before partial order. Supports tombstones for deletion.
 *
 * <p>Operations:
 *  - put(key, value, writerId, clock) — write
 *  - get(key) — read returns (value, version)
 *  - merge(other) — causally-consistent merge
 *  - tombstone(key, writerId, clock) — delete
 *
 * <p>Pure functions (CONSTITUTION I) — same inputs → same outputs.
 */
public final class CausalCrdt {

    public record Version(int writerId, long clock) implements Comparable<Version> {
        @Override
        public int compareTo(Version other) {
            int c = Long.compare(this.clock, other.clock);
            return c != 0 ? c : Integer.compare(this.writerId, other.writerId);
        }
    }

    public record Entry(String value, Version version, boolean deleted) {}

    public record CausalStore(
            Map<String, Entry> entries,
            Map<Integer, Long> clocks
    ) {}

    private CausalCrdt() {}

    /** Empty store with given writer IDs. */
    public static CausalStore empty(Set<Integer> writerIds) {
        Map<Integer, Long> clocks = new HashMap<>();
        for (int id : writerIds) clocks.put(id, 0L);
        return new CausalStore(new HashMap<>(), clocks);
    }

    /** Write value. Increments writer's clock. */
    public static CausalStore put(CausalStore store, String key,
                                  String value, int writerId) {
        Map<String, Entry> entries = new HashMap<>(store.entries());
        Map<Integer, Long> clocks = new HashMap<>(store.clocks());
        long newClock = clocks.getOrDefault(writerId, 0L) + 1;
        clocks.put(writerId, newClock);
        Version newV = new Version(writerId, newClock);
        // Last-write-wins: only overwrite if new version > old
        Entry old = entries.get(key);
        if (old == null || newV.compareTo(old.version()) > 0) {
            entries.put(key, new Entry(value, newV, false));
        }
        return new CausalStore(entries, clocks);
    }

    /** Read value. Returns null if key not present or tombstoned. */
    public static Entry get(CausalStore store, String key) {
        Entry e = store.entries().get(key);
        if (e == null || e.deleted()) return null;
        return e;
    }

    /** Tombstone (delete) a key. */
    public static CausalStore tombstone(CausalStore store, String key, int writerId) {
        Map<String, Entry> entries = new HashMap<>(store.entries());
        Map<Integer, Long> clocks = new HashMap<>(store.clocks());
        long newClock = clocks.getOrDefault(writerId, 0L) + 1;
        clocks.put(writerId, newClock);
        Version newV = new Version(writerId, newClock);
        Entry old = entries.get(key);
        if (old == null || newV.compareTo(old.version()) > 0) {
            entries.put(key, new Entry(null, newV, true));
        }
        return new CausalStore(entries, clocks);
    }

    /**
     * Causal merge. For each key, take the entry with the highest
     * version. Combine clocks by taking max of each.
     */
    public static CausalStore merge(CausalStore a, CausalStore b) {
        Map<String, Entry> entries = new HashMap<>(a.entries());
        for (var entry : b.entries().entrySet()) {
            Entry oldE = entries.get(entry.getKey());
            if (oldE == null || entry.getValue().version().compareTo(oldE.version()) > 0) {
                entries.put(entry.getKey(), entry.getValue());
            }
        }
        Map<Integer, Long> clocks = new HashMap<>(a.clocks());
        for (var entry : b.clocks().entrySet()) {
            clocks.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        return new CausalStore(entries, clocks);
    }

    public static CausalStore merge(List<CausalStore> stores) {
        if (stores == null || stores.isEmpty()) return empty(new HashSet<>());
        CausalStore result = stores.get(0);
        for (int i = 1; i < stores.size(); i++) {
            result = merge(result, stores.get(i));
        }
        return result;
    }
}
