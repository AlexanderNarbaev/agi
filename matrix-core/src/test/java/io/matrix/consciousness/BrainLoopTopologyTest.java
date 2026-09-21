package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 246 — BrainLoopTopology unit tests. */
class BrainLoopTopologyTest {

    @Test
    void nodesAreComplete() {
        var nodes = BrainLoopTopology.nodes();
        assertThat(nodes).hasSize(7);
        assertThat(nodes).extracting("name")
                .contains("perception", "saliency", "attention",
                        "deliberation", "gate", "action", "trace");
    }

    @Test
    void edgesArePresent() {
        var edges = BrainLoopTopology.edges();
        assertThat(edges).hasSize(8);
        // 5 dataflow + 3 audit edges
        long dataflow = edges.stream()
                .filter(e -> "dataflow".equals(e.type())).count();
        long audit = edges.stream()
                .filter(e -> "audit".equals(e.type())).count();
        assertThat(dataflow).isEqualTo(5);
        assertThat(audit).isEqualTo(3);
    }

    @Test
    void nodeCount() {
        assertThat(BrainLoopTopology.nodeCount()).isEqualTo(7);
    }

    @Test
    void edgeCount() {
        assertThat(BrainLoopTopology.edgeCount()).isEqualTo(8);
    }

    @Test
    void nodeRecord() {
        var n = new BrainLoopTopology.Node("X", "Y role");
        assertThat(n.name()).isEqualTo("X");
        assertThat(n.role()).isEqualTo("Y role");
    }

    @Test
    void edgeRecord() {
        var e = new BrainLoopTopology.Edge("A", "B", "dataflow");
        assertThat(e.from()).isEqualTo("A");
        assertThat(e.to()).isEqualTo("B");
        assertThat(e.type()).isEqualTo("dataflow");
    }
}
