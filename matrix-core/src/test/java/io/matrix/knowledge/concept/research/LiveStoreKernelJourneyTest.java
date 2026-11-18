package io.matrix.knowledge.concept.research;

import io.matrix.actions.ArtifactActionService;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.LearningTrace;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import io.matrix.security.NetworkExposure;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — the closed loop, on the owner's <b>migrated</b> live store.
 *
 * <p><b>What this is.</b> Gate 5 asks for ONE complete existing-kernel-driven journey:
 * <i>a question over a workspace, a plan and a method chosen by the kernel rather than
 * written into the route, a permission honoured, an action executed, an artifact written,
 * an effect observed by a reader that did not write it, and a trace left behind that the
 * next analogous task can read — and does read, choosing a different method the second
 * time</i>.
 *
 * <p><b>Why on the live store.</b> The journey that already exists
 * ({@code ClosedLoopReportJourneyTest}) runs over a fresh temporary store with the bundled
 * photosynthesis package. The owner's store is the store the identity migration rewrote
 * (schema v15 → v21), so the loop must be shown to close <b>there</b>: a journey over a
 * store whose claims now point at referents, not over a pristine one.
 *
 * <p><b>What this test does not claim.</b> The replayed report is a record of what the
 * workspace said earlier, not new evidence — the response says so. Nothing here claims the
 * loop makes the workspace smarter; it claims the loop closes and that its trace is kept.</p>
 *
 * <p><b>What it pins.</b> The published numbers of the chain: the workspace the journey
 * runs over (the concepts 37, the claims 24), the episode's <b>seven</b> steps after the
 * first journey — PLAN, PERMISSION, ACTION, OBSERVE, VERIFY, RESULT, RETAINED, in that
 * order — and the fact that the <b>second</b> journey appends to the same episode, so the
 * episode holds fourteen steps afterwards. The step count is therefore asserted where the
 * first journey leaves it, not after the replay.</p>
 *
 * <p>The test is <b>skipped, not failed</b>, when the store is not in the checkout (CI).</p>
 */
@DisplayName("P2 — the closed loop on the owner's migrated live store")
class LiveStoreKernelJourneyTest {

    /** The permissions an authorized output root must have: nobody else may write to it. */
    private static final EnumSet<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    private static final Path LIVE = Path.of("data/knowledge/workbench.sqlite");

    /** The question the journey asks. */
    private static final String QUESTION = "What is photosynthesis?";

