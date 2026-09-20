package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W588 — Tests for Federation Map.
 */
class FederationMapTest {

    @Test
    void testAddNode() {
        FederationMap map = new FederationMap(42);
        map.addNode(1, "Node1", NodeRole.ADULT);

        assertEquals(1, map.getNodeCount());
        assertNotNull(map.getNode(1));
        assertEquals("Node1", map.getNode(1).label());
    }

    @Test
    void testAddEdge() {
        FederationMap map = new FederationMap(42);
        map.addNode(1, "A", NodeRole.ADULT);
        map.addNode(2, "B", NodeRole.LEARNER);
        map.addEdge(1, 2, "consensus", 0.8);

        assertEquals(1, map.getEdgeCount());
    }

    @Test
    void testToJson() {
        FederationMap map = new FederationMap(42);
        map.addNode(1, "A", NodeRole.ADULT);
        map.addNode(2, "B", NodeRole.GUARDIAN);
        map.addEdge(1, 2, "consensus", 0.9);

        String json = map.toJson();

        assertTrue(json.contains("\"nodes\""));
        assertTrue(json.contains("\"edges\""));
        assertTrue(json.contains("ADULT"));
        assertTrue(json.contains("GUARDIAN"));
    }

    @Test
    void testToHtml() {
        FederationMap map = new FederationMap(42);
        map.addNode(1, "A", NodeRole.ADULT);

        String html = map.toHtml();

        assertTrue(html.contains("MATRIX Federation Map"));
        assertTrue(html.contains("d3.v7.min.js"));
        assertTrue(html.contains("<svg"));
    }

    @Test
    void testRoleColors() {
        FederationMap map = new FederationMap(42);
        map.addNode(1, "Infant", NodeRole.INFANT);
        map.addNode(2, "Guardian", NodeRole.GUARDIAN);

        String json = map.toJson();

        assertTrue(json.contains("#666666")); // INFANT color
        assertTrue(json.contains("#ff4444")); // GUARDIAN color
    }
}
