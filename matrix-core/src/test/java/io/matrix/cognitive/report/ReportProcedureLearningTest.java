package io.matrix.cognitive.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.security.NetworkExposure;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CRJ-01 / OD-082 — the arm C mechanism, as a gate.
 *
 * <p>What is pinned here is the whole loop, and every claim asserts:</p>
 * <ul>
 *   <li>the learner REFUSES a candidate whose validation case was one of the episodes it was
 *       proposed from (the held-out-case rule), and refuses a candidate whose learning set
 *       carries a quarantined episode, and refuses when the quarantine list is ABSENT
 *       (the gate fails closed), and refuses below the minimum number of cases;</li>
 *   <li>the learner PROMOTES a candidate that agrees with the reference on every held-out
 *       case, and the promoted record carries its version, its measured agreement and the
 *       episodes it was learned from;</li>
 *   <li>the promoted procedure is DURABLE: a store opened over the same file after the
 *       learner is gone still holds it;</li>
 *   <li>a LATER, DIFFERENT question is answered by the promoted procedure on the served
 *       route — the trace names the procedure's capability, and the answer carries the
 *       procedure's provenance;</li>
 *   <li>a procedure whose support set is NOT present in the workspace is not offered, and
 *       the reference method answers instead.</li>
 * </ul>
 */
@DisplayName("CRJ-01 / OD-082 — a validated domain-A procedure")
class ReportProcedureLearningTest {

    /** The workspace the route is asked about; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The permissions an authorized output root must have: nobody else may write to it. */
    private static final EnumSet<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    /** The questions the procedure is proposed from — three journeys, one domain. */
    private static final List<String> OBSERVED_QUESTIONS = List.of(
            "What is photosynthesis?",
            "How do plants convert light into chemical energy?",
            "What does oxygenic photosynthesis release?");

    /** The questions the candidate must survive — five cases, none of them observed. */
    private static final List<String> HELD_OUT_QUESTIONS = List.of(
            "What is the role of water in photosynthesis?",
            "Which organisms perform oxygenic photosynthesis?",
            "What is the Calvin-Benson cycle?",
            "How does RuBisCO compete with oxygenation?",
            "What limits photosynthetic output in angiosperms?");

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

    // ── the refusals ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the learner fails closed when the quarantine list is absent")
    void theLearnerFailsClosedWithoutTheQuarantineList() throws Exception {
        Fixture fixture = fixture("noconsent-quarantine");
        ReportProcedureLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                fixture.observations(), fixture.cases(), null, "2026-11-20T00:00:00Z");

