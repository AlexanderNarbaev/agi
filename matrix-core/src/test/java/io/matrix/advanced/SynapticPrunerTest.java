package io.matrix.advanced;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SynapticPrunerTest {

    @Test
    void testCreatePruner() {
        SynapticPruner pruner = new SynapticPruner();
        assertNotNull(pruner);
        assertEquals(0, pruner.getConnectionCount());
    }

    @Test
    void testAddConnection() {
        SynapticPruner pruner = new SynapticPruner();
        pruner.addConnection("conn-1", 0.5);
        assertEquals(1, pruner.getConnectionCount());
    }

    @Test
    void testPruneLowUtility() {
        SynapticPruner pruner = new SynapticPruner(0.3, 1000);
        pruner.addConnection("high", 0.8);
        pruner.addConnection("low", 0.05);
        SynapticPruner.PruneResult result = pruner.prune();
        assertEquals(2, result.totalBefore());
        assertEquals(1, result.pruned());
        assertEquals(1, result.retained());
    }

    @Test
    void testTouchUpdatesRecency() {
        SynapticPruner pruner = new SynapticPruner(0.5, 100000);
        pruner.addConnection("conn-1", 0.6);
        pruner.touch("conn-1");
        // After touch, recency is reset, should be retained
        SynapticPruner.PruneResult result = pruner.prune();
        assertEquals(1, result.retained());
    }

    @Test
    void testNoConnectionsNoCrash() {
        SynapticPruner pruner = new SynapticPruner();
        SynapticPruner.PruneResult result = pruner.prune();
        assertEquals(0, result.totalBefore());
        assertEquals(0, result.pruned());
        assertEquals(0, result.retained());
    }
}
