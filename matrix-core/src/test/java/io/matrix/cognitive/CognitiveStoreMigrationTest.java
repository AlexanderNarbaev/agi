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
            assertThat(store.claimIdempotency("delivery-1", "fingerprint", "2026-11-03T00:00:00Z"))
                    .isTrue();
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
