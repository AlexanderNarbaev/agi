package io.matrix.api.bridge;

import io.matrix.contract.bridge.CognitiveTaskRequest;

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
import java.util.Optional;

/**
 * W53 — chain B's durable inbox for remote cognitive tasks.
 *
 * <p><b>Why this class exists.</b> W52's receiver deduplicated in memory
 * ({@code InMemoryBridgeIdempotency}), and the W52 report named the consequence: a restart
 * of chain B between two deliveries of the same request id can apply the effect twice. For a
 * message that carries a proposition, that is a duplicate memory entry. For a message that
 * asks a peer to <b>execute a cognitive path</b>, it is a second execution of the same
 * task — and the mandate is explicit that the idempotency decision must not depend solely
 * on an in-memory map.</p>
 *
 * <p><b>What it owns.</b> Chain B's own database, written by chain B alone: the accepted
 * request, its payload hash, its state, its result and the reference to its trace. Nothing
 * here is written by chain A, and nothing here is written by any process but the one that
 * owns it. There is no cross-database transaction and none is pretended: the guarantee is
 * at-least-once delivery with effectively-once logical effects.</p>
 *
 * <p><b>The rules it enforces.</b></p>
 * <ul>
 *   <li>the first valid payload for a request id is accepted;</li>
 *   <li>a duplicate with the SAME payload hash creates no second effect: it replays the
 *       stored status, and the stored result when the task is already complete;</li>
 *   <li>the same request id with a DIFFERENT payload hash is refused as a conflict — the
 *       caller is asking for two different things under one identity;</li>
 *   <li>a request found in a non-terminal state after a restart is visible to the
 *       reconciliation pass, and is neither silently re-run nor silently failed: the
 *       pass closes it as a reasoned failure, because the inbox keeps the request's
 *       digest and not its payload and therefore cannot finish the execution.</li>
 * </ul>
 *
 * <p><b>The effect ledger.</b> The row carries an explicit {@code effect_committed} flag and
 * the reference to the trace. That is what makes the crash boundary between "the cognitive
 * path ran" and "the effect was committed" recoverable: after a restart, the reconciler
 * asks this store whether the effect was committed, and the answer is in the row rather
 * than in the reconciler's memory.</p>
 *
 * <p><b>One writer, one lock.</b> The peer serves concurrent deliveries, and this store
 * holds ONE connection: a JDBC connection is not thread-safe, and two statements on it at
 * once may interleave. Every method that touches the connection therefore synchronizes on
 * the store, exactly as the cognitive store of chain A does. The lock costs nothing that
 * SQLite's own write lock did not already cost: only one writer at a time may commit
 * anyway, and the lock replaces a busy wait with a wait.</p>
 */
public final class RemoteTaskStore implements AutoCloseable {

    /** The state of a request chain B has accepted. */
    public enum State {
        /** The request was accepted and has not started yet. */
        ACCEPTED,
        /** The cognitive path is running. */
        RUNNING,
        /** The task produced a result. */
        COMPLETED,
        /** The task was refused by policy. */
        REFUSED,
        /** The task was not served: the capability does not exist or does not serve it. */
        UNSUPPORTED,
        /** The execution failed. */
        FAILED,
        /** The deadline passed. */
        TIMED_OUT,
        /** The request was cancelled. */
        CANCELLED;

        /** True when the state is terminal — nothing more will happen to the request. */
        public boolean terminal() {
            return this != ACCEPTED && this != RUNNING;
        }
    }

    /** What the store decided about an incoming request. */
    public enum Decision {
        /** The request is new and was accepted. */
        ACCEPTED,
        /** The request was already accepted and is still running. */
        DUPLICATE_IN_PROGRESS,
        /** The request was already completed; the stored result is to be replayed. */
        DUPLICATE_COMPLETED,
        /**
         * The request was already handled and ended in a terminal state — refused,
         * unsupported, failed, timed out or cancelled; its stored result is to be
         * replayed.
         */
        DUPLICATE_TERMINAL,
        /** The request id is already bound to a different payload. */
        CONFLICT,
        /** The inbox holds as much unfinished work as it is allowed to hold. */
        INBOX_FULL
    }

    /** One row of the inbox. */
    public record Row(
            String requestId,
            String taskId,
            String payloadDigest,
            String payloadHash,
            String capabilityId,
            String capabilityVersion,
            State state,
            String resultJson,
            String traceReference,
            String refusalReason,
            boolean effectCommitted,
            String createdAt,
            String updatedAt,
            String stageTimingsJson) {
    }

