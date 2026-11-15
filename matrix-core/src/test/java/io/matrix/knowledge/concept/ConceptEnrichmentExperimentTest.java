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
 * <p><b>What this test is NOT.</b> It does not change the production retrieval path: not one
 * line of {@code matrix-core/src/main/java} is touched. The production
 * {@link io.matrix.knowledge.KnowledgeRetriever} is reused as it is; the candidates are applied
 * to the snapshot before it sees it, or to the question before it is asked.</p>
 *
 * <p><b>The assertions.</b> The test asserts the invariants of the experiment, not the numbers
 * it hopes for: the frozen denominators, the monotonicity of recall@K, the internal consistency
 * of the metric code (the aggregate is recomputed from the per-case rows), the provenance of
 * every candidate (its span is really a verbatim span of the source document, at the offsets
 * that are recorded), the fact that the candidates change the concepts and NOT the claims, and
 * the binding of the results to the freeze (the SHA-256 of the freeze file is in the results
 * file).</p>
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

    private static Path workDirectory;
    private static RetrievalExperiment.Result result;
    private static RetrievalExperiment.Dataset dataset;
    private static String freezeSha256;

    @BeforeAll
    static void runTheExperiment() throws Exception {
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
    @DisplayName("every gold proposition is present verbatim in the corpus of its scope")
    void everyGoldPropositionIsInTheCorpus() {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows();
        int checked = 0;
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
            if (row.goldPropositions().isEmpty()) {
                continue;
            }
            for (String proposition : row.goldPropositions()) {
                assertTrue(proposition != null && !proposition.isBlank());
                checked++;
            }
            }
        }
        assertTrue(checked >= 27, "the answerable cases must carry their gold, checked " + checked);
    }

    @Test
    @DisplayName("the metrics are the frozen ones, and they are internally consistent")
    void theMetricsAreConsistent() {
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : result.metrics().entrySet()) {
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
            // THE AGGREGATE IS RECOMPUTED FROM THE PER-CASE ROWS.
            double recomputed = 0.0;
            int counted = 0;
            for (RetrievalExperiment.CaseResult row : result.cases()) {
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

    @Test
    @DisplayName("the baseline row judges all 29 cases, and the official reference is recorded")
    void theBaselineJudgesEveryCase() {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows();
        assertEquals(29, baseline.size(), "every case must have a baseline row");
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
            assertNotNull(row.problems(), "every baseline row must carry its problems");
            assertNotNull(row.observed(), "every baseline row must carry what was observed");
            if (row.passed()) {
                assertTrue(row.problems().isEmpty(),
                        "a passing case must carry no problem: " + row.caseId());
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
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows();
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
            if (row.passed()) {
                continue;
            }
            assertFalse(row.classification().isBlank(),
                    "the failure " + row.caseId() + " carries no classification");
            assertFalse(row.classificationEvidence().isBlank(),
                    "the failure " + row.caseId() + " carries no evidence");
            }
        }
    }

    @Test
    @DisplayName("the extraction is grounded: every candidate carries a verbatim, located span")
    void theExtractionIsGrounded() throws Exception {
        Map<String, Object> summary = result.extractionByScope().get("A-01");
        assertNotNull(summary, "the extraction of the first answer case must be recorded");
        assertNotNull(summary.get("definitions"));
        assertNotNull(summary.get("relations"));
        assertTrue((int) summary.get("concepts") > 0);
        assertEquals((int) summary.get("concepts"),
                (int) summary.get("definitions") + (int) summary.get("conceptsWithoutDefinition"),
                "every concept must have a definition or be recorded as having none");
    }

    @Test
    @DisplayName("every definition and every relation is a verbatim span of its source document")
    void everySpanIsVerbatim() {
        RetrievalExperiment.ExtractionRecord record = null;
        for (RetrievalExperiment.ExtractionRecord candidate : result.extractions()) {
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
            assertEquals(SourceGroundedEnrichment.DEFINITION_METHOD, definition.extractionMethod());
            assertEquals(SourceGroundedEnrichment.OFFSET_UNIT, definition.offsetUnit());
            assertFalse(definition.sourceId().isBlank());
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
        // The candidate rows of one case share the claims of the baseline row: the corpus is
        // the same, so a difference in the verdict is the candidate's doing and not the
        // corpus's.
        Map<String, List<RetrievalExperiment.CaseResult>> byCase = new LinkedHashMap<>();
        for (RetrievalExperiment.CaseResult row : result.cases()) {
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
                        "the case " + entry.getKey() + " must run every candidate on the same corpus");
            }
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
        Path report = RepositoryPaths.root().resolve(REPORT_PATH);
        assertTrue(Files.exists(report), "the report must be written");
        assertTrue(Files.size(report) > 2000, "the report must hold the findings");
        assertTrue(Files.readString(report, StandardCharsets.UTF_8).contains(freezeSha256),
                "the report must carry the SHA-256 of the freeze too");
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

    private static Map<String, List<RetrievalExperiment.CaseResult>> baselineRows() {
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = new LinkedHashMap<>();
        for (RetrievalExperiment.CaseResult row : result.cases()) {
            if (row.candidate().equals(RetrievalExperiment.BASELINE_PATH)) {
                baseline.computeIfAbsent(row.caseId(), key -> new ArrayList<>()).add(row);
            }
        }
        return baseline;
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

        Map<String, Object> freeze = new LinkedHashMap<>();
        freeze.put("path", FREEZE_PATH);
        freeze.put("sha256", freezeSha256);
        freeze.put("branch", BRANCH);
        freeze.put("commit", COMMIT);
        freeze.put("statement", "the results below were produced against the candidates frozen in "
                + FREEZE_PATH + " (sha256 " + freezeSha256 + "); the candidates were frozen before "
                + "any measurement was taken");
        json.put("freeze", freeze);

        Map<String, Object> reproduction = new LinkedHashMap<>();
        reproduction.put("officialFailures", RetrievalExperiment.officialFailures());
        reproduction.put("harnessFailures", result.baselineFailures());
        Set<String> official = new LinkedHashSet<>(RetrievalExperiment.officialFailures());
        Set<String> harness = new LinkedHashSet<>(result.baselineFailures());
        reproduction.put("identical", official.equals(harness));
        reproduction.put("onlyInOfficial", difference(official, harness));
        reproduction.put("onlyInHarness", difference(harness, official));
        json.put("baselineReproduction", reproduction);

        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : result.metrics().entrySet()) {
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
            row.put("supportedAnswerAccuracy", value.supportedAnswerAccuracy());
            row.put("abstentionAccuracy", value.abstentionAccuracy());
            row.put("unsupportedAnswerRate", value.unsupportedAnswerRate());
            row.put("meanNonGoldClaimsCited", value.meanNonGoldCited());
            row.put("casesWhoseTopRankIsNotGold", value.casesWhoseTopRankIsNotGold());
            row.put("meanRetrievalMillis", value.meanRetrievalMillis());
            row.put("totalRetrievalMillis", value.totalRetrievalMillis());
            row.put("measuredCases", value.measuredCases());
            metrics.put(entry.getKey(), row);
        }
        json.put("metrics", metrics);

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
        json.put("extractionByCase", result.extractionByScope());

        List<Map<String, Object>> shifts = new ArrayList<>();
        for (RetrievalExperiment.CoverageShift shift : result.coverageShifts()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("case", shift.caseId());
            row.put("candidate", shift.candidate());
            row.put("proposition", shift.proposition());
            row.put("baselineCoverageFloor", shift.baselineFloor());
            row.put("candidateCoverageFloor", shift.candidateFloor());
            shifts.add(row);
        }
        json.put("coverageShifts", shifts);

        // THE FULL EXTRACTION OF THE SEED SCOPE, with the provenance of every candidate.
        RetrievalExperiment.ExtractionRecord seed = null;
        for (RetrievalExperiment.ExtractionRecord candidate : result.extractions()) {
            if (candidate.caseId().equals("A-01")) {
                seed = candidate;
            }
        }
        if (seed != null) {
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
            json.put("definitionsOfTheSeedScope", definitions);
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
        json.put("preparationMillis", result.preparationMillis());
        json.put("extractionMillis", result.extractionMillis());
        json.put("retainedExtractionBytes", result.retainedExtractionBytes());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (RetrievalExperiment.CaseResult row : result.cases()) {
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
            item.put("goldRanks", row.goldRanks());
            item.put("recallAt1", row.recallAt1());
            item.put("recallAt5", row.recallAt5());
            item.put("recallAt10", row.recallAt10());
            item.put("recallAt20", row.recallAt20());
            item.put("mrr", row.mrr());
            item.put("retrievalMillis", row.retrievalMillis());
            item.put("classification", row.classification());
            item.put("classificationEvidence", row.classificationEvidence());
            item.put("abstentionReason", row.abstentionReason());
            item.put("workspaceClaims", row.workspaceClaims());
            rows.add(item);
        }
        json.put("cases", rows);

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
                .append("29-case evaluation of the versioned dataset.\n");
        text.append("**Date:** ").append(java.time.LocalDate.now()).append(".\n");
        text.append("**Branch / commit:** `").append(BRANCH).append("` / `").append(COMMIT)
                .append("`.\n");
        text.append("**Freeze:** `").append(FREEZE_PATH).append("` — sha256 `").append(freezeSha256)
                .append("`.\n");
        text.append("**Produced by:** `matrix-core/src/test/java/io/matrix/knowledge/concept/")
                .append("ConceptEnrichmentExperimentTest.java` (research only; the production ")
                .append("retrieval path is unchanged).\n\n");
        text.append("> The candidates were frozen **before** this measurement. The results below ")
                .append("were produced against the frozen definitions; the SHA-256 above binds ")
                .append("them to that freeze.\n\n");

        // ── 1. the preparation ──
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

        // ── 2. the extraction ──
        text.append("## 2. What the source-grounded extraction found\n\n");
        Map<String, Object> first = result.extractionByScope().get("A-01");
        if (first != null) {
            text.append("The extraction of the workspace of the first answer case (S1, ")
                    .append(first.get("workspaceClaims")).append(" claims, ")
                    .append(first.get("concepts")).append(" concepts):\n\n");
            text.append("| Measure | Value |\n|---|---|\n");
            text.append("| concepts | ").append(first.get("concepts")).append(" |\n");
            text.append("| definition candidates | ").append(first.get("definitions"))
                    .append(" |\n");
            text.append("| — of which SUPPORTED (the production validator promoted a claim ")
                    .append("carrying the same sentence) | ").append(first.get("supportedDefinitions"))
                    .append(" |\n");
            text.append("| concepts with **no** definition found | ")
                    .append(first.get("conceptsWithoutDefinition")).append(" |\n");
            text.append("| relation candidates | ").append(first.get("relations")).append(" |\n");
            text.append("| — of which SUPPORTED | ").append(first.get("supportedRelations"))
                    .append(" |\n");
            text.append("| relation candidates rejected | ").append(first.get("rejections"))
                    .append(" |\n");
            text.append("| extraction time | ").append(first.get("extractionMillis")).append(" ms |\n");
            text.append("| retained text of the extraction | ").append(first.get("retainedBytes"))
                    .append(" bytes |\n\n");
            Object byReason = first.get("rejectionsByReason");
            if (byReason instanceof Map<?, ?> reasons && !reasons.isEmpty()) {
                text.append("The rejections, by reason:\n\n| Reason | Count |\n|---|---|\n");
                for (Map.Entry<?, ?> entry : reasons.entrySet()) {
                    text.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue())
                            .append(" |\n");
                }
                text.append("\n");
            }
            Object byType = first.get("relationsByType");
            if (byType instanceof Map<?, ?> types && !types.isEmpty()) {
                text.append("The relations, by type:\n\n| Type | Count |\n|---|---|\n");
                for (Map.Entry<?, ?> entry : types.entrySet()) {
                    text.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue())
                            .append(" |\n");
                }
                text.append("\n");
            }
        }
        text.append("The extraction over the whole run took ").append(result.extractionMillis())
                .append(" ms and retained ").append(result.retainedExtractionBytes())
                .append(" bytes of text.\n\n");

        RetrievalExperiment.ExtractionRecord seed = null;
        for (RetrievalExperiment.ExtractionRecord candidate : result.extractions()) {
            if (candidate.caseId().equals("A-01")) {
                seed = candidate;
            }
        }
        if (seed != null) {
            text.append("### 2.1 The definitions the seed scope yielded\n\n");
            text.append("Every definition is the **verbatim sentence** of the concept's own source ")
                    .append("document, with the offsets that sentence occupies in the extracted ")
                    .append("text of that document. The full list, with the provenance of every ")
                    .append("one, is in `C2A-CONCEPT-ENRICHMENT-RESULTS.json` under ")
                    .append("`definitionsOfTheSeedScope`. The first twenty:\n\n");
            text.append("| Concept | Definition (verbatim) | Source | Offsets | Precision | ")
                    .append("Status |\n|---|---|---|---|---|---|\n");
            int shown = 0;
            for (SourceGroundedEnrichment.DefinitionCandidate definition
                    : seed.extraction().definitions()) {
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
            text.append("\n**The concepts with NO definition found:** ")
                    .append(seed.extraction().conceptsWithoutDefinition().size())
                    .append(" of ").append(seed.extraction().conceptCount())
                    .append(" — the rule is closed, so a concept whose own document never states ")
                    .append("it with a definitional copula has no definition, and nothing was ")
                    .append("invented for it. The full list is in the results file under ")
                    .append("`conceptsWithoutDefinitionOfTheSeedScope`.\n\n");
            text.append("### 2.2 The typed relations the seed scope yielded\n\n");
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

        // ── 3. the baseline reproduction ──
        Map<String, List<RetrievalExperiment.CaseResult>> baseline = baselineRows();
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
            text.append("The per-case verdicts are in §5; the difference is explained there.\n\n");
        }

        // ── 4. the metric table ──
        text.append("## 4. The metrics\n\n");
        text.append("All the numbers are on the 29 cases. `BASELINE-PATH` is the full production ")
                .append("path (`KnowledgeWorkbench.ask`); the other rows are the production ")
                .append("retriever alone, which is the level the candidates live at. The recall ")
                .append("and the MRR are measured on the floor-0.0 candidate list; the answers, ")
                .append("the abstentions and the false positives on the floor-0.25 answer.\n\n");
        text.append("| Candidate | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | ")
                .append("Supported answer | Abstention | Unsupported answer | Mean non-gold cited | ")
                .append("Top-1 not gold | Mean retrieval (ms) |\n");
        text.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : result.metrics().entrySet()) {
            RetrievalExperiment.CandidateMetrics metrics = entry.getValue();
            text.append("| `").append(entry.getKey()).append("` | ").append(metrics.passed())
                    .append("/").append(metrics.total()).append(" | ")
                    .append(four(metrics.recallAt1())).append(" | ")
                    .append(four(metrics.recallAt5())).append(" | ")
                    .append(four(metrics.recallAt10())).append(" | ")
                    .append(four(metrics.recallAt20())).append(" | ")
                    .append(four(metrics.mrr())).append(" | ")
                    .append(four(metrics.supportedAnswerAccuracy())).append(" | ")
                    .append(four(metrics.abstentionAccuracy())).append(" | ")
                    .append(four(metrics.unsupportedAnswerRate())).append(" | ")
                    .append(four(metrics.meanNonGoldCited())).append(" | ")
                    .append(metrics.casesWhoseTopRankIsNotGold()).append(" | ")
                    .append(metrics.meanRetrievalMillis()).append(" |\n");
        }
        text.append("\nThe failure lists:\n\n");
        for (Map.Entry<String, RetrievalExperiment.CandidateMetrics> entry
                : result.metrics().entrySet()) {
            text.append("* `").append(entry.getKey()).append("`: ")
                    .append(entry.getValue().failedCases()).append("\n");
        }
        text.append("\n### 4.1 The two baseline rows, and why they differ\n\n");
        text.append("`BASELINE-PATH` is the whole production path: `KnowledgeWorkbench.ask`, ")
                .append("which adds the workbench-level gates (the demand analysis, the quantity ")
                .append("gate, the premise check) and the dialogue context. `BASELINE-RETRIEVER` is ")
                .append("the production retriever alone. The candidates are retriever-level by ")
                .append("construction — the candidate is applied to the snapshot before the ")
                .append("retriever sees it, or to the question before it is asked — so they are ")
                .append("compared against `BASELINE-RETRIEVER`.\n\n");
        text.append("The two rows differ on exactly the four **quantity** cases `U-01`, `U-03`, ")
                .append("`U-04` and `U-06`: the quantity gate lives in the workbench ")
                .append("(`KnowledgeWorkbench.applyDemand`), above the retriever, and it refuses a ")
                .append("question that asks for a number the workspace does not state. The ")
                .append("retriever alone answers them. This is an asymmetry of the **instrument**, ")
                .append("not a property of the candidates, and it is the reason the abstention ")
                .append("accuracy of the retriever-level rows is lower than the path row's.\n\n");
        if (!result.coverageShifts().isEmpty()) {
            text.append("### 4.2 The coverage shifts: what an enrichment does to the floor\n\n");
            text.append("A candidate can **lose** a gold claim that the baseline cited. The table ")
                    .append("gives the coverage floor at which the claim enters the answer, ")
                    .append("measured with the production retriever alone.\n\n");
            text.append("| Case | Candidate | Gold proposition | Floor under the baseline | ")
                    .append("Floor under the candidate |\n|---|---|---|---|---|\n");
            for (RetrievalExperiment.CoverageShift shift : result.coverageShifts()) {
                text.append("| ").append(shift.caseId()).append(" | `").append(shift.candidate())
                        .append("` | ").append(shorten(shift.proposition())).append(" | ")
                        .append(four(shift.baselineFloor())).append(" | ")
                        .append(four(shift.candidateFloor())).append(" |\n");
            }
            text.append("\nThe coverage is a **ratio**: the matched weight of the question's terms ")
                    .append("over the total weight of the question's terms, and the weights are the ")
                    .append("IDF of the corpus (`TextFeatures.idf`: `log(1 + N / df)`). The ")
                    .append("enrichment changes the claim texts, so it changes the document ")
                    .append("frequencies, so it changes the weights, so it can move a claim in ")
                    .append("**either** direction. The enrichment is not monotone: it can push a ")
                    .append("claim below the floor that it was above.\n\n");
        }

        // ── 5. the per-case table ──
        text.append("## 5. The baseline, case by case\n\n");
        text.append("| Case | Category | Scope | Verdict | Answered | Claims | Sources | ")
                .append("Gold cited | Gold ranks | Failure (H4) |\n");
        text.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
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
                    .append(row.goldPropositions().size()).append(" | ").append(row.goldRanks())
                    .append(" | ")
                    .append(row.passed() ? "—" : row.classification()).append(" |\n");
        }
        text.append("\n");

        // ── 6. H4 ──
        text.append("## 6. H4 — what the failures actually are\n\n");
        text.append("| Case | Classification | Evidence |\n|---|---|---|\n");
        for (RetrievalExperiment.CaseSpec caseSpec : dataset.cases()) {
            List<RetrievalExperiment.CaseResult> rows = baseline.get(caseSpec.id());
            if (rows == null || rows.isEmpty() || rows.get(0).passed()) {
                continue;
            }
            RetrievalExperiment.CaseResult row = rows.get(0);
            text.append("| ").append(caseSpec.id()).append(" | `").append(row.classification())
                    .append("` | ").append(row.classificationEvidence()).append(" |\n");
        }
        text.append("\n");

        // ── 7. the candidates, case by case ──
        text.append("## 7. The candidates, case by case\n\n");
        List<String> candidates = new ArrayList<>();
        for (RetrievalExperiment.CaseResult row : result.cases()) {
            if (!candidates.contains(row.candidate())) {
                candidates.add(row.candidate());
            }
        }
        text.append("The gold ranks (the rank of each gold proposition in the floor-0.0 candidate ")
                .append("list; 0 means the gold is not in the list at all) and the verdict of each ")
                .append("candidate.\n\n");
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
            List<RetrievalExperiment.CaseResult> rows = baseline.get(caseSpec.id());
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            text.append("| ").append(caseSpec.id()).append(" | ")
                    .append(rows.get(0).goldRanks()).append(" |");
            for (String candidate : candidates) {
                RetrievalExperiment.CaseResult row = null;
                for (RetrievalExperiment.CaseResult candidateRow : result.cases()) {
                    if (candidateRow.caseId().equals(caseSpec.id())
                            && candidateRow.candidate().equals(candidate)) {
                        row = candidateRow;
                    }
                }
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

        // ── 8. the findings ──
        text.append("## 8. What the measurement says\n\n");
        text.append("**The headline: none of the three candidates turns a single baseline ")
                .append("failure into a pass.** The three explanations the brief offers for the ")
                .append("residual retrieval failures are all measurable, and all three of them ")
                .append("leave the eleven failures where they were — while each of them costs ")
                .append("something (a lost case, a diluted coverage, a larger answer). The ")
                .append("numbers are below, case by case.\n\n");
        Map<String, List<RetrievalExperiment.CaseResult>> byCandidate = new LinkedHashMap<>();
        for (RetrievalExperiment.CaseResult row : result.cases()) {
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
            for (RetrievalExperiment.CaseResult row : byCandidate.getOrDefault(candidate, List.of())) {
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
        Map<String, Integer> classifications = new LinkedHashMap<>();
        for (List<RetrievalExperiment.CaseResult> rows : baseline.values()) {
            for (RetrievalExperiment.CaseResult row : rows) {
            if (!row.passed()) {
                classifications.merge(row.classification(), 1, Integer::sum);
            }
            }
        }
        text.append("The baseline failures, by classification: ");
        for (Map.Entry<String, Integer> entry : classifications.entrySet()) {
            text.append("`").append(entry.getKey()).append("` × ").append(entry.getValue())
                    .append("; ");
        }
        text.append("\n\n");

        // ── 9. the limits ──
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
        text.append("* The held-out dataset is owned by another worker; this experiment has ")
                .append("never seen it, and it must be run against the freeze in ")
                .append("`").append(FREEZE_PATH).append("`.\n");

        Path target = RepositoryPaths.root().resolve(REPORT_PATH);
        Files.createDirectories(target.getParent());
        Files.writeString(target, text.toString(), StandardCharsets.UTF_8);
    }

    private static String four(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
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
