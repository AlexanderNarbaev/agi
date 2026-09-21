package io.matrix.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 161 — MemoryHierarchyTier unit tests. */
class MemoryHierarchyTierTest {

    @Test
    void allThreeTiersExist() {
        MemoryHierarchyTier[] tiers = MemoryHierarchyTier.values();
        assertThat(tiers).hasSize(3);
        assertThat(MemoryHierarchyTier.valueOf("M0")).isNotNull();
        assertThat(MemoryHierarchyTier.valueOf("M1")).isNotNull();
        assertThat(MemoryHierarchyTier.valueOf("M2")).isNotNull();
    }

    @Test
    void tiersAreDistinct() {
        assertThat(MemoryHierarchyTier.M0).isNotEqualTo(MemoryHierarchyTier.M1);
        assertThat(MemoryHierarchyTier.M1).isNotEqualTo(MemoryHierarchyTier.M2);
        assertThat(MemoryHierarchyTier.M0).isNotEqualTo(MemoryHierarchyTier.M2);
    }

    @Test
    void ordinalsReflectHierarchy() {
        // M0 → 0 (working), M1 → 1 (episodic), M2 → 2 (long-term)
        assertThat(MemoryHierarchyTier.M0.ordinal()).isZero();
        assertThat(MemoryHierarchyTier.M1.ordinal()).isEqualTo(1);
        assertThat(MemoryHierarchyTier.M2.ordinal()).isEqualTo(2);
    }
}
