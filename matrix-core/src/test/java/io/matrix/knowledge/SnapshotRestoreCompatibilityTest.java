package io.matrix.knowledge;

import io.matrix.cognitive.microworld.ObservationDisposition;
import io.matrix.neuron.HebbianUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W48-01 — the restore refuses a payload that cannot bring back what it would destroy.
 *
 * <p>The hazard, in one sentence: a restore erases the workspace and writes the payload
 * back, so a payload written by a build that did not capture a family the target holds
 * would DESTROY that family and cannot restore it — and the restore would report
 * success.</p>
 *
 * <p>Two payload shapes are older than the learning state: the version-1 payload (the
 * bare knowledge content) and the version-2 payload that carries the observation family
 * but not the learning family. Both are refused when the target holds rows of the
 * family they lack, and both are allowed when the target holds none — and the database
 * is byte-identical after the refusal.</p>
 */
class SnapshotRestoreCompatibilityTest {

    private static final String INSTANT = "2026-11-06T00:00:00Z";
    private static final String WORKSPACE = "demo";
    private static final String OTHER = "other";

    @TempDir
    Path directory;

    // ── 1 and 2: a legacy payload + a target with learning rows ────────────────

    @Test
    @DisplayName("a legacy payload is refused when the target holds learning rows")
    void aLegacyPayloadIsRefusedWhenTheTargetHoldsLearningRows() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordLearningState(store, WORKSPACE);
            writeLegacySnapshot(1);
            String before = fingerprint();

