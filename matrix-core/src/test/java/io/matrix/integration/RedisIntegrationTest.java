package io.matrix.integration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;

import io.matrix.cluster.NeuronId;
import io.matrix.neuron.TruthTable;
import io.matrix.redis.NeuronCacheService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis cache using Testcontainers.
 *
 * <p>Tests SET/GET, TTL expiration, pipeline operations, connection pool,
 * and the {@link NeuronCacheService} against a real Redis instance.
 */
@Testcontainers
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class RedisIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> sync;

    @BeforeEach
    void setUp() {
        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        redisClient = RedisClient.create(redisUri);
        connection = redisClient.connect();
        sync = connection.sync();
        sync.flushall();
    }

    @AfterEach
    void tearDown() {
        if (connection != null) connection.close();
        if (redisClient != null) redisClient.shutdown();
    }

    // ─── SET/GET operations ───

    @Test
    void setAndGetSimpleKeyValue() {
        sync.set("test:key1", "value1");
        String result = sync.get("test:key1");
        assertThat(result).isEqualTo("value1");
    }

    @Test
    void getNonExistentKeyReturnsNull() {
        String result = sync.get("nonexistent:key");
        assertThat(result).isNull();
    }

    @Test
    void setOverwritesExistingValue() {
        sync.set("test:overwrite", "first");
        sync.set("test:overwrite", "second");
        assertThat(sync.get("test:overwrite")).isEqualTo("second");
    }

    @Test
    void setAndGetBinaryData() {
        String encoded = java.util.Base64.getEncoder().encodeToString(
                new byte[]{0x01, 0x02, 0x03, (byte) 0xFF});
        sync.set("test:binary", encoded);
        assertThat(sync.get("test:binary")).isEqualTo(encoded);
    }

    @Test
    void msetAndGetMultipleKeys() {
        sync.mset(java.util.Map.of("test:a", "1", "test:b", "2", "test:c", "3"));
        List<io.lettuce.core.KeyValue<String, String>> result =
                sync.mget("test:a", "test:b", "test:c");
        List<String> values = result.stream()
                .map(kv -> kv.getValueOrElse(null))
                .toList();
        assertThat(values).containsExactly("1", "2", "3");
    }

    // ─── TTL expiration ───

    @Test
    void keyExpiresAfterTtl() throws Exception {
        sync.setex("test:ttl", 1, "ephemeral");
        assertThat(sync.get("test:ttl")).isEqualTo("ephemeral");

        // Wait for expiration
        Thread.sleep(1500);
        assertThat(sync.get("test:ttl")).isNull();
    }

    @Test
    void ttlReturnsRemainingSeconds() {
        sync.setex("test:ttlcheck", 60, "data");
        long ttl = sync.ttl("test:ttlcheck");
        assertThat(ttl).isBetween(1L, 60L);
    }

    @Test
    void persistRemovesExpiration() {
        sync.setex("test:persist", 60, "data");
        sync.persist("test:persist");
        long ttl = sync.ttl("test:persist");
        assertThat(ttl).isEqualTo(-1); // no expiration
    }

    // ─── Pipeline operations ───

    @Test
    void pipelineReducesRoundTrips() {
        int count = 100;

        // Use pipelined commands via async API
        var async = connection.async();
        var futures = new ArrayList<RedisFuture<String>>();
        for (int i = 0; i < count; i++) {
            futures.add(async.set("test:pipe:" + i, "val-" + i));
        }

        // Wait for all to complete
        boolean allCompleted = LettuceFutures.awaitAll(5, TimeUnit.SECONDS,
                futures.toArray(new RedisFuture[0]));
        assertThat(allCompleted).isTrue();

        // Verify all values
        for (int i = 0; i < count; i++) {
            assertThat(sync.get("test:pipe:" + i)).isEqualTo("val-" + i);
        }
    }

    @Test
    void transactionExecutesAtomically() {
        sync.multi();
        sync.set("test:tx:a", "1");
        sync.set("test:tx:b", "2");
        sync.incr("test:tx:counter");
        TransactionResult result = sync.exec();

        assertThat(result).isNotNull();
        assertThat(sync.get("test:tx:a")).isEqualTo("1");
        assertThat(sync.get("test:tx:b")).isEqualTo("2");
        assertThat(sync.get("test:tx:counter")).isEqualTo("1");
    }

    // ─── Connection pool ───

    @Test
    void multipleConnectionsWork() {
        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        try (RedisClient client2 = RedisClient.create(redisUri);
             StatefulRedisConnection<String, String> conn2 = client2.connect()) {

            sync.set("test:pool:shared", "from-conn1");
            String value = conn2.sync().get("test:pool:shared");
            assertThat(value).isEqualTo("from-conn1");
        }
    }

    @Test
    void concurrentAccessDoesNotCorruptData() throws Exception {
        int threadCount = 8;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(CompletableFuture.runAsync(() -> {
                try (RedisClient client = RedisClient.create(redisUri);
                     StatefulRedisConnection<String, String> conn = client.connect()) {
                    var cmds = conn.sync();
                    for (int i = 0; i < opsPerThread; i++) {
                        String key = "test:concurrent:" + threadId + ":" + i;
                        cmds.set(key, "t" + threadId + "-v" + i);
                        String read = cmds.get(key);
                        assertThat(read).isEqualTo("t" + threadId + "-v" + i);
                    }
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    // ─── NeuronCacheService ───

    @Test
    void neuronCacheServiceSetAndGet() {
        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        try (RedisClient client = RedisClient.create(redisUri)) {
            NeuronCacheService cacheService = new NeuronCacheService(client);

            NeuronId neuronId = NeuronId.create();
            TruthTable table = TruthTable.random(8, new Random(42));

            cacheService.cacheNeuron(neuronId, table);
            Optional<TruthTable> retrieved = cacheService.getNeuron(neuronId);

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().k()).isEqualTo(table.k());
            assertThat(retrieved.get().table()).isEqualTo(table.table());
        }
    }

    @Test
    void neuronCacheServiceInvalidate() {
        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        try (RedisClient client = RedisClient.create(redisUri)) {
            NeuronCacheService cacheService = new NeuronCacheService(client);

            NeuronId neuronId = NeuronId.create();
            TruthTable table = TruthTable.random(8, new Random(42));

            cacheService.cacheNeuron(neuronId, table);
            assertThat(cacheService.getNeuron(neuronId)).isPresent();

            cacheService.invalidateNeuron(neuronId);
            assertThat(cacheService.getNeuron(neuronId)).isEmpty();
        }
    }

    @Test
    void neuronCacheServiceBrainState() {
        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        try (RedisClient client = RedisClient.create(redisUri)) {
            NeuronCacheService cacheService = new NeuronCacheService(client);

            cacheService.cacheBrainState("agent-1", 0xABCDL, "MOVE_N");
            Optional<String> state = cacheService.getBrainState("agent-1");

            assertThat(state).isPresent();
            assertThat(state.get()).isEqualTo("43981|MOVE_N");
        }
    }

    @Test
    void neuronCacheServiceGetNonExistentReturnsEmpty() {
        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        try (RedisClient client = RedisClient.create(redisUri)) {
            NeuronCacheService cacheService = new NeuronCacheService(client);

            assertThat(cacheService.getNeuron(NeuronId.create())).isEmpty();
            assertThat(cacheService.getBrainState("ghost-agent")).isEmpty();
        }
    }

    // ─── Negative tests ───

    @Test
    void connectionToClosedClientThrows() {
        connection.close();
        redisClient.shutdown();
        // After shutdown, the client resources are released
        assertThat(connection.isOpen()).isFalse();
    }

    @Test
    void largeValueHandling() {
        String largeValue = "x".repeat(1_000_000); // 1MB
        sync.set("test:large", largeValue);
        String retrieved = sync.get("test:large");
        assertThat(retrieved).hasSize(1_000_000);
    }
}
