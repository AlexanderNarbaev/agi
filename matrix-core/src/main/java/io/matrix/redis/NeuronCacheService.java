package io.matrix.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.matrix.cluster.NeuronId;
import io.matrix.neuron.TruthTable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Base64;
import java.util.Optional;

/**
 * Caches active neuron states and agent brain snapshots in Redis.
 *
 * <p>Neuron truth tables are serialised via Avro and stored as Base64-encoded
 * strings with TTL of 1 hour. Agent brain states are stored as simple
 * {@code sensorBits|action} strings with TTL of 5 minutes.
 */
@Singleton
public class NeuronCacheService {

    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> sync;

    @Inject
    public NeuronCacheService(RedisClient redisClient) {
        this.connection = redisClient.connect();
        this.sync = connection.sync();
    }

    /**
     * Stores a neuron truth table in Redis with 1-hour TTL.
     *
     * @param id    neuron identifier
     * @param table truth table to cache
     */
    public void cacheNeuron(NeuronId id, TruthTable table) {
        try {
            String key = "neuron:" + id;
            byte[] avroBytes = table.toAvroBytes();
            String encoded = Base64.getEncoder().encodeToString(avroBytes);
            sync.setex(key, 3600, encoded);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cache neuron " + id + " in Redis", e);
        }
    }

    /**
     * Retrieves a cached neuron truth table from Redis.
     *
     * @param id neuron identifier
     * @return the cached truth table, or empty if not found
     */
    public Optional<TruthTable> getNeuron(NeuronId id) {
        try {
            String key = "neuron:" + id;
            String encoded = sync.get(key);
            if (encoded == null) {
                return Optional.empty();
            }
            byte[] avroBytes = Base64.getDecoder().decode(encoded);
            return Optional.of(TruthTable.fromAvroBytes(avroBytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve neuron " + id + " from Redis", e);
        }
    }

    /**
     * Removes a cached neuron from Redis.
     *
     * @param id neuron identifier
     */
    public void invalidateNeuron(NeuronId id) {
        try {
            String key = "neuron:" + id;
            sync.del(key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invalidate neuron " + id + " in Redis", e);
        }
    }

    /**
     * Caches the last brain state for an agent with 5-minute TTL.
     *
     * @param agentId    agent identifier
     * @param sensorBits encoded sensor input bits
     * @param action     the action taken
     */
    public void cacheBrainState(String agentId, long sensorBits, String action) {
        try {
            String key = "agent:" + agentId + ":state";
            String value = sensorBits + "|" + action;
            sync.setex(key, 300, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cache brain state for agent " + agentId, e);
        }
    }

    /**
     * Retrieves the last cached brain state for an agent.
     *
     * @param agentId agent identifier
     * @return the cached state string ({@code sensorBits|action}), or empty if not found
     */
    public Optional<String> getBrainState(String agentId) {
        try {
            String key = "agent:" + agentId + ":state";
            return Optional.ofNullable(sync.get(key));
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve brain state for agent " + agentId, e);
        }
    }

    @PreDestroy
    void close() {
        connection.close();
    }
}
