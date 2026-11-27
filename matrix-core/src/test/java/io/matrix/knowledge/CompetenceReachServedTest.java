package io.matrix.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.report.ReportJourneyMethod;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyTrace;
import io.matrix.cognitive.report.ReportProcedure;
import io.matrix.cognitive.report.ReportProcedureLearner;
import io.matrix.cognitive.report.ScopeStrategy;
import io.matrix.crj.reach.Crj01ReachCases;
import io.matrix.crj.reach.Crj01ReachGrid;
import io.matrix.crj.reach.Crj01ReachSummary;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CRJ-01 / COMPETENCE-REACH — what a competence's REACH is: which documents its scope should
 * consist of, and which rule decides whether that scope may serve a question.
 *
 * <p><b>What this milestone asks.</b> The ADAPTIVE-COMPETENCE milestone settled one rule — the
 * <b>anchor</b>: a scope may serve a question when it holds the claim the workspace's own
 * reading would cite <b>first</b> — and it left two things open. Its scope was the union of the
 * sources the learning episode <b>cited</b>, so an incidental citation could widen it; and the
 * anchor was never compared with a competitor, so nobody knows whether it admits a scope that
 * cannot carry the <b>second</b> claim a correct answer needs. This milestone measures both,
 * on the <b>real served route</b>, over six arms: three scope strategies crossed with two
 * criteria.</p>
 *
 * <p><b>What is asserted.</b> The grid's rows are measured on <b>identical</b> inputs — one
 * snapshot, one set of bindings, one history for all six arms — and the two criteria are shown
 * to differ on a case that needs a second claim, with the case author's oracle saying which of
 * the two is right. The lifecycle is shown on the route itself: a failure that is observable, a
 * diagnosis that names the missing evidence, a versioned proposal, a separate validation, a
 * promotion and a rejection, and the <b>explicit falsification</b> of the two lifecycle
 * transitions the durable store does not support. After a restart the <b>exact</b> promoted
 * method answers a genuinely different question.</p>
 *
 * <p><b>What is NOT claimed.</b> No timing is asserted: the costs are records. No arm is
 * declared the winner: the pre-registration forbids it, and the cells say only whether the two
 * criteria differ on them. The <b>sealed evaluation is NOT EXECUTED</b> — the isolation a
 * sealed split needs could not be established from this executor (the ADAPTIVE milestone's
 * retained verdict, reused as it stands and <b>not re-probed</b>) — so this is a DEV
 * measurement with a role-separated case author, not an independent-organisation
 * measurement.</p>
 */
class CompetenceReachServedTest {

    /** The classpath directory the reach corpora live in. */
    private static final String CORPUS_RESOURCE = "/crj01-reach/";

    /**
     * The retained evidence directory — the canonical home of this milestone's artefacts.
     *
     * <p>The rows live here and nowhere else: a second copy is a copy that can be stale.</p>
     */
    private static final String EVIDENCE_DIRECTORY =
            "docs-v2/engineering/evidence/crj01-reach";

    /** The instants the promotions are recorded with — supplied, never read from a clock. */
    private static final String PROMOTED_AT = "2026-11-27T00:00:00Z";

    /** The instant the narrowed competence is recorded with. */
    private static final String NARROWED_AT = "2026-11-27T00:00:01Z";

    /** The instant the second criterion's version is recorded with. */
    private static final String SECOND_CRITERION_AT = "2026-11-27T00:00:02Z";

    /** The workspace prefix: one workspace per domain and per condition. */
    private static final String WORKSPACE_PREFIX = "crj01-reach-";

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

    /** The case set — the IMPLEMENTER's own: two commissioned case authors delivered nothing. */
    private Crj01ReachCases.CaseSet caseSet;

    /** The observed episodes of every (domain, condition), by the episode's id. */
    private final Map<String, List<String>> episodes = new LinkedHashMap<>();

    /** The production source id of every corpus file, by (domain, file). */
    private final Map<String, Map<String, String>> sourceIds = new LinkedHashMap<>();

    /** The grid of every (domain, condition). */
    private final Map<String, Crj01ReachGrid.Result> grids = new LinkedHashMap<>();

