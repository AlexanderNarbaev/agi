package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ValidationKind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * W44 — the durable state of the cognitive kernel.
 *
 * <p>What lives here is the state that the epistemic core has no place for: episodes,
 * predictions, prediction errors, promoted procedures, internal tasks and the event stream that
 * makes a cycle replayable. Claims, evidence, sources and cases are NOT here — they belong to
 * {@code io.matrix.knowledge.KnowledgeStore}, and the kernel writes them there.</p>
 *
 * <p>The store opens the SAME SQLite file as the knowledge store with its own connection. That
 * is a deliberate cost, recorded in {@code ADR-W44-01} §2.4: the alternative was to put the
 * cognitive tables inside {@code KnowledgeStore}, which would have forced
 * {@code io.matrix.knowledge} to import {@code io.matrix.cognitive} records and created a package
 * cycle in the class that 1518 existing tests exercise. The price is that a write spanning both
 * stores is not atomic; the mitigation is ordering (cognitive state first, epistemic state
 * second) and deterministic identifiers, so an interrupted write is idempotent on replay.</p>
 *
 * <p>The durability settings are the same ones the knowledge store uses — WAL and
 * {@code synchronous=FULL} — because the property that matters is the same: a committed write
 * must be on disk before the call returns, so a {@code kill -9} cannot lose it.</p>
 *
 * <h2>W45.0 — what each table promises</h2>
 * <p>W44 wrote every record with {@code INSERT OR REPLACE}. That is the same as saying "the last
 * writer wins", and for a system whose value is its history it is the same as saying "there is no
 * history". A repeated request replaced the episode it repeated, and with it the prediction, the
 * error, the task and every event of the first attempt. W45.0 replaces the promise with an
 * explicit one, table by table:</p>
 * <ul>
 *   <li><b>{@code cognitive_event} — IMMUTABLE. Insert-only.</b> An event is a fact about a
 *       moment; editing it would make the trace a reconstruction instead of a record. The same
 *       id with the same content is an idempotent no-op; the same id with different content, or
 *       a different event at a position that is already taken, is a conflict. Both the event id
 *       and the position {@code (episode_id, sequence)} are unique.</li>
 *   <li><b>{@code cognitive_prediction}, {@code cognitive_error}, {@code cognitive_task} —
 *       IMMUTABLE. Insert-only.</b> They are the facts of one execution. The same argument
 *       applies: a prediction that was falsified must still be there to be read, and a task that
 *       was generated must not vanish because a later cycle wrote the same id.</li>
 *   <li><b>{@code cognitive_episode} — MUTABLE, and only in one direction.</b> An episode is
 *       OPENED and later CLOSED. The open is an insert and the close is an explicit
 *       {@code UPDATE} of {@code closed_at} alone: the summary and the start instant of an
 *       episode are never rewritten, because they are the account of what the episode was.</li>
 *   <li><b>{@code cognitive_procedure} — a VERSION, and versions are immutable.</b> The identity
 *       of a procedure version is the pair (id, version) — the {@code Procedure} record already
 *       carries the version, so that is the distinction that is used. Writing the same pair
 *       again is a no-op; writing the same id with a NEW version adds a row and leaves the
 *       predecessor in place, so a new version can never silently overwrite an older one.</li>
 * </ul>
 *
 * <h2>W45.0 — the durable sequence</h2>
 * <p>Identifiers that must not repeat are allocated from the {@code cognitive_sequence} table,
 * incremented inside a transaction. A restart cannot reset it, and two concurrent callers
 * cannot be handed the same number: the increment and the read happen in one transaction, and
 * SQLite serialises writers.</p>
 *
 * <p>Every method of this store synchronizes on the store. The store holds ONE JDBC
 * connection, and a connection is not thread-safe; serialising the access is what makes the
 * "two concurrent callers never get the same number" promise true inside one process.</p>
 */
public final class CognitiveStore implements AutoCloseable {

    /** The schema version this build writes. */
    public static final int SCHEMA_VERSION = 3;

    private static final System.Logger LOG = System.getLogger(CognitiveStore.class.getName());

    private final Connection connection;

    /**
     * Opens (and creates) the cognitive tables in the given SQLite file.
     *
     * @param databaseFile the knowledge database; it may already exist
     */
    public CognitiveStore(Path databaseFile) {
        try {
            Path parent = databaseFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            this.connection.setAutoCommit(true);
            configure();
            migrate();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open the cognitive store at " + databaseFile, e);
        }
    }

