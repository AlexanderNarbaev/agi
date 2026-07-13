package io.matrix.ethics.guardrail;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reactive PostgreSQL repository for {@link GuardrailAuditLog} entries.
 *
 * <p>Persists all guardrail decisions for EU AI Act compliance (Art. 12).
 * Table auto-created on first write.
 *
 * <p>Uses Vert.x Mutiny PgClient for non-blocking database access.
 */
@ApplicationScoped
public class GuardrailAuditRepository {

    private static final Logger log = LoggerFactory.getLogger(GuardrailAuditRepository.class);

    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS guardrail_audit_log (
            id BIGSERIAL PRIMARY KEY,
            timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            user_id VARCHAR(128) NOT NULL,
            endpoint VARCHAR(512) NOT NULL,
            input_text TEXT,
            output_text TEXT,
            guard_name VARCHAR(128) NOT NULL,
            verdict VARCHAR(16) NOT NULL,
            reason TEXT,
            confidence DOUBLE PRECISION,
            patterns TEXT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )""";

    private static final String INDEX_DDL = """
        CREATE INDEX IF NOT EXISTS idx_gal_timestamp ON guardrail_audit_log(timestamp);
        CREATE INDEX IF NOT EXISTS idx_gal_user ON guardrail_audit_log(user_id);
        CREATE INDEX IF NOT EXISTS idx_gal_verdict ON guardrail_audit_log(verdict)""";

    @Inject
    PgPool pgPool;

    private volatile boolean initialized;

    /**
     * Ensure the audit table exists.
     */
    private Uni<Void> ensureInitialized() {
        if (initialized) {
            return Uni.createFrom().voidItem();
        }
        return pgPool.query(DDL + "; " + INDEX_DDL)
                .execute()
                .onItem().invoke(() -> initialized = true)
                .replaceWithVoid();
    }

    /**
     * Persist a guardrail audit log entry.
     *
     * @param log the audit log entry to persist
     * @return Uni that completes when the entry is persisted
     */
    public Uni<Void> persist(GuardrailAuditLog log) {
        return ensureInitialized()
                .chain(() -> {
                    String patternsStr = log.patterns() != null
                            ? String.join(",", log.patterns())
                            : "";

                    Tuple params = Tuple.tuple(List.of(
                            log.timestamp(),
                            log.userId(),
                            log.endpoint(),
                            log.inputText(),
                            log.outputText(),
                            log.guardName(),
                            log.verdict().name(),
                            log.reason(),
                            log.confidence(),
                            patternsStr
                    ));

                    return pgPool.preparedQuery("""
                            INSERT INTO guardrail_audit_log
                                (timestamp, user_id, endpoint, input_text, output_text,
                                 guard_name, verdict, reason, confidence, patterns)
                            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
                            """)
                            .execute(params)
                            .replaceWithVoid();
                })
                .onFailure().invoke(error ->
                        this.log.error("Failed to persist audit log: {}", error.getMessage()));
    }

    /**
     * Query recent audit log entries.
     *
     * @param limit maximum number of entries to return
     * @return list of audit log entries
     */
    public Uni<List<GuardrailAuditLog>> findRecent(int limit) {
        return ensureInitialized()
                .chain(() -> pgPool.preparedQuery("""
                        SELECT id, timestamp, user_id, endpoint, input_text, output_text,
                               guard_name, verdict, reason, confidence, patterns
                        FROM guardrail_audit_log
                        ORDER BY timestamp DESC
                        LIMIT $1
                        """)
                        .execute(Tuple.of(limit))
                        .map(this::toAuditLogList));
    }

    /**
     * Query audit log entries by user.
     *
     * @param userId the user ID to filter by
     * @param limit  maximum number of entries
     * @return list of audit log entries
     */
    public Uni<List<GuardrailAuditLog>> findByUser(String userId, int limit) {
        return ensureInitialized()
                .chain(() -> pgPool.preparedQuery("""
                        SELECT id, timestamp, user_id, endpoint, input_text, output_text,
                               guard_name, verdict, reason, confidence, patterns
                        FROM guardrail_audit_log
                        WHERE user_id = $1
                        ORDER BY timestamp DESC
                        LIMIT $2
                        """)
                        .execute(Tuple.of(userId, limit))
                        .map(this::toAuditLogList));
    }

    /**
     * Query audit log entries by verdict type.
     *
     * @param verdict the verdict to filter by
     * @param limit   maximum number of entries
     * @return list of audit log entries
     */
    public Uni<List<GuardrailAuditLog>> findByVerdict(GuardrailVerdict verdict, int limit) {
        return ensureInitialized()
                .chain(() -> pgPool.preparedQuery("""
                        SELECT id, timestamp, user_id, endpoint, input_text, output_text,
                               guard_name, verdict, reason, confidence, patterns
                        FROM guardrail_audit_log
                        WHERE verdict = $1
                        ORDER BY timestamp DESC
                        LIMIT $2
                        """)
                        .execute(Tuple.of(verdict.name(), limit))
                        .map(this::toAuditLogList));
    }

    /**
     * Get audit statistics: count by verdict type.
     *
     * @return count of each verdict type
     */
    public Uni<Long> countByVerdict(GuardrailVerdict verdict) {
        return ensureInitialized()
                .chain(() -> pgPool.preparedQuery("""
                        SELECT COUNT(*) AS cnt FROM guardrail_audit_log WHERE verdict = $1
                        """)
                        .execute(Tuple.of(verdict.name()))
                        .map(rows -> {
                            if (rows.rowCount() == 0) return 0L;
                            return rows.iterator().next().getLong("cnt");
                        }));
    }

    /**
     * Delete audit log entries older than the retention period.
     *
     * @param retentionDays number of days to retain
     * @return number of deleted rows
     */
    public Uni<Integer> purgeOlderThan(int retentionDays) {
        return ensureInitialized()
                .chain(() -> pgPool.preparedQuery("""
                        DELETE FROM guardrail_audit_log
                        WHERE timestamp < NOW() - ($1 || ' days')::INTERVAL
                        """)
                        .execute(Tuple.of(String.valueOf(retentionDays)))
                        .map(RowSet::rowCount));
    }

    private List<GuardrailAuditLog> toAuditLogList(RowSet<Row> rows) {
        if (rows.rowCount() == 0) return List.of();
        var result = new ArrayList<GuardrailAuditLog>();
        for (Row row : rows) {
            String patternsStr = row.getString("patterns");
            List<String> patterns = (patternsStr != null && !patternsStr.isBlank())
                    ? List.of(patternsStr.split(","))
                    : List.of();

            var ts = row.getLocalDateTime("timestamp");
            result.add(new GuardrailAuditLog(
                    row.getLong("id"),
                    ts != null ? ts.toInstant(java.time.ZoneOffset.UTC) : Instant.now(),
                    row.getString("user_id"),
                    row.getString("endpoint"),
                    row.getString("input_text"),
                    row.getString("output_text"),
                    row.getString("guard_name"),
                    GuardrailVerdict.valueOf(row.getString("verdict")),
                    row.getString("reason"),
                    row.getDouble("confidence"),
                    patterns
            ));
        }
        return result;
    }
}
