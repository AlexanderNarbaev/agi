package io.matrix.privacy.storage;

import io.matrix.privacy.Tombstone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostgreSQL tombstone storage — durable backend using JDBC.
 *
 * <p>Schema (auto-created on first use):
 * <pre>
 * CREATE TABLE IF NOT EXISTS tombstones (
 *   id            UUID PRIMARY KEY,
 *   subject_id    TEXT NOT NULL,
 *   resource_type TEXT NOT NULL,
 *   resource_id   TEXT NOT NULL,
 *   reason        TEXT NOT NULL,
 *   signature     TEXT,
 *   deleted_at    TIMESTAMPTZ NOT NULL,
 *   requester_id  TEXT NOT NULL,
 *   UNIQUE (resource_type, resource_id)
 * );
 * </pre>
 *
 * <p>Connection pool: provided by Quarkus Agroal via the injected
 * {@link DataSource}; queries are async via a small bounded executor
 * so callers don't block the calling thread on JDBC round-trips.
 */
public final class PostgresTombstoneStorage implements TombstoneStorage {

    private static final Logger log = LoggerFactory.getLogger(PostgresTombstoneStorage.class);

    private static final String SCHEMA_SQL = """
            CREATE TABLE IF NOT EXISTS tombstones (
                id            UUID PRIMARY KEY,
                subject_id    TEXT NOT NULL,
                resource_type TEXT NOT NULL,
                resource_id   TEXT NOT NULL,
                reason        TEXT NOT NULL,
                signature     TEXT,
                deleted_at    TIMESTAMP NOT NULL,
                requester_id  TEXT NOT NULL,
                UNIQUE (resource_type, resource_id)
            );
            CREATE INDEX IF NOT EXISTS tombstones_subject_idx ON tombstones (subject_id);
            CREATE INDEX IF NOT EXISTS tombstones_reason_idx  ON tombstones (reason);
            CREATE INDEX IF NOT EXISTS tombstones_deleted_idx ON tombstones (deleted_at DESC);
            """;

    private static final String INSERT_SQL = """
            INSERT INTO tombstones
              (id, subject_id, resource_type, resource_id, reason, signature, deleted_at, requester_id)
            SELECT ?, ?, ?, ?, ?, ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM tombstones WHERE resource_type = ? AND resource_id = ?)
            """;

    private static final String SELECT_BY_KEY_SQL = """
            SELECT id, subject_id, resource_type, resource_id, reason, signature, deleted_at, requester_id
            FROM tombstones WHERE resource_type = ? AND resource_id = ?
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT id, subject_id, resource_type, resource_id, reason, signature, deleted_at, requester_id
            FROM tombstones ORDER BY deleted_at DESC
            """;

    private static final String SELECT_BY_REASON_SQL = """
            SELECT id, subject_id, resource_type, resource_id, reason, signature, deleted_at, requester_id
            FROM tombstones WHERE reason LIKE ? ORDER BY deleted_at DESC
            """;

    private static final String SELECT_BY_SUBJECT_SQL = """
            SELECT id, subject_id, resource_type, resource_id, reason, signature, deleted_at, requester_id
            FROM tombstones WHERE subject_id = ? ORDER BY deleted_at DESC
            """;

    private static final String HEALTH_CHECK_SQL = "SELECT 1";

    private final DataSource dataSource;
    private final ExecutorService executor;
    private volatile boolean schemaEnsured;

    public PostgresTombstoneStorage(DataSource dataSource) {
        this.dataSource = dataSource;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "postgres-tombstone");
            t.setDaemon(true);
            return t;
        });
    }

    private void ensureSchema() {
        if (schemaEnsured) return;
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute(SCHEMA_SQL);
            schemaEnsured = true;
            log.info("PostgresTombstoneStorage schema ensured");
        } catch (SQLException e) {
            throw new StorageUnavailableException("Could not ensure tombstones schema", e);
        }
    }

    @Override
    public CompletableFuture<Void> append(Tombstone tombstone) {
        return CompletableFuture.runAsync(() -> {
            ensureSchema();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
                ps.setObject(1, tombstone.id());
                ps.setString(2, tombstone.subjectId());
                ps.setString(3, tombstone.resourceType());
                ps.setString(4, tombstone.resourceId());
                ps.setString(5, tombstone.reason());
                ps.setString(6, tombstone.signature() == null ? "" : tombstone.signature());
                ps.setTimestamp(7, Timestamp.from(tombstone.deletedAt()));
                ps.setString(8, tombstone.requesterId());
                ps.setString(9, tombstone.resourceType());
                ps.setString(10, tombstone.resourceId());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new StorageUnavailableException("Failed to append tombstone", e);
            }
        }, executor);
    }

    @Override
    public Optional<Tombstone> load(String resourceType, String resourceId) {
        ensureSchema();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_BY_KEY_SQL)) {
            ps.setString(1, resourceType);
            ps.setString(2, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageUnavailableException("Failed to load tombstone", e);
        }
    }

    @Override
    public List<Tombstone> all() {
        ensureSchema();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            return readAll(rs);
        } catch (SQLException e) {
            throw new StorageUnavailableException("Failed to list tombstones", e);
        }
    }

    @Override
    public List<Tombstone> filterByReason(String reasonPrefix) {
        if (reasonPrefix == null || reasonPrefix.isEmpty()) return all();
        ensureSchema();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_BY_REASON_SQL)) {
            ps.setString(1, reasonPrefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        } catch (SQLException e) {
            throw new StorageUnavailableException("Failed to filter by reason", e);
        }
    }

    @Override
    public List<Tombstone> filterBySubject(String subjectId) {
        if (subjectId == null) return List.of();
        ensureSchema();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_BY_SUBJECT_SQL)) {
            ps.setString(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        } catch (SQLException e) {
            throw new StorageUnavailableException("Failed to filter by subject", e);
        }
    }

    @Override
    public boolean isHealthy() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(HEALTH_CHECK_SQL);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String backendId() { return "postgres"; }

    private static List<Tombstone> readAll(ResultSet rs) throws SQLException {
        List<Tombstone> out = new ArrayList<>();
        while (rs.next()) out.add(readRow(rs));
        return out;
    }

    private static Tombstone readRow(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String subjectId = rs.getString("subject_id");
        String resourceType = rs.getString("resource_type");
        String resourceId = rs.getString("resource_id");
        String reason = rs.getString("reason");
        String signature = rs.getString("signature");
        Timestamp ts = rs.getTimestamp("deleted_at");
        Instant deletedAt = ts == null ? Instant.EPOCH : ts.toInstant();
        String requesterId = rs.getString("requester_id");
        return new Tombstone(id, subjectId, resourceType, resourceId,
                reason, signature == null ? "" : signature, deletedAt, requesterId);
    }
}
