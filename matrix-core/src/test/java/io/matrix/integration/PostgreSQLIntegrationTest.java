package io.matrix.integration;

import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PostgreSQL (R2DBC via Vert.x PgPool) using Testcontainers.
 *
 * <p>Tests schema creation, CRUD operations, and connection recovery
 * against a real PostgreSQL instance.
 */
@Testcontainers
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class PostgreSQLIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("matrix_test")
            .withUsername("test")
            .withPassword("test");

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

    private static final String INDEXES = """
        CREATE INDEX IF NOT EXISTS idx_ce_instance ON cluster_events(instance_id);
        CREATE INDEX IF NOT EXISTS idx_ce_type ON cluster_events(event_type)""";

    @BeforeEach
    void setUp() {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(POSTGRES.getHost())
                .setPort(POSTGRES.getFirstMappedPort())
                .setDatabase(POSTGRES.getDatabaseName())
                .setUser(POSTGRES.getUsername())
                .setPassword(POSTGRES.getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        pgPool = PgPool.pool(connectOptions, poolOptions);
    }

    @AfterEach
    void tearDown() {
        if (pgPool != null) {
            pgPool.closeAndAwait();
        }
    }

    // ─── Schema creation ───

    @Test
    void schemaCreationSucceeds() {
        pgPool.query(DDL + "; " + INDEXES)
                .execute()
                .await()
                .indefinitely();

        // Verify table exists by querying pg_tables
        RowSet<Row> rows = pgPool.query(
                "SELECT tablename FROM pg_tables WHERE tablename = 'cluster_events'")
                .execute()
                .await()
                .indefinitely();

        assertThat(rows.iterator().hasNext()).isTrue();
        assertThat(rows.iterator().next().getString("tablename"))
                .isEqualTo("cluster_events");
    }

    @Test
    void schemaCreationIdempotent() {
        // Creating schema twice should not throw
        pgPool.query(DDL + "; " + INDEXES)
                .execute()
                .await()
                .indefinitely();
        pgPool.query(DDL + "; " + INDEXES)
                .execute()
                .await()
                .indefinitely();
    }

    @Test
    void indexesCreatedCorrectly() {
        pgPool.query(DDL + "; " + INDEXES)
                .execute()
                .await()
                .indefinitely();

        RowSet<Row> rows = pgPool.query(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'cluster_events'")
                .execute()
                .await()
                .indefinitely();

        List<String> indexNames = new ArrayList<>();
        for (Row row : rows) {
            indexNames.add(row.getString("indexname"));
        }

        assertThat(indexNames).contains(
                "idx_ce_instance", "idx_ce_type");
    }

    // ─── CRUD operations ───

    @Test
    void insertAndSelectEvent() {
        initSchema();

        String eventId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        pgPool.preparedQuery("""
                INSERT INTO cluster_events
                    (event_id, event_type, instance_id,
                     neuron_id_uuid, neuron_generation,
                     event_timestamp, payload)
                VALUES ($1, $2, $3, $4, $5, $6, $7)
                RETURNING event_index
                """)
                .execute(Tuple.tuple(java.util.List.of(
                        eventId, "NEURON_CREATED", "inst-1",
                        UUID.randomUUID().toString(), 0L, timestamp, "{\"key\":\"value\"}")))
                .await()
                .indefinitely();

        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT event_id, event_type, payload FROM cluster_events WHERE event_id = $1")
                .execute(Tuple.of(eventId))
                .await()
                .indefinitely();

        assertThat(rows.iterator().hasNext()).isTrue();
        Row row = rows.iterator().next();
        assertThat(row.getString("event_id")).isEqualTo(eventId);
        assertThat(row.getString("event_type")).isEqualTo("NEURON_CREATED");
        assertThat(row.getString("payload")).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void insertMultipleAndCount() {
        initSchema();

        for (int i = 0; i < 10; i++) {
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
                            "inst-1",
                            UUID.randomUUID().toString(),
                            0L,
                            System.currentTimeMillis(),
                            "payload-" + i)))
                    .await()
                    .indefinitely();
        }

        RowSet<Row> rows = pgPool.query("SELECT COUNT(*) AS cnt FROM cluster_events")
                .execute()
                .await()
                .indefinitely();

        assertThat(rows.iterator().next().getLong("cnt")).isEqualTo(10L);
    }

    @Test
    void updateEvent() {
        initSchema();

        String eventId = UUID.randomUUID().toString();
        insertTestEvent(eventId, "NEURON_CREATED", "payload-original");

        pgPool.preparedQuery(
                "UPDATE cluster_events SET payload = $1 WHERE event_id = $2")
                .execute(Tuple.of("payload-updated", eventId))
                .await()
                .indefinitely();

        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT payload FROM cluster_events WHERE event_id = $1")
                .execute(Tuple.of(eventId))
                .await()
                .indefinitely();

        assertThat(rows.iterator().next().getString("payload"))
                .isEqualTo("payload-updated");
    }

    @Test
    void deleteEvent() {
        initSchema();

        String eventId = UUID.randomUUID().toString();
        insertTestEvent(eventId, "NEURON_REMOVED", "to-delete");

        pgPool.preparedQuery("DELETE FROM cluster_events WHERE event_id = $1")
                .execute(Tuple.of(eventId))
                .await()
                .indefinitely();

        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT COUNT(*) AS cnt FROM cluster_events WHERE event_id = $1")
                .execute(Tuple.of(eventId))
                .await()
                .indefinitely();

        assertThat(rows.iterator().next().getLong("cnt")).isZero();
    }

    @Test
    void orderByTimestamp() {
        initSchema();

        long baseTime = System.currentTimeMillis();
        insertTestEventWithTimestamp(UUID.randomUUID().toString(), "TYPE_A", baseTime - 2000);
        insertTestEventWithTimestamp(UUID.randomUUID().toString(), "TYPE_B", baseTime - 1000);
        insertTestEventWithTimestamp(UUID.randomUUID().toString(), "TYPE_C", baseTime);

        RowSet<Row> rows = pgPool.query(
                "SELECT event_type FROM cluster_events ORDER BY event_timestamp ASC")
                .execute()
                .await()
                .indefinitely();

        List<String> types = new ArrayList<>();
        for (Row row : rows) {
            types.add(row.getString("event_type"));
        }
        assertThat(types).containsExactly("TYPE_A", "TYPE_B", "TYPE_C");
    }

    @Test
    void filterByInstanceId() {
        initSchema();

        insertTestEvent(UUID.randomUUID().toString(), "NEURON_CREATED", "p1", "inst-A");
        insertTestEvent(UUID.randomUUID().toString(), "NEURON_CREATED", "p2", "inst-A");
        insertTestEvent(UUID.randomUUID().toString(), "NEURON_CREATED", "p3", "inst-B");

        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT COUNT(*) AS cnt FROM cluster_events WHERE instance_id = $1")
                .execute(Tuple.of("inst-A"))
                .await()
                .indefinitely();

        assertThat(rows.iterator().next().getLong("cnt")).isEqualTo(2L);
    }

    // ─── Connection recovery ───

    @Test
    void connectionRecoveryAfterRestart() {
        initSchema();

        String eventId = UUID.randomUUID().toString();
        insertTestEvent(eventId, "NEURON_CREATED", "before-restart");

        // Verify connection works
        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT payload FROM cluster_events WHERE event_id = $1")
                .execute(Tuple.of(eventId))
                .await()
                .indefinitely();
        assertThat(rows.iterator().next().getString("payload"))
                .isEqualTo("before-restart");

        // Close and recreate pool (simulates reconnection)
        pgPool.closeAndAwait();

        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(POSTGRES.getHost())
                .setPort(POSTGRES.getFirstMappedPort())
                .setDatabase(POSTGRES.getDatabaseName())
                .setUser(POSTGRES.getUsername())
                .setPassword(POSTGRES.getPassword());
        pgPool = PgPool.pool(connectOptions, new PoolOptions().setMaxSize(5));

        // Should still be able to read existing data
        rows = pgPool.preparedQuery(
                "SELECT payload FROM cluster_events WHERE event_id = $1")
                .execute(Tuple.of(eventId))
                .await()
                .indefinitely();
        assertThat(rows.iterator().next().getString("payload"))
                .isEqualTo("before-restart");
    }

    @Test
    void poolHandlesConcurrentQueries() throws Exception {
        initSchema();

        int count = 20;
        var futures = new ArrayList<java.util.concurrent.CompletableFuture<Void>>();

        for (int i = 0; i < count; i++) {
            final int idx = i;
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
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
                                "inst-concurrent",
                                UUID.randomUUID().toString(),
                                0L,
                                System.currentTimeMillis(),
                                "concurrent-" + idx)))
                        .await()
                        .indefinitely();
            }));
        }

        java.util.concurrent.CompletableFuture.allOf(
                futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        RowSet<Row> rows = pgPool.preparedQuery(
                "SELECT COUNT(*) AS cnt FROM cluster_events WHERE instance_id = $1")
                .execute(Tuple.of("inst-concurrent"))
                .await()
                .indefinitely();

        assertThat(rows.iterator().next().getLong("cnt")).isEqualTo(count);
    }

    // ─── Negative tests ───

    @Test
    void duplicateEventIdRejected() {
        initSchema();

        String eventId = UUID.randomUUID().toString();
        insertTestEvent(eventId, "NEURON_CREATED", "first");

        // Second insert with same event_id should fail
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            pgPool.preparedQuery("""
                    INSERT INTO cluster_events
                        (event_id, event_type, instance_id,
                         neuron_id_uuid, neuron_generation,
                         event_timestamp, payload)
                    VALUES ($1, $2, $3, $4, $5, $6, $7)
                    """)
                    .execute(Tuple.tuple(java.util.List.of(
                            eventId, // duplicate
                            "NEURON_MUTATED",
                            "inst-2",
                            UUID.randomUUID().toString(),
                            0L,
                            System.currentTimeMillis(),
                            "duplicate")))
                    .await()
                    .indefinitely();
        }).hasMessageContaining("duplicate");
    }

    @Test
    void queryEmptyTableReturnsNoRows() {
        initSchema();

        RowSet<Row> rows = pgPool.query("SELECT * FROM cluster_events")
                .execute()
                .await()
                .indefinitely();

        assertThat(rows.rowCount()).isZero();
    }

    // ─── helpers ───

    private void initSchema() {
        pgPool.query(DDL + "; " + INDEXES)
                .execute()
                .await()
                .indefinitely();
    }

    private void insertTestEvent(String eventId, String eventType, String payload) {
        insertTestEventWithTimestamp(eventId, eventType, System.currentTimeMillis(), "inst-1", payload);
    }

    private void insertTestEvent(String eventId, String eventType, String payload, String instanceId) {
        insertTestEventWithTimestamp(eventId, eventType, System.currentTimeMillis(), instanceId, payload);
    }

    private void insertTestEventWithTimestamp(String eventId, String eventType, long timestamp) {
        insertTestEventWithTimestamp(eventId, eventType, timestamp, "inst-1", "payload");
    }

    private void insertTestEventWithTimestamp(String eventId, String eventType,
                                               long timestamp, String instanceId, String payload) {
        io.vertx.mutiny.sqlclient.Tuple t = io.vertx.mutiny.sqlclient.Tuple.tuple()
                .addString(eventId)
                .addString(eventType)
                .addString(instanceId)
                .addString(UUID.randomUUID().toString())
                .addLong(0L)
                .addLong(timestamp)
                .addString(payload);
        pgPool.preparedQuery("""
                INSERT INTO cluster_events
                    (event_id, event_type, instance_id,
                     neuron_id_uuid, neuron_generation,
                     event_timestamp, payload)
                VALUES ($1, $2, $3, $4, $5, $6, $7)
                """)
                .execute(t)
                .await()
                .indefinitely();
    }
}
