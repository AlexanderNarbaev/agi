package io.matrix.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyOutcome;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import io.matrix.cognitive.report.ReportProcedure;
import io.matrix.cognitive.report.ReportProcedureLearner;
import io.matrix.cognitive.report.ReportProcedureMethod;
import io.matrix.crj.evidence.SourceSpanOracle;
import io.matrix.security.NetworkExposure;
import io.matrix.testsupport.RepositoryPaths;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / H2 — the SERVED TRANSFER measurement: can a learned, versioned procedure answer a
 * <b>previously unseen but related</b> question, after a restart, on the path the product
 * really serves?
 *
 * <p><b>What this milestone measures.</b> The contract's REUSE row — "different related task
 * retrieves accepted knowledge/procedure and changes the choice, prediction or result" — against
 * the two controls it must beat: the stateless reference (arm A) and the exact replay (arm B).
 * The contract names the trap: "Cached identical queries must be scored separately from
 * genuinely new task instances. If arm C wins only exact repeats, do not call it learning
 * transfer."</p>
 *
 * <p><b>Why the corpus is new.</b> The measurements so far ran on a corpus whose input domain
 * is exhausted, so there was no genuine transfer case to run. This milestone adds a
 * <b>synthetic, test-owned</b> corpus — four documents on one technical subject — and draws
 * its gold from what the <b>production</b> acquisition path actually extracted
 * ({@code docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json}). The gold is the source's own
 * sentence, byte-identical; the oracle re-reads the corpus bytes and never calls the
 * retriever, the workbench or the selector.</p>
 *
 * <p><b>What the fixture does, and why it restarts.</b> The fixture serves the OBSERVED
 * questions through the real route, learns a procedure from the journeys' retained traces,
 * promotes it on held-out VALIDATION cases, then <b>closes the durable store and opens it
 * again</b> — the procedure is then used by a process that did not learn it. A learning
 * episode that is not followed by a restart does not show that a competence is durable.</p>
 *
 * <p><b>What is asserted, and what is only measured.</b> The assertions are the ones a reader
 * can check against the artefacts: the procedure was promoted, it is versioned, it survived the
 * restart, the kernel <b>selected</b> it for a question it was never shown, the answer is
 * grounded in the source's own bytes, the effect is verified by a reader that did not write it,
 * the exact replay is <b>inapplicable</b> on a novel task, a changed payload under a reused
 * idempotency key is <b>not</b> a stale success, a superseded source revision is not answered
 * from, and a question outside the procedure's support set is not answered out of it. The arms
 * are compared <b>per metric</b> — grounding, disposition, citations, effect and resource cost —
 * and no aggregate "winner" is invented after the fact. <b>Latency is measured and never
 * asserted</b>: it is machine-sensitive, and a fast answer is not evidence of learning.</p>
 *
 * <p><b>What is NOT claimed here.</b> This is a DEVELOPMENT measurement on the solver's own
 * split. The sealed evaluation is <b>NOT EXECUTED</b>: the isolation a sealed split needs — an
 * implementer that cannot read the gold and an evaluator that cannot read the solver — has not
 * been established from this executor, and the coordinator's instruction records that
 * explicitly. No conclusion about a sealed δ_MI may be drawn from these rows.</p>
 */
class CRJ01H2ServedTransferTest {

    /** The classpath directory the H2 corpus lives in. */
    private static final String CORPUS_RESOURCE = "/crj01-h2/";

    /** The corpus files, in the order the production import path reads them. */
    private static final List<String> CORPUS_FILES = List.of(
            "h2-magnetic-anomalies.md",
            "h2-spreading-rate.md",
            "h2-deep-trenches.md",
            "h2-hydrothermal-vents.md");

    /** The split the cases are read from. */
    private static final String SPLIT = "docs-v2/engineering/CRJ-01-H2-DEV-SPLIT.json";

    /** The evidence artefact the measurement is written to. */
    private static final String EVIDENCE = "docs-v2/engineering/CRJ-01-H2-EVIDENCE.json";

    /** The corpus report the split's gold was written against. */
    private static final String CORPUS_REPORT = "docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json";

    /** The workspace the served path runs in. */
    private static final String WORKSPACE = "crj01-h2-served";

    /** The instant the promotion is recorded with — supplied, never read from a clock. */
    private static final String PROMOTED_AT = "2026-11-27T00:00:00Z";

    /** The thread's CPU clock. */
    private static final ThreadMXBean CPU = ManagementFactory.getThreadMXBean();

    @TempDir
    Path directory;

    /** The workbench this test opened. */
    private KnowledgeWorkbench bench;

    /** The kernel's service — closed and reopened by the restart. */
    private CognitiveService cognitive;

    /** The route, wired by hand, the way the container wires it. */
    private ArtifactReportResource route;

    /** The corpus, as the bytes on disk. */
    private Path corpusDirectory;

    /** The output root the action writes into. */
    private Path outputRoot;

    /** The cases of the split. */
    private List<Case> cases;

    /** The promoted procedure. */
    private Procedure procedure;

    /** The method id the procedure is served under. */
    private String procedureMethodId;

    /** The episodes the learning episode produced. */
    private List<String> observedEpisodes;

    // ── the fixture ────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
        corpusDirectory = Files.createDirectories(directory.resolve("corpus"));
        for (String file : CORPUS_FILES) {
            Files.write(corpusDirectory.resolve(file), corpusBytes(file));
        }
        cases = readCases();
        bench = KnowledgeWorkbench.at(Files.createDirectories(directory.resolve("db"))
                .resolve("workbench.sqlite").toString());
        for (String file : CORPUS_FILES) {
            bench.importDocument(WORKSPACE, corpusDirectory.resolve(file), file, "en");
        }
        outputRoot = Files.createDirectories(directory.resolve("output"));
        Files.setPosixFilePermissions(outputRoot, java.nio.file.attribute.PosixFilePermissions
                .fromString("rwx------"));
        cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route = route(bench, cognitive, outputRoot, new ReportJourneyPlanner());

