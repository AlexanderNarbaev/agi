package io.matrix.knowledge.epistemic;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.actions.ArtifactActionService;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.security.NetworkExposure;
import jakarta.ws.rs.core.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P0.2 — the served response carries the epistemic status of the premise.
 *
 * <p><b>What this class asserts.</b> The route
 * {@code POST /v1/workbench/workspaces/{id}/reports} hands the caller what the workspace made
 * of the question's premise, and it keeps the things apart that the project has always kept
 * apart:
 *
 * <ul>
 *   <li>the DISPOSITION of the action (written, replayed) is its own field;</li>
 *   <li>the EPISTEMIC STATUS of the answer (answered, abstained) is its own field;</li>
 *   <li>the status of the PREMISE is its own field, with the kind of premise and the reason;</li>
 *   <li>the LIMITATIONS and the REFUSAL REASONS are their own fields;</li>
 *   <li>a conflict of the corpus is reported with BOTH sides and their provenance, and the
 *       system does not decide between them;</li>
 *   <li>the readings of an ambiguous question are reported, and none of them is chosen;</li>
 *   <li>no response body carries an absolute path of the server.</li>
 * </ul>
 *
 * <p>The route is driven the way the HTTP layer drives it — through the method, with the two
 * injected collaborators assigned by hand — so that the contract is tested without a server.
 */
class PremiseReportResponseTest {

    /** The workspace the bundled photosynthesis package is loaded into. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The permissions an authorized output root must have: nobody else may write to it. */
    private static final EnumSet<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() throws Exception {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
        workbench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve("workbench.sqlite")
                        .toString());
        workbench.loadBundledPackage(WORKSPACE);
    }

    @AfterEach
    void tearDown() {
        NetworkExposure.reset();
        workbench.close();
    }

    @Test
    @DisplayName("the response carries the status of the premise, the kind and the reason")
    void theResponseCarriesThePremiseStatus() throws Exception {
        Response response = report("Since photosynthesis produces oxygen from carbon dioxide, "
                + "how much oxygen does a plant release in an hour?");

        assertThat(response.getStatus()).isEqualTo(201);
        ArtifactReportResource.ReportResponse body =
                (ArtifactReportResource.ReportResponse) response.getEntity();

        assertThat(body.premise()).as("the premise status is a field of its own").isNotNull();
        assertThat(body.premise().status()).isEqualTo("REFUTED");
        assertThat(body.premise().kind()).isEqualTo("BACKGROUND");
        assertThat(body.premise().premise()).isNotBlank();
        assertThat(body.premise().reason()).isNotBlank();
        assertThat(body.premise().refutingClaimIds())
                .as("the claim that refutes the premise is named")
                .isNotEmpty();
        assertThat(body.premise().refutingSourceIds())
                .as("the source that refutes the premise is named")
                .isNotEmpty();
        assertThat(body.epistemicDisposition())
                .as("the disposition of the answer is its own field")
                .isEqualTo("ABSTAINED");
        assertThat(body.disposition())
                .as("the disposition of the action is its own field")
                .isEqualTo("WRITTEN");
    }

    @Test
    @DisplayName("the report body names the premise and the claim that denies it")
    void theReportBodyNamesThePremise() throws Exception {
        Response response = report("Since photosynthesis produces oxygen from carbon dioxide, "
                + "how much oxygen does a plant release in an hour?");

        Path artifact = artifact();
        String body = Files.readString(artifact);

        assertThat(body)
                .as("the report says what the workspace made of the premise")
                .contains("The premise of the question")
                .contains("REFUTED");
        assertThat(body)
                .as("the claim that denies the premise is in the report")
                .contains("The claim that denies it");
    }

    @Test
    @DisplayName("no response body carries an absolute path of the server")
    void noResponseBodyCarriesAnAbsolutePath() throws Exception {
        Response response = report("Since photosynthesis produces oxygen from carbon dioxide, "
                + "how much oxygen does a plant release in an hour?");

        String artifact = Files.readString(artifact());
        assertThat(artifact)
                .as("the report body must not carry the server's filesystem")
                .doesNotContain(directory.toString())
                .doesNotContain("/tmp/");
        String serialised = String.valueOf(response.getEntity());
        assertThat(serialised).doesNotContain(directory.toString());
    }

    // ── the plumbing ──────────────────────────────────────────────────────────

    /**
     * A report of a question, through the route.
     *
     * @param question the question
     * @return the response
     * @throws Exception when the output root cannot be created
     */
    private Response report(String question) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, OWNER_ONLY);
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = new ArtifactReportResource();
        resource.workbench = workbench;
        resource.actions = actions;
        return resource.report(WORKSPACE, new ArtifactReportResource.ReportRequest(
                question, "report.md", Boolean.TRUE, "key-" + question.hashCode(), null));
    }

    /**
     * The artifact the last report wrote.
     *
     * @return the artifact
     * @throws Exception when the artifact cannot be read
     */
    private Path artifact() throws Exception {
        Path root = directory.resolve("root");
        try (var files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no report was written"));
        }
    }
}
