package io.matrix.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import io.matrix.cognitive.report.ReportProcedure;
import io.matrix.cognitive.report.ReportProcedureLearner;
import io.matrix.cognitive.report.ReportProcedureMethod;
import io.matrix.cognitive.report.ScopeStrategy;
import io.matrix.crj.necessity.Crj01NecessityCases;
import io.matrix.crj.necessity.Crj01NecessityGrid;
import io.matrix.crj.necessity.Crj01NecessitySummary;
import io.matrix.knowledge.acquisition.SourceRecord;
import io.matrix.security.NetworkExposure;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — what a task actually needs, and what happens when the evidence
 * it needs is not there.
 *
 * <p><b>What this milestone asks.</b> The previous milestone compared two rules about whether a
 * competence's scope may serve a question, and it compared them about the <b>reading</b>: the
 * anchor asks whether the scope holds the claim the reading cites first, the competitor asks
 * whether it holds every claim the reading cites. This milestone asks the prior question — what
 * does the <b>task</b> need — and walks the whole journey on the served route: an unseen task
 * whose available competence fails, the missing prerequisite made visible, a bounded typed
 * request for it, authorised evidence arriving, the refusal of evidence that is unauthorised,
 * conflicting or stale, a durable revision, and the exact revised procedure serving a different
 * task after a restart.</p>
 *
 * <p><b>Who wrote the cases.</b> Four <b>independent</b> DSH case authors, each given the case
 * contract and its own domain's corpus and nothing else — no implementation, no instrument, no
 * previous case set, no pre-registration. The oracle is therefore <b>not</b> the implementer's,
 * and the measurement is a DEV measurement with an independent oracle, not a sealed evaluation
 * (the isolation a sealed split needs was not established from this executor, and the prior
 * milestone's retained verdict on it is reused as it stands and <b>not re-probed</b>).</p>
 *
 * <p><b>What is asserted.</b> The two rules are measured on <b>identical</b> inputs — one
 * snapshot, one set of bindings, one history — and the confusion matrix is reported with its
 * denominators, including the cells whose denominator is empty. The lifecycle is walked on the
 * real route: an observable failure, a diagnosis that names the missing evidence, a bounded
 * request, an authorised fulfilment, three refusals, a durable retirement that survives a
 * restart, and the exact revised procedure answering a different question after the restart.</p>
 *
 * <p><b>What is NOT claimed.</b> No winner: the pre-registration fixes the comparison, not the
 * outcome. No threshold. No generalisation and no superiority — the user-observable outcome is
 * reported separately from any transfer claim. No sealed evaluation.</p>
 */
class EpistemicNecessityServedTest {

    /** The classpath directory the corpora live in. */
    private static final String CORPUS_RESOURCE = "/crj01-reach/";

    /** The retained evidence directory — the canonical home of this milestone's artefacts. */
    private static final String EVIDENCE_DIRECTORY =
            "docs-v2/engineering/evidence/crj01-necessity";

    /** The instants the promotions are recorded with — supplied, never read from a clock. */
    private static final String PROMOTED_AT = "2026-11-28T00:00:00Z";

    /** The instant the revision is recorded with. */
    private static final String REVISED_AT = "2026-11-28T00:00:01Z";

    /** The instant the retirement is recorded with. */
    private static final String RETIRED_AT = "2026-11-28T00:00:02Z";

    /** The workspace prefix: one workspace per domain and per condition. */
    private static final String WORKSPACE_PREFIX = "crj01-necessity-";

    /** The conditions the grid runs under, in the fixed order. */
    private static final List<String> CONDITIONS = List.of(
            "BASELINE", "CHANGED_BINDINGS", "SUPERSEDED");

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

    /** The case set — four independent authors' work. */
    private Crj01NecessityCases.CaseSet caseSet;

    /** The observed episodes of every (domain, condition), by the episode's id. */
    private final Map<String, List<String>> episodes = new LinkedHashMap<>();

    /** The production source id of every corpus file, by (domain, condition). */
    private final Map<String, Map<String, String>> sourceIds = new LinkedHashMap<>();

    /** The grid of every (domain, condition). */
    private final Map<String, Crj01NecessityGrid.Result> grids = new LinkedHashMap<>();

    // ── the fixture ───────────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
        caseSet = Crj01NecessityCases.read();
        corpusDirectory = Files.createDirectories(directory.resolve("corpus"));
        for (Crj01NecessityCases.Domain domain : caseSet.domains()) {
            Path into = Files.createDirectories(corpusDirectory.resolve(domain.id()));
            for (String file : domain.files()) {
                Files.write(into.resolve(file), corpusBytes(domain.id(), file));
            }
        }
        bench = KnowledgeWorkbench.at(Files.createDirectories(directory.resolve("db"))
                .resolve("workbench.sqlite").toString());
        outputRoot = Files.createDirectories(directory.resolve("output"));
        Files.setPosixFilePermissions(outputRoot, java.nio.file.attribute.PosixFilePermissions
                .fromString("rwx------"));
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

    // ── the case set ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the case set is SIX INDEPENDENT authors' work, the slices are disjoint, the "
            + "nine conditions are covered, and every proposition is a sentence of the corpus")
    void theCaseSetIsIndependentAndCoherent() throws Exception {
        assertThat(caseSet.cases())
                .as("the milestone carries cases from every domain")
                .hasSizeGreaterThanOrEqualTo(12);
        assertThat(caseSet.author())
                .as("the case set records who wrote it")
                .containsKey("independence");
        assertThat(String.valueOf(caseSet.author().get("role")))
                .as("the case set's author record names the independent authors")
                .contains("INDEPENDENT");
        assertThat(String.valueOf(caseSet.author().get("independence")))
                .as("the case set's independence record states what the authors did and did "
                        + "not read")
                .contains("the case contract")
                .contains("no implementation");

        // THE FOUR ASSIGNMENTS ARE THE PROVENANCE OF THE ORACLE.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments =
                (List<Map<String, Object>>) caseSet.provenance().get("assignments");
        assertThat(assignments)
                .as("the case set records the SIX independent assignments — four of the first "
                        + "round and two of the follow-up round")
                .hasSize(6);
        Set<String> authors = new LinkedHashSet<>();
        for (Map<String, Object> assignment : assignments) {
            authors.add(String.valueOf(assignment.get("assignment")));
            assertThat(String.valueOf(assignment.get("sha256")))
                    .as("every assignment records the digest of the authors' bytes")
                    .hasSize(64);
        }
        assertThat(authors).as("six separate assignments").hasSize(6);

        // EVERY PROPOSITION IS A SENTENCE OF THE CORPUS FILE IT NAMES.
        for (Crj01NecessityCases.Case testCase : caseSet.cases()) {
            for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                assertThat(sentenceOf(testCase.domain(), evidence.corpusFile()))
                        .as("the necessary evidence of %s is a sentence of %s", testCase.id(),
                                evidence.corpusFile())
                        .contains(evidence.proposition());
            }
            for (List<Crj01NecessityCases.Evidence> alternative
                    : testCase.alternativeSufficientSets()) {
                for (Crj01NecessityCases.Evidence evidence : alternative) {
                    assertThat(sentenceOf(testCase.domain(), evidence.corpusFile()))
                            .as("the alternative evidence of %s is a sentence of %s",
                                    testCase.id(), evidence.corpusFile())
                            .contains(evidence.proposition());
                }
            }
        }

        // THE SLICES ARE DISJOINT — no necessary proposition crosses a slice.
        Map<String, Set<String>> goldsBySlice = new LinkedHashMap<>();
        for (Crj01NecessityCases.Case testCase : caseSet.cases()) {
            Set<String> golds = goldsBySlice.computeIfAbsent(testCase.slice(),
                    key -> new LinkedHashSet<>());
            for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                golds.add(testCase.domain() + "|" + evidence.corpusFile() + "|"
                        + evidence.proposition());
            }
        }
        for (String slice : List.of("PROPOSAL", "VALIDATION", "EVALUATION")) {
            for (String other : List.of("PROPOSAL", "VALIDATION", "EVALUATION")) {
                if (slice.equals(other)) {
                    continue;
                }
                Set<String> shared = new LinkedHashSet<>(goldsBySlice.getOrDefault(slice, Set.of()));
                shared.retainAll(goldsBySlice.getOrDefault(other, Set.of()));
                assertThat(shared)
                        .as("no necessary proposition of the %s slice is one of the %s slice",
                                slice, other)
                        .isEmpty();
            }
        }

        // THE NINE CONDITIONS ARE COVERED.
        Set<String> conditions = new LinkedHashSet<>();
        for (Crj01NecessityCases.Case testCase : caseSet.cases()) {
            conditions.add(testCase.condition());
        }
        assertThat(conditions)
                .as("the case set covers every condition the pre-registration names")
                .contains("LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT",
                        "MISSING_PREREQUISITE", "CONTRADICTION", "SUPERSESSION",
                        "CHANGED_BINDINGS", "AMBIGUITY", "UNANSWERABLE");

        // THE REACHABILITY OF THE GOLD IS MEASURED, NOT ASSUMED. A case whose necessary
        // sentence no reading reaches is a case about the RETRIEVER, and the milestone has to
        // say which of its cases are like that — the independent authors wrote the questions
        // without seeing the retriever, and the first round produced cases whose gold the
        // workspace's own reading does not reach. The rows retain the measurement.
        int reachable = 0;
        int unreachable = 0;
        for (Crj01NecessityCases.Domain domain : caseSet.domains()) {
            String workspace = workspaceOf(domain.id(), "BASELINE");
            prepared(domain.id(), "BASELINE");
            KnowledgeSnapshot view = bench.workspace(workspace);
            for (Crj01NecessityCases.Case testCase : caseSet.slice(domain.id(), "EVALUATION")) {
                Answer reading = bench.askInView(view, testCase.question(),
                        AbstractionLevel.valueOf(testCase.level()), Set.of(), false);
                List<String> propositions = new ArrayList<>();
                for (Claim claim : reading.claims()) {
                    propositions.add(claim.proposition());
                }
                boolean all = true;
                for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                    if (!propositions.contains(evidence.proposition())) {
                        all = false;
                    }
                }
                if (all) {
                    reachable++;
                } else {
                    unreachable++;
                }
            }
        }
        assertThat(reachable)
                .as("at least one evaluation case's gold the workspace's own reading reaches, so "
                        + "the measurement is not vacuous")
                .isPositive();
        int evaluationCases = 0;
        for (Crj01NecessityCases.Domain domain : caseSet.domains()) {
            evaluationCases += caseSet.slice(domain.id(), "EVALUATION").size();
        }
        assertThat(reachable + unreachable)
                .as("every evaluation case was measured for reachability")
                .isEqualTo(evaluationCases);
    }

    // ── the paired measurement ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the two rules are measured on IDENTICAL inputs, and the confusion matrix "
            + "carries every denominator — including the cells that have none")
    void theTwoRulesAreMeasuredOnIdenticalInputs() throws Exception {
        int rows = 0;
        for (Crj01NecessityCases.Domain domain : caseSet.domains()) {
            for (String condition : CONDITIONS) {
                Crj01NecessityGrid.Result grid = grid(domain.id(), condition);
                Set<String> fingerprints = new LinkedHashSet<>();
                for (Crj01NecessityGrid.Row row : grid.rows()) {
                    fingerprints.add(row.inputFingerprint());
                    assertThat(row.classification())
                            .as("every row is classified into the confusion matrix")
                            .isIn("TP", "FP", "TN", "FN");
                }
                assertThat(fingerprints)
                        .as("the arms of %s/%s were measured against ONE input", domain.id(),
                                condition)
                        .hasSize(1);
                rows += grid.rows().size();
            }
        }
        assertThat(rows).as("the grid measures every case of every arm").isPositive();

        // THE MEASUREMENT IS REPRODUCIBLE — Article I. The grid is computed a SECOND time,
        // from scratch, and every classification is compared with the first run's. A grid
        // whose verdicts differ between two runs in one process is not a measurement.
        Map<String, String> first = new LinkedHashMap<>();
        for (Crj01NecessityGrid.Row row : allRows()) {
            first.put(row.domain() + "|" + row.condition() + "|" + row.strategy() + "|"
                    + row.criterion() + "|" + row.caseId(), row.classification());
        }
        grids.clear();
        Map<String, String> second = new LinkedHashMap<>();
        for (Crj01NecessityGrid.Row row : allRows()) {
            second.put(row.domain() + "|" + row.condition() + "|" + row.strategy() + "|"
                    + row.criterion() + "|" + row.caseId(), row.classification());
        }
        assertThat(second)
                .as("the grid is reproducible: every row of the second run has the same "
                        + "classification as the first")
                .isEqualTo(first);

        // THE SUMMARY'S RATES CARRY THEIR DENOMINATORS, AND AN EMPTY DENOMINATOR IS UNDEFINED.
        Crj01NecessitySummary.Summary summary = Crj01NecessitySummary.summarise(allRows());
        assertThat(summary.cells()).as("the summary has a cell per rule").isNotEmpty();
        boolean sawUndefined = false;
        boolean sawDefined = false;
        for (Crj01NecessitySummary.Cell cell : summary.cells()) {
            for (Crj01NecessitySummary.Rate rate : List.of(cell.fpr(), cell.fnr(),
                    cell.falseDiscovery(), cell.falseOmission())) {
                assertThat(rate.label()).as("every rate names its formula").isNotBlank();
                if (rate.denominator() == 0) {
                    assertThat(rate.value())
                            .as("a rate with no denominator is UNDEFINED, never 0.0")
                            .isNull();
                    sawUndefined = true;
                } else {
                    assertThat(rate.value()).isNotNull();
                    sawDefined = true;
                }
            }
        }
        assertThat(sawDefined).as("at least one rate has a denominator").isTrue();
        assertThat(summary.differences())
                .as("the summary records where the two rules decided differently")
                .isNotNull();
    }

    // ── the journey on the served route ─────────────────────────────────────────────────────

    @Test
    @DisplayName("the journey: an unseen task the competence cannot carry, the missing "
            + "prerequisite named, a bounded typed request, an authorised fulfilment that "
            + "changes the SAME task's disposition, and three refusals")
    void theJourneyIsWalkedOnTheServedRoute() throws Exception {
        // ── THE FAILURE — TAKEN FROM THE MEASUREMENT, NOT CHOSEN ─────────────────────────
        // A FALSE ADMISSION of the grid: the incumbent admitted a scope that does not carry
        // the task's necessary evidence. The first version of this test also demanded a
        // REACHABLE gold, and no such row existed: after the oracle's "carries" was
        // corrected to mean what the served path means, every false admission of this corpus
        // sits on a task whose evidence the workspace's own reader does not reach. The
        // milestone reports that instead of hiding it behind a filter that made the journey
        // disappear.
        String domain = null;
        String condition = "BASELINE";
        Crj01NecessityGrid.Row failure = null;
        for (Crj01NecessityCases.Domain candidate : caseSet.domains()) {
            for (String under : CONDITIONS) {
                for (Crj01NecessityGrid.Row row : grid(candidate.id(), under).rows()) {
                    if (!row.criterion().equals("ANCHOR") || !row.admitted()
                            || row.oracleAdequate()) {
                        continue;
                    }
                    domain = candidate.id();
                    condition = under;
                    failure = row;
                    break;
                }
                if (failure != null) {
                    break;
                }
            }
            if (failure != null) {
                break;
            }
        }
        assertThat(failure)
                .as("the measurement found a case the incumbent rule admitted and whose scope "
                        + "does not carry the evidence the oracle requires")
                .isNotNull();
        String workspace = workspaceOf(domain, condition);
        Crj01NecessityCases.Case subject = caseOf(failure.caseId());
        AbstractionLevel level = AbstractionLevel.valueOf(subject.level());
        Set<String> declared = declaredSources(domain, condition);

        // ── THE TWO COMPETENCES — the SAME scope, the TWO rules, both promoted ───────────
        // The validation is over the held-out cases that stay INSIDE the declared scope, and
        // the milestone says so: the production promotion rule requires agreement with the
        // reference on EVERY held-out case, and one independent held-out question reaches a
        // document the declared scope leaves out. That question is not dropped — it is the
        // journey's UNSEEN TASK below.
        List<ReportProcedureLearner.Case> confined = confinedValidationCases(domain, condition,
                declared);
        assertThat(confined)
                .as("at least five held-out cases stay inside the declared scope, so the "
                        + "promotion can be decided on them")
                .hasSizeGreaterThanOrEqualTo(5);
        ReportProcedureLearner.Outcome incumbent = learner().learn(workspace,
                observations(domain, condition), confined, Set.of(), PROMOTED_AT,
                ScopeStrategy.DECLARED, declared, ScopeCriterion.ANCHOR);
        assertThat(incumbent.promoted())
                .as("the incumbent competence was promoted: %s", incumbent.reason()).isTrue();
        Set<String> scope = ReportProcedure.supportOf(incumbent.procedure());
        assertThat(scope).as("the competence carries a scope").isNotEmpty();

        // ── THE OBSERVABLE FAILURE — under the INCUMBENT the answer is unjustified ─────────
        // The incumbent is served BEFORE the proposal's version is written: the two rules are
        // two VERSIONS of one competence, and the route offers the newest one first.
        ArtifactReportResource.JourneyResult served = route.journey(workspace,
                request(subject, "necessity-incumbent"), "procedure." + incumbent.procedure().id());
        assertThat(served.response().getStatus())
                .as("the journey was served; the route said: %s", body(served.response()))
                .isEqualTo(201);
        List<String> cited = citedOf(served.response());
        List<String> necessaryPropositions = new ArrayList<>();
        for (Crj01NecessityCases.Evidence evidence : subject.necessaryEvidence()) {
            necessaryPropositions.add(evidence.proposition());
        }
        int carried = 0;
        for (String proposition : necessaryPropositions) {
            if (cited.contains(proposition)) {
                carried++;
            }
        }
        assertThat(carried)
                .as("the served answer does NOT carry the whole evidence the task needs: "
                        + "this is the observable failure of the competence's reach")
                .isLessThan(necessaryPropositions.size());

        // THE REFERENCE — on the same question, the same workspace and the same moment. What
        // it carries is MEASURED, not asserted: the comparison the milestone may claim is
        // only what the measurement supports.
        ArtifactReportResource.JourneyResult reference = route.journey(workspace,
                request(subject, "necessity-reference"), ReportJourneyMethod.REFERENCE_ID);
        assertThat(reference.response().getStatus()).as("the reference answered").isEqualTo(201);
        List<String> referenceCited = citedOf(reference.response());
        int referenceCarried = 0;
        for (String proposition : necessaryPropositions) {
            if (referenceCited.contains(proposition)) {
                referenceCarried++;
            }
        }
        assertThat(carried)
                .as("the served answer carries no MORE of the task's necessary evidence than "
                        + "the reference does — the narrow scope cannot be better than the "
                        + "whole workspace")
                .isLessThanOrEqualTo(referenceCarried);

        // ── THE PROPOSAL — the SAME scope, the OTHER rule, promoted as a NEW VERSION ─────
        ReportProcedureLearner.Outcome proposal = learner().learn(workspace,
                observations(domain, condition), confined, Set.of(), REVISED_AT,
                ScopeStrategy.DECLARED, declared, ScopeCriterion.NECESSARY_PREREQUISITE);
        assertThat(proposal.promoted())
                .as("the proposal competence was promoted: %s", proposal.reason()).isTrue();
        assertThat(ReportProcedure.supportOf(proposal.procedure()))
                .as("the two rules are two versions of ONE competence over the SAME scope")
                .isEqualTo(scope);
        assertThat(proposal.procedure().version())
                .as("the rule is part of the competence's definition, so it carries its own "
                        + "version")
                .isNotEqualTo(incumbent.procedure().version());

        // ── THE DIAGNOSIS — under the PROPOSAL the scope is refused and the missing
        //    prerequisite is NAMED ─────────────────────────────────────────────────────────
        KnowledgeSnapshot view = bench.workspace(workspace);
        ScopeAssessment anchored = bench.assessScope(view, view, subject.question(), level, scope,
                false, ScopeCriterion.ANCHOR);
        ScopeAssessment prerequisite = bench.assessScope(view, view, subject.question(), level,
                scope, false, ScopeCriterion.NECESSARY_PREREQUISITE);
        assertThat(anchored.applicable())
                .as("the incumbent rule admitted the scope, and the served answer was degraded")
                .isTrue();
        assertThat(prerequisite.applicable())
                .as("the proposal refuses the same scope for the same question")
                .isFalse();
        assertThat(prerequisite.taskPrerequisites())
                .as("the proposal derived the task's necessary evidence from the workspace")
                .isNotNull();
        assertThat(prerequisite.taskPrerequisites().necessary())
                .as("the derivation named what the task cannot be done without")
                .isNotEmpty();
        assertThat(prerequisite.missingClaimIds())
                .as("the refusal names the requirements the scope does not carry")
                .isNotEmpty();

        // ── THE REQUEST — bounded, typed, and produced by the served path ────────────────
        // The request travels with the JOURNEY: it is the journey's own metadata, carried
        // in its result, and the resource keeps nothing between requests.
        ArtifactReportResource.JourneyResult proposalRun = route.journey(workspace,
                request(subject, "necessity-proposal"),
                "procedure." + proposal.procedure().id());
        EvidenceAcquisitionRequest acquisition = null;
        for (EvidenceAcquisitionRequest candidate : proposalRun.acquisitionRequests()) {
            if (candidate.procedureId().equals(proposal.procedure().id())) {
                acquisition = candidate;
            }
        }
        assertThat(acquisition)
                .as("the served path produced a typed acquisition request for the missing "
                        + "prerequisite")
                .isNotNull();
        assertThat(acquisition.kind())
                .as("the request is about evidence the scope does not carry")
                .isEqualTo(EvidenceAcquisitionRequest.Kind.EVIDENCE);
        assertThat(acquisition.missing())
                .as("the request names what is missing")
                .isNotEmpty();
        assertThat(acquisition.channel())
                .as("the request names the authorised channel")
                .isEqualTo(EvidenceAcquisitionRequest.CHANNEL_LEARNING);
        assertThat(acquisition.allowedActions())
                .as("the request is BOUNDED: it authorises a finite set of channels")
                .containsExactly(EvidenceAcquisitionRequest.CHANNEL_LEARNING);
        assertThat(acquisition.summary())
                .as("the request carries a sentence a reader can act on")
                .contains("does not carry the evidence this task needs");

        // ── THE AUTHORISED FULFILMENT — the wider scope is LEARNED and VALIDATED ─────────
        // The wider scope is the declared scope plus the source the missing requirement comes
        // from, and it is validated against the WHOLE held-out slice — including the question
        // the declared scope could not carry.
        Set<String> widerScope = new LinkedHashSet<>(scope);
        for (EvidenceRequirement requirement : acquisition.missing()) {
            widerScope.add(requirement.sourceId());
        }
        assertThat(widerScope).as("the wider scope holds more documents").hasSizeGreaterThan(
                scope.size());
        ReportProcedureLearner.Outcome widened = learner().learn(workspace,
                observations(domain, condition), validationCases(domain), Set.of(), REVISED_AT,
                ScopeStrategy.DECLARED, widerScope, ScopeCriterion.NECESSARY_PREREQUISITE);
        assertThat(widened.promoted())
                .as("the wider competence was learned and validated against the WHOLE held-out "
                        + "slice: %s", widened.reason())
                .isTrue();

        List<Claim> offered = new ArrayList<>();
        for (Claim claim : view.claims()) {
            if (widerScope.contains(claim.introducedBySourceId())) {
                offered.add(claim);
            }
        }
        EvidenceAcquisition.Fulfilment fulfilled = EvidenceAcquisition.offer(acquisition,
                EvidenceAcquisitionRequest.CHANNEL_LEARNING, offered, view);
        assertThat(fulfilled.fulfilled())
                .as("the evidence arrived through the authorised channel: %s", fulfilled.reason())
                .isTrue();

        // ── THE SAME TASK, A DIFFERENT DISPOSITION ────────────────────────────────────────
        ScopeAssessment afterAssessment = bench.assessScope(view, view, subject.question(), level,
                widerScope, false, ScopeCriterion.NECESSARY_PREREQUISITE);
        assertThat(afterAssessment.applicable())
                .as("the SAME task's disposition changed after the evidence arrived: the wider "
                        + "scope carries the requirement, and the proposal now admits it")
                .isTrue();
        ArtifactReportResource.JourneyResult after = route.journey(workspace,
                request(subject, "necessity-revised"),
                "procedure." + widened.procedure().id());
        assertThat(after.response().getStatus()).as("the task was served again").isEqualTo(201);
        assertThat(citedOf(after.response()))
                .as("the served answer now cites the evidence the narrow scope could not carry")
                .isNotEqualTo(cited);

        // ── THE THREE REFUSALS ───────────────────────────────────────────────────────────
        EvidenceAcquisition.Fulfilment unauthorised = EvidenceAcquisition.offer(acquisition,
                EvidenceAcquisitionRequest.CHANNEL_RECEIPT_IMPORT, offered, view);
        assertThat(unauthorised.disposition())
                .as("evidence that arrives through a channel the request does not authorise is "
                        + "refused")
                .isEqualTo(EvidenceAcquisition.REFUSED_UNAUTHORIZED);

        // THE PROVENANCE — a claim the workspace does not hold cannot answer a request for
        // evidence the workspace HOLDS. The fixture is a fabricated claim: it names a source
        // the workspace has, and it is not one of the workspace's claims.
        Claim held = offered.get(0);
        Claim fabricated = new Claim("clm-necessity-fabricated", workspace,
                held.conceptId(), held.proposition(), held.scope(), held.context(),
                held.status(), held.confidence(), held.version(),
                held.introducedBySourceId(), held.extractionMethod(), held.validationMethod(),
                held.warrant(), held.derivationStatus(), held.createdAt(), held.updatedAt());
        EvidenceAcquisition.Fulfilment unverifiedOffer = EvidenceAcquisition.offer(acquisition,
                EvidenceAcquisitionRequest.CHANNEL_LEARNING, List.of(fabricated), view);
        assertThat(unverifiedOffer.disposition())
                .as("evidence the workspace does not hold cannot answer a request for evidence "
                        + "it does hold")
                .isEqualTo(EvidenceAcquisition.REFUSED_UNVERIFIED);

        // THE STALENESS — a claim the workspace has TAKEN BACK, produced by the production
        // path: a refinement supersedes the original claim and keeps the record.
        Claim toRefine = null;
        for (Claim claim : bench.workspace(workspace).claims()) {
            if (claim.status() == ClaimStatus.SUPPORTED && claim.proposition() != null) {
                toRefine = claim;
                break;
            }
        }
        assertThat(toRefine).as("the workspace holds a claim that can be taken back").isNotNull();
        bench.acceptRefinement(workspace, toRefine.id(), "the narrower account of "
                + toRefine.proposition());
        Claim withdrawn = null;
        for (Claim claim : bench.workspace(workspace).claims()) {
            if (claim.id().equals(toRefine.id())
                    && claim.status() == ClaimStatus.SUPERSEDED) {
                withdrawn = claim;
                break;
            }
        }
        assertThat(withdrawn)
                .as("the production path kept the record of the claim it took back")
                .isNotNull();
        EvidenceAcquisition.Fulfilment staleOffer = EvidenceAcquisition.offer(acquisition,
                EvidenceAcquisitionRequest.CHANNEL_LEARNING, List.of(withdrawn),
                bench.workspace(workspace));
        assertThat(staleOffer.disposition())
                .as("evidence the workspace has taken back is refused")
                .isEqualTo(EvidenceAcquisition.REFUSED_STALE);

        // THE CONFLICT — the PRODUCTION detector decides what a conflict is, and the refusal
        // uses it, so that the two cannot drift apart. The fixture supplies a pair that
        // satisfies the detector's OWN conditions — one concept, the same conditions, the same
        // terms with opposite polarity — and the corpus's own two rain-drop sentences are
        // exactly such a pair. The detector is asked first: if it does not see a conflict,
        // the milestone says so rather than asserting one.
        // The corpus's own contradiction — the two rain-drop sentences — is NOT of the shape
        // the production detector recognises: they differ in more than the negation
        // ("changes" against "does not change"), and the milestone records that as a finding
        // about the corpus rather than pretending the corpus's contradiction was detected.
        String corpusPositive = "The size of the rain drops changes the erosion rate of a "
                + "wind-turbine blade at an exposed coastal site.";
        String corpusNegative = "The size of the rain drops does not change the erosion rate of "
                + "a wind-turbine blade at an exposed coastal site.";
        assertThat(new ContradictionDetector().compare(
                new Claim("clm-necessity-corpus-a", workspace, "cpt-corpus", corpusPositive,
                        null, null, ClaimStatus.SUPPORTED, 0.9, 1, sourceOfId(view, scope), null,
                        null, ClaimWarrant.NONE, DerivationStatus.NOT_DERIVED, PROMOTED_AT,
                        PROMOTED_AT),
                new Claim("clm-necessity-corpus-b", workspace, "cpt-corpus", corpusNegative,
                        null, null, ClaimStatus.SUPPORTED, 0.9, 1, sourceOfId(view, scope), null,
                        null, ClaimWarrant.NONE, DerivationStatus.NOT_DERIVED, PROMOTED_AT,
                        PROMOTED_AT)))
                .as("the corpus's own contradiction is NOT seen by the production detector — "
                        + "its two sentences differ in more than the negation, and the "
                        + "milestone records that as a finding")
                .isNull();

        // THE CONFLICT — a REAL conflicting pair, produced by the production import path in
        // a workspace of its own: a document that states an account and its negation. The
        // production detector decides what a conflict is, and the refusal uses it.
        String conflictWorkspace = workspace + "-conflict";
        Path conflictDocument = directory.resolve("conflict-document.md");
        Files.writeString(conflictDocument,
                "The inspection interval of a wind-turbine blade is set by the erosion rate of "
                        + "its site.\n"
                        + "The inspection interval of a wind-turbine blade is not set by the "
                        + "erosion rate of its site.\n",
                java.nio.charset.StandardCharsets.UTF_8);
        bench.importDocument(conflictWorkspace, conflictDocument, "conflict-document.md", "en");
        KnowledgeSnapshot conflictView = bench.workspace(conflictWorkspace);
        Claim contradicting = null;
        for (Claim claim : conflictView.claims()) {
            if (claim.proposition() != null && claim.proposition().contains("is not set by")) {
                contradicting = claim;
                break;
            }
        }
        assertThat(contradicting)
                .as("the imported document states an account and its negation, so the workspace "
                        + "holds a conflicting pair")
                .isNotNull();
        assertThat(new ContradictionDetector().detect(conflictView))
                .as("the production contradiction detector sees the pair the workspace holds")
                .isNotEmpty();
        EvidenceAcquisition.Fulfilment conflictingOffer = EvidenceAcquisition.offer(acquisition,
                EvidenceAcquisitionRequest.CHANNEL_LEARNING, List.of(contradicting), conflictView);
        assertThat(conflictingOffer.disposition())
                .as("evidence that contradicts an established claim of the workspace is refused")
                .isEqualTo(EvidenceAcquisition.REFUSED_CONFLICTING);

        // ── THE REVISION — the predecessor is retired, with a reason and a causal link ────
        boolean retired = cognitive.store().retireProcedure(workspace,
                proposal.procedure().id(), proposal.procedure().version(), RETIRED_AT,
                "the competence was replaced by a wider one that carries the evidence the tasks "
                        + "of this workspace actually need",
                "replaced-by:" + widened.procedure().id() + "@" + widened.procedure().version());
        assertThat(retired).as("the predecessor was retired durably").isTrue();
        CognitiveStore.ProcedureInvalidation invalidation = cognitive.store()
                .procedureInvalidation(workspace, proposal.procedure().id(),
                        proposal.procedure().version());
        assertThat(invalidation).as("the retirement is readable").isNotNull();
        assertThat(invalidation.cause())
                .as("the retirement carries the causal link to its successor")
                .contains(widened.procedure().id());
    }

    @Test
    @DisplayName("the journey's acquisition metadata is REQUEST-LOCAL: one journey never sees "
            + "another's, and the resource keeps nothing between requests")
    void theAcquisitionMetadataDoesNotBleedBetweenRequests() throws Exception {
        // THE JOURNEY THAT PRODUCES A REQUEST — the proposal's competence, asked for a task
        // whose necessary evidence its scope does not carry.
        Crj01NecessityCases.Domain domain = caseSet.domains().get(0);
        String workspace = workspaceOf(domain.id(), "BASELINE");
        prepared(domain.id(), "BASELINE");
        Set<String> declared = declaredSources(domain.id(), "BASELINE");
        List<ReportProcedureLearner.Case> confined = confinedValidationCases(domain.id(),
                "BASELINE", declared);
        ReportProcedureLearner.Outcome proposal = learner().learn(workspace,
                observations(domain.id(), "BASELINE"), confined, Set.of(), REVISED_AT,
                ScopeStrategy.DECLARED, declared, ScopeCriterion.NECESSARY_PREREQUISITE);
        assertThat(proposal.promoted()).as("the proposal competence was promoted").isTrue();
        List<Crj01NecessityCases.Case> evaluation = caseSet.slice(domain.id(), "EVALUATION");
        assertThat(evaluation).as("the domain carries evaluation cases").hasSizeGreaterThan(1);
        Crj01NecessityCases.Case firstCase = evaluation.get(0);
        Crj01NecessityCases.Case otherCase = evaluation.get(1);

        ArtifactReportResource.JourneyResult first = route.journey(workspace,
                request(firstCase, "necessity-bleed-first"),
                "procedure." + proposal.procedure().id());
        assertThat(first.acquisitionRequests())
                .as("the first journey carries the acquisition metadata it produced")
                .isNotEmpty();
        List<String> firstIds = first.acquisitionRequests().stream()
                .map(EvidenceAcquisitionRequest::requestId).toList();

        // THE SECOND JOURNEY — a DIFFERENT question, over the same resource. The metadata it
        // carries is its own: the first journey's request is not in it.
        ArtifactReportResource.JourneyResult second = route.journey(workspace,
                request(otherCase, "necessity-bleed-second"),
                "procedure." + proposal.procedure().id());
        List<String> secondIds = second.acquisitionRequests().stream()
                .map(EvidenceAcquisitionRequest::requestId).toList();
        for (String id : firstIds) {
            assertThat(secondIds)
                    .as("the second journey carries NOTHING of the first one's — the metadata "
                            + "is request-local and does not bleed")
                    .doesNotContain(id);
        }

        // AND THE FIRST ONE AGAIN — the same journey, asked a third time, still carries only
        // its own.
        ArtifactReportResource.JourneyResult third = route.journey(workspace,
                request(firstCase, "necessity-bleed-third"),
                "procedure." + proposal.procedure().id());
        assertThat(third.acquisitionRequests().stream()
                .map(EvidenceAcquisitionRequest::requestId).toList())
                .as("the third journey carries the metadata of its own request")
                .containsExactlyElementsOf(firstIds);
        for (String id : secondIds) {
            assertThat(third.acquisitionRequests().stream()
                    .map(EvidenceAcquisitionRequest::requestId).toList())
                    .as("the third journey carries NOTHING of the second one's")
                    .doesNotContain(id);
        }

        // THE RESOURCE KEEPS NOTHING — the type that carried the shared state is gone, and
        // the journey's result is where the metadata lives.
        assertThat(java.util.Arrays.stream(ArtifactReportResource.class.getMethods())
                .filter(method -> "lastOfferedMethods".equals(method.getName())).count())
                .as("the resource no longer offers the last request's methods")
                .isZero();
    }

    @Test
    @DisplayName("the durable lifecycle: a retirement survives a restart, prevents the stale "
            + "selection, keeps the historical evidence, and the competence can be revalidated")
    void theDurableLifecycleIsWalked() throws Exception {
        Crj01NecessityCases.Domain domain = caseSet.domains().get(0);
        String workspace = workspaceOf(domain.id(), "BASELINE");
        prepared(domain.id(), "BASELINE");
        Set<String> declared = declaredSources(domain.id(), "BASELINE");
        List<ReportProcedureLearner.Case> confined = confinedValidationCases(domain.id(),
                "BASELINE", declared);
        ReportProcedureLearner.Outcome promoted = learner().learn(workspace,
                observations(domain.id(), "BASELINE"), confined, Set.of(),
                PROMOTED_AT, ScopeStrategy.DECLARED, declared, ScopeCriterion.ANCHOR);
        assertThat(promoted.promoted()).as("the competence was promoted").isTrue();
        Procedure competence = promoted.procedure();

        // THE RETIREMENT — durable, with a reason and a causal link.
        boolean retired = cognitive.store().retireProcedure(workspace, competence.id(),
                competence.version(), RETIRED_AT,
                "the competence was replaced by a narrower one that carries the evidence the "
                        + "tasks of this workspace actually need",
                "replaced-by:the-revised-competence");
        assertThat(retired).as("the retirement was recorded").isTrue();

        CognitiveStore.ProcedureInvalidation invalidation = cognitive.store()
                .procedureInvalidation(workspace, competence.id(), competence.version());
        assertThat(invalidation).as("the retirement is readable").isNotNull();
        assertThat(invalidation.invalidatedAt()).as("the retirement carries its instant")
                .isEqualTo(RETIRED_AT);
        assertThat(invalidation.reason()).as("the retirement carries its reason").isNotBlank();
        assertThat(invalidation.cause()).as("the retirement carries its causal link").isNotBlank();

        // THE HISTORICAL EVIDENCE IS RETAINED — the version is still there, and still readable.
        Procedure stored = cognitive.store().procedures(workspace).stream()
                .filter(procedure -> procedure.id().equals(competence.id())
                        && procedure.version().equals(competence.version()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the retired version is gone"));
        assertThat(stored.invalidatedAt()).as("the retired version reads as retired")
                .isEqualTo(RETIRED_AT);
        assertThat(stored.active()).as("the retired version is no longer active").isFalse();
        assertThat(stored.applicability())
                .as("the retired version keeps its definition — the retirement did not rewrite it")
                .isEqualTo(competence.applicability());

        // A SECOND RETIREMENT IS REFUSED — the standing does not change twice.
        assertThat(cognitive.store().retireProcedure(workspace, competence.id(),
                competence.version(), RETIRED_AT, "again", "again"))
                .as("a version that is already retired is not retired twice")
                .isFalse();

        // ── THE RESTART — the stale selection is prevented ────────────────────────────────
        cognitive.store().close();
        cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route = route(bench, cognitive, outputRoot, new ReportJourneyPlanner());
        Procedure afterRestart = cognitive.store().procedures(workspace).stream()
                .filter(procedure -> procedure.id().equals(competence.id())
                        && procedure.version().equals(competence.version()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the retired version is gone"));
        assertThat(afterRestart.active())
                .as("the retirement survives the restart: the version is still retired")
                .isFalse();
        assertThat(cognitive.store().procedures(workspace).stream()
                .filter(Procedure::active)
                .filter(procedure -> procedure.id().equals(competence.id())))
                .as("no ACTIVE version of the retired competence is offered to the selector")
                .isEmpty();

        // ── THE REVALIDATION — the retirement is not a one-way door ──────────────────────
        ReportProcedureLearner.Outcome revalidated = learner().learn(workspace,
                observations(domain.id(), "BASELINE"), confined, Set.of(),
                REVISED_AT, ScopeStrategy.DECLARED, declared, ScopeCriterion.ANCHOR,
                competence.version());
        assertThat(revalidated.promoted())
                .as("the competence can be learned and validated again: %s", revalidated.reason())
                .isTrue();
        assertThat(revalidated.procedure().version())
                .as("the revalidation is a NEW version — a retired version is never rewritten "
                        + "in place")
                .isNotEqualTo(competence.version());
        assertThat(ReportProcedure.revalidatesOf(revalidated.procedure()))
                .as("the revalidated record names the version it revalidates")
                .isEqualTo(competence.version());
        boolean activeAgain = cognitive.store().procedures(workspace).stream()
                .filter(procedure -> procedure.id().equals(competence.id())
                        && procedure.version().equals(revalidated.procedure().version()))
                .anyMatch(Procedure::active);
        assertThat(activeAgain)
                .as("the revalidated version is ACTIVE — a retirement can be undone by a "
                        + "validated promotion")
                .isTrue();
        assertThat(cognitive.store().procedures(workspace).stream()
                .filter(procedure -> procedure.id().equals(competence.id())
                        && procedure.version().equals(competence.version()))
                .findFirst().orElseThrow().active())
                .as("the retired version stays retired: the revalidation did not revive it")
                .isFalse();
    }

    @Test
    @DisplayName("after a restart the EXACT REVISED procedure serves a DIFFERENT task, the "
            + "retired one is not offered to the selector, and the user-observable outcome is "
            + "kept apart from any generalisation claim")
    void theExactRevisedProcedureServesADifferentTaskAfterARestart() throws Exception {
        // THE DOMAIN THAT CARRIES A TASK THE NARROW COMPETENCE CANNOT CARRY. The search is
        // over EVERY domain: the requirement is that the task is DIFFERENT from the journey's
        // own, and a task of another domain is different by construction.
        Crj01NecessityCases.Domain domain = null;
        Crj01NecessityCases.Case distinct = null;
        ReportProcedureLearner.Outcome promoted = null;
        for (Crj01NecessityCases.Domain candidate : caseSet.domains()) {
            prepared(candidate.id(), "BASELINE");
            Set<String> candidateScope = declaredSources(candidate.id(), "BASELINE");
            List<ReportProcedureLearner.Case> candidateConfined = confinedValidationCases(
                    candidate.id(), "BASELINE", candidateScope);
            ReportProcedureLearner.Outcome candidatePromoted = learner().learn(
                    workspaceOf(candidate.id(), "BASELINE"),
                    observations(candidate.id(), "BASELINE"), candidateConfined, Set.of(),
                    PROMOTED_AT, ScopeStrategy.DECLARED, candidateScope, ScopeCriterion.ANCHOR);
            if (!candidatePromoted.promoted()) {
                continue;
            }
            Crj01NecessityCases.Case candidateDistinct = distinctCase(candidate.id(),
                    ReportProcedure.supportOf(candidatePromoted.procedure()));
            if (candidateDistinct != null) {
                domain = candidate;
                distinct = candidateDistinct;
                promoted = candidatePromoted;
                break;
            }
        }
        assertThat(domain)
                .as("a domain carries a task the narrow competence cannot carry")
                .isNotNull();
        String workspace = workspaceOf(domain.id(), "BASELINE");
        Set<String> narrow = ReportProcedure.supportOf(promoted.procedure());
        Procedure predecessor = promoted.procedure();

        // ── THE REVISION — the predecessor is retired and a wider one takes its place ──────
        // The revision is a LEARNED and VALIDATED competence over a wider scope, and the
        // predecessor is retired with a reason and a causal link. The test named "the exact
        // REVISED procedure" now revises; the first version of it promoted a fresh
        // competence and served it, and the independent reviewer found that it never
        // revised anything.
        Set<String> wider = new LinkedHashSet<>(narrow);
        for (Source source : bench.workspace(workspace).sources()) {
            if (!wider.contains(source.id())) {
                wider.add(source.id());
                break;
            }
        }
        assertThat(wider).as("the revision is a wider scope").hasSizeGreaterThan(narrow.size());
        ReportProcedureLearner.Outcome revised = learner().learn(workspace,
                observations(domain.id(), "BASELINE"), validationCases(domain.id()), Set.of(),
                REVISED_AT, ScopeStrategy.DECLARED, wider, ScopeCriterion.ANCHOR);
        assertThat(revised.promoted())
                .as("the revised competence was learned and validated: %s", revised.reason())
                .isTrue();
        boolean retired = cognitive.store().retireProcedure(workspace, predecessor.id(),
                predecessor.version(), RETIRED_AT,
                "the competence was replaced by a wider one that carries the evidence the tasks "
                        + "of this workspace actually need",
                "replaced-by:" + revised.procedure().id() + "@" + revised.procedure().version());
        assertThat(retired).as("the predecessor was retired durably").isTrue();

        // ── THE RESTART ─────────────────────────────────────────────────────────────────
        cognitive.store().close();
        cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route = route(bench, cognitive, outputRoot, new ReportJourneyPlanner());

        // THE STALE SELECTION IS PREVENTED — the retired version is offered to nobody.
        assertThat(cognitive.store().procedures(workspace).stream()
                .filter(procedure -> procedure.id().equals(predecessor.id()))
                .filter(Procedure::active))
                .as("the retired competence has NO active version after the restart")
                .isEmpty();
        assertThat(cognitive.store().procedures(workspace).stream()
                .filter(procedure -> procedure.id().equals(revised.procedure().id()))
                .filter(Procedure::active))
                .as("the revised competence is ACTIVE after the restart")
                .hasSize(1);

        // ── THE THREE BRANCHES ON THE SAME TASK, IN THE SAME WORKSPACE ────────────────────
        // (a) THE EXACT REVISED PROCEDURE — the version that was just written, forced by
        //     its identifier, so the route cannot silently pick another one.
        ArtifactReportResource.JourneyResult revisedRun = route.journey(workspace,
                request(distinct, "necessity-revised-after-restart"),
                "procedure." + revised.procedure().id());
        // (b) THE MATCHED STATELESS REFERENCE — the same task, the same workspace, no
        //     competence at all.
        ArtifactReportResource.JourneyResult reference = route.journey(workspace,
                request(distinct, "necessity-reference-after-restart"),
                ReportJourneyMethod.REFERENCE_ID);
        // (c) THE EXACT REPLAY — the same task again, from the replay branch.
        ArtifactReportResource.JourneyResult replay = route.journey(workspace,
                request(distinct, "necessity-replay-after-restart"),
                ReportJourneyMethod.REPLAY_ID);

        assertThat(reference.response().getStatus()).as("the reference answered").isEqualTo(201);
        List<String> referenceCited = citedOf(reference.response());
        int referenceCarried = 0;
        for (Crj01NecessityCases.Evidence evidence : distinct.necessaryEvidence()) {
            if (referenceCited.contains(evidence.proposition())) {
                referenceCarried++;
            }
        }
        assertThat(referenceCarried)
                .as("the reference carries the whole evidence of the distinct task")
                .isEqualTo(distinct.necessaryEvidence().size());

        // THE USER-OBSERVABLE OUTCOME of the revised procedure — what the caller received —
        // and NOTHING MORE. The milestone reports it and does not read a generalisation into
        // it.
        assertThat(revisedRun.response().getStatus())
                .as("the revised competence answered the distinct task after the restart")
                .isEqualTo(201);
        List<String> revisedCited = citedOf(revisedRun.response());
        int revisedCarried = 0;
        for (Crj01NecessityCases.Evidence evidence : distinct.necessaryEvidence()) {
            if (revisedCited.contains(evidence.proposition())) {
                revisedCarried++;
            }
        }
        assertThat(revisedCarried)
                .as("the revised competence carries the whole evidence of the distinct task — "
                        + "the user-observable difference from the retired one, and no more "
                        + "is claimed")
                .isEqualTo(distinct.necessaryEvidence().size());

        // THE INCUMBENT ADAPTIVE BRANCH — the retired competence, FORCED. It must not be
        // served: the store refuses to rewrite a retired version, and the route must not
        // offer it. This is the stale-selection prevention, exercised on the served route.
        // The route REFUSES to serve a retired competence: the planner offers only the active
        // versions, so forcing the retired one is an error, not a stale answer. The
        // assertion is on the refusal itself — the exception is the production behaviour.
        boolean refused = false;
        try {
            ArtifactReportResource.JourneyResult retiredRun = route.journey(workspace,
                    request(distinct, "necessity-retired-after-restart"),
                    "procedure." + predecessor.id());
            refused = retiredRun.response().getStatus() != 201;
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        assertThat(refused)
                .as("the retired competence is NOT served after the restart — the route refuses "
                        + "it, rather than answering from a retired version")
                .isTrue();

        // THE EXACT REPLAY — the same task, served from the replay branch. The replay can
        // only serve what it RECORDED, so on a task it has not recorded it refuses; the
        // milestone reports the outcome it measured rather than the one it wished for.
        assertThat(replay.response().getStatus())
                .as("the replay branch answered, or refused what it has not recorded")
                .isIn(201, 422);
        if (replay.response().getStatus() == 201) {
            assertThat(citedOf(replay.response()))
                    .as("the exact replay cites EXACTLY what the reference cited")
                    .isEqualTo(referenceCited);
        }
    }

    // ── the retained evidence ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the retained evidence carries every per-case row and the summary, and the "
            + "manifest verifies the directory")
    void theEvidenceIsWritten() throws Exception {
        List<Crj01NecessityGrid.Row> rows = new ArrayList<>();
        List<Crj01NecessityGrid.ScopeRow> scopes = new ArrayList<>();
        for (Crj01NecessityCases.Domain domain : caseSet.domains()) {
            for (String condition : CONDITIONS) {
                Crj01NecessityGrid.Result grid = grid(domain.id(), condition);
                scopes.addAll(grid.scopes());
                rows.addAll(grid.rows());
            }
        }
        Crj01NecessitySummary.Summary summary = Crj01NecessitySummary.summarise(rows);

        ObjectMapper json = JsonMapper.builder().build();
        Path directory = RepositoryPaths.root().resolve(EVIDENCE_DIRECTORY);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("CRJ-01-NECESSITY-GRID-ROWS.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(rows),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("CRJ-01-NECESSITY-GRID-SUMMARY.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(summary),
                StandardCharsets.UTF_8);
        refreshManifest(directory);

        List<?> reread = json.readValue(
                directory.resolve("CRJ-01-NECESSITY-GRID-ROWS.json").toFile(), List.class);
        assertThat(reread).as("the retained rows are the rows that were measured")
                .hasSize(rows.size());
    }

    // ── the helpers ──────────────────────────────────────────────────────────────────────────

    /**
     * The route, wired by hand, the way the container wires it.
     *
     * @param bench     the workbench
     * @param cognitive the kernel's service
     * @param outputRoot the output root
     * @param planner   the planner
     * @return the route
     */
    private static ArtifactReportResource route(KnowledgeWorkbench bench,
            CognitiveService cognitive, Path outputRoot, ReportJourneyPlanner planner) {
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = new ArtifactActionService(true, outputRoot.toString());
        route.planner = planner;
        route.cognitive = cognitive;
        return route;
    }

    /** The learner that derives and validates the candidate scopes. */
    private ReportProcedureLearner learner() {
        return new ReportProcedureLearner(bench, cognitive.store());
    }

    /** The procedures of the workspace, as the route offers them. */
    private List<ReportJourneyPlanner.JourneyMethod> offeredMethods(String workspace) {
        List<ReportJourneyPlanner.JourneyMethod> methods = new ArrayList<>();
        for (Procedure procedure : cognitive.store().procedures(workspace)) {
            if (procedure.active()
                    && ReportProcedure.FAMILY.equals(procedure.problemFamily())) {
                methods.add(new ReportProcedureMethod(procedure,
                        cognitive.store().procedureMeasuredCostNanos(workspace, procedure.id(),
                                procedure.version())));
            }
        }
        return methods;
    }

    /** The workspace a domain and a condition run in. */
    private static String workspaceOf(String domain, String condition) {
        return WORKSPACE_PREFIX + domain + "-" + condition.toLowerCase(Locale.ROOT);
    }

    /** The grid of a domain and a condition, run once and kept. */
    private Crj01NecessityGrid.Result grid(String domain, String condition) throws Exception {
        String key = domain + "/" + condition;
        Crj01NecessityGrid.Result cached = grids.get(key);
        if (cached != null) {
            return cached;
        }
        String workspace = workspaceOf(domain, condition);
        prepared(domain, condition);
        List<ReportProcedureLearner.Observation> observations = observations(domain, condition);
        KnowledgeSnapshot view = SourceRevisionView.project(bench.workspace(workspace), null);
        Map<String, String> sourceIdByFile = sourceIds.get(domain + "/" + condition);
        Map<String, String> claimIdByProposition = new LinkedHashMap<>();
        for (Claim claim : view.claims()) {
            claimIdByProposition.put(claim.proposition(), claim.id());
        }
        Crj01NecessityGrid.Result grid = Crj01NecessityGrid.run(new Crj01NecessityGrid.Input(bench,
                learner(), workspace, domain, condition, view,
                caseSet.domain(domain).declaredScope(), sourceIdByFile, observations,
                validationCases(domain), caseSet.slice(domain, "EVALUATION"),
                claimIdByProposition));
        grids.put(key, grid);
        return grid;
    }

    /** Every measured row of every grid. */
    private List<Crj01NecessityGrid.Row> allRows() throws Exception {
        List<Crj01NecessityGrid.Row> rows = new ArrayList<>();
        for (Crj01NecessityCases.Domain domain : caseSet.domains()) {
            for (String condition : CONDITIONS) {
                rows.addAll(grid(domain.id(), condition).rows());
            }
        }
        return rows;
    }

    /** The case of an id. */
    private Crj01NecessityCases.Case caseOf(String caseId) {
        for (Crj01NecessityCases.Case testCase : caseSet.cases()) {
            if (testCase.id().equals(caseId)) {
                return testCase;
            }
        }
        throw new IllegalStateException("the case set does not carry " + caseId);
    }

    /**
     * Prepares a (domain, condition) workspace once: the corpus, the condition and the
     * learning episode.
     *
     * @param domain    the domain
     * @param condition the condition
     */
    private void prepared(String domain, String condition) throws Exception {
        String key = domain + "/" + condition;
        if (episodes.containsKey(key)) {
            return;
        }
        prepare(domain, condition, workspaceOf(domain, condition));
    }

    /**
     * Prepares a (domain, condition) workspace: the corpus, the condition, and the learning
     * episode.
     *
     * @param domain    the domain
     * @param condition the condition
     * @param workspace the workspace
     */
    private void prepare(String domain, String condition, String workspace) throws Exception {
        Map<String, String> sourceIdByFile = new LinkedHashMap<>();
        for (String file : caseSet.domain(domain).files()) {
            SourceRecord source = bench.importDocument(workspace,
                    corpusDirectory.resolve(domain).resolve(file), file, "en").source();
            sourceIdByFile.put(file, source.sourceId());
        }
        sourceIds.put(domain + "/" + condition, sourceIdByFile);
        if ("CHANGED_BINDINGS".equals(condition)) {
            weakenBindings(workspace);
        }
        if ("SUPERSEDED".equals(condition)) {
            supersede(domain, workspace, sourceIdByFile);
        }
        List<String> observed = new ArrayList<>();
        for (Crj01NecessityCases.Case testCase : caseSet.slice(domain, "PROPOSAL")) {
            ArtifactReportResource.JourneyResult result = route.journey(workspace,
                    request(testCase, "proposal-" + condition), ReportJourneyMethod.REFERENCE_ID);
            assertThat(result.response().getStatus())
                    .as("the learning episode's journey was served").isEqualTo(201);
            observed.add(ReportJourneyTrace.episodeId(result.outcome().taskId()));
        }
        episodes.put(domain + "/" + condition, observed);
    }

    /**
     * Weakens the workspace's Hebbian bindings through the production path.
     *
     * @param workspace the workspace
     */
    private void weakenBindings(String workspace) {
        List<Concept> concepts = bench.workspace(workspace).concepts();
        assertThat(concepts).as("the workspace holds concepts to weaken").isNotEmpty();
        List<LearningError> errors = new ArrayList<>();
        for (int index = 0; index < Math.min(3, concepts.size()); index++) {
            errors.add(new LearningError("necessity-error-" + index, workspace,
                    "necessity-item-" + index, concepts.get(index).id(), LearningDimension.RECALL,
                    "the learner answered with the wrong claim about " + concepts.get(index).name(),
                    "the correct claim about " + concepts.get(index).name(), true));
        }
        bench.store().recordLearningErrors(workspace, errors);
        bench.applyErrorDrivenDecay(workspace);
    }

    /**
     * Supersedes a document through the production source-revision path.
     *
     * @param domain         the domain
     * @param workspace      the workspace
     * @param sourceIdByFile the source ids
     */
    private void supersede(String domain, String workspace, Map<String, String> sourceIdByFile)
            throws IOException {
        String file = caseSet.domain(domain).files().get(0);
        Source superseded = sourceOf(workspace, sourceIdByFile.get(file));
        String documentKey = "necessity-document-" + domain + "-" + file;
        bench.selectSourceRevision(new SourceRevisionSelection("necessity-revision-1", workspace,
                documentKey, superseded.id(), superseded.contentHash(), null));
        Path revised = directory.resolve("revised-" + domain + "-" + file);
        Files.writeString(revised, Files.readString(corpusDirectory.resolve(domain).resolve(file))
                + "\nA revised document adds one further observation about its subject.\n",
                StandardCharsets.UTF_8);
        SourceRecord imported = bench.importDocument(workspace, revised, file, "en").source();
        bench.selectSourceRevision(new SourceRevisionSelection("necessity-revision-2", workspace,
                documentKey, imported.sourceId(), imported.contentHash(), "necessity-revision-1"));
    }

    /**
     * The source of a source id.
     *
     * @param workspace the workspace
     * @param sourceId  the source's id
     * @return the source
     */
    private Source sourceOf(String workspace, String sourceId) {
        for (Source source : bench.workspace(workspace).sources()) {
            if (source.id().equals(sourceId)) {
                return source;
            }
        }
        throw new IllegalStateException("the workspace holds no source " + sourceId);
    }

    /**
     * The observed episodes of a domain and a condition.
     *
     * @param domain    the domain
     * @param condition the condition
     * @return the observations
     */
    private List<ReportProcedureLearner.Observation> observations(String domain, String condition)
            throws Exception {
        prepared(domain, condition);
        List<String> observed = episodes.get(domain + "/" + condition);
        assertThat(observed).as("the learning episode of %s/%s was run", domain, condition)
                .isNotNull();
        return ReportProcedureLearner.observationsFrom(cognitive.store(), observed);
    }

    /**
     * The declared scope of a domain, as source identifiers.
     *
     * @param domain    the domain
     * @param condition the condition
     * @return the source ids
     */
    private Set<String> declaredSources(String domain, String condition) {
        Map<String, String> sourceIdByFile = sourceIds.get(domain + "/" + condition);
        assertThat(sourceIdByFile).as("the workspace of %s/%s was prepared", domain, condition)
                .isNotNull();
        Set<String> declared = new LinkedHashSet<>();
        for (String file : caseSet.domain(domain).declaredScope()) {
            String sourceId = sourceIdByFile.get(file);
            assertThat(sourceId).as("the declared document %s was imported", file).isNotNull();
            declared.add(sourceId);
        }
        return declared;
    }

    /**
     * The held-out cases whose reading stays INSIDE the declared scope.
     *
     * <p><b>Why this subset exists and what it costs.</b> The production promotion rule
     * requires the restricted reading over the scope to reproduce the reference on <b>every</b>
     * held-out case. An independent case author does not know which documents a competence
     * will declare, so a held-out question may reach outside — and then the promotion is
     * refused whatever the scope. The milestone measures how many held-out questions reach
     * outside, validates on the confined ones, and <b>uses</b> the unconfined ones as the
     * journey's unseen task.</p>
     *
     * @param domain    the domain
     * @param condition the condition
     * @param declared  the declared scope, as source identifiers
     * @return the held-out cases that stay inside the scope
     */
    private List<ReportProcedureLearner.Case> confinedValidationCases(String domain,
            String condition, Set<String> declared) throws Exception {
        prepared(domain, condition);
        KnowledgeSnapshot view = bench.workspace(workspaceOf(domain, condition));
        List<ReportProcedureLearner.Case> cases = new ArrayList<>();
        for (Crj01NecessityCases.Case testCase : caseSet.slice(domain, "VALIDATION")) {
            Answer reading = bench.askInView(view, testCase.question(),
                    AbstractionLevel.valueOf(testCase.level()), Set.of(), false);
            boolean inside = true;
            for (Claim claim : reading.claims()) {
                String sourceId = claim.introducedBySourceId();
                if (sourceId != null && !declared.contains(sourceId)) {
                    inside = false;
                    break;
                }
            }
            if (inside) {
                cases.add(new ReportProcedureLearner.Case(testCase.id(), testCase.question(),
                        AbstractionLevel.valueOf(testCase.level())));
            }
        }
        return cases;
    }

    /**
     * The held-out cases of a domain's validation.
     *
     * @param domain the domain
     * @return the cases
     */
    private List<ReportProcedureLearner.Case> validationCases(String domain) {
        List<ReportProcedureLearner.Case> cases = new ArrayList<>();
        for (Crj01NecessityCases.Case testCase : caseSet.slice(domain, "VALIDATION")) {
            cases.add(new ReportProcedureLearner.Case(testCase.id(), testCase.question(),
                    AbstractionLevel.valueOf(testCase.level())));
        }
        return cases;
    }

    /**
     * A task of the domain that a scope cannot carry.
     *
     * @param domain the domain
     * @param scope  the scope
     * @return the task, or null when the domain carries none
     */
    private Crj01NecessityCases.Case distinctCase(String domain, Set<String> scope)
            throws Exception {
        Map<String, String> sourceIdByFile = sourceIds.get(domain + "/BASELINE");
        KnowledgeSnapshot view = bench.workspace(workspaceOf(domain, "BASELINE"));
        for (Crj01NecessityCases.Case testCase : caseSet.slice(domain, "EVALUATION")) {
            boolean carried = true;
            for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                String sourceId = sourceIdByFile.get(evidence.corpusFile());
                if (sourceId == null || !scope.contains(sourceId)) {
                    carried = false;
                    break;
                }
            }
            if (carried) {
                continue;
            }
            // the reference must be able to CARRY the task's evidence, or the comparison
            // would be about the retriever and not about the scope
            Answer reading = bench.askInView(view, testCase.question(),
                    AbstractionLevel.valueOf(testCase.level()), Set.of(), false);
            List<String> propositions = new ArrayList<>();
            for (Claim claim : reading.claims()) {
                propositions.add(claim.proposition());
            }
            boolean reachable = true;
            for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                if (!propositions.contains(evidence.proposition())) {
                    reachable = false;
                    break;
                }
            }
            if (reachable) {
                return testCase;
            }
        }
        return null;
    }

    /**
     * The request of a journey.
     *
     * @param testCase the case
     * @param name     the report name
     * @return the request
     */
    private static ArtifactReportResource.ReportRequest request(Crj01NecessityCases.Case testCase,
            String name) {
        // The report NAME is left to the action's own default and the request carries an
        // idempotency key of its own: two cases of the same episode must not write the same
        // file, and a reused key under a changed question is a conflict the action refuses —
        // which is a property of the route, not of this milestone.
        return new ArtifactReportResource.ReportRequest(testCase.question(), null, Boolean.TRUE,
                "necessity-" + testCase.id().toLowerCase(Locale.ROOT) + "-" + name,
                testCase.level(), null);
    }

    /**
     * The propositions the response cites.
     *
     * @param response the response
     * @return the propositions
     */
    private static String sourceOfId(KnowledgeSnapshot view, Set<String> scope) {
        for (Source source : view.sources()) {
            if (scope.contains(source.id())) {
                return source.id();
            }
        }
        return view.sources().isEmpty() ? null : view.sources().get(0).id();
    }

    private static List<String> citedOf(jakarta.ws.rs.core.Response response) {
        List<String> propositions = new ArrayList<>();
        for (ArtifactReportResource.EvidenceReference reference : body(response).evidence()) {
            propositions.add(reference.proposition());
        }
        return propositions;
    }

    /**
     * The body of a response.
     *
     * @param response the response
     * @return the body
     */
    private static ArtifactReportResource.ReportResponse body(
            jakarta.ws.rs.core.Response response) {
        Object entity = response.getEntity();
        assertThat(entity).as("the response carries a body")
                .isInstanceOf(ArtifactReportResource.ReportResponse.class);
        return (ArtifactReportResource.ReportResponse) entity;
    }

    /**
     * The sentences of a corpus document.
     *
     * @param domain the domain
     * @param file   the file
     * @return the sentences
     */
    private List<String> sentenceOf(String domain, String file) throws IOException {
        String text = Files.readString(corpusDirectory.resolve(domain).resolve(file),
                StandardCharsets.UTF_8);
        List<String> sentences = new ArrayList<>();
        for (String block : text.split("\n\\s*\n")) {
            String trimmed = block.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /**
     * The bytes of a corpus document, as they are on the classpath.
     *
     * @param domain the domain
     * @param file   the file
     * @return the bytes
     * @throws IOException when the corpus cannot be read
     */
    private byte[] corpusBytes(String domain, String file) throws IOException {
        String resource = CORPUS_RESOURCE + domain + "/" + file;
        try (InputStream in = EpistemicNecessityServedTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the corpus document " + resource + " is absent");
            }
            return in.readAllBytes();
        }
    }

    /**
     * The digest of a string.
     *
     * @param value the value
     * @return the digest
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * Refreshes the manifest of the retained evidence directory.
     *
     * @param directory the directory
     * @throws IOException when the manifest cannot be written
     */
    private static void refreshManifest(Path directory) throws IOException {
        List<String> lines = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            for (Path path : entries.sorted().toList()) {
                if (path.getFileName().toString().equals("CHECKSUMS.sha256")) {
                    continue;
                }
                if (!Files.isRegularFile(path)) {
                    // a sub-directory is not a file of the manifest: the case authors' own
                    // files are theirs, and the manifest covers the files this milestone wrote
                    continue;
                }
                lines.add(sha256(Files.readString(path, StandardCharsets.UTF_8))
                        + "  " + path.getFileName());
            }
        }
        Files.writeString(directory.resolve("CHECKSUMS.sha256"),
                String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }
}