        // ── THE LEARNING EPISODE — the product answers, and the answer is kept ──
        observedEpisodes = new ArrayList<>();
        for (Case observed : subset("OBSERVED")) {
            ArtifactReportResource.JourneyResult result = route.journey(WORKSPACE,
                    request(observed, "observed"), ReportJourneyMethod.REFERENCE_ID);
            assertThat(result.response().getStatus())
                    .as("the learning episode's journey was served: %s", observed.id())
                    .isEqualTo(201);
            observedEpisodes.add(ReportJourneyTrace.episodeId(result.outcome().taskId()));
        }

        // ── THE PROMOTION — on cases the procedure was NOT proposed from ────────
        List<ReportProcedureLearner.Observation> observations =
                ReportProcedureLearner.observationsFrom(cognitive.store(), observedEpisodes);
        assertThat(observations)
                .as("the retained journeys carry the observations the procedure is learned from")
                .hasSize(observedEpisodes.size());
        ReportProcedureLearner.Outcome outcome = new ReportProcedureLearner(bench, cognitive.store())
                .learn(WORKSPACE, observations, validationCases(), Set.of(), PROMOTED_AT);
        assertThat(outcome.promoted())
                .as("the candidate was promoted on the held-out cases: %s", outcome.reason())
                .isTrue();
        procedure = outcome.procedure();
        procedureMethodId = new ReportProcedureMethod(procedure).capability().id();

