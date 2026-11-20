package io.matrix.knowledge;

import io.matrix.actions.ArtifactActionService;
import io.matrix.actions.ArtifactWriter;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.security.NetworkExposure;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01.1 — the changed-payload idempotency contract, as an executable gate.
 *
 * <p><b>What this class is.</b> The acceptance contract asks for one thing that a source
 * reading cannot settle: under a REUSED idempotency key, a request whose payload changed
 * must never be answered with the earlier journey's success. The exact replay — the same
 * key with the same payload — must keep working, because replay is a feature and not a
 * defect.</p>
 *
 * <p><b>Why the question is real.</b> {@code ArtifactActionService.identity(...)} derives the
 * action identity from the idempotency key when the caller supplies one, and from the
 * question otherwise. The identity names one artifact, and the writer refuses a second,
 * different content under a committed identity ({@code ArtifactWriter}, the
 * {@code REFUSED_IDENTITY} branch). But the journey's REPLAY method serves the RETAINED
 * bytes of an earlier journey without asking the workspace again; those bytes carry the
 * earlier question's answer, and their digest matches the committed record, so the writer
 * sees a legitimate replay. The route then answers a NEW question with an OLD report.</p>
 *
 * <p><b>What this class asserts.</b> Two contracts, both asserted (this is a gate, not a
 * measurement probe — it can and must fail the build):</p>
 * <ul>
 *   <li>the exact replay stays correct: the same key and the same payload answers 200
 *       {@code REPLAYED}, carries the same digest and leaves one artifact in the root;</li>
 *   <li>the changed payload is never a stale success: under a reused key, a different
 *       question is either answered for THAT question — the served report must carry the
 *       new question and not the old one — or refused with an explicit conflict. Serving
 *       the earlier report as the answer to a different question is a failure, whatever
 *       the HTTP status says.</li>
 * </ul>
 *
 * <p><b>What this class does not claim.</b> It does not decide what the route SHOULD do
 * with a reused key: answering the new question under a new artifact and refusing with a
 * conflict are both acceptable outcomes, and the contract the owner accepts picks one.
 * What is not acceptable is the third outcome — the earlier report served as a success
 * for a question it does not answer.</p>
 */
class ReportIdempotencyConflictTest {

    /** The workspace the route is asked about; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The first question: the package answers it with cited claims. */
    private static final String FIRST_QUESTION = "What is photosynthesis?";

    /** The second question: a different question the same workspace can answer. */
    private static final String SECOND_QUESTION = "What is cellular respiration?";

    /** The idempotency key the two requests share. */
    private static final String SHARED_KEY = "key-reused-across-questions";

    /** The permissions an authorized output root must have: nobody else may write to it. */
    private static final EnumSet<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    @TempDir
    Path directory;

