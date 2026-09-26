package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryIndexTest {

    @Test
    void addEdge_creates_bidirectional_link() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        g.addEdge("a", "b");
        assertThat(g.neighbours("a").get("b")).isEqualTo(1.0);
        assertThat(g.neighbours("b").get("a")).isEqualTo(1.0);
    }

    @Test
    void repeated_addEdge_accumulates_weight() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        for (int i = 0; i < 5; i++) g.addEdge("a", "b");
        assertThat(g.neighbours("a").get("b")).isEqualTo(5.0);
    }

    @Test
    void self_edge_ignored() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        g.addEdge("a", "a");
        assertThat(g.nodeCount()).isEqualTo(0);
        assertThat(g.edgeCount()).isEqualTo(0);
    }

    @Test
    void null_edge_safe() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        g.addEdge(null, "b");
        g.addEdge("a", null);
        assertThat(g.nodeCount()).isEqualTo(0);
    }

    @Test
    void spread_returns_neighbours_with_decay() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        g.addEdge("a", "b");
        g.addEdge("a", "c");
        Map<String, Double> res = g.spread("a", 0.5, 1);
        assertThat(res).containsKeys("b", "c");
        assertThat(res.get("b")).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(res.get("c")).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void spread_multiple_hops_reaches_far_nodes() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        g.addEdge("a", "b");
        g.addEdge("b", "c");
        g.addEdge("c", "d");
        Map<String, Double> res = g.spread("a", 0.8, 3);
        assertThat(res).containsKey("b");
        assertThat(res).containsKey("c");
        assertThat(res).containsKey("d");
    }

    @Test
    void spread_excludes_query_node() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        g.addEdge("a", "b");
        Map<String, Double> res = g.spread("a", 0.5, 2);
        assertThat(res).doesNotContainKey("a");
    }

    @Test
    void spread_invalid_args_rejected() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        assertThat(catchThrowable(() -> g.spread("a", 0, 1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> g.spread("a", 1.0, 1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> g.spread("a", 0.5, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spread_from_null_returns_empty() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        Map<String, Double> res = g.spread(null, 0.5, 2);
        assertThat(res).isEmpty();
    }

    @Test
    void node_count_and_edge_count() {
        GraphMemoryIndex g = new GraphMemoryIndex();
        g.addEdge("a", "b");
        g.addEdge("a", "c");
        g.addEdge("b", "c");
        assertThat(g.nodeCount()).isEqualTo(3);
        assertThat(g.edgeCount()).isEqualTo(3);
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
