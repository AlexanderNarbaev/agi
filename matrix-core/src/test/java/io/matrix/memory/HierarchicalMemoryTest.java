package io.matrix.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchicalMemoryTest {

    private HierarchicalMemory memory;

    @BeforeEach
    void setup() {
        memory = new HierarchicalMemory(100);
    }

    // ── Storage ──

    @Test
    void shouldStoreEntry() {
        var entry = memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Neural networks learn patterns", "AI", Set.of("neural", "learning"));

        assertThat(entry.id()).startsWith("mem-");
        assertThat(entry.level()).isEqualTo(HierarchicalMemory.Level.L1_PATTERN);
        assertThat(entry.content()).isEqualTo("Neural networks learn patterns");
        assertThat(entry.domain()).isEqualTo("AI");
        assertThat(entry.tags()).contains("neural", "learning");
        assertThat(entry.importance()).isEqualTo(0.3);
    }

    @Test
    void shouldRetrieveEntry() {
        var stored = memory.store(HierarchicalMemory.Level.L2_MODULE,
                "RAG system", "search", Set.of("rag"));

        var retrieved = memory.get(stored.id());

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().content()).isEqualTo("RAG system");
    }

    @Test
    void shouldUpdateAccessCount() {
        var stored = memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "test", "domain", Set.of());

        memory.get(stored.id());
        memory.get(stored.id());
        var entry = memory.get(stored.id());

        assertThat(entry.get().accessCount()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyForMissingId() {
        assertThat(memory.get("nonexistent")).isEmpty();
    }

    // ── Search ──

    @Test
    void shouldSearchByContent() {
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Neural networks are powerful", "AI", Set.of());
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Database optimization techniques", "DB", Set.of());
        memory.store(HierarchicalMemory.Level.L2_MODULE,
                "Deep neural network architecture", "AI", Set.of());

        var results = memory.search("neural", 10);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.content().toLowerCase().contains("neural"));
    }

    @Test
    void shouldSearchByDomain() {
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Pattern A", "AI", Set.of());
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Pattern B", "DB", Set.of());

        var results = memory.search("AI", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).domain()).isEqualTo("AI");
    }

    @Test
    void shouldSearchByTags() {
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Content", "domain", Set.of("important", "neural"));

        var results = memory.search("neural", 10);

        assertThat(results).hasSize(1);
    }

    @Test
    void shouldLimitSearchResults() {
        for (int i = 0; i < 20; i++) {
            memory.store(HierarchicalMemory.Level.L1_PATTERN,
                    "pattern " + i, "domain", Set.of());
        }

        var results = memory.search("pattern", 5);

        assertThat(results).hasSize(5);
    }

    @Test
    void shouldSearchAtSpecificLevel() {
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Pattern content", "domain", Set.of());
        memory.store(HierarchicalMemory.Level.L3_QUANTUM,
                "Quantum content", "domain", Set.of());

        var results = memory.searchAtLevel(HierarchicalMemory.Level.L3_QUANTUM,
                "content", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).level()).isEqualTo(HierarchicalMemory.Level.L3_QUANTUM);
    }

    // ── Promotion ──

    @Test
    void shouldPromoteEntry() {
        var stored = memory.store(HierarchicalMemory.Level.L0_ARTIFACT,
                "Important finding", "AI", Set.of());

        var promoted = memory.promote(stored.id(), HierarchicalMemory.Level.L2_MODULE);

        assertThat(promoted).isPresent();
        assertThat(promoted.get().level()).isEqualTo(HierarchicalMemory.Level.L2_MODULE);
        assertThat(promoted.get().importance()).isEqualTo(0.5);
    }

    @Test
    void shouldNotPromoteToLowerLevel() {
        var stored = memory.store(HierarchicalMemory.Level.L2_MODULE,
                "content", "domain", Set.of());

        var result = memory.promote(stored.id(), HierarchicalMemory.Level.L1_PATTERN);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotPromoteNonexistent() {
        var result = memory.promote("nonexistent", HierarchicalMemory.Level.L3_QUANTUM);

        assertThat(result).isEmpty();
    }

    // ── Level Statistics ──

    @Test
    void shouldTrackLevelStats() {
        memory.store(HierarchicalMemory.Level.L0_ARTIFACT, "a", "d", Set.of());
        memory.store(HierarchicalMemory.Level.L0_ARTIFACT, "b", "d", Set.of());
        memory.store(HierarchicalMemory.Level.L1_PATTERN, "c", "d", Set.of());
        memory.store(HierarchicalMemory.Level.L3_QUANTUM, "d", "d", Set.of());

        var stats = memory.levelStats();

        assertThat(stats.get(HierarchicalMemory.Level.L0_ARTIFACT)).isEqualTo(2);
        assertThat(stats.get(HierarchicalMemory.Level.L1_PATTERN)).isEqualTo(1);
        assertThat(stats.get(HierarchicalMemory.Level.L2_MODULE)).isZero();
        assertThat(stats.get(HierarchicalMemory.Level.L3_QUANTUM)).isEqualTo(1);
        assertThat(stats.get(HierarchicalMemory.Level.L4_KERNEL)).isZero();
    }

    // ── Domain Index ──

    @Test
    void shouldIndexByDomain() {
        memory.store(HierarchicalMemory.Level.L1_PATTERN, "a", "AI", Set.of());
        memory.store(HierarchicalMemory.Level.L1_PATTERN, "b", "AI", Set.of());
        memory.store(HierarchicalMemory.Level.L1_PATTERN, "c", "DB", Set.of());

        var aiEntries = memory.entriesInDomain("AI");
        var dbEntries = memory.entriesInDomain("DB");

        assertThat(aiEntries).hasSize(2);
        assertThat(dbEntries).hasSize(1);
    }

    // ── Eviction ──

    @Test
    void shouldEvictExpiredEntries() throws InterruptedException {
        memory.store(HierarchicalMemory.Level.L0_ARTIFACT, "old", "d", Set.of());

        // Wait a bit to ensure the entry is older than 0ms
        Thread.sleep(10);

        // Evict entries older than 1ms (all should be evicted)
        int evicted = memory.evictExpired(1);

        assertThat(evicted).isEqualTo(1);
        assertThat(memory.size()).isZero();
    }

    @Test
    void shouldNotEvictNonL0Entries() {
        memory.store(HierarchicalMemory.Level.L1_PATTERN, "pattern", "d", Set.of());

        int evicted = memory.evictExpired(0);

        assertThat(evicted).isZero();
        assertThat(memory.size()).isEqualTo(1);
    }

    // ── Drift Detection ──

    @Test
    void shouldDetectDrift() {
        // Store entry in AI domain but content heavily mentions DB domain
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "This is about DB and database and SQL", "AI", Set.of());
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Another AI pattern about neural networks", "AI", Set.of());
        memory.store(HierarchicalMemory.Level.L1_PATTERN,
                "Pure DB pattern about databases", "DB", Set.of());

        var signals = memory.detectDrift();

        // The first entry should show drift from AI to DB
        assertThat(signals).isNotEmpty();
        assertThat(signals.get(0).expectedDomain()).isEqualTo("AI");
        assertThat(signals.get(0).actualDomain()).isEqualTo("DB");
    }

    // ── Capacity ──

    @Test
    void shouldTrackSize() {
        assertThat(memory.size()).isZero();

        memory.store(HierarchicalMemory.Level.L0_ARTIFACT, "a", "d", Set.of());
        assertThat(memory.size()).isEqualTo(1);

        memory.store(HierarchicalMemory.Level.L1_PATTERN, "b", "d", Set.of());
        assertThat(memory.size()).isEqualTo(2);
    }

    @Test
    void shouldHandleDefaultCapacity() {
        var defaultMemory = new HierarchicalMemory();
        assertThat(defaultMemory.size()).isZero();
    }

    // ── Levels ──

    @Test
    void shouldHaveCorrectLevelProperties() {
        assertThat(HierarchicalMemory.Level.L0_ARTIFACT.index()).isZero();
        assertThat(HierarchicalMemory.Level.L4_KERNEL.index()).isEqualTo(4);
        assertThat(HierarchicalMemory.Level.L0_ARTIFACT.baseImportance()).isEqualTo(0.1);
        assertThat(HierarchicalMemory.Level.L4_KERNEL.baseImportance()).isEqualTo(1.0);
    }

    @Test
    void shouldHaveLevelDisplayNames() {
        assertThat(HierarchicalMemory.Level.L0_ARTIFACT.displayName()).isEqualTo("Raw Artifact");
        assertThat(HierarchicalMemory.Level.L4_KERNEL.displayName()).isEqualTo("Kernel");
    }
}
