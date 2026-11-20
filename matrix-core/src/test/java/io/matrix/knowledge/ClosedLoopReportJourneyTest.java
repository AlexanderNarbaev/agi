package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.actions.ArtifactActionService;
import io.matrix.actions.ArtifactWriter;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.report.ArtifactObserver;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
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
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P2 — the closed loop, end to end, on the product's own report journey.
 *
 * <p><b>What is pinned here.</b> The chain the design asks for: a question over a
 * workspace, a plan and a method chosen by the kernel rather than written into the route,
 * a permission honoured, an action executed, an artifact written, an effect observed by a
 * reader that did not write it, and a trace left behind that the next analogous task can
 * read — and does read, choosing a different method the second time.</p>
 *
 * <p><b>What the test does not claim.</b> A replayed report is a record of what the
 * workspace said earlier, not new evidence: the response says so, and the replay is
 * admissible only while the workspace is unchanged. Nothing here claims the loop makes
 * the workspace smarter; it claims the loop can be observed, and that the observation is
 * kept.</p>
 */
class ClosedLoopReportJourneyTest {

    /** The workspace the journey runs over; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** A question the photosynthesis package answers with claims and sources. */
    private static final String QUESTION = "What is photosynthesis?";

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
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
    }

    @AfterEach
    void tearDown() {
        NetworkExposure.reset();
        workbenches.forEach(KnowledgeWorkbench::close);
    }

    // ── the loop ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the journey is planned, executed, observed and traced")
    void theJourneyIsClosed() throws Exception {
        Path root = root("closed");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("closed"), actions, new ReportJourneyPlanner(),
                new CognitiveService());

        Response response = route.report(WORKSPACE, request(QUESTION, true, "key-closed"));

        assertThat(response.getStatus()).isEqualTo(201);
        ArtifactReportResource.ReportResponse body = success(response);
        assertThat(body.disposition()).isEqualTo("WRITTEN");
        assertThat(body.sha256()).matches("[0-9a-f]{64}");
        Path artifact = root.resolve(body.artifactName());
        assertThat(artifact).exists();
        assertThat(sha256(artifact)).isEqualTo(body.sha256());

        // THE TRACE — what happened, kept where the next task can read it.
        List<io.matrix.cognitive.LearningTrace.Step> steps = route.cognitive.store()
                .events(ReportJourneyTrace.episodeId(body.taskId()));
        assertThat(steps).extracting(io.matrix.cognitive.LearningTrace.Step::phase)
                .contains(ReportJourneyTrace.PHASE_PLAN,
                        ReportJourneyTrace.PHASE_PERMISSION,
                        ReportJourneyTrace.PHASE_ACTION,
                        ReportJourneyTrace.PHASE_OBSERVE,
                        ReportJourneyTrace.PHASE_VERIFY,
                        ReportJourneyTrace.PHASE_RESULT,
                        ReportJourneyTrace.PHASE_RETAINED);
        assertThat(detailOf(steps, ReportJourneyTrace.PHASE_PLAN))
                .as("the plan names the method the kernel chose")
                .contains(ReportJourneyMethod.REFERENCE_ID);
        assertThat(detailOf(steps, ReportJourneyTrace.PHASE_VERIFY))
                .as("the effect was verified by the independent reader")
                .contains("the effect is verified");
    }

    @Test
    @DisplayName("the same request replays the retained journey and asks nothing again")
    void theSameRequestIsReplayedByTheKernel() throws Exception {
        Path root = root("replay");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("replay"), actions, new ReportJourneyPlanner(),
                new CognitiveService());

        Response first = route.report(WORKSPACE, request(QUESTION, true, "key-replay"));
        assertThat(first.getStatus()).isEqualTo(201);
        Response second = route.report(WORKSPACE, request(QUESTION, true, "key-replay"));

        assertThat(second.getStatus())
                .as("the repeat of an action that already happened is not a new creation")
                .isEqualTo(200);
        ArtifactReportResource.ReportResponse replayed = success(second);
        assertThat(replayed.disposition()).isEqualTo("REPLAYED");
        assertThat(replayed.sha256()).isEqualTo(success(first).sha256());
        assertThat(replayed.evidence())
                .as("the replay serves the answer the earlier journey gave")
                .extracting(ArtifactReportResource.EvidenceReference::claimId)
                .containsExactlyInAnyOrderElementsOf(success(first).evidence().stream()
                        .map(ArtifactReportResource.EvidenceReference::claimId).toList());
        assertThat(replayed.limitations())
                .as("the reply says that the journey replayed the retained one")
                .anyMatch(limitation -> limitation.contains("replayed the retained one"));

        // THE METHOD CHANGED — the kernel consulted the trace and chose the cheap path.
        List<io.matrix.cognitive.LearningTrace.Step> steps = route.cognitive.store()
                .events(ReportJourneyTrace.episodeId(replayed.taskId()));
        assertThat(detailOf(steps, ReportJourneyTrace.PHASE_PLAN))
                .as("the second journey planned the replay")
                .contains(ReportJourneyMethod.REPLAY_ID);
        assertThat(artifactFiles(root))
                .as("the root still holds exactly one artifact")
                .hasSize(1);
    }

    @Test
    @DisplayName("the replay agrees with the reference over the validation corpus")
    void theReplayAgreesWithTheReference() throws Exception {
        // the corpus the replay's declared accuracy rests on: three questions, each run
        // twice, and the two answers compared
        List<String> questions = List.of(QUESTION, "What is chlorophyll?", "What is the light reaction?");
        for (int index = 0; index < questions.size(); index++) {
            String question = questions.get(index);
            Path root = root("corpus-" + index);
            ArtifactActionService actions = new ArtifactActionService(true, root.toString());
            ArtifactReportResource route = route(workbench("corpus-" + index), actions,
                    new ReportJourneyPlanner(), new CognitiveService());
            String key = "key-corpus-" + index;

            Response first = route.report(WORKSPACE, request(question, true, key));
            Response second = route.report(WORKSPACE, request(question, true, key));

            assertThat(first.getStatus()).isEqualTo(201);
            assertThat(second.getStatus()).isEqualTo(200);
            ArtifactReportResource.ReportResponse reference = success(first);
            ArtifactReportResource.ReportResponse replay = success(second);
            assertThat(replay.sha256()).isEqualTo(reference.sha256());
            assertThat(replay.epistemicDisposition()).isEqualTo(reference.epistemicDisposition());
            assertThat(replay.evidence())
                    .extracting(ArtifactReportResource.EvidenceReference::claimId)
                    .containsExactlyInAnyOrderElementsOf(reference.evidence().stream()
                            .map(ArtifactReportResource.EvidenceReference::claimId).toList());
        }
    }

    @Test
    @DisplayName("a request for another file name under the same identity is not replayed")
    void anotherNameIsNotReplayed() throws Exception {
        Path root = root("rename");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("rename"), actions, new ReportJourneyPlanner(),
                new CognitiveService());

        route.report(WORKSPACE, request(QUESTION, true, "key-rename"));
        Response renamed = route.report(WORKSPACE,
                request(QUESTION, true, "key-rename", "another-name.md"));

        // the writer's own rule: one identity, one artifact. A request that asks for a
        // different file under an identity that already committed one is not answered by
        // replaying the other file.
        assertThat(renamed.getStatus()).isNotEqualTo(200);
        List<io.matrix.cognitive.LearningTrace.Step> steps = route.cognitive.store()
                .events(ReportJourneyTrace.episodeId(
                        actions.identity(WORKSPACE, "key-rename", QUESTION)));
        assertThat(detailOf(steps, ReportJourneyTrace.PHASE_PLAN))
                .as("the plan fell back to the reference method")
                .contains(ReportJourneyMethod.REFERENCE_ID);
    }

    @Test
    @DisplayName("a journey the kernel has no method for is refused with a reason")
    void noMethodIsARefusal() throws Exception {
        Path root = root("nomethod");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("nomethod"), actions,
                new ReportJourneyPlanner(new CostLedger(), List.of()), new CognitiveService());

        Response response = route.report(WORKSPACE, request(QUESTION, true, "key-nomethod"));

        assertThat(response.getStatus())
                .as("a request the kernel cannot serve is refused, not answered by accident")
                .isEqualTo(422);
        assertThat(refusal(response)).containsEntry("error", "no_method_for_request");
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("the journey without the trace is the no-learning baseline")
    void withoutTheTraceTheReferenceIsChosen() throws Exception {
        Path root = root("baseline");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        // no cognitive service: the trace is not consulted and not kept, which is the
        // design's no-learning baseline — the same journey with the trace consult disabled
        ArtifactReportResource route = route(workbench("baseline"), actions,
                new ReportJourneyPlanner(), null);

        Response first = route.report(WORKSPACE, request(QUESTION, true, "key-baseline"));
        Response second = route.report(WORKSPACE, request(QUESTION, true, "key-baseline"));

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(success(second).disposition())
                .as("without the trace there is nothing to replay from, so the workspace is "
                        + "asked again and the writer replays the artifact")
                .isEqualTo("REPLAYED");
        assertThat(success(second).limitations())
                .as("the baseline says that the trace was not kept")
                .anyMatch(limitation -> limitation.contains("trace could not be written"));
    }

    // ── the observation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("the effect is observed by a reader that did not write it")
    void theEffectIsObservedIndependently() throws Exception {
        Path root = root("observe");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("observe"), actions, new ReportJourneyPlanner(),
                new CognitiveService());

        Response response = route.report(WORKSPACE, request(QUESTION, true, "key-observe"));
        ArtifactReportResource.ReportResponse body = success(response);
        ArtifactWriter writer = actions.writer();

        ArtifactObserver.Observation seen = ArtifactObserver.observe(writer, body.taskId(),
                body.artifactName(), body.sha256());
        assertThat(seen.exists()).isTrue();
        assertThat(seen.sha256()).isEqualTo(body.sha256());
        assertThat(seen.stagedRecords()).isEqualTo(1);
        assertThat(seen.committedRecords()).isEqualTo(1);
        assertThat(seen.verified())
                .as("the artifact is there, its digest is the one that was reported, and the "
                        + "index holds exactly one committed record")
                .isTrue();

        // A MISSING ARTIFACT IS A FAILED EFFECT, not a success.
        Files.delete(root.resolve(body.artifactName()));
        ArtifactObserver.Observation gone = ArtifactObserver.observe(writer, body.taskId(),
                body.artifactName(), body.sha256());
        assertThat(gone.exists()).isFalse();
        assertThat(gone.verified()).isFalse();

        // A TAMPERED ARTIFACT IS A FAILED EFFECT TOO.
        Files.writeString(root.resolve(body.artifactName()), "not the report");
        ArtifactObserver.Observation tampered = ArtifactObserver.observe(writer, body.taskId(),
                body.artifactName(), body.sha256());
        assertThat(tampered.exists()).isTrue();
        assertThat(tampered.matches()).isFalse();
        assertThat(tampered.verified()).isFalse();
    }

    @Test
    @DisplayName("the observer refuses a name it cannot trust")
    void theObserverRefusesANameItCannotTrust() throws Exception {
        Path root = root("observe-names");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactWriter writer = actions.writer();

        for (String name : new String[] {"../outside.md", "sub/dir.md", "..", ".hidden.md", "",
                "  "}) {
            ArtifactObserver.Observation observation = ArtifactObserver.observe(writer,
                    "identity", name, "a-digest");
            assertThat(observation.exists())
                    .as("the observer does not resolve a name that could leave the root")
                    .isFalse();
            assertThat(observation.verified()).isFalse();
            assertThat(observation.detail()).contains("not a plain file name");
        }

        // and a plain name IS read
        Files.writeString(root.resolve("plain.md"), "the report");
        ArtifactObserver.Observation plain = ArtifactObserver.observe(writer, "identity",
                "plain.md", null);
        assertThat(plain.exists()).isTrue();
        assertThat(plain.bytes()).isEqualTo("the report".length());
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

    /** The route, wired by hand, the way the container wires it. */
    private ArtifactReportResource route(KnowledgeWorkbench bench, ArtifactActionService actions,
            ReportJourneyPlanner planner, CognitiveService cognitive) {
        if (cognitive != null) {
            cognitive.workbench(bench);
        }
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = actions;
        route.planner = planner;
        route.cognitive = cognitive;
        return route;
    }

    /** A request that carries the question, the consent and the idempotency key. */
    private static ArtifactReportResource.ReportRequest request(String question, Boolean consent,
            String idempotencyKey) {
        return request(question, consent, idempotencyKey, null);
    }

    /** A request that also names the report the caller wants. */
    private static ArtifactReportResource.ReportRequest request(String question, Boolean consent,
            String idempotencyKey, String reportName) {
        return new ArtifactReportResource.ReportRequest(question, reportName, consent,
                idempotencyKey, null);
    }

    /** The body of a refusal, in the shape the route's clients read. */
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> refusal(Response response) {
        assertThat(response.getEntity()).isInstanceOf(java.util.Map.class);
        return (java.util.Map<String, Object>) response.getEntity();
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

    /**
     * The detail of the LAST step of a phase.
     *
     * <p>The trace is append-only: a second journey over the same identity adds its own
     * steps to the same episode, so the last step of a phase is the one the latest
     * journey wrote.</p>
     */
    private static String detailOf(List<io.matrix.cognitive.LearningTrace.Step> steps, String phase) {
        return steps.stream()
                .filter(step -> step.phase().equals(phase))
                .map(io.matrix.cognitive.LearningTrace.Step::detail)
                .reduce((first, second) -> second)
                .orElse("");
    }

    /** The digest of a file, measured on its bytes. */
    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    /** Deletes a directory and everything in it. */
    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
