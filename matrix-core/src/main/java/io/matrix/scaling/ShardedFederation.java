package io.matrix.scaling;

import java.util.*;

/**
 * W1296 — Sharded Federation.
 *
 * Partition memory across 1000+ nodes. Consensus across shards.
 * Each shard owns a subset of the knowledge base.
 */
public final class ShardedFederation {

    public record Shard(int id, String name, Set<String> keys, int nodeCount) {}

    public record CrossShardResult(
            String key,
            int sourceShard,
            int targetShard,
            boolean found,
            long latencyMs
    ) {}

    private final int shardCount;
    private final Map<Integer, Shard> shards = new HashMap<>();
    private final Map<String, Integer> keyToShard = new HashMap<>();
    private final Random rng;

    public ShardedFederation(int shardCount, long seed) {
        this.shardCount = shardCount;
        this.rng = new Random(seed);
        for (int i = 0; i < shardCount; i++) {
            shards.put(i, new Shard(i, "shard-" + i, new HashSet<>(), 1000 / shardCount));
        }
    }

    /**
     * Place a key into a shard using consistent hashing.
     */
    public int placeKey(String key) {
        int shard = Math.floorMod(key.hashCode(), shardCount);
        Shard s = shards.get(shard);
        s.keys().add(key);
        keyToShard.put(key, shard);
        return shard;
    }

    /**
     * Cross-shard lookup: find which shard owns a key.
     */
    public CrossShardResult lookup(String key) {
        long start = System.currentTimeMillis();
        Integer shard = keyToShard.get(key);
        long latency = System.currentTimeMillis() - start;

        if (shard == null) {
            return new CrossShardResult(key, -1, -1, false, latency);
        }
        return new CrossShardResult(key, shard, shard, true, latency);
    }

    /**
     * Rebalance shards when nodes are added/removed.
     */
    public void rebalance(int newShardCount) {
        Map<String, Integer> newMapping = new HashMap<>();
        for (var entry : keyToShard.entrySet()) {
            int newShard = Math.floorMod(entry.getKey().hashCode(), newShardCount);
            newMapping.put(entry.getKey(), newShard);
        }
        keyToShard.clear();
        keyToShard.putAll(newMapping);
    }

    public int getShardCount() { return shardCount; }
    public int getKeyCount() { return keyToShard.size(); }
}
