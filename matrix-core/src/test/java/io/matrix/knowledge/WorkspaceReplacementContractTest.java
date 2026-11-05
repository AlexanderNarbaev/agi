package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matrix.cognitive.microworld.ObservationDisposition;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W47 — the contract of a workspace replacement.
 *
 * <p><b>What this test holds the store to.</b> The owner's decision of 2026-11-05 (OD-025)
 * says that a replacement erases the epistemic case together with its decision history and
 * its evidence. Before that decision the erasure set predated the observation tables, so a
 * replacement left a case and a disposition whose evidence rows were gone: a workspace that
 * claimed to hold a judgement, with nothing to judge.</p>
 *
 * <p><b>What is proved here.</b> That the case and its history go, that they go together
 * with their evidence, that the snapshot carries them so that the destruction stays
 * recoverable, that a dry run changes nothing at all, that the consent can be given in
 * either of the two approved forms, and that the in-process door that used to replace
 * without asking now asks too.</p>
 *
 * <p><b>Every destructive test runs on its own temporary database.</b> The live workspace
 * under {@code data/} is never opened by this test.</p>
 */
class WorkspaceReplacementContractTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-05T00:00:00Z";

    /** The workspace the records live in. */
    private static final String WORKSPACE = "demo";

    /** A second workspace, which a replacement must not touch. */
    private static final String OTHER_WORKSPACE = "other";

    /** The identifier of the case the observation produces. */
    private static final String CASE_ID = "case-1";

    @TempDir
    Path directory;

    // ── the erasure set ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a workspace that holds only a case is not empty")
    void aWorkspaceThatHoldsOnlyACaseIsNotEmpty() {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);

            assertThat(store.isWorkspaceEmpty(WORKSPACE))
                    .withFailMessage("a workspace that holds a case holds something: the "
                            + "emptiness check decides whether the caller has to consent, and a "
                            + "check that counts the wrong tables lets the replacement destroy "
                            + "the case history without asking")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a replacement erases the case, its history and its evidence")
    void aReplacementErasesTheCaseItsHistoryAndItsEvidence() {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);

            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), true, INSTANT);

            assertThat(rows("epistemic_case")).isZero();
            assertThat(rows("observation_occurrence")).isZero();
            assertThat(rows("observation_disposition")).isZero();
            assertThat(rows("observation_adjudication")).isZero();
            assertThat(rows("evidence"))
                    .withFailMessage("the observation's evidence is gone; the one that is left "
                            + "belongs to the content that replaced it")
                    .isEqualTo(1);
            assertThat(rowsWhere("evidence", "id = 'obs-evd-demo'"))
                    .withFailMessage("the evidence of the erased observation must not survive "
                            + "it")
                    .isZero();
            assertThatNoDanglingReference();
        }
    }

    @Test
    @DisplayName("a replacement leaves no epistemic reference dangling")
    void aReplacementLeavesNoEpistemicReferenceDangling() {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);
            review(store);

            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), true, INSTANT);

            assertThatNoDanglingReference();
            assertThat(rows("workspace_snapshot"))
                    .withFailMessage("the snapshot is the recoverable copy: it must survive "
                            + "the replacement that destroyed what it holds")
                    .isPositive();
        }
    }

    @Test
    @DisplayName("a replacement does not touch another workspace")
    void aReplacementDoesNotTouchAnotherWorkspace() {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            store.importWorkspaceWithSnapshot(demoSnapshot(OTHER_WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);
            recordObservation(store, OTHER_WORKSPACE);

            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), true, INSTANT);

            assertThat(rows("epistemic_case", OTHER_WORKSPACE)).isPositive();
            assertThat(rows("observation_occurrence", OTHER_WORKSPACE)).isPositive();
            assertThat(rows("concept", OTHER_WORKSPACE)).isPositive();
        }
    }

    // ── the snapshot ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the snapshot carries the case history it is about to destroy")
    void theSnapshotCarriesTheCaseHistoryItIsAboutToDestroy() {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);
            review(store);
            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(
                    demoSnapshot(WORKSPACE), true, INSTANT);
            assertThat(taken).isNotNull();
            assertThat(taken.caseCount()).isEqualTo(1);
            assertThat(taken.occurrenceCount()).isEqualTo(1);
            assertThat(taken.dispositionCount()).isEqualTo(1);
            assertThat(taken.adjudicationCount())
                    .withFailMessage("the adjudication history is append-only: the observation "
                            + "wrote the first decision and the review wrote the second")
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("a restore brings the case history back")
    void aRestoreBringsTheCaseHistoryBack() {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);
            review(store);
            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(
                    demoSnapshot(WORKSPACE), true, INSTANT);

            store.restoreWorkspaceSnapshot(taken.id(), INSTANT);

            assertThat(rows("epistemic_case")).isEqualTo(1);
            assertThat(rows("observation_occurrence")).isEqualTo(1);
            assertThat(rows("observation_disposition")).isEqualTo(1);
            assertThat(rows("observation_adjudication"))
                    .withFailMessage("the whole adjudication history comes back, not only the "
                            + "decision that is projected")
                    .isEqualTo(2);
            assertThat(rows("evidence")).isPositive();
            assertThatNoDanglingReference();
        }
    }

    @Test
    @DisplayName("a snapshot taken before this wave still restores")
    void aSnapshotTakenBeforeThisWaveStillRestores() throws Exception {
        // The payload of a version 1 snapshot is the bare knowledge content. It must still
        // be readable: a snapshot is the only recoverable copy of what a replacement
        // destroyed, and a build that cannot read the old ones cannot honour that.
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            writeVersionOneSnapshot();
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), true, INSTANT);

            List<WorkspaceSnapshot> snapshots = store.workspaceSnapshots(WORKSPACE);
            assertThat(snapshots).isNotEmpty();
            WorkspaceSnapshot oldest = snapshots.get(snapshots.size() - 1);

            store.restoreWorkspaceSnapshot(oldest.id(), INSTANT);

            assertThat(rows("concept")).isPositive();
        }
    }

    // ── the preview ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a preview reports what would be erased and changes nothing")
    void aPreviewReportsWhatWouldBeErasedAndChangesNothing() throws Exception {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);
            String before = fingerprint();

            ReplacementPreview preview = store.previewWorkspaceReplacement(WORKSPACE);

            assertThat(preview.wouldReplace()).isTrue();
            assertThat(preview.totalRecords()).isPositive();
            assertThat(preview.counts()).containsKeys("epistemic_case", "observation_occurrence",
                    "observation_disposition", "observation_adjudication", "concept", "evidence");
            assertThat(preview.counts().get("epistemic_case")).isEqualTo(1);
            assertThat(preview.counts().get("observation_occurrence")).isEqualTo(1);
            assertThat(preview.caseIds()).containsExactly(CASE_ID);
            assertThat(fingerprint())
                    .withFailMessage("a preview must change nothing: not a row, not a snapshot, "
                            + "not a counter")
                    .isEqualTo(before);
        }
    }

    @Test
    @DisplayName("a preview of an empty workspace says that there is nothing to replace")
    void aPreviewOfAnEmptyWorkspaceSaysThatThereIsNothingToReplace() {
        try (KnowledgeStore store = store()) {
            ReplacementPreview preview = store.previewWorkspaceReplacement(WORKSPACE);

            assertThat(preview.wouldReplace()).isFalse();
            assertThat(preview.totalRecords()).isZero();
            assertThat(preview.caseIds()).isEmpty();
        }
    }

    // ── the consent ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a replacement without the consent is refused")
    void aReplacementWithoutTheConsentIsRefused() {
        try (KnowledgeStore store = store()) {
            store.importWorkspaceWithSnapshot(demoSnapshot(WORKSPACE), false, INSTANT);
            recordObservation(store, WORKSPACE);

            assertThatThrownBy(() -> store.importWorkspaceWithSnapshot(
                    demoSnapshot(WORKSPACE), false, INSTANT))
                    .isInstanceOf(WorkspaceReplacementRefused.class)
                    .withFailMessage("the refusal must come before anything is deleted")
                    .hasMessageContaining("replace=true");
            assertThat(rows("epistemic_case")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("the in-process door asks too")
    void theInProcessDoorAsksToo() {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(
                directory.resolve("workbench.sqlite").toString())) {
            workbench.loadBundledPackage(WORKSPACE);

            assertThatThrownBy(() -> workbench.loadBundledPackage(WORKSPACE))
                    .withFailMessage("the in-process door used to replace without asking; it "
                            + "is the door through which a history could be destroyed quietly")
                    .isInstanceOf(WorkspaceReplacementRefused.class);
            assertThat(workbench.workspaceSnapshots(WORKSPACE)).isEmpty();
        }
    }

    // ── the fixtures ─────────────────────────────────────────────────────────────

    /** The store under test. */
    private KnowledgeStore store() {
        return new KnowledgeStore(database());
    }

    /** The database file under test. */
    private Path database() {
        return directory.resolve("knowledge.sqlite");
    }

    /** A workspace with one concept, one claim and one source. */
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

    /** Records one observation of the canonical case, through the production path. */
    private void recordObservation(KnowledgeStore store, String workspace) {
        String sourceId = "obs-src-" + workspace;
        store.recordObservationAtomically(workspace,
                new Source(sourceId, workspace, SourceKind.OBSERVATION, "an example",
                        "power=1,switch=1", 40, 60, "the lamp was on", INSTANT, "hash",
                        null, null, null),
                new Evidence("obs-evd-" + workspace, workspace, "clm-" + workspace, sourceId,
                        EvidenceRelation.EXEMPLIFIES, "the lamp was on", 50, INSTANT),
                new Case(CASE_ID, workspace, "cpt-" + workspace, "the lamp was on",
                        "power=1,switch=1", "the lamp was on", sourceId, Case.Origin.OBSERVED,
                        INSTANT),
                ObservationDisposition.ELIGIBLE, "the observation was recorded", INSTANT);
    }

    /** Reviews the observation, so that the adjudication history holds a decision. */
    private void review(KnowledgeStore store) {
        store.adjudicateObservation(WORKSPACE, CASE_ID, ObservationDisposition.ACCEPTED,
                "the observation was reviewed and accepted", null, "the reviewer", INSTANT, null,
                INSTANT);
    }

    /**
     * Writes a snapshot in the shape this wave's predecessor wrote: the bare knowledge
     * content, with no version and no observation rows.
     */
    private void writeVersionOneSnapshot() throws SQLException {
        String payload = "{\"workspaceId\":\"" + WORKSPACE + "\",\"workspaceName\":\"Demo workspace\","
                + "\"concepts\":[],\"sources\":[],\"claims\":[],\"evidence\":[],"
                + "\"representations\":[],\"prerequisites\":[],\"revisions\":[],"
                + "\"derivations\":[],\"contradictions\":[],\"goals\":[],\"exportedAt\":\"" + INSTANT + "\"}";
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO workspace_snapshot(id, workspace_id, created_at, "
                    + "kind, payload) VALUES ('snap-v1-" + WORKSPACE + "', '" + WORKSPACE + "', '"
                    + INSTANT + "', 'LOAD', '" + payload + "')");
        }
    }

    // ── the inspection ───────────────────────────────────────────────────────────

    /** How many rows of a table the workspace holds. */
    private int rows(String table) {
        return rows(table, WORKSPACE);
    }

    /** How many rows of a table the workspace holds. */
    private int rows(String table, String workspace) {
        String sql = switch (table) {
            case "derivation_premise" -> "SELECT count(*) FROM derivation_premise WHERE derivation_id IN "
                    + "(SELECT id FROM derivation WHERE workspace_id = '" + workspace + "')";
            case "learning_goal_subtask" -> "SELECT count(*) FROM learning_goal_subtask WHERE goal_id IN "
                    + "(SELECT id FROM learning_goal WHERE workspace_id = '" + workspace + "')";
            default -> "SELECT count(*) FROM " + table + " WHERE workspace_id = '" + workspace + "'";
        };
        return scalar(sql);
    }

    /** How many rows of a table match a predicate. */
    private int rowsWhere(String table, String predicate) {
        return scalar("SELECT count(*) FROM " + table + " WHERE " + predicate);
    }

    /** The value of a single-cell query. */
    private int scalar(String sql) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read: " + sql, e);
        }
    }

    /**
     * The fingerprint of the whole database: every row of every table, in a stable order.
     *
     * <p>A dry run has to change nothing, and "nothing" is only provable by comparing the
     * whole content and not by counting a few tables.</p>
     */
    private String fingerprint() throws SQLException {
        StringBuilder fingerprint = new StringBuilder();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database());
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
            List<String> names = new ArrayList<>();
            while (tables.next()) {
                names.add(tables.getString(1));
            }
            for (String name : names) {
                fingerprint.append(name).append(':');
                try (ResultSet rows = statement.executeQuery(
                        "SELECT * FROM \"" + name + "\" ORDER BY 1")) {
                    ResultSetMetaData meta = rows.getMetaData();
                    int columns = meta.getColumnCount();
                    while (rows.next()) {
                        for (int column = 1; column <= columns; column++) {
                            fingerprint.append(meta.getColumnLabel(column)).append('=')
                                    .append(rows.getObject(column)).append(';');
                        }
                        fingerprint.append('\n');
                    }
                }
                fingerprint.append('\n');
            }
        }
        return fingerprint.toString();
    }

    /**
     * Asserts that no epistemic row points at a row that is not there.
     *
     * <p>The references of this schema are logical, not declared: SQLite has no foreign key
     * here, so a dangling reference is not an error the database would raise. It is a
     * silent inconsistency, and only a check like this one finds it.</p>
     */
    private void assertThatNoDanglingReference() {
        assertThat(scalar("SELECT count(*) FROM observation_occurrence o WHERE NOT EXISTS "
                + "(SELECT 1 FROM epistemic_case c WHERE c.id = o.case_id)"))
                .withFailMessage("an occurrence points at a case that is not there")
                .isZero();
        assertThat(scalar("SELECT count(*) FROM observation_occurrence o WHERE o.evidence_id IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM evidence e WHERE e.id = o.evidence_id)"))
                .withFailMessage("an occurrence points at evidence that is not there")
                .isZero();
        assertThat(scalar("SELECT count(*) FROM observation_disposition d WHERE NOT EXISTS "
                + "(SELECT 1 FROM epistemic_case c WHERE c.id = d.case_id)"))
                .withFailMessage("a disposition points at a case that is not there")
                .isZero();
        assertThat(scalar("SELECT count(*) FROM observation_adjudication a WHERE NOT EXISTS "
                + "(SELECT 1 FROM epistemic_case c WHERE c.id = a.case_id)"))
                .withFailMessage("an adjudication points at a case that is not there")
                .isZero();
        assertThat(scalar("SELECT count(*) FROM evidence e WHERE e.case_id IS NOT NULL AND NOT EXISTS "
                + "(SELECT 1 FROM epistemic_case c WHERE c.id = e.case_id)"))
                .withFailMessage("an evidence row points at a case that is not there")
                .isZero();
        assertThat(scalar("SELECT count(*) FROM epistemic_case c WHERE c.concept_id IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM concept k WHERE k.id = c.concept_id)"))
                .withFailMessage("a case points at a concept that is not there")
                .isZero();
    }
}
