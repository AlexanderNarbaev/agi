package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.actions.ArtifactActionService;
import io.matrix.actions.ArtifactWriter;
import io.matrix.security.NetworkExposure;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B3 — the HTTP contract of the bounded Evidence-to-Action route.
 *
 * <p>The route turns one question into one real file, and everything else about it is a
 * refusal: the feature is off by default, the consent must be given, the workspace must
 * exist, the report name must be a plain file name, the process must be local-only, and the
 * output root must be the server's own, real directory. These tests drive the resource the
 * way the HTTP layer does — through the method, with the two injected collaborators assigned
 * by hand — and pin what the caller is told: the status, the stable error code, and what
 * happened on the disk.</p>
 *
 * <p>Two facts about the environment are stated here rather than left to the reader:</p>
 *
 * <ul>
 *   <li><b>The authorized root must not be writable by other users.</b> The service refuses a
 *       root that is group- or world-writable, and the ambient umask of this machine (0002)
 *       makes a freshly created directory group-writable. The tests therefore set the root's
 *       permissions explicitly; without that the gate would refuse every request and the
 *       refusal would be about the umask rather than about the route.</li>
 *   <li><b>The concurrency case uses two workbenches over two databases.</b> The knowledge
 *       workbench is not thread-safe — two concurrent asks on one instance raise a
 *       {@code ConcurrentModificationException} in the conversation map and a
 *       {@code Failed to record the asked question} in the store — so a single shared
 *       workbench would test the workbench, not the file action. The two workbenches answer
 *       the same question about the same workspace and write into the same root through the
 *       same service, which is exactly the situation the identity and the writer's lease
 *       exist for: one artifact, one writer, one replay.</li>
 * </ul>
 *
 * <p>What these tests do not claim: a written report is a record of what the workspace said.
 * It is not evidence for the propositions it repeats, and the response says so.</p>
 */
class ArtifactReportResourceTest {

    /** The workspace the route is asked about; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /**
     * The question the photosynthesis package answers with claims and sources.
     *
     * <p>Verified empirically: the workspace answers it with seven cited claims drawn from
     * four sources, and the report therefore has something to carry.</p>
     */
    private static final String QUESTION = "What is photosynthesis?";

    /** The name of a workspace that does not exist. */
    private static final String MISSING_WORKSPACE = "no-such-workspace";

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

