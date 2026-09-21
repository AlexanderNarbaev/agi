package io.matrix.api.federation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FederationRegistryTest {

    @Test
    void testJoinCreatesNode() {
        FederationRegistry registry = new FederationRegistry();
        FederationRegistry.Node node = registry.join("us-east", 500);
        assertNotNull(node.nodeId());
        assertTrue(node.nodeId().startsWith("node_"));
        assertEquals("us-east", node.region());
        assertEquals(500, node.shardCapacity());
        assertEquals(1, registry.size());
    }

    @Test
    void testMultipleJoins() {
        FederationRegistry registry = new FederationRegistry();
        registry.join("us-east", 100);
        registry.join("eu-west", 200);
        registry.join("ap-south", 300);
        assertEquals(3, registry.size());
        assertEquals(3, registry.list().size());
    }

    @Test
    void testGetNode() {
        FederationRegistry registry = new FederationRegistry();
        FederationRegistry.Node n = registry.join("us-east", 100);
        FederationRegistry.Node found = registry.get(n.nodeId());
        assertEquals(n.nodeId(), found.nodeId());
    }

    @Test
    void testLeaveNode() {
        FederationRegistry registry = new FederationRegistry();
        FederationRegistry.Node n = registry.join("us-east", 100);
        assertTrue(registry.leave(n.nodeId()));
        assertNull(registry.get(n.nodeId()));
        assertEquals(0, registry.size());
    }

    @Test
    void testLeaveUnknownNode() {
        FederationRegistry registry = new FederationRegistry();
        assertFalse(registry.leave("node_nonexistent"));
    }
}