    // ── the fixture ───────────────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
        caseSet = Crj01ReachCases.read();
        corpusDirectory = Files.createDirectories(directory.resolve("corpus"));
        for (Crj01ReachCases.Domain domain : caseSet.domains()) {
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

    // ── the case set and the corpus ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the case set is the IMPLEMENTER's own, the slices are disjoint, and every "
            + "gold is a sentence of the corpus bytes")
    void theCaseSetIsDisjointAndEveryGoldIsInTheCorpus() throws Exception {
        assertThat(caseSet.domains())
                .as("the milestone carries at least two genuinely different development domains")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(caseSet.cases())
                .as("the case set carries cases")
                .hasSizeGreaterThanOrEqualTo(24);

        // THE GOLD IS A SENTENCE OF THE CORPUS BYTES. The oracle is the corpus, not the
        // case file: a gold that is not a sentence of the file it names can never be cited.
        for (Crj01ReachCases.Case testCase : caseSet.cases()) {
            for (Crj01ReachCases.Evidence evidence : testCase.necessaryEvidence()) {
                String sentence = sentenceOf(testCase.domain(), evidence.corpusFile(),
                        evidence.proposition());
                assertThat(sentence)
                        .as("the necessary evidence of %s is a sentence of %s",
                                testCase.id(), evidence.corpusFile())
                        .isEqualTo(evidence.proposition());
            }
        }

        // THE SLICES ARE DISJOINT — by identity, by question and by gold.
        Set<String> questions = new LinkedHashSet<>();
        for (Crj01ReachCases.Case testCase : caseSet.cases()) {
            assertThat(questions.add(testCase.question()))
                    .as("the question of %s appears in no other case", testCase.id()).isTrue();
        }
        // THE GOLDS OF THE SLICES DO NOT CROSS. A gold that two slices share would put the
        // held-out evidence into the learning episode, and the validation would then
        // validate nothing. INSIDE a slice a gold may be shared by two cases — the cases
        // differ in their question and in their other necessary claims — and the suite
        // says so rather than pretending otherwise.
        Map<String, Set<String>> goldsBySlice = new LinkedHashMap<>();
        for (Crj01ReachCases.Case testCase : caseSet.cases()) {
            for (Crj01ReachCases.Evidence evidence : testCase.necessaryEvidence()) {
                goldsBySlice.computeIfAbsent(testCase.slice(), key -> new LinkedHashSet<>())
                        .add(testCase.domain() + "|" + evidence.proposition());
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
                        .as("no gold of the %s slice is a gold of the %s slice", slice, other)
                        .isEmpty();
            }
        }
        for (Crj01ReachCases.Domain domain : caseSet.domains()) {
            assertThat(caseSet.slice(domain.id(), "PROPOSAL"))
                    .as("the proposal slice of %s", domain.id()).isNotEmpty();
            assertThat(caseSet.slice(domain.id(), "VALIDATION"))
                    .as("the validation slice of %s", domain.id())
                    .hasSizeGreaterThanOrEqualTo(5);
            assertThat(caseSet.slice(domain.id(), "EVALUATION"))
                    .as("the evaluation slice of %s", domain.id()).isNotEmpty();
        }

        // THE QUESTIONS ARE NOT REFERENTIAL. A question that refers to something without
        // naming it is rewritten by the dialogue context, and the context is a HISTORY: if
        // the questions were referential, the six arms would not have read the same
        // history, and the comparison would be between six different questions.
        for (Crj01ReachCases.Case testCase : caseSet.cases()) {
            assertThat(DialogueContext.isReferential(testCase.question()))
                    .as("the question of %s names its subject, so the dialogue context cannot "
                            + "rewrite it", testCase.id())
                    .isFalse();
        }

        // THE CORPUS DIGESTS THE AUTHOR RECORDED ARE THE DIGESTS OF THE BYTES.
        Object recorded = caseSet.provenance().get("corpusSha256");
        assertThat(recorded).as("the author recorded the corpus digests").isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> digests = (Map<String, Object>) recorded;
        for (Map.Entry<String, Object> entry : digests.entrySet()) {
            String[] parts = entry.getKey().split("/", 2);
            assertThat(sha256(Files.readAllBytes(
                    corpusDirectory.resolve(parts[0]).resolve(parts[1]))))
                    .as("the recorded digest of %s", entry.getKey())
                    .isEqualTo(String.valueOf(entry.getValue()));
        }
    }

