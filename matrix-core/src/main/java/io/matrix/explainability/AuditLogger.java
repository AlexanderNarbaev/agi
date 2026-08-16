package io.matrix.explainability;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Audit logger for neuron decision provenance.
 *
 * <p>Stores all decision provenance records in PostgreSQL via R2DBC (Vert.x Mutiny PgPool).
 * Supports querying by time range, neuron ID, and decision type. Includes configurable
 * retention policy for automatic cleanup of old records.
 *
 * <p>Thread-safe: relies on the thread-safety of Vert.x PgPool and immutable records.
 *
 * <p>Ref: arXiv:2605.11595 §5 — "Audit and Compliance"
 */
@ApplicationScoped
public final class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS decision_audit_log (
            id BIGSERIAL PRIMARY KEY,
            decision_id VARCHAR(64) NOT NULL UNIQUE,
            neuron_id VARCHAR(128) NOT NULL,
            decision_timestamp TIMESTAMPTZ NOT NULL,
            input_k INTEGER NOT NULL,
            output BOOLEAN NOT NULL,
            confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
            provenance_json TEXT NOT NULL,
            explanation_count INTEGER NOT NULL DEFAULT 0,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )""";

    private static final String INDEX_DDL = """
        CREATE INDEX IF NOT EXISTS idx_dal_timestamp ON decision_audit_log(decision_timestamp);
        CREATE INDEX IF NOT EXISTS idx_dal_neuron ON decision_audit_log(neuron_id);
        CREATE INDEX IF NOT EXISTS idx_dal_output ON decision_audit_log(output)""";

    @Inject
    PgPool pgPool;

    private volatile boolean initialized;

    /** Default retention period: 90 days. */
    private volatile Duration retentionPeriod = Duration.ofDays(90);

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    pgPool.query(DDL + "; " + INDEX_DDL)
                            .execute()
                            .await()
                            .indefinitely();
                    initialized = true;
                }
            }
        }
    }

    /**
     * Sets the retention period for audit logs.
     *
     * @param period retention duration (e.g., Duration.ofDays(90))
     */
    public void setRetentionPeriod(Duration period) {
        if (period == null || period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("Retention period must be positive");
        }
        this.retentionPeriod = period;
    }

    /**
     * Returns the current retention period.
     */
    public Duration retentionPeriod() {
        return retentionPeriod;
    }

    /**
     * Logs a decision with full provenance.
     *
     * @param provenance the decision provenance record
     * @return Uni that completes when the record is persisted
     */
    public Uni<Void> log(DecisionProvenance provenance) {
        ensureInitialized();

        String provenanceJson = provenance.toJson();
        Tuple params = Tuple.tuple(List.of(
                provenance.decisionId(),
                provenance.neuronId(),
                provenance.timestamp(),
                provenance.inputK(),
                provenance.output(),
                provenance.confidence(),
                provenanceJson,
                provenance.explanationPrimitives().size()
        ));

        return pgPool.preparedQuery("""
                INSERT INTO decision_audit_log
                    (decision_id, neuron_id, decision_timestamp,
                     input_k, output, confidence, provenance_json, explanation_count)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
                ON CONFLICT (decision_id) DO NOTHING
                """)
                .execute(params)
                .replaceWithVoid()
                .onFailure().invoke(error ->
                        log.error("Failed to log decision {}: {}",
                                provenance.decisionId(), error.getMessage()));
    }

    /**
     * Queries audit logs by neuron ID.
     *
     * @param neuronId the neuron ID to filter by
     * @param limit    maximum number of records
     * @return list of provenance records
     */
    public Uni<List<DecisionProvenance>> findByNeuronId(String neuronId, int limit) {
        ensureInitialized();
        return pgPool.preparedQuery("""
                SELECT provenance_json FROM decision_audit_log
                WHERE neuron_id = $1
                ORDER BY decision_timestamp DESC
                LIMIT $2
                """)
                .execute(Tuple.of(neuronId, limit))
                .map(this::toProvenanceList);
    }

    /**
     * Queries audit logs by time range.
     *
     * @param from start of time range (inclusive)
     * @param to   end of time range (inclusive)
     * @param limit maximum number of records
     * @return list of provenance records
     */
    public Uni<List<DecisionProvenance>> findByTimeRange(Instant from, Instant to, int limit) {
        ensureInitialized();
        return pgPool.preparedQuery("""
                SELECT provenance_json FROM decision_audit_log
                WHERE decision_timestamp BETWEEN $1 AND $2
                ORDER BY decision_timestamp DESC
                LIMIT $3
                """)
                .execute(Tuple.of(from, to, limit))
                .map(this::toProvenanceList);
    }

    /**
     * Queries audit logs by output value (true/false decisions).
     *
     * @param output the output value to filter by
     * @param limit  maximum number of records
     * @return list of provenance records
     */
    public Uni<List<DecisionProvenance>> findByOutput(boolean output, int limit) {
        ensureInitialized();
        return pgPool.preparedQuery("""
                SELECT provenance_json FROM decision_audit_log
                WHERE output = $1
                ORDER BY decision_timestamp DESC
                LIMIT $2
                """)
                .execute(Tuple.of(output, limit))
                .map(this::toProvenanceList);
    }

    /**
     * Retrieves a single decision by its ID.
     *
     * @param decisionId the decision ID
     * @return the provenance record, or null if not found
     */
    public Uni<DecisionProvenance> findById(String decisionId) {
        ensureInitialized();
        return pgPool.preparedQuery("""
                SELECT provenance_json FROM decision_audit_log
                WHERE decision_id = $1
                """)
                .execute(Tuple.of(decisionId))
                .map(rows -> {
                    if (rows.rowCount() == 0) return null;
                    return DecisionProvenance.fromJson(
                            rows.iterator().next().getString("provenance_json"));
                });
    }

    /**
     * Returns the total number of audit log entries.
     */
    public Uni<Long> count() {
        ensureInitialized();
        return pgPool.preparedQuery("SELECT COUNT(*) AS cnt FROM decision_audit_log")
                .execute()
                .map(rows -> {
                    if (rows.rowCount() == 0) return 0L;
                    return rows.iterator().next().getLong("cnt");
                });
    }

    /**
     * Applies the retention policy: deletes records older than the configured period.
     *
     * @return Uni that completes with the number of deleted records
     */
    public Uni<Long> applyRetentionPolicy() {
        ensureInitialized();
        Instant cutoff = Instant.now().minus(retentionPeriod);
        return pgPool.preparedQuery("""
                DELETE FROM decision_audit_log
                WHERE decision_timestamp < $1
                """)
                .execute(Tuple.of(cutoff))
                .map(rows -> (long) rows.rowCount())
                .onItem().invoke(count ->
                        log.info("Retention policy removed {} audit records older than {}",
                                count, cutoff));
    }

    /**
     * Truncates all audit log entries (for testing).
     */
    public Uni<Void> truncate() {
        ensureInitialized();
        return pgPool.query("TRUNCATE TABLE decision_audit_log")
                .execute()
                .replaceWithVoid();
    }

    private List<DecisionProvenance> toProvenanceList(RowSet<Row> rows) {
        if (rows.rowCount() == 0) return List.of();
        List<DecisionProvenance> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(DecisionProvenance.fromJson(row.getString("provenance_json")));
        }
        return list;
    }
}
