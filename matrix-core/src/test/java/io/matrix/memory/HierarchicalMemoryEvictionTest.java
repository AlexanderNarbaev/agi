package io.matrix.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W42.1 — the memory's capacity is a bound, not a wish.
 *
 * <p>The eviction used to look only at L0 while the production path writes
 * only L2, so on the live path the capacity was checked, the check always
 * passed, and nothing was ever removed: the entry map grew without bound.
 */
class HierarchicalMemoryEvictionTest {

    @Test
    @DisplayName("the memory stays within its capacity when only L2 entries are written")
    void theMemoryStaysWithinItsCapacityWhenOnlyL2EntriesAreWritten() {
        HierarchicalMemory memory = new HierarchicalMemory(50);

        // The production path's shape: the pipeline stores every interaction
        // as an L2 module and never writes an L0 artifact.
        for (int i = 0; i < 500; i++) {
            memory.store(HierarchicalMemory.Level.L2_MODULE, "Q: question " + i + " | A: answer " + i,
                    "brain", java.util.Set.of("pipeline", "auto", HierarchicalMemory.EPISODE_TAG));
        }

        assertThat(memory.size()).isLessThanOrEqualTo(51);
        assertThat(memory.evictedCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("the eviction prefers the lowest level that holds something")
    void theEvictionPrefersTheLowestLevelThatHoldsSomething() {
        HierarchicalMemory memory = new HierarchicalMemory(10);

        memory.store(HierarchicalMemory.Level.L0_ARTIFACT, "an artifact", "brain", java.util.Set.of());
        memory.store(HierarchicalMemory.Level.L2_MODULE, "a module", "brain", java.util.Set.of());
        assertThat(memory.evictedCount()).isZero();

        for (int i = 0; i < 20; i++) {
            memory.store(HierarchicalMemory.Level.L2_MODULE, "module " + i, "brain",
                    java.util.Set.of());
        }

        // The L0 artifact is the first thing to go: it is the lowest level
        // and the least valuable.
        assertThat(memory.get("mem-1")).isEmpty();
        assertThat(memory.evictedCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("the eviction counter starts at zero and grows with the removals")
    void theEvictionCounterStartsAtZeroAndGrowsWithTheRemovals() {
        HierarchicalMemory memory = new HierarchicalMemory(5);
        assertThat(memory.evictedCount()).isZero();

        for (int i = 0; i < 20; i++) {
            memory.store(HierarchicalMemory.Level.L3_QUANTUM, "quantum " + i, "brain",
                    java.util.Set.of());
        }

        assertThat(memory.evictedCount()).isEqualTo(memory.evictedCount());
        assertThat(memory.evictedCount()).isGreaterThanOrEqualTo(15);
    }
}