    // ── the refusals ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the feature switched off answers 503 feature_disabled and writes nothing")
    void theFeatureIsOff() throws Exception {
        Path root = root("off");
        ArtifactActionService actions = new ArtifactActionService(false, root.toString());
        ArtifactReportResource resource = resource(workbench("off"), actions);

        Response response = resource.report(WORKSPACE, request(QUESTION, true, "key-off"));

        assertThat(response.getStatus())
                .as("a feature that is off is unavailable, not a bad request")
                .isEqualTo(503);
        assertThat(refusal(response)).containsEntry("error", "feature_disabled");
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("a request without consent answers 403 consent_required and writes nothing")
    void consentIsMissing() throws Exception {
        Path root = root("noconsent");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("noconsent"), actions);

        Response response = resource.report(WORKSPACE, request(QUESTION, null, "key-noconsent"));

        assertThat(response.getStatus())
                .as("a request that does not consent to a write is refused")
                .isEqualTo(403);
        assertThat(refusal(response)).containsEntry("error", "consent_required");
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("a request with consent=false answers 403 consent_required and writes nothing")
    void consentIsRefused() throws Exception {
        Path root = root("consentfalse");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("consentfalse"), actions);

        Response response = resource.report(WORKSPACE, request(QUESTION, Boolean.FALSE, "key-false"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(refusal(response)).containsEntry("error", "consent_required");
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("a question for a workspace that does not exist answers 404 and writes nothing")
    void theWorkspaceDoesNotExist() throws Exception {
        Path root = root("noworkspace");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("noworkspace"), actions);

        Response response = resource.report(MISSING_WORKSPACE, request(QUESTION, true, "key-nows"));

        assertThat(response.getStatus())
                .as("a workspace that is not there is not found, not refused")
                .isEqualTo(404);
        assertThat(refusal(response)).containsEntry("error", "workspace_not_found");
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("a report name that leaves the root answers 400 invalid_report_name")
    void theReportNameMustBePlain() throws Exception {
        Path root = root("badname");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("badname"), actions);
        Path absolute = Path.of("/etc/x.md");
        boolean existedBefore = Files.exists(absolute);

        for (String name : new String[] {"../outside.md", "/etc/x.md"}) {
            Response response = resource.report(WORKSPACE, request(QUESTION, true, "key-name", name));

            assertThat(response.getStatus())
                    .as("a name that tries to leave the root is a bad request")
                    .isEqualTo(400);
            assertThat(refusal(response)).containsEntry("error", "invalid_report_name");
        }

        assertThat(artifactFiles(root)).isEmpty();
        assertThat(directory.resolve("outside.md"))
                .as("nothing was written next to the root either")
                .doesNotExist();
        assertThat(Files.exists(absolute))
                .as("nothing was written at the absolute path the name named")
                .isEqualTo(existedBefore);
    }

    @Test
    @DisplayName("a network-exposed process answers 403 local_only_action even with consent")
    void theProcessMustBeLocalOnly() throws Exception {
        Path root = root("exposed");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("exposed"), actions);

        NetworkExposure.configure(NetworkExposure.Mode.NETWORK_EXPOSED, "token");
        try {
            Response response = resource.report(WORKSPACE, request(QUESTION, true, "key-exposed"));

            assertThat(response.getStatus())
                    .as("a side effect on the file system needs a local-only process")
                    .isEqualTo(403);
            assertThat(refusal(response)).containsEntry("error", "local_only_action");
        } finally {
            NetworkExposure.reset();
        }
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("a missing output-root configuration answers 503 output_root_unavailable")
    void theOutputRootIsNotConfigured() throws Exception {
        ArtifactActionService actions = new ArtifactActionService(true, "");
        ArtifactReportResource resource = resource(workbench("noconfig"), actions);

        Response response = resource.report(WORKSPACE, request(QUESTION, true, "key-noconfig"));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(refusal(response)).containsEntry("error", "output_root_unavailable");
        assertThat(artifactFiles(directory))
                .as("nothing was written anywhere")
                .isEmpty();
    }

    @Test
    @DisplayName("an unsafe output root answers 503 output_root_unavailable")
    void theOutputRootMustBeReal() throws Exception {
        Path missing = directory.resolve("missing-root");
        ArtifactActionService missingActions = new ArtifactActionService(true, missing.toString());
        ArtifactReportResource missingResource = resource(workbench("missingroot"), missingActions);

        Response missingResponse = missingResource.report(WORKSPACE,
                request(QUESTION, true, "key-missing"));

        assertThat(missingResponse.getStatus()).isEqualTo(503);
        assertThat(refusal(missingResponse)).containsEntry("error", "output_root_unavailable");
        assertThat(missing)
                .as("the route does not create the root it was not given")
                .doesNotExist();

        // a root that is a symbolic link to a real directory is the same refusal: the
        // directory the operator authorized is not the one the path names
        Path real = root("real-root");
        Path link = directory.resolve("linked-root");
        Files.createSymbolicLink(link, real);
        ArtifactActionService linkActions = new ArtifactActionService(true, link.toString());
        ArtifactReportResource linkResource = resource(workbench("linkedroot"), linkActions);

        Response linkResponse = linkResource.report(WORKSPACE, request(QUESTION, true, "key-link"));

        assertThat(linkResponse.getStatus()).isEqualTo(503);
        assertThat(refusal(linkResponse)).containsEntry("error", "output_root_unavailable");
        assertThat(artifactFiles(real)).isEmpty();
    }

    // ── the successful write ────────────────────────────────────────────────

    @Test
    @DisplayName("a successful request answers 201 and writes the report the workspace cited")
    void theSuccessfulRequest() throws Exception {
        Path root = root("out");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("out"), actions);

        // the answer the route will produce, asked of an independent workspace over its own
        // database, so that the citations the report carries are the workspace's own
        KnowledgeWorkbench reference = workbench("reference");
        Answer expected = reference.ask(WORKSPACE, QUESTION, AbstractionLevel.INTRODUCTORY);
        assertThat(expected.claims()).as("the question must be answerable").isNotEmpty();
        assertThat(expected.sources()).isNotEmpty();

        Response response = resource.report(WORKSPACE, request(QUESTION, true, "key-ok"));

        assertThat(response.getStatus())
                .as("a report that was written is created")
                .isEqualTo(201);
        ArtifactReportResource.ReportResponse body = success(response);
        assertThat(body.taskId()).isNotBlank();
        assertThat(body.actionName()).isEqualTo("report.artifact.write");
        assertThat(body.actionVersion()).isEqualTo(1);
        assertThat(body.disposition()).isEqualTo("WRITTEN");
        assertThat(body.artifactName()).isNotBlank();
        assertThat(body.sha256()).matches("[0-9a-f]{64}");
        assertThat(body.epistemicDisposition()).isEqualTo("ANSWERED");
        assertThat(body.traceReference()).isEqualTo("artifact-index:" + body.taskId());
        assertThat(body.refusalReasons()).isEmpty();
        assertThat(body.evidence())
                .as("the response cites exactly the claims the answer cited")
                .extracting(ArtifactReportResource.EvidenceReference::claimId)
                .containsExactlyInAnyOrderElementsOf(
                        expected.claims().stream().map(Claim::id).toList());

        Path artifact = root.resolve(body.artifactName());
        assertThat(artifact).exists();
        assertThat(sha256(artifact))
                .as("the digest in the response is the digest of the bytes on disk")
                .isEqualTo(body.sha256());
        String text = Files.readString(artifact);
        for (Claim claim : expected.claims()) {
            assertThat(text).as("the report carries the id of every cited claim").contains(claim.id());
        }
        for (Source source : expected.sources()) {
            assertThat(text).as("the report carries the title of every cited source")
                    .contains(source.title());
        }
    }

    @Test
    @DisplayName("the same request repeated answers 200 REPLAYED and writes one artifact")
    void theSameRequestIsReplayed() throws Exception {
        Path root = root("replay");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("replay"), actions);

        Response first = resource.report(WORKSPACE, request(QUESTION, true, "key-replay"));
        assertThat(first.getStatus()).isEqualTo(201);
        Response second = resource.report(WORKSPACE, request(QUESTION, true, "key-replay"));

        assertThat(second.getStatus())
                .as("the repeat of an action that already happened is not a new creation")
                .isEqualTo(200);
        assertThat(success(second).disposition()).isEqualTo("REPLAYED");
        assertThat(success(second).sha256()).isEqualTo(success(first).sha256());
        assertThat(success(second).taskId()).isEqualTo(success(first).taskId());
        assertThat(artifactFiles(root))
                .as("the root holds exactly one artifact")
                .hasSize(1);

        String identity = success(first).taskId();
        assertThat(recordsOf(actions, identity))
                .as("the index holds one staged and one committed record for the identity")
                .extracting(ArtifactWriter.Record::status)
                .containsExactly("STAGED", "COMMITTED");
    }

