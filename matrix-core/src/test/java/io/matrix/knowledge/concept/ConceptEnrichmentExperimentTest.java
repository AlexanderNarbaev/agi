package io.matrix.knowledge.concept;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.DocumentImporter;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C2A (Workstream B3+B5) — the concept-enrichment experiment.
 *
 * <p><b>What this test is.</b> It runs the three frozen candidates (H1 definition enrichment,
 * H2 relation expansion, H3 query-aware lexical expansion) against the 29-case evaluation, on
 * the workspaces the dataset's scopes define, and it writes the findings:
 * {@code docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md} and
 * {@code docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-RESULTS.json}.</p>
 *
 * <p><b>What it measures.</b> TWO definition rules in one pass over the cases: the rule of the
 * CURRENT freeze (v1.1, the tight rule: the name must be immediately followed by a definitional
 * copula) and the rule of the freeze it SUPERSEDES (v1.0, the loose rule: the name and a
 * definitional copula somewhere in the sentence). Both sets of rows are computed over the same
 * corpus and the same moment, so that the only difference between them is the rule. The
 * v1.0 measurement as published is preserved verbatim in §0 of the report and under
 * {@code v1_0Measurement} in the results file: it is the record of the FIRST measurement and is
 * not rewritten.</p>
 *
 * <p><b>What this test is NOT.</b> It does not change the production retrieval path: not one
 * line of {@code matrix-core/src/main/java} is touched. The production
 * {@link io.matrix.knowledge.KnowledgeRetriever} is reused as it is; the candidates are applied
 * to the snapshot before it sees it, or to the question before it is asked.</p>
 *
 * <p><b>The assertions.</b> The test asserts the invariants of the experiment, not the numbers
 * it hopes for: the frozen denominators, the monotonicity of recall@K, the internal consistency
 * of the metric code (the aggregate is recomputed from the per-case rows), the provenance of
 * every candidate (its span is really a verbatim span of the source document, at the offsets
 * that are recorded), the fact that the candidates change the concepts and NOT the claims, the
 * binding of the results to the freeze (the SHA-256 of the freeze file is in the results
 * file), and the DEFINITIONS of the metrics: the MRR is the reciprocal rank of the topmost
 * gold, the supported-answer accuracy honours the case's own gold condition, the top-1
 * metric looks at the top-ranked claim, and the H4 classification is per gold.</p>
 */
@DisplayName("C2A — the concept-enrichment experiment (H1, H2, H3) on the 29-case evaluation")
class ConceptEnrichmentExperimentTest {

    /** The freeze: the candidates and the metrics, fixed before the measurement. */
    private static final String FREEZE_PATH = "docs-v2/engineering/C2A-CANDIDATE-FREEZE.md";

    /** The findings. */
    private static final String REPORT_PATH =
            "docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md";

    /** The machine-readable metrics. */
    private static final String RESULTS_PATH =
            "docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-RESULTS.json";

    /** The branch and the commit the freeze was written at. */
    private static final String BRANCH = "feature/concept-to-action";
    private static final String COMMIT = "d15eb3340e99b31aebff552791b0f1fd6a90e095";

    /**
     * The SHA-256 of the freeze v1.0 — the freeze the FIRST measurement was produced against.
     *
     * <p>The freeze v1.1 supersedes it, and the first measurement is preserved with it: the
     * numbers below are the record of that measurement, and they are quoted with the digest
     * that binds them to the definitions they were produced under.</p>
     */
    private static final String V1_0_FREEZE_SHA256 =
            "14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4";

    /**
     * The v1.0 measurement, preserved: the numbers the FIRST measurement published.
     *
     * <p>Columns: row, passed, recall@1, recall@5, recall@10, recall@20, MRR, supported
     * answer, abstention, unsupported answer, mean non-gold cited, top-1 not gold, mean
     * retrieval (ms). These are the numbers of the metric definitions of v1.0 — including the
     * definitions the independent review found defective. They are kept as they were
     * published: a measurement is a record, not a number to be edited after the fact.</p>
     */
    private static final List<String[]> V1_0_METRICS = List.of(
            new String[] {"BASELINE-PATH", "18/29", "0.2024", "0.3214", "0.3690", "0.5952",
                    "0.4615", "0.3571", "1.0000", "0.6429", "38.2857", "9", "32"},
            new String[] {"BASELINE-RETRIEVER", "8/20", "0.2024", "0.3214", "0.3690", "0.5952",
                    "0.4615", "0.3571", "0.3333", "0.6429", "38.2857", "9", "10"},
            new String[] {"H1-definition-enrichment", "7/20", "0.2381", "0.3214", "0.3690", "0.5238",
                    "0.4987", "0.2857", "0.3333", "0.7143", "44.4286", "8", "12"},
            new String[] {"H2-relation-expansion", "8/20", "0.2024", "0.3214", "0.3690", "0.5952",
                    "0.4613", "0.3571", "0.3333", "0.6429", "39.0000", "9", "10"},
            new String[] {"H3-query-lexical-expansion", "5/20", "0.1429", "0.3214", "0.4048",
                    "0.5476", "0.3932", "0.1429", "0.3333", "0.8571", "17.6429", "11", "10"},
            new String[] {"H1+H2-definition-and-relation-expansion", "7/20", "0.2024", "0.2857",
                    "0.3690", "0.4643", "0.4404", "0.2857", "0.3333", "0.7143", "46.4286", "9",
                    "11"});

    /** The H4 classification the v1.0 measurement published, preserved. */
    private static final List<String[]> V1_0_CLASSIFICATIONS = List.of(
            new String[] {"BELOW_FLOOR", "7", "A-02, A-04, A-05, A-06, P-01, M-01, M-03"},
            new String[] {"NO_LEXICAL_MATCH", "1", "P-02"},
            new String[] {"FALSE_PREMISE_MISMATCH", "1", "F-02"},
            new String[] {"REVISION_NOT_APPLIED", "2", "R-04, R-05"});

    private static Path workDirectory;
    private static RetrievalExperiment.Result result;
    private static RetrievalExperiment.Dataset dataset;
    private static String freezeSha256;
    private static boolean ran = false;

    @BeforeAll
    static void runTheExperiment() throws Exception {
        if (ran) {
            return;
        }
        ran = true;
        workDirectory = Files.createTempDirectory("c2a-concept-enrichment-");
        Path database = workDirectory.resolve("knowledge.sqlite");
        try (RetrievalExperiment experiment = new RetrievalExperiment(database)) {
            dataset = experiment.dataset();
            result = experiment.run();
        }
        freezeSha256 = sha256(RepositoryPaths.root().resolve(FREEZE_PATH));
        writeResults();
        writeReport();
    }

