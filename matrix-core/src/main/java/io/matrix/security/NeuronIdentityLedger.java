package io.matrix.security;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed registry of neuron identities and their public keys.
 *
 * <p>Maps {@code NeuronId UUID → Ed25519 public key}. Used to verify that
 * a snapshot or signal originated from a registered neuron.
 *
 * <p>When a {@link RedisClient} is available, identities are persisted in
 * Redis for cross-instance sharing. An in-memory {@link ConcurrentHashMap}
 * serves as a hot cache for fast lookups.
 *
 * <p>Ref: L6_Memory.md §7
 */
@ApplicationScoped
public class NeuronIdentityLedger {

    private static final Logger LOG = LoggerFactory.getLogger(NeuronIdentityLedger.class);
    private static final String REDIS_KEY_PREFIX = "neuron:identity:";
    private static final int REDIS_TTL_SECONDS = 86400; // 24 hours

    private final Map<UUID, PublicKey> ledger = new ConcurrentHashMap<>();
    private final StatefulRedisConnection<String, String> redisConnection;
    private final RedisCommands<String, String> redis;

    /**
     * Creates an in-memory-only ledger (for testing or when Redis is unavailable).
     */
    public NeuronIdentityLedger() {
        this.redisConnection = null;
        this.redis = null;
    }

    /**
     * Creates a Redis-backed ledger for distributed neuron identity persistence.
     *
     * @param redisClient the Lettuce Redis client
     */
    @Inject
    public NeuronIdentityLedger(RedisClient redisClient) {
        this.redisConnection = redisClient.connect();
        this.redis = redisConnection.sync();
        LOG.info("NeuronIdentityLedger initialized with Redis backing");
        loadFromRedis();
    }

    /**
     * Registers a neuron's public key in the ledger.
     *
     * <p>Persists to Redis when available, and always updates the in-memory cache.
     *
     * @param neuronId the neuron's UUID
     * @param pubKey   the neuron's Ed25519 public key
     */
    public void register(UUID neuronId, PublicKey pubKey) {
        ledger.put(neuronId, pubKey);
        if (redis != null) {
            try {
                String encoded = Base64.getEncoder().encodeToString(pubKey.getEncoded());
                redis.setex(REDIS_KEY_PREFIX + neuronId, REDIS_TTL_SECONDS, encoded);
                LOG.debug("Persisted neuron identity {} to Redis", neuronId);
            } catch (Exception e) {
                LOG.warn("Failed to persist neuron {} to Redis: {}", neuronId, e.getMessage());
            }
        }
    }

    /**
     * Verifies that the given data was signed by the neuron registered
     * under {@code neuronId}.
     *
     * @param neuronId  the neuron's UUID
     * @param data      the original data
     * @param signature the signature to verify
     * @return true if the neuron is registered and the signature is valid
     * @throws Exception if verification fails
     */
    public boolean verify(UUID neuronId, byte[] data, byte[] signature) throws Exception {
        PublicKey pubKey = getPublicKey(neuronId);
        if (pubKey == null) {
            return false;
        }
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(pubKey);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Returns the public key for a registered neuron.
     *
     * <p>Checks the in-memory cache first, then falls back to Redis.
     *
     * @param neuronId the neuron's UUID
     * @return the public key, or {@code null} if not registered
     */
    public PublicKey getPublicKey(UUID neuronId) {
        PublicKey key = ledger.get(neuronId);
        if (key != null) {
            return key;
        }
        // Fallback to Redis
        if (redis != null) {
            try {
                String encoded = redis.get(REDIS_KEY_PREFIX + neuronId);
                if (encoded != null) {
                    byte[] keyBytes = Base64.getDecoder().decode(encoded);
                    key = KeyFactory.getInstance("Ed25519")
                            .generatePublic(new X509EncodedKeySpec(keyBytes));
                    ledger.put(neuronId, key); // warm cache
                    return key;
                }
            } catch (Exception e) {
                LOG.warn("Failed to read neuron {} from Redis: {}", neuronId, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Checks whether a neuron is registered in the ledger.
     *
     * @param neuronId the neuron's UUID
     * @return true if registered
     */
    public boolean isRegistered(UUID neuronId) {
        if (ledger.containsKey(neuronId)) {
            return true;
        }
        return getPublicKey(neuronId) != null;
    }

    /**
     * Removes a neuron from the ledger (in-memory and Redis).
     *
     * @param neuronId the neuron's UUID
     */
    public void unregister(UUID neuronId) {
        ledger.remove(neuronId);
        if (redis != null) {
            try {
                redis.del(REDIS_KEY_PREFIX + neuronId);
            } catch (Exception e) {
                LOG.warn("Failed to delete neuron {} from Redis: {}", neuronId, e.getMessage());
            }
        }
    }

    /**
     * Returns the number of registered neurons (in-memory cache count).
     */
    public int size() {
        return ledger.size();
    }

    @PreDestroy
    void close() {
        if (redisConnection != null) {
            redisConnection.close();
        }
    }

    // ─── internal ───

    /**
     * Loads all neuron identities from Redis into the in-memory cache on startup.
     */
    private void loadFromRedis() {
        try {
            var keys = redis.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String uuidStr = key.substring(REDIS_KEY_PREFIX.length());
                    try {
                        UUID neuronId = UUID.fromString(uuidStr);
                        PublicKey pubKey = getPublicKey(neuronId); // triggers cache warm
                        if (pubKey != null) {
                            ledger.put(neuronId, pubKey);
                        }
                    } catch (IllegalArgumentException e) {
                        LOG.debug("Skipping non-UUID Redis key: {}", key);
                    }
                }
                LOG.info("Loaded {} neuron identities from Redis", ledger.size());
            }
        } catch (Exception e) {
            LOG.warn("Failed to load neuron identities from Redis: {}", e.getMessage());
        }
    }
}