    @Test
    @DisplayName("two concurrent requests with the same identity write one artifact")
    void theConcurrentRequests() throws Exception {
        Path root = root("concurrent");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource first = resource(workbench("concurrent-a"), actions);
        ArtifactReportResource second = resource(workbench("concurrent-b"), actions);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> answers = new ArrayList<>();
        try {
            for (ArtifactReportResource each : List.of(first, second)) {
                answers.add(pool.submit(() -> {
                    start.await();
                    return each.report(WORKSPACE, request(QUESTION, true, "key-concurrent"));
                }));
            }
            start.countDown();

            List<Response> responses = List.of(answers.get(0).get(), answers.get(1).get());
            assertThat(responses).extracting(Response::getStatus)
                    .as("both requests succeed")
                    .containsExactlyInAnyOrder(200, 201);
            assertThat(responses.stream().map(response -> success(response).disposition()).toList())
                    .as("exactly one of them writes, the other one replays")
                    .containsExactlyInAnyOrder("WRITTEN", "REPLAYED");
            assertThat(success(responses.get(0)).taskId())
                    .as("both requests carry the same identity")
                    .isEqualTo(success(responses.get(1)).taskId());
            assertThat(artifactFiles(root))
                    .as("the root holds exactly one artifact")
                    .hasSize(1);
            assertThat(recordsOf(actions, success(responses.get(0)).taskId()))
                    .as("the index holds one staged and one committed record for the identity")
                    .extracting(ArtifactWriter.Record::status)
                    .containsExactly("STAGED", "COMMITTED");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the response body carries no absolute path of the output root")
    void theResponseCarriesNoPath() throws Exception {
        Path root = root("nopath");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("nopath"), actions);

        Response response = resource.report(WORKSPACE, request(QUESTION, true, "key-nopath"));

        assertThat(response.getStatus()).isEqualTo(201);
        String body = new ObjectMapper().writeValueAsString(response.getEntity());
        assertThat(body)
                .as("the body must not disclose where the server writes")
                .doesNotContain(root.toAbsolutePath().toString());
        assertThat(body).doesNotContain(directory.toAbsolutePath().toString());
        assertThat(success(response).artifactLocator())
                .as("the locator is a name, not a path")
                .doesNotContain(root.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("a root that was replaced answers 500 REFUSED_ROOT_REPLACED")
    void theRootWasReplaced() throws Exception {
        Path root = root("replaced");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(workbench("replaced"), actions);
        // the writer is bound to the root before the root is replaced; a writer made per
        // request would compare the root with itself and notice nothing
        actions.writer();

        deleteRecursively(root);
        Files.createDirectories(root);
        Files.setPosixFilePermissions(root, OWNER_ONLY);

        Response response = resource.report(WORKSPACE, request(QUESTION, true, "key-replaced"));

        assertThat(response.getStatus())
                .as("a replaced root is mapped, not swallowed and not written into")
                .isEqualTo(500);
        assertThat(refusal(response)).containsEntry("error", "REFUSED_ROOT_REPLACED");
        assertThat(artifactFiles(root)).isEmpty();
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

    /** The resource, wired by hand, the way the container wires it. */
    private ArtifactReportResource resource(KnowledgeWorkbench bench, ArtifactActionService actions) {
        ArtifactReportResource resource = new ArtifactReportResource();
        resource.workbench = bench;
        resource.actions = actions;
        return resource;
    }

    /** A request that carries the question, the consent and the idempotency key. */
    private static ArtifactReportResource.ReportRequest request(String question, Boolean consent,
                                                               String idempotencyKey) {
        return request(question, consent, idempotencyKey, null);
    }

    /** A request that also names the report the caller wants. */
    private static ArtifactReportResource.ReportRequest request(String question, Boolean consent,
                                                               String idempotencyKey,
                                                               String reportName) {
        return new ArtifactReportResource.ReportRequest(question, reportName, consent,
                idempotencyKey, null);
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
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }

    /** The index records of one identity. */
    private static List<ArtifactWriter.Record> recordsOf(ArtifactActionService actions,
                                                         String identity) throws IOException {
        return actions.writer().readIndex().stream()
                .filter(record -> record.actionId().equals(identity))
                .toList();
    }

    /** Deletes a directory and everything in it. */
    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** The digest of a file, measured on its bytes. */
    private static String sha256(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
