package io.matrix.ktopo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KtopoTest {

    @Test
    void knowledgeGraphBasics() {
        var g = new KnowledgeGraph();
        g.addNode(new KnowledgeGraph.KnowledgeNode("n1", "content1", "math", "test", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("n2", "content2", "math", "test", 0, 0, 1.0));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("n1", "n2", "related", 1.0, "test"));

        assertEquals(2, g.nodeCount());
        assertEquals(1, g.edgeCount());
        assertEquals(1, g.neighbors("n1").size());
        assertEquals(1, g.neighbors("n2").size());
    }

    @Test
    void domainFilter() {
        var g = new KnowledgeGraph();
        g.addNode(new KnowledgeGraph.KnowledgeNode("n1", "math1", "math", "test", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("n2", "phys1", "physics", "test", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("n3", "math2", "math", "test", 0, 0, 1.0));

        var mathNodes = g.nodesInDomain("math");
        assertEquals(2, mathNodes.size());
    }

    @Test
    void degreeDistribution() {
        var g = new KnowledgeGraph();
        g.addNode(new KnowledgeGraph.KnowledgeNode("a", "", "d", "t", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("b", "", "d", "t", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("c", "", "d", "t", 0, 0, 1.0));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("a", "b", "r", 1.0, "t"));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("a", "c", "r", 1.0, "t"));

        var dist = g.degreeDistribution();
        assertEquals(2, dist.get("a")); // a has 2 neighbors
        assertEquals(1, dist.get("b"));
        assertEquals(1, dist.get("c"));
    }

    @Test
    void ricciCurvature() {
        var g = new KnowledgeGraph();
        // Triangle: a-b-c-a (dense community)
        g.addNode(new KnowledgeGraph.KnowledgeNode("a", "", "d", "t", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("b", "", "d", "t", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("c", "", "d", "t", 0, 0, 1.0));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("a", "b", "r", 1.0, "t"));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("b", "c", "r", 1.0, "t"));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("a", "c", "r", 1.0, "t"));

        var flow = new RicciFlow(g);
        var curvatures = flow.computeCurvatures();
        assertEquals(3, curvatures.size());
        // All edges in triangle have positive curvature (shared neighbor)
        for (double c : curvatures.values()) {
            assertTrue(c > 0);
        }
    }

    @Test
    void ricciFragileBridge() {
        var g = new KnowledgeGraph();
        // Two triangles connected by single bridge
        for (String n : new String[]{"a", "b", "c", "d", "e", "f"}) {
            g.addNode(new KnowledgeGraph.KnowledgeNode(n, "", "d", "t", 0, 0, 1.0));
        }
        // Triangle 1: a-b-c
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("a", "b", "r", 1.0, "t"));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("b", "c", "r", 1.0, "t"));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("a", "c", "r", 1.0, "t"));
        // Triangle 2: d-e-f
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("d", "e", "r", 1.0, "t"));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("e", "f", "r", 1.0, "t"));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("d", "f", "r", 1.0, "t"));
        // Bridge: c-d
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("c", "d", "r", 1.0, "t"));

        var flow = new RicciFlow(g);
        var bridges = flow.detectFragileBridges();
        assertFalse(bridges.isEmpty());
        assertTrue(bridges.contains("c→d"));
    }

    @Test
    void driftFingerprint() {
        var g = new KnowledgeGraph();
        g.addNode(new KnowledgeGraph.KnowledgeNode("a", "", "d", "t", 0, 0, 1.0));
        g.addNode(new KnowledgeGraph.KnowledgeNode("b", "", "d", "t", 0, 0, 1.0));
        g.addEdge(new KnowledgeGraph.KnowledgeEdge("a", "b", "r", 1.0, "t"));

        var flow = new RicciFlow(g);
        double[] fp = flow.driftFingerprint();
        assertEquals(10, fp.length);
        // Sum should be 1.0 (normalized)
        double sum = 0;
        for (double v : fp) sum += v;
        assertEquals(1.0, sum, 0.001);
    }
}
