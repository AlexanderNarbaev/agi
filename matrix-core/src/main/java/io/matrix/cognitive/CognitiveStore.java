package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.Procedure;

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
 */
public final class CognitiveStore implements AutoCloseable {

    /** The schema version this build writes. */
    public static final int SCHEMA_VERSION = 1;

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
                    CREATE TABLE IF NOT EXISTS cognitive_procedure (
                        id TEXT PRIMARY KEY,
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
                        invalidated_at TEXT
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
            st.execute("CREATE INDEX IF NOT EXISTS idx_cognitive_event_episode "
                    + "ON cognitive_event(episode_id, sequence)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_cognitive_prediction_workspace "
                    + "ON cognitive_prediction(workspace_id, made_at)");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO cognitive_schema_meta(key, value) VALUES('version', ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, Integer.toString(SCHEMA_VERSION));
            ps.executeUpdate();
        }
    }

    // ── episodes ─────────────────────────────────────────────────────────────

    /** Records the start of an episode. */
    public void startEpisode(String episodeId, String workspaceId, String summary, String startedAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cognitive_episode(id, workspace_id, summary, started_at, "
                        + "closed_at) VALUES (?,?,?,?,NULL)")) {
            ps.setString(1, episodeId);
            ps.setString(2, workspaceId);
            ps.setString(3, summary);
            ps.setString(4, startedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the episode", e);
        }
    }

    /** Records the end of an episode. */
    public void closeEpisode(String episodeId, String closedAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE cognitive_episode SET closed_at = ? WHERE id = ?")) {
            ps.setString(1, closedAt);
            ps.setString(2, episodeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close the episode", e);
        }
    }

    /** The episodes of a workspace, newest last. */
    public List<String> episodes(String workspaceId) {
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

    /** Records a prediction. */
    public void recordPrediction(Prediction prediction) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cognitive_prediction(id, workspace_id, episode_id, subject, "
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
            throw new IllegalStateException("failed to record the prediction", e);
        }
    }

    /** The predictions of a workspace, newest last. */
    public List<Prediction> predictions(String workspaceId) {
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

    /** Records a prediction error. */
    public void recordError(ErrorSignal error) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cognitive_error(id, workspace_id, prediction_id, expected, "
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
            throw new IllegalStateException("failed to record the error", e);
        }
    }

    /** The prediction errors of a workspace, newest last. */
    public List<ErrorSignal> errors(String workspaceId) {
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

    /** Records or replaces a promoted procedure. */
    public void recordProcedure(String workspaceId, Procedure procedure) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cognitive_procedure(id, workspace_id, name, problem_family, "
                        + "version, applicability, required_facts, reference_algorithm_id, "
                        + "validation_cases, validation_agreement, use_count, failure_count, "
                        + "promoted_at, invalidated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to record the procedure", e);
        }
    }

    /** The procedures of a workspace. */
    public List<Procedure> procedures(String workspaceId) {
        List<Procedure> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, problem_family, version, applicability, required_facts, "
                        + "reference_algorithm_id, validation_cases, validation_agreement, "
                        + "use_count, failure_count, promoted_at, invalidated_at "
                        + "FROM cognitive_procedure WHERE workspace_id = ? ORDER BY id")) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Procedure(rs.getString("id"), rs.getString("name"),
                            rs.getString("problem_family"), rs.getString("version"),
                            rs.getString("applicability"), splitSet(rs.getString("required_facts")),
                            rs.getString("reference_algorithm_id"),
                            rs.getInt("validation_cases"), rs.getDouble("validation_agreement"),
                            rs.getInt("use_count"), rs.getInt("failure_count"),
                            rs.getString("promoted_at"), rs.getString("invalidated_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read the procedures", e);
        }
        return out;
    }

    // ── tasks ────────────────────────────────────────────────────────────────

    /** Records an internal task. */
    public void recordTask(CognitiveTask task) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cognitive_task(id, workspace_id, origin, reason, serves_goal, "
                        + "expected_benefit, cost_estimate, completion_condition, stop_condition, "
                        + "allowed_actions, budget_wall_clock_millis, status, created_at, completed_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
            throw new IllegalStateException("failed to record the task", e);
        }
    }

    /** The internal tasks of a workspace. */
    public List<CognitiveTask> tasks(String workspaceId) {
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
                            rs.getString("stop_condition"), splitList(rs.getString("allowed_actions")),
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

    /** Appends one event to an episode's stream. */
    public void appendEvent(String eventId, String episodeId, long sequence, String type,
                            String actor, String detail, String occurredAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cognitive_event(id, episode_id, sequence, type, actor, "
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
            throw new IllegalStateException("failed to append the event", e);
        }
    }

    /** The events of an episode, in order. */
    public List<LearningTrace.Step> events(String episodeId) {
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

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close the cognitive store", e);
        }
    }
}
