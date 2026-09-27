package io.matrix.brain.runtime;

import io.matrix.scaling.ShardedFederation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RECON-W7 — Production caller of ShardedFederation.
 *
 * <p>Routes incoming federation facts to the right shard based on key
 * (consistent-hash). Tracks per-shard stats. Deterministic with seeded
 * Random(42L); same input ⇒ same shard placement.</p>
 *
 * <p>Article VIII: emits engine markers naming the actual core engine call
 * (ShardedFederation.placeKey).</p>
 */
public final class FedShardManager {

    private final ShardedFederation federation;
    private final Map<Integer, Integer> shardHits = new HashMap<>();
    private int totalRouted = 0;

    public FedShardManager() {
        this(16, 42L);
    }

    public FedShardManager(int shardCount, long seed) {
        this.federation = new ShardedFederation(shardCount, seed);
    }

    public int shardCount() { return federation.getShardCount(); }

    /** Route a key to its shard. Returns the shard id. */
    public int route(String key) {
        int shard = federation.placeKey(key);
        shardHits.merge(shard, 1, Integer::sum);
        totalRouted++;
        return shard;
    }

    /** Look up keys across shards. */
    public Map<String, Integer> routeAll(List<String> keys) {
        Map<String, Integer> out = new HashMap<>();
        for (String k : keys) out.put(k, route(k));
        return out;
    }

    /** Snapshot for /v1/status. */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("shard_count", federation.getShardCount());
        m.put("key_count", federation.getKeyCount());
        m.put("total_routed", totalRouted);
        m.put("shard_hits", new java.util.HashMap<>(shardHits));
        m.put("engine", "ShardedFederation.placeKey(seed=42L)");
        return m;
    }
}
