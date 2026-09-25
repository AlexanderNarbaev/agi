package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W578 — Tests for Causal Graph Engine.
 */
class CausalGraphTest {

    @Test
    void testAddEdge() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");

        assertTrue(graph.getDirectEffects("A").contains("B"));
        assertTrue(graph.getDirectEffects("B").contains("C"));
    }

    @Test
    void testExplainCause() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("rain", "wet_ground");
        graph.addEdge("wet_ground", "slip");

        List<String> chain = graph.explainCause("slip");
        assertEquals(3, chain.size());
        assertEquals("rain", chain.get(0)); // Root cause first
        assertEquals("wet_ground", chain.get(1));
        assertEquals("slip", chain.get(2));
    }

    @Test
    void testGetEffects() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("A", "D");

        Set<String> effects = graph.getEffects("A");
        assertEquals(3, effects.size());
        assertTrue(effects.contains("B"));
        assertTrue(effects.contains("C"));
        assertTrue(effects.contains("D"));
    }

    @Test
    void testCounterfactual() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("rain", "wet_ground");
        graph.addEdge("wet_ground", "slip");

        Set<String> wouldNotHappen = graph.counterfactual("rain");
        assertTrue(wouldNotHappen.contains("wet_ground"));
        assertTrue(wouldNotHappen.contains("slip"));
    }

    @Test
    void testCounterfactualWithMultipleCauses() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("rain", "wet_ground");
        graph.addEdge("sprinkler", "wet_ground");
        graph.addEdge("wet_ground", "slip");

        // Removing rain alone doesn't remove wet_ground (sprinkler still causes it)
        Set<String> wouldNotHappen = graph.counterfactual("rain");
        assertFalse(wouldNotHappen.contains("wet_ground")); // sprinkler still causes it
    }

    @Test
    void testHasCycles() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");

        assertFalse(graph.hasCycles());

        // Add cycle
        graph.addEdge("C", "A");
        assertTrue(graph.hasCycles());
    }

    @Test
    void testGetAllNodes() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");

        Set<String> nodes = graph.getAllNodes();
        assertEquals(3, nodes.size());
        assertTrue(nodes.contains("A"));
        assertTrue(nodes.contains("B"));
        assertTrue(nodes.contains("C"));
    }

    @Test
    void testNodeWeight() {
        CausalGraph graph = new CausalGraph();
        graph.addNode("A", 0.8);
        graph.addNode("B", 0.5);

        assertEquals(0.8, graph.getNodeWeight("A"), 0.001);
        assertEquals(0.5, graph.getNodeWeight("B"), 0.001);
    }

    @Test
    void testEdgeCount() {
        CausalGraph graph = new CausalGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("A", "C");

        assertEquals(3, graph.getEdgeCount());
    }
}
