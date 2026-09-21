package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 262 — CycleTag unit tests. */
class CycleTagTest {

    @Test
    void emptyTagSet() {
        var t = new CycleTag();
        assertThat(t.size()).isZero();
    }

    @Test
    void addAndContains() {
        var t = new CycleTag();
        assertThat(t.add("important")).isTrue();
        assertThat(t.contains("important")).isTrue();
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    void removeTag() {
        var t = new CycleTag();
        t.add("x");
        assertThat(t.remove("x")).isTrue();
        assertThat(t.contains("x")).isFalse();
    }

    @Test
    void duplicateAddReturnsFalse() {
        var t = new CycleTag();
        t.add("x");
        assertThat(t.add("x")).isFalse();
        assertThat(t.size()).isEqualTo(1);
    }

    @Test
    void hasAllSubset() {
        var t = new CycleTag();
        t.add("a"); t.add("b"); t.add("c");
        assertThat(t.hasAll(Set.of("a", "b"))).isTrue();
        assertThat(t.hasAll(Set.of("a", "d"))).isFalse();
    }

    @Test
    void hasAnyOverlaps() {
        var t = new CycleTag();
        t.add("a"); t.add("b");
        assertThat(t.hasAny(Set.of("b", "c"))).isTrue();
        assertThat(t.hasAny(Set.of("x", "y"))).isFalse();
    }

    @Test
    void allReturnsCopy() {
        var t = new CycleTag();
        t.add("a");
        Set<String> copy = t.all();
        copy.add("injected");
        assertThat(t.size()).isEqualTo(1);
    }
}
