package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W45.2 — the migration that gives the workspace its snapshots.
 *
 * <p>The v19 migration adds one table and changes nothing else. It has to be safe on a
 * fresh database, it has to be safe on a database that the W45.1 wave wrote, and it has to
 * be safe to run AGAIN after an interruption — because the version is written last, a process
 * that died before that left a database that claims to be older than it is, and the next
 * process to open it runs the migration a second time.</p>
 *
 * <p>The v18 database is written by hand, so that nothing of this build's migration chain can
 * be mistaken for the database under test.</p>
 */
class WorkspaceSnapshotMigrationTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-04T00:00:00Z";

    /** The workspace the records live in. */
    private static final String WORKSPACE = "demo";

    @TempDir
    Path directory;

    @Test
    @DisplayName("a fresh database is built at the current version with the snapshot table")
    void aFreshDatabaseIsBuiltAtTheCurrentVersion() throws Exception {
        Path database = directory.resolve("fresh.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat(store.schemaVersion()).isEqualTo(19);
        }
        assertThat(tableNames(database)).contains("workspace_snapshot");
    }

    @Test
    @DisplayName("a hand-written v18 database is migrated and keeps its content")
    void aHandWrittenV18DatabaseIsMigratedAndKeepsItsContent() throws Exception {
        Path database = directory.resolve("legacy.sqlite");
        writeV18Database(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(19);
            assertThat(store.exportWorkspace(WORKSPACE).concepts()).hasSize(1);
            assertThat(store.contradictions(WORKSPACE)).hasSize(1);
            assertThat(store.workspaceSnapshots(WORKSPACE))
                    .withFailMessage("the migration invents no snapshot: there was nothing to "
                            + "snapshot")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("the migration changes no existing table's shape")
    void theMigrationChangesNoExistingTablesShape() throws Exception {
        Path database = directory.resolve("additive.sqlite");
        writeV18Database(database);
        List<String> before = tableShapes(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(19);
        }

        List<String> after = tableShapes(database);
        assertThat(after).containsAll(before);
        for (String shape : before) {
            assertThat(after).contains(shape);
        }
    }

    @Test
    @DisplayName("the migration is safe to run again after an interruption")
    void theMigrationIsSafeToRunAgainAfterAnInterruption() throws Exception {
        Path database = directory.resolve("interrupted.sqlite");
        writeV18Database(database);

        // The first run of the migration.
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(19);
            // A snapshot is taken, so that the second run has something to be idempotent
            // about.
            store.takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, INSTANT);
        }

        // The process died before the version was written: the table is there, and the
        // database still claims to be a v18 one.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("UPDATE schema_meta SET value='18' WHERE key='version'");
        }

        // The migration runs again, over a database that already has everything.
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(19);
            assertThat(store.workspaceSnapshots(WORKSPACE))
                    .withFailMessage("the migration must not touch the snapshots")
                    .hasSize(1);
            assertThat(store.exportWorkspace(WORKSPACE).concepts()).hasSize(1);
        }
    }

    // ── the fixture ──────────────────────────────────────────────────────────

    /** Writes a database in the shape the W45.1 wave left behind. */
    private void writeV18Database(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT INTO schema_meta(key, value) VALUES('version', '18')");
            st.execute("CREATE TABLE workspace (id TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL, version INTEGER NOT NULL)");
            st.execute("CREATE TABLE concept (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "name TEXT NOT NULL, definition TEXT, domain TEXT, created_at TEXT NOT NULL)");
            st.execute("CREATE TABLE source (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "kind TEXT NOT NULL, title TEXT NOT NULL, locator TEXT, authority INTEGER NOT NULL, "
                    + "freshness INTEGER NOT NULL, excerpt TEXT, added_at TEXT NOT NULL, "
                    + "content_hash TEXT, published_at TEXT, language TEXT, format TEXT)");
            st.execute("CREATE TABLE claim (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "concept_id TEXT NOT NULL, proposition TEXT NOT NULL, scope TEXT, context TEXT, "
                    + "status TEXT NOT NULL, confidence REAL NOT NULL, version INTEGER NOT NULL, "
                    + "introduced_by_source_id TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, "
                    + "warrant TEXT, derivation_status TEXT, extraction_method TEXT, validation_method TEXT)");
            st.execute("CREATE TABLE evidence (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "claim_id TEXT NOT NULL, source_id TEXT NOT NULL, relation TEXT NOT NULL, "
                    + "excerpt TEXT, quality INTEGER NOT NULL, recorded_at TEXT NOT NULL, case_id TEXT)");
            st.execute("CREATE TABLE representation (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "concept_id TEXT NOT NULL, level TEXT NOT NULL, audience TEXT, statement TEXT NOT NULL, "
                    + "scope TEXT, assumptions TEXT NOT NULL, included_facts TEXT NOT NULL, "
                    + "omitted TEXT NOT NULL, validity_conditions TEXT NOT NULL, prerequisites TEXT NOT NULL, "
                    + "refines TEXT, confidence REAL NOT NULL, created_at TEXT NOT NULL)");
            st.execute("CREATE TABLE prerequisite (workspace_id TEXT NOT NULL, concept_id TEXT NOT NULL, "
                    + "requires_concept_id TEXT NOT NULL, required INTEGER NOT NULL, note TEXT, "
                    + "PRIMARY KEY (workspace_id, concept_id, requires_concept_id))");
            st.execute("CREATE TABLE revision (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "target_type TEXT NOT NULL, target_id TEXT NOT NULL, kind TEXT NOT NULL, "
                    + "summary TEXT NOT NULL, added_facts TEXT NOT NULL, still_valid TEXT NOT NULL, "
                    + "no_longer_valid TEXT NOT NULL, newly_explained TEXT NOT NULL, "
                    + "evidence_ids TEXT NOT NULL, recorded_at TEXT NOT NULL)");
            st.execute("CREATE TABLE derivation (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "rule_id TEXT NOT NULL, rule_name TEXT NOT NULL, connective TEXT NOT NULL, "
                    + "conclusion_claim_id TEXT NOT NULL, status TEXT NOT NULL, recorded_at TEXT NOT NULL)");
            st.execute("CREATE TABLE derivation_premise (derivation_id TEXT NOT NULL, "
                    + "ordinal INTEGER NOT NULL, claim_id TEXT NOT NULL, PRIMARY KEY (derivation_id, ordinal))");
            st.execute("CREATE TABLE contradiction (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "claim_a_id TEXT NOT NULL, claim_b_id TEXT NOT NULL, kind TEXT NOT NULL, "
                    + "detected_by TEXT NOT NULL, recorded_at TEXT NOT NULL)");
            st.execute("CREATE TABLE learning_goal (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "description TEXT NOT NULL, success_criteria TEXT NOT NULL, prerequisites TEXT NOT NULL, "
                    + "status TEXT NOT NULL, progress_evidence TEXT NOT NULL, failed_attempts TEXT NOT NULL, "
                    + "remaining_work TEXT, budget_units INTEGER NOT NULL, budget_used INTEGER NOT NULL, "
                    + "last_checkpoint TEXT, recovery_instructions TEXT, "
                    + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            // The learning state lives in the same database, and the
            // migrations that create it all run BEFORE v19. A real v18
            // database therefore has these tables, and the fixture has to
            // have them too: the snapshot of a workspace carries the learning
            // state with the knowledge, and a fixture that lacks the tables
            // would prove nothing about a database that has them.
            st.execute("CREATE TABLE asked_question (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, canonical_key TEXT NOT NULL, "
                    + "question TEXT NOT NULL, uncovered_terms TEXT NOT NULL, "
                    + "times_asked INTEGER NOT NULL, first_asked_at INTEGER NOT NULL, "
                    + "last_asked_at INTEGER NOT NULL, status TEXT NOT NULL, "
                    + "resolution_note TEXT, answered INTEGER NOT NULL DEFAULT 0, "
                    + "forgotten_at INTEGER, forgotten_kind TEXT, forgotten_reason TEXT)");
            st.execute("CREATE TABLE assessment_outcome (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, item_id TEXT NOT NULL, "
                    + "concept_id TEXT NOT NULL, dimension TEXT NOT NULL, "
                    + "correct INTEGER NOT NULL, score REAL NOT NULL, "
                    + "claimed_confidence REAL NOT NULL, learner_answer TEXT, "
                    + "session_id TEXT, recorded_at TEXT NOT NULL)");
            st.execute("CREATE TABLE learning_state (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, concept_id TEXT NOT NULL, "
                    + "dimension TEXT NOT NULL, stage TEXT NOT NULL, score REAL NOT NULL, "
                    + "evidence_count INTEGER NOT NULL, transitioned_at TEXT NOT NULL, "
                    + "note TEXT, UNIQUE (workspace_id, concept_id, dimension))");
            st.execute("CREATE TABLE learning_transition (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, concept_id TEXT NOT NULL, "
                    + "dimension TEXT NOT NULL, from_stage TEXT, to_stage TEXT NOT NULL, "
                    + "reason TEXT NOT NULL, outcome_id TEXT, occurred_at TEXT NOT NULL)");
            st.execute("CREATE TABLE learning_error (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, item_id TEXT NOT NULL, "
                    + "concept_id TEXT NOT NULL, dimension TEXT NOT NULL, "
                    + "learner_answer TEXT NOT NULL, expected_answer TEXT NOT NULL, "
                    + "verified INTEGER NOT NULL, recorded_at TEXT NOT NULL, "
                    + "UNIQUE (workspace_id, item_id))");
            st.execute("CREATE TABLE assessment_item (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, concept_id TEXT NOT NULL, "
                    + "dimension TEXT NOT NULL, prompt TEXT NOT NULL, key_terms TEXT NOT NULL, "
                    + "difficulty INTEGER NOT NULL, variant TEXT NOT NULL, "
                    + "critical INTEGER NOT NULL, held_out INTEGER NOT NULL, "
                    + "recorded_at TEXT NOT NULL)");
            st.execute("CREATE TABLE dialogue_turn (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, question TEXT NOT NULL, "
                    + "concept_ids TEXT NOT NULL, concept_names TEXT NOT NULL, "
                    + "claim_ids TEXT NOT NULL, language TEXT NOT NULL, "
                    + "answered INTEGER NOT NULL, act TEXT NOT NULL, "
                    + "recorded_at TEXT NOT NULL, forgotten_at INTEGER)");
            st.execute("CREATE TABLE hebbian_binding (workspace_id TEXT NOT NULL, "
                    + "concept_id TEXT NOT NULL, weights TEXT NOT NULL, "
                    + "accumulator TEXT NOT NULL, updated_at TEXT NOT NULL, "
                    + "PRIMARY KEY(workspace_id, concept_id))");

            st.execute("CREATE TABLE learning_goal_subtask (goal_id TEXT NOT NULL, "
                    + "subtask_id TEXT NOT NULL, description TEXT NOT NULL, required INTEGER NOT NULL, "
                    + "prerequisites TEXT NOT NULL, status TEXT NOT NULL, evidence TEXT NOT NULL, note TEXT, "
                    + "PRIMARY KEY (goal_id, subtask_id))");

            // The tables that the v16, v17 and v18 migrations add. A database that claims
            // to be a v18 one HAS them: the W47 capture and the erasure read them, and a
            // fixture that leaves them out is not a v18 database — it is a broken one.
            st.execute("CREATE TABLE epistemic_case (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "concept_id TEXT, statement TEXT NOT NULL, conditions TEXT NOT NULL, "
                    + "outcome TEXT NOT NULL, source_id TEXT, origin TEXT NOT NULL, "
                    + "observed_at TEXT NOT NULL)");
            st.execute("CREATE TABLE observation_disposition (case_id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, disposition TEXT NOT NULL, reason TEXT, "
                    + "contradicted_prediction_id TEXT, resolved_by TEXT, resolved_at TEXT, "
                    + "model_revision_id TEXT, recorded_at TEXT NOT NULL)");
            st.execute("CREATE TABLE observation_occurrence (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, case_id TEXT NOT NULL, episode_id TEXT, "
                    + "signal_id TEXT, source_id TEXT, evidence_id TEXT, observed_at TEXT NOT NULL, "
                    + "disposition TEXT NOT NULL, reason TEXT)");
            st.execute("CREATE TABLE observation_adjudication (decision_id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, case_id TEXT NOT NULL, previous_disposition TEXT, "
                    + "disposition TEXT NOT NULL, reason TEXT, contradicted_prediction_id TEXT, "
                    + "resolved_by TEXT, resolved_at TEXT, model_revision_id TEXT, "
                    + "recorded_at TEXT NOT NULL, request_id TEXT)");

            st.execute("INSERT INTO workspace(id, name, created_at, version) VALUES('"
                    + WORKSPACE + "','Demo workspace','" + INSTANT + "',1)");
            st.execute("INSERT INTO concept(id, workspace_id, name, definition, domain, created_at) "
                    + "VALUES('cpt-1','" + WORKSPACE + "','Photosynthesis','light into sugar',"
                    + "'biology','" + INSTANT + "')");
            st.execute("INSERT INTO claim(id, workspace_id, concept_id, proposition, status, "
                    + "confidence, version, created_at, updated_at) VALUES('clm-1','" + WORKSPACE
                    + "','cpt-1','Plants use light to make sugars.','VERIFIED',0.9,1,'" + INSTANT
                    + "','" + INSTANT + "')");
            st.execute("INSERT INTO contradiction(id, workspace_id, claim_a_id, claim_b_id, kind, "
                    + "detected_by, recorded_at) VALUES('con-1','" + WORKSPACE + "','clm-1','clm-2',"
                    + "'OPPOSITE_POLARITY','the detector','" + INSTANT + "')");
        }
    }

    /** The names of the tables a database holds. */
    private List<String> tableNames(Path database) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    /** The DDL of every table a database holds, so that two shapes can be compared. */
    private List<String> tableShapes(Path database) throws Exception {
        List<String> shapes = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, sql FROM sqlite_master "
                     + "WHERE type='table' AND name <> 'workspace_snapshot' ORDER BY name")) {
            while (rs.next()) {
                shapes.add(rs.getString("name") + ": " + rs.getString("sql"));
            }
        }
        return shapes;
    }
}
