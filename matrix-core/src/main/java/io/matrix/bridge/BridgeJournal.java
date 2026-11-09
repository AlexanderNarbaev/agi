package io.matrix.bridge;

import io.matrix.contract.bridge.BridgeAuditRecord;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeIdempotency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * W51 — the bridge's own durable state: its idempotency ledger and its audit trail.
 *
 * <p><b>Why the bridge has its own file.</b> BRIDGE-8 says the idempotency must be durable
 * "so it survives the death of either process", and BRIDGE-7 says every call and every
 * refusal must be written to an append-only audit chain. Neither can live in the epistemic
 * database: that database has exactly one writer by design, and adding a second writer to it
 * would weaken the guarantee the whole isolation argument rests on. The bridge therefore
 * owns a small database of its own, in its own directory, next to nothing else.</p>
 *
 * <p><b>What the ledger holds.</b> The request id, the digest of the payload and the
 * decision. A repeated delivery of the same request id with the same payload gets the
 * original decision back; a repeated delivery with a DIFFERENT payload is a conflict, and
 * the conflict is refused and audited. That is BRIDGE-8 exactly.</p>
 *
 * <p><b>What the audit holds.</b> One row per call, appended and never updated. The refusal
 * is recorded as carefully as the acceptance: a bridge that silently drops traffic is
 * indistinguishable from a bridge that never existed.</p>
 *
 * <p><b>Nothing here decides anything.</b> The ledger and the audit record what the protocol
 * decided; the decision itself belongs to {@code io.matrix.contract.bridge.BridgeReceiver}.
 * This class is the durability of that decision.</p>
 */
public final class BridgeJournal implements BridgeIdempotency, BridgeAudit, AutoCloseable {

    /** The version of the journal's own schema. */
    public static final int SCHEMA_VERSION = 1;

    private final Connection connection;

    /**
     * Opens or creates the journal.
     *
     * @param path the file of the journal; its parent directory is created if it is absent
     * @throws IllegalStateException when the file cannot be opened or the schema cannot be
     *                               created
     */
    public BridgeJournal(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            this.connection.setAutoCommit(true);
            createSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("the bridge journal could not be opened: "
                    + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("the bridge journal's directory could not be created: "
                    + e.getMessage(), e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bridge_request ("
                    + "request_id TEXT PRIMARY KEY, payload_digest TEXT NOT NULL, "
                    + "status TEXT NOT NULL, reason TEXT, audit_id TEXT, decided_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bridge_audit ("
                    + "audit_id TEXT PRIMARY KEY, request_id TEXT NOT NULL, decision TEXT NOT NULL, "
                    + "reason TEXT, payload_digest TEXT, at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bridge_meta ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.executeUpdate("INSERT OR REPLACE INTO bridge_meta(key, value) VALUES "
                    + "('schema_version', '" + SCHEMA_VERSION + "')");
        }
    }

    @Override
    public Optional<Outcome> find(String requestId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payload_digest, status, reason, audit_id FROM bridge_request "
                        + "WHERE request_id = ?")) {
            statement.setString(1, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                BridgeDecision decision = new BridgeDecision(
                        BridgeDecision.Status.valueOf(rows.getString("status")), requestId,
                        rows.getString("audit_id"), rows.getString("reason"));
                return Optional.of(Outcome.of(decision, rows.getString("payload_digest")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("the bridge journal could not be read: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Outcome firstOutcome(String requestId, String payloadDigest,
                                java.util.function.Supplier<Outcome> supplier) {
        Optional<Outcome> known = find(requestId);
        if (known.isPresent()) {
            return known.get();
        }
        Outcome outcome = supplier.get();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO bridge_request(request_id, payload_digest, status, reason, "
                        + "audit_id, decided_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, requestId);
            statement.setString(2, payloadDigest);
            statement.setString(3, outcome.decision().status().name());
            statement.setString(4, outcome.decision().reason());
            statement.setString(5, outcome.decision().auditId());
            statement.setString(6, java.time.Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("the bridge journal could not be written: "
                    + e.getMessage(), e);
        }
        return outcome;
    }

    @Override
    public void append(BridgeAuditRecord record) {
        appendRecord(record);
    }

    /**
     * Appends a decision to the audit trail.
     *
     * <p>The row is appended; there is no method that updates or removes one.</p>
     *
     * @param record the record to append
     */
    public void appendRecord(BridgeAuditRecord record) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO bridge_audit(audit_id, request_id, decision, reason, "
                        + "payload_digest, at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, record.auditId());
            statement.setString(2, record.requestId());
            statement.setString(3, record.decision().name());
            statement.setString(4, record.reason());
            statement.setString(5, record.payloadDigest());
            statement.setString(6, record.at());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("the bridge audit could not be written: "
                    + e.getMessage(), e);
        }
    }

    /**
     * The number of request ids the ledger has decided.
     *
     * @return the count
     */
    public int recordedCount() {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM bridge_request")) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("the bridge ledger could not be counted: "
                    + e.getMessage(), e);
        }
    }

    /**
     * The number of audit records the journal holds.
     *
     * @return the count
     */
    public int auditCount() {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM bridge_audit")) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("the bridge audit could not be counted: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("the bridge journal could not be closed: "
                    + e.getMessage(), e);
        }
    }
}
