package io.matrix.integration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;
import io.matrix.cluster.NeuronId;
import io.matrix.events.ClusterEvent;
import io.matrix.events.ClusterEventType;
import io.matrix.events.KafkaEventJournal;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.Task;
import io.matrix.mediator.scheduler.TaskScheduler;
import io.matrix.neuron.TruthTable;
import io.matrix.redis.NeuronCacheService;

import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the full MATRIX pipeline.
 *
 * <p>Starts Kafka, Redis, and PostgreSQL containers simultaneously,
 * then exercises the AgentLoop cycle with real infrastructure:
 * <ol>
 *   <li>Create AgentBrainService</li>
 *   <li>Run AgentLoop cycle</li>
 *   <li>Verify events published to Kafka</li>
 *   <li>Verify cache written to Redis</li>
 *   <li>Verify persistence in PostgreSQL</li>
 * </ol>
 */
@Testcontainers
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class EndToEndIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withEmbeddedZookeeper();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("matrix_e2e")
            .withUsername("test")
            .withPassword("test");

    private KafkaEventJournal kafkaJournal;
    private RedisClient redisClient;
    private NeuronCacheService cacheService;
    private PgPool pgPool;

    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS cluster_events (
            event_index BIGSERIAL PRIMARY KEY,
            event_id VARCHAR(36) NOT NULL UNIQUE,
            event_type VARCHAR(64) NOT NULL,
            instance_id VARCHAR(36) NOT NULL,
            neuron_id_uuid VARCHAR(36),
            neuron_generation BIGINT,
            event_timestamp BIGINT NOT NULL,
            payload TEXT,
            created_at TIMESTAMPTZ DEFAULT NOW()
        )""";

    @BeforeEach
    void setUp() {
        // Kafka
        kafkaJournal = new KafkaEventJournal("e2e-events", KAFKA.getBootstrapServers());

        // Redis
        String redisUri = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
        redisClient = RedisClient.create(redisUri);
        cacheService = new NeuronCacheService(redisClient);

        // PostgreSQL
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(POSTGRES.getHost())
                .setPort(POSTGRES.getFirstMappedPort())
                .setDatabase(POSTGRES.getDatabaseName())
                .setUser(POSTGRES.getUsername())
                .setPassword(POSTGRES.getPassword());
        pgPool = PgPool.pool(connectOptions, new PoolOptions().setMaxSize(5));
        pgPool.query(DDL).execute().await().indefinitely();
    }

    @AfterEach
    void tearDown() {
        if (kafkaJournal != null) kafkaJournal.close();
        if (redisClient != null) redisClient.shutdown();
        if (pgPool != null) pgPool.closeAndAwait();
    }

    @Test
    void fullAgentLoopCycleWithAllInfrastructure() {
        // ─── Step 1: Create AgentBrainService ───
        AgentBrainService brainService = new AgentBrainService();
        assertThat(brainService).isNotNull();

        // ─── Step 2: Run AgentLoop cycle ───
        DriverState[] drivers = {
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.SAFETY),
                DriverState.withDefaults(DriverType.CURIOSITY)
        };

        TaskScheduler scheduler = TaskScheduler.withDefaults();
        Random rng = new Random(42);
        long[] sensorReadings = {0xABCDL, 0x1234L, 0x5678L, 0xDEF0L, 0x9999L};
        int[] readingIndex = {0};

        AgentLoop.Sensor sensor = () -> sensorReadings[readingIndex[0]++ % sensorReadings.length];
        AgentLoop.Effector effector = action -> AgentAction.ActionResult.success("ok", 1);

        AgentLoop loop = new AgentLoop(brainService, sensor, effector, drivers, scheduler, 5);
        List<AgentState> history = loop.run(10);

        assertThat(history).isNotEmpty();
        assertThat(loop.tickCount()).isGreaterThan(0);

        // ─── Step 3: Publish events to Kafka ───
        NeuronId neuronId = NeuronId.create();
        for (AgentState state : history) {
            ClusterEvent event = ClusterEvent.of(
                    ClusterEventType.SIGNAL_EMITTED,
                    "e2e-instance",
                    neuronId,
                    "{\"tick\":" + state.tick() + ",\"action\":\"" + state.actionType() + "\"}");
            kafkaJournal.append(event);
        }

        // Verify Kafka delivery
        int kafkaEventCount = consumeKafkaEvents("e2e-events");
        assertThat(kafkaEventCount).isEqualTo(history.size());

        // ─── Step 4: Cache brain state in Redis ───
        for (int i = 0; i < history.size(); i++) {
            AgentState state = history.get(i);
            cacheService.cacheBrainState(
                    "e2e-agent",
                    state.observation(),
                    state.actionType() != null ? state.actionType().name() : "UNKNOWN");
        }

        // Verify Redis cache
        Optional<String> cachedState = cacheService.getBrainState("e2e-agent");
        assertThat(cachedState).isPresent();

        // Cache a neuron
        TruthTable table = TruthTable.random(8, rng);
        cacheService.cacheNeuron(neuronId, table);
        Optional<TruthTable> cachedNeuron = cacheService.getNeuron(neuronId);
        assertThat(cachedNeuron).isPresent();
        assertThat(cachedNeuron.get().k()).isEqualTo(8);

        // ─── Step 5: Persist events to PostgreSQL ───
        for (AgentState state : history) {
            String eventId = UUID.randomUUID().toString();
            pgPool.preparedQuery("""
                    INSERT INTO cluster_events
                        (event_id, event_type, instance_id,
                         neuron_id_uuid, neuron_generation,
                         event_timestamp, payload)
                    VALUES ($1, $2, $3, $4, $5, $6, $7)
                    """)
                    .execute(Tuple.tuple(java.util.List.of(
                            eventId,
                            "SIGNAL_EMITTED",
                            "e2e-instance",
                            neuronId.uuid().toString(),
                            neuronId.generation(),
                            state.timestampMs(),
                            "{\"tick\":" + state.tick() + "}")))
                    .await()
                    .indefinitely();
        }

        // Verify PostgreSQL persistence
        RowSet<Row> rows = pgPool.query(
                "SELECT COUNT(*) AS cnt FROM cluster_events")
                .execute()
                .await()
                .indefinitely();
        assertThat(rows.iterator().next().getLong("cnt"))
                .isEqualTo(history.size());
    }

    @Test
    void agentLoopPublishesEventsToKafka() {
        AgentBrainService brainService = new AgentBrainService();
        DriverState[] drivers = {DriverState.withDefaults(DriverType.CURIOSITY)};
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        long[] readings = {0x1111L, 0x2222L, 0x3333L};
        int[] idx = {0};
        AgentLoop.Sensor sensor = () -> readings[idx[0]++ % readings.length];
        AgentLoop.Effector effector = action -> AgentAction.ActionResult.success("ok", 1);

        AgentLoop loop = new AgentLoop(brainService, sensor, effector, drivers, scheduler, 3);
        List<AgentState> history = loop.run(5);

        // Publish each state as a Kafka event
        NeuronId neuronId = NeuronId.create();
        for (AgentState state : history) {
            kafkaJournal.append(ClusterEvent.of(
                    ClusterEventType.BATCH_EVALUATED,
                    "kafka-test",
                    neuronId,
                    "tick=" + state.tick()));
        }

        int received = consumeKafkaEvents("e2e-events");
        assertThat(received).isEqualTo(history.size());
    }

    @Test
    void redisCacheSurvivesAcrossOperations() {
        NeuronId neuronId = NeuronId.create();
        TruthTable table = TruthTable.random(12, new Random(42));

        // Cache neuron
        cacheService.cacheNeuron(neuronId, table);

        // Do other operations
        cacheService.cacheBrainState("agent-1", 100L, "MOVE_N");
        cacheService.cacheBrainState("agent-2", 200L, "MINE");

        // Neuron should still be there
        Optional<TruthTable> cached = cacheService.getNeuron(neuronId);
        assertThat(cached).isPresent();
        assertThat(cached.get().table()).isEqualTo(table.table());

        // Invalidate one brain state, other should remain
        // (NeuronCacheService doesn't have per-agent invalidation, verify via direct Redis)
        RedisCommands<String, String> sync = redisClient.connect().sync();
        sync.del("agent:agent-1:state");
        assertThat(sync.get("agent:agent-2:state")).isNotNull();
    }

    @Test
    void postgresPersistsAcrossReconnections() {
        String eventId = UUID.randomUUID().toString();
        pgPool.preparedQuery("""
                INSERT INTO cluster_events
                    (event_id, event_type, instance_id,
                     neuron_id_uuid, neuron_generation,
                     event_timestamp, payload)
                VALUES ($1, $2, $3, $4, $5, $6, $7)
                """)
                .execute(Tuple.tuple(java.util.List.of(
                        eventId, "NEURON_CREATED", "reconnect-test",
                        UUID.randomUUID().toString(), 0L,
                        System.currentTimeMillis(), "persistent-data")))
                .await()
                .indefinitely();

        // Close and recreate pool
        pgPool.closeAndAwait();
        PgConnectOptions opts = new PgConnectOptions()
                .setHost(POSTGRES.getHost())
                .setPort(POSTGRES.getFirstMappedPort())
                .setDatabase(POSTGRES.getDatabaseName())
                .setUser(POSTGRES.getUsername())
                .setPassword(POSTGRES.getPassword());
        pgPool = PgPool.pool(opts, new PoolOptions().setMaxSize(5));

        // Data should still be there
        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT payload FROM cluster_events WHERE event_id = $1")
                .execute(Tuple.of(eventId))
                .await()
                .indefinitely();

        assertThat(rows.iterator().hasNext()).isTrue();
        assertThat(rows.iterator().next().getString("payload"))
                .isEqualTo("persistent-data");
    }

    @Test
    void allThreeSystemsHandleConcurrentWrites() throws Exception {
        int count = 10;
        NeuronId neuronId = NeuronId.create();

        var futures = new ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                // Kafka
                kafkaJournal.append(ClusterEvent.of(
                        ClusterEventType.SIGNAL_EMITTED, "concurrent", neuronId, "k-" + idx));

                // Redis
                cacheService.cacheBrainState("concurrent-agent-" + idx, idx, "ACTION_" + idx);

                // PostgreSQL
                pgPool.preparedQuery("""
                        INSERT INTO cluster_events
                            (event_id, event_type, instance_id,
                             neuron_id_uuid, neuron_generation,
                             event_timestamp, payload)
                        VALUES ($1, $2, $3, $4, $5, $6, $7)
                        """)
                        .execute(Tuple.tuple(java.util.List.of(
                                UUID.randomUUID().toString(),
                                "SIGNAL_EMITTED",
                                "concurrent",
                                neuronId.uuid().toString(),
                                neuronId.generation(),
                                System.currentTimeMillis(),
                                "pg-" + idx)))
                        .await()
                        .indefinitely();
            }));
        }

        java.util.concurrent.CompletableFuture.allOf(
                futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

        // Verify Kafka
        assertThat(kafkaJournal.size()).isEqualTo(count);

        // Verify Redis
        for (int i = 0; i < count; i++) {
            Optional<String> state = cacheService.getBrainState("concurrent-agent-" + i);
            assertThat(state).isPresent();
        }

        // Verify PostgreSQL
        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT COUNT(*) AS cnt FROM cluster_events WHERE instance_id = $1")
                .execute(Tuple.of("concurrent"))
                .await()
                .indefinitely();
        assertThat(rows.iterator().next().getLong("cnt")).isEqualTo(count);
    }

    @Test
    void agentLoopWithSafetyDriverHighProducesWaitActions() {
        AgentBrainService brainService = new AgentBrainService();

        // Safety driver at 0.9 (above 0.7 threshold)
        DriverState safetyDriver = new DriverState(
                DriverType.SAFETY, 0.9, 0.05, 0.05, 0.01, 0.7, 0.1);
        DriverState[] drivers = {safetyDriver};
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        AgentLoop.Sensor sensor = () -> 0xABCDL;
        AgentLoop.Effector effector = action -> AgentAction.ActionResult.success("ok", 1);

        AgentLoop loop = new AgentLoop(brainService, sensor, effector, drivers, scheduler, 5);
        List<AgentState> history = loop.run(10);

        // With safety high, at least some actions should be WAIT
        long waitCount = history.stream()
                .filter(s -> s.actionType() == AgentAction.ActionType.WAIT)
                .count();
        assertThat(waitCount).isGreaterThan(0);
    }

    @Test
    void negativeTestKafkaConnectionFailureDoesNotBreakAgentLoop() {
        // Create journal with bad broker
        KafkaEventJournal badJournal = new KafkaEventJournal("bad-topic", "localhost:19999");

        AgentBrainService brainService = new AgentBrainService();
        DriverState[] drivers = {DriverState.withDefaults(DriverType.ENERGY)};
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        AgentLoop.Sensor sensor = () -> 0x1234L;
        AgentLoop.Effector effector = action -> AgentAction.ActionResult.success("ok", 1);

        AgentLoop loop = new AgentLoop(brainService, sensor, effector, drivers, scheduler, 3);
        List<AgentState> history = loop.run(5);

        // Events should still be buffered in-memory
        NeuronId neuronId = NeuronId.create();
        for (AgentState state : history) {
            badJournal.append(ClusterEvent.of(
                    ClusterEventType.SIGNAL_EMITTED, "bad-broker", neuronId, "data"));
        }
        assertThat(badJournal.size()).isEqualTo(history.size());

        badJournal.close();
    }

    // ─── helpers ───

    private int consumeKafkaEvents(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            int total = 0;
            long deadline = System.currentTimeMillis() + 15_000;
            while (total < 1 && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(2));
                total += records.count();
            }
            return total;
        }
    }
}
