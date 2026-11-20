package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyDiff;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyOutcome;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.security.NetworkExposure;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.lang.reflect.Method;
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
 * P2 — the counterfactual arm: the same request, the method forced.
 *
 * <p><b>What the directive asks for.</b> "Counterfactual/replay tests must show what
 * changed and what did not." The two arms differ in exactly one thing — the method that
 * ran — and every other aspect of what happened is compared and marked, so that the
 * difference in what the caller observed can be attributed to the method rather than to
 * anything else.</p>
 *
 * <p><b>What forcing may not do.</b> It may not widen the consent, the output-root
 * control, the writer's lease or the idempotency, and it may not be reachable by an HTTP
 * caller. Both are pinned here.</p>
 */
class ReportJourneyCounterfactualTest {

    /** The workspace the journey runs over; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The question both arms ask. */
    private static final String QUESTION = "What is photosynthesis?";

    /** The idempotency key both arms share: the same request, twice. */
    private static final String KEY = "counterfactual-key";

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

    // ── the diff ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the counterfactual shows what changed and what did not")
    void theCounterfactualShowsWhatChangedAndWhatDidNot() throws Exception {
        Path root = root("counterfactual");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("counterfactual"), actions,
                new ReportJourneyPlanner(), new CognitiveService());

        // THE FIRST JOURNEY: there is no trace yet, so the evidence chooses the reference
        // and the workspace is asked.
        ArtifactReportResource.JourneyResult first = route.journey(WORKSPACE, request(), null);
        assertThat(first.response().getStatus()).isEqualTo(201);
        assertThat(first.outcome().methodId()).isEqualTo(ReportJourneyMethod.REFERENCE_ID);

        // THE ARM THE EVIDENCE CHOICES: the same request again, and now the trace says
        // the journey has already happened.
        ArtifactReportResource.JourneyResult chosen = route.journey(WORKSPACE, request(), null);
        assertThat(chosen.response().getStatus()).isEqualTo(200);
        assertThat(chosen.outcome().methodId()).isEqualTo(ReportJourneyMethod.REPLAY_ID);

        // THE COUNTERFACTUAL ARM: the same request, the same workspace, the same consent
        // — with the OTHER method forced. The arms differ in exactly one thing.
        ArtifactReportResource.JourneyResult forced = route.journey(WORKSPACE, request(),
                ReportJourneyMethod.REFERENCE_ID);
        assertThat(forced.response().getStatus()).isEqualTo(200);
        assertThat(forced.outcome().methodId()).isEqualTo(ReportJourneyMethod.REFERENCE_ID);

        ReportJourneyDiff diff = ReportJourneyDiff.between(chosen.outcome(), forced.outcome());

        // WHAT CHANGED — the method, and everything that follows from it.
        assertThat(diff.differences()).extracting(ReportJourneyDiff.Aspect::aspect)
                .contains("the method that ran", "whether the method was forced",
                        "the reason the method was chosen", "the limitations the caller was told",
                        "the trace's steps");
        assertThat(diff.differences()).filteredOn(aspect -> aspect.aspect().equals(
                "the method that ran")).singleElement()
                .satisfies(aspect -> {
                    assertThat(aspect.reference()).isEqualTo(ReportJourneyMethod.REPLAY_ID);
                    assertThat(aspect.counterfactual()).isEqualTo(ReportJourneyMethod.REFERENCE_ID);
                });
        assertThat(diff.differences()).filteredOn(aspect -> aspect.aspect().equals(
                "the reason the method was chosen")).singleElement()
                .satisfies(aspect -> assertThat(aspect.counterfactual())
                        .as("the forced choice says what the evidence would have chosen")
                        .contains("FORCED").contains(ReportJourneyMethod.REFERENCE_ID)
                        .contains(ReportJourneyMethod.REPLAY_ID));

