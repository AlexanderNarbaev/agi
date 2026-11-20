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
 *
 * <p><b>Whether the arms share a store.</b> Two runs are reported. The first is the
 * laboratory: both arms run against ONE workbench, one cognitive store and one output
 * root, so the arm that consults the trace can find the journey the other arm wrote. The
 * second is the field: each arm gets its OWN workbench, its OWN cognitive store and its
 * OWN output root, so that no state crosses between the arms and each arm pays for its
 * own first journey. The two runs are reported separately because they answer different
 * questions: the laboratory asks what the replay saves inside one process, and the field
 * asks whether the saving survives when the arms cannot see each other at all.</p>
 *
 * <p><b>Why the arms are measured INTERLEAVED.</b> The first version measured one arm
 * entirely and then the other. The arm that ran first paid the warm-up of its own store
 * and of the JVM, and the arm that ran second benefited from it — so the measurement was
 * biased AGAINST the effect, by an amount that has nothing to do with the closed loop. On a
 * loaded runner that bias was larger than the effect itself, and the release lane failed a
 * test that had passed on the same commit in the fast lane: the failure was in the
 * measurement's ORDER, not in the code. The arms are now measured one journey each per
 * round, with the order of the two arms swapped every round, over an even number of
 * rounds — the same interleaved measurement the independent review used (40 interleaved
 * rounds). The effect is still asserted; only the bias is removed.</p>
 */
class ClosedLoopReportJourneyMeasurementTest {

    /** The workspace the journey runs over; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The question the journey asks. */
    private static final String QUESTION = "What is photosynthesis?";

    /** The idempotency key the two runs of one journey share. */
    private static final String KEY = "measurement-key";

    /**
     * How many rounds the INTERLEAVED measurement runs.
     *
     * <p>It is even, so that each arm runs first exactly as often as the other.</p>
     */
    private static final int INTERLEAVED_ROUNDS = 20;

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

        // ── the second journey: the kernel consults the trace and chooses ─────────
        // The two arms are measured INTERLEAVED (see the class's javadoc): one journey of
        // each arm per round, and the order of the two arms swaps every round, so neither
        // arm pays the warm-up of the other's store.
        double[] means = interleavedMeans(
                () -> {
                    Response second = route.report(WORKSPACE, request());
                    assertThat(second.getStatus()).isEqualTo(200);
                    assertThat(success(second).disposition()).isEqualTo("REPLAYED");
                },
                () -> baseline.report(WORKSPACE, request()),
                INTERLEAVED_ROUNDS);
        double replayMean = means[0];
        double baselineMean = means[1];
        System.out.println("### the replayed journey: the mean " + replayMean
                + " ms over " + INTERLEAVED_ROUNDS + " interleaved repetition(s)");
        System.out.println("### the replayed journey chose: "
                + detailOf(route.cognitive.store().events(
                        ReportJourneyTrace.episodeId(body.taskId())),
                        ReportJourneyTrace.PHASE_PLAN));
        System.out.println("### the baseline (the trace consult disabled): the mean " + baselineMean
                + " ms over " + INTERLEAVED_ROUNDS + " interleaved repetition(s)");
        System.out.println("### the gain: the replayed journey costs " + (baselineMean - replayMean)
                + " ms less than the baseline (" + (baselineMean == 0.0 ? 0.0
                        : Math.round(100.0 * (baselineMean - replayMean) / baselineMean)) + "%)");

