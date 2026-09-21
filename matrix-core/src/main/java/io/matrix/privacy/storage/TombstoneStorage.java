package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Pluggable storage backend for {@link io.matrix.privacy.TombstoneService}.
 *
 * <p>Implementations are responsible for durable, append-only persistence of
 * tombstones. The interface is intentionally narrow so multiple backends can
 * coexist and be configured per-deployment via environment variables.
 *
 * <p>Available implementations (in this package):
 * <ul>
 *   <li>{@link InMemoryTombstoneStorage} — default, no network/DB deps. Useful for tests.</li>
 *   <li>{@link PostgresTombstoneStorage} — JDBC backend using the existing quarkus-reactive-pg-client.</li>
 *   <li>{@link KafkaTombstoneStorage} — append-only Kafka topic for audit; pairs with PG for query.</li>
 *   <li>{@link S3TombstoneStorage} — long-term audit log on S3 (MinIO compatible).</li>
 *   <li>{@link CompositeTombstoneStorage} — fan-out to multiple backends (e.g. Kafka + PG + S3).</li>
 * </ul>
 *
 * <p>Selection: see {@link TombstoneStorageFactory}.
 *
 * <p>Ref: L12 §4 (audit trail), GDPR Article 30.
 */
public interface TombstoneStorage {

    /**
     * Append a tombstone to durable storage. Implementations MUST guarantee
     * that a successful return implies the tombstone is persisted and
     * queryable via {@link #load(String, String)}.
     */
    CompletableFuture<Void> append(Tombstone tombstone);

    /**
     * Load a specific tombstone by composite key (resourceType + resourceId).
     */
    Optional<Tombstone> load(String resourceType, String resourceId);

    /**
     * List all tombstones (newest-first). May be expensive; prefer
     * {@link #filter} when possible.
     */
    List<Tombstone> all();

    /**
     * Filter tombstones by reason prefix (e.g. {@code "gdpr."}).
     */
    List<Tombstone> filterByReason(String reasonPrefix);

    /**
     * Filter tombstones by subject id.
     */
    List<Tombstone> filterBySubject(String subjectId);

    /**
     * Health-check / liveness probe for monitoring.
     *
     * @return {@code true} when the backend is reachable and operational
     */
    boolean isHealthy();

    /** Stable identifier for logs and metrics (e.g. {@code "postgres"}, {@code "kafka"}). */
    String backendId();
}