        // WHAT DID NOT CHANGE — the artifact, the answer and the effect are the same.
        assertThat(diff.unities()).extracting(ReportJourneyDiff.Aspect::aspect)
                .contains("the action's identity", "the artifact's name",
                        "the artifact's locator", "the sha256 the response reported",
                        "the sha256 measured on the artifact's bytes",
                        "the index's records for the identity",
                        "whether the effect was verified",
                        "the epistemic disposition of the answer",
                        "the claims the answer cited",
                        "the status of the question's premise",
                        "the conflicts the answer carried",
                        "the readings the answer carried",
                        "the refusal reasons the caller was told",
                        "the disposition of the action");
        assertThat(diff.unities()).filteredOn(aspect -> aspect.aspect().equals(
                "the sha256 the response reported")).singleElement()
                .satisfies(aspect -> assertThat(aspect.reference())
                        .isEqualTo(chosen.outcome().sha256())
                        .isEqualTo(forced.outcome().sha256()));
        assertThat(diff.unities()).filteredOn(aspect -> aspect.aspect().equals(
                "the index's records for the identity")).singleElement()
                .satisfies(aspect -> assertThat(aspect.reference())
                        .as("the replay writes no second record: one identity, one artifact")
                        .isEqualTo("[STAGED, COMMITTED]"));
    }

    // ── the safety of the forced path ───────────────────────────────────────

    @Test
    @DisplayName("forcing a method does not bypass the consent")
    void forcingDoesNotBypassTheConsent() throws Exception {
        Path root = root("noconsent");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("noconsent"), actions,
                new ReportJourneyPlanner(), new CognitiveService());

        ArtifactReportResource.JourneyResult forced = route.journey(WORKSPACE,
                new ArtifactReportResource.ReportRequest(QUESTION, null, null, KEY, null),
                ReportJourneyMethod.REPLAY_ID);

        assertThat(forced.response().getStatus())
                .as("the consent is the caller's statement and no method may replace it")
                .isEqualTo(403);
        assertThat(forced.outcome().disposition()).isEqualTo("REFUSED");
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("forcing a method does not bypass the output root's control")
    void forcingDoesNotBypassTheOutputRoot() throws Exception {
        Path root = root("off");
        ArtifactActionService actions = new ArtifactActionService(false, root.toString());
        ArtifactReportResource route = route(workbench("off"), actions,
                new ReportJourneyPlanner(), new CognitiveService());

        ArtifactReportResource.JourneyResult forced = route.journey(WORKSPACE, request(),
                ReportJourneyMethod.REPLAY_ID);

        assertThat(forced.response().getStatus())
                .as("a feature that is off is unavailable, whatever the method")
                .isEqualTo(503);
        assertThat(artifactFiles(root)).isEmpty();

        // and the root confinement itself: a name that tries to leave the root
        Path live = root("live");
        ArtifactActionService liveActions = new ArtifactActionService(true, live.toString());
        ArtifactReportResource liveRoute = route(workbench("live"), liveActions,
                new ReportJourneyPlanner(), new CognitiveService());
        ArtifactReportResource.JourneyResult traversal = liveRoute.journey(WORKSPACE,
                new ArtifactReportResource.ReportRequest(QUESTION, "../outside.md", Boolean.TRUE,
                        KEY, null), ReportJourneyMethod.REPLAY_ID);
        assertThat(traversal.response().getStatus()).isEqualTo(400);
        assertThat(artifactFiles(live)).isEmpty();
    }

    @Test
    @DisplayName("forcing a method does not bypass the idempotency")
    void forcingDoesNotBypassTheIdempotency() throws Exception {
        Path root = root("idempotency");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("idempotency"), actions,
                new ReportJourneyPlanner(), new CognitiveService());

        route.journey(WORKSPACE, request(), null);
        ArtifactReportResource.JourneyResult same = route.journey(WORKSPACE, request(), null);

        assertThat(same.response().getStatus()).isEqualTo(200);
        assertThat(same.outcome().disposition()).isEqualTo("REPLAYED");
        assertThat(artifactFiles(root))
                .as("one identity, one artifact")
                .hasSize(1);

        // a different key is a different identity, and a forced method does not merge them
        ArtifactReportResource.JourneyResult other = route.journey(WORKSPACE,
                new ArtifactReportResource.ReportRequest(QUESTION, null, Boolean.TRUE,
                        "another-key", null), ReportJourneyMethod.REFERENCE_ID);
        assertThat(other.response().getStatus()).isEqualTo(201);
        assertThat(other.outcome().taskId())
                .as("a different key names a different action")
                .isNotEqualTo(same.outcome().taskId());
        assertThat(artifactFiles(root)).hasSize(2);
    }

    @Test
    @DisplayName("a forced method whose preconditions do not hold is refused")
    void aForcedMethodOutsideItsDomainIsRefused() throws Exception {
        Path root = root("outside");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = route(workbench("outside"), actions,
                new ReportJourneyPlanner(), new CognitiveService());

        // the replay needs a retained journey, and there is none: forcing it does not make
        // it possible
        ArtifactReportResource.JourneyResult forced = route.journey(WORKSPACE, request(),
                ReportJourneyMethod.REPLAY_ID);

        assertThat(forced.response().getStatus())
                .as("forcing chooses what runs; it does not make the run possible")
                .isEqualTo(422);
        assertThat(artifactFiles(root)).isEmpty();
    }

    @Test
    @DisplayName("an HTTP caller cannot force a method")
    void anHttpCallerCannotForceAMethod() {
        // the only method of the resource that carries an HTTP annotation is the one the
        // serving path uses, and the request carries no field a caller could turn into a
        // method
        List<String> annotated = new ArrayList<>();
        for (Method method : ArtifactReportResource.class.getDeclaredMethods()) {
            if (method.getAnnotation(jakarta.ws.rs.POST.class) != null
                    || method.getAnnotation(jakarta.ws.rs.GET.class) != null
                    || method.getAnnotation(jakarta.ws.rs.PUT.class) != null
                    || method.getAnnotation(jakarta.ws.rs.DELETE.class) != null) {
                annotated.add(method.getName());
            }
        }
        assertThat(annotated).containsExactly("report");
        assertThat(List.of(ArtifactReportResource.ReportRequest.class.getRecordComponents()))
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("method", "methodId", "forcedMethod");
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

    /** The request both arms make. */
    private static ArtifactReportResource.ReportRequest request() {
        return new ArtifactReportResource.ReportRequest(QUESTION, null, Boolean.TRUE, KEY, null);
    }

    /** The artifact files a root holds: the reports, not the index and not the lease. */
    private static List<String> artifactFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }
}
