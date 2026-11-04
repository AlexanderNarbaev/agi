package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ValidationKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W45.0 — the migration of a database that the W44 wave wrote.
 *
 * <p>The W45.0 schema adds the durable sequence and the idempotency registry, and it changes
 * the identity of a procedure from its id alone to the pair (id, version). A database written
 * by W44 must therefore be migrated rather than dropped: the procedures it holds are the
 * record of what the system had learned, and the episodes it holds are the record of what it
 * had done.</p>
 */
class CognitiveStoreMigrationTest {

    private static final String WORKSPACE = "microworld";

    @TempDir
    Path directory;

    private Path database;

    @BeforeEach
    void setUp() {
        database = directory.resolve("cognitive.sqlite");
    }

    @AfterEach
    void tearDown() {
        // the store is closed inside the tests
    }

    @Test
    @DisplayName("a database written by W44 keeps its episodes, its events and its procedures")
    void aDatabaseWrittenByW44KeepsItsRecords() throws SQLException {
        writeW44Database();

        CognitiveStore store = new CognitiveStore(database);
        try {
            assertThat(store.episodes(WORKSPACE)).containsExactly("ep-w44");
            assertThat(store.events("ep-w44")).hasSize(2);
            assertThat(store.events("ep-w44").get(0).phase()).isEqualTo("PERCEIVE");

            List<Procedure> procedures = store.procedures(WORKSPACE);
            assertThat(procedures).hasSize(1);
            assertThat(procedures.get(0).id()).isEqualTo("proc-w44");
            assertThat(procedures.get(0).version()).isEqualTo("v1");
            assertThat(procedures.get(0).name()).isEqualTo("the way W44 had it");
            // A row that was written before the validation kind existed is a consistency
            // check, and the record says so.
            assertThat(procedures.get(0).validationKind()).isEqualTo(ValidationKind.IN_SAMPLE);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("after the migration a procedure may have a second version beside the first")
    void afterTheMigrationAProcedureMayHaveASecondVersion() throws SQLException {
        writeW44Database();

        CognitiveStore store = new CognitiveStore(database);
        try {
            store.recordProcedure(WORKSPACE, new Procedure("proc-w44", "the second way",
                    "micro-world-outcome", "v2", "the model has settled", Set.of("MODEL_SETTLED"),
                    "reference", 2, 1.0, 0, 0, "2026-11-03T00:00:00Z", null,
                    ValidationKind.INDEPENDENT));

            List<Procedure> procedures = store.procedures(WORKSPACE);
            assertThat(procedures).hasSize(2);
            assertThat(procedures).extracting(Procedure::version).containsExactlyInAnyOrder("v1", "v2");
            assertThat(procedures).extracting(Procedure::name)
                    .containsExactlyInAnyOrder("the way W44 had it", "the second way");
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("the durable sequence and the idempotency registry arrive with the migration")
    void theDurableSequenceAndTheIdempotencyRegistryArriveWithTheMigration() throws SQLException {
        writeW44Database();

        CognitiveStore store = new CognitiveStore(database);
        try {
            assertThat(store.nextSequence("cognitive.signal")).isEqualTo(1L);
            assertThat(store.claimIdempotency("delivery-1", "fingerprint", "req-1", "sig-1", 1L,
                    "ep-sig-1", OwnershipToken.current(), "2026-11-03T00:00:00Z"))
                    .isNotNull();
            assertThat(store.findIdempotency("delivery-1")).isPresent();
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("a W44 database that holds two events at one position still opens")
    void aW44DatabaseThatHoldsTwoEventsAtOnePositionStillOpens() throws SQLException {
        writeW44Database();
        // The W44 store replaced an event by its id, so two events of one episode could hold
        // one position. The unique index cannot be created over such a table, and the store
        // must still open and still refuse the second event at that position.
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = raw.createStatement()) {
            st.execute("INSERT INTO cognitive_event(id, episode_id, sequence, type, actor, detail, "
                    + "occurred_at) VALUES('ep-w44-evt-9','ep-w44',0,'OTHER','OTHER','other',"
                    + "'2026-11-02T00:00:00Z')");
        }

        CognitiveStore store = new CognitiveStore(database);
        try {
            assertThat(store.events("ep-w44")).hasSize(3);
            assertThatThrownBy(() -> store.appendEvent("ep-w44-evt-1", "ep-w44", 0L, "PERCEIVE",
                    "ChannelClassifier", "channel=STRUCTURED", "2026-11-02T00:00:00Z"))
                    .isInstanceOf(CognitiveConflictException.class);
        } finally {
            store.close();
        }
    }

    /**
     * W45.1 — the migration of a database that the W45.0 wave wrote.
     *
     * <p>The W45.1 schema adds the request lifecycle: the checkpoint, the owner, the request
     * identity and the arrival number of a request, and the append-only history of the
     * lifecycle. A database written by W45.0 therefore has an idempotency row that knows
     * nothing about the checkpoint or the owner — and it must be MIGRATED, not dropped: the
     * outcome it holds is the record of a request that already happened.</p>
     */
    @Test
    @DisplayName("a database written by W45.0 keeps its requests and gains the request lifecycle")
    void aDatabaseWrittenByW45KeepsItsRequestsAndGainsTheRequestLifecycle() throws SQLException {
        writeW45Database();

        CognitiveStore store = new CognitiveStore(database);
        try {
            // The mapping of the completed request survived, with its outcome.
            CognitiveStore.IdempotencyRecord record = store.findIdempotency("delivery-1")
                    .orElseThrow();
            assertThat(record.state()).isEqualTo(RequestExecutionState.COMPLETED.name());
            assertThat(record.episodeId()).isEqualTo("ep-w45");
            assertThat(record.outcome()).isNotBlank();
            // The new columns exist and are empty for a row that predates them.
            assertThat(record.checkpoint()).isNull();
            assertThat(record.ownerToken()).isNull();
            assertThat(record.requestId()).isNull();
            // The request history table exists and is empty.
            assertThat(store.requestEvents("delivery-1")).isEmpty();
            // The episode of the old request is still there.
            assertThat(store.episodes(WORKSPACE)).containsExactly("ep-w45");
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("a database written by W45.0 can claim a request afterwards")
    void aDatabaseWrittenByW45CanClaimARequestAfterwards() throws SQLException {
        writeW45Database();

        CognitiveStore store = new CognitiveStore(database);
        try {
            CognitiveStore.IdempotencyRecord claimed = store.claimIdempotency("delivery-2",
                    "fingerprint", "req-2", "sig-2", 1L, "ep-sig-2", OwnershipToken.current(),
                    "2026-11-04T00:00:00Z");

            assertThat(claimed.state()).isEqualTo(RequestExecutionState.IN_PROGRESS.name());
            assertThat(claimed.requestId()).isEqualTo("req-2");
            assertThat(claimed.signalSequence()).isEqualTo(1L);
            assertThat(claimed.lastCheckpoint()).contains(RequestCheckpoint.CLAIMED);
            assertThat(claimed.owner()).isPresent();
            // The lifecycle history is append-only and lives beside the request.
            store.recordRequestEvent("delivery-2", "req-2", "ep-sig-2",
                    RequestExecutionState.IN_PROGRESS.name(), RequestCheckpoint.CLAIMED.name(),
                    "the request was claimed", "2026-11-04T00:00:00Z");
            assertThat(store.requestEvents("delivery-2")).isNotEmpty();
        } finally {
            store.close();
        }
    }

    /** Writes a database in the shape the W45.0 wave left behind. */
    private void writeW45Database() throws SQLException {
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = raw.createStatement()) {
            st.execute("CREATE TABLE cognitive_schema_meta ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT INTO cognitive_schema_meta(key, value) VALUES('version', '3')");
            st.execute("""
                    CREATE TABLE cognitive_episode (
                        id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, summary TEXT NOT NULL,
                        started_at TEXT NOT NULL, closed_at TEXT)""");
            st.execute("""
                    CREATE TABLE cognitive_idempotency (
                        key TEXT PRIMARY KEY, fingerprint TEXT NOT NULL, episode_id TEXT,
                        signal_id TEXT, state TEXT NOT NULL, outcome TEXT,
                        created_at TEXT NOT NULL, completed_at TEXT)""");
            st.execute("""
                    CREATE TABLE cognitive_regulatory_state (
                        workspace_id TEXT PRIMARY KEY, state TEXT NOT NULL, version INTEGER NOT NULL,
                        recorded_at TEXT NOT NULL)""");
            st.execute("""
                    CREATE TABLE cognitive_regulatory_event (
                        id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, episode_id TEXT,
                        kind TEXT NOT NULL, magnitude REAL NOT NULL, source TEXT NOT NULL,
                        applied_at TEXT NOT NULL)""");
            st.execute("INSERT INTO cognitive_episode(id, workspace_id, summary, started_at, "
                    + "closed_at) VALUES('ep-w45','microworld','a cognitive cycle on STRUCTURED',"
                    + "'2026-11-03T00:00:00Z','2026-11-03T00:00:01Z')");
            st.execute("INSERT INTO cognitive_idempotency(key, fingerprint, episode_id, signal_id, "
                    + "state, outcome, created_at, completed_at) VALUES('delivery-1','fingerprint',"
                    + "'ep-w45','sig-w45','COMPLETED','{\"episodeId\":\"ep-w45\"}',"
                    + "'2026-11-03T00:00:00Z','2026-11-03T00:00:01Z')");
        }
    }

    /** Writes a database in the shape the W44 wave left behind. */
    private void writeW44Database() throws SQLException {
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = raw.createStatement()) {
            st.execute("CREATE TABLE cognitive_schema_meta ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT INTO cognitive_schema_meta(key, value) VALUES('version', '1')");
            st.execute("""
                    CREATE TABLE cognitive_episode (
                        id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, summary TEXT NOT NULL,
                        started_at TEXT NOT NULL, closed_at TEXT)""");
            st.execute("""
                    CREATE TABLE cognitive_prediction (
                        id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, episode_id TEXT NOT NULL,
                        subject TEXT NOT NULL, predicted_value TEXT NOT NULL, basis TEXT NOT NULL,
                        basis_version TEXT NOT NULL, confidence REAL NOT NULL,
                        observed_only INTEGER NOT NULL, case_ids TEXT NOT NULL,
                        made_at TEXT NOT NULL)""");
            st.execute("""
                    CREATE TABLE cognitive_error (
                        id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, prediction_id TEXT NOT NULL,
                        expected TEXT NOT NULL, observed TEXT NOT NULL, magnitude REAL NOT NULL,
                        disposition TEXT NOT NULL, responsible_model TEXT NOT NULL,
                        responsible_version TEXT NOT NULL, case_ids TEXT NOT NULL,
                        detected_at TEXT NOT NULL)""");
            st.execute("""
                    CREATE TABLE cognitive_procedure (
                        id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, name TEXT NOT NULL,
                        problem_family TEXT NOT NULL, version TEXT NOT NULL, applicability TEXT NOT NULL,
                        required_facts TEXT NOT NULL, reference_algorithm_id TEXT NOT NULL,
                        validation_cases INTEGER NOT NULL, validation_agreement REAL NOT NULL,
                        use_count INTEGER NOT NULL, failure_count INTEGER NOT NULL,
                        promoted_at TEXT NOT NULL, invalidated_at TEXT)""");
            st.execute("""
                    CREATE TABLE cognitive_task (
                        id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, origin TEXT NOT NULL,
                        reason TEXT NOT NULL, serves_goal TEXT NOT NULL, expected_benefit TEXT NOT NULL,
                        cost_estimate TEXT NOT NULL, completion_condition TEXT NOT NULL,
                        stop_condition TEXT NOT NULL, allowed_actions TEXT NOT NULL,
                        budget_wall_clock_millis INTEGER NOT NULL, status TEXT NOT NULL,
                        created_at TEXT NOT NULL, completed_at TEXT)""");
            st.execute("""
                    CREATE TABLE cognitive_event (
                        id TEXT PRIMARY KEY, episode_id TEXT NOT NULL, sequence INTEGER NOT NULL,
                        type TEXT NOT NULL, actor TEXT NOT NULL, detail TEXT NOT NULL,
                        occurred_at TEXT NOT NULL)""");
            st.execute("INSERT INTO cognitive_episode(id, workspace_id, summary, started_at, "
                    + "closed_at) VALUES('ep-w44','microworld','a cognitive cycle on STRUCTURED',"
                    + "'2026-11-02T00:00:00Z','2026-11-02T00:00:01Z')");
            st.execute("INSERT INTO cognitive_event(id, episode_id, sequence, type, actor, detail, "
                    + "occurred_at) VALUES('ep-w44-evt-0','ep-w44',0,'PERCEIVE','ChannelClassifier',"
                    + "'channel=STRUCTURED','2026-11-02T00:00:00Z')");
            st.execute("INSERT INTO cognitive_event(id, episode_id, sequence, type, actor, detail, "
                    + "occurred_at) VALUES('ep-w44-evt-1','ep-w44',1,'ANSWER','CognitiveKernel',"
                    + "'lamp=false.','2026-11-02T00:00:01Z')");
            st.execute("INSERT INTO cognitive_procedure(id, workspace_id, name, problem_family, "
                    + "version, applicability, required_facts, reference_algorithm_id, "
                    + "validation_cases, validation_agreement, use_count, failure_count, "
                    + "promoted_at, invalidated_at) VALUES('proc-w44','microworld',"
                    + "'the way W44 had it','micro-world-outcome','v1','the model has settled',"
                    + "'MODEL_SETTLED','reference',2,1.0,0,0,'2026-11-02T00:00:00Z',NULL)");
        }
    }
}
