package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 244 — CycleMerger unit tests. */
class CycleMergerTest {

    @Test
    void emptyMerge() {
        var r = CycleMerger.merge(List.of());
        assertThat(r.merged()).isEmpty();
        assertThat(r.duplicates()).isZero();
    }

    @Test
    void noDuplicatesUnchanged() {
        var r = CycleMerger.merge(List.of("a", "b", "c"));
        assertThat(r.merged()).hasSize(3);
        assertThat(r.duplicates()).isZero();
    }

    @Test
    void adjacentDuplicatesRemoved() {
        var r = CycleMerger.merge(List.of("a", "a", "b", "b", "c"));
        assertThat(r.merged()).containsExactly("a", "b", "c");
        assertThat(r.duplicates()).isEqualTo(2);
    }

    @Test
    void nonAdjacentDuplicatesAlsoRemoved() {
        var r = CycleMerger.merge(List.of("a", "b", "a", "c", "a"));
        assertThat(r.merged()).containsExactly("a", "b", "c");
        assertThat(r.duplicates()).isEqualTo(2);
    }

    @Test
    void allDuplicates() {
        var r = CycleMerger.merge(List.of("x", "x", "x", "x"));
        assertThat(r.merged()).containsExactly("x");
        assertThat(r.duplicates()).isEqualTo(3);
    }

    @Test
    void savedCyclesMatches() {
        var r = CycleMerger.merge(List.of("a", "b", "a", "b"));
        assertThat(CycleMerger.savedCycles(r)).isEqualTo(2);
    }
}