            String snapshotId = snapshotIds().get(0);
            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot(snapshotId, INSTANT))
                    .as("the restore must refuse before it destroys anything")
                    .isInstanceOf(IncompatibleSnapshotException.class)
                    .hasMessageContaining(IncompatibleSnapshotException.IDENTIFIER)
                    .hasMessageContaining("the learning state");

            assertThat(fingerprint())
                    .as("the refusal must leave the database exactly as it was")
                    .isEqualTo(before);
        }
    }

    @Test
    @DisplayName("a version-2 payload without the learning family is refused the same way")
    void aVersionTwoPayloadWithoutTheLearningFamilyIsRefused() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordLearningState(store, WORKSPACE);
            writeLegacySnapshot(2);
            String before = fingerprint();

            String snapshotId = snapshotIds().get(0);
            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot(snapshotId, INSTANT))
                    .isInstanceOf(IncompatibleSnapshotException.class)
                    .hasMessageContaining(IncompatibleSnapshotException.IDENTIFIER);

            assertThat(fingerprint()).isEqualTo(before);
        }
    }

    // ── 3: a legacy payload + a target WITHOUT the affected rows ───────────────

    @Test
    @DisplayName("a legacy payload restores a target that holds none of the missing rows")
    void aLegacyPayloadRestoresATargetThatHoldsNoneOfTheMissingRows() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            writeLegacySnapshot(1);

            String snapshotId = snapshotIds().get(0);
            // The restore is ALLOWED: the target holds none of the rows the
            // payload lacks, so nothing can be lost by it. What the payload
            // carried is what the workspace holds afterwards — and a legacy
            // payload carries no learning state and no observation history, so
            // the workspace's rows of those families are gone, as they would
            // be after any replacement.
            WorkspaceSnapshot restored = store.restoreWorkspaceSnapshot(snapshotId, INSTANT);

            assertThat(restored).isNotNull();
            assertThat(rows("learning_state")).isZero();
            assertThat(rows("epistemic_case")).isZero();
        }
    }

    @Test
    @DisplayName("a legacy payload does NOT restore an observation history it does not carry")
    void aLegacyPayloadDoesNotRestoreAnObservationHistoryItDoesNotCarry() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store);
            writeLegacySnapshot(1);
            String before = fingerprint();

            String snapshotId = snapshotIds().get(0);
            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot(snapshotId, INSTANT))
                    .isInstanceOf(IncompatibleSnapshotException.class)
                    .hasMessageContaining("the observation history");

            assertThat(fingerprint()).isEqualTo(before);
        }
    }

    // ── 4 and 5: a complete payload ───────────────────────────────────────────

    @Test
    @DisplayName("a complete payload restores all eight learning tables")
    void aCompletePayloadRestoresAllEightLearningTables() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordLearningState(store, WORKSPACE);
            recordObservation(store);
            WorkspaceSnapshot taken = store.takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, INSTANT);

            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), true, INSTANT);
            assertThat(rows("learning_state")).isZero();
            assertThat(rows("epistemic_case")).isZero();

            store.restoreWorkspaceSnapshot(taken.id(), INSTANT);

            assertThat(rows("learning_state")).isPositive();
            assertThat(rows("assessment_item")).isPositive();
            assertThat(rows("learning_error")).isPositive();
            assertThat(rows("assessment_outcome")).isPositive();
            assertThat(rows("learning_transition")).isPositive();
            assertThat(rows("dialogue_turn")).isPositive();
            assertThat(rows("asked_question")).isPositive();
            assertThat(rows("hebbian_binding")).isPositive();
        }
    }

    @Test
    @DisplayName("the restored observation history stays referentially consistent")
    void theRestoredObservationHistoryStaysReferentiallyConsistent() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store);
            review(store);
            WorkspaceSnapshot taken = store.takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, INSTANT);

            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), true, INSTANT);
            store.restoreWorkspaceSnapshot(taken.id(), INSTANT);

            assertThat(rows("epistemic_case")).isPositive();
            assertThat(rows("observation_occurrence")).isPositive();
            assertThat(rows("observation_disposition")).isPositive();
            assertThat(rows("observation_adjudication")).isPositive();
            assertThat(scalar("SELECT count(*) FROM evidence WHERE case_id IS NOT NULL "
                    + "AND case_id NOT IN (SELECT id FROM epistemic_case)")).isZero();
        }
    }

    // ── 7 and 8: the payloads this build cannot trust ─────────────────────────

    @Test
    @DisplayName("a payload from a newer build is refused")
    void aPayloadFromANewerBuildIsRefused() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            writePayloadWithVersion(99);
            String before = fingerprint();

            String snapshotId = snapshotIds().get(0);
            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot(snapshotId, INSTANT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("newer build");

            assertThat(fingerprint()).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("a corrupt payload is refused")
    void aCorruptPayloadIsRefused() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            writeRawSnapshot("{\"version\": 2, \"knowledge\": { this is not json");
            String before = fingerprint();

            String snapshotId = snapshotIds().get(0);
            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot(snapshotId, INSTANT))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(fingerprint()).isEqualTo(before);
        }
    }

    // ── 9: a failure during the restore ────────────────────────────────────────

    @Test
    @DisplayName("a failure during the restore leaves no partial replacement")
    void aFailureDuringTheRestoreLeavesNoPartialReplacement() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordLearningState(store, WORKSPACE);
            WorkspaceSnapshot taken = store.takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, INSTANT);
            String before = fingerprint();

            // The payload names a table this store does not capture: the insert
            // fails inside the transaction, and the transaction must roll back.
            writePayloadNamingAnUnknownTable();

            String snapshotId = snapshotIds().get(0);
            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot(snapshotId, INSTANT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not capture");

            assertThat(fingerprint())
                    .as("the failed restore must leave the workspace as it was")
                    .isEqualTo(before);
            assertThat(taken.id()).isNotBlank();
        }
    }

    // ── 10: the workspace boundary ─────────────────────────────────────────────

    @Test
    @DisplayName("a restore does not touch another workspace")
    void aRestoreDoesNotTouchAnotherWorkspace() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            store.importWorkspaceWithSnapshot(demoSnapshot(OTHER), false, INSTANT);
            recordLearningState(store, OTHER);
            int otherBefore = rows("learning_state", OTHER);
            WorkspaceSnapshot taken = store.takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, INSTANT);

            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), true, INSTANT);
            store.restoreWorkspaceSnapshot(taken.id(), INSTANT);

            assertThat(rows("learning_state", OTHER)).isEqualTo(otherBefore);
        }
    }

    // ── the inspection contract ────────────────────────────────────────────────

    @Test
    @DisplayName("a snapshot that holds only learning rows is not empty")
    void aSnapshotThatHoldsOnlyLearningRowsIsNotEmpty() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordLearningState(store, WORKSPACE);
            WorkspaceSnapshot taken = store.takeWorkspaceSnapshot(WORKSPACE, WorkspaceSnapshot.Kind.MANUAL, INSTANT);

            assertThat(taken.coverage()).isEqualTo(SnapshotCoverage.FULL);
            assertThat(taken.untypedCounts()).containsKey("learning_state");
            assertThat(taken.empty())
                    .as("a snapshot that carries learning rows is not empty")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a legacy snapshot says what it does not carry")
    void aLegacySnapshotSaysWhatItDoesNotCarry() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            writeLegacySnapshot(1);

            WorkspaceSnapshot legacy = store.workspaceSnapshots(WORKSPACE).get(0);
            assertThat(legacy.coverage()).isEqualTo(SnapshotCoverage.KNOWLEDGE_ONLY);
            assertThat(legacy.coverage().missingFamilies())
                    .contains("the learning state");
        }
    }

    // ── the fixtures ───────────────────────────────────────────────────────────

    private KnowledgeStore store() {
        return new KnowledgeStore(directory.resolve("knowledge.sqlite"));
    }

    private void recordObservation(KnowledgeStore store) {
        store.recordObservationAtomically(WORKSPACE,
                new Source("obs-src", WORKSPACE, SourceKind.OBSERVATION, "an example",
                        "power=1,switch=1", 40, 60, "the lamp was on", INSTANT, "hash",
                        null, null, null),
                new Evidence("obs-evd", WORKSPACE, "clm-demo", "obs-src",
                        EvidenceRelation.EXEMPLIFIES, "the lamp was on", 50, INSTANT),
                new Case("case-1", WORKSPACE, "cpt-demo", "the lamp was on",
                        "power=1,switch=1", "the lamp was on", "obs-src", Case.Origin.OBSERVED,
                        INSTANT),
                ObservationDisposition.ELIGIBLE, "the observation was recorded", INSTANT);
    }

    private void review(KnowledgeStore store) {
        store.adjudicateObservation(WORKSPACE, "case-1", ObservationDisposition.ACCEPTED,
                "the observation was reviewed and accepted", null, "the reviewer", INSTANT, null,
                INSTANT);
    }

    private void recordLearningState(KnowledgeStore store, String workspace) {
        String conceptId = "cpt-" + workspace;
        String itemId = "itm-" + workspace;
        String sessionId = "session-" + workspace;
        long answeredAt = 1_700_000_000_000L;

        AssessmentItem item = new AssessmentItem(itemId, conceptId, LearningDimension.RECALL,
                "What does photosynthesis do?", List.of("light", "sugar"), 3, "standard",
                true, false, List.of(), null);
        store.recordAssessmentItems(workspace, List.of(item));
        store.recordLearningErrors(workspace, List.of(new LearningError("err-" + workspace,
                workspace, itemId, conceptId, LearningDimension.RECALL,
                "it makes oxygen", "it makes sugar", true)));
        store.recordOutcome(workspace, item, new AssessmentOutcome(itemId, false, 0.25, 0.9,
                "it makes oxygen", sessionId, answeredAt, false));
        store.recordLearningStates(workspace,
                Map.of(conceptId, new LearningState(conceptId, LearningDimension.RECALL,
                        LearningStage.INITIAL_LEARNING, 0.4, 3, 0, 0, 1, List.of(itemId),
                        "the learner is learning")),
                List.of(new LearningTransition("trn-" + workspace, workspace, conceptId,
                        LearningDimension.RECALL, LearningStage.EXPLORATION,
                        LearningStage.INITIAL_LEARNING, "the recall test was answered",
                        workspace + "|" + itemId + "|" + sessionId + "|" + answeredAt, INSTANT)));
        store.recordDialogueTurn(workspace, new DialogueContext.Turn("turn-" + workspace,
                "what does photosynthesis do?", List.of(conceptId), List.of("Photosynthesis"),
                List.of("clm-" + workspace), "en", false, DialogueAct.Act.QUESTION));
        store.recordAskedQuestion(workspace, "what does photosynthesis do?",
                List.of("sugar"), false, answeredAt);
        store.saveHebbianState(workspace, conceptId, HebbianUpdater.empty());
    }

    private static KnowledgeSnapshot demoSnapshot(String workspace) {
        String conceptId = "cpt-" + workspace;
        String sourceId = "src-" + workspace;
        String claimId = "clm-" + workspace;
        return new KnowledgeSnapshot(workspace, "Demo workspace",
                List.of(new Concept(conceptId, workspace, "Photosynthesis",
                        "Conversion of light energy into chemical energy.", "biology", INSTANT)),
                List.of(new Source(sourceId, workspace, SourceKind.AUTHORITATIVE_REFERENCE,
                        "A textbook", "urn:isbn:978-0-00", 90, 70, "Plants photosynthesise.",
                        INSTANT, "hash-of-the-textbook-excerpt", null)),
                List.of(new Claim(claimId, workspace, conceptId,
                        "Plants use light to make sugars.", "In green plants", "general biology",
                        ClaimStatus.VERIFIED, 0.95, 1, sourceId, "transcribed from the excerpt",
                        "checked against the excerpt", ClaimWarrant.TESTIMONY,
                        DerivationStatus.NOT_DERIVED, INSTANT, INSTANT)),
                List.of(new Evidence("evd-" + workspace, workspace, claimId, sourceId,
                        EvidenceRelation.SUPPORTS, "Plants photosynthesise.", 90, INSTANT)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), INSTANT);
    }

    /** The ids of the snapshots the store holds, newest first. */
    private List<String> snapshotIds() {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("knowledge.sqlite"));
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery(
                     "SELECT id FROM workspace_snapshot WHERE workspace_id = '" + WORKSPACE
                             + "' ORDER BY created_at DESC, id DESC")) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
            return ids;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Writes a snapshot in an older payload shape. */
    private void writeLegacySnapshot(int version) throws SQLException {
        String knowledge = "{\"workspaceId\":\"" + WORKSPACE + "\",\"workspaceName\":\"Demo\","
                + "\"concepts\":[],\"sources\":[],\"claims\":[],\"evidence\":[],"
                + "\"representations\":[],\"prerequisites\":[],\"revisions\":[],"
                + "\"derivations\":[],\"contradictions\":[],\"goals\":[],\"exportedAt\":\"" + INSTANT + "\"}";
        String payload;
        if (version == 1) {
            payload = knowledge;
        } else {
            // A version-2 payload that carries the observation family but not the
            // learning family: the shape this build had before OD-036.
            payload = "{\"version\":2,\"knowledge\":" + knowledge + ",\"tables\":["
                    + "{\"table\":\"epistemic_case\",\"rows\":[]},"
                    + "{\"table\":\"observation_occurrence\",\"rows\":[]},"
                    + "{\"table\":\"observation_disposition\",\"rows\":[]},"
                    + "{\"table\":\"observation_adjudication\",\"rows\":[]}]}";
        }
        writeRawSnapshot(payload);
    }

    private void writePayloadWithVersion(int version) throws SQLException {
        writeRawSnapshot("{\"version\":" + version + ",\"knowledge\":{\"workspaceId\":\"" + WORKSPACE
                + "\",\"workspaceName\":\"Demo\",\"concepts\":[],\"sources\":[],\"claims\":[],"
                + "\"evidence\":[],\"representations\":[],\"prerequisites\":[],\"revisions\":[],"
                + "\"derivations\":[],\"contradictions\":[],\"goals\":[],\"exportedAt\":\"" + INSTANT
                + "\"},\"tables\":[]}");
    }

    /**
     * A payload with the COMPLETE coverage and one table the store does not capture.
     *
     * <p>The coverage has to be complete, or the restore is refused before it starts —
     * and this test is about a failure DURING the restore, not about the refusal.</p>
     */
    private void writePayloadNamingAnUnknownTable() throws SQLException {
        StringBuilder tables = new StringBuilder();
        for (String table : SnapshotCoverage.OBSERVATION_TABLES) {
            tables.append("{\"table\":\"").append(table).append("\",\"rows\":[]},");
        }
        for (String table : SnapshotCoverage.LEARNING_TABLES) {
            tables.append("{\"table\":\"").append(table).append("\",\"rows\":[]},");
        }
        tables.append("{\"table\":\"no_such_table\",\"rows\":[]}");
        writeRawSnapshot("{\"version\":2,\"knowledge\":{\"workspaceId\":\"" + WORKSPACE
                + "\",\"workspaceName\":\"Demo\",\"concepts\":[],\"sources\":[],\"claims\":[],"
                + "\"evidence\":[],\"representations\":[],\"prerequisites\":[],\"revisions\":[],"
                + "\"derivations\":[],\"contradictions\":[],\"goals\":[],\"exportedAt\":\"" + INSTANT
                + "\"},\"tables\":[" + tables + "]}");
    }

    private void writeRawSnapshot(String payload) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("knowledge.sqlite"));
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM workspace_snapshot WHERE workspace_id = '"
                    + WORKSPACE + "'");
            statement.executeUpdate("INSERT INTO workspace_snapshot(id, workspace_id, created_at, "
                    + "kind, payload) VALUES ('snap-legacy-" + WORKSPACE + "', '" + WORKSPACE + "', '"
                    + INSTANT + "', 'LOAD', '" + payload.replace("'", "''") + "')");
        }
    }

    /** How many rows of a table the workspace holds. */
    private int rows(String table) {
        return rows(table, WORKSPACE);
    }

    private int rows(String table, String workspace) {
        return scalar("SELECT count(*) FROM " + table + " WHERE workspace_id = '" + workspace + "'");
    }

    private int scalar(String sql) {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("knowledge.sqlite"));
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A fingerprint of the whole database: every table, every row, in a stable order.
     *
     * <p>It is what proves that a refused restore changed nothing — not "the counts are
     * the same", but the bytes of every row of every table.</p>
     */
    private String fingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("knowledge.sqlite"));
             Statement statement = connection.createStatement()) {
            var tables = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
            while (tables.next()) {
                String table = tables.getString(1);
                fingerprint.append("== ").append(table).append('\n');
                var rows = statement.executeQuery("SELECT * FROM " + table);
                int columns = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    for (int column = 1; column <= columns; column++) {
                        fingerprint.append(rows.getMetaData().getColumnLabel(column)).append('=')
                                .append(rows.getObject(column)).append('|');
                    }
                    fingerprint.append('\n');
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return fingerprint.toString();
    }
}