    /**
     * The idempotency key of this run.
     *
     * <p>The journey's trace episode is derived from the action identity, which is
     * derived from the idempotency key: a fixed key would make every run of this test
     * append to the same episode, and the trace would then hold the plan of the FIRST
     * run rather than this one's. The key is therefore unique per run.</p>
     */
    private final String key = "key-live-" + java.util.UUID.randomUUID();

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
    }

    @AfterEach
    void tearDown() {
        NetworkExposure.reset();
        if (workbench != null) {
            workbench.close();
        }
    }

    @Test
    @DisplayName("the journey plans, acts, verifies, traces — and the next task reuses the trace")
    void theJourneyClosesOnTheMigratedStore() throws Exception {
        Path live = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(live),
                "the owner's live store is not in this checkout — the journey is skipped, not failed");
        workbench = KnowledgeWorkbench.at(live.toString());
        System.out.println("[kernel-journey] the store: " + live
                + " — the workspaces " + workbench.workspaces());

        Path root = Files.createDirectories(directory.resolve("reports"));
        Files.setPosixFilePermissions(root, OWNER_ONLY);
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench, actions);

        // ── the first journey: the kernel plans, the action runs, the effect is verified ──
        Response response = route.report("photosynthesis", request(QUESTION,
                true, key));
        assertThat(response.getStatus()).as("the report was created").isEqualTo(201);
        ArtifactReportResource.ReportResponse body = success(response);
        assertThat(body.disposition()).isEqualTo("WRITTEN");
        assertThat(body.sha256()).matches("[0-9a-f]{64}");

        Path artifact = root.resolve(body.artifactName());
        assertThat(artifact).as("the artifact exists").exists();
        assertThat(sha256(artifact)).as("the artifact's bytes are the ones that were hashed")
                .isEqualTo(body.sha256());

        // ── the trace: the phases the design names ──
        List<LearningTrace.Step> steps = route.cognitive.store()
                .events(ReportJourneyTrace.episodeId(body.taskId()));
        // The published number: the episode holds SEVEN steps after the first journey —
        // PLAN, PERMISSION, ACTION, OBSERVE, VERIFY, RESULT, RETAINED.
        assertThat(steps.size()).as("the steps the first journey left in the episode").isEqualTo(7);
        assertThat(steps).extracting(LearningTrace.Step::phase)
                .containsExactly(ReportJourneyTrace.PHASE_PLAN,
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
                .as("the effect was verified by a reader that did not write it")
                .contains("the effect is verified");
        System.out.printf("[kernel-journey] the first journey: the disposition %s, the artifact %s, "
                + "the steps %d%n", body.disposition(), body.artifactName(), steps.size());

        // ── what the kernel had to work with ──
        String identity = actions.identity("photosynthesis", key, QUESTION);
        String reportName = actions.defaultReportName(identity);
        String episodeId = ReportJourneyTrace.episodeId(identity);
        var retained = ReportJourneyTrace.retained(route.cognitive.store(), episodeId);
        String fingerprint = io.matrix.cognitive.report.WorkspaceFingerprint
                .of(workbench.workspace("photosynthesis"));
        System.out.println("[kernel-journey] the identity: " + identity);
        System.out.println("[kernel-journey] the report name: " + reportName);
        System.out.println("[kernel-journey] the retained: " + (retained == null ? "none"
                : retained.fileName() + " over the fingerprint " + retained.workspaceFingerprint()));
        System.out.println("[kernel-journey] the fingerprint now: " + fingerprint);

        // ── the counterfactual: the same request replays, and the kernel changes method ──
        Response second = route.report("photosynthesis", request(QUESTION,
                true, key));
        assertThat(second.getStatus())
                .as("the repeat of an action that already happened is not a new creation")
                .isEqualTo(200);
        ArtifactReportResource.ReportResponse replayed = success(second);
        assertThat(replayed.disposition()).isEqualTo("REPLAYED");
        assertThat(replayed.sha256()).as("the replay serves the same bytes")
                .isEqualTo(body.sha256());
        assertThat(replayed.limitations())
                .as("the reply says that the journey replayed the retained one")
                .anyMatch(limitation -> limitation.contains("replayed the retained one"));

        List<LearningTrace.Step> replaySteps = route.cognitive.store()
                .events(ReportJourneyTrace.episodeId(replayed.taskId()));
        System.out.println("[kernel-journey] the second plan: "
                + lastDetailOf(replaySteps, ReportJourneyTrace.PHASE_PLAN));
        System.out.printf("[kernel-journey] the second journey: the steps %d%n",
                replaySteps.size());
        // The second journey APPENDS to the same episode: the episode id is derived from
        // the action identity, and the identity of a repeated request is the same one.
        assertThat(ReportJourneyTrace.episodeId(replayed.taskId()))
                .as("the second journey's episode")
                .isEqualTo(ReportJourneyTrace.episodeId(body.taskId()));
        // The episode therefore holds BOTH journeys: seven steps for the first and seven
        // more for the replay.
        assertThat(replaySteps.size()).as("the steps the episode holds after the second journey")
                .isEqualTo(14);
        assertThat(lastDetailOf(replaySteps, ReportJourneyTrace.PHASE_PLAN))
                .as("the kernel read the trace and chose the cheap path")
                .contains(ReportJourneyMethod.REPLAY_ID);
        System.out.printf("[kernel-journey] the second journey: the disposition %s, the method %s%n",
                replayed.disposition(), ReportJourneyMethod.REPLAY_ID);

        // ── the workspace is not worse for the journey ──
        var snapshot = workbench.workspace("photosynthesis");
        assertThat(snapshot.claims()).as("the store still holds its claims")
                .isNotEmpty();
        // The published counts of the migrated photosynthesis workspace.
        assertThat(snapshot.concepts().size()).as("the photosynthesis concepts")
                .isEqualTo(37);
        assertThat(snapshot.claims().size()).as("the photosynthesis claims").isEqualTo(24);
        System.out.printf("[kernel-journey] the store after: the concepts %d, the claims %d%n",
                snapshot.concepts().size(), snapshot.claims().size());
    }

    /** The route, wired by hand, the way the container wires it. */
    private static ArtifactReportResource route(KnowledgeWorkbench bench, ArtifactActionService actions) {
        CognitiveService cognitive = new CognitiveService();
        cognitive.workbench(bench);
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = actions;
        route.planner = new ReportJourneyPlanner();
        route.cognitive = cognitive;
        return route;
    }

    /** A request that carries the question, the consent and the idempotency key. */
    private static ArtifactReportResource.ReportRequest request(String question, Boolean consent,
            String idempotencyKey) {
        return new ArtifactReportResource.ReportRequest(question, null, consent, idempotencyKey,
                null);
    }

    /**
     * The detail the trace step carries.
     *
     * @param steps the steps of the episode
     * @param phase the phase
     * @return the first step of that phase
     */
    private static String detailOf(List<LearningTrace.Step> steps, String phase) {
        return steps.stream().filter(step -> phase.equals(step.phase()))
                .map(step -> String.valueOf(step.detail())).findFirst().orElse("");
    }

    /**
     * The detail of the LAST step of a phase.
     *
     * <p><b>Why the last and not the first.</b> The trace's episode is derived from the
     * action identity, and the identity of a repeated request is the same one — so the
     * episode holds <b>both</b> journeys: the first one's seven steps and the second one's
     * seven. The first step of a phase therefore belongs to the FIRST journey, and the
     * second journey's plan is the LAST step of that phase.</p>
     *
     * @param steps the steps of the episode
     * @param phase the phase
     * @return the last step of that phase
     */
    private static String lastDetailOf(List<LearningTrace.Step> steps, String phase) {
        List<String> details = steps.stream().filter(step -> phase.equals(step.phase()))
                .map(step -> String.valueOf(step.detail())).toList();
        return details.isEmpty() ? "" : details.get(details.size() - 1);
    }

    /** The successful body of a response. */
    private static ArtifactReportResource.ReportResponse success(Response response) {
        return (ArtifactReportResource.ReportResponse) response.getEntity();
    }

    /** The sha256 of a file's bytes. */
    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
