package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W48-01/§4.4 — the HTTP surface of the refusal.
 *
 * <p>The store refuses to restore a payload that cannot bring back what the restore would
 * destroy, and it refuses BEFORE anything is destroyed. The directive also asks that the
 * <em>API response</em> identify an incomplete legacy snapshot — a caller that receives an
 * opaque failure cannot tell "this snapshot is too old" from "the server is broken", and
 * cannot tell a refusal from a not-found.</p>
 *
 * <p>This test drives the resource the way the HTTP layer does — through the method — and
 * checks what the caller is told: the status, the stable identifier, what the payload
 * carries, and what it does not.</p>
 */
class SnapshotRestoreEndpointTest {

    /** The workspace the endpoint is asked about. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The id of the legacy snapshot the fixture writes. */
    private static final String LEGACY_SNAPSHOT_ID = "snap-legacy-" + WORKSPACE;

    /** A fixed instant, so the fixture does not depend on the clock. */
    private static final String INSTANT = "2026-11-06T00:00:00Z";

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private LearningWorkbenchResource resource;

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(directory.resolve("workbench.sqlite").toString());
        resource = new LearningWorkbenchResource();
        resource.workbench = workbench;
    }

    @AfterEach
    void tearDown() {
        if (workbench != null) {
            workbench.close();
        }
    }

    @Test
    @DisplayName("the endpoint refuses a legacy snapshot and says WHICH refusal it is")
    void theEndpointRefusesALegacySnapshotAndSaysWhichRefusalItIs() throws Exception {
        // A workspace with knowledge and, on top of it, learning state.
        workbench.loadBundledPackage(WORKSPACE);
        seedOneLearningRow();
        // A snapshot whose payload is the version-1 shape: knowledge only, no learning family.
        writeLegacySnapshot();

        Response response = resource.restore(WORKSPACE, LEGACY_SNAPSHOT_ID);

        assertThat(response.getStatus())
                .as("a refused restore is a conflict, not a server failure")
                .isEqualTo(Response.Status.CONFLICT.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body)
                .as("the caller must be able to match on the stable identifier")
                .containsEntry("identifier", IncompatibleSnapshotException.IDENTIFIER)
                .containsEntry("snapshotId", LEGACY_SNAPSHOT_ID)
                .containsKey("coverage")
                .containsKey("missingFamilies");
        assertThat(String.valueOf(body.get("error")))
                .as("the message itself carries the identifier")
                .contains(IncompatibleSnapshotException.IDENTIFIER);
        assertThat(workbench.workspace(WORKSPACE).concepts())
                .as("the refusal must leave the workspace exactly as it was")
                .isNotEmpty();
    }

    /**
     * Seeds one row of the learning family.
     *
     * <p>The refusal triggers on the TARGET holding rows the payload cannot bring back, and
     * the store's own API records learning state through six different calls. One row of one
     * learning table is the same fact for the purpose of this test, and it is written here
     * as SQL so the test states its fixture instead of hiding it behind a helper.</p>
     */
    private void seedOneLearningRow() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("workbench.sqlite"));
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO learning_state(id, workspace_id, concept_id, "
                    + "dimension, stage, score, evidence_count, transitioned_at, note) VALUES "
                    + "('ls-" + WORKSPACE + "', '" + WORKSPACE + "', 'cpt-" + WORKSPACE + "', "
                    + "'RECALL', 'INITIAL_LEARNING', 0.4, 1, '" + INSTANT + "', 'seeded')");
        }
    }

    /**
     * Writes a snapshot whose payload is the version-1 shape.
     *
     * <p>It carries the knowledge and none of the learning family — the payload a build
     * from before OD-036 would have written.</p>
     */
    private void writeLegacySnapshot() throws Exception {
        String knowledge = "{\"workspaceId\":\"" + WORKSPACE + "\",\"workspaceName\":\"Demo\","
                + "\"concepts\":[],\"sources\":[],\"claims\":[],\"evidence\":[],"
                + "\"representations\":[],\"prerequisites\":[],\"revisions\":[],"
                + "\"derivations\":[],\"contradictions\":[],\"goals\":[],\"exportedAt\":\""
                + INSTANT + "\"}";
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("workbench.sqlite"));
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM workspace_snapshot WHERE workspace_id = '"
                    + WORKSPACE + "'");
            statement.executeUpdate("INSERT INTO workspace_snapshot(id, workspace_id, created_at, "
                    + "kind, payload) VALUES ('" + LEGACY_SNAPSHOT_ID + "', '" + WORKSPACE + "', '"
                    + INSTANT + "', 'LOAD', '" + knowledge.replace("'", "''") + "')");
        }
    }
}
