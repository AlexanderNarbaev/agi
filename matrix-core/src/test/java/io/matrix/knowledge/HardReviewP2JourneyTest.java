package io.matrix.knowledge;

import io.matrix.actions.ArtifactActionService;
import io.matrix.actions.ArtifactWriter;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.LearningTrace;
import io.matrix.cognitive.report.ArtifactObserver;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * HARD REVIEW — P2, the closed-loop cognitive action.
 *
 * <p>The method choice, the observer's independence, the trace's integrity, the replay's
 * precondition, the safety boundary and the claimed win.</p>
 */
class HardReviewP2JourneyTest {

    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;
    private static final String QUESTION = "What is photosynthesis?";
    private static final EnumSet<PosixFilePermission> OWNER_ONLY =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);

    @TempDir
    Path directory;

    // ── the method choice, the trace and the replay ─────────────────────────

    @Test
    @DisplayName("P2-a: the method is chosen by the selector, and the trace says why")
    void theMethodChoiceAndTheTrace() throws Exception {
        KnowledgeWorkbench bench = bench("p2a");
        Path root = root("p2a");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(bench, actions, new ReportJourneyPlanner());
        Response first = resource.report(WORKSPACE, request("key-p2a"));
        System.out.println("### P2-a - the first journey: " + first.getStatus());
        Response second = resource.report(WORKSPACE, request("key-p2a"));
        System.out.println("  the second journey: " + second.getStatus());
        List<LearningTrace.Step> steps = traceStepsOf(bench, episode(bench, "key-p2a"));
        for (LearningTrace.Step step : steps) {
            System.out.println("  " + step.phase() + " / " + step.actor() + " :: "
                    + trim(step.detail(), 220));
        }
    }

    @Test
    @DisplayName("P2-b: the observer is not fooled by the writer's own bookkeeping")
    void theObserverIsIndependent() throws Exception {
        KnowledgeWorkbench bench = bench("p2b");
        Path root = root("p2b");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(bench, actions, new ReportJourneyPlanner());
        Response response = resource.report(WORKSPACE, request("key-p2b"));
        System.out.println("### P2-b - the observer: " + response.getStatus());
        ArtifactWriter writer = actions.writer();
        String fileName = writtenFileName(root);
        String identity = actions.identity(WORKSPACE, "key-p2b", QUESTION);
        String digest = ArtifactObserver.sha256(Files.readAllBytes(root.resolve(fileName)));
        ArtifactObserver.Observation clean = ArtifactObserver.observe(writer, identity, fileName, digest);
        System.out.println("  before the change: exists=" + clean.exists() + " matches="
                + clean.matches() + " staged=" + clean.stagedRecords() + " committed="
                + clean.committedRecords() + " verified=" + clean.verified());
        // the artifact is replaced on disk: the bytes now differ from the reported digest
        Files.writeString(root.resolve(fileName), "a different artifact\n");
        ArtifactObserver.Observation after = ArtifactObserver.observe(writer, identity, fileName, digest);
        System.out.println("  after the change:  exists=" + after.exists() + " matches="
                + after.matches() + " committed=" + after.committedRecords() + " verified="
                + after.verified());
        System.out.println("  detail: " + after.detail());
        // the artifact is gone: the index still says COMMITTED
        Files.delete(root.resolve(fileName));
        ArtifactObserver.Observation gone = ArtifactObserver.observe(writer, identity, fileName, digest);
        System.out.println("  after the removal: exists=" + gone.exists() + " committed="
                + gone.committedRecords() + " verified=" + gone.verified());
        System.out.println("  detail: " + gone.detail());
    }

    @Test
    @DisplayName("P2-c: the replay is refused when the workspace's content changed")
    void theReplayPrecondition() throws Exception {
        KnowledgeWorkbench bench = bench("p2c");
        Path root = root("p2c");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(bench, actions, new ReportJourneyPlanner());
        resource.report(WORKSPACE, request("key-p2c"));
        System.out.println("### P2-c - the workspace changes under the retained journey");
        String fingerprintBefore = fingerprintOf(bench);
        // a claim is added to the workspace: its content changed, its name did not
        Path extra = directory.resolve("extra.md");
        Files.writeString(extra, "A trench is a long narrow depression in the ocean floor.\n");
        bench.importDocument(WORKSPACE, extra, "extra.md", "en");
        String fingerprintAfter = fingerprintOf(bench);
        System.out.println("  the fingerprint before=" + fingerprintBefore.substring(0, 16)
                + " after=" + fingerprintAfter.substring(0, 16)
                + " changed=" + !fingerprintBefore.equals(fingerprintAfter));
        Response after = resource.report(WORKSPACE, request("key-p2c"));
        System.out.println("  the journey after the change: " + after.getStatus());
        for (LearningTrace.Step step : traceStepsOf(bench, episode(bench, "key-p2c"))) {
            if ("PLAN".equals(step.phase())) {
                System.out.println("  the plan: " + trim(step.detail(), 220));
            }
        }
    }

    @Test
    @DisplayName("P2-d: the consent is checked before the plan, and the name is bounded")
    void theSafetyBoundary() throws Exception {
        KnowledgeWorkbench bench = bench("p2d");
        Path root = root("p2d");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        // a planner with NO method: every request would be a 422 if the plan were consulted
        ArtifactReportResource resource = resource(bench, actions,
                new ReportJourneyPlanner(new io.matrix.cognitive.algorithms.CostLedger(), List.of()));
        Response noConsent = resource.report(WORKSPACE,
                new ArtifactReportResource.ReportRequest(QUESTION, null, null, "key-noconsent", null));
        System.out.println("### P2-d - the safety boundary");
        System.out.println("  no consent and no method: " + noConsent.getStatus());
        Response withConsent = resource.report(WORKSPACE,
                new ArtifactReportResource.ReportRequest(QUESTION, null, Boolean.TRUE, "key-ok", null));
        System.out.println("  consent but no method: " + withConsent.getStatus());
        // the report name is bounded
        ArtifactReportResource resource2 = resource(bench, actions, new ReportJourneyPlanner());
        Response escaping = resource2.report(WORKSPACE,
                new ArtifactReportResource.ReportRequest(QUESTION, "../escape.md", Boolean.TRUE,
                        "key-escape", null));
        System.out.println("  a name with a parent reference: " + escaping.getStatus());
    }

    @Test
    @DisplayName("P2-e: the replay against the no-learning baseline, interleaved")
    void theInterleavedMeasurement() throws Exception {
        KnowledgeWorkbench bench = bench("p2e");
        Path root = root("p2e");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource learner = resource(bench, actions, new ReportJourneyPlanner());
        ArtifactReportResource baseline = resource(bench, actions, new ReportJourneyPlanner());
        baseline.consultRetainedTrace = false;
        // the first journey writes the artifact and the trace; the arms are measured after
        learner.report(WORKSPACE, request("key-p2e"));
        List<Long> learning = new ArrayList<>();
        List<Long> noLearning = new ArrayList<>();
        for (int round = 0; round < 40; round++) {
            long started = System.nanoTime();
            learner.report(WORKSPACE, request("key-p2e"));
            learning.add((System.nanoTime() - started) / 1_000_000L);
            started = System.nanoTime();
            baseline.report(WORKSPACE, request("key-p2e"));
            noLearning.add((System.nanoTime() - started) / 1_000_000L);
        }
        System.out.println("### P2-e - the interleaved arms (40 rounds each)");
        System.out.println("  the replay arm:    median=" + median(learning) + " ms  min=" + min(learning)
                + " max=" + max(learning));
        System.out.println("  the no-learning arm: median=" + median(noLearning) + " ms  min=" + min(noLearning)
                + " max=" + max(noLearning));
        System.out.println("  the difference of the medians: " + (median(noLearning) - median(learning))
                + " ms");
    }

    @Test
    @DisplayName("P2-f: the journey runs when the trace cannot be written, and says so")
    void theTraceFailureIsVisible() throws Exception {
        KnowledgeWorkbench bench = bench("p2f");
        Path root = root("p2f");
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource resource = resource(bench, actions, new ReportJourneyPlanner());
        resource.cognitive = null;
        Response response = resource.report(WORKSPACE, request("key-p2f"));
        System.out.println("### P2-f - the trace is not writable: " + response.getStatus());
        String body = String.valueOf(response.getEntity());
        System.out.println("  the body says the trace was not kept: "
                + body.toLowerCase().contains("trace"));
        System.out.println("  the body: " + trim(body, 400));
        System.out.println("  the artifact on disk: " + writtenFileName(root));
    }

    // ── the fixtures ────────────────────────────────────────────────────────

    private KnowledgeWorkbench bench(String name) throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve(name + ".sqlite")
                        .toString());
        bench.loadBundledPackage(WORKSPACE);
        return bench;
    }

    private Path root(String name) throws Exception {
        Path root = Files.createDirectories(directory.resolve(name));
        Files.setPosixFilePermissions(root, OWNER_ONLY);
        return root;
    }

    private ArtifactReportResource resource(KnowledgeWorkbench bench, ArtifactActionService actions,
                                            ReportJourneyPlanner planner) {
        ArtifactReportResource resource = new ArtifactReportResource();
        resource.workbench = bench;
        resource.actions = actions;
        resource.planner = planner;
        CognitiveService cognitive = new CognitiveService();
        cognitive.workbench(bench);
        resource.cognitive = cognitive;
        return resource;
    }

    private static ArtifactReportResource.ReportRequest request(String key) {
        return new ArtifactReportResource.ReportRequest(QUESTION, null, Boolean.TRUE, key, null);
    }

    private List<LearningTrace.Step> traceSteps(KnowledgeWorkbench bench, String key) {
        return traceStepsOf(bench, ReportJourneyTrace.episodeId(
                actionsIdentity(bench, key)));
    }

    private static String actionsIdentity(KnowledgeWorkbench bench, String key) {
        try {
            ArtifactActionService actions = new ArtifactActionService(true,
                    Files.createTempDirectory("hardreview-id-").toString());
            return actions.identity(WORKSPACE, key, QUESTION);
        } catch (Exception unavailable) {
            return key;
        }
    }

    private static String episode(KnowledgeWorkbench bench, String key) {
        return ReportJourneyTrace.episodeId(actionsIdentity(bench, key));
    }

    private List<LearningTrace.Step> traceStepsOf(KnowledgeWorkbench bench, String episode) {
        List<LearningTrace.Step> steps = new ArrayList<>();
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            steps.addAll(cognitive.store().events(episode));
        } catch (RuntimeException unavailable) {
            System.out.println("  the trace is not readable: " + unavailable.getMessage());
        }
        return steps;
    }

    private static String fingerprintOf(KnowledgeWorkbench bench) {
        StringBuilder material = new StringBuilder();
        bench.workspace(WORKSPACE).claims().forEach(claim -> material.append(claim.id())
                .append('|').append(claim.proposition()).append('|').append(claim.status())
                .append('\n'));
        return ArtifactObserver.sha256(material.toString());
    }

    private static String writtenFileName(Path root) throws Exception {
        try (var files = Files.list(root)) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .findFirst().orElseThrow(() -> new IllegalStateException("nothing was written"));
        }
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        java.util.Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private static long min(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).min().orElse(-1);
    }

    private static long max(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).max().orElse(-1);
    }

    private static String trim(String text, int length) {
        if (text == null) {
            return "";
        }
        return text.length() <= length ? text : text.substring(0, length) + "…";
    }
}
