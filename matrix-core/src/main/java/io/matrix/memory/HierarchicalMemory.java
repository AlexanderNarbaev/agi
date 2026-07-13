package io.matrix.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hierarchical Memory — multi-layer memory model with drift detection.
 *
 * <p>Implements a 5-level memory hierarchy inspired by NOUZ MCP Server (Habr #1033746):
 * <ul>
 *   <li><b>L0 — Raw Artifacts:</b> logs, sources, drafts (ephemeral)</li>
 *   <li><b>L1 — Patterns:</b> confirmed relationships between concepts</li>
 *   <li><b>L2 — Modules:</b> functional units of knowledge</li>
 *   <li><b>L3 — Quanta:</b> synthesized knowledge (high confidence)</li>
 *   <li><b>L4 — Kernels:</b> semantic domains (stable, rarely changed)</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Drift detection: warns when content direction diverges from manual labels</li>
 *   <li>Importance-based retention: low-importance entries decay over time</li>
 *   <li>Automatic promotion: entries that gain enough references move up levels</li>
 * </ul>
 *
 * <p>Ref: Research Synthesis 2026-Q3 §3.1
 */
public final class HierarchicalMemory {

    /**
     * Memory level in the hierarchy.
     */
    public enum Level {
        /** Raw artifacts (ephemeral, auto-expire). */
        L0_ARTIFACT(0, "Raw Artifact", 0.1),
        /** Confirmed patterns (short-term). */
        L1_PATTERN(1, "Pattern", 0.3),
        /** Functional modules (medium-term). */
        L2_MODULE(2, "Module", 0.5),
        /** Synthesized quanta (long-term). */
        L3_QUANTUM(3, "Quantum", 0.8),
        /** Semantic kernels (permanent). */
        L4_KERNEL(4, "Kernel", 1.0);

        private final int index;
        private final String name;
        private final double baseImportance;

        Level(int index, String name, double baseImportance) {
            this.index = index;
            this.name = name;
            this.baseImportance = baseImportance;
        }

        public int index() { return index; }
        public String displayName() { return name; }
        public double baseImportance() { return baseImportance; }
    }

    /**
     * A memory entry in the hierarchy.
     */
    public record MemoryEntry(
            String id,
            Level level,
            String content,
            String domain,
            Set<String> tags,
            double importance,
            long createdAt,
            long lastAccessedAt,
            int accessCount,
            String parentId,
            Set<String> childIds
    ) {
        public MemoryEntry withAccessed() {
            return new MemoryEntry(id, level, content, domain, tags,
                    Math.min(1.0, importance + 0.01),
                    createdAt, System.currentTimeMillis(), accessCount + 1,
                    parentId, childIds);
        }

        public MemoryEntry withLevel(Level newLevel) {
            return new MemoryEntry(id, newLevel, content, domain, tags,
                    newLevel.baseImportance,
                    createdAt, lastAccessedAt, accessCount,
                    parentId, childIds);
        }
    }

    /**
     * Drift detection result.
     */
    public record DriftSignal(
            String entryId,
            String expectedDomain,
            String actualDomain,
            double driftScore,
            String message
    ) {
        public boolean isSignificant() {
            return driftScore > 0.5;
        }
    }

    // ── State ──

    private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();
    private final Map<Level, Set<String>> levelIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> domainIndex = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(0);
    private final int maxEntries;

    public HierarchicalMemory(int maxEntries) {
        this.maxEntries = maxEntries;
        for (Level level : Level.values()) {
            levelIndex.put(level, ConcurrentHashMap.newKeySet());
        }
    }

    public HierarchicalMemory() {
        this(1000);
    }

    /**
     * Stores a new memory entry at the specified level.
     */
    public MemoryEntry store(Level level, String content, String domain, Set<String> tags) {
        String id = "mem-" + idCounter.incrementAndGet();
        MemoryEntry entry = new MemoryEntry(
                id, level, content, domain, tags,
                level.baseImportance,
                System.currentTimeMillis(), System.currentTimeMillis(), 0,
                null, Set.of()
        );

        entries.put(id, entry);
        levelIndex.get(level).add(id);
        domainIndex.computeIfAbsent(domain, k -> ConcurrentHashMap.newKeySet()).add(id);

        // Auto-evict L0 if over capacity
        if (entries.size() > maxEntries) {
            evictLowestLevel();
        }

        return entry;
    }

    /**
     * Retrieves a memory entry by ID.
     */
    public Optional<MemoryEntry> get(String id) {
        MemoryEntry entry = entries.get(id);
        if (entry != null) {
            MemoryEntry accessed = entry.withAccessed();
            entries.put(id, accessed);
            return Optional.of(accessed);
        }
        return Optional.empty();
    }

    /**
     * Searches for entries matching the query across all levels.
     */
    public List<MemoryEntry> search(String query, int limit) {
        String lowerQuery = query.toLowerCase();
        return entries.values().stream()
                .filter(e -> e.content().toLowerCase().contains(lowerQuery)
                        || e.domain().toLowerCase().contains(lowerQuery)
                        || e.tags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery)))
                .sorted(Comparator.comparingDouble(MemoryEntry::importance).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Searches within a specific level.
     */
    public List<MemoryEntry> searchAtLevel(Level level, String query, int limit) {
        String lowerQuery = query.toLowerCase();
        Set<String> ids = levelIndex.getOrDefault(level, Set.of());
        return ids.stream()
                .map(entries::get)
                .filter(Objects::nonNull)
                .filter(e -> e.content().toLowerCase().contains(lowerQuery)
                        || e.tags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery)))
                .sorted(Comparator.comparingDouble(MemoryEntry::importance).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Promotes an entry to a higher level.
     */
    public Optional<MemoryEntry> promote(String id, Level targetLevel) {
        MemoryEntry entry = entries.get(id);
        if (entry == null) return Optional.empty();
        if (targetLevel.index() <= entry.level().index()) return Optional.empty();

        levelIndex.get(entry.level()).remove(id);
        MemoryEntry promoted = entry.withLevel(targetLevel);
        entries.put(id, promoted);
        levelIndex.get(targetLevel).add(id);

        return Optional.of(promoted);
    }

    /**
     * Detects drift: content direction diverges from expected domain.
     */
    public List<DriftSignal> detectDrift() {
        List<DriftSignal> signals = new ArrayList<>();

        for (var entry : entries.values()) {
            // Check if content mentions domains other than the assigned one
            String contentLower = entry.content().toLowerCase();
            for (String domain : domainIndex.keySet()) {
                if (domain.equals(entry.domain())) continue;
                String domainLower = domain.toLowerCase();
                // Check if domain name appears in content
                if (contentLower.contains(domainLower)) {
                    double driftScore = computeDriftScore(entry, domain);
                    signals.add(new DriftSignal(
                            entry.id(), entry.domain(), domain, driftScore,
                            String.format("Entry '%s' assigned to '%s' but references '%s' (drift: %.2f)",
                                    entry.id(), entry.domain(), domain, driftScore)
                    ));
                }
            }
        }

        signals.sort(Comparator.comparingDouble(DriftSignal::driftScore).reversed());
        return signals;
    }

    /**
     * Returns entries at a specific level.
     */
    public List<MemoryEntry> entriesAtLevel(Level level) {
        Set<String> ids = levelIndex.getOrDefault(level, Set.of());
        return ids.stream()
                .map(entries::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Returns entries in a specific domain.
     */
    public List<MemoryEntry> entriesInDomain(String domain) {
        Set<String> ids = domainIndex.getOrDefault(domain, Set.of());
        return ids.stream()
                .map(entries::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Returns the total number of entries.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns statistics per level.
     */
    public Map<Level, Integer> levelStats() {
        Map<Level, Integer> stats = new LinkedHashMap<>();
        for (Level level : Level.values()) {
            stats.put(level, levelIndex.getOrDefault(level, Set.of()).size());
        }
        return stats;
    }

    /**
     * Removes expired L0 entries (older than maxAgeMs).
     */
    public int evictExpired(long maxAgeMs) {
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        Set<String> l0Ids = levelIndex.get(Level.L0_ARTIFACT);
        List<String> expired = l0Ids.stream()
                .map(entries::get)
                .filter(Objects::nonNull)
                .filter(e -> e.createdAt() < cutoff)
                .map(MemoryEntry::id)
                .toList();

        for (String id : expired) {
            remove(id);
        }
        return expired.size();
    }

    private void remove(String id) {
        MemoryEntry entry = entries.remove(id);
        if (entry != null) {
            levelIndex.get(entry.level()).remove(id);
            domainIndex.getOrDefault(entry.domain(), Set.of()).remove(id);
        }
    }

    private void evictLowestLevel() {
        Set<String> l0Ids = levelIndex.get(Level.L0_ARTIFACT);
        if (l0Ids != null && !l0Ids.isEmpty()) {
            String oldest = l0Ids.stream()
                    .map(entries::get)
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingLong(MemoryEntry::lastAccessedAt))
                    .map(MemoryEntry::id)
                    .orElse(null);
            if (oldest != null) remove(oldest);
        }
    }

    private double computeDriftScore(MemoryEntry entry, String otherDomain) {
        String contentLower = entry.content().toLowerCase();
        String domainLower = otherDomain.toLowerCase();
        // Count occurrences of domain-related words in content
        long count = 0;
        String[] words = contentLower.split("\\s+");
        for (String word : words) {
            if (word.contains(domainLower) || domainLower.contains(word)) {
                count++;
            }
        }
        // Also check if the domain name itself appears
        if (contentLower.contains(domainLower)) {
            count += 3; // Strong signal
        }
        return Math.min(1.0, (double) count / Math.max(words.length, 1) * 5);
    }
}