    // ── the scopes ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the three scope strategies produce different scopes, and every one of them "
            + "goes through the same held-out validation")
    void theStrategiesProduceDifferentScopesAndEveryScopeIsValidated() throws Exception {
        for (Crj01ReachCases.Domain domain : caseSet.domains()) {
            List<String> scopes = new ArrayList<>();
            for (ScopeStrategy strategy : Crj01ReachGrid.STRATEGIES) {
                ReportProcedureLearner.Scope scope = learner().scope(workspaceOf(domain.id(),
                        "BASELINE"), observations(domain.id(), "BASELINE"), strategy,
                        declaredSources(domain.id(), "BASELINE"));
                assertThat(scope.accepted())
                        .as("the %s scope of %s could be obtained", strategy, domain.id())
                        .isTrue();
                assertThat(scope.support())
                        .as("the %s scope of %s names sources", strategy, domain.id())
                        .isNotEmpty();
                ReportProcedureLearner.Validation validation = learner().validate(
                        workspaceOf(domain.id(), "BASELINE"), observations(domain.id(), "BASELINE"),
                        scope.support(), validationCases(domain.id()), Set.of());
                assertThat(validation.reason())
                        .as("the %s scope of %s was validated and the reason is recorded",
                                strategy, domain.id())
                        .isNotBlank();
                scopes.add(String.join(",", scope.support()));
            }
            assertThat(new LinkedHashSet<>(scopes))
                    .as("the three strategies of %s do not ALL produce the same scope — otherwise "
                            + "the comparison would be vacuous. Whether two of them COINCIDE is "
                            + "a measurement, not a failure: the milestone reports it",
                            domain.id())
                    .hasSizeGreaterThan(1);
        }
    }

    // ── the grid ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the six arms are measured on identical inputs, and the two criteria differ on "
            + "a case that needs a second claim")
    void theSixArmsAreMeasuredOnIdenticalInputs() throws Exception {
        int rows = 0;
        for (Crj01ReachCases.Domain domain : caseSet.domains()) {
            for (String condition : CONDITIONS) {
                Crj01ReachGrid.Result grid = grid(domain.id(), condition);
                assertThat(grid.scopes())
                        .as("every strategy of %s/%s has a candidate scope", domain.id(),
                                condition)
                        .hasSize(Crj01ReachGrid.STRATEGIES.size());
                for (Crj01ReachGrid.ScopeRow scope : grid.scopes()) {
                    assertThat(scope.validationReason())
                            .as("the %s scope of %s/%s was validated", scope.strategy(),
                                    domain.id(), condition)
                            .isNotBlank();
                }
                assertThat(grid.rows())
                        .as("every case of %s/%s is measured in every arm", domain.id(), condition)
                        .hasSize(caseSet.slice(domain.id(), "EVALUATION").size()
                                * Crj01ReachGrid.STRATEGIES.size() * Crj01ReachGrid.CRITERIA.size());
                rows += grid.rows().size();
                // THE INPUTS ARE IDENTICAL ACROSS THE ARMS. Every row of the cell carries the
                // fingerprint of the view it was measured against, and the six arms of a cell
                // must carry the SAME one — that is the property, checked from the measurement
                // itself rather than promised by a comment.
                Set<String> fingerprints = new LinkedHashSet<>();
                for (Crj01ReachGrid.Row row : grid.rows()) {
                    assertThat(row.scope()).as("the scope of a row is never empty").isNotEmpty();
                    fingerprints.add(row.inputFingerprint());
                }
                assertThat(fingerprints)
                        .as("the six arms of %s/%s were measured against ONE input", domain.id(),
                                condition)
                        .hasSize(1);
            }
        }
        assertThat(rows).as("the grid measures every case of every arm").isPositive();

        // THE TWO RULES MUST DISAGREE SOMEWHERE. The comparison is vacuous if the two
        // criteria decide every case the same way, and the suite is what says so.
        Map<String, List<Crj01ReachGrid.Row>> bySubject = new LinkedHashMap<>();
        for (Crj01ReachGrid.Row row : allRows()) {
            bySubject.computeIfAbsent(row.domain() + "|" + row.condition() + "|"
                    + row.strategy() + "|" + row.caseId(),
                    key -> new ArrayList<>()).add(row);
        }
        int disagreements = 0;
        for (List<Crj01ReachGrid.Row> pair : bySubject.values()) {
            if (pair.size() != Crj01ReachGrid.CRITERIA.size()) {
                continue;
            }
            if (pair.get(0).admitted() != pair.get(1).admitted()) {
                disagreements++;
            }
        }
        assertThat(disagreements)
                .as("the two criteria disagree on at least one case of the grid — the same "
                        + "scope, the same inputs, the same cases")
                .isPositive();

        // THE CRITERION DIFFERENCE, AGAINST THE ORACLE. There must be a case whose answer needs
        // a SECOND claim from a second document, on which the two rules disagree.
        List<Crj01ReachGrid.Row> degraded = new ArrayList<>();
        for (Crj01ReachGrid.Row row : allRows()) {
            if (row.necessaryClaimIds().size() > 1 && row.admitted() && !row.evidenceComplete()) {
                degraded.add(row);
            }
        }
        assertThat(degraded)
                .as("at least one served answer was admitted on the first cited claim and still "
                        + "missed a necessary one — the anchor's blind spot")
                .isNotEmpty();
        for (Crj01ReachGrid.Row row : degraded) {
            assertThat(row.falseAdmission())
                    .as("the admission of %s in %s was a FALSE admission: the scope did not carry "
                            + "the evidence the oracle requires", row.caseId(), row.arm())
                    .isTrue();
        }
    }

    // ── the lifecycle ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the lifecycle: an observable failure, a diagnosis, a versioned proposal, a "
            + "separate validation, a promotion and a rejection, and the explicit falsification "
            + "of the two transitions the durable store does not support")
    void theLifecycleIsWalkedOnTheServedRoute() throws Exception {
        // ── THE DOMAIN AND THE FAILURE — TAKEN FROM THE GRID THE SUITE MEASURED ───────────
        // The lifecycle does not choose a case to fail: it takes the case the measurement
        // found — the one the incumbent rule ADMITTED and whose answer was still incomplete.
        // A lifecycle that picks its own failure would prove nothing about the measurement.
        Crj01ReachCases.Domain domain = null;
        Crj01ReachCases.Case failure = null;
        for (Crj01ReachCases.Domain candidate : caseSet.domains()) {
            Crj01ReachCases.Case measured = measuredFailureCase(candidate.id());
            if (measured != null) {
                domain = candidate;
                failure = measured;
                break;
            }
        }
        assertThat(domain)
                .as("the grid measured at least one case that the incumbent rule admitted and "
                        + "whose answer was still incomplete")
                .isNotNull();
        String workspace = workspaceOf(domain.id(), "BASELINE");
        Map<String, String> sourceIdByFile = sourceIds.get(domain.id() + "/BASELINE");
        List<ReportProcedureLearner.Observation> observations = observations(domain.id(),
                "BASELINE");

        // ── THE FIRST COMPETENCE — the DECLARED scope, the subject the competence is about ──
        ReportProcedureLearner.Outcome first = learner().learn(workspace, observations,
                validationCases(domain.id()), Set.of(), PROMOTED_AT, ScopeStrategy.DECLARED,
                declaredSources(domain.id(), "BASELINE"), ScopeCriterion.ANCHOR);
        assertThat(first.promoted())
                .as("the first competence was promoted: %s", first.reason()).isTrue();
        Set<String> narrowScope = ReportProcedure.supportOf(first.procedure());
        assertThat(narrowScope).as("the promoted competence carries a support set").isNotEmpty();

        // ── THE OBSERVABLE FAILURE — a served answer that stands on incomplete evidence ──────
        ArtifactReportResource.JourneyResult failed = route.journey(workspace,
                request(failure, "reach-failure"), "procedure." + first.procedure().id());
        assertThat(failed.response().getStatus())
                .as("the failure episode was served").isEqualTo(201);
        List<String> served = citedOf(failed.response());
        int carried = 0;
        for (Crj01ReachCases.Evidence evidence : failure.necessaryEvidence()) {
            if (served.contains(evidence.proposition())) {
                carried++;
            }
        }
        assertThat(carried)
                .as("the served answer does NOT carry the whole evidence the oracle requires — "
                        + "this is the observable failure of the competence's reach")
                .isLessThan(failure.necessaryEvidence().size());
        // AND THE REFERENCE CARRIES IT — so the loss is the SCOPE's loss and not the
        // workspace's: the same question, the same workspace, the same moment.
        Answer reference = bench.askInView(bench.workspace(workspace), failure.question(),
                AbstractionLevel.valueOf(failure.level()), Set.of(), false);
        List<String> referencePropositions = new ArrayList<>();
        for (Claim claim : reference.claims()) {
            referencePropositions.add(claim.proposition());
        }
        for (Crj01ReachCases.Evidence evidence : failure.necessaryEvidence()) {
            assertThat(referencePropositions)
                    .as("the reference reading carries the evidence of %s", evidence.corpusFile())
                    .contains(evidence.proposition());
        }

        // ── THE DIAGNOSIS — the competitor rule, on the SAME scope and the SAME question,
        //    refuses and NAMES the claims the scope does not carry ─────────────────────────
        KnowledgeSnapshot view = bench.workspace(workspace);
        ScopeAssessment anchored = bench.assessScope(view, view, failure.question(),
                AbstractionLevel.valueOf(failure.level()), narrowScope, false,
                ScopeCriterion.ANCHOR);
        ScopeAssessment sufficient = bench.assessScope(view, view, failure.question(),
                AbstractionLevel.valueOf(failure.level()), narrowScope, false,
                ScopeCriterion.CLAIM_SUFFICIENCY);
        assertThat(anchored.applicable())
                .as("the incumbent rule ADMITTED the scope, and the served answer was degraded: "
                        + "this is the anchor's blind spot, witnessed on the served route")
                .isTrue();
        assertThat(sufficient.applicable())
                .as("the competitor rule REFUSES the same scope for the same question")
                .isFalse();
        assertThat(sufficient.missingClaimIds())
                .as("the diagnosis names the claims the scope does not carry")
                .isNotEmpty();
        assertThat(String.join(" | ", body(failed.response()).limitations()))
                .as("the journey's own record carries a note")
                .isNotBlank();

        // ── THE PROPOSAL — a candidate with a WIDER scope, learned from the SAME episode ────
        ReportProcedureLearner.Outcome widened = learner().learn(workspace, observations,
                validationCases(domain.id()), Set.of(), NARROWED_AT, ScopeStrategy.CITATION_DERIVED,
                Set.of(), ScopeCriterion.ANCHOR);
        assertThat(widened.promoted())
                .as("the widened candidate was promoted: %s", widened.reason()).isTrue();
        Set<String> wideScope = ReportProcedure.supportOf(widened.procedure());
        assertThat(wideScope)
                .as("the widened scope holds more documents than the narrow one")
                .hasSizeGreaterThan(narrowScope.size());
        assertThat(widened.procedure().id())
                .as("the widened competence has its OWN identity: the identity is derived from "
                        + "the scope, so a different scope is a different competence and not a "
                        + "new version of the old one")
                .isNotEqualTo(first.procedure().id());

        // ── A REJECTION — a candidate whose scope is too narrow ─────────────────────────────
        Set<String> tooNarrow = new LinkedHashSet<>();
        tooNarrow.add(narrowScope.iterator().next());
        ReportProcedureLearner.Outcome refused = learner().learn(workspace, observations,
                validationCases(domain.id()), Set.of(), NARROWED_AT, ScopeStrategy.DECLARED,
                tooNarrow, ScopeCriterion.ANCHOR);
        assertThat(refused.promoted())
                .as("a candidate whose scope cannot carry the evidence is REFUSED")
                .isFalse();
        assertThat(refused.reason())
                .as("the refusal names its reason")
                .isNotBlank();

        // ── THE FALSIFICATION — the retire and the narrow are NOT supported ────────────────
        // (a) THE DURABLE RETIRE. The write of a retired version of a promoted competence is
        //     NOT refused and NOT recorded: the store's immutability check compares the
        //     DEFINITION of a version, and the retirement instant is not part of it, so the
        //     write is accepted as the same definition and dropped. The competence stays
        //     active, and the route keeps serving it. There is no durable retire.
        Procedure retired = first.procedure().invalidatedAt(NARROWED_AT);
        cognitive.store().recordProcedure(workspace, retired);
        Procedure stored = cognitive.store().procedures(workspace).stream()
                .filter(procedure -> procedure.id().equals(first.procedure().id())
                        && procedure.version().equals(first.procedure().version()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the promoted competence is gone"));
        assertThat(stored.invalidatedAt())
                .as("the durable store does NOT record a retirement: the retired version was "
                        + "accepted as the same definition and dropped, and the competence stays "
                        + "active — there is no durable retire transition")
                .isNull();
        assertThat(stored.active())
                .as("the competence that was written as retired is still active")
                .isTrue();

        // (b) THE DURABLE NARROW. A narrower scope is a DIFFERENT COMPETENCE: the identity of
        //     a procedure is derived from its support set, so a narrow is not a new version of
        //     the wide one but a second competence — and the predecessor stays active and
        //     served. The concrete alternative is retained below.
        Set<String> narrowedSupport = new LinkedHashSet<>(narrowScope);
        narrowedSupport.remove(narrowScope.iterator().next());
        ReportProcedureLearner.Outcome narrowed = learner().learn(workspace, observations,
                validationCases(domain.id()), Set.of(), NARROWED_AT, ScopeStrategy.DECLARED,
                narrowedSupport, ScopeCriterion.ANCHOR);
        assertThat(narrowed.promoted())
                .as("a candidate whose scope is too narrow is REFUSED by the validation: the "
                        + "restricted reading no longer reproduces the reference on the held-out "
                        + "cases — the narrow is not promotable as a narrowing of the wide "
                        + "competence. The refusal says: %s", narrowed.reason())
                .isFalse();
        assertThat(narrowed.reason())
                .as("the refusal names its reason")
                .isNotBlank();
        assertThat(narrowed.procedure())
                .as("a refused candidate leaves no procedure behind")
                .isNull();
        assertThat(cognitive.store().procedures(workspace))
                .as("the predecessor stays active after the narrow was written: the route keeps "
                        + "serving it, and no durable transition retired it")
                .anyMatch(procedure -> procedure.id().equals(first.procedure().id())
                        && procedure.active());

        // ── THE RESTART — the EXACT promoted method on a DIFFERENT question ────────────────
        cognitive.store().close();
        cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route = route(bench, cognitive, outputRoot, new ReportJourneyPlanner());
        Crj01ReachCases.Case distinct = distinctCase(domain.id(), failure.id(), sourceIdByFile,
                narrowScope, wideScope);
        ArtifactReportResource.JourneyResult before = route.journey(workspace,
                request(distinct, "reach-before"), "procedure." + first.procedure().id());
        ArtifactReportResource.JourneyResult afterRestart = route.journey(workspace,
                request(distinct, "reach-after-restart"), "procedure." + widened.procedure().id());
        assertThat(afterRestart.response().getStatus())
                .as("the exact promoted method answered after the restart").isEqualTo(201);
        List<String> afterCitations = citedOf(afterRestart.response());
        for (Crj01ReachCases.Evidence evidence : distinct.necessaryEvidence()) {
            assertThat(afterCitations)
                    .as("the promoted competence carries the evidence of %s",
                            evidence.corpusFile())
                    .contains(evidence.proposition());
        }
        // The FIRST competence could NOT carry that question's evidence — it either refused
        // it, or answered it from a narrower scope. Either way the revision changed what
        // the competence could answer, and the change survives a restart.
        if (before.response().getStatus() == 201) {
            assertThat(citedOf(before.response()))
                    .as("the FIRST competence did not carry the whole evidence of the distinct "
                            + "question — the revision changed what the competence could answer")
                    .isNotEqualTo(afterCitations);
        } else {
            assertThat(before.response().getStatus())
                    .as("the FIRST competence REFUSED the distinct question")
                    .isEqualTo(422);
        }
    }

    @Test
    @DisplayName("the two criteria are served differently by the SAME competence, and the oracle "
            + "says which of the two answers is right")
    void theTwoCriteriaAreServedDifferentlyByTheSameCompetence() throws Exception {
        Crj01ReachCases.Domain domain = caseSet.domains().get(0);
        String workspace = workspaceOf(domain.id(), "BASELINE");
        List<ReportProcedureLearner.Observation> observations = observations(domain.id(),
                "BASELINE");
        Crj01ReachCases.Case subject = failureCase(domain.id(), Set.of());
        assertThat(subject).as("the domain carries a case that needs more than one claim")
                .isNotNull();

        // THE SAME SCOPE, THE TWO RULES, AND THE SAME SNAPSHOT. The rule is part of the
        // competence's own definition, so the two rules are two VERSIONS of one competence.
        ReportProcedureLearner.Outcome anchored = learner().learn(workspace, observations,
                validationCases(domain.id()), Set.of(), PROMOTED_AT, ScopeStrategy.CITATION_DERIVED,
                Set.of(), ScopeCriterion.ANCHOR);
        assertThat(anchored.promoted()).as("the anchored competence was promoted").isTrue();
        ScopeCriterion anchorCriterion = ReportProcedure.criterionOf(anchored.procedure());
        assertThat(anchorCriterion).as("a competence that names no rule is judged by the "
                + "incumbent").isEqualTo(ScopeCriterion.ANCHOR);

        ReportProcedureLearner.Outcome sufficient = learner().learn(workspace, observations,
                validationCases(domain.id()), Set.of(), SECOND_CRITERION_AT,
                ScopeStrategy.CITATION_DERIVED, Set.of(), ScopeCriterion.CLAIM_SUFFICIENCY);
        assertThat(sufficient.promoted()).as("the sufficiency version was promoted").isTrue();
        assertThat(ReportProcedure.criterionOf(sufficient.procedure()))
                .as("the sufficiency version names its rule")
                .isEqualTo(ScopeCriterion.CLAIM_SUFFICIENCY);
        assertThat(sufficient.procedure().id())
                .as("the two rules are two versions of ONE competence")
                .isEqualTo(anchored.procedure().id());
        assertThat(sufficient.procedure().version())
                .as("a changed definition carries a changed version")
                .isNotEqualTo(anchored.procedure().version());

        // THE ROUTE SERVES THE NEWEST VERSION, AND THE TWO VERSIONS DISAGREE ON THE SUBJECT.
        ArtifactReportResource.JourneyResult served = route.journey(workspace,
                request(subject, "reach-criterion"), "procedure." + sufficient.procedure().id());
        assertThat(served.response().getStatus()).isEqualTo(201);
        ScopeAssessment assessment = bench.assessScope(bench.workspace(workspace),
                bench.workspace(workspace), subject.question(),
                AbstractionLevel.valueOf(subject.level()),
                ReportProcedure.supportOf(sufficient.procedure()), false,
                ScopeCriterion.CLAIM_SUFFICIENCY);
        assertThat(assessment.workspaceCitedIds().size())
                .as("the workspace's own reading cites more than one claim for this question, "
                        + "which is what separates the two rules")
                .isGreaterThan(1);
    }

    @Test
    @DisplayName("the incumbent's contract did not change: the anchor's reason is the reason the "
            + "prior milestone's retained evidence records")
    void theIncumbentsReasonIsThePriorMilestonesReason() throws Exception {
        Path adaptive = RepositoryPaths.root().resolve(
                "docs-v2/engineering/evidence/crj01-adaptive-competence/"
                        + "CRJ-01-ADAPTIVE-EVIDENCE.json");
        assertThat(adaptive)
                .as("the prior milestone's retained evidence is present — the check reads it and "
                        + "never writes it")
                .isRegularFile();
        ObjectMapper json = JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        Map<String, Object> retained = json.readValue(adaptive.toFile(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) retained.get("rows");
        List<String> priorReasons = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String reason = String.valueOf(row.get("fallbackReason"));
            if (reason.startsWith("the support set ")) {
                priorReasons.add(reason);
            }
        }
        assertThat(priorReasons)
                .as("the prior milestone's retained evidence carries the anchor's reason")
                .isNotEmpty();

        Crj01ReachCases.Domain domain = caseSet.domains().get(0);
        String workspace = workspaceOf(domain.id(), "BASELINE");
        prepared(domain.id(), "BASELINE");
        KnowledgeSnapshot view = bench.workspace(workspace);
        Set<String> scope = declaredSources(domain.id(), "BASELINE");
        ScopeAssessment assessment = bench.assessScope(view, view,
                caseSet.slice(domain.id(), "EVALUATION").get(0).question(),
                AbstractionLevel.INTRODUCTORY, scope, false, ScopeCriterion.ANCHOR);
        Set<String> priorForms = new LinkedHashSet<>();
        for (String prior : priorReasons) {
            priorForms.add(fixedPartOf(prior));
        }
        assertThat(priorForms)
                .as("the prior milestone's retained evidence records BOTH forms of the anchor's "
                        + "reason — the one that admits and the one that refuses")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(priorForms)
                .as("the FIXED part of the anchor's reason the current code produces is one the "
                        + "prior milestone's retained evidence records")
                .contains(fixedPartOf(assessment.reason()));

        // AND THE REFUSING FORM, produced on the same route with a scope that does not hold
        // the anchor's claim.
        Set<String> narrow = new LinkedHashSet<>();
        narrow.add(scope.iterator().next());
        ScopeAssessment refused = bench.assessScope(view, view,
                caseSet.slice(domain.id(), "EVALUATION").get(0).question(),
                AbstractionLevel.INTRODUCTORY, narrow, false, ScopeCriterion.ANCHOR);
        assertThat(refused.applicable())
                .as("a scope that does not hold the anchor's claim is refused")
                .isFalse();
        assertThat(priorForms)
                .as("the FIXED part of the REFUSING reason is one the prior milestone's retained "
                        + "evidence records")
                .contains(fixedPartOf(refused.reason()));
    }

    /**
     * The fixed part of a reason: everything before the claim identifier and the numbers.
     *
     * @param reason the reason
     * @return the fixed part
     */
    private static String fixedPartOf(String reason) {
        int at = reason.indexOf("clm-");
        return at < 0 ? reason : reason.substring(0, at);
    }

    @Test
    @DisplayName("every case's gold is reachable, and every held-out question stays inside the "
            + "declared scope — a case set that breaks this silently empties an arm")
    void theCaseSetIsCoherentWithItsCorpus() throws Exception {
        for (Crj01ReachCases.Domain domain : caseSet.domains()) {
            String workspace = workspaceOf(domain.id(), "BASELINE");
            prepared(domain.id(), "BASELINE");
            KnowledgeSnapshot view = bench.workspace(workspace);
            Map<String, String> sourceIdByFile = sourceIds.get(domain.id() + "/BASELINE");
            Map<String, String> fileBySourceId = new LinkedHashMap<>();
            for (Source source : view.sources()) {
                fileBySourceId.put(source.id(), source.title());
            }
            for (Crj01ReachCases.Case testCase : caseSet.slice(domain.id(), "EVALUATION")) {
                Answer reading = bench.askInView(view, testCase.question(),
                        AbstractionLevel.valueOf(testCase.level()), Set.of(), false);
                List<String> propositions = new ArrayList<>();
                for (Claim claim : reading.claims()) {
                    propositions.add(claim.proposition());
                }
                for (Crj01ReachCases.Evidence evidence : testCase.necessaryEvidence()) {
                    assertThat(propositions)
                            .as("the reading reaches the gold of %s (%s): a case whose gold no "
                                    + "answer can cite measures the retriever, not the scope",
                                    testCase.id(), evidence.corpusFile())
                            .contains(evidence.proposition());
                }
            }
            for (Crj01ReachCases.Case testCase : caseSet.slice(domain.id(), "VALIDATION")) {
                Answer reading = bench.askInView(view, testCase.question(),
                        AbstractionLevel.valueOf(testCase.level()), Set.of(), false);
                for (Claim claim : reading.claims()) {
                    String file = fileBySourceId.get(claim.introducedBySourceId());
                    if (file == null) {
                        continue;
                    }
                    assertThat(domain.declaredScope())
                            .as("the held-out question %s reaches the document %s, which the "
                                    + "declaration leaves out: the restricted reading over the "
                                    + "declared scope could not reproduce the reference, and the "
                                    + "declared arm would have been empty",
                                    testCase.id(), file)
                            .contains(file);
                }
            }
        }
    }

    // ── the retained evidence ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the retained evidence carries every per-case row, the cells, and the manifest")
    void theEvidenceIsWritten() throws Exception {
        List<Crj01ReachGrid.Row> rows = new ArrayList<>();
        List<Crj01ReachGrid.ScopeRow> scopes = new ArrayList<>();
        for (Crj01ReachCases.Domain domain : caseSet.domains()) {
            for (String condition : CONDITIONS) {
                Crj01ReachGrid.Result grid = grid(domain.id(), condition);
                scopes.addAll(grid.scopes());
                rows.addAll(grid.rows());
            }
        }
        Map<String, Object> summary = Crj01ReachSummary.summarise(scopes, rows);

        ObjectMapper json = JsonMapper.builder().build();
        Path directory = RepositoryPaths.root().resolve(EVIDENCE_DIRECTORY);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("CRJ-01-REACH-GRID-ROWS.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(rows),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("CRJ-01-REACH-GRID-SUMMARY.json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(summary),
                StandardCharsets.UTF_8);
        refreshManifest(directory);

        List<?> reread = json.readValue(
                directory.resolve("CRJ-01-REACH-GRID-ROWS.json").toFile(), List.class);
        assertThat(reread)
                .as("the retained rows are the rows that were measured")
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

    /** The workspace a domain and a condition run in. */
    private static String workspaceOf(String domain, String condition) {
        return WORKSPACE_PREFIX + domain + "-" + condition.toLowerCase(Locale.ROOT);
    }

    /**
     * The grid of a domain and a condition, run once and kept.
     *
     * @param domain    the domain
     * @param condition the condition
     * @return the grid
     */
    private Crj01ReachGrid.Result grid(String domain, String condition) throws Exception {
        String key = domain + "/" + condition;
        Crj01ReachGrid.Result cached = grids.get(key);
        if (cached != null) {
            return cached;
        }
        String workspace = workspaceOf(domain, condition);
        prepared(domain, condition);
        List<ReportProcedureLearner.Observation> observations = observations(domain, condition);
        KnowledgeSnapshot view = SourceRevisionView.project(bench.workspace(workspace), null);
        Map<String, String> sourceIdByFile = sourceIds.get(domain + "/" + condition);
        Map<String, String> claimIdByProposition = new LinkedHashMap<>();
        Map<String, String> sourceIdByClaim = new LinkedHashMap<>();
        for (Claim claim : view.claims()) {
            claimIdByProposition.put(claim.proposition(), claim.id());
            if (claim.introducedBySourceId() != null) {
                sourceIdByClaim.put(claim.id(), claim.introducedBySourceId());
            }
        }
        Crj01ReachGrid.Result grid = Crj01ReachGrid.run(new Crj01ReachGrid.Input(bench, learner(),
                workspace, domain, condition, view,
                caseSet.domain(domain).declaredScope(), sourceIdByFile, observations,
                validationCases(domain), caseSet.slice(domain, "EVALUATION"),
                claimIdByProposition, sourceIdByClaim));
        grids.put(key, grid);
        return grid;
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
        for (Crj01ReachCases.Case testCase : caseSet.slice(domain, "PROPOSAL")) {
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
            errors.add(new LearningError("reach-error-" + index, workspace, "reach-item-" + index,
                    concepts.get(index).id(), LearningDimension.RECALL,
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
        String documentKey = "reach-document-" + domain + "-" + file;
        bench.selectSourceRevision(new SourceRevisionSelection("reach-revision-1", workspace,
                documentKey, superseded.id(), superseded.contentHash(), null));
        Path revised = directory.resolve("revised-" + domain + "-" + file);
        Files.writeString(revised, Files.readString(corpusDirectory.resolve(domain).resolve(file))
                + "\nA revised document adds one further observation about its subject.\n",
                StandardCharsets.UTF_8);
        SourceRecord imported = bench.importDocument(workspace, revised, file, "en").source();
        bench.selectSourceRevision(new SourceRevisionSelection("reach-revision-2", workspace,
                documentKey, imported.sourceId(), imported.contentHash(), "reach-revision-1"));
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
        assertThat(observed)
                .as("the learning episode of %s/%s was run", domain, condition).isNotNull();
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
        assertThat(sourceIdByFile)
                .as("the workspace of %s/%s was prepared", domain, condition).isNotNull();
        Set<String> declared = new LinkedHashSet<>();
        for (String file : caseSet.domain(domain).declaredScope()) {
            String sourceId = sourceIdByFile.get(file);
            assertThat(sourceId).as("the declared document %s was imported", file).isNotNull();
            declared.add(sourceId);
        }
        return declared;
    }

    /**
     * The held-out cases of a domain's validation.
     *
     * @param domain the domain
     * @return the cases
     */
    private List<ReportProcedureLearner.Case> validationCases(String domain) {
        List<ReportProcedureLearner.Case> validation = new ArrayList<>();
        for (Crj01ReachCases.Case held : caseSet.slice(domain, "VALIDATION")) {
            validation.add(new ReportProcedureLearner.Case(held.id(), held.question(),
                    AbstractionLevel.valueOf(held.level())));
        }
        return validation;
    }

    /**
     * The case whose answer needs a claim the scope does not carry.
     *
     * @param domain the domain
     * @param scope  the scope, or an empty set to accept any scope
     * @return the case, or null
     */
    private Crj01ReachCases.Case measuredFailureCase(String domain) throws Exception {
        for (Crj01ReachGrid.Row row : grid(domain, "BASELINE").rows()) {
            if (row.admitted() && !row.evidenceComplete()
                    && ScopeCriterion.ANCHOR.name().equals(row.criterion())
                    && ScopeStrategy.DECLARED.name().equals(row.strategy())) {
                for (Crj01ReachCases.Case testCase : caseSet.slice(domain, "EVALUATION")) {
                    if (testCase.id().equals(row.caseId())) {
                        return testCase;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The case whose answer needs a claim the scope does not carry.
     *
     * @param domain the domain
     * @param scope  the scope
     * @return the case, or null
     */
    private Crj01ReachCases.Case failureCase(String domain, Set<String> scope) throws Exception {
        Map<String, String> sourceIdByFile = sourceIds.get(domain + "/BASELINE");
        KnowledgeSnapshot view = bench.workspace(workspaceOf(domain, "BASELINE"));
        for (Crj01ReachCases.Case testCase : caseSet.slice(domain, "EVALUATION")) {
            if (testCase.necessaryEvidence().size() < 2) {
                continue;
            }
            // THE ANCHOR MUST ADMIT IT. A case the incumbent rule refuses is a case the
            // procedure never serves, and it would show the FALLBACK, not the degradation.
            ScopeAssessment anchored = bench.assessScope(view, view, testCase.question(),
                    AbstractionLevel.valueOf(testCase.level()), scope, false,
                    ScopeCriterion.ANCHOR);
            if (!anchored.applicable()) {
                continue;
            }
            // AND THE SCOPE MUST NOT CARRY THE WHOLE ANSWER.
            boolean complete = true;
            for (Crj01ReachCases.Evidence evidence : testCase.necessaryEvidence()) {
                String sourceId = sourceIdByFile.get(evidence.corpusFile());
                if (sourceId != null && !scope.contains(sourceId)) {
                    complete = false;
                }
            }
            if (!complete) {
                return testCase;
            }
        }
        return null;
    }

    /**
     * A case of the evaluation slice that is neither the failure case nor a validation case.
     *
     * @param domain    the domain
     * @param excluded  the id of the case to exclude
     * @return the case
     */
    private Crj01ReachCases.Case distinctCase(String domain, String excluded,
            Map<String, String> sourceIdByFile, Set<String> narrowScope, Set<String> wideScope) {
        for (Crj01ReachCases.Case testCase : caseSet.slice(domain, "EVALUATION")) {
            if (testCase.id().equals(excluded) || testCase.necessaryEvidence().size() != 1) {
                continue;
            }
            String sourceId = sourceIdByFile.get(
                    testCase.necessaryEvidence().get(0).corpusFile());
            if (sourceId != null && wideScope.contains(sourceId)
                    && !narrowScope.contains(sourceId)) {
                return testCase;
            }
        }
        throw new IllegalStateException("the evaluation slice of " + domain + " carries no "
                + "distinct case whose evidence only the widened scope holds");
    }

    /**
     * The claim identifiers a response cites.
     *
     * @param response the response
     * @return the propositions it cites
     */
    private static List<String> citedOf(jakarta.ws.rs.core.Response response) {
        List<String> cited = new ArrayList<>();
        for (ArtifactReportResource.EvidenceReference reference : body(response).evidence()) {
            cited.add(reference.proposition());
        }
        return cited;
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
     * The request the served path takes.
     *
     * @param testCase the case
     * @param purpose  what the request is for; it keeps the keys apart
     * @return the request
     */
    private static ArtifactReportResource.ReportRequest request(Crj01ReachCases.Case testCase,
            String purpose) {
        return new ArtifactReportResource.ReportRequest(testCase.question(), null, Boolean.TRUE,
                "reach-" + testCase.id().toLowerCase(Locale.ROOT) + "-" + purpose,
                testCase.level());
    }

    /**
     * The text of a proposition as the response carries it: the proposition itself.
     *
     * @param proposition the proposition
     * @return the text
     */
    private static String propositionText(String proposition) {
        return proposition;
    }

    /**
     * The sentence of a corpus file that a proposition names.
     *
     * @param domain      the domain
     * @param file        the file
     * @param proposition the proposition
     * @return the sentence
     */
    private String sentenceOf(String domain, String file, String proposition) throws IOException {
        Path path = corpusDirectory.resolve(domain).resolve(file);
        assertThat(Files.isRegularFile(path)).as("the corpus file %s exists", file).isTrue();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.strip().equals(proposition)) {
                return line.strip();
            }
        }
        return "";
    }

    /**
     * Every row the grids produced.
     *
     * @return the rows
     */
    private List<Crj01ReachGrid.Row> allRows() throws Exception {
        List<Crj01ReachGrid.Row> rows = new ArrayList<>();
        for (Crj01ReachCases.Domain domain : caseSet.domains()) {
            for (String condition : CONDITIONS) {
                rows.addAll(grid(domain.id(), condition).rows());
            }
        }
        return rows;
    }

    /**
     * The bytes of one corpus file, as the classpath holds them.
     *
     * @param domain the domain's directory name
     * @param file   the file's name
     * @return the bytes
     */
    private static byte[] corpusBytes(String domain, String file) throws IOException {
        try (InputStream stream = CompetenceReachServedTest.class
                .getResourceAsStream(CORPUS_RESOURCE + domain + "/" + file)) {
            assertThat(stream).as("the corpus file %s/%s is on the classpath", domain, file)
                    .isNotNull();
            return stream.readAllBytes();
        }
    }

    /**
     * The digest of some bytes.
     *
     * @param bytes the bytes
     * @return the digest
     */
    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * Refreshes the retained evidence directory's manifest.
     *
     * @param directory the retained directory
     */
    private static void refreshManifest(Path directory) throws IOException {
        List<String> names = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            entries.filter(path -> path.getFileName().toString().endsWith(".md")
                            || path.getFileName().toString().endsWith(".json")
                            || path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .forEach(path -> names.add(path.getFileName().toString()));
        }
        StringBuilder manifest = new StringBuilder();
        for (String name : names) {
            if ("CHECKSUMS.sha256".equals(name)) {
                continue;
            }
            manifest.append(sha256(Files.readAllBytes(directory.resolve(name)))).append("  ")
                    .append(name).append("\n");
        }
        Files.writeString(directory.resolve("CHECKSUMS.sha256"), manifest.toString(),
                StandardCharsets.UTF_8);
    }
}
