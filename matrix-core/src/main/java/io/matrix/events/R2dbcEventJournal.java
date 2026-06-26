package io.matrix.events;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * R2DBC (PostgreSQL) persistent event journal for Event Sourcing.
 *
 * <p>Replaces {@link InMemoryEventJournal} for production deployments.
 * Events survive restarts and can be replayed by any instance.
 *
 * <p>Ref: L6_Memory.md §3
 */
@ApplicationScoped
public final class R2dbcEventJournal implements EventJournal {

    @Inject
    PgPool pgPool;

    void init(@Observes StartupEvent ev) {
        pgPool.query("""
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
            );
            CREATE INDEX IF NOT EXISTS idx_cluster_events_instance
                ON cluster_events(instance_id);
            CREATE INDEX IF NOT EXISTS idx_cluster_events_type
                ON cluster_events(event_type);
            CREATE INDEX IF NOT EXISTS idx_cluster_events_timestamp
                ON cluster_events(event_timestamp);
            """)
            .execute()
            .await()
            .indefinitely();
    }

    @Override
    public long append(ClusterEvent event) {
        Tuple params = Tuple.tuple(java.util.List.of(
                event.eventId(),
                event.type().name(),
                event.instanceId(),
                event.neuronId() != null ? event.neuronId().uuid().toString() : null,
                event.neuronId() != null ? event.neuronId().generation() : null,
                event.timestamp(),
                event.payload()
        ));

        RowSet<Row> rows = pgPool.preparedQuery("""
            INSERT INTO cluster_events
                (event_id, event_type, instance_id,
                 neuron_id_uuid, neuron_generation,
                 event_timestamp, payload)
            VALUES ($1, $2, $3, $4, $5, $6, $7)
            RETURNING event_index
            """)
            .execute(params)
            .await()
            .indefinitely();

        return rows.iterator().next().getLong("event_index") - 1;
    }

    @Override
    public List<ClusterEvent> replayFrom(long fromIndex) {
        RowSet<Row> rows = pgPool.preparedQuery("""
            SELECT event_id, event_type, instance_id,
                   neuron_id_uuid, neuron_generation,
                   event_timestamp, payload
            FROM cluster_events
            WHERE event_index > $1
            ORDER BY event_index ASC
            """)
            .execute(Tuple.of(fromIndex))
            .await()
            .indefinitely();

        return toEventList(rows);
    }

    @Override
    public List<ClusterEvent> replayAll() {
        RowSet<Row> rows = pgPool.preparedQuery("""
            SELECT event_id, event_type, instance_id,
                   neuron_id_uuid, neuron_generation,
                   event_timestamp, payload
            FROM cluster_events
            ORDER BY event_index ASC
            """)
            .execute()
            .await()
            .indefinitely();

        return toEventList(rows);
    }

    @Override
    public long size() {
        RowSet<Row> rows = pgPool.query("SELECT COUNT(*) AS cnt FROM cluster_events")
                .execute()
                .await()
                .indefinitely();
        return rows.iterator().next().getLong("cnt");
    }

    public Uni<Void> truncate() {
        return pgPool.query("TRUNCATE TABLE cluster_events")
                .execute()
                .replaceWithVoid();
    }

    public Uni<List<ClusterEvent>> findByInstanceId(String instanceId) {
        return pgPool.preparedQuery("""
            SELECT event_id, event_type, instance_id,
                   neuron_id_uuid, neuron_generation,
                   event_timestamp, payload
            FROM cluster_events
            WHERE instance_id = $1
            ORDER BY event_index ASC
            """)
            .execute(Tuple.of(instanceId))
            .map(this::toEventList);
    }

    public Uni<List<ClusterEvent>> findByType(String eventType, int limit) {
        return pgPool.preparedQuery("""
            SELECT event_id, event_type, instance_id,
                   neuron_id_uuid, neuron_generation,
                   event_timestamp, payload
            FROM cluster_events
            WHERE event_type = $1
            ORDER BY event_index DESC
            LIMIT $2
            """)
            .execute(Tuple.of(eventType, limit))
            .map(this::toEventList);
    }

    private List<ClusterEvent> toEventList(RowSet<Row> rows) {
        if (rows.rowCount() == 0) return List.of();
        var events = new ArrayList<ClusterEvent>();
        for (Row row : rows) {
            String nUuid = row.getString("neuron_id_uuid");
            Long nGen = row.getLong("neuron_generation");
            io.matrix.cluster.NeuronId nid = (nUuid != null && nGen != null)
                    ? new io.matrix.cluster.NeuronId(java.util.UUID.fromString(nUuid), nGen)
                    : null;

            events.add(new ClusterEvent(
                    row.getString("event_id"),
                    ClusterEventType.valueOf(row.getString("event_type")),
                    row.getString("instance_id"),
                    nid,
                    row.getLong("event_timestamp"),
                    row.getString("payload")
            ));
        }
        return Collections.unmodifiableList(events);
    }
}