        assertThat(replayMean)
                .as("the journey that consults the trace asks the workspace no second time")
                .isLessThan(baselineMean);
    }


    @Test
    @DisplayName("the measured counterfactual: the forced arm against the replay")
    void theMeasuredCounterfactual() throws Exception {
        Path root = root("counterfactual");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("counterfactual"), actions,
                new ReportJourneyPlanner(), new CognitiveService());

        // the first journey establishes the trace
        route.journey(WORKSPACE, request(), null);

        // THE TWO ARMS, MEASURED INTERLEAVED (see the class's javadoc): the arm the
        // evidence chooses (the replay) against the same request with the reference forced.
        ArtifactReportResource.JourneyResult[] chosen =
                new ArtifactReportResource.JourneyResult[1];
        ArtifactReportResource.JourneyResult[] forced =
                new ArtifactReportResource.JourneyResult[1];
        double[] means = interleavedMeans(
                () -> chosen[0] = route.journey(WORKSPACE, request(), null),
                () -> forced[0] = route.journey(WORKSPACE, request(),
                        io.matrix.cognitive.report.ReportJourneyMethod.REFERENCE_ID),
                INTERLEAVED_ROUNDS);
        double replayMean = means[0];
        double forcedMean = means[1];

        io.matrix.cognitive.report.ReportJourneyDiff diff =
                io.matrix.cognitive.report.ReportJourneyDiff.between(chosen[0].outcome(),
                        forced[0].outcome());
        System.out.println("### the replay arm: the mean " + replayMean + " ms over "
                + INTERLEAVED_ROUNDS + " interleaved repetition(s), the method "
                + chosen[0].outcome().methodId());
        System.out.println("### the forced arm: the mean " + forcedMean + " ms over "
                + INTERLEAVED_ROUNDS + " interleaved repetition(s), the method "
                + forced[0].outcome().methodId());
        System.out.println("### the forced arm costs " + (forcedMean - replayMean)
                + " ms more than the replay");
        for (io.matrix.cognitive.report.ReportJourneyDiff.Aspect aspect : diff.aspects()) {
            System.out.println("### the diff: " + aspect.aspect() + " | "
                    + (aspect.identical() ? "IDENTICAL" : "DIFFERENT") + " | "
                    + aspect.reference() + " | " + aspect.counterfactual());
        }

        assertThat(replayMean)
                .as("the replay asks the workspace no second time")
                .isLessThan(forcedMean);
        assertThat(diff.unities()).extracting(
                        io.matrix.cognitive.report.ReportJourneyDiff.Aspect::aspect)
                .as("the replay skips the ask WITHOUT changing what the caller receives")
                .contains("the sha256 the response reported",
                        "the sha256 measured on the artifact's bytes",
                        "the epistemic disposition of the answer",
                        "the claims the answer cited");
        assertThat(diff.differences()).extracting(
                        io.matrix.cognitive.report.ReportJourneyDiff.Aspect::aspect)
                .contains("the method that ran", "the reason the method was chosen");
    }

    @Test
    @DisplayName("the measured win with the arms' stores separated")
    void theMeasuredWinWithSeparateStores() throws Exception {
        // THE FIELD RUN: each arm has its own workbench, its own store and its own root.
        Path replayRoot = root("field-replay");
        ArtifactReportResource replayRoute = route(workbench("field-replay"),
                new ArtifactActionService(true, replayRoot.toString()),
                new ReportJourneyPlanner(), new CognitiveService());
        replayRoute.consultRetainedTrace = true;
        replayRoute.journey(WORKSPACE, request(), null);
        Path baselineRoot = root("field-baseline");
        ArtifactReportResource baselineRoute = route(workbench("field-baseline"),
                new ArtifactActionService(true, baselineRoot.toString()),
                new ReportJourneyPlanner(), new CognitiveService());
        baselineRoute.consultRetainedTrace = false;
        baselineRoute.journey(WORKSPACE, request(), null);
        // The arms are measured INTERLEAVED (see the class's javadoc): each arm pays its
        // own warm-up, and neither arm is measured on a store the other has warmed.
        double[] means = interleavedMeans(
                () -> replayRoute.journey(WORKSPACE, request(), null),
                () -> baselineRoute.journey(WORKSPACE, request(), null),
                INTERLEAVED_ROUNDS);
        double replayMean = means[0];
        double baselineMean = means[1];
        System.out.println("### the field run (the arms' stores separated): the replay "
                + replayMean + " ms, the baseline " + baselineMean + " ms, the replay costs "
                + (baselineMean - replayMean) + " ms less");
        assertThat(replayMean)
                .as("the replay asks the workspace no second time, in a store of its own")
                .isLessThan(baselineMean);
    }

    /** A call that may throw. */
    private interface Call {
        void run() throws Exception;
    }

    /** The cost of one call, in nanoseconds. */
    private static long timed(Call call) throws Exception {
        long started = System.nanoTime();
        call.run();
        return System.nanoTime() - started;
    }

    /**
     * Two arms, measured INTERLEAVED.
     *
     * <p>One journey of each arm per round, and the order of the two arms swaps every
     * round, so that neither arm is always the first to run and therefore neither arm pays
     * the other's warm-up. The number of rounds is even for the same reason.</p>
     *
     * @param first  the first arm's journey
     * @param second the second arm's journey
     * @param rounds how many rounds
     * @return the two means, in milliseconds, in the order of the arms
     */
    private static double[] interleavedMeans(Call first, Call second, int rounds) throws Exception {
        long firstNanos = 0L;
        long secondNanos = 0L;
        for (int round = 0; round < rounds; round++) {
            if (round % 2 == 0) {
                firstNanos += timed(first);
                secondNanos += timed(second);
            } else {
                secondNanos += timed(second);
                firstNanos += timed(first);
            }
        }
        return new double[] {firstNanos / 1_000_000.0 / rounds,
                secondNanos / 1_000_000.0 / rounds};
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
