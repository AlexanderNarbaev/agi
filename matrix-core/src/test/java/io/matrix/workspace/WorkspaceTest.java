package io.matrix.workspace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 219 — Workspace unit tests. */
class WorkspaceTest {

    @Test
    void emptyWorkspace() {
        var w = new Workspace();
        assertThat(w.size()).isZero();
    }

    @Test
    void putAndGet() {
        var w = new Workspace();
        w.put("k", "v");
        assertThat(w.get("k")).isEqualTo("v");
        assertThat(w.contains("k")).isTrue();
    }

    @Test
    void typedGet() {
        var w = new Workspace();
        w.put("n", 42);
        Integer v = w.get("n", Integer.class);
        assertThat(v).isEqualTo(42);
    }

    @Test
    void typedGetWrongTypeReturnsNull() {
        var w = new Workspace();
        w.put("n", 42);
        String v = w.get("n", String.class);
        assertThat(v).isNull();
    }

    @Test
    void getReturnsNullForMissing() {
        var w = new Workspace();
        assertThat(w.get("nonexistent")).isNull();
    }

    @Test
    void clearRemovesAll() {
        var w = new Workspace();
        w.put("a", 1);
        w.put("b", 2);
        w.clear();
        assertThat(w.size()).isZero();
    }

    @Test
    void sizeReflectsEntries() {
        var w = new Workspace();
        w.put("a", 1);
        w.put("b", 2);
        w.put("c", 3);
        assertThat(w.size()).isEqualTo(3);
    }
}
