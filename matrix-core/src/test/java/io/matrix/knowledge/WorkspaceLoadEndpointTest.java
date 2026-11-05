package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.core.Response;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W45.2 — the load endpoint fails closed, and the restore brings a workspace back.
 *
 * <p>The owner's decision names the endpoint: {@code POST /v1/workbench/workspaces/{id}/load}
 * must refuse to replace a workspace that holds something unless the caller asks for it, and
 * it must leave a recoverable snapshot. This test drives the resource the way the HTTP layer
 * does — through the method and the query parameter — and checks what a caller is told.</p>
 *
 * <p>The refusal has to be actionable: a caller that is told only "no" has to guess what to
 * send, and a caller that guesses wrong erases the workspace anyway.</p>
 */
class WorkspaceLoadEndpointTest {

    /** The workspace the endpoint is asked about. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

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
    @DisplayName("a load into an empty workspace works without the flag")
    void aLoadIntoAnEmptyWorkspaceWorksWithoutTheFlag() {
        Response response = resource.load(WORKSPACE, false, false, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry("workspace", WORKSPACE);
        assertThat(body).containsEntry("replaced", false);
        assertThat(body).containsEntry("snapshotId", null);
        assertThat(workbench.hasWorkspace(WORKSPACE)).isTrue();
    }

    @Test
    @DisplayName("a load into a non-empty workspace without the flag is refused and says what to send")
    void aLoadIntoANonEmptyWorkspaceWithoutTheFlagIsRefused() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, false, false, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry("refused", true);
        assertThat(body).containsEntry("replaceParameter", "replace");
        assertThat((String) body.get("error"))
                .withFailMessage("the refusal must name what the caller has to send")
                .contains("replace=true");
    }

    @Test
    @DisplayName("a load with the flag replaces the workspace and reports the snapshot")
    void aLoadWithTheFlagReplacesTheWorkspaceAndReportsTheSnapshot() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, true, false, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry("replaced", true);
        assertThat((String) body.get("snapshotId"))
                .withFailMessage("the caller must be able to find the snapshot it can restore")
                .isNotBlank();
        assertThat(workbench.workspaceSnapshots(WORKSPACE)).hasSize(1);
    }

    @Test
    @DisplayName("a load of a workspace with no bundled package is not found")
    void aLoadOfAWorkspaceWithNoBundledPackageIsNotFound() {
        Response response = resource.load("no-such-workspace", false, false, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    @DisplayName("a restore brings the workspace back and reports the snapshot it replaced")
    void aRestoreBringsTheWorkspaceBack() {
        resource.load(WORKSPACE, false, false, null);
        Response replaced = resource.load(WORKSPACE, true, false, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) replaced.getEntity();
        String snapshotId = (String) body.get("snapshotId");
        assertThat(snapshotId).isNotBlank();

        Response restored = resource.restore(WORKSPACE, snapshotId);

        assertThat(restored.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> restoredBody = (Map<String, Object>) restored.getEntity();
        assertThat(restoredBody).containsEntry("workspace", WORKSPACE);
        assertThat(restoredBody).containsEntry("restoredFrom", snapshotId);
        assertThat((Integer) restoredBody.get("concepts")).isPositive();
        assertThat(workbench.workspace(WORKSPACE).concepts()).isNotEmpty();
    }

    @Test
    @DisplayName("a restore of a snapshot that does not exist is not found")
    void aRestoreOfASnapshotThatDoesNotExistIsNotFound() {
        Response response = resource.restore(WORKSPACE, "snap-does-not-exist-1");

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    @DisplayName("the legacy in-process load asks before it replaces")
    void theLegacyInProcessLoadAsksBeforeItReplaces() {
        // The in-process entry point that the pre-W45.2 callers use asks too, since W47: it
        // is the door through which a workspace's history could be destroyed without the
        // caller ever being asked. The caller that means the replacement says so.
        workbench.loadBundledPackage(WORKSPACE);

        assertThatThrownBy(() -> workbench.loadBundledPackage(WORKSPACE))
                .withFailMessage("the in-process door must fail closed like the HTTP door")
                .isInstanceOf(WorkspaceReplacementRefused.class);
        assertThat(workbench.workspaceSnapshots(WORKSPACE))
                .withFailMessage("a refused load takes no snapshot: it changed nothing")
                .isEmpty();
    }

    // ── W47: two ways to consent, and the preview ─────────────────────────────────

    @Test
    @DisplayName("the body field replaceExisting consents exactly like the query parameter")
    void theBodyFieldReplaceExistingConsentsExactlyLikeTheQueryParameter() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, false, false, Map.of("replaceExisting", true));

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry("replaced", true);
        assertThat((String) body.get("snapshotId")).isNotBlank();
        assertThat(workbench.workspaceSnapshots(WORKSPACE)).hasSize(1);
    }

    @Test
    @DisplayName("both consents together are still one consent")
    void bothConsentsTogetherAreStillOneConsent() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, true, false, Map.of("replaceExisting", true));

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(workbench.workspaceSnapshots(WORKSPACE)).hasSize(1);
    }

    @Test
    @DisplayName("a body without the consent field does not consent")
    void aBodyWithoutTheConsentFieldDoesNotConsent() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, false, false, Map.of("somethingElse", true));

        assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
        assertThat(workbench.workspaceSnapshots(WORKSPACE)).isEmpty();
    }

    @Test
    @DisplayName("a consent that is not a boolean is refused")
    void aConsentThatIsNotABooleanIsRefused() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, false, false, Map.of("replaceExisting", "yes"));

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(workbench.workspaceSnapshots(WORKSPACE)).isEmpty();
    }

    @Test
    @DisplayName("a dry run reports what would be replaced and replaces nothing")
    void aDryRunReportsWhatWouldBeReplacedAndReplacesNothing() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, false, true, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry("dryRun", true);
        assertThat(body).containsEntry("wouldReplace", true);
        assertThat((Integer) body.get("totalRecords")).isPositive();
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) body.get("counts");
        assertThat(counts).containsKey("concept");
        assertThat(workbench.workspaceSnapshots(WORKSPACE))
                .withFailMessage("a dry run takes no snapshot: it changes nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("a dry run and a consent at once are refused")
    void aDryRunAndAConsentAtOnceAreRefused() {
        resource.load(WORKSPACE, false, false, null);

        Response response = resource.load(WORKSPACE, true, true, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(workbench.workspaceSnapshots(WORKSPACE)).isEmpty();
    }

    @Test
    @DisplayName("a dry run of an empty workspace says that there is nothing to replace")
    void aDryRunOfAnEmptyWorkspaceSaysThatThereIsNothingToReplace() {
        Response response = resource.load(WORKSPACE, false, true, null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertThat(body).containsEntry("wouldReplace", false);
        assertThat((Integer) body.get("totalRecords")).isZero();
    }
}
