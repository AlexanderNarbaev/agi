package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 286 — CycleFilter unit tests. */
class CycleFilterTest {

    @Test
    void nonEmptyFilter() {
        var f = CycleFilter.nonEmpty();
        assertThat(f.accepts("hello")).isTrue();
        assertThat(f.accepts("")).isFalse();
        assertThat(f.accepts("   ")).isFalse();
        assertThat(f.accepts(null)).isFalse();
    }

    @Test
    void safeFilter() {
        var f = CycleFilter.safe();
        assertThat(f.accepts("hello")).isTrue();
        assertThat(f.accepts("hello\u0001world")).isFalse();
        assertThat(f.accepts("rm -rf $(echo /)")).isFalse();
    }

    @Test
    void maxLengthFilter() {
        var f = CycleFilter.maxLength(5);
        assertThat(f.accepts("hello")).isTrue();
        assertThat(f.accepts("hello!")).isFalse();
    }

    @Test
    void allCombinesFilters() {
        var f = CycleFilter.all(CycleFilter.nonEmpty(), CycleFilter.maxLength(5));
        assertThat(f.accepts("hello")).isTrue();
        assertThat(f.accepts("")).isFalse();       // non-empty fails
        assertThat(f.accepts("toolong")).isFalse(); // maxLength fails
    }

    @Test
    void nullInputAlwaysRejected() {
        assertThat(CycleFilter.nonEmpty().accepts(null)).isFalse();
        assertThat(CycleFilter.safe().accepts(null)).isFalse();
        assertThat(CycleFilter.maxLength(5).accepts(null)).isFalse();
    }
}
