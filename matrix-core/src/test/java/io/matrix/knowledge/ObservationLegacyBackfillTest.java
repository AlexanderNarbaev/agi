package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.cognitive.microworld.ObservationDisposition;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W45.1 — the legacy disposition of a database the W44 wave wrote.
 *
 * <p>The v16→v17 migration has to classify the cases of a database that was written before
 * the disposition existed, and the rule it uses has to be the rule the W45 SEMANTIC uses —
 * not the rule the W44 wave used. The two are not the same, and the difference is the whole
 * point of this test.</p>
 *
 * <p><b>What the W44 wave did, exactly.</b> From the git history:</p>
 * <ul>
 *   <li>{@code MicroWorldSession.observe()} held an observation out of the model whenever it
 *       disagreed with ANY prediction — settled or not
 *       ({@code 81f08478:.../microworld/MicroWorldSession.java}, lines 187-191);</li>
 *   <li>{@code CognitiveKernel} wrote the evidence relation from ONE question — "does any
 *       surviving hypothesis explain this observation?" — so {@code CONTRADICTS} meant "no
 *       hypothesis explains it" and nothing more
 *       ({@code 81f08478:.../cognitive/CognitiveKernel.java}, the OBSERVE phase);</li>
 *   <li>the prediction's confidence was {@code forecast.unanimous() ? 1.0 : 0.0}, so a
 *       SETTLED prediction and an UNSETTLED one are told apart in the database itself;</li>
 *   <li>a prediction error was written with the disposition {@code UNRESOLVED} when the
 *       model was confidently wrong and {@code MISSING_CONDITION} when no hypothesis
 *       explained the observation at all.</li>
 * </ul>
 *
 * <p><b>What the W45 wave decided.</b> A contradiction is a disagreement with a BELIEF — a
 * unanimous prediction — and not with any prediction. So an observation that the W44 session
 * held only because it disagreed with an UNSETTLED prediction is, under W45, ordinary
 * learning, and it must not be classified as a held conflict.</p>
 *
 * <p>The fixture below contains one case of each of the four situations, written by hand in
 * the v16 shape, and the assertions are the W45 restoration policy.</p>
 */
class ObservationLegacyBackfillTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-04T00:00:00Z";

    /** The workspace the records live in. */
    private static final String WORKSPACE = "microworld";

    @TempDir
    Path directory;

    @Test
    @DisplayName("the four cases of a v16 database are classified by the W45 policy")
    void theFourCasesOfAV16DatabaseAreClassifiedByTheW45Policy() throws Exception {
        Path database = directory.resolve("legacy.sqlite");
        writeV16Database(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            Map<String, ObservationDisposition> dispositions = store.caseDispositions(WORKSPACE);

            assertThat(dispositions)
                    .withFailMessage("(a) a disagreement with an UNSETTLED prediction is "
                            + "ordinary learning under W45, not a conflict")
                    .containsEntry("case-unsettled", ObservationDisposition.ELIGIBLE);

            assertThat(dispositions)
                    .withFailMessage("(b) a disagreement with a UNANIMOUS prediction contradicts "
                            + "a belief and is held")
                    .containsEntry("case-unanimous", ObservationDisposition.UNRESOLVED);

            assertThat(dispositions)
                    .withFailMessage("(c) an observation no hypothesis explained is learning "
                            + "under W45: there was no belief to disagree with")
                    .containsEntry("case-unexplained", ObservationDisposition.ELIGIBLE);

            assertThat(dispositions)
                    .withFailMessage("(d) an ordinary supporting observation is model data")
                    .containsEntry("case-supporting", ObservationDisposition.ELIGIBLE);
        }
    }

    @Test
    @DisplayName("a case that cannot be classified is held")
    void aCaseThatCannotBeClassifiedIsHeld() throws Exception {
        Path database = directory.resolve("unclassifiable.sqlite");
        writeV16Database(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            Map<String, ObservationDisposition> dispositions = store.caseDispositions(WORKSPACE);
            // No evidence link at all: nothing can be said about the observation.
            assertThat(dispositions).containsEntry("case-orphan", ObservationDisposition.UNRESOLVED);
            // A recorded conflict that no error row explains: the cause is not established.
            assertThat(dispositions)
                    .containsEntry("case-unexplained-conflict", ObservationDisposition.UNRESOLVED);
        }
    }

    @Test
    @DisplayName("the backfilled dispositions and the history agree")
    void theBackfilledDispositionsAndTheHistoryAgree() throws Exception {
        Path database = directory.resolve("legacy.sqlite");
        writeV16Database(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            // Every disposition of the legacy database has a row in the history, and the
            // projection is exactly what the history says.
            List<ObservationAdjudication> history = store.adjudications(WORKSPACE);
            assertThat(history).hasSize(6);
            assertThat(history).allSatisfy(decision ->
                    assertThat(decision.previousDisposition()).isNull());
            assertThat(store.rebuildDispositionProjection(WORKSPACE)).isTrue();
        }
    }

    @Test
    @DisplayName("a knowledge database without the cognitive tables falls back to the relation")
    void aKnowledgeDatabaseWithoutTheCognitiveTablesFallsBackToTheRelation() throws Exception {
        Path database = directory.resolve("knowledge-only.sqlite");
        writeV16KnowledgeOnlyDatabase(database);

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            Map<String, ObservationDisposition> dispositions = store.caseDispositions(WORKSPACE);
            // The relation is all there is to read, and it is read conservatively.
            assertThat(dispositions)
                    .containsEntry("case-unsettled", ObservationDisposition.ELIGIBLE)
                    .containsEntry("case-unanimous", ObservationDisposition.UNRESOLVED)
                    .containsEntry("case-unexplained", ObservationDisposition.UNRESOLVED)
                    .containsEntry("case-supporting", ObservationDisposition.ELIGIBLE);
        }
    }

    @Test
    @DisplayName("a database rewound to v16 is migrated and its dispositions are backfilled")
    void aDatabaseRewoundToV16IsMigratedAndItsDispositionsAreBackfilled() throws Exception {
        Path database = directory.resolve("rewound.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.recordObservationAtomically(WORKSPACE,
                    new Source("src-1", WORKSPACE, SourceKind.OBSERVATION, "an example",
                            "power=1,switch=1", 40, 60, "the lamp was on", INSTANT, "hash", null,
                            null, null),
                    new Evidence("evd-1", WORKSPACE, "clm-1", "src-1",
                            EvidenceRelation.EXEMPLIFIES, "the lamp was on", 50, INSTANT),
                    new Case("case-supported", WORKSPACE, "cpt-1", "the lamp was on",
                            "power=1,switch=1", "the lamp was on", "src-1", Case.Origin.OBSERVED,
                            INSTANT),
                    ObservationDisposition.ELIGIBLE, "ordinary model data", INSTANT);
        }

        // The database is rewound to the shape a W44 build left behind: the disposition table
        // does not exist yet, and the version says so.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("DROP TABLE observation_disposition");
            st.execute("UPDATE schema_meta SET value='16' WHERE key='version'");
        }

        try (KnowledgeStore upgraded = new KnowledgeStore(database)) {
            assertThat(upgraded.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat(upgraded.caseDispositions(WORKSPACE))
                    .containsEntry("case-supported", ObservationDisposition.ELIGIBLE);
        }
    }

    // ── the fixture ──────────────────────────────────────────────────────────

    /**
     * Writes a database in the shape the W44 wave left behind.
     *
     * <p>Both stores' tables are written by hand, so that nothing of this build's migration
     * chain can be mistaken for the database under test. The cognitive tables carry the
     * records the W44 kernel wrote: a prediction with the confidence it gave it, and an error
     * with the disposition it gave it.</p>
     */
    private void writeV16Database(Path database) throws Exception {
        writeKnowledgeTables(database);
        writeCognitiveTables(database);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            // (a) a disagreement with an UNSETTLED prediction. The W44 session held it, the
            // W44 kernel found a hypothesis that explains it, and it recorded no error.
            st.execute(prediction("prd-unsettled", 0.0));
            st.execute(sourceRow("src-unsettled"));
            st.execute(caseRow("case-unsettled", "src-unsettled", "power=1,switch=1"));
            st.execute(evidenceRow("evd-unsettled", "src-unsettled", "EXEMPLIFIES", "case-unsettled"));

            // (b) a disagreement with a UNANIMOUS prediction: a belief. The W44 kernel
            // recorded a conflict and an error whose disposition is UNRESOLVED.
            st.execute(prediction("prd-unanimous", 1.0));
            st.execute(sourceRow("src-unanimous"));
            st.execute(caseRow("case-unanimous", "src-unanimous", "power=0,switch=0"));
            st.execute(evidenceRow("evd-unanimous", "src-unanimous", "CONTRADICTS", "case-unanimous"));
            st.execute(errorRow("err-unanimous", "prd-unanimous", "UNRESOLVED", "case-unanimous"));

            // (c) an observation no hypothesis explained. The W44 session held it because it
            // disagreed with the unsettled prediction; the W44 kernel recorded a conflict and
            // an error whose disposition is MISSING_CONDITION.
            st.execute(prediction("prd-unexplained", 0.0));
            st.execute(sourceRow("src-unexplained"));
            st.execute(caseRow("case-unexplained", "src-unexplained", "power=0,switch=1"));
            st.execute(evidenceRow("evd-unexplained", "src-unexplained", "CONTRADICTS", "case-unexplained"));
            st.execute(errorRow("err-unexplained", "prd-unexplained", "MISSING_CONDITION",
                    "case-unexplained"));

            // (d) an ordinary supporting observation.
            st.execute(prediction("prd-supporting", 1.0));
            st.execute(sourceRow("src-supporting"));
            st.execute(caseRow("case-supporting", "src-supporting", "power=1,switch=1"));
            st.execute(evidenceRow("evd-supporting", "src-supporting", "EXEMPLIFIES", "case-supporting"));

            // A case with no evidence link at all, and a recorded conflict that no error
            // row explains.
            st.execute(sourceRow("src-orphan"));
            st.execute(caseRow("case-orphan", "src-orphan", "power=1,switch=0"));
            st.execute(sourceRow("src-conflict"));
            st.execute(caseRow("case-unexplained-conflict", "src-conflict", "power=0,switch=1"));
            st.execute(evidenceRow("evd-conflict", "src-conflict", "CONTRADICTS",
                    "case-unexplained-conflict"));
        }
    }

    /** Writes a database with the knowledge tables only — no cognitive store ever wrote it. */
    private void writeV16KnowledgeOnlyDatabase(Path database) throws Exception {
        writeKnowledgeTables(database);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute(sourceRow("src-unsettled"));
            st.execute(caseRow("case-unsettled", "src-unsettled", "power=1,switch=1"));
            st.execute(evidenceRow("evd-unsettled", "src-unsettled", "EXEMPLIFIES", "case-unsettled"));
            st.execute(sourceRow("src-unanimous"));
            st.execute(caseRow("case-unanimous", "src-unanimous", "power=0,switch=0"));
            st.execute(evidenceRow("evd-unanimous", "src-unanimous", "CONTRADICTS", "case-unanimous"));
            st.execute(sourceRow("src-unexplained"));
            st.execute(caseRow("case-unexplained", "src-unexplained", "power=0,switch=1"));
            st.execute(evidenceRow("evd-unexplained", "src-unexplained", "CONTRADICTS", "case-unexplained"));
            st.execute(sourceRow("src-supporting"));
            st.execute(caseRow("case-supporting", "src-supporting", "power=1,switch=1"));
            st.execute(evidenceRow("evd-supporting", "src-supporting", "EXEMPLIFIES", "case-supporting"));
        }
    }

    /** The knowledge tables of a v16 database. */
    private void writeKnowledgeTables(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT INTO schema_meta(key, value) VALUES('version', '16')");
            st.execute("CREATE TABLE workspace (id TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL, version INTEGER NOT NULL)");
            st.execute("CREATE TABLE source (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "kind TEXT NOT NULL, title TEXT NOT NULL, locator TEXT, authority INTEGER NOT NULL, "
                    + "freshness INTEGER NOT NULL, excerpt TEXT, added_at TEXT NOT NULL, "
                    + "content_hash TEXT, published_at TEXT, language TEXT, format TEXT)");
            st.execute("CREATE TABLE claim (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "concept_id TEXT NOT NULL, proposition TEXT NOT NULL, scope TEXT, context TEXT, "
                    + "status TEXT NOT NULL, confidence REAL NOT NULL, version INTEGER NOT NULL, "
                    + "introduced_by_source_id TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, "
                    + "warrant TEXT, derivation_status TEXT, extraction_method TEXT, "
                    + "validation_method TEXT)");
            st.execute("CREATE TABLE evidence (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "claim_id TEXT NOT NULL, source_id TEXT NOT NULL, relation TEXT NOT NULL, "
                    + "excerpt TEXT, quality INTEGER NOT NULL, recorded_at TEXT NOT NULL, case_id TEXT)");
            st.execute("CREATE TABLE epistemic_case (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "concept_id TEXT, statement TEXT NOT NULL, conditions TEXT NOT NULL, "
                    + "outcome TEXT NOT NULL, source_id TEXT, origin TEXT NOT NULL, observed_at TEXT NOT NULL)");
            st.execute("INSERT INTO workspace(id, name, created_at, version) VALUES('"
                    + WORKSPACE + "','the micro-world','" + INSTANT + "',1)");
            st.execute("INSERT INTO claim(id, workspace_id, concept_id, proposition, status, "
                    + "confidence, version, created_at, updated_at) VALUES('clm-model','"
                    + WORKSPACE + "','cpt-1','the lamp is on when power and switch are both on',"
                    + "'CANDIDATE',0.5,1,'" + INSTANT + "','" + INSTANT + "')");
        }
    }

    /** The cognitive tables of a v16 database, in the shape the W44 wave wrote them. */
    private void writeCognitiveTables(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE cognitive_schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT INTO cognitive_schema_meta(key, value) VALUES('version', '2')");
            st.execute("CREATE TABLE cognitive_episode (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "summary TEXT NOT NULL, started_at TEXT NOT NULL, closed_at TEXT)");
            st.execute("CREATE TABLE cognitive_prediction (id TEXT PRIMARY KEY, "
                    + "workspace_id TEXT NOT NULL, episode_id TEXT NOT NULL, subject TEXT NOT NULL, "
                    + "predicted_value TEXT NOT NULL, basis TEXT NOT NULL, basis_version TEXT NOT NULL, "
                    + "confidence REAL NOT NULL, observed_only INTEGER NOT NULL, "
                    + "case_ids TEXT NOT NULL, made_at TEXT NOT NULL)");
            st.execute("CREATE TABLE cognitive_error (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, "
                    + "prediction_id TEXT NOT NULL, expected TEXT NOT NULL, observed TEXT NOT NULL, "
                    + "magnitude REAL NOT NULL, disposition TEXT NOT NULL, responsible_model TEXT NOT NULL, "
                    + "responsible_version TEXT NOT NULL, case_ids TEXT NOT NULL, detected_at TEXT NOT NULL)");
            st.execute("INSERT INTO cognitive_episode(id, workspace_id, summary, started_at, closed_at) "
                    + "VALUES('ep-1','" + WORKSPACE + "','a cognitive cycle','" + INSTANT + "','"
                    + INSTANT + "')");
        }
    }

    /** A prediction row, with the confidence the W44 kernel gave it. */
    private String prediction(String id, double confidence) {
        return "INSERT INTO cognitive_prediction(id, workspace_id, episode_id, subject, "
                + "predicted_value, basis, basis_version, confidence, observed_only, case_ids, "
                + "made_at) VALUES('" + id + "','" + WORKSPACE + "','ep-1','lamp@power=1,switch=1',"
                + "'true','surviving-hypotheses','v1'," + confidence + ",0,'','" + INSTANT + "')";
    }

    /** A prediction error row, with the disposition the W44 kernel gave it. */
    private String errorRow(String id, String predictionId, String disposition, String caseId) {
        return "INSERT INTO cognitive_error(id, workspace_id, prediction_id, expected, observed, "
                + "magnitude, disposition, responsible_model, responsible_version, case_ids, "
                + "detected_at) VALUES('" + id + "','" + WORKSPACE + "','" + predictionId + "',"
                + "'true','false',1.0,'" + disposition + "','micro-world-hypothesis-set','v1','"
                + caseId + "','" + INSTANT + "')";
    }

    /** A case row. */
    private String caseRow(String caseId, String sourceId, String conditions) {
        return "INSERT INTO epistemic_case(id, workspace_id, concept_id, statement, conditions, "
                + "outcome, source_id, origin, observed_at) VALUES('" + caseId + "','" + WORKSPACE
                + "','cpt-1','the lamp was on','" + conditions + "','the lamp was on','" + sourceId
                + "','OBSERVED','" + INSTANT + "')";
    }

    /** The source an observation was made through. */
    private String sourceRow(String sourceId) {
        return "INSERT INTO source(id, workspace_id, kind, title, locator, authority, freshness, "
                + "excerpt, added_at) VALUES('" + sourceId + "','" + WORKSPACE + "','OBSERVATION',"
                + "'an example','power=1,switch=1',40,60,'the lamp was on','" + INSTANT + "')";
    }

    /** An evidence row, linked to its case. */
    private String evidenceRow(String evidenceId, String sourceId, String relation, String caseId) {
        return "INSERT INTO evidence(id, workspace_id, claim_id, source_id, relation, excerpt, "
                + "quality, recorded_at, case_id) VALUES('" + evidenceId + "','" + WORKSPACE
                + "','clm-model','" + sourceId + "','" + relation + "','the lamp was on',50,'"
                + INSTANT + "','" + caseId + "')";
    }
}
