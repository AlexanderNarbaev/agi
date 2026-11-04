package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.cognitive.microworld.ObservationDisposition;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W45.1 — the migration of a database that the W45.0 wave wrote.
 *
 * <p>The v18 migration adds the occurrence and the adjudication history and turns the
 * disposition into a projection. It has to do three things and be safe about them:</p>
 * <ul>
 *   <li>it must work on a database that was NOT written by this build — the v17 shape is
 *       written by hand here, so that nothing of this build's migration chain can be
 *       mistaken for the database under test;</li>
 *   <li>it must be ADDITIVE: no existing table changes shape, so no existing query can
 *       break;</li>
 *   <li>it must be RE-RUNNABLE: a migration that was interrupted half way has to be safe to
 *       run again, because the version is written last and a process that died before that
 *       left the database claiming to be older than it is.</li>
 * </ul>
 */
class ObservationHistoryMigrationTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-04T00:00:00Z";

    /** The workspace the records live in. */
    private static final String WORKSPACE = "microworld";

    @TempDir
    Path directory;

    @Test
    @DisplayName("a hand-written v17 database is migrated and its history is backfilled")
    void aHandWrittenV17DatabaseIsMigratedAndItsHistoryIsBackfilled() throws Exception {
        Path database = directory.resolve("legacy.sqlite");
        writeV17Database(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);

            // Every disposition of the v17 database has a row in the history.
            List<ObservationAdjudication> history = store.adjudications(WORKSPACE);
            assertThat(history).hasSize(2);
            assertThat(history).extracting(ObservationAdjudication::decisionId)
                    .containsExactlyInAnyOrder("adj-backfill-case-eligible",
                            "adj-backfill-case-rejected");
            assertThat(history).extracting(ObservationAdjudication::previousDisposition)
                    .as("nothing is known about what came before a v17 disposition")
                    .containsOnlyNulls();
            assertThat(history).extracting(ObservationAdjudication::disposition)
                    .containsExactlyInAnyOrder(ObservationDisposition.ELIGIBLE,
                            ObservationDisposition.REJECTED);

            // The projection and the history agree, so a rebuild reproduces it exactly.
            assertThat(store.rebuildDispositionProjection(WORKSPACE)).isTrue();
            assertThat(store.caseDispositions(WORKSPACE))
                    .containsEntry("case-eligible", ObservationDisposition.ELIGIBLE)
                    .containsEntry("case-rejected", ObservationDisposition.REJECTED);
        }
    }

    @Test
    @DisplayName("a fresh database is built at the current version with the new tables")
    void aFreshDatabaseIsBuiltAtTheCurrentVersion() throws Exception {
        Path database = directory.resolve("fresh.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat(tableNames(database))
                    .contains("observation_occurrence", "observation_adjudication",
                            "observation_disposition");
        }
    }

    @Test
    @DisplayName("the migration is safe to run again after an interruption")
    void theMigrationIsSafeToRunAgainAfterAnInterruption() throws Exception {
        Path database = directory.resolve("interrupted.sqlite");
        writeV17Database(database);

        // The first run of the migration.
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat(store.adjudications(WORKSPACE)).hasSize(2);
        }

        // The process died before the version was written: the tables are there, the rows are
        // there, and the database still claims to be a v17 one.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("UPDATE schema_meta SET value='17' WHERE key='version'");
        }

        // The migration runs again, over a database that already has everything.
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat(store.adjudications(WORKSPACE))
                    .withFailMessage("the backfill must not copy a row twice")
                    .hasSize(2);
            assertThat(store.adjudications(WORKSPACE))
                    .extracting(ObservationAdjudication::decisionId)
                    .containsExactlyInAnyOrder("adj-backfill-case-eligible",
                            "adj-backfill-case-rejected");
            assertThat(store.rebuildDispositionProjection(WORKSPACE)).isTrue();
        }
    }

    @Test
    @DisplayName("the migration leaves every existing table exactly as it was")
    void theMigrationLeavesEveryExistingTableExactlyAsItWas() throws Exception {
        Path database = directory.resolve("additive.sqlite");
        writeV17Database(database);
        List<String> before = tableShapes(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
        }

        List<String> after = tableShapes(database);
        assertThat(after).containsAll(before);
        for (String shape : before) {
            assertThat(after).contains(shape);
        }
    }

    @Test
    @DisplayName("a v17 database keeps its observations after the migration")
    void aV17DatabaseKeepsItsObservationsAfterTheMigration() throws Exception {
        Path database = directory.resolve("legacy.sqlite");
        writeV17Database(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.cases(WORKSPACE)).hasSize(2);
            assertThat(store.sources(WORKSPACE)).hasSize(2);
            assertThat(store.evidence(WORKSPACE)).hasSize(2);
            assertThat(store.caseIdForEvidence("evd-1")).isEqualTo("case-eligible");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Writes a database in the shape the W45.0 wave left behind.
     *
     * <p>The DDL is written out by hand rather than produced by this build, so that the
     * migration is tested against a database this build never touched. The tables are the
     * ones the W45.1 wave reads and writes: the disposition, the case, the source, the
     * evidence and the version. The rest of the v17 shape is unchanged by the migration, and
     * that is what {@link #theMigrationLeavesEveryExistingTableExactlyAsItWas()} pins.</p>
     */
    private void writeV17Database(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT INTO schema_meta(key, value) VALUES('version', '17')");
            st.execute("CREATE TABLE workspace (id TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL, version INTEGER NOT NULL)");
            st.execute("CREATE TABLE source (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "kind TEXT NOT NULL, title TEXT NOT NULL, locator TEXT, authority INTEGER NOT NULL, "
                    + "freshness INTEGER NOT NULL, excerpt TEXT, added_at TEXT NOT NULL, "
                    + "content_hash TEXT, published_at TEXT, language TEXT, format TEXT)");
            st.execute("CREATE TABLE evidence (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "claim_id TEXT NOT NULL, source_id TEXT NOT NULL, relation TEXT NOT NULL, "
                    + "excerpt TEXT, quality INTEGER NOT NULL, recorded_at TEXT NOT NULL, case_id TEXT)");
            st.execute("CREATE TABLE epistemic_case (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "concept_id TEXT, statement TEXT NOT NULL, conditions TEXT NOT NULL, "
                    + "outcome TEXT NOT NULL, source_id TEXT, origin TEXT NOT NULL, observed_at TEXT NOT NULL)");
            st.execute("CREATE TABLE observation_disposition (case_id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, disposition TEXT NOT NULL, reason TEXT, "
                    + "contradicted_prediction_id TEXT, resolved_by TEXT, resolved_at TEXT, "
                    + "model_revision_id TEXT, recorded_at TEXT NOT NULL)");
            st.execute("CREATE INDEX idx_disposition_workspace ON observation_disposition(workspace_id)");

            st.execute("INSERT INTO workspace(id, name, created_at, version) VALUES('"
                    + WORKSPACE + "','the micro-world','" + INSTANT + "',1)");
            st.execute("INSERT INTO source(id, workspace_id, kind, title, locator, authority, "
                    + "freshness, excerpt, added_at) VALUES('src-1','" + WORKSPACE + "',"
                    + "'OBSERVATION','an example','power=1,switch=1',40,60,'the lamp was on','"
                    + INSTANT + "')");
            st.execute("INSERT INTO source(id, workspace_id, kind, title, locator, authority, "
                    + "freshness, excerpt, added_at) VALUES('src-2','" + WORKSPACE + "',"
                    + "'OBSERVATION','a counterexample','power=0,switch=0',40,60,'the lamp was on','"
                    + INSTANT + "')");
            st.execute("INSERT INTO evidence(id, workspace_id, claim_id, source_id, relation, "
                    + "excerpt, quality, recorded_at, case_id) VALUES('evd-1','" + WORKSPACE
                    + "','clm-1','src-1','EXEMPLIFIES','the lamp was on',50,'" + INSTANT
                    + "','case-eligible')");
            st.execute("INSERT INTO evidence(id, workspace_id, claim_id, source_id, relation, "
                    + "excerpt, quality, recorded_at, case_id) VALUES('evd-2','" + WORKSPACE
                    + "','clm-1','src-2','CONTRADICTS','the lamp was on',50,'" + INSTANT
                    + "','case-rejected')");
            st.execute("INSERT INTO epistemic_case(id, workspace_id, concept_id, statement, "
                    + "conditions, outcome, source_id, origin, observed_at) VALUES('case-eligible','"
                    + WORKSPACE + "','cpt-1','the lamp was on','power=1,switch=1','the lamp was on',"
                    + "'src-1','OBSERVED','" + INSTANT + "')");
            st.execute("INSERT INTO epistemic_case(id, workspace_id, concept_id, statement, "
                    + "conditions, outcome, source_id, origin, observed_at) VALUES('case-rejected','"
                    + WORKSPACE + "','cpt-1','the lamp was on','power=0,switch=0','the lamp was on',"
                    + "'src-2','OBSERVED','" + INSTANT + "')");
            st.execute("INSERT INTO observation_disposition(case_id, workspace_id, disposition, "
                    + "reason, contradicted_prediction_id, resolved_by, resolved_at, "
                    + "model_revision_id, recorded_at) VALUES('case-eligible','" + WORKSPACE
                    + "','ELIGIBLE','the observation was ordinary model data',NULL,NULL,NULL,NULL,'"
                    + INSTANT + "')");
            st.execute("INSERT INTO observation_disposition(case_id, workspace_id, disposition, "
                    + "reason, contradicted_prediction_id, resolved_by, resolved_at, "
                    + "model_revision_id, recorded_at) VALUES('case-rejected','" + WORKSPACE
                    + "','REJECTED','a review rejected the observation',NULL,'the owner','"
                    + INSTANT + "',NULL,'" + INSTANT + "')");
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
                     + "WHERE type='table' AND name NOT IN ('observation_occurrence', "
                     + "'observation_adjudication') ORDER BY name")) {
            while (rs.next()) {
                shapes.add(rs.getString("name") + ": " + rs.getString("sql"));
            }
        }
        return shapes;
    }
}