    /** The workbenches this test opened, so that every one of them is closed. */
    private final List<KnowledgeWorkbench> workbenches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // the gate's boundary depends on the exposure of the process; the tests state it
        // rather than inherit it from the environment
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
    }

    @AfterEach
    void tearDown() {
        NetworkExposure.reset();
        workbenches.forEach(KnowledgeWorkbench::close);
    }

    // ── the contract ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the exact replay of a request stays a correct replay")
    void theExactReplayStaysCorrect() throws Exception {
        Path root = root("replay");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("replay"), actions);

        Response first = resource.report(WORKSPACE, request(FIRST_QUESTION, "key-exact"));
        assertThat(first.getStatus()).isEqualTo(201);

        Response second = resource.report(WORKSPACE, request(FIRST_QUESTION, "key-exact"));

        assertThat(second.getStatus())
                .as("the repeat of an action that already happened is not a new creation")
                .isEqualTo(200);
        assertThat(success(second).disposition()).isEqualTo("REPLAYED");
        assertThat(success(second).sha256()).isEqualTo(success(first).sha256());
        assertThat(success(second).taskId()).isEqualTo(success(first).taskId());
        assertThat(artifactFiles(root)).as("the root holds exactly one artifact").hasSize(1);
    }

    @Test
    @DisplayName("a reused key with a different question is never answered with the earlier report")
    void aReusedKeyWithADifferentQuestionIsNeverAStaleSuccess() throws Exception {
        Path root = root("changed-question");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("changed-question"), actions);

        Response first = resource.report(WORKSPACE, request(FIRST_QUESTION, SHARED_KEY));
        assertThat(first.getStatus()).isEqualTo(201);
        String firstDigest = success(first).sha256();

        Response second = resource.report(WORKSPACE, request(SECOND_QUESTION, SHARED_KEY));

        if (second.getStatus() == 409) {
            // the route refused the conflict explicitly: the earlier journey keeps its
            // artifact and the caller is told that the key names a different payload
            assertThat(refusal(second)).containsEntry("error", "REFUSED_IDENTITY");
            assertThat(artifactFiles(root)).as("the root holds exactly one artifact").hasSize(1);
            return;
        }

        assertThat(second.getStatus())
                .as("a changed payload under a reused key is answered or refused, never a "
                        + "silent stale success")
                .isIn(200, 201);
        assertThat(success(second).sha256())
                .as("the answer to a different question is not the earlier journey's report")
                .isNotEqualTo(firstDigest);

        String served = Files.readString(root.resolve(success(second).artifactName()));
        assertThat(served)
                .as("the report the caller received answers the question the caller asked")
                .contains(SECOND_QUESTION)
                .doesNotContain(FIRST_QUESTION);
    }

    @Test
    @DisplayName("a reused key with a different report name does not serve the earlier report")
    void aReusedKeyWithADifferentReportNameIsNeverAStaleSuccess() throws Exception {
        Path root = root("changed-name");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("changed-name"), actions);

        Response first = resource.report(WORKSPACE,
                new ArtifactReportResource.ReportRequest(FIRST_QUESTION, "first.md", true,
                        "key-name", null));
        assertThat(first.getStatus()).isEqualTo(201);

        Response second = resource.report(WORKSPACE,
                new ArtifactReportResource.ReportRequest(FIRST_QUESTION, "second.md", true,
                        "key-name", null));

        assertThat(second.getStatus())
                .as("a changed payload under a reused key is answered or refused, never a "
                        + "silent stale success")
                .isIn(200, 201, 409);
        if (second.getStatus() != 409) {
            assertThat(success(second).artifactName())
                    .as("the answer names the artifact the caller asked for")
                    .isEqualTo("second.md");
            assertThat(artifactFiles(root))
                    .as("the earlier artifact keeps its name and the new one gets its own")
                    .containsExactly("first.md", "second.md");
            assertThat(Files.readString(root.resolve("second.md")))
                    .as("the new artifact answers the question the caller asked")
                    .contains("**Question.** " + FIRST_QUESTION);
        }
    }

    // ── the fixtures ────────────────────────────────────────────────────────

    /** A workbench over its own database, with the bundled package loaded. */
    private KnowledgeWorkbench workbench(String name) throws IOException {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve(name + ".sqlite")
                        .toString());
        bench.loadBundledPackage(WORKSPACE);
        workbenches.add(bench);
        return bench;
    }

    /** An authorized output root: a real directory that no other user may write to. */
    private Path root(String name) throws IOException {
        Path root = Files.createDirectories(directory.resolve(name));
        Files.setPosixFilePermissions(root, OWNER_ONLY);
        return root;
    }

    /**
     * The resource, wired by hand, the way the container wires it.
     *
     * <p>The planner is where the kernel chooses the journey's method and the cognitive
     * service owns the store the journey's trace — and therefore the retained journey — is
     * written into. The retained journey is what the replay method serves, so the route
     * under test is the one the container serves, trace included.</p>
     */
    private ArtifactReportResource resource(KnowledgeWorkbench bench, ArtifactActionService actions) {
        ArtifactReportResource resource = new ArtifactReportResource();
        resource.workbench = bench;
        resource.actions = actions;
        resource.planner = new ReportJourneyPlanner();
        CognitiveService cognitive = new CognitiveService();
        cognitive.workbench(bench);
        resource.cognitive = cognitive;
        return resource;
    }

    /** A request that carries the question, the consent and the idempotency key. */
    private static ArtifactReportResource.ReportRequest request(String question,
                                                               String idempotencyKey) {
        return new ArtifactReportResource.ReportRequest(question, null, true, idempotencyKey, null);
    }

    /** The body of a refusal, in the shape the route's clients read. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> refusal(Response response) {
        assertThat(response.getEntity()).isInstanceOf(Map.class);
        return (Map<String, Object>) response.getEntity();
    }

    /** The body of a write that happened. */
    private static ArtifactReportResource.ReportResponse success(Response response) {
        assertThat(response.getEntity()).isInstanceOf(ArtifactReportResource.ReportResponse.class);
        return (ArtifactReportResource.ReportResponse) response.getEntity();
    }

    /** The artifact files a root holds: the reports, not the index and not the lease. */
    private static List<String> artifactFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }
}