        // ── THE RESTART — the procedure must outlive the process that learned it ─
        cognitive.store().close();
        cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route = route(bench, cognitive, outputRoot, new ReportJourneyPlanner());
    }

    @AfterEach
    void tearDown() {
        NetworkExposure.reset();
        if (cognitive != null) {
            cognitive.store().close();
        }
        if (bench != null) {
            bench.close();
        }
    }

    // ── the acceptance ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a learned, versioned procedure survives the restart and still carries its standing")
    void theProcedureSurvivesTheRestart() {
        List<Procedure> stored = cognitive.store().procedures(WORKSPACE);

        assertThat(stored)
                .as("the promoted procedure is in the durable store after the restart")
                .hasSize(1);
        Procedure reloaded = stored.get(0);
        assertThat(reloaded.id()).isEqualTo(procedure.id());
        assertThat(reloaded.version())
                .as("a procedure is a versioned thing, and the version survives the restart")
                .isEqualTo(procedure.version());
        assertThat(reloaded.validationCases())
                .as("the standing rests on the held-out cases, and it survives the restart: the "
                        + "SPLIT's own number of validation cases, not a product constant — a "
                        + "split with more cases must not fail this milestone")
                .isEqualTo(validationCases().size());
        assertThat(reloaded.validationCases())
                .as("and the split carries enough validation cases for a promotion to be possible")
                .isGreaterThanOrEqualTo(ReportProcedureLearner.MINIMUM_CASES);
        assertThat(reloaded.validationAgreement()).isEqualTo(1.0);
        assertThat(reloaded.validated())
                .as("the cases were held out, so the agreement is an independent validation")
                .isTrue();
        assertThat(reloaded.learnedInEpisode())
                .as("the record names the episodes it was learned from")
                .contains(observedEpisodes.toArray(String[]::new));
        assertThat(ReportProcedure.supportOf(reloaded))
                .as("the support set survives with it")
                .isEqualTo(ReportProcedure.supportOf(procedure));
        assertThat(cognitive.store().procedureMeasuredCostNanos(WORKSPACE, reloaded.id(),
                reloaded.version()))
                .as("the cost the promotion MEASURED survives the restart; it is what the "
                        + "kernel's first selection rests on")
                .isNotNull()
                .isPositive();
    }

    @Test
    @DisplayName("a NOVEL question after the restart is answered by the LEARNED procedure, with "
            + "grounded evidence, a verified effect and a trace")
    void aNovelQuestionIsAnsweredByTheLearnedProcedure() {
        List<Case> novel = subset("NOVEL");
        assertThat(novel)
                .as("the split carries novel cases the procedure was never shown")
                .isNotEmpty();

        for (Case transfer : novel) {
            // the SERVED path: no method is forced, so the kernel chooses on its evidence
            ArtifactReportResource.JourneyResult result = route.journey(WORKSPACE,
                    request(transfer, "novel"), null);
            assertThat(result.response().getStatus())
                    .as("the novel question was served: %s", transfer.id())
                    .isEqualTo(201);
            ArtifactReportResource.ReportResponse served = body(result.response());

            // ── the kernel CHOSE the learned procedure, on its own evidence ────
            String plan = planOf(served.taskId());
            assertThat(plan)
                    .as("the kernel selected the learned procedure for the novel question %s: %s",
                            transfer.id(), plan)
                    .contains("procedure." + procedure.id());

            // ── the answer is grounded in the source's OWN bytes ─────────────────
            SourceSpanOracle.Verdict verdict = score(transfer, result.response(), oracle(),
                    servedFixture());
            assertThat(verdict.grounded())
                    .as("the novel answer is grounded in the source's own text: %s (%s)",
                            verdict.reason(), verdict.outcome())
                    .isTrue();

            // ── the effect was observed by a reader that did not write it ───────
            assertThat(result.outcome().effectVerified())
                    .as("the effect of the novel journey was verified")
                    .isTrue();
            assertThat(served.sha256())
                    .as("the served artifact carries a digest")
                    .matches("[0-9a-f]{64}");

            // ── the trace names every phase of the served journey ───────────────
            assertThat(tracePhases(served.taskId())).contains(
                    ReportJourneyTrace.PHASE_PLAN,
                    ReportJourneyTrace.PHASE_PERMISSION,
                    ReportJourneyTrace.PHASE_ACTION,
                    ReportJourneyTrace.PHASE_OBSERVE,
                    ReportJourneyTrace.PHASE_VERIFY,
                    ReportJourneyTrace.PHASE_RESULT,
                    ReportJourneyTrace.PHASE_RETAINED);

            // ── the provenance is the source the claim came from ───────────────
            for (ArtifactReportResource.EvidenceReference reference : served.evidence()) {
                assertThat(reference.sourceId())
                        .as("every cited claim names the source it came from")
                        .isNotNull();
                assertThat(reference.status())
                        .as("a cited claim is not silently a CANDIDATE: %s", reference.proposition())
                        .isNotEqualTo(String.valueOf(ClaimStatus.CANDIDATE));
            }
        }
    }

    @Test
    @DisplayName("the exact replay is INAPPLICABLE on a novel task and serves an exact repeat")
    void theReplayIsInapplicableOnANovelTask() {
        Case transfer = subset("NOVEL").get(0);

        // ── the novel task: the replay's precondition cannot hold ──────────────
        ArtifactReportResource.JourneyResult novel = route.journey(WORKSPACE,
                request(transfer, "replay-novel"), ReportJourneyMethod.REPLAY_ID);
        assertThat(novel.response().getStatus())
                .as("the replay cannot answer a task it never saw")
                .isEqualTo(422);
        assertThat(score(transfer, novel.response(), oracle(), servedFixture()).grounded())
                .as("the replay grounds nothing on a novel task")
                .isFalse();

        // ── the exact repeat: the replay serves what it retained ───────────────
        route.journey(WORKSPACE, request(transfer, "replay-repeat"), ReportJourneyMethod.REFERENCE_ID);
        ArtifactReportResource.JourneyResult repeat = route.journey(WORKSPACE,
                request(transfer, "replay-repeat"), ReportJourneyMethod.REPLAY_ID);
        assertThat(repeat.outcome().disposition())
                .as("the replay serves the retained journey on an exact repeat")
                .isEqualTo("REPLAYED");
        assertThat(score(transfer, repeat.response(), oracle(), servedFixture()).grounded())
                .as("the replay IS grounded on an exact repeat — which is why the novel subset "
                        + "is measured apart from it")
                .isTrue();
    }

    @Test
    @DisplayName("a changed payload under a reused idempotency key is NOT a stale success")
    void aChangedPayloadIsNotAStaleSuccess() {
        Case first = subset("NOVEL").get(0);
        Case second = subset("NOVEL").get(1);
        // the SAME key, the first time
        ArtifactReportResource.JourneyResult served = route.journey(WORKSPACE,
                new ArtifactReportResource.ReportRequest(first.question(), null, Boolean.TRUE,
                        first.idempotencyKey(), first.level()), null);
        assertThat(served.response().getStatus()).isEqualTo(201);
        String digest = body(served.response()).sha256();

        // the SAME key, a DIFFERENT question
        Response conflict = route.report(WORKSPACE, new ArtifactReportResource.ReportRequest(
                second.question(), null, Boolean.TRUE, first.idempotencyKey(), first.level()));

        assertThat(conflict.getStatus())
                .as("a reused key with a changed payload is a conflict, not a success")
                .isEqualTo(409);
        Object entity = conflict.getEntity();
        assertThat(entity).as("the refusal names the reason").isInstanceOf(Map.class);
        assertThat(String.valueOf(((Map<?, ?>) entity).get("error")))
                .as("the refusal names the writer's own disposition")
                .isEqualTo("REFUSED_IDENTITY");
        assertThat(body(served.response()).sha256())
                .as("the committed artifact is untouched by the refused request")
                .isEqualTo(digest);
    }

    @Test
    @DisplayName("a superseded source revision is not answered from, and the provenance names the "
            + "current revision")
    void aSupersededRevisionIsNotAnsweredFrom() throws Exception {
        Case transfer = subset("NOVEL").get(0);
        route.journey(WORKSPACE, request(transfer, "revision"), null);

        // ── the revision: a NEW version of an in-support document ─────────────
        //
        // The chain is what supersedes the old source: the FIRST event names the source
        // that was there, and the SECOND names its successor. An event that names only the
        // successor does not supersede anything — the document's first revision IS the event
        // that says which source it replaces.
        String file = CORPUS_FILES.get(0);
        io.matrix.knowledge.Source superseded = sourceOf(file);
        String documentKey = "h2-document-" + file;
        bench.selectSourceRevision(new SourceRevisionSelection(
                "h2-revision-1", WORKSPACE, documentKey,
                superseded.id(), superseded.contentHash(), null));
        Path revised = directory.resolve("revised-" + file);
        Files.writeString(revised, Files.readString(corpusDirectory.resolve(file))
                + "\nThe revised survey adds one further track across the basin.\n",
                StandardCharsets.UTF_8);
        var imported = bench.importDocument(WORKSPACE, revised, file, "en").source();
        bench.selectSourceRevision(new SourceRevisionSelection(
                "h2-revision-2", WORKSPACE, documentKey,
                imported.sourceId(), imported.contentHash(), "h2-revision-1"));

        // ── the answer after the revision ─────────────────────────────────────
        ArtifactReportResource.JourneyResult result = route.journey(WORKSPACE,
                request(transfer, "revision-after"), null);
        assertThat(result.response().getStatus()).isEqualTo(201);
        ArtifactReportResource.ReportResponse served = body(result.response());
        String plan = planOf(served.taskId());

        assertThat(plan)
                .as("the procedure's support set no longer holds, so the reference answers")
                .doesNotContain("procedure." + procedure.id());
        assertThat(served.evidence())
                .as("no answer cites the superseded revision: the provenance of a claim whose "
                        + "source was superseded is not the superseded source")
                .allSatisfy(reference -> assertThat(reference.sourceId())
                        .isNotEqualTo(superseded.id()));
        assertThat(result.outcome().effectVerified())
                .as("the journey after the revision was still served and its effect verified")
                .isTrue();
    }

    @Test
    @DisplayName("a question OUTSIDE the procedure's support set is not answered out of it")
    void aQuestionOutsideTheSupportSetIsNotAnsweredOutOfIt() {
        List<Case> outside = subset("OUT_OF_SUPPORT");
        assertThat(outside)
                .as("the split carries the control cases whose evidence is outside the support")
                .isNotEmpty();

        for (Case control : outside) {
            // ── the reference CAN answer it: the workspace holds the evidence ──
            ArtifactReportResource.JourneyResult reference = route.journey(WORKSPACE,
                    request(control, "control-reference"), ReportJourneyMethod.REFERENCE_ID);
            assertThat(score(control, reference.response(), oracle(), servedFixture()).grounded())
                    .as("the reference answers the out-of-support case from the workspace: %s",
                            control.id())
                    .isTrue();

            // ── the LEARNED COMPETENCE does not reach it ────────────────────────
            ArtifactReportResource.JourneyResult procedure = route.journey(WORKSPACE,
                    request(control, "control-procedure"), procedureMethodId);
            assertThat(score(control, procedure.response(), oracle(), servedFixture()).grounded())
                    .as("the procedure's support does not carry the evidence of %s, so the "
                            + "learned competence does not answer it", control.id())
                    .isFalse();

            // ── WHAT THE SERVED PATH DOES IS MEASURED, NOT ASSUMED ────────────
            //
            // The procedure's applicability domain is the PRESENCE of its support set: a
            // statement about the workspace, not about the request. The kernel therefore
            // offers the procedure for a question the support cannot answer, and the
            // served answer is the restricted reading. That is the architecture's own
            // design ("the support set is applied to whatever question is asked"), and
            // the consequence is a MEASURED limitation of the learned competence — not a
            // property this test may bless. It is recorded in the evidence rows under the
            // "served" variant, and it is stated in the milestone's disposition.
            ArtifactReportResource.JourneyResult served = route.journey(WORKSPACE,
                    request(control, "control-served"), null);
            assertThat(served.response().getStatus())
                    .as("the out-of-support question was served: %s", control.id())
                    .isEqualTo(201);
            assertThat(served.outcome().effectVerified())
                    .as("the out-of-support journey's effect was verified: %s", control.id())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("the arms are measured on the SAME cases, per metric, and the rows are retained")
    void theArmsAreMeasuredOnTheSameCases() throws Exception {
        List<Row> rows = new ArrayList<>();
        for (String arm : List.of("a", "b", "c")) {
            ArmFixture fixture = arm(arm);
            String methodId = switch (arm) {
                case "a" -> ReportJourneyMethod.REFERENCE_ID;
                case "b" -> ReportJourneyMethod.REPLAY_ID;
                default -> fixture.procedureMethodId();
            };
            for (Case testCase : measured()) {
                if ("b".equals(arm)) {
                    rows.add(measure(fixture, arm, "novel", testCase, methodId, false));
                    rows.add(measure(fixture, arm, "repeat", testCase, methodId, true));
                } else {
                    rows.add(measure(fixture, arm, "measured", testCase, methodId, false));
                }
                if ("c".equals(arm)) {
                    // the SERVED variant: no method is forced, so the kernel chooses. It is
                    // the path the product really runs, and it is recorded beside the
                    // forced arms rather than assumed to agree with them.
                    rows.add(measure(fixture, arm, "served", testCase, null, false));
                }
            }
            fixture.close();
        }
        assertThat(rows).as("every arm produced a row for every case").isNotEmpty();

        writeEvidence(rows);

        // ── the per-metric comparison, and NO aggregate winner ─────────────────
        Map<String, Long> grounded = new LinkedHashMap<>();
        Map<String, Long> total = new LinkedHashMap<>();
        for (String key : List.of("a/measured", "b/novel", "b/repeat", "c/measured",
                "c/served")) {
            List<Row> selected = rows.stream().filter(row -> row.armVariant().equals(key)).toList();
            grounded.put(key, selected.stream().filter(Row::grounded).count());
            total.put(key, (long) selected.size());
        }
        assertThat(total.get("a/measured"))
                .as("the arms are measured on the same cases, so their denominators agree")
                .isEqualTo(total.get("c/measured"));
        assertThat(total.get("b/novel")).isEqualTo(total.get("a/measured"));

        // the replay is inapplicable on a novel task: the whole point of separating the subsets
        assertThat(grounded.get("b/novel"))
                .as("the replay answers no novel task — an exact repeat is not a transfer")
                .isLessThan(grounded.get("a/measured"));

        // the learned procedure must at least reach the reference on the novel subset
        List<Row> novel = rows.stream()
                .filter(row -> "c/measured".equals(row.armVariant()))
                .filter(row -> "NOVEL".equals(row.subset()))
                .toList();
        List<Row> reference = rows.stream()
                .filter(row -> "a/measured".equals(row.armVariant()))
                .filter(row -> "NOVEL".equals(row.subset()))
                .toList();
        assertThat(novel).hasSameSizeAs(reference);
        assertThat(novel.stream().filter(row -> "UNGROUNDED".equals(row.oracleOutcome())).count())
                .as("no NOVEL case is left UNGROUNDED by the learned procedure")
                .isZero();
        assertThat(novel.stream().filter(Row::grounded).count())
                .as("every NOVEL case carries a positive oracle verdict: a cited gold for a "
                        + "positive case, the expected disposition for a negative-premise one")
                .isEqualTo(novel.size());
    }

    // ── the arms ───────────────────────────────────────────────────────────────

    /**
     * One arm's own world: its own workspace, its own store, its own output root.
     *
     * @param arm               the arm's name
     * @param workspace         the arm's workspace
     * @param bench             the arm's workbench
     * @param cognitive         the arm's kernel service
     * @param route             the arm's route
     * @param outputRoot        the arm's output root
     * @param procedureMethodId arm C's method id
     */
    private record ArmFixture(String arm, String workspace, KnowledgeWorkbench bench,
            CognitiveService cognitive, ArtifactReportResource route, Path outputRoot,
            String procedureMethodId) {

        /** Closes what the arm opened. */
        void close() {
            cognitive.store().close();
            bench.close();
        }
    }

    /**
     * One arm's world, built from scratch.
     *
     * @param arm the arm's name
     * @return the fixture
     */
    private ArmFixture arm(String arm) throws Exception {
        Path armRoot = Files.createDirectories(directory.resolve("arm-" + arm));
        Path corpus = Files.createDirectories(armRoot.resolve("corpus"));
        for (String file : CORPUS_FILES) {
            Files.write(corpus.resolve(file), corpusBytes(file));
        }
        String workspace = "crj01-h2-arm-" + arm;
        KnowledgeWorkbench armBench = KnowledgeWorkbench.at(
                Files.createDirectories(armRoot.resolve("db")).resolve("w.sqlite").toString());
        for (String file : CORPUS_FILES) {
            armBench.importDocument(workspace, corpus.resolve(file), file, "en");
        }
        Path root = Files.createDirectories(armRoot.resolve("output"));
        Files.setPosixFilePermissions(root, java.nio.file.attribute.PosixFilePermissions
                .fromString("rwx------"));
        CognitiveService service = new CognitiveService();
        service.workbench(armBench);
        ArtifactReportResource armRoute = route(armBench, service, root, new ReportJourneyPlanner());

        String methodId = null;
        if ("c".equals(arm)) {
            // ── arm C has no method until a procedure has been PROMOTED ────────
            List<String> episodes = new ArrayList<>();
            for (Case observed : subset("OBSERVED")) {
                ArtifactReportResource.JourneyResult result = armRoute.journey(workspace,
                        request(observed, "observed"), ReportJourneyMethod.REFERENCE_ID);
                assertThat(result.response().getStatus()).isEqualTo(201);
                episodes.add(ReportJourneyTrace.episodeId(result.outcome().taskId()));
            }
            List<ReportProcedureLearner.Observation> observations =
                    ReportProcedureLearner.observationsFrom(service.store(), episodes);
            ReportProcedureLearner.Outcome outcome =
                    new ReportProcedureLearner(armBench, service.store())
                            .learn(workspace, observations, validationCases(), Set.of(),
                                    PROMOTED_AT);
            assertThat(outcome.promoted())
                    .as("arm C's procedure was promoted: %s", outcome.reason())
                    .isTrue();
            methodId = new ReportProcedureMethod(outcome.procedure()).capability().id();
        }
        // ── THE RESTART, for every arm alike, so their conditions match ────────
        service.store().close();
        CognitiveService restarted = new CognitiveService();
        restarted.workbench(armBench);
        armRoute = route(armBench, restarted, root, new ReportJourneyPlanner());
        return new ArmFixture(arm, workspace, armBench, restarted, armRoute, root, methodId);
    }

    /**
     * Measures one case on one arm.
     *
     * @param fixture  the arm
     * @param arm      the arm's name
     * @param variant  the arm's variant
     * @param testCase the case
     * @param methodId the method to force
     * @param prime    whether the request is served once before the measured run
     * @return the row
     */
    private Row measure(ArmFixture fixture, String arm, String variant, Case testCase,
            String methodId, boolean prime) {
        // the SERVED variant is a NEW request: it carries its own idempotency key, so that
        // the kernel's choice is made for a request the workspace has not served yet. With
        // the forced run's key the identity would already be committed, and the cheapest
        // admitted method would be the replay of that very run — which would measure the
        // cache instead of the served path.
        String key = methodId == null
                ? testCase.idempotencyKey() + "-served"
                : testCase.idempotencyKey();
        ArtifactReportResource.ReportRequest request = new ArtifactReportResource.ReportRequest(
                testCase.question(), null, Boolean.TRUE, key, testCase.level());
        if (prime) {
            // the replay cannot prime itself: its precondition is that the identity was
            // ALREADY retained, so the priming run uses the reference
            fixture.route.journey(fixture.workspace, request, ReportJourneyMethod.REFERENCE_ID);
        }
        long cpuBefore = CPU.getCurrentThreadCpuTime();
        long wallStart = System.nanoTime();
        ArtifactReportResource.JourneyResult result;
        try {
            result = fixture.route.journey(fixture.workspace, request, methodId);
            // a null method id means the kernel chooses: that is the served path.
        } catch (RuntimeException failure) {
            // a CRASH is a ROW, not a missing row
            return new Row(testCase.id(), testCase.subset(), arm, variant, methodId, "", "", "",
                    false, "CRASHED", failure.getMessage(), List.of(), 0, 0,
                    System.nanoTime() - wallStart, CPU.getCurrentThreadCpuTime() - cpuBefore,
                    true, false, "", "", 0, "the arm threw", failure.toString());
        }
        long wall = System.nanoTime() - wallStart;
        long cpu = CPU.getCurrentThreadCpuTime() - cpuBefore;
        ReportJourneyOutcome outcome = result.outcome();
        List<SourceSpanOracle.CitedClaim> cited = new ArrayList<>();
        Object entity = result.response() == null ? null : result.response().getEntity();
        if (entity instanceof ArtifactReportResource.ReportResponse served) {
            for (ArtifactReportResource.EvidenceReference reference : served.evidence()) {
                cited.add(new SourceSpanOracle.CitedClaim(reference.claimId(),
                        reference.proposition(), reference.status(), reference.sourceId()));
            }
        }
        SourceSpanOracle.Verdict verdict = score(testCase, result.response(), oracle(fixture),
                fixture);
        String reason = outcome.reason();
        if (outcome.refusalReasons() != null && !outcome.refusalReasons().isEmpty()) {
            reason = (reason == null ? "" : reason + " | refused: ")
                    + String.join("; ", outcome.refusalReasons());
        }
        return new Row(testCase.id(), testCase.subset(), arm, variant, outcome.methodId(),
                outcome.disposition(), outcome.epistemicDisposition(), outcome.premiseStatus(),
                verdict.grounded(), verdict.outcome().name(), verdict.reason(),
                outcome.evidenceClaimIds(), cited.size(), matchedGold(testCase, cited),
                wall, cpu, "REFUSED".equals(outcome.disposition()) && !verdict.grounded(),
                outcome.effectVerified(), outcome.sha256(),
                String.join(",", outcome.tracePhases()), outcome.conflictCount(), reason, null);
    }

    // ── the helpers ────────────────────────────────────────────────────────────

    /**
     * One case of the split.
     *
     * @param id                  the case's id
     * @param caseClass           the case's class
     * @param subset              the case's subset
     * @param level               the level
     * @param question            the question
     * @param expectedDisposition the expected disposition
     * @param positive            whether the case expects an answer
     * @param expectedField       the disposition field a negative case is scored against
     * @param expectedProvenance  the expected refuting source
     * @param corpusFile          the corpus file the gold is in
     * @param corpusSha256        the digest of that file
     * @param goldPropositions    the gold propositions, verbatim
     * @param goldClaimIds        the production claim ids
     * @param goldSourceId        the source the gold was introduced by
     * @param idempotencyKey      the case's fresh key
     */
    private record Case(String id, String caseClass, String subset, String level, String question,
                        String expectedDisposition, boolean positive, String expectedField,
                        String expectedProvenance, String corpusFile, String corpusSha256,
                        List<String> goldPropositions, List<String> goldClaimIds,
                        String goldSourceId, String idempotencyKey) {
    }

    /**
     * One row of the measurement: one case, one arm, one variant.
     *
     * @param caseId                  the case
     * @param subset                  the case's subset
     * @param arm                     the arm
     * @param variant                 the arm's variant
     * @param methodId                the method that ran
     * @param disposition             the action's disposition
     * @param epistemicDisposition    the answer's epistemic disposition
     * @param premiseStatus           the premise's status
     * @param grounded                whether the oracle grounded the answer
     * @param oracleOutcome           the oracle's outcome
     * @param oracleReason            why the oracle decided so
     * @param citedClaimIds           the claims the answer cited
     * @param citations               how many claims the answer cited
     * @param goldPropositionsMatched how many of the gold propositions were cited
     * @param wallNanos               the wall time of the call
     * @param cpuNanos                the CPU time of the call
     * @param inapplicable            whether the arm could not run the case
     * @param effectVerified          whether the effect was verified
     * @param artifactSha256          the artifact's digest
     * @param traceRef                the trace's phases
     * @param conflictCount           the conflicts the answer carried
     * @param reason                  the route's own reason
     * @param error                   the error text, when the call failed
     */
    private record Row(String caseId, String subset, String arm, String variant, String methodId,
                        String disposition, String epistemicDisposition, String premiseStatus,
                        boolean grounded, String oracleOutcome, String oracleReason,
                        List<String> citedClaimIds, int citations, int goldPropositionsMatched,
                        long wallNanos, long cpuNanos, boolean inapplicable, boolean effectVerified,
                        String artifactSha256, String traceRef, int conflictCount, String reason,
                        String error) {

        Row {
            citedClaimIds = List.copyOf(citedClaimIds == null ? List.of() : citedClaimIds);
        }

        /** The arm and its variant, as the summary reads them. */
        String armVariant() {
            return arm + "/" + variant;
        }
    }

    /** The served fixture, as the oracle reads an arm's workspace. */
    private ArmFixture servedFixture() {
        return new ArmFixture("served", WORKSPACE, bench, cognitive, route, outputRoot, null);
    }

    /** The cases of one subset. */
    private List<Case> subset(String name) {
        List<Case> selected = new ArrayList<>();
        for (Case testCase : cases) {
            if (name.equals(testCase.subset())) {
                selected.add(testCase);
            }
        }
        return selected;
    }

    /** The cases the arms are measured on: everything the procedure was not learned from. */
    private List<Case> measured() {
        List<Case> selected = new ArrayList<>();
        for (Case testCase : cases) {
            if (!"OBSERVED".equals(testCase.subset())) {
                selected.add(testCase);
            }
        }
        return selected;
    }

    /** The held-out cases of the promotion. */
    private List<ReportProcedureLearner.Case> validationCases() {
        List<ReportProcedureLearner.Case> validation = new ArrayList<>();
        for (Case held : subset("VALIDATION")) {
            validation.add(new ReportProcedureLearner.Case(held.id(), held.question(),
                    AbstractionLevel.INTRODUCTORY));
        }
        return validation;
    }

    /**
     * The request the served path takes.
     *
     * @param testCase the case
     * @param purpose  what the request is for; it keeps the keys apart
     * @return the request
     */
    private static ArtifactReportResource.ReportRequest request(Case testCase, String purpose) {
        return new ArtifactReportResource.ReportRequest(testCase.question(), null, Boolean.TRUE,
                testCase.idempotencyKey() + "-" + purpose, testCase.level());
    }

    /** The route, wired by hand, the way the container wires it. */
    private static ArtifactReportResource route(KnowledgeWorkbench bench, CognitiveService cognitive,
            Path outputRoot, ReportJourneyPlanner planner) {
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = new ArtifactActionService(true, outputRoot.toString());
        route.planner = planner;
        route.cognitive = cognitive;
        return route;
    }

    /** The response's body. */
    private static ArtifactReportResource.ReportResponse body(Response response) {
        Object entity = response.getEntity();
        assertThat(entity).as("the response carries a body").isInstanceOf(
                ArtifactReportResource.ReportResponse.class);
        return (ArtifactReportResource.ReportResponse) entity;
    }

    /** The plan the kernel made, read from the trace. */
    private String planOf(String taskId) {
        for (io.matrix.cognitive.LearningTrace.Step step : traceSteps(taskId)) {
            if (ReportJourneyTrace.PHASE_PLAN.equals(step.phase())) {
                return step.detail();
            }
        }
        return "";
    }

    /** The trace's phases. */
    private List<String> tracePhases(String taskId) {
        return traceSteps(taskId).stream().map(io.matrix.cognitive.LearningTrace.Step::phase).toList();
    }

    /** The trace's steps. */
    private List<io.matrix.cognitive.LearningTrace.Step> traceSteps(String taskId) {
        return cognitive.store().events(ReportJourneyTrace.episodeId(taskId));
    }

    /** The source the workspace gave one corpus document. */
    private io.matrix.knowledge.Source sourceOf(String file) throws IOException {
        String text = Files.readString(corpusDirectory.resolve(file), StandardCharsets.UTF_8);
        for (Claim claim : bench.workspace(WORKSPACE).claims()) {
            if (claim.introducedBySourceId() != null && text.contains(claim.proposition())) {
                for (io.matrix.knowledge.Source source : bench.workspace(WORKSPACE).sources()) {
                    if (source.id().equals(claim.introducedBySourceId())) {
                        return source;
                    }
                }
            }
        }
        throw new IllegalStateException("the workspace holds no claims for " + file);
    }

    /** The oracle over the corpus, resolving the gold's ids in the served workspace. */
    private SourceSpanOracle oracle() {
        return oracle(servedFixture());
    }

    /** The oracle over the corpus, resolving the gold's ids in an arm's workspace. */
    private SourceSpanOracle oracle(ArmFixture fixture) {
        Map<String, String> idsByProposition = new LinkedHashMap<>();
        for (Claim claim : fixture.bench.workspace(fixture.workspace).claims()) {
            idsByProposition.put(claim.proposition(), claim.id());
        }
        return new SourceSpanOracle(corpusDirectory, idsByProposition);
    }

    /** The gold of a case, as the oracle reads it. */
    private SourceSpanOracle.GoldCase gold(Case testCase, ArmFixture fixture) {
        String expectedSource = null;
        if (testCase.expectedProvenance() != null && !testCase.expectedProvenance().isBlank()) {
            // the split names the source the corpus report froze; the workspace under test
            // gave the SAME document a different source id, and the refutation the answer
            // names is the one of THIS workspace
            for (Claim claim : fixture.bench.workspace(fixture.workspace).claims()) {
                if (testCase.goldPropositions().contains(claim.proposition())
                        && claim.introducedBySourceId() != null) {
                    expectedSource = claim.introducedBySourceId();
                    break;
                }
            }
            if (expectedSource == null) {
                expectedSource = testCase.expectedProvenance();
            }
        }
        return new SourceSpanOracle.GoldCase(testCase.id(), testCase.corpusFile(),
                testCase.corpusSha256(), testCase.goldPropositions(), testCase.goldClaimIds(),
                testCase.positive(), fieldOf(testCase.expectedField()),
                testCase.expectedDisposition(), expectedSource);
    }

    /** Scores a served answer with the oracle. */
    private SourceSpanOracle.Verdict score(Case testCase, Response response,
            SourceSpanOracle oracle, ArmFixture fixture) {
        List<SourceSpanOracle.CitedClaim> cited = new ArrayList<>();
        List<String> refuting = new ArrayList<>();
        String disposition = "REFUSED";
        String epistemic = "";
        String premise = "";
        Object entity = response == null ? null : response.getEntity();
        if (entity instanceof ArtifactReportResource.ReportResponse served) {
            disposition = served.disposition();
            epistemic = served.epistemicDisposition();
            if (served.premise() != null) {
                premise = served.premise().status();
                refuting.addAll(served.premise().refutingSourceIds());
            }
            for (ArtifactReportResource.EvidenceReference reference : served.evidence()) {
                cited.add(new SourceSpanOracle.CitedClaim(reference.claimId(),
                        reference.proposition(), reference.status(), reference.sourceId()));
            }
        }
        return oracle.score(gold(testCase, fixture), new SourceSpanOracle.ServedAnswer(
                disposition, epistemic, premise, cited, refuting));
    }

    /** How many of the case's gold propositions the answer cited, byte-identically. */
    private static int matchedGold(Case testCase, List<SourceSpanOracle.CitedClaim> cited) {
        int matched = 0;
        for (SourceSpanOracle.CitedClaim claim : cited) {
            for (String gold : testCase.goldPropositions()) {
                if (gold.equals(claim.proposition())) {
                    matched++;
                    break;
                }
            }
        }
        return matched;
    }

    /** The field a negative case's expected disposition lives in. */
    private static SourceSpanOracle.Field fieldOf(String name) {
        if (name == null) {
            return SourceSpanOracle.Field.PREMISE;
        }
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "EPISTEMIC" -> SourceSpanOracle.Field.EPISTEMIC;
            case "ACTION" -> SourceSpanOracle.Field.ACTION;
            default -> SourceSpanOracle.Field.PREMISE;
        };
    }

    // ── the split, the evidence and the digests ───────────────────────────────

    /** Reads the split. */
    private List<Case> readCases() throws IOException {
        Path split = RepositoryPaths.root().resolve(SPLIT);
        assertThat(split).as("the DEV split %s is present", SPLIT).exists();
        ObjectMapper json = JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = json.readValue(Files.readString(split, StandardCharsets.UTF_8),
                Map.class);
        List<Case> read = new ArrayList<>();
        for (Object entry : (List<?>) parsed.get("cases")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            List<String> propositions = new ArrayList<>();
            List<String> claimIds = new ArrayList<>();
            String sourceId = null;
            if (map.get("goldClaims") instanceof List<?> golds) {
                for (Object gold : golds) {
                    if (gold instanceof Map<?, ?> goldMap) {
                        propositions.add(String.valueOf(goldMap.get("proposition")));
                        claimIds.add(String.valueOf(goldMap.get("claimId")));
                        if (sourceId == null) {
                            sourceId = String.valueOf(goldMap.get("sourceId"));
                        }
                    }
                }
            }
            read.add(new Case(String.valueOf(map.get("id")), String.valueOf(map.get("class")),
                    String.valueOf(map.get("subset")), String.valueOf(map.get("level")),
                    String.valueOf(map.get("question")),
                    String.valueOf(map.get("expectedDisposition")),
                    !Boolean.FALSE.equals(map.get("positive")),
                    String.valueOf(map.get("expectedField")),
                    map.get("expectedProvenance") == null ? null
                            : String.valueOf(map.get("expectedProvenance")),
                    String.valueOf(map.get("corpusFile")),
                    String.valueOf(map.get("corpusSha256")), propositions, claimIds, sourceId,
                    String.valueOf(map.get("idempotencyKey"))));
        }
        assertThat(read).as("the split carries cases").isNotEmpty();
        return read;
    }

    /** Writes the evidence artefact: one row per (case, arm, variant). */
    private void writeEvidence(List<Row> rows) throws Exception {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("report", "crj-01-h2-evidence");
        evidence.put("version", "1.0");
        evidence.put("date", "2026-11-27");
        evidence.put("baselineSha", "7d8c695fa75385ba09e70267e11b7f7301e415c2");
        evidence.put("split", SPLIT);
        evidence.put("splitSha256", sha256(Files.readAllBytes(
                RepositoryPaths.root().resolve(SPLIT))));
        evidence.put("corpusReport", CORPUS_REPORT);
        evidence.put("corpusReportSha256", sha256(Files.readAllBytes(
                RepositoryPaths.root().resolve(CORPUS_REPORT))));
        evidence.put("sealed", false);
        evidence.put("sealedNote", "the sealed evaluation is NOT EXECUTED: the isolation it "
                + "needs has not been established from this executor");
        evidence.put("arms", Map.of("a", "the stateless reference",
                "b", "the exact replay, measured twice: on a novel task and on an exact repeat",
                "c", "the promoted, versioned procedure"));
        List<Map<String, Object>> written = new ArrayList<>();
        for (Row row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("caseId", row.caseId());
            map.put("subset", row.subset());
            map.put("arm", row.arm());
            map.put("variant", row.variant());
            map.put("methodId", row.methodId());
            map.put("disposition", row.disposition());
            map.put("epistemicDisposition", row.epistemicDisposition());
            map.put("premiseStatus", row.premiseStatus());
            map.put("grounded", row.grounded());
            map.put("oracleOutcome", row.oracleOutcome());
            map.put("oracleReason", row.oracleReason());
            map.put("citedClaimIds", row.citedClaimIds());
            map.put("citations", row.citations());
            map.put("goldPropositionsMatched", row.goldPropositionsMatched());
            map.put("wallNanos", row.wallNanos());
            map.put("cpuNanos", row.cpuNanos());
            map.put("inapplicable", row.inapplicable());
            map.put("effectVerified", row.effectVerified());
            map.put("artifactSha256", row.artifactSha256());
            map.put("traceRef", row.traceRef());
            map.put("conflictCount", row.conflictCount());
            map.put("reason", row.reason());
            map.put("error", row.error());
            written.add(map);
        }
        evidence.put("cases", written);
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String key : List.of("a/measured", "b/novel", "b/repeat", "c/measured",
                "c/served")) {
            List<Row> selected = rows.stream().filter(row -> row.armVariant().equals(key)).toList();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("cases", selected.size());
            line.put("grounded", selected.stream().filter(Row::grounded).count());
            line.put("groundedMeaning", "the oracle's POSITIVE verdict: a cited gold for a "
                    + "positive case, the expected disposition for a negative-premise case");
            // the breakdown, so the headline count is not read as one metric
            Map<String, Long> byOutcome = new LinkedHashMap<>();
            for (Row row : selected) {
                byOutcome.merge(row.oracleOutcome(), 1L, Long::sum);
            }
            line.put("byOracleOutcome", byOutcome);
            line.put("inapplicable", selected.stream().filter(Row::inapplicable).count());
            line.put("effectVerified", selected.stream().filter(Row::effectVerified).count());
            line.put("wallNanosTotal", selected.stream().mapToLong(Row::wallNanos).sum());
            line.put("wallNanosMedian", median(selected.stream().mapToLong(Row::wallNanos)
                    .boxed().collect(java.util.stream.Collectors.toList())));
            line.put("cpuNanosTotal", selected.stream().mapToLong(Row::cpuNanos).sum());
            summary.put(key, line);
        }
        evidence.put("summary", summary);
        evidence.put("latencyNote", "the wall and CPU times are machine-sensitive; they are "
                + "recorded and are NOT asserted, and a faster answer is not evidence of learning");

        ObjectMapper json = JsonMapper.builder().build();
        String text = json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence);
        Path artefact = RepositoryPaths.root().resolve(EVIDENCE);
        Files.createDirectories(artefact.getParent());
        Files.writeString(artefact, text, StandardCharsets.UTF_8);
        // the artefact must be READABLE and must carry the rows — comparing the text with
        // itself would assert nothing
        Map<?, ?> reread = JsonMapper.builder().build().readValue(artefact.toFile(), Map.class);
        assertThat(reread.get("cases")).as("the artefact carries its rows").isNotNull();
        assertThat(((List<?>) reread.get("cases")).size())
                .as("every measured row is in the artefact")
                .isEqualTo(rows.size());
    }

    /** The median of a list of measurements. */
    private static long median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    /** The bytes of one corpus file, as the classpath holds them. */
    private static byte[] corpusBytes(String file) throws IOException {
        try (InputStream stream = CRJ01H2ServedTransferTest.class
                .getResourceAsStream(CORPUS_RESOURCE + file)) {
            assertThat(stream).as("the corpus file %s is on the classpath", file).isNotNull();
            return stream.readAllBytes();
        }
    }

    /** The digest of some bytes. */
    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