        assertThat(outcome.promoted()).isFalse();
        assertThat(outcome.reason())
                .as("a missing quarantine list must refuse, not admit the evaluation traffic")
                .contains("quarantine list is absent");
    }

    @Test
    @DisplayName("the learner refuses an episode that is quarantined")
    void theLearnerRefusesAQuarantinedEpisode() throws Exception {
        Fixture fixture = fixture("quarantined-episode");
        Set<String> quarantined = Set.of(fixture.observations().get(0).episodeId());

        ReportProcedureLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                fixture.observations(), fixture.cases(), quarantined, "2026-11-20T00:00:00Z");

        assertThat(outcome.promoted()).isFalse();
        assertThat(outcome.reason())
                .as("a quarantined episode is not learning-eligible")
                .contains("quarantined");
    }

    @Test
    @DisplayName("the learner refuses a validation case that was one of the observed episodes")
    void theLearnerRefusesALeakingValidationCase() throws Exception {
        Fixture fixture = fixture("leaking-case");
        List<ReportProcedureLearner.Case> cases = new ArrayList<>(fixture.cases());
        // the FIRST case asks exactly what the first observed journey asked
        cases.set(0, new ReportProcedureLearner.Case(cases.get(0).caseId(),
                OBSERVED_QUESTIONS.get(0), AbstractionLevel.INTRODUCTORY));

        ReportProcedureLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                fixture.observations(), cases, Set.of(), "2026-11-20T00:00:00Z");

        assertThat(outcome.promoted()).isFalse();
        assertThat(outcome.reason())
                .as("a case the procedure was proposed from is not held out")
                .contains("not held out");
    }

    @Test
    @DisplayName("the learner refuses to promote on fewer cases than the named minimum")
    void theLearnerRefusesBelowTheMinimumCaseCount() throws Exception {
        Fixture fixture = fixture("too-few-cases");
        List<ReportProcedureLearner.Case> cases = fixture.cases().subList(0, 2);

        ReportProcedureLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                fixture.observations(), cases, Set.of(), "2026-11-20T00:00:00Z");

        assertThat(outcome.promoted()).isFalse();
        assertThat(outcome.reason())
                .as("two cases are not evidence")
                .contains("at least " + ReportProcedureLearner.MINIMUM_CASES);
    }

    // ── the promotion, its durability and its use ───────────────────────────

    @Test
    @DisplayName("a candidate that agrees on every held-out case is promoted, with its provenance")
    void aCandidateThatAgreesIsPromoted() throws Exception {
        Fixture fixture = fixture("promoted");

        ReportProcedureLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                fixture.observations(), fixture.cases(), Set.of(), "2026-11-20T00:00:00Z");

        assertThat(outcome.promoted())
                .as("the candidate agreed on every held-out case: %s", outcome.reason())
                .isTrue();
        Procedure procedure = outcome.procedure();
        assertThat(procedure).isNotNull();
        assertThat(procedure.validationCases()).isEqualTo(fixture.cases().size());
        assertThat(procedure.validationAgreement()).isEqualTo(1.0);
        assertThat(procedure.validated())
                .as("the cases were held out, so the agreement is an independent validation")
                .isTrue();
        assertThat(procedure.learnedInEpisode())
                .as("the record names the episodes it was learned from")
                .contains(fixture.observations().get(0).episodeId());
        assertThat(procedure.validationNote())
                .as("the record says what its standing rests on")
                .contains("held-out case");
        assertThat(ReportProcedure.supportOf(procedure))
                .as("the procedure carries the support set it was validated on")
                .isNotEmpty();

        List<Procedure> stored = fixture.store().procedures(WORKSPACE);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).id()).isEqualTo(procedure.id());
    }

    @Test
    @DisplayName("the promoted procedure is durable: a store opened later still holds it")
    void thePromotedProcedureIsDurable() throws Exception {
        Fixture fixture = fixture("durable");
        fixture.learner().learn(WORKSPACE, fixture.observations(), fixture.cases(), Set.of(),
                "2026-11-20T00:00:00Z");
        fixture.store().close();

        CognitiveStore reopened = new CognitiveStore(fixture.database());

        List<Procedure> procedures = reopened.procedures(WORKSPACE);
        assertThat(procedures)
                .as("the procedure survives the process that learned it")
                .hasSize(1);
        assertThat(ReportProcedure.supportOf(procedures.get(0)))
                .as("the support set survives with it")
                .isNotEmpty();
        reopened.close();
    }

    @Test
    @DisplayName("a later, DIFFERENT question is answered by the promoted procedure")
    void aLaterDifferentQuestionIsAnsweredByTheProcedure() throws Exception {
        Fixture fixture = fixture("transfer");
        ReportProcedureLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                fixture.observations(), fixture.cases(), Set.of(), "2026-11-20T00:00:00Z");
        assertThat(outcome.promoted()).as("the candidate was promoted: %s", outcome.reason())
                .isTrue();

        // a question the procedure was NEVER proposed from and never validated on
        String laterQuestion = "What are the light-dependent reactions?";
        Response response = fixture.route().report(WORKSPACE,
                new ArtifactReportResource.ReportRequest(laterQuestion, null, true,
                        "key-later-question", null));

        assertThat(response.getStatus()).isEqualTo(201);
        ArtifactReportResource.ReportResponse body =
                (ArtifactReportResource.ReportResponse) response.getEntity();
        assertThat(body.traceReference()).isNotNull();

        String episodeId = "report-" + body.taskId();
        List<io.matrix.cognitive.LearningTrace.Step> steps =
                fixture.store().events(episodeId);
        String plan = steps.stream()
                .filter(step -> "PLAN".equals(step.phase()))
                .map(io.matrix.cognitive.LearningTrace.Step::detail)
                .findFirst()
                .orElse("");
        assertThat(plan)
                .as("the kernel chose the promoted procedure for the later question")
                .contains("procedure." + outcome.procedure().id());
    }

    @Test
    @DisplayName("a procedure whose support set is absent is not offered: the reference answers")
    void aProcedureWithoutItsSupportIsNotOffered() throws Exception {
        Fixture fixture = fixture("no-support");
        Set<String> absent = Set.of("source-that-is-not-in-the-workspace");
        Procedure procedure = ReportProcedure.record(absent, "a domain that is not present", 5,
                1.0, "episode-x", "validated on 5 held-out case(s)", "2026-11-20T00:00:00Z");
        fixture.store().recordProcedure(WORKSPACE, procedure);

        Response response = fixture.route().report(WORKSPACE,
                new ArtifactReportResource.ReportRequest("What is photosynthesis?", null, true,
                        "key-absent-support", null));

        assertThat(response.getStatus()).isEqualTo(201);
        ArtifactReportResource.ReportResponse body =
                (ArtifactReportResource.ReportResponse) response.getEntity();
        String plan = fixture.store().events("report-" + body.taskId()).stream()
                .filter(step -> "PLAN".equals(step.phase()))
                .map(io.matrix.cognitive.LearningTrace.Step::detail)
                .findFirst()
                .orElse("");
        assertThat(plan)
                .as("the procedure's domain does not hold, so the reference method answers")
                .doesNotContain("procedure.");
    }

    @Test
    @DisplayName("the procedure reads FEWER claims than the reference: the saving's mechanism")
    void theProcedureReadsFewerClaimsThanTheReference() throws Exception {
        Fixture fixture = fixture("cheaper");
        ReportProcedureLearner.Outcome outcome = fixture.learner().learn(WORKSPACE,
                fixture.observations(), fixture.cases(), Set.of(), "2026-11-20T00:00:00Z");
        assertThat(outcome.promoted()).as("the candidate was promoted: %s", outcome.reason())
                .isTrue();
        Set<String> support = ReportProcedure.supportOf(outcome.procedure());

        int everyClaim = fixture.bench().workspace(WORKSPACE).claims().size();
        int supportedClaims = (int) fixture.bench().workspace(WORKSPACE).claims().stream()
                .filter(claim -> support.contains(claim.introducedBySourceId()))
                .count();

        assertThat(supportedClaims)
                .as("the procedure's reading is narrower than the reference's: %d of %d claims",
                        supportedClaims, everyClaim)
                .isLessThan(everyClaim);
        assertThat(supportedClaims)
                .as("the narrower reading is not empty: a procedure that reads nothing is a trap")
                .isPositive();
    }

    // ── the fixture ─────────────────────────────────────────────────────────

    /** The pieces the test drives. */
    private record Fixture(KnowledgeWorkbench bench, CognitiveStore store,
            ArtifactReportResource route, ReportProcedureLearner learner,
            List<ReportProcedureLearner.Observation> observations,
            List<ReportProcedureLearner.Case> cases, Path database) {
    }

    /**
     * A workspace, a route, and the observations of three real journeys.
     *
     * @param name the fixture's name
     * @return the fixture
     */
    private Fixture fixture(String name) throws Exception {
        Path database = directory.resolve(name + ".sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(WORKSPACE);
        workbenches.add(bench);
        Path root = Files.createDirectories(directory.resolve(name + "-output"));
        Files.setPosixFilePermissions(root, OWNER_ONLY);
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = actions;
        route.planner = new ReportJourneyPlanner();
        CognitiveService cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route.cognitive = cognitive;
        CognitiveStore store = cognitive.store();

        // three real journeys, so the observations come from the trace and not from a fixture
        List<String> episodeIds = new ArrayList<>();
        for (String question : OBSERVED_QUESTIONS) {
            Response response = route.report(WORKSPACE,
                    new ArtifactReportResource.ReportRequest(question, null, true,
                            "key-observed-" + question.hashCode(), null));
            assertThat(response.getStatus()).isEqualTo(201);
            ArtifactReportResource.ReportResponse body =
                    (ArtifactReportResource.ReportResponse) response.getEntity();
            episodeIds.add("report-" + body.taskId());
        }
        List<ReportProcedureLearner.Observation> observations =
                ReportProcedureLearner.observationsFrom(store, episodeIds);
        assertThat(observations)
                .as("the journeys left the observations the learner reads")
                .hasSize(OBSERVED_QUESTIONS.size());
        List<ReportProcedureLearner.Case> cases = new ArrayList<>();
        for (int i = 0; i < HELD_OUT_QUESTIONS.size(); i++) {
            cases.add(new ReportProcedureLearner.Case("case-" + i, HELD_OUT_QUESTIONS.get(i),
                    AbstractionLevel.INTRODUCTORY));
        }
        return new Fixture(bench, store, route, new ReportProcedureLearner(bench, store),
                observations, cases, database);
    }
}
