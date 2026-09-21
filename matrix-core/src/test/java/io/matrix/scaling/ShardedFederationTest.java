package io.matrix.scaling;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShardedFederationTest {

    @Test
    void testCreateFederation() {
        ShardedFederation fed = new ShardedFederation(10, 42L);
        assertNotNull(fed);
        assertEquals(10, fed.getShardCount());
    }

    @Test
    void testPlaceKey() {
        ShardedFederation fed = new ShardedFederation(10, 42L);
        int shard = fed.placeKey("user-123");
        assertTrue(shard >= 0 && shard < 10);
        assertEquals(1, fed.getKeyCount());
    }

    @Test
    void testConsistentHashing() {
        ShardedFederation fed = new ShardedFederation(10, 42L);
        int s1 = fed.placeKey("same-key");
        int s2 = fed.placeKey("same-key");
        assertEquals(s1, s2, "Same key should map to same shard");
    }

    @Test
    void testLookup() {
        ShardedFederation fed = new ShardedFederation(10, 42L);
        int shard = fed.placeKey("findme");
        ShardedFederation.CrossShardResult result = fed.lookup("findme");
        assertTrue(result.found());
        assertEquals(shard, result.targetShard());
    }

    @Test
    void testLookupMissing() {
        ShardedFederation fed = new ShardedFederation(10, 42L);
        ShardedFederation.CrossShardResult result = fed.lookup("nonexistent");
        assertFalse(result.found());
    }

    @Test
    void testRebalance() {
        ShardedFederation fed = new ShardedFederation(10, 42L);
        for (int i = 0; i < 100; i++) fed.placeKey("key-" + i);
        fed.rebalance(20);
        assertEquals(100, fed.getKeyCount(), "Keys should be preserved during rebalance");
    }

    @Test
    void testCrossShardLatency() {
        ShardedFederation fed = new ShardedFederation(10, 42L);
        fed.placeKey("test");
        ShardedFederation.CrossShardResult result = fed.lookup("test");
        assertTrue(result.latencyMs() >= 0);
    }
}
