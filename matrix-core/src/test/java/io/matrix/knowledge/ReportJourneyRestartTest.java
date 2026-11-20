package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import io.matrix.security.NetworkExposure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P2 — the restart: the next analogous task consults the earlier experience.
 *
 * <p><b>What the directive asks for.</b> "On restart, demonstrate that a subsequent
 * analogous task consults the earlier experience." The trace lives in the knowledge
 * database, so it survives the process that wrote it — but a trace that survives is not
 * the same as a trace that is <i>consulted</i> after a restart, and only the second can
 * be demonstrated.</p>
 *
 * <p><b>What this test does.</b> It stops the process (the cognitive store and the
 * workbench are closed, the store's writer's lease is released) and starts a new one
 * over the same database, with new collaborators — the planner's ledger is empty again,
 * the ledger of measured costs is gone, and the only thing that carries over is what the
 * trace holds. The next analogous task then has to find that trace, choose the replay
 * because of it, and say why.</p>
 */
class ReportJourneyRestartTest {

    /** The workspace the journey runs over; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The question the journey asks. */
    private static final String QUESTION = "What is photosynthesis?";

    /** The idempotency key the two journeys share. */
    private static final String KEY = "restart-key";

    /** The permissions an authorized output root must have: nobody else may write to it. */
    private static final EnumSet<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    @TempDir
    Path directory;

    /** The workbenches this test opened, so that every one of them is closed. */
    private final java.util.List<KnowledgeWorkbench> workbenches = new java.util.ArrayList<>();

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
    @DisplayName("after a restart the next analogous task consults the earlier experience")
    void afterARestartTheNextTaskConsultsTheTrace() throws Exception {
        Path database = Files.createDirectories(directory.resolve("databases"))
                .resolve("restart.sqlite");
        Path root = root("restart");

        // ── the first process ────────────────────────────────────────────────
        KnowledgeWorkbench before = workbench(database, true);
        CognitiveService firstService = new CognitiveService();
        firstService.workbench(before);
        ArtifactReportResource first = route(before, new ArtifactActionService(true,
                root.toString()), new ReportJourneyPlanner(), firstService);

        ArtifactReportResource.JourneyResult initial = first.journey(WORKSPACE, request(), null);
        assertThat(initial.response().getStatus()).isEqualTo(201);
        assertThat(initial.outcome().methodId()).isEqualTo(ReportJourneyMethod.REFERENCE_ID);

        // ── STOP ──────────────────────────────────────────────────────────────
        // the cognitive store holds the database's writer's lease; it is released when the
        // store is closed, and the workbench's own store is closed with the workbench
        firstService.store().close();
        before.close();

        // ── the restarted process ─────────────────────────────────────────────
        // the same database, new collaborators: the ledger of measured costs is empty
        // again, and the only thing that carried over is the trace
        KnowledgeWorkbench after = workbench(database, false);
        CognitiveService secondService = new CognitiveService();
        secondService.workbench(after);
        ArtifactReportResource restarted = route(after, new ArtifactActionService(true,
                root.toString()), new ReportJourneyPlanner(), secondService);

        ArtifactReportResource.JourneyResult next = restarted.journey(WORKSPACE, request(), null);

        // THE NEXT ANALOGOUS TASK CONSULTED THE EARLIER EXPERIENCE
        assertThat(next.response().getStatus())
                .as("the journey that already happened is replayed, not done again")
                .isEqualTo(200);
        assertThat(next.outcome().methodId())
                .as("the trace that survived the restart chose the replay")
                .isEqualTo(ReportJourneyMethod.REPLAY_ID);
        assertThat(next.outcome().disposition()).isEqualTo("REPLAYED");
        assertThat(next.outcome().sha256()).isEqualTo(initial.outcome().sha256());

        // AND IT SAYS WHY: the reason is in the trace, which is where the journey recorded
        // what it did and why
        String plan = planOf(secondService, next.outcome().taskId());
        assertThat(plan)
                .as("the plan of the journey after the restart names the replay")
                .contains(ReportJourneyMethod.REPLAY_ID);
        assertThat(plan).contains("selected");
        assertThat(plan)
                .as("the decision record says that both methods were considered")
                .contains("2 admitted candidate(s)");

        // the trace of the first journey is still there: the episode grew, it was not
        // replaced
        List<io.matrix.cognitive.LearningTrace.Step> steps = secondService.store()
                .events(ReportJourneyTrace.episodeId(next.outcome().taskId()));
        assertThat(steps).isNotEmpty();
        assertThat(steps.stream()
                .filter(step -> ReportJourneyTrace.PHASE_PLAN.equals(step.phase())).count())
                .as("the trace holds both journeys")
                .isEqualTo(2);
    }

    // ── the fixtures ────────────────────────────────────────────────────────

    /** A workbench over the given database, optionally loading the bundled package. */
    private KnowledgeWorkbench workbench(Path database, boolean load) throws IOException {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        if (load) {
            bench.loadBundledPackage(WORKSPACE);
        }
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
        cognitive.workbench(bench);
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = actions;
        route.planner = planner;
        route.cognitive = cognitive;
        return route;
    }

    /** The request the journey makes. */
    private static ArtifactReportResource.ReportRequest request() {
        return new ArtifactReportResource.ReportRequest(QUESTION, null, Boolean.TRUE, KEY, null);
    }

    /** The plan step of the last journey of an identity. */
    private static String planOf(CognitiveService service, String taskId) {
        List<io.matrix.cognitive.LearningTrace.Step> steps = service.store()
                .events(ReportJourneyTrace.episodeId(taskId));
        String detail = "";
        for (io.matrix.cognitive.LearningTrace.Step step : steps) {
            if (ReportJourneyTrace.PHASE_PLAN.equals(step.phase())) {
                detail = step.detail();
            }
        }
        return detail;
    }
}
