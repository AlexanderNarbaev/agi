package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.actions.ArtifactActionService;
import io.matrix.actions.ArtifactWriter;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.LearningTrace;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P2 — the measured end-to-end run of the closed loop.
 *
 * <p>This is the measurement the design asks for: one question over one workspace, the
 * plan the kernel made, the method it chose, the permission, the artifact, the effect
 * observed by a reader that did not write it, the trace that was kept, and what the
 * journey cost — measured against the no-learning baseline, which is the same journey
 * with the trace consult disabled.</p>
 *
 * <p><b>What the numbers are.</b> The latencies are means over the repetitions the test
 * prints, measured in the same process on the same machine, with the workspace and the
 * output root held fixed. They are the cost of the journey, not a benchmark of the
 * machine: what they are for is the comparison between the journey that consults the
 * trace and the journey that does not.</p>
 */
class ClosedLoopReportJourneyMeasurementTest {

    /** The workspace the journey runs over; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The question the journey asks. */
    private static final String QUESTION = "What is photosynthesis?";

    /** The idempotency key the two runs of one journey share. */
    private static final String KEY = "measurement-key";

    /** How many times the replayed journey is measured. */
    private static final int REPETITIONS = 5;

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

    @Test
    @DisplayName("the measured end-to-end run: the plan, the method, the effect, the trace, the cost")
    void theMeasuredRun() throws Exception {
        Path root = root("loop");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        KnowledgeWorkbench bench = workbench("loop");
        ArtifactReportResource route = route(bench, actions, new ReportJourneyPlanner(),
                new CognitiveService());

        // ── the first journey: the workspace is asked and the artifact is written ──
        long started = System.nanoTime();
        Response first = route.report(WORKSPACE, request());
        long firstNanos = System.nanoTime() - started;
        ArtifactReportResource.ReportResponse body = success(first);
        System.out.println("### the question: " + QUESTION);
        System.out.println("### the workspace: " + WORKSPACE);
        System.out.println("### the first journey: the status " + first.getStatus() + ", the method "
                + ReportJourneyMethod.REFERENCE_ID + ", the disposition " + body.disposition()
                + ", " + firstNanos / 1_000_000.0 + " ms");
        System.out.println("### the artifact: the name " + body.artifactName() + ", the locator "
                + body.artifactLocator() + ", the sha256 " + body.sha256());

        // ── the observation: read by a reader that did not write ──────────────────
        ArtifactObserver.Observation observation = ArtifactObserver.observe(actions.writer(),
                body.taskId(), body.artifactName(), body.sha256());
        System.out.println("### the observation: the artifact is there "
                + observation.exists() + ", " + observation.bytes() + " bytes, the digest matches "
                + observation.matches() + ", the index holds " + observation.stagedRecords()
                + " STAGED and " + observation.committedRecords() + " COMMITTED record(s), the "
                + "effect is verified " + observation.verified());

        // ── the trace: what happened, kept where the next task can read it ───────
        List<LearningTrace.Step> steps = route.cognitive.store()
                .events(ReportJourneyTrace.episodeId(body.taskId()));
        for (LearningTrace.Step step : steps) {
            System.out.println("### the trace: " + step.phase() + " | " + step.actor() + " | "
                    + step.detail());
        }

        // ── the second journey: the kernel consults the trace and chooses ─────────
        long replayNanos = 0L;
        for (int repetition = 0; repetition < REPETITIONS; repetition++) {
            started = System.nanoTime();
            Response second = route.report(WORKSPACE, request());
            replayNanos += System.nanoTime() - started;
            assertThat(second.getStatus()).isEqualTo(200);
            assertThat(success(second).disposition()).isEqualTo("REPLAYED");
        }
        System.out.println("### the replayed journey: the mean " + (replayNanos / 1_000_000.0
                / REPETITIONS) + " ms over " + REPETITIONS + " repetition(s)");

        // ── the no-learning baseline: the same journey, the trace consult disabled ─
        Path baselineRoot = root("baseline");
        ArtifactActionService baselineActions = new ArtifactActionService(true,
                baselineRoot.toString());
        ArtifactReportResource baseline = route(workbench("baseline"), baselineActions,
                new ReportJourneyPlanner(), new CognitiveService());
        // the no-learning baseline: the same journey, the same trace written, but the
        // retained journey is not read back, so the workspace is asked every time
        baseline.consultRetainedTrace = false;
        baseline.report(WORKSPACE, request());
        long baselineNanos = 0L;
        for (int repetition = 0; repetition < REPETITIONS; repetition++) {
            started = System.nanoTime();
            baseline.report(WORKSPACE, request());
            baselineNanos += System.nanoTime() - started;
        }
        double baselineMean = baselineNanos / 1_000_000.0 / REPETITIONS;
        double replayMean = replayNanos / 1_000_000.0 / REPETITIONS;
        System.out.println("### the replayed journey chose: "
                + detailOf(route.cognitive.store().events(
                        ReportJourneyTrace.episodeId(body.taskId())),
                        ReportJourneyTrace.PHASE_PLAN));
        System.out.println("### the baseline (the trace consult disabled): the mean " + baselineMean
                + " ms over " + REPETITIONS + " repetition(s)");
        System.out.println("### the gain: the replayed journey costs " + (baselineMean - replayMean)
                + " ms less than the baseline (" + (baselineMean == 0.0 ? 0.0
                        : Math.round(100.0 * (baselineMean - replayMean) / baselineMean)) + "%)");

        assertThat(replayMean)
                .as("the journey that consults the trace asks the workspace no second time")
                .isLessThan(baselineMean);
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

    /** The request the measurement makes. */
    private static ArtifactReportResource.ReportRequest request() {
        return new ArtifactReportResource.ReportRequest(QUESTION, null, Boolean.TRUE, KEY, null);
    }

    /** The body of a write that happened. */
    private static ArtifactReportResource.ReportResponse success(Response response) {
        assertThat(response.getEntity()).isInstanceOf(ArtifactReportResource.ReportResponse.class);
        return (ArtifactReportResource.ReportResponse) response.getEntity();
    }

    /** The detail of the LAST step of a phase. */
    private static String detailOf(List<LearningTrace.Step> steps, String phase) {
        return steps.stream()
                .filter(step -> step.phase().equals(phase))
                .map(LearningTrace.Step::detail)
                .reduce((first, second) -> second)
                .orElse("");
    }

    /** The writer over the measured root. */
    @SuppressWarnings("unused")
    private static ArtifactWriter writer(ArtifactActionService actions) {
        return actions.writer();
    }
}