    /**
     * The reason a task that was unfinished when the peer restarted carries.
     *
     * <p>It says what is known and what is not: the task was in flight, the peer died,
     * the request itself was not kept, so the execution cannot be finished — and the
     * outcome of the work that was under way is unknown.</p>
     */
    public static final String RESTART_REASON =
            "the peer restarted while this task was unfinished; the request was not kept, so "
                    + "the execution could not be finished and its outcome is unknown";

    /** The name of the table the inbox lives in. */
    public static final String TABLE = "remote_task";

    /**
     * W53 — where chain B keeps its task inbox.
     *
     * <p>It is chain B's own file, under chain B's own data directory, and chain B's
     * own single writer opens it. No other process opens it, and no other process may.</p>
     */
    public static final String DEFAULT_DATABASE = "data/mind/tasks.sqlite";

    /**
     * The environment variable that says where chain B keeps its mind.
     *
     * <p>The task inbox lives beside the mind, in the same directory, so that one
     * variable moves both of chain B's own files together and no other process has to
     * be told about either.</p>
     */
    public static final String MIND_DIR_ENV = "MATRIX_MIND_DIR";

    /**
     * The path of the inbox, honouring the mind's own directory.
     *
     * @return the path of the inbox file
     */
    public static java.nio.file.Path defaultDatabase() {
        String directory = System.getenv(MIND_DIR_ENV);
        if (directory == null || directory.isBlank()) {
            return java.nio.file.Path.of(DEFAULT_DATABASE);
        }
        return java.nio.file.Path.of(directory).resolve("tasks.sqlite");
    }

    /** How long a connection waits for another one to release the file. */
    public static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private final Connection connection;

    /**
     * Opens (or creates) chain B's task inbox.
     *
     * @param database the file chain B owns for this purpose
     * @throws SQLException when the file cannot be opened or the schema cannot be created
     */
    /**
     * The default bound of the durable inbox.
     *
     * <p><b>Why the inbox has a bound.</b> A durable inbox that accepts everything is
     * an unbounded queue with a database behind it: the peer would accept work it cannot
     * finish, and the caller's deadline would expire while the work waited. The bound is
     * on the <i>unfinished</i> rows — the work in flight — because that is the pressure;
     * the finished rows are history, and history is the retention policy's business, not
     * this one's.</p>
     */
    public static final int DEFAULT_MAX_ENTRIES = 512;

    /** How many unfinished requests the inbox holds at most. */
    private final int maxEntries;

    /** The reason a request is refused when the inbox is full. */
    public static final String INBOX_FULL_REASON = "the task inbox is full";

    /**
     * The inbox.
     *
     * @param database the database file
     * @throws SQLException when the inbox cannot be opened
     */
    public RemoteTaskStore(Path database) throws SQLException {
        this(database, DEFAULT_MAX_ENTRIES);
    }