    /**
     * Regenerates the development artefacts when they do not carry the current freeze.
     *
     * <p>The held-out evaluation reads the development results for its comparison table, and it
     * must read the results of the SAME freeze. The order between the two test classes inside
     * one suite is not guaranteed, so the held-out run regenerates the development artefacts
     * when they are stale — with the same code, against the same freeze.</p>
     *
     * @param expectedFreezeSha256 the digest the artefacts must carry
     * @throws Exception when the experiment cannot be run
     */
    static void regenerateIfStale(String expectedFreezeSha256) throws Exception {
        Path results = RepositoryPaths.root().resolve(RESULTS_PATH);
        if (Files.exists(results)) {
            String written = Files.readString(results, StandardCharsets.UTF_8);
            if (written.contains(expectedFreezeSha256)) {
                return;
            }
        }
        runTheExperiment();
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (workDirectory != null && Files.exists(workDirectory)) {
            try (var paths = Files.walk(workDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // the temporary directory is below the operating system's own care
                    }
                });
            }
        }
    }

    /** The run of the tight rule — the rule of the current freeze (v1.1). */
    private static RetrievalExperiment.RuleRun tight() {
        return runOf(SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1);
    }

    /** The run of the loose rule — the rule of the freeze v1.1 supersedes. */
    private static RetrievalExperiment.RuleRun loose() {
        return runOf(SourceGroundedEnrichment.DefinitionRule.LOOSE_V1_0);
    }

    private static RetrievalExperiment.RuleRun runOf(SourceGroundedEnrichment.DefinitionRule rule) {
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            if (run.rule().equals(rule.name())) {
                return run;
            }
        }
        throw new IllegalStateException("the run of the rule " + rule + " is missing");
    }

    // ── the invariants of the experiment ──────────────────────────────────────

    @Test
    @DisplayName("the dataset is the 29 cases of the versioned evaluation")
    void theDatasetIsTheFrozenOne() {
        assertEquals(29, dataset.cases().size(), "the dataset must hold the 29 cases");
        assertEquals(6, dataset.scopes().size(), "the dataset must define the six scopes");
        assertEquals("w50-plate-tectonics", dataset.dataset());
        assertEquals("1.1", dataset.version());
        int prepared = 0;
        for (RetrievalExperiment.ScopeSpec scope : dataset.scopes().values()) {
            prepared += scope.cases().size();
        }
        assertEquals(29, prepared, "every case belongs to exactly one scope");
        assertEquals(14, result.answerableCases(), "the frozen denominator of the answerable cases");
        assertEquals(6, result.unanswerableCases(), "the frozen denominator of the unanswerable cases");
    }

    @Test
    @DisplayName("every scope of the dataset was prepared with the production methods")
    void everyScopeWasPrepared() {
        for (RetrievalExperiment.ScopeSpec scope : dataset.scopes().values()) {
            RetrievalExperiment.ScopeReport report = null;
            for (RetrievalExperiment.ScopeReport candidate : result.scopes()) {
                if (candidate.scope().equals(scope.id())) {
                    report = candidate;
                }
            }
            assertNotNull(report, "the scope " + scope.id() + " was not prepared");
            assertTrue(report.preparations() > 0, "the scope " + scope.id() + " was never prepared");
            assertTrue(report.claimsInWorkspace() > 0,
                    "the workspace of the scope " + scope.id() + " holds no claims");
            assertEquals(scope.workspace(), report.workspace());
        }
        // THE SEED SCOPE: the two seed documents, imported by the production method.
        RetrievalExperiment.ScopeReport seed = null;
        for (RetrievalExperiment.ScopeReport candidate : result.scopes()) {
            if (candidate.scope().equals("S1")) {
                seed = candidate;
            }
        }
        assertNotNull(seed);
        assertEquals("plate-tectonics-eval-seed", seed.workspace());
        assertTrue(seed.claimsInWorkspace() >= 348,
                "the seed corpus must yield at least the 348 claims the baseline measured, got "
                        + seed.claimsInWorkspace());
        assertTrue(seed.conceptsInWorkspace() >= 326,
                "the seed corpus must yield at least the 326 concepts the baseline measured, got "
                        + seed.conceptsInWorkspace());
    }

    @Test
    @DisplayName("every gold proposition of an answerable case is a claim of its workspace")
    void everyGoldPropositionIsInTheCorpus() {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows(tight());
        int checked = 0;
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
                if (row.goldPropositions().isEmpty()) {
                    continue;
                }
                assertEquals(row.goldPropositions().size(), row.goldInWorkspace(),
                        "the gold of the case " + row.caseId() + " is not a claim of the "
                                + "workspace of its scope: " + row.goldPropositions());
                checked += row.goldPropositions().size();
            }
        }
        assertTrue(checked >= 27, "the answerable cases must carry their gold, checked " + checked);
    }

    @Test
    @DisplayName("the metrics are the frozen ones, and they are internally consistent")
    void theMetricsAreConsistent() {
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                    : run.metrics().entrySet()) {
                RetrievalExperiment.CandidateMetrics metrics = entry.getValue();
                assertTrue(metrics.recallAt1() <= metrics.recallAt5(),
                        entry.getKey() + ": recall@1 must not exceed recall@5");
                assertTrue(metrics.recallAt5() <= metrics.recallAt10(),
                        entry.getKey() + ": recall@5 must not exceed recall@10");
                assertTrue(metrics.recallAt10() <= metrics.recallAt20(),
                        entry.getKey() + ": recall@10 must not exceed recall@20");
                assertTrue(metrics.mrr() >= 0.0 && metrics.mrr() <= 1.0,
                        entry.getKey() + ": the MRR must lie in [0,1]");
                assertTrue(metrics.supportedAnswerAccuracy() >= 0.0
                        && metrics.supportedAnswerAccuracy() <= 1.0);
                assertTrue(metrics.abstentionAccuracy() >= 0.0 && metrics.abstentionAccuracy() <= 1.0);
                assertTrue(metrics.unsupportedAnswerRate() >= 0.0
                        && metrics.unsupportedAnswerRate() <= 1.0);
                assertEquals(14, metrics.measuredCases(),
                        entry.getKey() + ": the answerable denominator is frozen at 14");
                assertEquals(20, metrics.askCases(),
                        entry.getKey() + ": the ask denominator is the 14 answerable and the 6 "
                                + "unanswerable cases");
                // THE AGGREGATE IS RECOMPUTED FROM THE PER-CASE ROWS.
                double recomputed = 0.0;
                int counted = 0;
                for (RetrievalExperiment.CaseResult row : run.cases()) {
                    if (!row.candidate().equals(entry.getKey())) {
                        continue;
                    }
                    if (!RetrievalExperiment.isAnswerable(row.caseId(), dataset)) {
                        continue;
                    }
                    recomputed += row.recallAt5();
                    counted++;
                }
                assertEquals(counted, 14);
                assertEquals(Math.round(recomputed / counted * 10000.0) / 10000.0,
                        Math.round(metrics.recallAt5() * 10000.0) / 10000.0,
                        entry.getKey() + ": the aggregate must be the mean of the per-case rows");
            }
        }
    }

    @Test
    @DisplayName("the MRR is the reciprocal rank of the TOPMOST gold, and the misses count")
    void theMrrIsTheTopmostGold() {
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            for (RetrievalExperiment.CaseResult row : run.cases()) {
                if (!RetrievalExperiment.isAnswerable(row.caseId(), dataset)) {
                    continue;
                }
                int topmost = Integer.MAX_VALUE;
                for (Integer rank : row.goldRanks()) {
                    if (rank > 0 && rank < topmost) {
                        topmost = rank;
                    }
                }
                double expected = topmost == Integer.MAX_VALUE ? 0.0 : 1.0 / topmost;
                assertEquals(expected, row.mrr(), 1e-9,
                        "the MRR of " + row.caseId() + " under " + row.candidate() + " (rule "
                                + run.rule() + ") must be the reciprocal rank of the topmost gold");
            }
        }
    }

    @Test
    @DisplayName("the supported answer honours the case's own gold condition")
    void theSupportedAnswerHonoursTheCaseCondition() {
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                    : run.metrics().entrySet()) {
                long supported = 0;
                long unsupported = 0;
                for (RetrievalExperiment.CaseResult row : run.cases()) {
                    if (!row.candidate().equals(entry.getKey())
                            || !RetrievalExperiment.isAnswerable(row.caseId(), dataset)) {
                        continue;
                    }
                    if (!row.answered()) {
                        continue;
                    }
                    if (row.goldConditionMet()) {
                        supported++;
                    } else {
                        unsupported++;
                    }
                }
                assertEquals(supported, entry.getValue().supportedCount(),
                        entry.getKey() + ": the supported count must be the number of the answered "
                                + "cases that met their own gold condition");
                assertEquals(unsupported, entry.getValue().unsupportedCount(),
                        entry.getKey() + ": the unsupported count must be the number of the "
                                + "answered cases that did not");
                assertEquals(14, entry.getValue().supportedCount()
                        + entry.getValue().unsupportedCount(),
                        entry.getKey() + ": every answerable case is answered in this run");
            }
        }
        // THE ANY-CASE: K-02 names alternatives, and it PASSES.
        RetrievalExperiment.CaseResult anyCase = null;
        for (RetrievalExperiment.CaseResult row : tight().cases()) {
            if (row.caseId().equals("K-02")
                    && row.candidate().equals(RetrievalExperiment.BASELINE_PATH)) {
                anyCase = row;
            }
        }
        assertNotNull(anyCase, "the case K-02 must have a baseline row");
        assertEquals("ANY", anyCase.goldCondition(),
                "K-02 names alternative propositions, so its condition is ANY");
        assertTrue(anyCase.goldConditionMet(),
                "K-02 must meet its own condition — it cites one of its alternatives");
    }

    @Test
    @DisplayName("the top-1 metric looks at the top-ranked claim, not at the first gold")
    void theTopRankMetricIsTheTopRankedClaim() {
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            int counted = 0;
            for (RetrievalExperiment.CaseResult row : run.cases()) {
                if (!row.candidate().equals(RetrievalExperiment.BASELINE_RETRIEVER)
                        || !RetrievalExperiment.isAnswerable(row.caseId(), dataset)) {
                    continue;
                }
                if (row.topRankIsGold() == null || row.topRankIsGold()) {
                    continue;
                }
                counted++;
            }
            assertEquals(counted, run.metrics().get(RetrievalExperiment.BASELINE_RETRIEVER)
                    .casesWhoseTopRankIsNotGold(),
                    "the top-1 metric must count the cases whose top-ranked claim is not a gold "
                            + "claim (rule " + run.rule() + ")");
        }
    }

    @Test
    @DisplayName("the baseline row judges all 29 cases, and the official reference is recorded")
    void theBaselineJudgesEveryCase() {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows(tight());
        assertEquals(29, baseline.size(), "every case must have a baseline row");
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
                assertNotNull(row.problems(), "every baseline row must carry its problems");
                assertNotNull(row.observed(), "every baseline row must carry what was observed");
                if (row.passed()) {
                    assertTrue(row.problems().isEmpty(),
                            "a passing case must carry no problem: " + row.caseId());
                    assertTrue(row.classification().isBlank(),
                            "a PASSING case must carry no classification: " + row.caseId());
                } else {
                    assertFalse(row.problems().isEmpty(),
                            "a failing case must name at least one problem: " + row.caseId());
                }
            }
        }
        assertEquals(11, RetrievalExperiment.officialFailures().size(),
                "the official reference names eleven failures");
        assertTrue(result.baselineFailures().size() <= 29);
    }

    @Test
    @DisplayName("every baseline failure carries a classification and its evidence")
    void everyFailureIsClassified() {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows(tight());
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
                if (row.passed()) {
                    continue;
                }
                assertFalse(row.classification().isBlank(),
                        "the failure " + row.caseId() + " carries no classification");
                assertFalse(row.classificationEvidence().isBlank(),
                        "the failure " + row.caseId() + " carries no evidence");
                if (!row.goldPropositions().isEmpty()) {
                    assertFalse(row.goldClassifications().isEmpty(),
                            "the failure " + row.caseId() + " carries no per-gold classification");
                    assertEquals(row.goldPropositions().size(),
                            row.goldClassifications().size(),
                            "every gold of the failure " + row.caseId() + " must carry a code");
                }
            }
        }
    }

    @Test
    @DisplayName("the H4 classification is per gold, and the mixed cases are named")
    void theH4ClassificationIsPerGold() {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows(tight());
        int mixed = 0;
        int pureBelowFloor = 0;
        int pureNoLexicalMatch = 0;
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
                if (row.passed()) {
                    continue;
                }
                List<String> codes = new ArrayList<>(row.goldClassifications());
                Set<String> defects = new LinkedHashSet<>();
                for (String code : codes) {
                    if (!RetrievalExperiment.GOLD_REACHED.equals(code)) {
                        defects.add(code);
                    }
                }
                if (defects.size() > 1) {
                    assertEquals(RetrievalExperiment.MIXED_GOLD_CODES, row.classification(),
                            "the case " + row.caseId() + " carries different codes for its golds "
                                    + "and must be MIXED_GOLD_CODES");
                    mixed++;
                } else if (defects.size() == 1) {
                    assertEquals(defects.iterator().next(), row.classification(),
                            "the case " + row.caseId() + " must carry the code its golds agree on");
                    if ("BELOW_FLOOR".equals(row.classification())) {
                        pureBelowFloor++;
                    }
                    if ("NO_LEXICAL_MATCH".equals(row.classification())) {
                        pureNoLexicalMatch++;
                    }
                }
            }
        }
        assertEquals(5, mixed, "five baseline failures carry different codes for their golds");
        assertEquals(2, pureBelowFloor, "two baseline failures are purely BELOW_FLOOR");
        assertEquals(1, pureNoLexicalMatch,
                "one baseline failure is purely NO_LEXICAL_MATCH — the same defect P-01 carries "
                        + "on two of its three golds");
    }

    @Test
    @DisplayName("the extraction is grounded: every candidate carries a verbatim, located span")
    void theExtractionIsGrounded() throws Exception {
        Map<String, Object> summary = tight().extractionByScope().get("A-01");
        assertNotNull(summary, "the extraction of the first answer case must be recorded");
        assertNotNull(summary.get("definitions"));
        assertNotNull(summary.get("relations"));
        assertTrue((int) summary.get("concepts") > 0);
        assertEquals((int) summary.get("concepts"),
                (int) summary.get("definitions") + (int) summary.get("conceptsWithoutDefinition"),
                "every concept must have a definition or be recorded as having none");
    }

    @Test
    @DisplayName("the tight rule keeps fewer definitions than the loose one")
    void theTightRuleIsTighter() {
        Map<String, Object> tightSummary = tight().extractionByScope().get("A-01");
        Map<String, Object> looseSummary = loose().extractionByScope().get("A-01");
        int tightDefinitions = (int) tightSummary.get("definitions");
        int looseDefinitions = (int) looseSummary.get("definitions");
        assertTrue(tightDefinitions < looseDefinitions,
                "the tight rule must keep fewer definitions than the loose one (" + tightDefinitions
                        + " against " + looseDefinitions + ")");
        // EVERY definition the tight rule kept satisfies the adjacency AND the length cap.
        for (SourceGroundedEnrichment.DefinitionCandidate definition : definitionsOf(tight())) {
            assertTrue(SourceGroundedEnrichment.holdsCopulaAfterName(definition.definition(),
                    definition.conceptName()),
                    "the tight rule kept a definition whose name is not immediately followed by "
                            + "a definitional copula: " + definition.conceptName());
            assertTrue(definition.definition().length()
                    <= SourceGroundedEnrichment.MAX_DEFINITION_LENGTH,
                    "the tight rule kept a definition longer than the cap");
        }
        // AND the loose rule kept definitions the tight rule rejects.
        int adjacent = 0;
        for (SourceGroundedEnrichment.DefinitionCandidate definition : definitionsOf(loose())) {
            if (SourceGroundedEnrichment.holdsCopulaAfterName(definition.definition(),
                    definition.conceptName())) {
                adjacent++;
            }
        }
        assertTrue(adjacent < looseDefinitions,
                "some of the loose definitions must fail the adjacency of the tight rule ("
                        + adjacent + " of " + looseDefinitions + " are adjacent)");
    }

    @Test
    @DisplayName("every definition and every relation is a verbatim span of its source document")
    void everySpanIsVerbatim() {
        RetrievalExperiment.ExtractionRecord record = null;
        for (RetrievalExperiment.ExtractionRecord candidate : tight().extractions()) {
            if (candidate.caseId().equals("A-01")) {
                record = candidate;
            }
        }
        assertNotNull(record, "the extraction of the seed scope must be kept");
        int definitions = 0;
        for (SourceGroundedEnrichment.DefinitionCandidate definition
                : record.extraction().definitions()) {
            assertTrue(grounded(definition.sourceSha256(), definition.startOffset(),
                    definition.endOffset(), definition.definition(), definition.offsetPrecision()),
                    "the definition of " + definition.conceptName() + " is not the span its "
                            + "offsets name");
            assertEquals(SourceGroundedEnrichment.DEFINITION_METHOD_V1_1,
                    definition.extractionMethod());
            assertEquals(SourceGroundedEnrichment.OFFSET_UNIT, definition.offsetUnit());
            assertFalse(definition.sourceId().isBlank());
            assertTrue(definition.definition().length()
                    <= SourceGroundedEnrichment.MAX_DEFINITION_LENGTH,
                    "the tight rule must not keep a definition longer than the cap");
            definitions++;
        }
        assertTrue(definitions > 0, "the seed scope must hold definition candidates");
        int relations = 0;
        for (SourceGroundedEnrichment.RelationCandidate relation : record.extraction().relations()) {
            assertTrue(grounded(relation.sourceSha256(), relation.startOffset(),
                    relation.endOffset(), relation.span(), relation.offsetPrecision()),
                    "the relation " + relation.direction() + " is not the span its offsets name");
            assertEquals(SourceGroundedEnrichment.RELATION_METHOD, relation.extractionMethod());
            assertNotNull(relation.type());
            assertTrue(relation.direction().contains(relation.type().name()));
            assertFalse(relation.fromConceptId().equals(relation.toConceptId()));
            relations++;
        }
        assertTrue(relations > 0, "the seed scope must hold relation candidates");
    }

    /**
     * Whether a recorded span really holds the recorded text.
     *
     * <p>An `EXACT` span must hold the text character for character. A `SEGMENT` span is the
     * span of the segment the sentence was extracted from: the extractor joined the lines of
     * the segment, so the sentence is not a literal substring of the extracted text, and the
     * span holds it once the whitespace is normalised.</p>
     */
    private static boolean grounded(String sha256, int start, int end, String text,
            String precision) {
        if ("EXACT".equals(precision)) {
            return SourceGroundedEnrichment.spanMatches(sha256, start, end, text);
        }
        return SourceGroundedEnrichment.spanHolds(sha256, start, end, text);
    }

    @Test
    @DisplayName("the candidates change the concepts and never the claims")
    void theCandidatesChangeOnlyTheConcepts() {
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            Map<String, List<RetrievalExperiment.CaseResult>> byCase = new LinkedHashMap<>();
            for (RetrievalExperiment.CaseResult row : run.cases()) {
                byCase.computeIfAbsent(row.caseId(), key -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<String, List<RetrievalExperiment.CaseResult>> entry : byCase.entrySet()) {
                List<RetrievalExperiment.CaseResult> rows = entry.getValue();
                if (rows.size() < 2) {
                    continue;
                }
                int claims = rows.get(0).workspaceClaims();
                for (RetrievalExperiment.CaseResult row : rows) {
                    assertEquals(claims, row.workspaceClaims(),
                            "the case " + entry.getKey() + " must run every candidate on the same "
                                    + "corpus");
                }
            }
        }
    }

    @Test
    @DisplayName("every candidate reports the scale of what it touched")
    void everyCandidateReportsItsScale() {
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            Map<String, RetrievalExperiment.CandidateScale> scale = run.scale().get("A-01");
            assertNotNull(scale, "the scale of the first answer case must be recorded");
            RetrievalExperiment.CandidateScale h2 = scale.get(EnrichmentCandidates.H2);
            assertNotNull(h2, "H2 must report its scale");
            assertTrue(h2.conceptsTouched() > 0, "H2 must touch at least one concept");
            assertTrue(h2.conceptsTouched() < h2.conceptsInWorkspace(),
                    "H2 must touch fewer concepts than the workspace holds");
            RetrievalExperiment.CandidateScale h3 = scale.get(EnrichmentCandidates.H3);
            assertNotNull(h3, "H3 must report its scale");
            assertEquals(0, h3.conceptsTouched(), "H3 changes no concept");
            assertEquals(0, h3.claimsTouched(), "H3 changes no claim");
        }
    }

    @Test
    @DisplayName("the results are bound to the freeze by its SHA-256")
    void theResultsAreBoundToTheFreeze() throws Exception {
        Path results = RepositoryPaths.root().resolve(RESULTS_PATH);
        assertTrue(Files.exists(results), "the results file must be written");
        String written = Files.readString(results, StandardCharsets.UTF_8);
        assertTrue(written.contains(freezeSha256),
                "the results file must carry the SHA-256 of the freeze it was produced against");
        assertTrue(written.contains(FREEZE_PATH));
        assertTrue(written.contains("frozen"), "the results file must state that the candidates "
                + "were frozen before the measurement");
        assertTrue(written.contains(V1_0_FREEZE_SHA256),
                "the results file must preserve the digest of the freeze v1.0 the first "
                        + "measurement was produced against");
        Path report = RepositoryPaths.root().resolve(REPORT_PATH);
        assertTrue(Files.exists(report), "the report must be written");
        assertTrue(Files.size(report) > 2000, "the report must hold the findings");
        String text = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(text.contains(freezeSha256),
                "the report must carry the SHA-256 of the freeze too");
        assertTrue(text.contains(V1_0_FREEZE_SHA256),
                "the report must preserve the digest of the freeze v1.0");
    }

    @Test
    @DisplayName("the corpus the extraction reads is the corpus the manifest names")
    void theCorpusIsTheManifestedOne() throws Exception {
        ClasspathCorpus corpus = ClasspathCorpus.extract(
                io.matrix.knowledge.acquisition.AcquisitionCycle.SEED_CORPUS);
        assertEquals(2, corpus.entries().size(), "the seed corpus holds two documents");
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            byte[] bytes = Files.readAllBytes(entry.path());
            assertEquals(entry.sha256(), DocumentImporter.sha256(bytes),
                    "the bytes of " + entry.file() + " must match the manifest");
        }
    }

    // ── the report ────────────────────────────────────────────────────────────

    private static Map<String, List<RetrievalExperiment.CaseResult>> baselineRows(
            RetrievalExperiment.RuleRun run) {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = new LinkedHashMap<>();
        for (RetrievalExperiment.CaseResult row : run.cases()) {
            if (row.candidate().equals(RetrievalExperiment.BASELINE_PATH)) {
                baseline.computeIfAbsent(row.caseId(), key -> new ArrayList<>()).add(row);
            }
        }
        return baseline;
    }

    /** The definitions of the seed scope of one run. */
    private static List<SourceGroundedEnrichment.DefinitionCandidate> definitionsOf(
            RetrievalExperiment.RuleRun run) {
        for (RetrievalExperiment.ExtractionRecord record : run.extractions()) {
            if (record.caseId().equals("A-01")) {
                return record.extraction().definitions();
            }
        }
        return List.of();
    }

    /** The row of a candidate for a case. */
    private static RetrievalExperiment.CaseResult rowOf(RetrievalExperiment.RuleRun run,
            String caseId, String candidate) {
        for (RetrievalExperiment.CaseResult row : run.cases()) {
            if (row.caseId().equals(caseId) && row.candidate().equals(candidate)) {
                return row;
            }
        }
        return null;
    }

    private static void writeResults() throws IOException {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("record", "C2A-CONCEPT-ENRICHMENT-RESULTS");
        json.put("date", java.time.LocalDate.now().toString());
        json.put("dataset", dataset.dataset());
        json.put("datasetVersion", dataset.version());
        json.put("candidates", List.of(RetrievalExperiment.BASELINE_PATH,
                RetrievalExperiment.BASELINE_RETRIEVER, EnrichmentCandidates.H1,
                EnrichmentCandidates.H2, EnrichmentCandidates.H3, EnrichmentCandidates.H1_AND_H2));
        json.put("floor", RetrievalExperiment.BASELINE_FLOOR);
        json.put("rankingFloor", RetrievalExperiment.RANKING_FLOOR);
        json.put("answerableCases", result.answerableCases());
        json.put("unanswerableCases", result.unanswerableCases());
        json.put("askCases", result.answerableCases() + result.unanswerableCases());

        Map<String, Object> freeze = new LinkedHashMap<>();
        freeze.put("path", FREEZE_PATH);
        freeze.put("sha256", freezeSha256);
        freeze.put("branch", BRANCH);
        freeze.put("commit", COMMIT);
        freeze.put("statement", "the results below were produced against the candidates frozen in "
                + FREEZE_PATH + " (sha256 " + freezeSha256 + "); the candidates were frozen before "
                + "any measurement was taken");
        json.put("freeze", freeze);

        Map<String, Object> supersedes = new LinkedHashMap<>();
        supersedes.put("version", "v1.0");
        supersedes.put("sha256", V1_0_FREEZE_SHA256);
        supersedes.put("supersededBy", freezeSha256);
        supersedes.put("reason", "the v1.0 definition rule only asked for the concept's name and "
                + "a definitional copula somewhere in the sentence, and the first measurement "
                + "showed that most of the definitions it produced are not definitions; the v1.1 "
                + "rule requires the name to be IMMEDIATELY followed by the copula and caps the "
                + "length of a definition");
        supersedes.put("firstMeasurementPreserved", true);
        json.put("freezeSupersedes", supersedes);

        // THE FIRST MEASUREMENT, PRESERVED (the record of the v1.0 run).
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("freezeVersion", "v1.0");
        first.put("freezeSha256", V1_0_FREEZE_SHA256);
        first.put("note", "the numbers of the FIRST measurement, as they were published; they "
                + "were produced under the metric definitions of the freeze v1.0, which the "
                + "independent review found defective in three places (the MRR, the supported "
                + "and unsupported answers, and the top-1 metric)");
        List<Map<String, Object>> firstMetrics = new ArrayList<>();
        for (String[] row : V1_0_METRICS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("candidate", row[0]);
            item.put("passed", row[1]);
            item.put("recallAt1", Double.parseDouble(row[2]));
            item.put("recallAt5", Double.parseDouble(row[3]));
            item.put("recallAt10", Double.parseDouble(row[4]));
            item.put("recallAt20", Double.parseDouble(row[5]));
            item.put("mrr", Double.parseDouble(row[6]));
            item.put("supportedAnswerAccuracy", Double.parseDouble(row[7]));
            item.put("abstentionAccuracy", Double.parseDouble(row[8]));
            item.put("unsupportedAnswerRate", Double.parseDouble(row[9]));
            item.put("meanNonGoldClaimsCited", Double.parseDouble(row[10]));
            item.put("casesWhoseTopRankIsNotGold", Integer.parseInt(row[11]));
            item.put("meanRetrievalMillis", Long.parseLong(row[12]));
            firstMetrics.add(item);
        }
        first.put("metrics", firstMetrics);
        List<Map<String, Object>> firstClassifications = new ArrayList<>();
        for (String[] row : V1_0_CLASSIFICATIONS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("classification", row[0]);
            item.put("count", Integer.parseInt(row[1]));
            item.put("cases", List.of(row[2].split(", ")));
            firstClassifications.add(item);
        }
        first.put("h4Classifications", firstClassifications);
        json.put("v1_0Measurement", first);

        Map<String, Object> reproduction = new LinkedHashMap<>();
        reproduction.put("officialFailures", RetrievalExperiment.officialFailures());
        reproduction.put("harnessFailures", result.baselineFailures());
        Set<String> official = new LinkedHashSet<>(RetrievalExperiment.officialFailures());
        Set<String> harness = new LinkedHashSet<>(result.baselineFailures());
        reproduction.put("identical", official.equals(harness));
        reproduction.put("onlyInOfficial", difference(official, harness));
        reproduction.put("onlyInHarness", difference(harness, official));
        json.put("baselineReproduction", reproduction);

        // THE MEASUREMENT, PER RULE.
        Map<String, Object> rules = new LinkedHashMap<>();
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("rule", run.rule());
            rule.put("definitionRule", run.rule().equals(
                    SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1.name())
                    ? SourceGroundedEnrichment.DEFINITION_METHOD_V1_1
                    : SourceGroundedEnrichment.DEFINITION_METHOD);
            rule.put("maxDefinitionLength", run.rule().equals(
                    SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1.name())
                    ? SourceGroundedEnrichment.MAX_DEFINITION_LENGTH : null);
            rule.put("extractionMillis", run.extractionMillis());
            rule.put("retainedExtractionBytes", run.retainedExtractionBytes());
            Map<String, Object> metrics = new LinkedHashMap<>();
            for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                    : run.metrics().entrySet()) {
                RetrievalExperiment.CandidateMetrics value = entry.getValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("passed", value.passed());
                row.put("total", value.total());
                row.put("failedCases", value.failedCases());
                row.put("recallAt1", value.recallAt1());
                row.put("recallAt5", value.recallAt5());
                row.put("recallAt10", value.recallAt10());
                row.put("recallAt20", value.recallAt20());
                row.put("mrr", value.mrr());
                row.put("microRecallAt1", value.microRecallAt1());
                row.put("microRecallAt5", value.microRecallAt5());
                row.put("microRecallAt10", value.microRecallAt10());
                row.put("microRecallAt20", value.microRecallAt20());
                row.put("supportedAnswerAccuracy", value.supportedAnswerAccuracy());
                row.put("supportedCount", value.supportedCount());
                row.put("abstentionAccuracy", value.abstentionAccuracy());
                row.put("unsupportedAnswerRate", value.unsupportedAnswerRate());
                row.put("unsupportedCount", value.unsupportedCount());
                row.put("answeredWithZeroGoldCited", value.zeroGoldCitedCount());
                row.put("unsupportedOverAllAskCases", value.unsupportedOverAllAsk());
                row.put("askCases", value.askCases());
                row.put("goldAvailability", value.goldAvailability());
                row.put("meanNonGoldClaimsCited", value.meanNonGoldCited());
                row.put("casesWhoseTopRankIsNotGold", value.casesWhoseTopRankIsNotGold());
                row.put("topRankNotGoldDenominator", value.topRankNotGoldDenominator());
                row.put("meanRetrievalMillis", value.meanRetrievalMillis());
                row.put("totalRetrievalMillis", value.totalRetrievalMillis());
                row.put("overheadMillisPerCase", value.overheadMillisPerCase());
                row.put("measuredCases", value.measuredCases());
                metrics.put(entry.getKey(), row);
            }
            rule.put("metrics", metrics);
            rule.put("extractionByCase", run.extractionByScope());
            Map<String, Object> scale = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, RetrievalExperiment.CandidateScale>> entry
                    : run.scale().entrySet()) {
                Map<String, Object> byCandidate = new LinkedHashMap<>();
                for (Map.Entry<String, RetrievalExperiment.CandidateScale> candidate
                        : entry.getValue().entrySet()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("conceptsTouched", candidate.getValue().conceptsTouched());
                    row.put("claimsTouched", candidate.getValue().claimsTouched());
                    row.put("definitionsApplied", candidate.getValue().definitionsApplied());
                    row.put("relatedTextsApplied", candidate.getValue().relatedTextsApplied());
                    row.put("relatedConceptNamesApplied",
                            candidate.getValue().relatedConceptNamesApplied());
                    row.put("questionTermsAdded", candidate.getValue().questionTermsAdded());
                    row.put("conceptsInWorkspace", candidate.getValue().conceptsInWorkspace());
                    row.put("claimsInWorkspace", candidate.getValue().claimsInWorkspace());
                    byCandidate.put(candidate.getKey(), row);
                }
                scale.put(entry.getKey(), byCandidate);
            }
            rule.put("scaleByCase", scale);
            List<Map<String, Object>> shifts = new ArrayList<>();
            for (RetrievalExperiment.CoverageShift shift : run.coverageShifts()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("case", shift.caseId());
                row.put("candidate", shift.candidate());
                row.put("proposition", shift.proposition());
                row.put("baselineCoverageFloor", shift.baselineFloor());
                row.put("baselineCoverageBracket",
                        RetrievalExperiment.coverageInterval(shift.baselineFloor()));
                row.put("candidateCoverageFloor", shift.candidateFloor());
                row.put("candidateCoverageBracket",
                        RetrievalExperiment.coverageInterval(shift.candidateFloor()));
                shifts.add(row);
            }
            rule.put("coverageShifts", shifts);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (RetrievalExperiment.CaseResult row : run.cases()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("case", row.caseId());
                item.put("category", row.category());
                item.put("scope", row.scope());
                item.put("workspace", row.workspace());
                item.put("candidate", row.candidate());
                item.put("verdict", row.passed() ? "PASS" : "FAIL");
                item.put("problems", row.problems());
                item.put("observed", row.observed());
                item.put("answered", row.answered());
                item.put("citedClaims", row.citedClaims());
                item.put("citedSources", row.citedSources());
                item.put("goldPropositions", row.goldPropositions());
                item.put("goldCited", row.goldCited());
                item.put("goldInWorkspace", row.goldInWorkspace());
                item.put("goldCondition", row.goldCondition());
                item.put("goldConditionMet", row.goldConditionMet());
                item.put("goldRanks", row.goldRanks());
                item.put("topRankIsGold", row.topRankIsGold());
                item.put("recallAt1", row.recallAt1());
                item.put("recallAt5", row.recallAt5());
                item.put("recallAt10", row.recallAt10());
                item.put("recallAt20", row.recallAt20());
                item.put("mrr", row.mrr());
                item.put("retrievalMillis", row.retrievalMillis());
                item.put("classification", row.classification());
                item.put("goldClassifications", row.goldClassifications());
                item.put("classificationEvidence", row.classificationEvidence());
                item.put("abstentionReason", row.abstentionReason());
                item.put("workspaceClaims", row.workspaceClaims());
                rows.add(item);
            }
            rule.put("cases", rows);
            rules.put(run.rule(), rule);
        }
        json.put("rules", rules);

        List<Map<String, Object>> scopes = new ArrayList<>();
        for (RetrievalExperiment.ScopeReport report : result.scopes()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scope", report.scope());
            row.put("workspace", report.workspace());
            row.put("preparations", report.preparations());
            row.put("documentsAcquired", report.documentsAcquired());
            row.put("claimsAdded", report.claimsAdded());
            row.put("claimsInWorkspace", report.claimsInWorkspace());
            row.put("conceptsInWorkspace", report.conceptsInWorkspace());
            row.put("description", report.note());
            scopes.add(row);
        }
        json.put("scopes", scopes);
        json.put("preparationMillis", result.preparationMillis());

        // THE FULL EXTRACTION OF THE SEED SCOPE, with the provenance of every candidate.
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            RetrievalExperiment.ExtractionRecord seed = null;
            for (RetrievalExperiment.ExtractionRecord candidate : run.extractions()) {
                if (candidate.caseId().equals("A-01")) {
                    seed = candidate;
                }
            }
            if (seed == null) {
                continue;
            }
            List<Map<String, Object>> definitions = new ArrayList<>();
            for (SourceGroundedEnrichment.DefinitionCandidate definition
                    : seed.extraction().definitions()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("conceptId", definition.conceptId());
                row.put("conceptName", definition.conceptName());
                row.put("definition", definition.definition());
                row.put("sourceId", definition.sourceId());
                row.put("sourceFile", definition.sourceFile());
                row.put("sourceSha256", definition.sourceSha256());
                row.put("startOffset", definition.startOffset());
                row.put("endOffset", definition.endOffset());
                row.put("offsetUnit", definition.offsetUnit());
                row.put("offsetPrecision", definition.offsetPrecision());
                row.put("extractionMethod", definition.extractionMethod());
                row.put("status", definition.status().name());
                row.put("supportingClaimId", definition.supportingClaimId());
                definitions.add(row);
            }
            json.put("definitionsOfTheSeedScope-" + run.rule(), definitions);
            List<Map<String, Object>> relations = new ArrayList<>();
            for (SourceGroundedEnrichment.RelationCandidate relation
                    : seed.extraction().relations()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fromConceptId", relation.fromConceptId());
                row.put("fromConceptName", relation.fromConceptName());
                row.put("toConceptId", relation.toConceptId());
                row.put("toConceptName", relation.toConceptName());
                row.put("type", relation.type().name());
                row.put("direction", relation.direction());
                row.put("sourceId", relation.sourceId());
                row.put("sourceFile", relation.sourceFile());
                row.put("sourceSha256", relation.sourceSha256());
                row.put("startOffset", relation.startOffset());
                row.put("endOffset", relation.endOffset());
                row.put("offsetUnit", relation.offsetUnit());
                row.put("offsetPrecision", relation.offsetPrecision());
                row.put("span", relation.span());
                row.put("extractionMethod", relation.extractionMethod());
                row.put("status", relation.status().name());
                row.put("supportingClaimId", relation.supportingClaimId());
                relations.add(row);
            }
            json.put("relationsOfTheSeedScope", relations);
            json.put("conceptsWithoutDefinitionOfTheSeedScope",
                    seed.extraction().conceptsWithoutDefinition());
            List<Map<String, Object>> rejections = new ArrayList<>();
            for (SourceGroundedEnrichment.Rejection rejection : seed.extraction().rejections()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reason", rejection.reason());
                row.put("sourceId", rejection.sourceId());
                row.put("sourceFile", rejection.sourceFile());
                row.put("span", rejection.span());
                row.put("trigger", rejection.trigger());
                row.put("detail", rejection.detail());
                rejections.add(row);
            }
            json.put("rejectionsOfTheSeedScope", rejections);
        }

        Path target = RepositoryPaths.root().resolve(RESULTS_PATH);
        Files.createDirectories(target.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(target.toFile(), json);
    }

    private static List<String> difference(Set<String> left, Set<String> right) {
        List<String> values = new ArrayList<>();
        for (String value : left) {
            if (!right.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static void writeReport() throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("# C2A — the concept-enrichment experiment (H1, H2, H3)\n\n");
        text.append("**Status:** `MEASURED` — the three frozen candidates were run against the ")
                .append("29-case evaluation of the versioned dataset, under the definition rule ")
                .append("of the freeze v1.1 and under the rule of the freeze v1.1 supersedes.\n");
        text.append("**Date:** ").append(java.time.LocalDate.now()).append(".\n");
        text.append("**Branch / commit:** `").append(BRANCH).append("` / `").append(COMMIT)
                .append("`.\n");
        text.append("**Freeze:** `").append(FREEZE_PATH).append("` — sha256 `").append(freezeSha256)
                .append("` (the v1.1 version; it supersedes the v1.0 version, sha256 `")
                .append(V1_0_FREEZE_SHA256).append("`, whose first measurement is preserved ")
                .append("in §0).\n");
        text.append("**Produced by:** `matrix-core/src/test/java/io/matrix/knowledge/concept/")
                .append("ConceptEnrichmentExperimentTest.java` (research only; the production ")
                .append("retrieval path is unchanged).\n\n");
        text.append("> The candidates were frozen **before** this measurement. The results below ")
                .append("were produced against the frozen definitions; the SHA-256 above binds ")
                .append("them to that freeze. The first measurement — the one the freeze v1.1 ")
                .append("supersedes — is preserved in §0 and is not rewritten.\n\n");

        writeFirstMeasurement(text);
        writeScopes(text);
        writeExtraction(text);
        writeBaselineReproduction(text);
        writeDenominators(text);
        writeRuleMetrics(text, tight(), "4.1", "The tight rule — the rule of the freeze v1.1");
        writeBaselineDifference(text);
        writeRuleMetrics(text, loose(), "4.3",
                "The loose rule — the rule of the freeze v1.1 supersedes");
        writeCoverageShifts(text, tight(), "4.4");
        writeScale(text);
        writeCoverageShifts(text, loose(), "4.6");
        writeBaselineCases(text);
        writeH4(text);
        writeCandidateCases(text);
        writeFindings(text);
        writeLimits(text);

        Path target = RepositoryPaths.root().resolve(REPORT_PATH);
        Files.createDirectories(target.getParent());
        Files.writeString(target, text.toString(), StandardCharsets.UTF_8);
    }

    /** §0 — the first measurement, preserved. */
    private static void writeFirstMeasurement(StringBuilder text) {
        text.append("## 0. The v1.0 measurement, preserved\n\n");
        text.append("The freeze v1.1 supersedes the freeze v1.0 (sha256 `")
                .append(V1_0_FREEZE_SHA256).append("`). The first measurement was produced ")
                .append("against v1.0, and it is kept here as the record of that measurement: ")
                .append("its numbers are quoted **as they were published**, under the metric ")
                .append("definitions of v1.0 — including the three that the independent review ")
                .append("found defective (the MRR, the supported and unsupported answers, and the ")
                .append("top-1 metric). The corrected numbers are in §4.\n\n");
        text.append("| Row | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | ")
                .append("Supported answer | Abstention | Unsupported answer | Mean non-gold cited | ")
                .append("Top-1 not gold | Mean retrieval (ms) |\n");
        text.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (String[] row : V1_0_METRICS) {
            text.append("| `").append(row[0]).append("` | ").append(row[1]);
            for (int index = 2; index < row.length; index++) {
                text.append(" | ").append(row[index]);
            }
            text.append(" |\n");
        }
        text.append("\nThe v1.0 H4 classification, as published: ");
        for (String[] row : V1_0_CLASSIFICATIONS) {
            text.append("`").append(row[0]).append("` × ").append(row[1]).append(" (")
                    .append(row[2]).append("); ");
        }
        text.append("\n\nThe v1.0 classification counted a CASE, not a GOLD, and it counted a ")
                .append("case as `BELOW_FLOOR` when ANY of its golds was in the floor-0.0 ")
                .append("candidate list. §6 gives the per-gold classification the freeze v1.1 ")
                .append("requires.\n\n");
    }

    /** §1 — the workspaces. */
    private static void writeScopes(StringBuilder text) {
        text.append("## 1. The workspaces, prepared as the dataset says\n\n");
        text.append("| Scope | Workspace | Preparations | Documents acquired | Claims added | ")
                .append("Claims in the workspace | Concepts |\n|---|---|---|---|---|---|---|\n");
        for (RetrievalExperiment.ScopeReport report : result.scopes()) {
            text.append("| `").append(report.scope()).append("` | `").append(report.workspace())
                    .append("` | ").append(report.preparations()).append(" | ")
                    .append(report.documentsAcquired()).append(" | ").append(report.claimsAdded())
                    .append(" | ").append(report.claimsInWorkspace()).append(" | ")
                    .append(report.conceptsInWorkspace()).append(" |\n");
        }
        text.append("\nEvery scope of the dataset was prepared with the production methods ")
                .append("behind the HTTP calls: `AcquisitionCycle.loadSeed` (the seed route), ")
                .append("`AcquisitionCycle.run` (the learn route) and ")
                .append("`AcquisitionCycle.acquireAndRevise` (the revise route). The preparation ")
                .append("is repeated before every case, exactly as the official runner does it. ")
                .append("Preparation cost: ").append(result.preparationMillis()).append(" ms.\n\n");
    }

    /** §2 — the extraction under both rules. */
    private static void writeExtraction(StringBuilder text) {
        text.append("## 2. What the source-grounded extraction found\n\n");
        Map<String, Object> tight = tight().extractionByScope().get("A-01");
        Map<String, Object> loose = loose().extractionByScope().get("A-01");
        text.append("The extraction of the workspace of the first answer case (S1, ")
                .append(tight.get("workspaceClaims")).append(" claims, ")
                .append(tight.get("concepts")).append(" concepts), under both definition rules:\n\n");
        text.append("| Measure | The tight rule (v1.1) | The loose rule (v1.0) |\n|---|---|---|\n");
        text.append("| definitions | ").append(tight.get("definitions")).append(" | ")
                .append(loose.get("definitions")).append(" |\n");
        text.append("| — of which SUPPORTED | ").append(tight.get("supportedDefinitions"))
                .append(" | ").append(loose.get("supportedDefinitions")).append(" |\n");
        text.append("| concepts with **no** definition | ")
                .append(tight.get("conceptsWithoutDefinition")).append(" | ")
                .append(loose.get("conceptsWithoutDefinition")).append(" |\n");
        text.append("| relation candidates | ").append(tight.get("relations")).append(" | ")
                .append(loose.get("relations")).append(" |\n");
        text.append("| — of which SUPPORTED | ").append(tight.get("supportedRelations"))
                .append(" | ").append(loose.get("supportedRelations")).append(" |\n");
        text.append("| relation candidates rejected | ").append(tight.get("rejections"))
                .append(" | ").append(loose.get("rejections")).append(" |\n");
        text.append("| extraction time | ").append(tight.get("extractionMillis")).append(" ms | ")
                .append(loose.get("extractionMillis")).append(" ms |\n");
        text.append("| retained text of the extraction | ").append(tight.get("retainedBytes"))
                .append(" bytes | ").append(loose.get("retainedBytes")).append(" bytes |\n\n");

        int adjacent = 0;
        int tooLong = 0;
        List<SourceGroundedEnrichment.DefinitionCandidate> looseDefinitions = definitionsOf(loose());
        for (SourceGroundedEnrichment.DefinitionCandidate definition : looseDefinitions) {
            if (SourceGroundedEnrichment.holdsCopulaAfterName(definition.definition(),
                    definition.conceptName())) {
                adjacent++;
            }
            if (definition.definition().length()
                    > SourceGroundedEnrichment.MAX_DEFINITION_LENGTH) {
                tooLong++;
            }
        }
        int tightDefinitions = (int) tight.get("definitions");
        int different = 0;
        Map<String, String> looseByConcept = new LinkedHashMap<>();
        for (SourceGroundedEnrichment.DefinitionCandidate definition : looseDefinitions) {
            looseByConcept.put(definition.conceptId(), definition.definition());
        }
        for (SourceGroundedEnrichment.DefinitionCandidate definition : definitionsOf(tight())) {
            String before = looseByConcept.get(definition.conceptId());
            if (before != null && !before.equals(definition.definition())) {
                different++;
            }
        }
        text.append("**Why the rule was tightened.** The v1.0 rule asked for two things that may ")
                .append("happen ANYWHERE in the same sentence: the concept's name and a ")
                .append("definitional copula. Of the ").append(looseDefinitions.size())
                .append(" definitions it produced, only ").append(adjacent)
                .append(" have the name **immediately** followed by a definitional copula (")
                .append(percent(adjacent, looseDefinitions.size()))
                .append("), and ").append(tooLong)
                .append(" are longer than the ").append(SourceGroundedEnrichment.MAX_DEFINITION_LENGTH)
                .append(" characters the v1.1 rule allows. The other sentences state ")
                .append("something ELSE about the concept — they are not definitions, and a ")
                .append("candidate that carries them does not test the hypothesis it claims to ")
                .append("test. The v1.1 rule therefore requires the **adjacency** and the ")
                .append("**length cap**. It keeps ").append(tightDefinitions)
                .append(" definitions; for ").append(different)
                .append(" of those concepts it is a DIFFERENT sentence — the loose rule stopped ")
                .append("at the first sentence that carried the name and a copula anywhere, and ")
                .append("the tight rule kept looking for one that carried the name immediately ")
                .append("before the copula. The examples the loose rule produced are below.\n\n");

        text.append("### 2.1 The definitions the loose rule produced that are not definitions\n\n");
        text.append("The first twelve of them — the sentences that carry the concept's name and ")
                .append("a definitional copula somewhere, but do not state a definition:\n\n");
        text.append("| Concept | The definition the loose rule produced | Length |\n")
                .append("|---|---|---|\n");
        int shown = 0;
        for (SourceGroundedEnrichment.DefinitionCandidate definition : looseDefinitions) {
            if (SourceGroundedEnrichment.holdsCopulaAfterName(definition.definition(),
                    definition.conceptName())) {
                continue;
            }
            if (shown++ >= 12) {
                break;
            }
            text.append("| ").append(definition.conceptName()).append(" | ")
                    .append(definition.definition()).append(" | ")
                    .append(definition.definition().length()).append(" |\n");
        }
        text.append("\n### 2.2 The definitions the tight rule kept\n\n");
        text.append("| Concept | Definition (verbatim) | Source | Offsets | Precision | ")
                .append("Status |\n|---|---|---|---|---|---|\n");
        shown = 0;
        for (SourceGroundedEnrichment.DefinitionCandidate definition : definitionsOf(tight())) {
            if (shown++ >= 20) {
                break;
            }
            text.append("| ").append(definition.conceptName()).append(" | ")
                    .append(definition.definition()).append(" | ")
                    .append(definition.sourceFile()).append(" | ")
                    .append(definition.startOffset()).append("-")
                    .append(definition.endOffset()).append(" | ")
                    .append(definition.offsetPrecision()).append(" | ")
                    .append(definition.status()).append(" |\n");
        }
        text.append("\n**The concepts with NO definition found under the tight rule:** ")
                .append(tight.get("conceptsWithoutDefinition")).append(" of ")
                .append(tight.get("concepts")).append(" — the rule is closed, so a concept ")
                .append("whose own document never states it with the name IMMEDIATELY followed ")
                .append("by a definitional copula has no definition, and nothing was invented ")
                .append("for it. The full list is in the results file under ")
                .append("`conceptsWithoutDefinitionOfTheSeedScope`.\n\n");

        RetrievalExperiment.ExtractionRecord seed = null;
        for (RetrievalExperiment.ExtractionRecord candidate : tight().extractions()) {
            if (candidate.caseId().equals("A-01")) {
                seed = candidate;
            }
        }
        if (seed != null) {
            text.append("### 2.3 The typed relations the seed scope yielded\n\n");
            text.append("| From | Type | To | Direction | Source | Offsets | Precision | Status |\n");
            text.append("|---|---|---|---|---|---|---|---|\n");
            for (SourceGroundedEnrichment.RelationCandidate relation
                    : seed.extraction().relations()) {
                text.append("| ").append(relation.fromConceptName()).append(" | ")
                        .append(relation.type()).append(" | ")
                        .append(relation.toConceptName()).append(" | ")
                        .append(relation.direction()).append(" | ")
                        .append(relation.sourceFile()).append(" | ")
                        .append(relation.startOffset()).append("-")
                        .append(relation.endOffset()).append(" | ")
                        .append(relation.offsetPrecision()).append(" | ")
                        .append(relation.status()).append(" |\n");
            }
            text.append("\nThe supporting span of every relation is the sentence it was read from; ")
                    .append("the full spans are in the results file under ")
                    .append("`relationsOfTheSeedScope`.\n\n");
            text.append("**What `SUPPORTED` says.** It says that the **sentence** is an ")
                    .append("established claim of the workspace — the production validator ")
                    .append("promoted a claim carrying it. It does **not** say that the relation ")
                    .append("is true: the rule reads the sentence lexically, and a reader may ")
                    .append("call a relation wrong (the `IS_A` between \"Plate tectonics ...\" ")
                    .append("and \"Tectonic plates\" is read from a sentence that says plate ")
                    .append("tectonics *comprises* plates). The status is about the provenance ")
                    .append("of the text, not about the semantics of the relation — which is ")
                    .append("exactly why the relation is a **candidate**.\n\n");
        }
        text.append("The extraction over the whole run took ")
                .append(tight().extractionMillis()).append(" ms under the tight rule and ")
                .append(loose().extractionMillis()).append(" ms under the loose one, and retained ")
                .append(tight().retainedExtractionBytes()).append(" and ")
                .append(loose().retainedExtractionBytes()).append(" bytes of text.\n\n");
    }

    /** §3 — the baseline reproduction. */
    private static void writeBaselineReproduction(StringBuilder text) {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows(tight());
        Set<String> official = new LinkedHashSet<>(RetrievalExperiment.officialFailures());
        Set<String> harness = new LinkedHashSet<>(result.baselineFailures());
        text.append("## 3. Does the in-process harness reproduce the official 18/29?\n\n");
        text.append("The official result (`docs-v2/engineering/E2-FLOOR-0p25.json`) is **18 / 29**,")
                .append(" with the eleven failures `A-02, A-04, A-05, A-06, F-02, M-01, P-01, ")
                .append("P-02, M-03, R-04, R-05`.\n\n");
        text.append("The in-process harness, on the production path, passes **")
                .append(29 - harness.size()).append(" / 29**")
                .append(official.equals(harness)
                        ? " — **the same 18/29, and the same eleven failures.**"
                        : " — **not the same set.**")
                .append("\n\n");
        if (!official.equals(harness)) {
            text.append("| Difference | Cases |\n|---|---|\n");
            text.append("| failing in the official run only | ")
                    .append(difference(official, harness)).append(" |\n");
            text.append("| failing in the harness only | ")
                    .append(difference(harness, official)).append(" |\n\n");
        }
    }

    /** §4 — the denominators, stated once. */
    private static void writeDenominators(StringBuilder text) {
        text.append("## 4. The metrics\n\n");
        text.append("**The denominators.** The recall and the MRR are the MEAN over the ")
                .append("**14 answerable cases** (the cases whose `expect.answered` is true: ")
                .append("`A-01…A-06`, `P-01`, `P-02`, `M-01…M-03`, `K-01`, `K-02`, `R-03`), ")
                .append("measured on the floor-0.0 candidate list. The GLOBAL (micro) recall is ")
                .append("the share over **every gold proposition of those 14 cases** — ")
                .append("the cases are not weighted equally there. The supported answer, the ")
                .append("unsupported answer and the answered-with-no-gold share are over the ")
                .append("**14 answerable cases**; the abstention accuracy is over the **6 ")
                .append("unanswerable cases** (`U-01…U-06`); the unsupported answer over ALL the ")
                .append("ask cases is over the **20 ask cases** (the 14 answerable and the 6 ")
                .append("unanswerable ones) and counts an answered unanswerable case as ")
                .append("unsupported. The passed/total column is over the **29 cases** for the ")
                .append("production path and over the **20 ask cases** for the retriever-level ")
                .append("rows. The latency of the retriever-level rows is the PURE retriever; ")
                .append("the latency of `BASELINE-PATH` is the full production path, and the two ")
                .append("are not comparable.\n\n");
    }

    /** §4 — the metrics of one rule. */
    private static void writeRuleMetrics(StringBuilder text, RetrievalExperiment.RuleRun run,
            String section, String title) {
        text.append("### ").append(section).append(" ").append(title).append("\n\n");
        text.append("The ranking metrics (the recall and the MRR are measured on the ")
                .append("floor-0.0 candidate list of the **14 answerable cases**):\n\n");
        text.append("| Row | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | ")
                .append("micro Recall@1 | micro Recall@5 | micro Recall@10 | micro Recall@20 |\n");
        text.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : run.metrics().entrySet()) {
            RetrievalExperiment.CandidateMetrics m = entry.getValue();
            text.append("| `").append(entry.getKey()).append("` | ").append(m.passed())
                    .append("/").append(m.total()).append(" | ")
                    .append(four(m.recallAt1())).append(" | ")
                    .append(four(m.recallAt5())).append(" | ")
                    .append(four(m.recallAt10())).append(" | ")
                    .append(four(m.recallAt20())).append(" | ")
                    .append(four(m.mrr())).append(" | ")
                    .append(four(m.microRecallAt1())).append(" | ")
                    .append(four(m.microRecallAt5())).append(" | ")
                    .append(four(m.microRecallAt10())).append(" | ")
                    .append(four(m.microRecallAt20())).append(" |\n");
        }
        text.append("\nThe answer metrics (the supported answer, the unsupported answer and ")
                .append("the answered-with-no-gold share are over the **14 answerable ")
                .append("cases**; the abstention accuracy over the **6 unanswerable cases**; ")
                .append("the unsupported answer over all the ask cases over the **20 ask ")
                .append("cases**):\n\n");
        text.append("| Row | Supported answer | Unsupported answer | Answered with NO gold | ")
                .append("Abstention | Unsupported over all 20 ask cases | Gold availability | ")
                .append("Mean non-gold cited | Top-1 not gold |\n");
        text.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : run.metrics().entrySet()) {
            RetrievalExperiment.CandidateMetrics m = entry.getValue();
            text.append("| `").append(entry.getKey()).append("` | ")
                    .append(four(m.supportedAnswerAccuracy())).append(" (")
                    .append(m.supportedCount()).append("/14) | ")
                    .append(four(m.unsupportedAnswerRate())).append(" (")
                    .append(m.unsupportedCount()).append("/14) | ")
                    .append(m.zeroGoldCitedCount()).append("/14 | ")
                    .append(four(m.abstentionAccuracy())).append(" | ")
                    .append(four(m.unsupportedOverAllAsk())).append(" (")
                    .append(m.askCases()).append(" ask cases) | ")
                    .append(four(m.goldAvailability())).append(" | ")
                    .append(four(m.meanNonGoldCited())).append(" | ")
                    .append(m.casesWhoseTopRankIsNotGold()).append("/14 |\n");
        }
        text.append("\nThe cost (the retriever-level rows are the PURE retriever; the ")
                .append("`BASELINE-PATH` row is the full production path — the two are ")
                .append("different instruments and their latencies are not comparable; the ")
                .append("overhead column is only meaningful for the retriever-level rows):\n\n");
        text.append("| Row | Mean retrieval (ms) | Total over the row (ms) | Overhead per case ")
                .append("against `BASELINE-RETRIEVER` (ms) |\n|---|---|---|---|\n");
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : run.metrics().entrySet()) {
            RetrievalExperiment.CandidateMetrics m = entry.getValue();
            text.append("| `").append(entry.getKey()).append("` | ")
                    .append(m.meanRetrievalMillis()).append(" | ")
                    .append(m.totalRetrievalMillis()).append(" | ")
                    .append(four(m.overheadMillisPerCase())).append(" |\n");
        }
        text.append("\nThe failure lists:\n\n");
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : run.metrics().entrySet()) {
            text.append("* `").append(entry.getKey()).append("`: ")
                    .append(entry.getValue().failedCases()).append("\n");
        }
        text.append("\n");
    }

    /** §4.2 — the two baseline rows, computed from the data. */
    private static void writeBaselineDifference(StringBuilder text) {
        text.append("### 4.2 The two baseline rows, and where they differ\n\n");
        text.append("`BASELINE-PATH` is the whole production path: `KnowledgeWorkbench.ask`, ")
                .append("which adds the workbench-level gates (the demand analysis, the quantity ")
                .append("gate, the premise check) and the dialogue context. `BASELINE-RETRIEVER` is ")
                .append("the production retriever alone. The candidates are retriever-level by ")
                .append("construction — the candidate is applied to the snapshot before the ")
                .append("retriever sees it, or to the question before it is asked — so they are ")
                .append("compared against `BASELINE-RETRIEVER`.\n\n");
        text.append("The cases on which the two rows differ, computed from the run, with the ")
                .append("gate read from the abstention reason of the production path itself:\n\n");
        text.append("| Case | The path row answered | Its abstention reason | The gate | ")
                .append("The retriever row |\n|---|---|---|---|---|\n");
        List<String> differing = new ArrayList<>();
        for (RetrievalExperiment.CaseSpec caseSpec : dataset.cases()) {
            RetrievalExperiment.CaseResult path = rowOf(tight(), caseSpec.id(),
                    RetrievalExperiment.BASELINE_PATH);
            RetrievalExperiment.CaseResult retriever = rowOf(tight(), caseSpec.id(),
                    RetrievalExperiment.BASELINE_RETRIEVER);
            if (path == null || retriever == null) {
                continue;
            }
            if (path.passed() == retriever.passed() && path.answered() == retriever.answered()) {
                continue;
            }
            differing.add(caseSpec.id());
            String reason = path.abstentionReason() == null ? "—" : path.abstentionReason();
            String gate = path.answered() ? "—" : RetrievalExperiment.abstentionCode(reason);
            text.append("| ").append(caseSpec.id()).append(" | ").append(path.answered())
                    .append(" | ").append(reason).append(" | `").append(gate).append("` | ")
                    .append(retriever.answered() ? "answered" : "abstained").append(" |\n");
        }
        text.append("\nThe two rows differ on exactly ").append(differing.size())
                .append(" cases: ").append(differing).append(". The gate is not assumed: it is ")
                .append("read from the abstention reason the production path itself gives, so the ")
                .append("quantity gate and the time-demand gate are told apart by the ")
                .append("instrument rather than by prose. This is an asymmetry of the ")
                .append("**instrument**, not a property of the candidates, and it is the reason ")
                .append("the abstention accuracy of the retriever-level rows is lower than the ")
                .append("path row's.\n\n");
    }

    /** §4 — the coverage shifts, as intervals. */
    private static void writeCoverageShifts(StringBuilder text, RetrievalExperiment.RuleRun run,
            String section) {
        text.append("### ").append(section).append(" The coverage shifts");
        if (run.rule().equals(SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1.name())) {
            text.append(" under the tight rule");
        } else {
            text.append(" under the loose rule");
        }
        text.append("\n\n");
        if (run.coverageShifts().isEmpty()) {
            text.append("**No candidate lost a gold claim that the baseline cited.** There is ")
                    .append("no coverage shift to report.\n\n");
            return;
        }
        text.append("A candidate can **lose** a gold claim that the baseline cited. The table ")
                .append("gives the coverage floor at which the claim enters the answer, ")
                .append("measured with the production retriever alone. The value is a ")
                .append("**bracket**: the floors are scanned from 0.00 upwards in steps of ")
                .append("0.01, and the interval is the one the scan can resolve — the claim ")
                .append("is cited at every floor inside the bracket and not at the next one.\n\n");
        text.append("| Case | Candidate | Gold proposition | Floor under the baseline | ")
                .append("Floor under the candidate |\n|---|---|---|---|---|\n");
        for (RetrievalExperiment.CoverageShift shift : run.coverageShifts()) {
            text.append("| ").append(shift.caseId()).append(" | `").append(shift.candidate())
                    .append("` | ").append(shorten(shift.proposition())).append(" | ")
                    .append(RetrievalExperiment.coverageInterval(shift.baselineFloor()))
                    .append(" | ")
                    .append(RetrievalExperiment.coverageInterval(shift.candidateFloor()))
                    .append(" |\n");
        }
        text.append("\nThe coverage is a **ratio**: the matched weight of the question's terms ")
                .append("over the total weight of the question's terms, and the weights are the ")
                .append("IDF of the corpus (`TextFeatures.idf`: `log(1 + N / df)`). The ")
                .append("enrichment changes the claim texts, so it changes the document ")
                .append("frequencies, so it changes the weights, so it can move a claim in ")
                .append("**either** direction. The enrichment is not monotone: it can push a ")
                .append("claim below the floor that it was above.\n\n");
    }

    /** §4.5 — the scale of every candidate. */
    private static void writeScale(StringBuilder text) {
        text.append("### 4.5 The scale of every candidate\n\n");
        text.append("A candidate that touches almost nothing cannot change much. The table ")
                .append("gives, for the seed scope (the workspace of the first answer case), the ")
                .append("number of concepts the candidate WROTE INTO and the number of claims ")
                .append("whose searchable text it changed — the production retriever composes ")
                .append("the searchable text of a claim out of its proposition, its concept's ")
                .append("name and its concept's definition, so a claim's text changes exactly ")
                .append("when the definition of ITS concept changes.\n\n");
        Map<String, RetrievalExperiment.CandidateScale> scale = tight().scale().get("A-01");
        text.append("| Candidate | Concepts written into | of the workspace's concepts | ")
                .append("Claims whose text changed | of the workspace's claims | Other |\n");
        text.append("|---|---|---|---|---|---|\n");
        for (Map.Entry<String, RetrievalExperiment.CandidateScale> entry : scale.entrySet()) {
            RetrievalExperiment.CandidateScale s = entry.getValue();
            String other = EnrichmentCandidates.H3.equals(entry.getKey())
                    ? s.questionTermsAdded() + " term(s) added to the question"
                    : s.relatedConceptNamesApplied() + " related concept name(s), "
                            + s.relatedTextsApplied() + " relation text(s)";
            text.append("| `").append(entry.getKey()).append("` | ").append(s.conceptsTouched())
                    .append(" | ").append(s.conceptsInWorkspace()).append(" | ")
                    .append(s.claimsTouched()).append(" | ").append(s.claimsInWorkspace())
                    .append(" | ").append(other).append(" |\n");
        }
        RetrievalExperiment.CandidateScale h2 = scale.get(EnrichmentCandidates.H2);
        text.append("\n**The scale of H2 is the size of the problem it was asked to solve.** It ")
                .append("writes into ").append(h2.conceptsTouched()).append(" of the ")
                .append(h2.conceptsInWorkspace()).append(" concepts of the scope (")
                .append(percent(h2.conceptsTouched(), h2.conceptsInWorkspace()))
                .append("), because the workspace holds ").append(h2.relationsInScope())
                .append(" relation candidates — the relation extraction is the binding ")
                .append("constraint, not the corpus. The remaining ")
                .append(h2.conceptsInWorkspace() - h2.conceptsTouched())
                .append(" concepts keep the definition they had, so H2 cannot help them.\n\n");
    }

    /** §5 — the baseline, case by case. */
    private static void writeBaselineCases(StringBuilder text) {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows(tight());
        text.append("## 5. The baseline, case by case\n\n");
        text.append("| Case | Category | Scope | Verdict | Answered | Claims | Sources | ")
                .append("Gold cited | Gold condition | Gold ranks | Top-ranked claim is gold | ")
                .append("Failure (H4) |\n");
        text.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (RetrievalExperiment.CaseSpec caseSpec : dataset.cases()) {
            List<RetrievalExperiment.CaseResult> rows = baseline.get(caseSpec.id());
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            RetrievalExperiment.CaseResult row = rows.get(0);
            text.append("| ").append(caseSpec.id()).append(" | ").append(caseSpec.category())
                    .append(" | ").append(row.scope()).append(" | ")
                    .append(row.passed() ? "PASS" : "**FAIL**").append(" | ")
                    .append(row.answered()).append(" | ").append(row.citedClaims()).append(" | ")
                    .append(row.citedSources()).append(" | ").append(row.goldCited()).append("/")
                    .append(row.goldPropositions().size()).append(" | ")
                    .append(row.goldPropositions().isEmpty() ? "—" : row.goldCondition())
                    .append(" | ").append(row.goldRanks())
                    .append(" | ").append(row.topRankIsGold() == null ? "—"
                            : row.goldPropositions().isEmpty() ? "—"
                            : row.topRankIsGold() ? "yes" : "no").append(" | ")
                    .append(row.passed() ? "—" : row.classification()).append(" |\n");
        }
        text.append("\n");
    }

    /** §6 — H4, per gold. */
    private static void writeH4(StringBuilder text) {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows(tight());
        text.append("## 6. H4 — what the failures actually are, per gold\n\n");
        text.append("The freeze v1.1 §5 classifies **per gold proposition**: every gold of a ")
                .append("failing case gets its own code, and the case gets the code its golds ")
                .append("agree on — or `MIXED_GOLD_CODES` when they do not agree. A gold the ")
                .append("answer cited is `GOLD_REACHED`: it is not a defect of that gold. The ")
                .append("classification is computed for the FAILING cases only; a passing case ")
                .append("carries no code at all.\n\n");
        text.append("| Case | The case's code | The code of each gold | Evidence |\n")
                .append("|---|---|---|---|\n");
        Map<String, List<String>> byCode = new LinkedHashMap<>();
        for (RetrievalExperiment.CaseSpec caseSpec : dataset.cases()) {
            List<RetrievalExperiment.CaseResult> rows = baseline.get(caseSpec.id());
            if (rows == null || rows.isEmpty() || rows.get(0).passed()) {
                continue;
            }
            RetrievalExperiment.CaseResult row = rows.get(0);
            byCode.computeIfAbsent(row.classification(), key -> new ArrayList<>())
                    .add(caseSpec.id());
            text.append("| ").append(caseSpec.id()).append(" | `").append(row.classification())
                    .append("` | ").append(row.goldClassifications()).append(" | ")
                    .append(row.classificationEvidence()).append(" |\n");
        }
        text.append("\n**The counts, per code:**\n\n");
        for (Map.Entry<String, List<String>> entry : byCode.entrySet()) {
            text.append("* `").append(entry.getKey()).append("` × ").append(entry.getValue().size())
                    .append(" — ").append(entry.getValue()).append("\n");
        }
        text.append("\n**What the counts say.** The v1.0 report counted a CASE and called seven ")
                .append("of them `BELOW_FLOOR`. Under the per-gold rule of the freeze v1.1 only ")
                .append("**two** of the failures are purely a floor failure: every gold of ")
                .append("those cases is reachable and below the floor. **Five** of them are ")
                .append("`MIXED_GOLD_CODES`: their golds carry DIFFERENT codes, and in every one ")
                .append("of them at least one gold is `NO_LEXICAL_MATCH` — a gold the question ")
                .append("shares no term with, so that gold is unreachable at ANY floor, and the ")
                .append("floor is not what failed to reach it. The report of the first ")
                .append("measurement therefore attributed to the floor a share of the failures ")
                .append("that the floor does not carry.\n\n");
    }

    /** §7 — the candidates, case by case. */
    private static void writeCandidateCases(StringBuilder text) {
        List<String> candidates = new ArrayList<>();
        for (RetrievalExperiment.CaseResult row : tight().cases()) {
            if (!candidates.contains(row.candidate())) {
                candidates.add(row.candidate());
            }
        }
        text.append("## 7. The candidates, case by case\n\n");
        text.append("The gold ranks (the rank of each gold proposition in the floor-0.0 candidate ")
                .append("list; 0 means the gold is not in the list at all) and the verdict of each ")
                .append("candidate, under the tight rule of the freeze v1.1.\n\n");
        text.append("| Case | Gold ranks |");
        for (String candidate : candidates) {
            text.append(" ").append(candidate).append(" |");
        }
        text.append("\n|---|---|");
        for (int index = 0; index < candidates.size(); index++) {
            text.append("---|");
        }
        text.append("\n");
        for (RetrievalExperiment.CaseSpec caseSpec : dataset.cases()) {
            RetrievalExperiment.CaseResult base = rowOf(tight(), caseSpec.id(),
                    RetrievalExperiment.BASELINE_PATH);
            if (base == null) {
                continue;
            }
            text.append("| ").append(caseSpec.id()).append(" | ").append(base.goldRanks())
                    .append(" |");
            for (String candidate : candidates) {
                RetrievalExperiment.CaseResult row = rowOf(tight(), caseSpec.id(), candidate);
                if (row == null) {
                    text.append(" — |");
                } else {
                    text.append(" ").append(row.goldRanks()).append(" ")
                            .append(row.passed() ? "PASS" : "FAIL").append(" |");
                }
            }
            text.append("\n");
        }
        text.append("\n");
    }

    /** §8 — the findings. */
    private static void writeFindings(StringBuilder text) {
        text.append("## 8. What the measurement says\n\n");
        for (RetrievalExperiment.RuleRun run : result.runs()) {
            boolean tightRun = run.rule().equals(
                    SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1.name());
            text.append(tightRun
                    ? "**Under the tight rule (the freeze v1.1):** none of the three "
                            + "candidates turns a single baseline failure into a pass.\n\n"
                    : "**Under the loose rule (the rule of the freeze v1.1 supersedes):** the "
                            + "same, and the rule changes nothing about the outcome.\n\n");
            Map<String, List<RetrievalExperiment.CaseResult>> byCandidate = new LinkedHashMap<>();
            for (RetrievalExperiment.CaseResult row : run.cases()) {
                byCandidate.computeIfAbsent(row.candidate(), key -> new ArrayList<>()).add(row);
            }
            List<String> baselinePass = new ArrayList<>();
            for (RetrievalExperiment.CaseResult row
                    : byCandidate.getOrDefault(RetrievalExperiment.BASELINE_RETRIEVER, List.of())) {
                if (row.passed()) {
                    baselinePass.add(row.caseId());
                }
            }
            for (String candidate : List.of(EnrichmentCandidates.H1, EnrichmentCandidates.H2,
                    EnrichmentCandidates.H3, EnrichmentCandidates.H1_AND_H2)) {
                List<String> nowPass = new ArrayList<>();
                List<String> lost = new ArrayList<>();
                for (RetrievalExperiment.CaseResult row
                        : byCandidate.getOrDefault(candidate, List.of())) {
                    if (row.passed() && !baselinePass.contains(row.caseId())) {
                        nowPass.add(row.caseId());
                    }
                    if (!row.passed() && baselinePass.contains(row.caseId())) {
                        lost.add(row.caseId());
                    }
                }
                text.append("* `").append(candidate).append("`: **newly passing** ")
                        .append(nowPass.isEmpty() ? "none" : nowPass)
                        .append("; **newly failing** ")
                        .append(lost.isEmpty() ? "none" : lost)
                        .append(".\n");
            }
            text.append("\n");
        }
        text.append("**The headline.** The three explanations the brief offers for the residual ")
                .append("retrieval failures are all measurable, and none of them recovers a ")
                .append("failure under either definition rule: the enrichment does not reach ")
                .append("the golds the question shares no term with (the `NO_LEXICAL_MATCH` ")
                .append("defect of §6), and where it does reach them it moves the coverage of ")
                .append("the claims it touches in BOTH directions (§4.4). What the definition ")
                .append("rule changes is what H1 COSTS: under the loose rule H1 lost A-03, a ")
                .append("case the baseline passed, because the sentences it wrote into the ")
                .append("concepts were not definitions; under the tight rule it loses nothing, ")
                .append("and it still recovers nothing. A candidate that recovers no failure ")
                .append("under either rule is not one that is merely too weak — it is one that ")
                .append("does not reach the failing golds at all.\n\n");
    }

    /** §9 — the limits. */
    private static void writeLimits(StringBuilder text) {
        text.append("## 9. The limits of this measurement\n\n");
        text.append("* The candidates are applied to the **snapshot** or to the **question**; the ")
                .append("production retriever is reused as it is and ")
                .append("`matrix-core/src/main/java` is not modified.\n");
        text.append("* The **false-premise** path (`PremiseAssessment.assess`) builds its own ")
                .append("retriever from the stored workspace, and the **revision** path does not ")
                .append("retrieve at all, so neither path can receive the enrichment under the ")
                .append("no-production-change constraint. The rows of those cases are the ")
                .append("baseline's own.\n");
        text.append("* The ranking metrics need the ranked candidate list, which the production ")
                .append("retriever only exposes at floor 0.0; that pass is timed separately and is ")
                .append("the cost of the instrument, not of the candidates.\n");
        text.append("* The **coverage floors** are brackets of 0.01, not exact values: the scan ")
                .append("resolves the floor only to the step it was run with.\n");
        text.append("* The held-out dataset is owned by another worker; this experiment has ")
                .append("never seen it, and it must be run against the freeze in `")
                .append(FREEZE_PATH).append("`.\n");
    }

    private static String four(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String percent(int part, int whole) {
        if (whole == 0) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 90 ? text : text.substring(0, 90) + "…";
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", failure);
        }
    }
}