    private void configure() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS cognitive_schema_meta ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_episode (
                        id TEXT PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        started_at TEXT NOT NULL,
                        closed_at TEXT
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_prediction (
                        id TEXT PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        episode_id TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        predicted_value TEXT NOT NULL,
                        basis TEXT NOT NULL,
                        basis_version TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        observed_only INTEGER NOT NULL,
                        case_ids TEXT NOT NULL,
                        made_at TEXT NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_error (
                        id TEXT PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        prediction_id TEXT NOT NULL,
                        expected TEXT NOT NULL,
                        observed TEXT NOT NULL,
                        magnitude REAL NOT NULL,
                        disposition TEXT NOT NULL,
                        responsible_model TEXT NOT NULL,
                        responsible_version TEXT NOT NULL,
                        case_ids TEXT NOT NULL,
                        detected_at TEXT NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_task (
                        id TEXT PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        origin TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        serves_goal TEXT NOT NULL,
                        expected_benefit TEXT NOT NULL,
                        cost_estimate TEXT NOT NULL,
                        completion_condition TEXT NOT NULL,
                        stop_condition TEXT NOT NULL,
                        allowed_actions TEXT NOT NULL,
                        budget_wall_clock_millis INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        completed_at TEXT
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_event (
                        id TEXT PRIMARY KEY,
                        episode_id TEXT NOT NULL,
                        sequence INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        actor TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        occurred_at TEXT NOT NULL
                    )""");
            // W45.0: the durable counter. A row per sequence name, incremented in a
            // transaction, so that a restart cannot reset it and two concurrent callers
            // cannot be handed the same number.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_sequence (
                        name TEXT PRIMARY KEY,
                        value INTEGER NOT NULL
                    )""");            // W45.0: the durable mapping from an idempotency key to the episode of the
            // request it names. The outcome is stored with it, so a repeated delivery can
            // be answered with the original outcome and no new work.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_idempotency (
                        key TEXT PRIMARY KEY,
                        fingerprint TEXT NOT NULL,
                        episode_id TEXT,
                        signal_id TEXT,
                        state TEXT NOT NULL,
                        outcome TEXT,
                        created_at TEXT NOT NULL,
                        completed_at TEXT
                    )""");
            // W45.0: the DURABLE REGULATORY STATE.
            //
            // ADR-W44-01 lists the drives among the cognitive state the store persists, and
            // they were the one item on that list that was NOT persisted: the regulatory
            // state lived in the in-memory adapter, so every restart reset the drives to
            // neutral and the document and the implementation disagreed.
            //
            // The state table holds the CURRENT state with a version. The event table holds
            // the provenance of every change — which event moved which drive — and its id is
            // unique, so a replay after a crash is detected instead of applied twice.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_regulatory_state (
                        workspace_id TEXT PRIMARY KEY,
                        state TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        recorded_at TEXT NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_regulatory_event (
                        id TEXT PRIMARY KEY,
                        workspace_id TEXT NOT NULL,
                        episode_id TEXT,
                        kind TEXT NOT NULL,
                        magnitude REAL NOT NULL,
                        source TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_cognitive_event_episode "
                    + "ON cognitive_event(episode_id, sequence)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_cognitive_prediction_workspace "
                    + "ON cognitive_prediction(workspace_id, made_at)");
        }
        migrateProcedureTable();
        createEventPositionIndex();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_schema_meta(key, value) VALUES('version', ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, Integer.toString(SCHEMA_VERSION));
            ps.executeUpdate();
        }
    }

    /**
     * W45.0 — brings the procedure table to the (id, version) identity.
     *
     * <p>W44 keyed a procedure by its id alone, so a new version of a procedure could only
     * overwrite the old one — which is exactly the silent overwrite the wave forbids. The
     * version is already a field of the {@link Procedure} record, so the key becomes the PAIR
     * (id, version): a new version is a new row and the predecessor survives.</p>
     *
     * <p>The table is created when it is absent and migrated when it still has the W44 shape.
     * The migration copies the existing rows, so a database written by W44 keeps its
     * procedures; the rows it copies carry no {@code validation_kind}, which the record reads
     * as {@link io.matrix.cognitive.algorithms.ValidationKind#IN_SAMPLE}.</p>
     */
    private void migrateProcedureTable() throws SQLException {
        if (!procedureTableExists()) {
            createProcedureTable();
            return;
        }
        if (!procedureTableNeedsVersionKey()) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS cognitive_procedure_v2");
            st.execute("""
                    CREATE TABLE cognitive_procedure_v2 (
                        id TEXT NOT NULL,
                        workspace_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        problem_family TEXT NOT NULL,
                        version TEXT NOT NULL,
                        applicability TEXT NOT NULL,
                        required_facts TEXT NOT NULL,
                        reference_algorithm_id TEXT NOT NULL,
                        validation_cases INTEGER NOT NULL,
                        validation_agreement REAL NOT NULL,
                        use_count INTEGER NOT NULL,
                        failure_count INTEGER NOT NULL,
                        promoted_at TEXT NOT NULL,
                        invalidated_at TEXT,
                        validation_kind TEXT,
                        PRIMARY KEY (id, version)
                    )""");
            // A table written before the validation kind existed carries no such column,
            // and the rows it holds are consistency checks.
            String kindColumn = procedureTableHasValidationKind() ? "validation_kind" : "NULL";
            st.execute("INSERT OR IGNORE INTO cognitive_procedure_v2 (id, workspace_id, name, "
                    + "problem_family, version, applicability, required_facts, "
                    + "reference_algorithm_id, validation_cases, validation_agreement, "
                    + "use_count, failure_count, promoted_at, invalidated_at, validation_kind) "
                    + "SELECT id, workspace_id, name, problem_family, version, applicability, "
                    + "required_facts, reference_algorithm_id, validation_cases, "
                    + "validation_agreement, use_count, failure_count, promoted_at, "
                    + "invalidated_at, " + kindColumn + " FROM cognitive_procedure");
            st.execute("DROP TABLE cognitive_procedure");
            st.execute("ALTER TABLE cognitive_procedure_v2 RENAME TO cognitive_procedure");
        }
    }

    /** Creates the procedure table in the W45.0 shape. */
    private void createProcedureTable() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS cognitive_procedure (
                        id TEXT NOT NULL,
                        workspace_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        problem_family TEXT NOT NULL,
                        version TEXT NOT NULL,
                        applicability TEXT NOT NULL,
                        required_facts TEXT NOT NULL,
                        reference_algorithm_id TEXT NOT NULL,
                        validation_cases INTEGER NOT NULL,
                        validation_agreement REAL NOT NULL,
                        use_count INTEGER NOT NULL,
                        failure_count INTEGER NOT NULL,
                        promoted_at TEXT NOT NULL,
                        invalidated_at TEXT,
                        validation_kind TEXT,
                        PRIMARY KEY (id, version)
                    )""");
        }
    }

    /** True when the procedure table is there at all. */
    private boolean procedureTableExists() {
        return !procedureTableShape().names().isEmpty();
    }

    /** True when the procedure table still has the W44 primary key (id alone). */
    private boolean procedureTableNeedsVersionKey() {
        return !procedureTableShape().primaryKeyPositions().contains(2);
    }

    /** True when the procedure table already carries the validation kind. */
    private boolean procedureTableHasValidationKind() {
        return procedureTableShape().names().contains("validation_kind");
    }

    /** The columns of the procedure table and their positions in its primary key. */
    private record ProcedureTableShape(List<String> names, List<Integer> primaryKeyPositions) {
    }

    /** Reads the shape of the procedure table. */
    private ProcedureTableShape procedureTableShape() {
        List<String> names = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(cognitive_procedure)")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
                // The 'pk' column of PRAGMA table_info is the one-based position of the column
                // in the primary key; 0 means "not in the primary key".
                positions.add(rs.getInt("pk"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to inspect the procedure table", e);
        }
        return new ProcedureTableShape(names, positions);
    }

    /**
     * W45.0 — makes the position of an event inside its episode unique.
     *
     * <p>Two events may not occupy the same position: the trace is an ordered account, and a
     * position that holds two events is an account of nothing. The index is created when it can
     * be created; a database that already holds a duplicated position keeps the Java-level
     * check, which refuses the same write, and the reason is logged rather than hidden.</p>
     */
    private void createEventPositionIndex() {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_cognitive_event_position "
                    + "ON cognitive_event(episode_id, sequence)");
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "the existing database holds duplicated event positions; the unique index "
                            + "could not be created and the write-time check remains in force", e);
        }
    }

    // ── the durable sequence ────────────────────────────────────────────────

    /**
     * The next value of a named sequence.
     *
     * <p>The counter lives in the store, so a restart does not reset it, and the increment and
     * the read happen inside one transaction, so two concurrent callers can never be handed the
     * same number. The row is created on first use.</p>
     *
     * @param name the name of the sequence
     * @return the next value, starting at one
     */
    public synchronized long nextSequence(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("the name of a sequence must not be blank");
        }
        boolean autoCommit = true;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            // A counter is not history: the row is created once and then only incremented,
            // so an existing row must not be replaced.
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO cognitive_sequence(name, value) VALUES(?, 0)")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE cognitive_sequence SET value = value + 1 WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            long value;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT value FROM cognitive_sequence WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    value = rs.next() ? rs.getLong(1) : 0L;
                }
            }
            connection.commit();
            return value;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                LOG.log(System.Logger.Level.WARNING, "failed to roll the sequence back", ignored);
            }
            throw new IllegalStateException("failed to allocate the next value of the sequence "
                    + name, e);
        } finally {
            try {
                connection.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                LOG.log(System.Logger.Level.WARNING, "failed to restore the autocommit mode", e);
            }
        }
    }

    // ── episodes ─────────────────────────────────────────────────────────────

    /**
     * Opens an episode.
     *
     * <p>The episode is MUTABLE — it is opened here and closed by {@link #closeEpisode} — but the
     * open is an INSERT and never a replace: the summary and the start instant of an episode are
     * the account of what the episode was, and they are never rewritten. Opening an episode
     * that already exists with the same content is an idempotent no-op; opening one that
     * exists with different content is a conflict.</p>
     *
     * @param episodeId  the episode
     * @param workspaceId the workspace the episode belongs to
     * @param summary    what the episode is about
     * @param startedAt  the instant the episode started
     */
    public synchronized void startEpisode(String episodeId, String workspaceId, String summary,
                                          String startedAt) {
        List<String> existing = episodeContent(episodeId);
        if (existing != null) {
            // The open of an episode is compared WITHOUT the closing instant: what must never
            // be rewritten is the account of what the episode WAS — its workspace, its summary
            // and the instant it started. A close that already happened stays as it is.
            if (existing.subList(0, 3).equals(episodeContentOf(workspaceId, summary, startedAt))) {
                return;
            }
            throw new CognitiveConflictException(CognitiveConflictException.Kind.EPISODE_STATE,
                    "episode " + episodeId + " already exists with different content; an episode "
                            + "may be opened only once");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_episode(id, workspace_id, summary, started_at, closed_at) "
                        + "VALUES (?,?,?,?,NULL)")) {
            ps.setString(1, episodeId);
            ps.setString(2, workspaceId);
            ps.setString(3, summary);
            ps.setString(4, startedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new CognitiveConflictException(CognitiveConflictException.Kind.EPISODE_STATE,
                        "episode " + episodeId + " already exists");
            }
            throw new IllegalStateException("failed to record the episode", e);
        }
    }

    /**
     * Closes an episode.
     *
     * <p>An explicit UPDATE of {@code closed_at} and of nothing else: the summary and the start
     * instant of a closed episode are never rewritten. Closing an episode that is already closed
     * with the same instant is a no-op; closing it with a different instant is a conflict.</p>
     *
     * @param episodeId the episode
     * @param closedAt  the instant the episode ended
     */
    public synchronized void closeEpisode(String episodeId, String closedAt) {
        List<String> existing = episodeContent(episodeId);
        if (existing == null) {
            throw new CognitiveConflictException(CognitiveConflictException.Kind.EPISODE_STATE,
                    "episode " + episodeId + " was never opened");
        }
        if (existing.get(3) != null) {
            if (existing.get(3).equals(closedAt)) {
                return;
            }
            throw new CognitiveConflictException(CognitiveConflictException.Kind.EPISODE_STATE,
                    "episode " + episodeId + " is already closed at " + existing.get(3));
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE cognitive_episode SET closed_at = ? WHERE id = ? AND closed_at IS NULL")) {
            ps.setString(1, closedAt);
            ps.setString(2, episodeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close the episode", e);
        }
    }

    /** The stored content of an episode: workspace, summary, started_at, closed_at. */
    private List<String> episodeContent(String episodeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT workspace_id, summary, started_at, closed_at FROM cognitive_episode "
                        + "WHERE id = ?")) {
            ps.setString(1, episodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                List<String> content = new ArrayList<>();
                content.add(rs.getString(1));
                content.add(rs.getString(2));
                content.add(rs.getString(3));
                content.add(rs.getString(4));
                return content;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the episode " + episodeId, e);
        }
    }

    private static List<String> episodeContentOf(String workspaceId, String summary,
                                                  String startedAt) {
        List<String> content = new ArrayList<>();
        content.add(workspaceId);
        content.add(summary);
        content.add(startedAt);
        return content;
    }

    /** The episodes of a workspace, newest last. */
    public synchronized List<String> episodes(String workspaceId) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM cognitive_episode WHERE workspace_id = ? ORDER BY started_at, id")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("id"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the episodes", e);
        }
        return out;
    }

    // ── predictions ──────────────────────────────────────────────────────────

    /**
     * Records a prediction.
     *
     * <p>A prediction is an IMMUTABLE fact of an episode: it is what the system believed BEFORE
     * the outcome was known, and it is the thing an error is measured against. It is therefore
     * insert-only. The same prediction written again with the same content is a no-op; with
     * different content it is a conflict.</p>
     *
     * @param prediction the prediction
     */
    public synchronized void recordPrediction(Prediction prediction) {
        List<String> existing = predictionContent(prediction.id());
        if (existing != null) {
            if (existing.equals(predictionContentOf(prediction))) {
                return;
            }
            throw new CognitiveConflictException(
                    CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                    "prediction " + prediction.id() + " already exists with different content; "
                            + "a prediction of an episode is never rewritten");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_prediction(id, workspace_id, episode_id, subject, "
                        + "predicted_value, basis, basis_version, confidence, observed_only, "
                        + "case_ids, made_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, prediction.id());
            ps.setString(2, prediction.workspaceId());
            ps.setString(3, prediction.episodeId());
            ps.setString(4, prediction.subject());
            ps.setString(5, prediction.predictedValue());
            ps.setString(6, prediction.basis());
            ps.setString(7, prediction.basisVersion());
            ps.setDouble(8, prediction.confidence());
            ps.setInt(9, prediction.observedOnly() ? 1 : 0);
            ps.setString(10, String.join(",", prediction.caseIds()));
            ps.setString(11, prediction.madeAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new CognitiveConflictException(
                        CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                        "prediction " + prediction.id() + " already exists");
            }
            throw new IllegalStateException("failed to record the prediction", e);
        }
    }

    /** The stored content of a prediction, or null when it does not exist. */
    private List<String> predictionContent(String predictionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT workspace_id, episode_id, subject, predicted_value, basis, basis_version, "
                        + "confidence, observed_only, case_ids, made_at FROM cognitive_prediction "
                        + "WHERE id = ?")) {
            ps.setString(1, predictionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                List<String> content = new ArrayList<>();
                content.add(rs.getString(1));
                content.add(rs.getString(2));
                content.add(rs.getString(3));
                content.add(rs.getString(4));
                content.add(rs.getString(5));
                content.add(rs.getString(6));
                content.add(Double.toString(rs.getDouble(7)));
                content.add(rs.getInt(8) == 1 ? "true" : "false");
                content.add(rs.getString(9));
                content.add(rs.getString(10));
                return content;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the prediction " + predictionId, e);
        }
    }

    private static List<String> predictionContentOf(Prediction prediction) {
        List<String> content = new ArrayList<>();
        content.add(prediction.workspaceId());
        content.add(prediction.episodeId());
        content.add(prediction.subject());
        content.add(prediction.predictedValue());
        content.add(prediction.basis());
        content.add(prediction.basisVersion());
        content.add(Double.toString(prediction.confidence()));
        content.add(prediction.observedOnly() ? "true" : "false");
        content.add(String.join(",", prediction.caseIds()));
        content.add(prediction.madeAt());
        return content;
    }

    /** The predictions of a workspace, newest last. */
    public synchronized List<Prediction> predictions(String workspaceId) {
        List<Prediction> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, workspace_id, episode_id, subject, predicted_value, basis, "
                        + "basis_version, confidence, observed_only, case_ids, made_at "
                        + "FROM cognitive_prediction WHERE workspace_id = ? ORDER BY made_at, id")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Prediction(rs.getString("id"), rs.getString("workspace_id"),
                            rs.getString("episode_id"), rs.getString("subject"),
                            rs.getString("predicted_value"), rs.getString("basis"),
                            rs.getString("basis_version"), rs.getDouble("confidence"),
                            rs.getInt("observed_only") == 1,
                            splitList(rs.getString("case_ids")), rs.getString("made_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the predictions", e);
        }
        return out;
    }

    // ── errors ───────────────────────────────────────────────────────────────

    /**
     * Records a prediction error.
     *
     * <p>An error is an IMMUTABLE fact: it is the record that a prediction was falsified, and
     * it must survive whatever happens afterwards. Insert-only, with the same content check as
     * the other facts.</p>
     *
     * @param error the error
     */
    public synchronized void recordError(ErrorSignal error) {
        List<String> existing = errorContent(error.id());
        if (existing != null) {
            if (existing.equals(errorContentOf(error))) {
                return;
            }
            throw new CognitiveConflictException(
                    CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                    "error " + error.id() + " already exists with different content; an error of "
                            + "an episode is never rewritten");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_error(id, workspace_id, prediction_id, expected, "
                        + "observed, magnitude, disposition, responsible_model, "
                        + "responsible_version, case_ids, detected_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, error.id());
            ps.setString(2, error.workspaceId());
            ps.setString(3, error.predictionId());
            ps.setString(4, error.expected());
            ps.setString(5, error.observed());
            ps.setDouble(6, error.magnitude());
            ps.setString(7, error.disposition().name());
            ps.setString(8, error.responsibleModel());
            ps.setString(9, error.responsibleVersion());
            ps.setString(10, String.join(",", error.caseIds()));
            ps.setString(11, error.detectedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new CognitiveConflictException(
                        CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                        "error " + error.id() + " already exists");
            }
            throw new IllegalStateException("failed to record the error", e);
        }
    }

    /** The stored content of an error, or null when it does not exist. */
    private List<String> errorContent(String errorId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT workspace_id, prediction_id, expected, observed, magnitude, disposition, "
                        + "responsible_model, responsible_version, case_ids, detected_at "
                        + "FROM cognitive_error WHERE id = ?")) {
            ps.setString(1, errorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                List<String> content = new ArrayList<>();
                content.add(rs.getString(1));
                content.add(rs.getString(2));
                content.add(rs.getString(3));
                content.add(rs.getString(4));
                content.add(Double.toString(rs.getDouble(5)));
                content.add(rs.getString(6));
                content.add(rs.getString(7));
                content.add(rs.getString(8));
                content.add(rs.getString(9));
                content.add(rs.getString(10));
                return content;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the error " + errorId, e);
        }
    }

    private static List<String> errorContentOf(ErrorSignal error) {
        List<String> content = new ArrayList<>();
        content.add(error.workspaceId());
        content.add(error.predictionId());
        content.add(error.expected());
        content.add(error.observed());
        content.add(Double.toString(error.magnitude()));
        content.add(error.disposition().name());
        content.add(error.responsibleModel());
        content.add(error.responsibleVersion());
        content.add(String.join(",", error.caseIds()));
        content.add(error.detectedAt());
        return content;
    }

    /** The prediction errors of a workspace, newest last. */
    public synchronized List<ErrorSignal> errors(String workspaceId) {
        List<ErrorSignal> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, workspace_id, prediction_id, expected, observed, magnitude, disposition, "
                        + "responsible_model, responsible_version, case_ids, detected_at "
                        + "FROM cognitive_error WHERE workspace_id = ? ORDER BY detected_at, id")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ErrorSignal(rs.getString("id"), rs.getString("workspace_id"),
                            rs.getString("prediction_id"), rs.getString("expected"),
                            rs.getString("observed"), rs.getDouble("magnitude"),
                            ErrorSignal.Disposition.valueOf(rs.getString("disposition")),
                            rs.getString("responsible_model"), rs.getString("responsible_version"),
                            splitList(rs.getString("case_ids")), rs.getString("detected_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the errors", e);
        }
        return out;
    }

    // ── procedures ───────────────────────────────────────────────────────────

    /**
     * Records a promoted procedure VERSION.
     *
     * <p>A procedure is a versioned thing, and the version is already a field of the
     * {@link Procedure} record: the identity of a version is the pair (id, version). Writing the
     * same pair again is an idempotent no-op — the version that is stored keeps the measurement
     * it was promoted with; writing the same id with a NEW version adds a row and leaves the
     * predecessor in place, so a new version can never silently overwrite an older one.</p>
     *
     * <p>The content that makes a version a version is its DEFINITION: what it is, what family
     * it belongs to, when it applies, what it needs and what it falls back on, and the quality
     * it was validated at. The counters and the promotion instant are the MEASUREMENT of that
     * version, and they are not part of the definition: re-promoting the same version with a
     * later instant is the same version, not a new one.</p>
     *
     * @param workspaceId the workspace the procedure belongs to
     * @param procedure   the version
     */
    public synchronized void recordProcedure(String workspaceId, Procedure procedure) {
        List<String> existing = procedureContent(procedure.id(), procedure.version());
        if (existing != null) {
            if (existing.equals(procedureDefinitionOf(procedure))) {
                return;
            }
            throw new CognitiveConflictException(
                    CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                    "procedure " + procedure.id() + " version " + procedure.version()
                            + " already exists with a different definition; a version of a "
                            + "procedure is never rewritten");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_procedure(id, workspace_id, name, problem_family, "
                        + "version, applicability, required_facts, reference_algorithm_id, "
                        + "validation_cases, validation_agreement, use_count, failure_count, "
                        + "promoted_at, invalidated_at, validation_kind) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, procedure.id());
            ps.setString(2, workspaceId);
            ps.setString(3, procedure.name());
            ps.setString(4, procedure.problemFamily());
            ps.setString(5, procedure.version());
            ps.setString(6, procedure.applicability());
            ps.setString(7, String.join(",", procedure.requiredFacts()));
            ps.setString(8, procedure.referenceAlgorithmId());
            ps.setInt(9, procedure.validationCases());
            ps.setDouble(10, procedure.validationAgreement());
            ps.setInt(11, procedure.useCount());
            ps.setInt(12, procedure.failureCount());
            ps.setString(13, procedure.promotedAt());
            ps.setString(14, procedure.invalidatedAt());
            ps.setString(15, procedure.validationKind() == null
                    ? ValidationKind.IN_SAMPLE.name() : procedure.validationKind().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new CognitiveConflictException(
                        CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                        "procedure " + procedure.id() + " version " + procedure.version()
                                + " already exists");
            }
            throw new IllegalStateException("failed to record the procedure", e);
        }
    }

    /** The definition of a stored procedure version, or null when it does not exist. */
    private List<String> procedureContent(String procedureId, String version) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, problem_family, applicability, required_facts, "
                        + "reference_algorithm_id, validation_agreement, validation_kind "
                        + "FROM cognitive_procedure WHERE id = ? AND version = ?")) {
            ps.setString(1, procedureId);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                List<String> content = new ArrayList<>();
                content.add(rs.getString(1));
                content.add(rs.getString(2));
                content.add(rs.getString(3));
                content.add(rs.getString(4));
                content.add(rs.getString(5));
                content.add(Double.toString(rs.getDouble(6)));
                // A row that was written before the validation kind existed reads as the
                // consistency check it was, and the record says the same thing.
                String kind = rs.getString(7);
                content.add(kind == null ? ValidationKind.IN_SAMPLE.name() : kind);
                return content;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the procedure " + procedureId, e);
        }
    }

    private static List<String> procedureDefinitionOf(Procedure procedure) {
        List<String> content = new ArrayList<>();
        content.add(procedure.name());
        content.add(procedure.problemFamily());
        content.add(procedure.applicability());
        content.add(String.join(",", new java.util.TreeSet<>(procedure.requiredFacts())));
        content.add(procedure.referenceAlgorithmId());
        content.add(Double.toString(procedure.validationAgreement()));
        content.add(procedure.validationKind() == null
                ? ValidationKind.IN_SAMPLE.name() : procedure.validationKind().name());
        return content;
    }

    /**
     * The procedures of a workspace.
     *
     * <p>The versions of one procedure are ordered newest first, so that the caller that wants
     * the current version of a procedure sees it before its predecessors.</p>
     *
     * @param workspaceId the workspace
     * @return the procedure versions
     */
    public synchronized List<Procedure> procedures(String workspaceId) {
        List<Procedure> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, problem_family, version, applicability, required_facts, "
                        + "reference_algorithm_id, validation_cases, validation_agreement, "
                        + "use_count, failure_count, promoted_at, invalidated_at, validation_kind "
                        + "FROM cognitive_procedure WHERE workspace_id = ? "
                        + "ORDER BY id, promoted_at DESC, version DESC")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String kind = rs.getString(14);
                    out.add(new Procedure(rs.getString("id"), rs.getString("name"),
                            rs.getString("problem_family"), rs.getString("version"),
                            rs.getString("applicability"), splitSet(rs.getString("required_facts")),
                            rs.getString("reference_algorithm_id"),
                            rs.getInt("validation_cases"), rs.getDouble("validation_agreement"),
                            rs.getInt("use_count"), rs.getInt("failure_count"),
                            rs.getString("promoted_at"), rs.getString("invalidated_at"),
                            kind == null ? ValidationKind.IN_SAMPLE
                                    : ValidationKind.valueOf(kind)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the procedures", e);
        }
        return out;
    }

    // ── tasks ────────────────────────────────────────────────────────────────

    /**
     * Records an internal task.
     *
     * <p>A task is an IMMUTABLE fact of an episode: it is the declaration the system made of
     * its own next step, with its reason, its budget and its stop condition. It is insert-only,
     * with the same content check as the other facts.</p>
     *
     * @param task the task
     */
    public synchronized void recordTask(CognitiveTask task) {
        List<String> existing = taskContent(task.id());
        if (existing != null) {
            if (existing.equals(taskContentOf(task))) {
                return;
            }
            throw new CognitiveConflictException(
                    CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                    "task " + task.id() + " already exists with different content; a task of an "
                            + "episode is never rewritten");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_task(id, workspace_id, origin, reason, serves_goal, "
                        + "expected_benefit, cost_estimate, completion_condition, stop_condition, "
                        + "allowed_actions, budget_wall_clock_millis, status, created_at, "
                        + "completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, task.id());
            ps.setString(2, task.workspaceId());
            ps.setString(3, task.origin());
            ps.setString(4, task.reason());
            ps.setString(5, task.servesGoal());
            ps.setString(6, task.expectedBenefit());
            ps.setString(7, task.costEstimate());
            ps.setString(8, task.completionCondition());
            ps.setString(9, task.stopCondition());
            ps.setString(10, String.join(",", task.allowedActions()));
            ps.setLong(11, task.budgetWallClockMillis());
            ps.setString(12, task.status());
            ps.setString(13, task.createdAt());
            ps.setString(14, task.completedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new CognitiveConflictException(
                        CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                        "task " + task.id() + " already exists");
            }
            throw new IllegalStateException("failed to record the task", e);
        }
    }

    /** The stored content of a task, or null when it does not exist. */
    private List<String> taskContent(String taskId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT workspace_id, origin, reason, serves_goal, expected_benefit, "
                        + "cost_estimate, completion_condition, stop_condition, allowed_actions, "
                        + "budget_wall_clock_millis, status, created_at, completed_at "
                        + "FROM cognitive_task WHERE id = ?")) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                List<String> content = new ArrayList<>();
                content.add(rs.getString(1));
                content.add(rs.getString(2));
                content.add(rs.getString(3));
                content.add(rs.getString(4));
                content.add(rs.getString(5));
                content.add(rs.getString(6));
                content.add(rs.getString(7));
                content.add(rs.getString(8));
                content.add(rs.getString(9));
                content.add(Long.toString(rs.getLong(10)));
                content.add(rs.getString(11));
                content.add(rs.getString(12));
                content.add(rs.getString(13));
                return content;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the task " + taskId, e);
        }
    }

    private static List<String> taskContentOf(CognitiveTask task) {
        List<String> content = new ArrayList<>();
        content.add(task.workspaceId());
        content.add(task.origin());
        content.add(task.reason());
        content.add(task.servesGoal());
        content.add(task.expectedBenefit());
        content.add(task.costEstimate());
        content.add(task.completionCondition());
        content.add(task.stopCondition());
        content.add(String.join(",", task.allowedActions()));
        content.add(Long.toString(task.budgetWallClockMillis()));
        content.add(task.status());
        content.add(task.createdAt());
        content.add(task.completedAt());
        return content;
    }

    /** The internal tasks of a workspace, newest last. */
    public synchronized List<CognitiveTask> tasks(String workspaceId) {
        List<CognitiveTask> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, workspace_id, origin, reason, serves_goal, expected_benefit, "
                        + "cost_estimate, completion_condition, stop_condition, allowed_actions, "
                        + "budget_wall_clock_millis, status, created_at, completed_at "
                        + "FROM cognitive_task WHERE workspace_id = ? ORDER BY created_at, id")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CognitiveTask(rs.getString("id"), rs.getString("workspace_id"),
                            rs.getString("origin"), rs.getString("reason"),
                            rs.getString("serves_goal"), rs.getString("expected_benefit"),
                            rs.getString("cost_estimate"), rs.getString("completion_condition"),
                            rs.getString("stop_condition"),
                            splitList(rs.getString("allowed_actions")),
                            rs.getLong("budget_wall_clock_millis"), rs.getString("status"),
                            rs.getString("created_at"), rs.getString("completed_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the tasks", e);
        }
        return out;
    }

    // ── events ───────────────────────────────────────────────────────────────

    /**
     * Appends one event to an episode's stream.
     *
     * <p>An event is IMMUTABLE: it is a fact about a moment of one execution. The event id is
     * unique, and so is the position {@code (episode_id, sequence)} inside the episode — two
     * events may not occupy the same position, because the trace is an ordered account and a
     * position that holds two events is an account of nothing.</p>
     *
     * <p>Writing the same event again — the same id at the same position with the same content —
     * is an idempotent no-op. Writing an event that would occupy a position that is already
     * taken by a different event, or reusing an id for different content, is a conflict: the
     * stream is never rewritten.</p>
     *
     * @param eventId    the id of the event
     * @param episodeId  the episode the event belongs to
     * @param sequence   the position of the event inside the episode
     * @param type       the phase the event records
     * @param actor      what produced the event
     * @param detail     what happened
     * @param occurredAt the instant the event happened
     */
    public synchronized void appendEvent(String eventId, String episodeId, long sequence,
                                         String type, String actor, String detail,
                                         String occurredAt) {
        List<String> existing = eventContent(eventId);
        if (existing == null) {
            existing = eventContentAt(episodeId, sequence);
        }
        if (existing != null) {
            if (existing.equals(eventContentOf(episodeId, sequence, type, actor, detail,
                    occurredAt))) {
                return;
            }
            throw new CognitiveConflictException(
                    CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                    "event " + eventId + " conflicts with the event already recorded at that "
                            + "position of episode " + episodeId + "; an event stream is never "
                            + "rewritten");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_event(id, episode_id, sequence, type, actor, "
                        + "detail, occurred_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, episodeId);
            ps.setLong(3, sequence);
            ps.setString(4, type);
            ps.setString(5, actor);
            ps.setString(6, detail);
            ps.setString(7, occurredAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new CognitiveConflictException(
                        CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                        "event " + eventId + " conflicts with an event that is already recorded");
            }
            throw new IllegalStateException("failed to append the event", e);
        }
    }

    /** The stored content of an event, or null when it does not exist. */
    private List<String> eventContent(String eventId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT episode_id, sequence, type, actor, detail, occurred_at "
                        + "FROM cognitive_event WHERE id = ?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return eventContentFrom(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the event " + eventId, e);
        }
    }

    /** The stored content of the event at a position, or null when there is none. */
    private List<String> eventContentAt(String episodeId, long sequence) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT episode_id, sequence, type, actor, detail, occurred_at "
                        + "FROM cognitive_event WHERE episode_id = ? AND sequence = ?")) {
            ps.setString(1, episodeId);
            ps.setLong(2, sequence);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return eventContentFrom(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the event at " + episodeId + "#"
                    + sequence, e);
        }
    }

    private static List<String> eventContentFrom(ResultSet rs) throws SQLException {
        List<String> content = new ArrayList<>();
        content.add(rs.getString(1));
        content.add(Long.toString(rs.getLong(2)));
        content.add(rs.getString(3));
        content.add(rs.getString(4));
        content.add(rs.getString(5));
        content.add(rs.getString(6));
        return content;
    }

    private static List<String> eventContentOf(String episodeId, long sequence, String type,
                                               String actor, String detail, String occurredAt) {
        List<String> content = new ArrayList<>();
        content.add(episodeId);
        content.add(Long.toString(sequence));
        content.add(type);
        content.add(actor);
        content.add(detail);
        content.add(occurredAt);
        return content;
    }

    /** The events of an episode, in order. */
    public synchronized List<LearningTrace.Step> events(String episodeId) {
        List<LearningTrace.Step> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT type, actor, detail FROM cognitive_event WHERE episode_id = ? "
                        + "ORDER BY sequence, id")) {
            ps.setString(1, episodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LearningTrace.Step(rs.getString("type"), rs.getString("actor"),
                            rs.getString("detail")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the events", e);
        }
        return out;
    }

    // ── idempotency ──────────────────────────────────────────────────────────

    /**
     * One row of the idempotency registry.
     *
     * @param key         the idempotency key
     * @param fingerprint the digest of the payload the key was used with
     * @param episodeId   the episode the request produced
     * @param signalId    the signal the request received
     * @param state       {@code IN_PROGRESS} or {@code COMPLETED}
     * @param outcome     the durable form of the outcome
     * @param createdAt   the instant the key was claimed
     * @param completedAt the instant the outcome was written
     */
    public record IdempotencyRecord(
            String key,
            String fingerprint,
            String episodeId,
            String signalId,
            String state,
            String outcome,
            String createdAt,
            String completedAt) {

        /** The state of a key whose delivery is still running. */
        public static final String STATE_IN_PROGRESS = "IN_PROGRESS";

        /** The state of a key whose delivery has finished. */
        public static final String STATE_COMPLETED = "COMPLETED";

        /** True when the delivery behind this key has finished. */
        public boolean completed() {
            return STATE_COMPLETED.equals(state) && outcome != null;
        }
    }

    /**
     * Claims an idempotency key.
     *
     * <p>The claim is an INSERT, and it is the only atomic step of the registry: the caller
     * that wins the insert owns the key and does the work; every other caller finds the key
     * claimed and does no work at all.</p>
     *
     * @param key         the idempotency key
     * @param fingerprint the digest of the payload
     * @param createdAt   the instant of the claim
     * @return true when the caller claimed the key, false when it was already claimed
     */
    public synchronized boolean claimIdempotency(String key, String fingerprint, String createdAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO cognitive_idempotency(key, fingerprint, episode_id, "
                        + "signal_id, state, outcome, created_at, completed_at) "
                        + "VALUES (?,?,NULL,NULL,?,NULL,?,NULL)")) {
            ps.setString(1, key);
            ps.setString(2, fingerprint);
            ps.setString(3, IdempotencyRecord.STATE_IN_PROGRESS);
            ps.setString(4, createdAt);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to claim the idempotency key", e);
        }
    }

    /**
     * The row of an idempotency key, when there is one.
     *
     * @param key the idempotency key
     * @return the row, or empty
     */
    public synchronized java.util.Optional<IdempotencyRecord> findIdempotency(String key) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT key, fingerprint, episode_id, signal_id, state, outcome, created_at, "
                        + "completed_at FROM cognitive_idempotency WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new IdempotencyRecord(rs.getString(1),
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the idempotency key", e);
        }
    }

    /**
     * Writes the outcome of a delivery under its idempotency key.
     *
     * @param key         the idempotency key
     * @param episodeId   the episode the delivery produced
     * @param signalId    the signal the delivery received
     * @param outcome     the durable form of the outcome
     * @param completedAt the instant the delivery finished
     */
    public synchronized void completeIdempotency(String key, String episodeId, String signalId,
                                                 String outcome, String completedAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE cognitive_idempotency SET episode_id = ?, signal_id = ?, state = ?, "
                        + "outcome = ?, completed_at = ? WHERE key = ?")) {
            ps.setString(1, episodeId);
            ps.setString(2, signalId);
            ps.setString(3, IdempotencyRecord.STATE_COMPLETED);
            ps.setString(4, outcome);
            ps.setString(5, completedAt);
            ps.setString(6, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to complete the idempotency key", e);
        }
    }

    /**
     * Releases a claimed key whose delivery failed, so that the key can be used again.
     *
     * @param key the idempotency key
     */
    public synchronized void releaseIdempotency(String key) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM cognitive_idempotency WHERE key = ? AND state <> ?")) {
            ps.setString(1, key);
            ps.setString(2, IdempotencyRecord.STATE_COMPLETED);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to release the idempotency key", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** True when the failure is a constraint violation, and not a broken statement. */
    private static boolean isConstraintViolation(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String text = message.toLowerCase(Locale.ROOT);
        return text.contains("constraint") || text.contains("unique");
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private static java.util.Set<String> splitSet(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Set.copyOf(List.of(value.split(",")));
    }

    // ── the durable regulatory state (W45.0) ─────────────────────────────────

    /**
     * W45.0 — one recorded regulatory event: what moved, and by how much.
     *
     * @param id          the unique identifier of the event; a replay of the same event
     *                    carries the same id and is therefore detected
     * @param workspaceId the workspace
     * @param episodeId   the episode the event belongs to
     * @param kind        the kind of the event
     * @param magnitude   how strong it was
     * @param source      where it came from
     * @param appliedAt   when it was applied
     */
    public record RegulatoryEventRecord(String id, String workspaceId, String episodeId,
                                         String kind, double magnitude, String source,
                                         String appliedAt) {
        public RegulatoryEventRecord {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("a regulatory event needs an id");
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalArgumentException("a regulatory event needs a workspace");
            }
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("a regulatory event needs a kind");
            }
        }
    }

    /**
     * W45.0 — persists the regulatory state and the provenance of the change, in ONE
     * transaction.
     *
     * <p>The state and its events are written together or not at all: a state without the
     * events that produced it is a number nobody can explain, and events without the state
     * are a history of changes that did not happen.</p>
     *
     * <p><b>The replay rule.</b> An event id that already exists means the same event is
     * being applied twice — after a crash, or by a caller that retried. The transaction is
     * then REFUSED and nothing is written: a drive must not move twice for one event.</p>
     *
     * <p>The version is allocated INSIDE the transaction: the caller does not say which
     * version it is writing, so two concurrent writers cannot both claim the same one.</p>
     *
     * @param workspaceId the workspace
     * @param state       the new state
     * @param recordedAt  when the state was recorded
     * @param events      the events that produced it
     * @return the version the state was written at
     */
    public synchronized long recordRegulatoryState(String workspaceId,
                                                   io.matrix.cognitive.neuromodulation.RegulatoryState state,
                                                   String recordedAt,
                                                   List<RegulatoryEventRecord> events) {
        try {
            connection.setAutoCommit(false);
            try {
                for (RegulatoryEventRecord event
                        : events == null ? List.<RegulatoryEventRecord>of() : events) {
                    if (regulatoryEventExists(event.id())) {
                        connection.rollback();
                        throw new CognitiveConflictException(
                                CognitiveConflictException.Kind.IMMUTABLE_RECORD,
                                "the regulatory event " + event.id() + " has already been applied; "
                                        + "a drive must not move twice for one event");
                    }
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO cognitive_regulatory_event(id, workspace_id, episode_id, "
                                    + "kind, magnitude, source, applied_at) VALUES (?,?,?,?,?,?,?)")) {
                        ps.setString(1, event.id());
                        ps.setString(2, event.workspaceId());
                        ps.setString(3, event.episodeId());
                        ps.setString(4, event.kind());
                        ps.setDouble(5, event.magnitude());
                        ps.setString(6, event.source());
                        ps.setString(7, event.appliedAt());
                        ps.executeUpdate();
                    }
                }
                long version = regulatoryVersion(workspaceId) + 1;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO cognitive_regulatory_state(workspace_id, state, version, "
                                + "recorded_at) VALUES (?,?,?,?) "
                                + "ON CONFLICT(workspace_id) DO UPDATE SET "
                                + "state = excluded.state, version = excluded.version, "
                                + "recorded_at = excluded.recorded_at")) {
                    ps.setString(1, workspaceId);
                    ps.setString(2, encodeRegulatoryState(state));
                    ps.setLong(3, version);
                    ps.setString(4, recordedAt);
                    ps.executeUpdate();
                }
                connection.commit();
                return version;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the regulatory state", e);
        }
    }

    /**
     * W45.0 — the persisted regulatory state of a workspace, or null when there is none.
     *
     * @param workspaceId the workspace
     * @return the state, or null
     */
    public synchronized io.matrix.cognitive.neuromodulation.RegulatoryState regulatoryState(
            String workspaceId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT state FROM cognitive_regulatory_state WHERE workspace_id = ?")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return decodeRegulatoryState(rs.getString("state"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the regulatory state", e);
        }
        return null;
    }

    /**
     * W45.0 — the version of the persisted regulatory state.
     *
     * @param workspaceId the workspace
     * @return the version, or zero when nothing is persisted
     */
    public synchronized long regulatoryVersion(String workspaceId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT version FROM cognitive_regulatory_state WHERE workspace_id = ?")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("version");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the regulatory state version", e);
        }
        return 0L;
    }

    /**
     * W45.0 — the provenance of the regulatory changes: every event that moved a drive.
     *
     * @param workspaceId the workspace
     * @return the events, oldest first
     */
    public synchronized List<RegulatoryEventRecord> regulatoryEvents(String workspaceId) {
        List<RegulatoryEventRecord> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, workspace_id, episode_id, kind, magnitude, source, applied_at "
                        + "FROM cognitive_regulatory_event WHERE workspace_id = ? "
                        + "ORDER BY applied_at, id")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new RegulatoryEventRecord(rs.getString("id"), rs.getString("workspace_id"),
                            rs.getString("episode_id"), rs.getString("kind"),
                            rs.getDouble("magnitude"), rs.getString("source"),
                            rs.getString("applied_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the regulatory events", e);
        }
        return out;
    }

    private boolean regulatoryEventExists(String id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM cognitive_regulatory_event WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * W45.0 — the state as one readable line per drive.
     *
     * <p>The format is deliberately plain: dimension, level, source, effect and the instant,
     * separated by tabs, one drive per line. A reader can open the database file and see what
     * the system believed without needing the application.</p>
     */
    private String encodeRegulatoryState(
            io.matrix.cognitive.neuromodulation.RegulatoryState state) {
        StringBuilder sb = new StringBuilder();
        if (state != null) {
            for (io.matrix.cognitive.DriveSignal drive : state.drives().values()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(drive.dimension().name()).append('\t')
                        .append(drive.level()).append('\t')
                        .append(drive.source() == null ? "" : drive.source()).append('\t')
                        .append(drive.effect() == null ? "" : drive.effect().name()).append('\t')
                        .append(drive.updatedAt() == null ? "" : drive.updatedAt());
            }
        }
        return sb.toString();
    }

    private io.matrix.cognitive.neuromodulation.RegulatoryState decodeRegulatoryState(String encoded) {
        java.util.Map<io.matrix.cognitive.DriveSignal.Dimension, io.matrix.cognitive.DriveSignal>
                drives = new java.util.EnumMap<>(io.matrix.cognitive.DriveSignal.Dimension.class);
        if (encoded != null && !encoded.isBlank()) {
            for (String line : encoded.split("\n")) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 5) {
                    continue;
                }
                io.matrix.cognitive.DriveSignal.Dimension dimension =
                        io.matrix.cognitive.DriveSignal.Dimension.valueOf(parts[0]);
                double level = Double.parseDouble(parts[1]);
                io.matrix.cognitive.DriveSignal.Effect effect = parts[3].isBlank()
                        ? null : io.matrix.cognitive.DriveSignal.Effect.valueOf(parts[3]);
                drives.put(dimension, new io.matrix.cognitive.DriveSignal(dimension, level,
                        parts[2], effect, parts[4]));
            }
        }
        return new io.matrix.cognitive.neuromodulation.RegulatoryState(drives);
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close the cognitive store", e);
        }
    }
}