    /**
     * The inbox, with its own bound.
     *
     * @param database   the database file
     * @param maxEntries how many unfinished requests the inbox holds at most
     * @throws SQLException when the inbox cannot be opened
     */
    public RemoteTaskStore(Path database, int maxEntries) throws SQLException {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("the inbox bound must be positive");
        }
        this.maxEntries = maxEntries;
        if (database == null) {
            throw new IllegalArgumentException("the task inbox needs a database file");
        }
        try {
            Path parent = database.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException e) {
            throw new SQLException("the task inbox directory could not be created: "
                    + e.getMessage(), e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        this.connection.setAutoCommit(true);
        // W53 — the same discipline the cognitive store uses, and for the same reason:
        // a WAL journal so that a reader is never blocked by the writer, FULL
        // synchronisation so that a committed request is really on disk when the process
        // dies, and a busy timeout so that two connections to the same file wait for
        // each other instead of failing at once.
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
            st.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MILLIS);
        }
        createSchema();
    }

    /** Creates the inbox's table when it is not there yet. */
    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS remote_task (
                        request_id TEXT PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        payload_digest TEXT NOT NULL,
                        payload_hash TEXT NOT NULL,
                        capability_id TEXT NOT NULL,
                        capability_version TEXT,
                        state TEXT NOT NULL,
                        result_json TEXT,
                        trace_reference TEXT,
                        refusal_reason TEXT,
                        effect_committed INTEGER NOT NULL DEFAULT 0,
                        cancel_requested_at TEXT,
                        stage_timings_json TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )""");
            // W53 — the column arrives after the first version of the inbox did, so an
            // inbox written by an earlier build is migrated rather than refused.
            addColumnIfMissing("cancel_requested_at", "TEXT");
            // W53 — the peer's own measurement of its own stages. It lives in the peer's
            // row, not on the wire: the answer contract does not change, and the reader
            // of the performance report reads the peer's own file.
            addColumnIfMissing("stage_timings_json", "TEXT");
        }
    }

    /**
     * Adds a column to an inbox that was written before the column existed.
     *
     * @param column the column
     * @param type   the column's type
     * @throws SQLException when the inbox cannot be migrated
     */
    private void addColumnIfMissing(String column, String type) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(remote_task)")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE remote_task ADD COLUMN " + column + " " + type);
        }
    }

    /**
     * The decision about an incoming request, and the row that carries it.
     *
     * @param decision what the store decided
     * @param row      the row, when there is one
     */
    public record Admission(Decision decision, Row row) {

        /** True when the request may be executed. */
        public boolean executable() {
            return decision == Decision.ACCEPTED;
        }
    }

    /**
     * Decides what to do with an incoming request, and records it when it is new.
     *
     * <p>The check and the write are one transaction of the inbox: a concurrent delivery of
     * the same request id finds the row the first delivery wrote.</p>
     *
     * @param request the request
     * @param now     the instant of the admission
     * @return the decision and the row
     * @throws SQLException when the inbox cannot be read or written
     */
    public synchronized Admission admit(CognitiveTaskRequest request, String payloadDigest, String now)
            throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("there is no request to admit");
        }
        Optional<Row> existing = find(request.requestId());
        if (existing.isPresent()) {
            Row row = existing.get();
            if (!row.payloadHash().equals(request.payloadHash())
                    || !row.payloadDigest().equals(payloadDigest)) {
                // The same identity, a different task. Refusing is the only safe answer:
                // executing either one would make the caller's trace a lie.
                return new Admission(Decision.CONFLICT, row);
            }
            if (row.state() == State.COMPLETED) {
                return new Admission(Decision.DUPLICATE_COMPLETED, row);
            }
            // A terminal row — refused, failed, timed out — is replayed too: the work is
            // over, and answering it as "still running" would make the caller wait for
            // something that will never happen.
            return new Admission(row.state().terminal()
                    ? Decision.DUPLICATE_TERMINAL : Decision.DUPLICATE_IN_PROGRESS, row);
        }
        if (unfinished().size() >= maxEntries) {
            // The inbox is full. The request is refused, not queued: a queue that grows
            // without bound is not a queue, it is a delay the caller cannot see. The
            // refusal is still written down — a terminal row does not occupy the bound,
            // and the caller must be able to read why its task was not taken.
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO remote_task (request_id, task_id, payload_digest, payload_hash, "
                            + "capability_id, capability_version, state, result_json, "
                            + "trace_reference, refusal_reason, effect_committed, created_at, "
                            + "updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, request.requestId());
                ps.setString(2, request.taskId());
                ps.setString(3, payloadDigest);
                ps.setString(4, request.payloadHash());
                ps.setString(5, request.requiredCapability());
                ps.setString(6, request.capabilityVersion());
                ps.setString(7, State.REFUSED.name());
                ps.setString(8, null);
                ps.setString(9, null);
                ps.setString(10, INBOX_FULL_REASON);
                ps.setInt(11, 0);
                ps.setString(12, now);
                ps.setString(13, now);
                ps.executeUpdate();
            }
            return new Admission(Decision.INBOX_FULL, find(request.requestId()).orElse(null));
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO remote_task (request_id, task_id, payload_digest, payload_hash, "
                        + "capability_id, capability_version, state, result_json, "
                        + "trace_reference, refusal_reason, effect_committed, created_at, "
                        + "updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, request.requestId());
            ps.setString(2, request.taskId());
            ps.setString(3, payloadDigest);
            ps.setString(4, request.payloadHash());
            ps.setString(5, request.requiredCapability());
            ps.setString(6, request.capabilityVersion());
            ps.setString(7, State.ACCEPTED.name());
            ps.setString(8, null);
            ps.setString(9, null);
            ps.setString(10, null);
            ps.setInt(11, 0);
            ps.setString(12, now);
            ps.setString(13, now);
            ps.executeUpdate();
        }
        return new Admission(Decision.ACCEPTED, find(request.requestId()).orElse(null));
    }

    /**
     * The rows that are still unfinished after a restart.
     *
     * <p><b>Why the peer reconciles its own rows.</b> A request that was in flight when
     * the process died would otherwise stay {@code ACCEPTED} or {@code RUNNING} for ever:
     * every later delivery of it would be answered as "still running", and the caller
     * would wait for a task nobody is doing. The peer keeps the request's <i>digest</i>,
     * not its payload, so it cannot finish the execution itself — and re-running it
     * would risk a second effect for a task whose first effect may have landed. So the
     * row is closed as a reasoned failure, and the caller learns what happened instead
     * of waiting for a silence.</p>
     *
     * @param now the instant of the reconciliation
     * @return how many rows were closed
     * @throws SQLException when the inbox cannot be read or written
     */
    public synchronized int reconcileUnfinished(String now) throws SQLException {
        int closed = 0;
        for (Row row : unfinished()) {
            update(row.requestId(), "state = ?", State.FAILED.name(), now);
            update(row.requestId(), "refusal_reason = ?", RESTART_REASON, now);
            closed++;
        }
        return closed;
    }

    /**
     * Withdraws a task the peer has not finished.
     *
     * <p><b>What the peer may cancel, and what it may not.</b> A task that has not
     * started is withdrawn outright: the state becomes {@code CANCELLED} and the task is
     * never executed. A task that is already running is not interrupted — the brain
     * cannot be interrupted — but the withdrawal is <i>recorded</i>, and when the
     * execution returns the row says what happened. A task that has already finished is
     * left alone: a cancellation cannot undo a committed effect, and pretending
     * otherwise would hide a fact the caller needs.</p>
     *
     * @param requestId the request to withdraw
     * @param now       the instant of the withdrawal
     * @return the state the row is in afterwards, or empty when the peer holds no such
     *         request
     * @throws SQLException when the inbox cannot be written
     */
    public synchronized Optional<State> cancel(String requestId, String now) throws SQLException {
        Row row = find(requestId).orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        if (row.state().terminal()) {
            return Optional.of(row.state());
        }
        if (row.state() == State.RUNNING) {
            // The computation is under way. The withdrawal is recorded, and the row is
            // closed as cancelled when the execution returns without a committed effect.
            update(requestId, "cancel_requested_at = ?", now, now);
            return Optional.of(row.state());
        }
        update(requestId, "state = ?", State.CANCELLED.name(), now);
        return Optional.of(State.CANCELLED);
    }

    /**
     * Whether a withdrawal was recorded for this request.
     *
     * @param requestId the request
     * @return true when the peer was told to withdraw it
     * @throws SQLException when the inbox cannot be read
     */
    public synchronized boolean cancelRequested(String requestId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT cancel_requested_at FROM remote_task WHERE request_id = ?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null;
            }
        }
    }

    /**
     * Records that the cognitive path started.
     *
     * @param requestId the request
     * @param now       the instant
     * @throws SQLException when the inbox cannot be written
     */
    public synchronized void markRunning(String requestId, String now) throws SQLException {
        update(requestId, "state = ?", State.RUNNING.name(), now);
    }

    /**
     * Records the result of a completed task.
     *
     * @param requestId     the request
     * @param resultJson    the canonical JSON of the response
     * @param traceReference the reference to the durable execution trace
     * @param effectCommitted whether the memory effect of the execution was committed
     * @param now           the instant
     * @throws SQLException when the inbox cannot be written
     */
    public synchronized void markCompleted(String requestId, String resultJson, String traceReference,
                               boolean effectCommitted, String now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE remote_task SET state = ?, result_json = ?, trace_reference = ?, "
                        + "effect_committed = ?, updated_at = ? WHERE request_id = ?")) {
            ps.setString(1, State.COMPLETED.name());
            ps.setString(2, resultJson);
            ps.setString(3, traceReference);
            ps.setInt(4, effectCommitted ? 1 : 0);
            ps.setString(5, now);
            ps.setString(6, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * Records a task that ended without a result.
     *
     * @param requestId the request
     * @param state     the terminal state
     * @param reason    the machine-readable reason
     * @param now       the instant
     * @throws SQLException when the inbox cannot be written
     */
    public synchronized void markTerminal(String requestId, State state, String reason, String now)
            throws SQLException {
        markTerminal(requestId, state, reason, null, now);
    }

    /**
     * Records a task that ended without a result, keeping the response that says why.
     *
     * <p>The response is kept even when the task did not succeed: the caller must be able
     * to read the refusal after a restart, exactly as it can read a result.</p>
     *
     * @param requestId  the request
     * @param state      the terminal state
     * @param reason     the machine-readable reason
     * @param resultJson the canonical JSON of the response, when there is one
     * @param now        the instant
     * @throws SQLException when the inbox cannot be written
     */
    public synchronized void markTerminal(String requestId, State state, String reason, String resultJson,
                             String now) throws SQLException {
        if (!state.terminal()) {
            throw new IllegalArgumentException("the state " + state + " is not terminal");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE remote_task SET state = ?, refusal_reason = ?, result_json = ?, "
                        + "updated_at = ? WHERE request_id = ?")) {
            ps.setString(1, state.name());
            ps.setString(2, reason);
            ps.setString(3, resultJson);
            ps.setString(4, now);
            ps.setString(5, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * Records the peer's own measurement of the stages of one execution.
     *
     * <p><b>Why it lives here and not on the wire.</b> The mandate asks for the peer's
     * authentication, its durable inbox write and its answer signing to be measured
     * apart. Sending the numbers to the caller would change the answer contract, and a
     * changed contract is a protocol question. The measurement is the peer's own
     * bookkeeping about its own work, so it stays in the peer's own row: the peer writes
     * it, the peer owns it, and the performance report reads the peer's own file. The
     * caller learns nothing new and the wire stays exactly as it was.</p>
     *
     * <p><b>Why it does not move {@code updated_at}.</b> The row's state did not change:
     * the stages describe an execution that already happened. Writing the instant of the
     * measurement into the column that means "the state last changed" would make the
     * record lie about when the task ended.</p>
     *
     * @param requestId the request
     * @param stagesJson the measurement, as canonical JSON
     * @throws SQLException when the inbox cannot be written
     */
    public synchronized void recordStages(String requestId, String stagesJson) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE remote_task SET stage_timings_json = ? WHERE request_id = ?")) {
            ps.setString(1, stagesJson);
            ps.setString(2, requestId);
            ps.executeUpdate();
        }
    }

    /**
     * The row of a request, when the inbox holds one.
     *
     * @param requestId the request
     * @return the row, or empty
     * @throws SQLException when the inbox cannot be read
     */
    public synchronized Optional<Row> find(String requestId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT request_id, task_id, payload_digest, payload_hash, capability_id, "
                        + "capability_version, state, result_json, trace_reference, "
                        + "refusal_reason, effect_committed, created_at, updated_at, "
                        + "stage_timings_json "
                        + "FROM remote_task WHERE request_id = ?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(rs));
            }
        }
    }

    /**
     * The requests that were accepted but never reached a terminal state.
     *
     * <p>These are the ones a restart must reconcile: the process died between the
     * acceptance and the result, and nobody may assume which side of the memory effect
     * the death fell on.</p>
     *
     * @return the rows
     * @throws SQLException when the inbox cannot be read
     */
    public synchronized List<Row> unfinished() throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT request_id, task_id, payload_digest, payload_hash, capability_id, "
                        + "capability_version, state, result_json, trace_reference, "
                        + "refusal_reason, effect_committed, created_at, updated_at, "
                        + "stage_timings_json "
                        + "FROM remote_task WHERE state IN ('ACCEPTED','RUNNING') "
                        + "ORDER BY created_at")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(read(rs));
                }
            }
        }
        return rows;
    }

    /** Reads one row. */
    private static Row read(ResultSet rs) throws SQLException {
        return new Row(rs.getString("request_id"), rs.getString("task_id"),
                rs.getString("payload_digest"), rs.getString("payload_hash"),
                rs.getString("capability_id"),
                rs.getString("capability_version"), State.valueOf(rs.getString("state")),
                rs.getString("result_json"), rs.getString("trace_reference"),
                rs.getString("refusal_reason"), rs.getInt("effect_committed") != 0,
                rs.getString("created_at"), rs.getString("updated_at"),
                rs.getString("stage_timings_json"));
    }

    /** Runs one update of the state column. */
    private void update(String requestId, String clause, String value, String now)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE remote_task SET " + clause + ", updated_at = ? WHERE request_id = ?")) {
            ps.setString(1, value);
            ps.setString(2, now);
            ps.setString(3, requestId);
            ps.executeUpdate();
        }
    }

    /** How many requests the inbox holds. */
    public synchronized int size() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM remote_task")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            // The store is already unusable; the caller closes it at shutdown.
        }
    }
}
