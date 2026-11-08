package io.matrix.safety;

import io.matrix.cognitive.microworld.ObservationDisposition;
import io.matrix.knowledge.Case;
import io.matrix.knowledge.IncompatibleSnapshotException;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.ObservationConflictException;
import io.matrix.neuron.HebbianUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W51 §11 — the stores under failure: a payload that is older than the data it would replace,
 * and a record that is written twice with different content.
 *
 * <p><b>What this file is for.</b> §11 asks for a stale snapshot and a conflicting record to be
 * refused rather than applied. Both are cases where the DANGEROUS outcome is a success: a
 * restore that quietly drops a family of rows reports "done", and a second adjudication that
 * quietly overwrites the first reports "accepted". Neither may happen, and the way to show
 * that is to check the data afterwards, not the exception alone.</p>
 *
 * <p><b>Why the payload is written with SQL.</b> The store captures its own snapshots with the
 * full coverage, so there is no public way to produce a payload from an older build. The
 * legacy payload is therefore written into the snapshot table directly, which is exactly what
 * a database restored from an older backup looks like from the inside.</p>
 */
class StoreSafetyTest {

    /** The instant every record in this file carries. */
    private static final String AT = "2026-11-08T00:00:00Z";

    /** The workspace the refusals happen in. */
    private static final String WORKSPACE = "safety-workspace";

    /** The identity of the case the conflicting records are about. */
    private static final String CASE_ID = "case-conflicting";

    /** The concept whose Hebbian state the workspace holds. */
    private static final String CONCEPT_ID = "cpt-safety";

    /** The number of learning rows the workspace holds before the restore is attempted. */
    private static final int LEARNING_ROWS_BEFORE = 1;

    @TempDir
    Path directory;

    // ── §11.7 — the stale snapshot ────────────────────────────────────────────

    @Test
    @DisplayName("§11.7 — a payload that cannot bring back what it would destroy is refused and the data survives")
    void aPayloadThatCannotBringBackWhatItWouldDestroyIsRefused() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            // The workspace holds a learning row that the legacy payload does not carry.
            store.saveHebbianState(WORKSPACE, CONCEPT_ID, HebbianUpdater.empty());
            writeLegacySnapshot(database);

            assertThat(learningRows(database)).isEqualTo(LEARNING_ROWS_BEFORE);

            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot("snap-legacy", AT))
                    .as("the restore must refuse BEFORE it erases the workspace")
                    .isInstanceOf(IncompatibleSnapshotException.class)
                    .hasMessageContaining(IncompatibleSnapshotException.IDENTIFIER);

            // The refusal changed nothing: the learning row is still there.
            assertThat(learningRows(database)).isEqualTo(LEARNING_ROWS_BEFORE);
        }
    }

    @Test
    @DisplayName("§11.7 — a payload that carries the observation family but not the learning one is refused the same way")
    void aPayloadThatCarriesTheObservationFamilyButNotTheLearningOneIsRefused() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.saveHebbianState(WORKSPACE, CONCEPT_ID, HebbianUpdater.empty());
            writeObservationOnlySnapshot(database);

            assertThatThrownBy(() -> store.restoreWorkspaceSnapshot("snap-legacy", AT))
                    .isInstanceOf(IncompatibleSnapshotException.class)
                    .hasMessageContaining(IncompatibleSnapshotException.IDENTIFIER);

            assertThat(learningRows(database)).isEqualTo(LEARNING_ROWS_BEFORE);
        }
    }

    // ── §11.7 — the conflicting record ─────────────────────────────────────────

    @Test
    @DisplayName("§11.7 — a record written twice with different content is refused and the first decision stands")
    void aRecordWrittenTwiceWithDifferentContentIsRefused() {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.recordCase(observationCase());
            store.adjudicateObservation(WORKSPACE, CASE_ID, ObservationDisposition.ELIGIBLE,
                    "the observation was model data", null, "the reviewer", AT, null, AT);
            store.adjudicateObservation(WORKSPACE, CASE_ID, ObservationDisposition.REJECTED,
                    "the review rejected the observation", null, "the reviewer", AT, null, AT);

            // A third decision would move a REJECTED case back to ELIGIBLE, which only a
            // review may do — and the store refuses to pretend it happened by itself.
            assertThatThrownBy(() -> store.adjudicateObservation(WORKSPACE, CASE_ID,
                    ObservationDisposition.ELIGIBLE, "the observation is model data again",
                    null, "the reviewer", AT, null, AT))
                    .isInstanceOf(ObservationConflictException.class)
                    .hasMessageContaining("may not become");

            // The first decision stands: the refusal did not overwrite it.
            assertThat(store.caseDispositions(WORKSPACE)).containsEntry(CASE_ID,
                    ObservationDisposition.REJECTED);
        }
    }

    // ── the fixtures ──────────────────────────────────────────────────────────

    private static Case observationCase() {
        return new Case(CASE_ID, WORKSPACE, null, "the lamp was on", "power=1,switch=1",
                "the lamp was on", null, Case.Origin.OBSERVED, AT);
    }

    /** A payload in the shape of a build that predates the learning family. */
    private void writeLegacySnapshot(Path database) throws SQLException {
        writeSnapshot(database, "{\"workspaceId\":\"" + WORKSPACE + "\",\"workspaceName\":\"Safety\","
                + "\"concepts\":[],\"sources\":[],\"claims\":[],\"evidence\":[],"
                + "\"representations\":[],\"prerequisites\":[],\"revisions\":[],"
                + "\"derivations\":[],\"contradictions\":[],\"goals\":[],\"exportedAt\":\"" + AT + "\"}");
    }

    /** A payload that carries the observation family but not the learning family. */
    private void writeObservationOnlySnapshot(Path database) throws SQLException {
        writeSnapshot(database, "{\"version\":2,\"knowledge\":{\"workspaceId\":\"" + WORKSPACE
                + "\",\"workspaceName\":\"Safety\",\"concepts\":[],\"sources\":[],\"claims\":[],"
                + "\"evidence\":[],\"representations\":[],\"prerequisites\":[],\"revisions\":[],"
                + "\"derivations\":[],\"contradictions\":[],\"goals\":[],\"exportedAt\":\"" + AT
                + "\"},\"tables\":["
                + "{\"table\":\"epistemic_case\",\"rows\":[]},"
                + "{\"table\":\"observation_occurrence\",\"rows\":[]},"
                + "{\"table\":\"observation_disposition\",\"rows\":[]},"
                + "{\"table\":\"observation_adjudication\",\"rows\":[]}]}");
    }

    private void writeSnapshot(Path database, String payload) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM workspace_snapshot WHERE workspace_id = '"
                    + WORKSPACE + "'");
            statement.executeUpdate("INSERT INTO workspace_snapshot(id, workspace_id, created_at, "
                    + "kind, payload) VALUES ('snap-legacy', '" + WORKSPACE + "', '" + AT
                    + "', 'LOAD', '" + payload.replace("'", "''") + "')");
        }
    }

    /** How many rows of the learning family the workspace holds. */
    private int learningRows(Path database) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT count(*) FROM hebbian_binding WHERE "
                     + "workspace_id = '" + WORKSPACE + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
