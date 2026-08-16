package io.matrix.memory.query;

import io.matrix.memory.HierarchicalMemory;
import io.matrix.memory.SqliteMemoryBackend;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Entity-aware query over the memory backend.
 *
 * <p>Supports domain-prefixed entity extraction and time-window queries.
 * Integrates with {@link SqliteMemoryBackend} for persistence-aware retrieval.
 */
public final class EntityQuery {

    private final SqliteMemoryBackend backend;

    public EntityQuery(SqliteMemoryBackend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    /**
     * Finds all entries in a given domain.
     */
    public List<HierarchicalMemory.MemoryEntry> findByDomain(String domain) {
        if (domain == null || domain.isBlank()) return Collections.emptyList();
        return backend.searchByDomain(domain);
    }

    /**
     * Groups all entries by their domain, returning domain → count.
     */
    public Map<String, Long> domainStats() {
        return backend.loadAll().values().stream()
                .filter(e -> e.domain() != null && !e.domain().isBlank())
                .collect(Collectors.groupingBy(
                        HierarchicalMemory.MemoryEntry::domain,
                        Collectors.counting()));
    }

    /**
     * Queries entries created within a time window.
     */
    public List<HierarchicalMemory.MemoryEntry> findByTimeRange(long startMs, long endMs) {
        return backend.listByTimeRange(startMs, endMs);
    }

    /**
     * Finds entries referencing a specific parent entry (children).
     */
    public List<HierarchicalMemory.MemoryEntry> findChildren(String parentId) {
        if (parentId == null) return Collections.emptyList();
        return backend.loadAll().values().stream()
                .filter(e -> parentId.equals(e.parentId()))
                .collect(Collectors.toList());
    }

    /**
     * Finds entries at a specific hierarchy level.
     */
    public List<HierarchicalMemory.MemoryEntry> findByLevel(HierarchicalMemory.Level level) {
        return backend.listByLevel(level.index());
    }

    /**
     * Returns the total number of entries.
     */
    public int count() {
        return backend.count();
    }
}
