package io.matrix.knowledge.concept;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeRetriever;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.ModelRevisionDecider;
import io.matrix.knowledge.PremiseAssessment;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * C2A (Workstream B3+B5) — the metrics and the runner of the concept-enrichment experiment.
 *
 * <p><b>This is research code, in the TEST source set.</b> The production retrieval path is not
 * modified: the production {@link KnowledgeRetriever} is reused as it is, over the snapshot the
 * candidate enriched.</p>
 *
 * <p><b>What the runner does, per case of the 29-case dataset:</b></p>
 *
 * <ol>
 *   <li>it prepares the case's scope with the PRODUCTION methods behind the HTTP calls of the
 *       dataset's {@code scopes} block — {@code AcquisitionCycle.loadSeed} (the seed route),
 *       {@code AcquisitionCycle.run} (the learn route) and
 *       {@code AcquisitionCycle.acquireAndRevise} (the revise route);</li>
 *   <li>it runs the case on the production path — {@code KnowledgeWorkbench.ask} for an answer,
 *       {@code PremiseAssessment.assess} for a false-premise check, the revise cycle for a
 *       revision — and judges it with the official runner's rules;</li>
 *   <li>for an answer case it builds the enriched snapshots of H1, H2 and H1+H2, expands the
 *       question of H3, and runs every candidate over the same corpus, so that the only
 *       difference between the rows is the candidate;</li>
 *   <li>it measures the retrieval metrics and classifies every baseline failure (H4).</li>
 * </ol>
 *
 * <p>The definitions of the candidates and of the metrics are frozen in
 * {@code docs-v2/engineering/C2A-CANDIDATE-FREEZE.md}.</p>
 */
public final class RetrievalExperiment implements AutoCloseable {

    /** The floor of the official baseline. */
    public static final double BASELINE_FLOOR = 0.25;

    /** The floor of the ranked candidate list the ranking metrics are measured on. */
    public static final double RANKING_FLOOR = 0.0;

    /** The name of the row that is the full production path. */
    public static final String BASELINE_PATH = "BASELINE-PATH";

    /** The name of the row that is the production retriever alone. */
    public static final String BASELINE_RETRIEVER = "BASELINE-RETRIEVER";

    /** The K of the recall metrics. */
    public static final List<Integer> RECALL_KS = List.of(1, 5, 10, 20);

    // ── the dataset ───────────────────────────────────────────────────────────

    /** One preparation step of a scope. */
    public record PrepareStep(String method, String path, String question, Integer documents,
            String file) {
    }

    /** One scope of the dataset. */
    public record ScopeSpec(String id, String workspace, String description,
            List<PrepareStep> prepare, List<String> cases) {
    }

    /** What a case expects. */
    public record Expect(Boolean answered, Integer minDistinctClaims, Integer minDistinctSources,
            String requiredSource, List<String> requiredPropositions,
            List<String> requiredPropositionsAny, List<String> requiredGapTerms, Boolean noClaims,
            Boolean noSources, Boolean statementEmpty, String verdict, Boolean promoted,
            Boolean noEvidence, String disputedPremise, List<String> reasonContains,
            Boolean claimsAddedGreaterThan, String revisionsApplied, List<String> requiredReasonAny,
            Boolean noClaimChangesStatus, String requiredClaimProposition,
            String requiredClaimStatus, Integer requiredClaimVersion,
            String requiredNewClaimProposition, String requiredNewClaimStatus,
            Integer requiredNewClaimVersion) {
    }

    /** One case of the dataset. */
    public record CaseSpec(String id, String category, String scope, String question,
            String assertion, String level, String callPath, String callMethod, String file,
            Expect expect) {
    }

    /** The dataset, as it was read. */
    public record Dataset(String dataset, String version, Map<String, ScopeSpec> scopes,
            List<CaseSpec> cases) {
    }

    // ── the results ───────────────────────────────────────────────────────────

    /** What one candidate did on one case. */
    public record CaseResult(String caseId, String category, String scope, String workspace,
            String candidate, boolean passed, List<String> problems, String observed,
            boolean answered, int citedClaims, int citedSources, List<String> citedPropositions,
            List<String> citedSourceTitles, List<String> goldPropositions, int goldCited,
            boolean goldFullyCited, List<Integer> goldRanks, double recallAt1, double recallAt5,
            double recallAt10, double recallAt20, double mrr, long retrievalMillis,
            long rankingMillis, String abstentionReason, List<String> gaps,
            List<String> unmatchedTerms, String classification, String classificationEvidence,
            String expandedQuestion, int workspaceClaims,
            /** The definition rule this row was measured under ("" when it does not depend). */
            String rule,
            /** {@code ALL}, {@code ANY} or {@code NONE}: the case's own gold condition. */
            String goldCondition,
            /** Whether the answer met the case's own gold condition. */
            boolean goldConditionMet,
            /** The per-gold classification: one code per gold proposition of the case. */
            List<String> goldClassifications,
            /** Whether the top-ranked claim of the floor-0.0 list is a gold claim. */
            Boolean topRankIsGold,
            /** How many of the golds are claims of the workspace (the corpus of the scope). */
            int goldInWorkspace) {
    }

    /** The metrics of one candidate over the whole dataset. */
    public record CandidateMetrics(String candidate, int passed, int total, List<String> failedCases,
            double recallAt1, double recallAt5, double recallAt10, double recallAt20, double mrr,
            double supportedAnswerAccuracy, double abstentionAccuracy, double unsupportedAnswerRate,
            double meanNonGoldCited, int casesWhoseTopRankIsNotGold, long meanRetrievalMillis,
            long totalRetrievalMillis, double overheadMillisPerCase, int measuredCases,
            /** The GLOBAL (micro) share of the golds found within the top K. */
            double microRecallAt1, double microRecallAt5, double microRecallAt10,
            double microRecallAt20,
            /** The share of the golds the answer cites, averaged over the answerable cases. */
            double goldAvailability,
            /** The unsupported-answer rate over ALL the ask cases. */
            double unsupportedOverAllAsk, int askCases,
            /** The counts behind the rates, so that a reader can recompute them. */
            long supportedCount, long unsupportedCount, long zeroGoldCitedCount,
            int topRankNotGoldDenominator) {
    }

    /** What a candidate touched: the scale of its effect. */
    public record CandidateScale(String candidate, int conceptsTouched, int claimsTouched,
            int definitionsApplied, int relatedTextsApplied, int relatedConceptNamesApplied,
            int questionTermsAdded, int conceptsInWorkspace, int claimsInWorkspace,
            int relationsInScope) {
    }

    /** What the preparation of a scope did. */
    public record ScopeReport(String scope, String workspace, int preparations, int documentsAcquired,
            int claimsAdded, int claimsInWorkspace, int conceptsInWorkspace, String note) {
    }

    /**
     * A gold claim that a candidate lost, and the coverage floor at which it enters the answer
     * under the baseline and under the candidate.
     *
     * <p>The floor is measured with the production retriever alone: the retriever is built at a
     * floor {@code f} and the claim is looked for in the answer's evidence. The bracket is the
     * smallest floor at which the claim is cited.</p>
     */
    public record CoverageShift(String caseId, String candidate, String proposition,
            double baselineFloor, double candidateFloor, int baselineCited,
            int candidateCited) {
    }

    /** The extraction of one case, kept whole so that the report can show its provenance. */
    public record ExtractionRecord(String caseId, String scope,
            SourceGroundedEnrichment.Extraction extraction) {
    }

    /** Everything the experiment produced, for ONE definition rule. */
    public record RuleRun(String rule,
            List<CaseResult> cases,
            Map<String, CandidateMetrics> metrics,
            Map<String, Map<String, CandidateScale>> scale,
            List<CoverageShift> coverageShifts,
            List<ExtractionRecord> extractions,
            Map<String, Map<String, Object>> extractionByScope,
            long extractionMillis,
            long retainedExtractionBytes) {
    }

    /** Everything the experiment produced. */
    public record Result(List<RuleRun> runs, Map<String, Map<String, CandidateMetrics>> metricsByRule,
            List<ScopeReport> scopes, List<String> officialFailures, List<String> baselineFailures,
            long preparationMillis, int answerableCases,
            int unanswerableCases) {
    }

    // ── the runner ────────────────────────────────────────────────────────────

    private final KnowledgeWorkbench workbench;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Integer> documentAcquired = new LinkedHashMap<>();
    private final Map<String, Integer> claimsAdded = new LinkedHashMap<>();
    private final Map<String, Integer> preparations = new LinkedHashMap<>();
    private final Map<String, List<CaseResult>> rowsByRule = new LinkedHashMap<>();
    private final Map<String, List<CoverageShift>> coverageShiftsByRule = new LinkedHashMap<>();
    private final Map<String, List<ExtractionRecord>> extractionsByRule = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> extractionByRule =
            new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, CandidateScale>>> scaleByRule =
            new LinkedHashMap<>();
    private final Map<String, Long> extractionMillisByRule = new LinkedHashMap<>();
    private final Map<String, Long> retainedBytesByRule = new LinkedHashMap<>();

    public RetrievalExperiment(Path databasePath) {
        this.workbench = KnowledgeWorkbench.at(databasePath.toString());
    }

    /** The rules the experiment measures: the current freeze and the one it supersedes. */
    public static List<SourceGroundedEnrichment.DefinitionRule> rules() {
        return List.of(SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1,
                SourceGroundedEnrichment.DefinitionRule.LOOSE_V1_0);
    }

    /** The dataset, read from the classpath. */
    public Dataset dataset() throws IOException {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("epistemic-eval/cases.json")) {
            if (stream == null) {
                throw new IOException("the evaluation dataset is not on the classpath");
            }
            JsonNode root = mapper.readTree(stream);
            Map<String, ScopeSpec> scopes = new LinkedHashMap<>();
            JsonNode scopeNodes = root.get("scopes");
            for (java.util.Iterator<String> it = scopeNodes.fieldNames(); it.hasNext();) {
                String id = it.next();
                JsonNode node = scopeNodes.get(id);
                List<PrepareStep> steps = new ArrayList<>();
                for (JsonNode step : node.get("prepare")) {
                    JsonNode query = step.get("query");
                    steps.add(new PrepareStep(text(step, "method"), text(step, "path"),
                            query == null ? null : text(query, "question"),
                            query == null ? null : (query.has("documents")
                                    ? query.get("documents").asInt() : null),
                            query == null ? null : text(query, "file")));
                }
                List<String> caseIds = new ArrayList<>();
                for (JsonNode idNode : node.get("cases")) {
                    caseIds.add(idNode.asText());
                }
                scopes.put(id, new ScopeSpec(id, text(node, "workspace"), text(node, "description"),
                        steps, caseIds));
            }
            List<CaseSpec> cases = new ArrayList<>();
            for (JsonNode node : root.get("cases")) {
                JsonNode call = node.get("call");
                JsonNode body = call.get("body");
                JsonNode query = call.get("query");
                cases.add(new CaseSpec(text(node, "id"), text(node, "category"),
                        text(node, "scope"), text(node, "question"), text(node, "assertion"),
                        body == null ? null : text(body, "level"), text(call, "path"),
                        text(call, "method"),
                        query == null ? null : text(query, "file"), expect(node.get("expect"))));
            }
            return new Dataset(text(root, "dataset"), text(root, "version"), scopes, cases);
        }
    }

    private static Expect expect(JsonNode node) {
        if (node == null) {
            return null;
        }
        return new Expect(bool(node, "answered"), integer(node, "minDistinctClaims"),
                integer(node, "minDistinctSources"), text(node, "requiredSource"),
                strings(node, "requiredPropositions"), strings(node, "requiredPropositionsAny"),
                strings(node, "requiredGapTerms"), bool(node, "noClaims"), bool(node, "noSources"),
                bool(node, "statementEmpty"), text(node, "verdict"), bool(node, "promoted"),
                bool(node, "noEvidence"), text(node, "disputedPremise"), strings(node, "reasonContains"),
                bool(node, "claimsAddedGreaterThan"), text(node, "revisionsApplied"),
                strings(node, "requiredReasonAny"), bool(node, "noClaimChangesStatus"),
                text(node.get("requiredClaim"), "proposition"), text(node.get("requiredClaim"), "status"),
                integer(node.get("requiredClaim"), "version"),
                text(node.get("requiredNewClaim"), "proposition"),
                text(node.get("requiredNewClaim"), "status"),
                integer(node.get("requiredNewClaim"), "version"));
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static Boolean bool(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static List<String> strings(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        if (node == null) {
            return values;
        }
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            return values;
        }
        for (JsonNode item : array) {
            values.add(item.asText());
        }
        return values;
    }

    /**
     * Runs the whole experiment.
     *
     * <p>Both definition rules are measured in ONE pass over the cases: the workspace is
     * prepared once, and each answer case is measured under the rule of the current freeze
     * (v1.1, the tight rule) and under the rule of the freeze it supersedes (v1.0, the loose
     * rule). The two runs share the corpus and the moment, so that the only difference between
     * the two sets of rows is the definition rule.</p>
     *
     * @return every number the report needs
     * @throws IOException when the dataset or the corpus cannot be read
     */
    public Result run() throws IOException {
        Dataset dataset = dataset();
        long preparationMillis = 0L;
        for (SourceGroundedEnrichment.DefinitionRule rule : rules()) {
            rowsByRule.put(rule.name(), new ArrayList<>());
        }

        for (String scopeId : dataset.scopes().keySet()) {
            ScopeSpec scope = dataset.scopes().get(scopeId);
            for (String caseId : scope.cases()) {
                CaseSpec caseSpec = caseById(dataset, caseId);
                long prepared = System.nanoTime();
                prepare(scope);
                preparationMillis += (System.nanoTime() - prepared) / 1_000_000L;

                if (caseSpec.callPath() != null && caseSpec.callPath().endsWith("/revise")) {
                    CaseResult row = runRevision(caseSpec, scope);
                    for (List<CaseResult> list : rowsByRule.values()) {
                        list.add(row);
                    }
                    continue;
                }
                if (caseSpec.callPath() != null && caseSpec.callPath().endsWith("/assess")) {
                    CaseResult row = runPremise(caseSpec, scope);
                    for (List<CaseResult> list : rowsByRule.values()) {
                        list.add(row);
                    }
                    continue;
                }
                // AN ANSWER CASE.
                AbstractionLevel level = levelOf(caseSpec.level());
                long pathStarted = System.nanoTime();
                Answer baseline = workbench.ask(scope.workspace(), caseSpec.question(), level);
                long pathMillis = (System.nanoTime() - pathStarted) / 1_000_000L;
                KnowledgeSnapshot snapshot = workbench.workspace(scope.workspace());

                for (SourceGroundedEnrichment.DefinitionRule rule : rules()) {
                    List<CaseResult> list = rowsByRule.get(rule.name());
                    long extraction = System.nanoTime();
                    SourceGroundedEnrichment.Extraction enrichment =
                            SourceGroundedEnrichment.extract(snapshot, rule);
                    extractionMillisByRule.merge(rule.name(),
                            (System.nanoTime() - extraction) / 1_000_000L, Long::sum);
                    retainedBytesByRule.merge(rule.name(), enrichment.retainedBytes(), Long::sum);

                    CaseResult baselineRow = answerRow(caseSpec, scope, BASELINE_PATH, baseline,
                            snapshot, pathMillis, rule);
                    list.add(baselineRow);

                    EnrichmentCandidates.EnrichedSnapshot h1 =
                            EnrichmentCandidates.applyH1(snapshot, enrichment);
                    EnrichmentCandidates.EnrichedSnapshot h2 =
                            EnrichmentCandidates.applyH2(snapshot, enrichment);
                    EnrichmentCandidates.EnrichedSnapshot h12 =
                            EnrichmentCandidates.applyH1AndH2(snapshot, enrichment);
                    String expanded = EnrichmentCandidates.expandQuestion(caseSpec.question(),
                            snapshot, enrichment);

                    list.add(candidateRow(caseSpec, scope, BASELINE_RETRIEVER, snapshot,
                            caseSpec.question(), level, rule));
                    list.add(candidateRow(caseSpec, scope, EnrichmentCandidates.H1, h1.snapshot(),
                            caseSpec.question(), level, rule));
                    list.add(candidateRow(caseSpec, scope, EnrichmentCandidates.H2, h2.snapshot(),
                            caseSpec.question(), level, rule));
                    list.add(candidateRow(caseSpec, scope, EnrichmentCandidates.H3, snapshot,
                            expanded, level, rule));
                    list.add(candidateRow(caseSpec, scope, EnrichmentCandidates.H1_AND_H2,
                            h12.snapshot(), caseSpec.question(), level, rule));

                    // THE COVERAGE SHIFTS: the gold claims a candidate lost.
                    coverageShiftsByRule.computeIfAbsent(rule.name(), key -> new ArrayList<>())
                            .addAll(coverageShifts(caseSpec, baselineRow, list, snapshot,
                                    h1.snapshot(), h2.snapshot(), h12.snapshot(), expanded, level));

                    extractionByRule.computeIfAbsent(rule.name(), key -> new LinkedHashMap<>())
                            .put(caseSpec.id(), extractionSummary(snapshot, enrichment));
                    extractionsByRule.computeIfAbsent(rule.name(), key -> new ArrayList<>())
                            .add(new ExtractionRecord(caseSpec.id(), scope.id(), enrichment));
                    scaleByRule.computeIfAbsent(rule.name(), key -> new LinkedHashMap<>())
                            .put(caseSpec.id(), scaleOf(snapshot, h1, h2, h12,
                                    EnrichmentCandidates.expansionTerms(caseSpec.question(),
                                            snapshot, enrichment).size(), enrichment));
                }
            }
        }

        Map<String, Map<String, CandidateMetrics>> metricsByRule = new LinkedHashMap<>();
        List<RuleRun> runs = new ArrayList<>();
        List<String> baselineFailures = List.of();
        for (SourceGroundedEnrichment.DefinitionRule rule : rules()) {
            List<CaseResult> rows = rowsByRule.get(rule.name());
            Map<String, CandidateMetrics> metrics = withOverhead(metrics(rows, dataset));
            metricsByRule.put(rule.name(), metrics);
            if (baselineFailures.isEmpty()) {
                baselineFailures = rows.stream()
                        .filter(result -> result.candidate().equals(BASELINE_PATH) && !result.passed())
                        .map(CaseResult::caseId).toList();
            }
            runs.add(new RuleRun(rule.name(), rows, metrics,
                    scaleByRule.getOrDefault(rule.name(), Map.of()),
                    coverageShiftsByRule.getOrDefault(rule.name(), List.of()),
                    extractionsByRule.getOrDefault(rule.name(), List.of()),
                    extractionByRule.getOrDefault(rule.name(), Map.of()),
                    extractionMillisByRule.getOrDefault(rule.name(), 0L),
                    retainedBytesByRule.getOrDefault(rule.name(), 0L)));
        }
        return new Result(runs, metricsByRule, scopeReports(dataset), officialFailures(),
                baselineFailures, preparationMillis, countAnswerable(dataset),
                countUnanswerable(dataset));
    }

    /**
     * The scale of every candidate on one case: what it touched.
     *
     * @param snapshot the workspace of the case
     * @param h1 the H1 snapshot
     * @param h2 the H2 snapshot
     * @param h12 the H1+H2 snapshot
     * @param questionTermsAdded the terms the H3 expansion added to the question
     * @return the scale of every candidate, by candidate name
     */
    private static Map<String, CandidateScale> scaleOf(KnowledgeSnapshot snapshot,
            EnrichmentCandidates.EnrichedSnapshot h1, EnrichmentCandidates.EnrichedSnapshot h2,
            EnrichmentCandidates.EnrichedSnapshot h12, int questionTermsAdded,
            SourceGroundedEnrichment.Extraction extraction) {
        int concepts = snapshot.concepts().size();
        int claims = snapshot.claims().size();
        int relations = extraction.relations().size();
        Map<String, CandidateScale> scale = new LinkedHashMap<>();
        scale.put(EnrichmentCandidates.H1, new CandidateScale(EnrichmentCandidates.H1,
                h1.conceptsChanged(), h1.claimsChanged(), h1.definitionsApplied(),
                h1.relatedTextsApplied(), h1.relatedConceptNamesApplied(), 0, concepts, claims,
                relations));
        scale.put(EnrichmentCandidates.H2, new CandidateScale(EnrichmentCandidates.H2,
                h2.conceptsChanged(), h2.claimsChanged(), h2.definitionsApplied(),
                h2.relatedTextsApplied(), h2.relatedConceptNamesApplied(), 0, concepts, claims,
                relations));
        scale.put(EnrichmentCandidates.H3, new CandidateScale(EnrichmentCandidates.H3,
                0, 0, 0, 0, 0, questionTermsAdded, concepts, claims, relations));
        scale.put(EnrichmentCandidates.H1_AND_H2, new CandidateScale(EnrichmentCandidates.H1_AND_H2,
                h12.conceptsChanged(), h12.claimsChanged(), h12.definitionsApplied(),
                h12.relatedTextsApplied(), h12.relatedConceptNamesApplied(), 0, concepts, claims,
                relations));
        return scale;
    }

    /**
     * The gold claims a candidate lost, and the coverage floor at which each of them enters the
     * answer under the baseline and under the candidate.
     */
    private List<CoverageShift> coverageShifts(CaseSpec caseSpec, CaseResult baselineRow,
            List<CaseResult> rows, KnowledgeSnapshot baseline, KnowledgeSnapshot h1,
            KnowledgeSnapshot h2, KnowledgeSnapshot h12, String expanded, AbstractionLevel level) {
        List<CoverageShift> shifts = new ArrayList<>();
        if (baselineRow.goldPropositions().isEmpty()) {
            return shifts;
        }
        Map<String, KnowledgeSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put(EnrichmentCandidates.H1, h1);
        snapshots.put(EnrichmentCandidates.H2, h2);
        snapshots.put(EnrichmentCandidates.H1_AND_H2, h12);
        // THE QUESTION-SIDE CANDIDATE: the same measurement over the UNMODIFIED snapshot and
        // with the EXPANDED question. The development run of v1.0 did not include it, because
        // the method took an `expanded` parameter its body never used.
        snapshots.put(EnrichmentCandidates.H3, baseline);
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put(EnrichmentCandidates.H1, caseSpec.question());
        questions.put(EnrichmentCandidates.H2, caseSpec.question());
        questions.put(EnrichmentCandidates.H1_AND_H2, caseSpec.question());
        questions.put(EnrichmentCandidates.H3, expanded);
        for (Map.Entry<String, KnowledgeSnapshot> entry : snapshots.entrySet()) {
            for (String proposition : baselineRow.goldPropositions()) {
                boolean citedByBaseline = baselineRow.citedPropositions().stream()
                        .anyMatch(text -> normalise(text).equals(normalise(proposition)));
                if (!citedByBaseline) {
                    continue;
                }
                boolean citedByCandidate = false;
                for (RetrievalExperiment.CaseResult row : resultsFor(rows, caseSpec.id(),
                        entry.getKey())) {
                    citedByCandidate = row.citedPropositions().stream()
                            .anyMatch(text -> normalise(text).equals(normalise(proposition)));
                }
                if (citedByCandidate) {
                    continue;
                }
                shifts.add(new CoverageShift(caseSpec.id(), entry.getKey(), proposition,
                        coverageFloor(baseline, proposition, caseSpec.question(), level),
                        coverageFloor(entry.getValue(), proposition,
                                questions.get(entry.getKey()), level),
                        baselineRow.citedClaims(), 0));
            }
        }
        return shifts;
    }

    private static List<CaseResult> resultsFor(List<CaseResult> rows, String caseId,
            String candidate) {
        List<CaseResult> found = new ArrayList<>();
        for (CaseResult row : rows) {
            if (row.caseId().equals(caseId) && row.candidate().equals(candidate)) {
                found.add(row);
            }
        }
        return found;
    }

    /**
     * The coverage floor at which a claim enters the answer's evidence set.
     *
     * <p>Measured with the production retriever alone: the floors are scanned from 0.00 upwards
     * in steps of 0.01 and the HIGHEST floor at which the claim is still cited is the claim's own
     * coverage — the coverage is the ratio the retriever divides by, and the claim is cited
     * exactly while the floor is at or below it. The representations are removed for the
     * measurement, because the retriever's concept BRIDGE adds the claims of a concept whose
     * representation covers enough of the question, and that would let a claim be cited at a
     * floor its own coverage never reached. The value is the upper bound of a 0.01-wide
     * bracket.</p>
     */
    public static double coverageFloor(KnowledgeSnapshot snapshot, String proposition,
            String question, AbstractionLevel level) {
        KnowledgeSnapshot measured = withoutRepresentations(snapshot);
        double lastCited = -1.0;
        for (int step = 0; step <= 100; step++) {
            double floor = step / 100.0;
            Answer answer = new KnowledgeRetriever(measured, floor).answer(question, level);
            boolean cited = answer.claims().stream()
                    .anyMatch(claim -> normalise(claim.proposition()).equals(normalise(proposition)));
            if (!cited) {
                break;
            }
            lastCited = floor;
        }
        if (lastCited < 0.0) {
            return Double.NaN;
        }
        return Math.round((lastCited + 0.01) * 100.0) / 100.0;
    }

    /**
     * The bracket the coverage floor stands for.
     *
     * <p>The floors are scanned from 0.00 upwards in steps of 0.01, so the measurement
     * resolves the floor only to the step it was run with: the value is the UPPER bound of the
     * bracket, and the bracket is the interval the scan can distinguish. A reader must see the
     * interval, not a four-decimal point that pretends to be exact.</p>
     *
     * @param upperBound the upper bound of the bracket
     * @return the interval, as it is printed
     */
    public static String coverageInterval(double upperBound) {
        if (Double.isNaN(upperBound)) {
            return "not reachable at any floor";
        }
        double low = Math.round((upperBound - 0.01) * 100.0) / 100.0;
        return "(" + String.format(Locale.ROOT, "%.2f", low) + ", "
                + String.format(Locale.ROOT, "%.2f", upperBound) + "]";
    }

    /**
     * The same workspace without its representations.
     *
     * <p>The retriever's concept BRIDGE adds the claims of a concept whose REPRESENTATION
     * covers enough of the question. With the representations in place, a claim can therefore
     * be cited at a floor its own coverage never reached, and the measurement would read the
     * bridge instead of the claim. Removing the representations leaves exactly one path to the
     * evidence: the claim's own coverage.</p>
     */
    public static KnowledgeSnapshot withoutRepresentations(KnowledgeSnapshot snapshot) {
        return new KnowledgeSnapshot(snapshot.workspaceId(), snapshot.workspaceName(),
                snapshot.concepts(), snapshot.sources(), snapshot.claims(), snapshot.evidence(),
                List.of(), snapshot.prerequisites(), snapshot.revisions(), snapshot.derivations(),
                snapshot.contradictions(), snapshot.goals(), snapshot.exportedAt());
    }

    /** The overhead of every candidate against the baseline retriever. */
    private static Map<String, CandidateMetrics> withOverhead(
            Map<String, CandidateMetrics> metrics) {
        CandidateMetrics baseline = metrics.get(BASELINE_RETRIEVER);
        double baselineMean = baseline == null ? 0.0 : baseline.meanRetrievalMillis();
        Map<String, CandidateMetrics> result = new LinkedHashMap<>();
        for (Map.Entry<String, CandidateMetrics> entry : metrics.entrySet()) {
            CandidateMetrics value = entry.getValue();
            result.put(entry.getKey(), new CandidateMetrics(value.candidate(), value.passed(),
                    value.total(), value.failedCases(), value.recallAt1(), value.recallAt5(),
                    value.recallAt10(), value.recallAt20(), value.mrr(),
                    value.supportedAnswerAccuracy(), value.abstentionAccuracy(),
                    value.unsupportedAnswerRate(), value.meanNonGoldCited(),
                    value.casesWhoseTopRankIsNotGold(), value.meanRetrievalMillis(),
                    value.totalRetrievalMillis(), value.meanRetrievalMillis() - baselineMean,
                    value.measuredCases(), value.microRecallAt1(), value.microRecallAt5(),
                    value.microRecallAt10(), value.microRecallAt20(), value.goldAvailability(),
                    value.unsupportedOverAllAsk(), value.askCases(), value.supportedCount(),
                    value.unsupportedCount(), value.zeroGoldCitedCount(),
                    value.topRankNotGoldDenominator()));
        }
        return result;
    }

    /** The eleven failures of the official 18/29 result. */
    public static List<String> officialFailures() {
        return List.of("A-02", "A-04", "A-05", "A-06", "F-02", "M-01", "P-01", "P-02", "M-03",
                "R-04", "R-05");
    }

    private CaseSpec caseById(Dataset dataset, String caseId) {
        for (CaseSpec caseSpec : dataset.cases()) {
            if (caseSpec.id().equals(caseId)) {
                return caseSpec;
            }
        }
        throw new IllegalStateException("the dataset does not hold the case " + caseId);
    }

    private static AbstractionLevel levelOf(String level) {
        if (level == null) {
            return AbstractionLevel.INTRODUCTORY;
        }
        for (AbstractionLevel candidate : AbstractionLevel.values()) {
            if (candidate.name().equalsIgnoreCase(level)) {
                return candidate;
            }
        }
        return AbstractionLevel.INTRODUCTORY;
    }

    // ── the preparation ───────────────────────────────────────────────────────

    /**
     * Prepares a scope with the production methods behind the HTTP calls of the dataset.
     */
    private void prepare(ScopeSpec scope) throws IOException {
        preparations.merge(scope.id(), 1, Integer::sum);
        for (PrepareStep step : scope.prepare()) {
            if (step.path() == null) {
                continue;
            }
            if (step.path().endsWith("/seed")) {
                AcquisitionCycle.Report report =
                        AcquisitionCycle.loadSeed(workbench, scope.workspace());
                record(scope.id(), report.documentsAcquired(), report.claimsAdded());
            } else if (step.path().endsWith("/learn")) {
                AcquisitionCycle.Report report = AcquisitionCycle.run(workbench, scope.workspace(),
                        step.question(), step.documents() == null ? 3 : step.documents());
                record(scope.id(), report.documentsAcquired(), report.claimsAdded());
            } else if (step.path().endsWith("/revise")) {
                AcquisitionCycle.RevisionReport report = AcquisitionCycle.acquireAndRevise(workbench,
                        scope.workspace(), step.file());
                record(scope.id(), 1, report.claimsAdded());
            } else {
                throw new IllegalStateException("the dataset names a preparation call this "
                        + "experiment does not know: " + step.path());
            }
        }
    }

    private void record(String scopeId, int documents, int claims) {
        documentAcquired.merge(scopeId, documents, Integer::sum);
        claimsAdded.merge(scopeId, claims, Integer::sum);
    }

    private List<ScopeReport> scopeReports(Dataset dataset) {
        List<ScopeReport> reports = new ArrayList<>();
        for (ScopeSpec scope : dataset.scopes().values()) {
            KnowledgeSnapshot snapshot = workbench.workspace(scope.workspace());
            reports.add(new ScopeReport(scope.id(), scope.workspace(),
                    preparations.getOrDefault(scope.id(), 0),
                    documentAcquired.getOrDefault(scope.id(), 0),
                    claimsAdded.getOrDefault(scope.id(), 0), snapshot.claims().size(),
                    snapshot.concepts().size(), scope.description()));
        }
        return reports;
    }

    // ── the rows ──────────────────────────────────────────────────────────────

    /** The row of the full production path. */
    private CaseResult answerRow(CaseSpec caseSpec, ScopeSpec scope, String candidate,
            Answer answer, KnowledgeSnapshot snapshot, long pathMillis,
            SourceGroundedEnrichment.DefinitionRule rule) {
        List<String> problems = new ArrayList<>();
        boolean passed = true;
        if (caseSpec.expect() != null) {
            problems.addAll(judgeAnswer(caseSpec.expect(), answer));
            passed = problems.isEmpty();
        }
        Answer ranking = new KnowledgeRetriever(snapshot, RANKING_FLOOR)
                .answer(caseSpec.question(), answer.level());
        return measure(caseSpec, scope, candidate, answer, snapshot, ranking, problems, passed,
                null, pathMillis, 0L, rule);
    }

    /** The row of a candidate: a fresh production retriever over the enriched snapshot. */
    private CaseResult candidateRow(CaseSpec caseSpec, ScopeSpec scope, String candidate,
            KnowledgeSnapshot snapshot, String question, AbstractionLevel level,
            SourceGroundedEnrichment.DefinitionRule rule) {
        long retrievalStarted = System.nanoTime();
        Answer answer = new KnowledgeRetriever(snapshot, BASELINE_FLOOR).answer(question, level);
        long retrievalMillis = (System.nanoTime() - retrievalStarted) / 1_000_000L;
        long rankingStarted = System.nanoTime();
        Answer ranking = new KnowledgeRetriever(snapshot, RANKING_FLOOR).answer(question, level);
        long rankingMillis = (System.nanoTime() - rankingStarted) / 1_000_000L;

        List<String> problems = new ArrayList<>();
        boolean passed = true;
        if (caseSpec.expect() != null) {
            problems.addAll(judgeAnswer(caseSpec.expect(), answer));
            passed = problems.isEmpty();
        }
        return measure(caseSpec, scope, candidate, answer, snapshot, ranking, problems, passed,
                EnrichmentCandidates.H3.equals(candidate) ? question : null, retrievalMillis,
                rankingMillis, rule);
    }

    /** Measures one answer against the case's gold. */
    private CaseResult measure(CaseSpec caseSpec, ScopeSpec scope, String candidate, Answer answer,
            KnowledgeSnapshot snapshot, Answer ranking, List<String> problems, boolean passed,
            String expandedQuestion, long pathMillis, long rankingMillis,
            SourceGroundedEnrichment.DefinitionRule rule) {
        List<String> cited = new ArrayList<>();
        Set<String> citedIds = new LinkedHashSet<>();
        for (Claim claim : answer.claims()) {
            cited.add(claim.proposition());
            citedIds.add(claim.id());
        }
        List<String> sourceTitles = new ArrayList<>();
        Set<String> sourceIds = new LinkedHashSet<>();
        for (Source source : answer.sources()) {
            sourceTitles.add(source.title());
            sourceIds.add(source.id());
        }
        List<String> gold = goldOf(caseSpec);
        int goldCited = 0;
        int goldInWorkspace = 0;
        List<Integer> goldRanks = new ArrayList<>();
        for (String proposition : gold) {
            boolean citedNow = cited.stream().anyMatch(text -> normalise(text).equals(
                    normalise(proposition)));
            if (citedNow) {
                goldCited++;
            }
            boolean inWorkspace = false;
            for (Claim claim : snapshot.claims()) {
                if (normalise(claim.proposition()).equals(normalise(proposition))) {
                    inWorkspace = true;
                }
            }
            if (inWorkspace) {
                goldInWorkspace++;
            }
        }
        // THE RANKED CANDIDATE LIST: the production retriever at floor 0.0.
        List<String> ranked = new ArrayList<>();
        for (Claim claim : ranking.claims()) {
            ranked.add(claim.proposition());
        }
        for (String proposition : gold) {
            int rank = 0;
            for (int index = 0; index < ranked.size(); index++) {
                if (normalise(ranked.get(index)).equals(normalise(proposition))) {
                    rank = index + 1;
                    break;
                }
            }
            goldRanks.add(rank);
        }
        double recall1 = recallAt(gold, goldRanks, 1);
        double recall5 = recallAt(gold, goldRanks, 5);
        double recall10 = recallAt(gold, goldRanks, 10);
        double recall20 = recallAt(gold, goldRanks, 20);
        // THE MRR (the freeze §6): the reciprocal rank of the TOPMOST GOLD — the gold with the
        // SMALLEST rank in the floor-0.0 candidate list. A case whose gold is not in the list
        // contributes 0: the misses are in the denominator.
        double mrr = 0.0;
        int topmost = Integer.MAX_VALUE;
        for (Integer rank : goldRanks) {
            if (rank > 0 && rank < topmost) {
                topmost = rank;
            }
        }
        if (topmost != Integer.MAX_VALUE) {
            mrr = 1.0 / topmost;
        }
        // THE TOP-RANKED CLAIM (the freeze §6): the claim at rank 1 of the floor-0.0 list. The
        // false-positive metric asks whether THAT claim is a gold claim — not whether the first
        // gold of the case happens to sit at rank 1.
        Boolean topRankIsGold = null;
        if (!ranked.isEmpty()) {
            String top = normalise(ranked.get(0));
            boolean goldClaim = false;
            for (String proposition : gold) {
                if (normalise(proposition).equals(top)) {
                    goldClaim = true;
                }
            }
            topRankIsGold = goldClaim;
        }
        // THE CASE'S OWN GOLD CONDITION: all of the required propositions, or at least one of
        // the alternative ones.
        String condition = goldConditionOf(caseSpec);
        boolean conditionMet = goldConditionMet(caseSpec, cited);
        String observed = "answered=" + answer.answered() + "; " + citedIds.size() + " claims; "
                + sourceIds.size() + " sources; gold " + goldCited + "/" + gold.size();
        Classification classification = BASELINE_PATH.equals(candidate) && !passed
                ? classify(caseSpec, answer, snapshot, ranking, problems)
                : new Classification("", "", List.of());
        return new CaseResult(caseSpec.id(), caseSpec.category(), scope.id(), scope.workspace(),
                candidate, passed, problems, observed, answer.answered(), citedIds.size(),
                sourceIds.size(), cited, sourceTitles, gold, goldCited, goldCited == gold.size()
                        && !gold.isEmpty(), goldRanks, recall1, recall5, recall10, recall20, mrr,
                pathMillis, rankingMillis, answer.abstentionReason(), answer.gaps(),
                answer.unmatchedTerms(), classification.code(), classification.evidence(),
                expandedQuestion, snapshot.claims().size(), rule == null ? "" : rule.name(),
                condition, conditionMet, classification.perGold(), topRankIsGold, goldInWorkspace);
    }

    // ── H4: the classification of a failure ───────────────────────────────────

    /** The code of a case whose golds carry different codes (the freeze v1.1 §5). */
    public static final String MIXED_GOLD_CODES = "MIXED_GOLD_CODES";

    /** The code of a gold the answer cited. */
    public static final String GOLD_REACHED = "GOLD_REACHED";

    /** The classification of a failure: one code for the case, one code per gold. */
    public record Classification(String code, String evidence, List<String> perGold) {
    }

    /**
     * The classification of a failing case, with the evidence it rests on.
     *
     * <p>The taxonomy is frozen in {@code C2A-CANDIDATE-FREEZE.md} §5 and, in the v1.1 version
     * of that freeze, the tree is walked <b>per gold proposition</b>: every gold of the case
     * gets its own code, and the case gets the code its golds agree on — or
     * {@link #MIXED_GOLD_CODES} when they do not agree.</p>
     *
     * <ol>
     *   <li>the answer abstained → the abstention code, read from the reason;</li>
     *   <li>the gold is cited → the gold is REACHED (it is not a defect of that gold);</li>
     *   <li>the gold is not cited: it is not in the workspace, or it is not in the floor-0.0
     *       candidate list, or the answer was about another subject (the gold's concept is not
     *       among the answer's concepts), or it was reachable but the floor kept it out;</li>
     *   <li>every gold is cited → the case failed on another condition
     *       ({@code GOLD_CITED_CASE_FAILED}); the golds carry different codes →
     *       {@link #MIXED_GOLD_CODES}.</li>
     * </ol>
     */
    private static Classification classify(CaseSpec caseSpec, Answer answer,
            KnowledgeSnapshot snapshot, Answer ranking, List<String> problems) {
        List<String> gold = goldOf(caseSpec);
        if (!answer.answered()) {
            String code = abstentionCode(answer.abstentionReason());
            List<String> perGold = new ArrayList<>();
            for (int index = 0; index < gold.size(); index++) {
                perGold.add(code);
            }
            return new Classification(code, "the answer abstained: " + answer.abstentionReason(),
                    perGold);
        }
        if (gold.isEmpty()) {
            return new Classification("NO_GOLD", "the case names no gold proposition", List.of());
        }
        Set<String> answeredConceptIds = new LinkedHashSet<>();
        for (Concept concept : answer.concepts()) {
            answeredConceptIds.add(concept.id());
        }
        List<String> perGold = new ArrayList<>();
        for (String proposition : gold) {
            boolean cited = false;
            for (Claim claim : answer.claims()) {
                if (normalise(claim.proposition()).equals(normalise(proposition))) {
                    cited = true;
                }
            }
            if (cited) {
                perGold.add(GOLD_REACHED);
                continue;
            }
            String conceptId = null;
            boolean inWorkspace = false;
            for (Claim claim : snapshot.claims()) {
                if (normalise(claim.proposition()).equals(normalise(proposition))) {
                    inWorkspace = true;
                    conceptId = claim.conceptId();
                }
            }
            if (!inWorkspace) {
                perGold.add("GOLD_NOT_IN_WORKSPACE");
                continue;
            }
            boolean inRanking = false;
            for (Claim claim : ranking.claims()) {
                if (normalise(claim.proposition()).equals(normalise(proposition))) {
                    inRanking = true;
                }
            }
            if (!inRanking) {
                perGold.add("NO_LEXICAL_MATCH");
                continue;
            }
            if (!answeredConceptIds.isEmpty() && conceptId != null
                    && !answeredConceptIds.contains(conceptId)) {
                perGold.add("WRONG_SCOPE");
                continue;
            }
            perGold.add("BELOW_FLOOR");
        }
        List<String> defects = new ArrayList<>();
        for (String code : perGold) {
            if (!GOLD_REACHED.equals(code)) {
                defects.add(code);
            }
        }
        if (defects.isEmpty()) {
            return new Classification("GOLD_CITED_CASE_FAILED",
                    "every gold is cited and the case failed on: " + problems, perGold);
        }
        Set<String> distinct = new LinkedHashSet<>(defects);
        if (distinct.size() == 1) {
            String code = distinct.iterator().next();
            return new Classification(code, evidenceOf(code, perGold), perGold);
        }
        return new Classification(MIXED_GOLD_CODES,
                "the golds of the case carry different codes: " + perGold, perGold);
    }

    /** The evidence of a classification, read off the per-gold codes. */
    private static String evidenceOf(String code, List<String> perGold) {
        if ("BELOW_FLOOR".equals(code)) {
            return "the gold is in the floor-0.0 candidate list but not in the floor-0.25 "
                    + "evidence set: every uncited gold of the case is reachable and below "
                    + "the floor (" + perGold + ")";
        }
        if ("NO_LEXICAL_MATCH".equals(code)) {
            return "the gold is in the workspace but shares no term with the question: it is not "
                    + "in the floor-0.0 candidate list either (" + perGold + ")";
        }
        if ("WRONG_SCOPE".equals(code)) {
            return "the gold is reachable but the answer was about another subject: the gold's "
                    + "concept is not among the answer's concepts (" + perGold + ")";
        }
        if ("GOLD_NOT_IN_WORKSPACE".equals(code)) {
            return "no claim of the workspace states the gold proposition (" + perGold + ")";
        }
        return perGold.toString();
    }

    /**
     * The abstention code, read from the abstention reason.
     *
     * @param reason the reason the answer gives
     * @return the code of the freeze §5
     */
    public static String abstentionCode(String reason) {
        String lowered = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (lowered.contains("premise")) {
            return "ABSTAINED_PREMISE";
        }
        if (lowered.contains("quantity") || lowered.contains("measured quantity")) {
            return "ABSTAINED_QUANTITY";
        }
        if (lowered.contains("has not happened")) {
            return "ABSTAINED_TIME_DEMAND";
        }
        if (lowered.contains("content terms")) {
            return "ABSTAINED_DEMAND";
        }
        if (lowered.contains("covers enough") || lowered.contains("established")) {
            return "ABSTAINED_NO_COVERAGE";
        }
        return "ABSTAINED_OTHER";
    }

    /**
     * The case's own gold condition: {@code ALL} when the case names the propositions it
     * requires, {@code ANY} when it names alternatives, {@code NONE} when it names neither.
     *
     * @param caseSpec the case
     * @return the name of the condition
     */
    public static String goldConditionOf(CaseSpec caseSpec) {
        if (caseSpec.expect() == null) {
            return "NONE";
        }
        if (!caseSpec.expect().requiredPropositions().isEmpty()) {
            return "ALL";
        }
        if (!caseSpec.expect().requiredPropositionsAny().isEmpty()) {
            return "ANY";
        }
        return "NONE";
    }

    /**
     * Whether the answer met the case's own gold condition.
     *
     * <p>ALL: every proposition of {@code requiredPropositions} is cited. ANY: at least one
     * proposition of {@code requiredPropositionsAny} is cited. NONE: nothing is required, so
     * the condition is met.</p>
     *
     * @param caseSpec the case
     * @param cited the propositions the answer cites
     * @return whether the condition is met
     */
    public static boolean goldConditionMet(CaseSpec caseSpec, List<String> cited) {
        if (caseSpec.expect() == null) {
            return true;
        }
        for (String proposition : caseSpec.expect().requiredPropositions()) {
            boolean found = false;
            for (String text : cited) {
                if (text.equals(proposition) || normalise(text).equals(normalise(proposition))) {
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        if (!caseSpec.expect().requiredPropositionsAny().isEmpty()) {
            boolean any = false;
            for (String proposition : caseSpec.expect().requiredPropositionsAny()) {
                for (String text : cited) {
                    if (text.equals(proposition)
                            || normalise(text).equals(normalise(proposition))) {
                        any = true;
                    }
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private static double recallAt(List<String> gold, List<Integer> ranks, int k) {
        if (gold.isEmpty()) {
            return 0.0;
        }
        int found = 0;
        for (Integer rank : ranks) {
            if (rank > 0 && rank <= k) {
                found++;
            }
        }
        return (double) found / gold.size();
    }

    /** The gold propositions of a case. */
    public static List<String> goldOf(CaseSpec caseSpec) {
        if (caseSpec.expect() == null) {
            return List.of();
        }
        List<String> gold = new ArrayList<>(caseSpec.expect().requiredPropositions());
        if (gold.isEmpty()) {
            gold.addAll(caseSpec.expect().requiredPropositionsAny());
        }
        return gold;
    }

    /** The comparison the official runner makes: whitespace collapsed, lower case. */
    public static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                .replaceAll("[.?!]+$", "");
    }

    // ── the judging (the official runner's rules) ─────────────────────────────

    /** The rules of {@code scripts/w51-epistemic-eval.py}: judge_answer. */
    public static List<String> judgeAnswer(Expect expect, Answer answer) {
        List<String> problems = new ArrayList<>();
        boolean answered = answer.answered();
        List<String> cited = new ArrayList<>();
        for (Claim claim : answer.claims()) {
            cited.add(claim.proposition());
        }
        Set<String> claimIds = new LinkedHashSet<>();
        for (Claim claim : answer.claims()) {
            claimIds.add(claim.id());
        }
        Set<String> sourceIds = new LinkedHashSet<>();
        Set<String> titles = new LinkedHashSet<>();
        for (Source source : answer.sources()) {
            sourceIds.add(source.id());
            titles.add(source.title());
        }
        if (expect.answered() != null && answered != expect.answered()) {
            problems.add("answered=" + answered + ", expected " + expect.answered());
        }
        if (Boolean.FALSE.equals(expect.answered())) {
            if (answer.statement() != null && !answer.statement().isBlank()) {
                problems.add("the statement is not empty");
            }
            if (!answer.sources().isEmpty()) {
                problems.add(answer.sources().size() + " source(s) still cited");
            }
            if (Boolean.TRUE.equals(expect.noClaims()) && !answer.claims().isEmpty()) {
                problems.add(answer.claims().size() + " claim(s) still cited");
            }
        }
        if (expect.minDistinctClaims() != null && claimIds.size() < expect.minDistinctClaims()) {
            problems.add("only " + claimIds.size() + " distinct claims (< "
                    + expect.minDistinctClaims() + ")");
        }
        if (expect.minDistinctSources() != null && sourceIds.size() < expect.minDistinctSources()) {
            problems.add("only " + sourceIds.size() + " distinct sources (< "
                    + expect.minDistinctSources() + ")");
        }
        if (expect.requiredSource() != null && !titles.contains(expect.requiredSource())) {
            problems.add("the source " + expect.requiredSource() + " is absent");
        }
        for (String proposition : expect.requiredPropositions()) {
            if (!cited.contains(proposition)) {
                problems.add("the proposition is not cited: " + shorten(proposition));
            }
        }
        if (!expect.requiredPropositionsAny().isEmpty()) {
            boolean any = false;
            for (String proposition : expect.requiredPropositionsAny()) {
                if (cited.contains(proposition)) {
                    any = true;
                }
            }
            if (!any) {
                problems.add("none of the named propositions is cited");
            }
        }
        Set<String> gapTerms = new LinkedHashSet<>();
        for (String gap : answer.gaps()) {
            for (String word : gap.replace(",", " ").split("\\s+")) {
                if (!word.isBlank()) {
                    gapTerms.add(word.toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String term : expect.requiredGapTerms()) {
            if (!namesTerm(gapTerms, term)) {
                problems.add("the gap list does not name '" + term + "' (gaps=" + gapTerms + ")");
            }
        }
        return problems;
    }

    /** The comparison the official runner makes for the gap terms: the singular form. */
    private static boolean namesTerm(Set<String> gapTerms, String term) {
        String wanted = stem(term);
        for (String gap : gapTerms) {
            if (stem(gap).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** The singular form of a word, as the official runner computes it. */
    public static String stem(String word) {
        String lowered = word.strip().toLowerCase(Locale.ROOT);
        if (lowered.endsWith("ies") && lowered.length() > 4) {
            return lowered.substring(0, lowered.length() - 3) + "y";
        }
        if (lowered.endsWith("es") && lowered.length() > 3) {
            return lowered.substring(0, lowered.length() - 2);
        }
        if (lowered.endsWith("s") && lowered.length() > 2) {
            return lowered.substring(0, lowered.length() - 1);
        }
        return lowered;
    }

    /** The rules of {@code scripts/w51-epistemic-eval.py}: judge_premise. */
    public static List<String> judgePremise(Expect expect,
            PremiseAssessment.Assessment assessment) {
        List<String> problems = new ArrayList<>();
        List<PremiseAssessment.Finding> findings = assessment.findings();
        String verdict = findings.isEmpty() ? null : findings.get(0).verdict().name();
        List<PremiseAssessment.EvidenceRef> evidence = new ArrayList<>();
        boolean disputed = false;
        for (PremiseAssessment.Finding finding : findings) {
            evidence.addAll(finding.evidence());
            disputed = disputed || (finding.disputedPremise() != null
                    && !finding.disputedPremise().isBlank());
        }
        if (!expect.verdict().equals(verdict)) {
            problems.add("the verdict is " + verdict + ", expected " + expect.verdict());
        }
        if (assessment.promoted()) {
            problems.add("promoted=true");
        }
        if (Boolean.TRUE.equals(expect.noEvidence()) && !evidence.isEmpty()) {
            problems.add("evidence is named where none is expected");
        }
        if ("named".equals(expect.disputedPremise()) && !disputed) {
            problems.add("the disputed premise is not named");
        }
        if (expect.requiredSource() != null) {
            boolean found = false;
            for (PremiseAssessment.EvidenceRef ref : evidence) {
                if (expect.requiredSource().equals(ref.sourceTitle())) {
                    found = true;
                }
            }
            if (!found) {
                problems.add("the source " + expect.requiredSource() + " is not named");
            }
        }
        for (String proposition : expect.requiredPropositions()) {
            boolean found = false;
            for (PremiseAssessment.EvidenceRef ref : evidence) {
                if (proposition.equals(ref.proposition())) {
                    found = true;
                }
            }
            if (!found) {
                problems.add("the claim is not named: " + shorten(proposition));
            }
        }
        for (String fragment : expect.reasonContains()) {
            boolean found = false;
            for (PremiseAssessment.Finding finding : findings) {
                if (finding.reason() != null && finding.reason().contains(fragment)) {
                    found = true;
                }
            }
            if (!found) {
                problems.add("the reason does not say '" + fragment + "'");
            }
        }
        return problems;
    }

    /** The rules of {@code scripts/w51-epistemic-eval.py}: judge_revision. */
    public static List<String> judgeRevision(Expect expect,
            AcquisitionCycle.RevisionReport report, List<Claim> before, List<Claim> after) {
        List<String> problems = new ArrayList<>();
        if (Boolean.TRUE.equals(expect.claimsAddedGreaterThan()) && report.claimsAdded() <= 0) {
            problems.add("the document was not acquired");
        }
        if ("none".equals(expect.revisionsApplied()) && report.revisionsApplied() != 0) {
            problems.add(report.revisionsApplied() + " supersessions applied, 0 expected");
        }
        if ("atLeastOne".equals(expect.revisionsApplied()) && report.revisionsApplied() < 1) {
            problems.add("no supersession applied");
        }
        if (!expect.requiredReasonAny().isEmpty()) {
            boolean found = false;
            for (ModelRevisionDecider.Decision decision : report.decisions()) {
                for (String fragment : expect.requiredReasonAny()) {
                    if (decision.reason() != null && decision.reason().contains(fragment)) {
                        found = true;
                    }
                }
            }
            if (!found) {
                problems.add("no decision carries the reason the case names");
            }
        }
        if (Boolean.TRUE.equals(expect.noClaimChangesStatus())) {
            Map<String, Claim> beforeById = new LinkedHashMap<>();
            for (Claim claim : before) {
                beforeById.put(claim.id(), claim);
            }
            List<String> changed = new ArrayList<>();
            for (Claim claim : after) {
                Claim earlier = beforeById.get(claim.id());
                if (earlier != null && (earlier.status() != claim.status()
                        || earlier.version() != claim.version())) {
                    changed.add(claim.id());
                }
            }
            if (!changed.isEmpty()) {
                problems.add(changed.size() + " claim(s) changed status or version: " + changed);
            }
        }
        problems.addAll(claimState(expect.requiredClaimProposition(), expect.requiredClaimStatus(),
                expect.requiredClaimVersion(), after, "the claim the case names"));
        problems.addAll(claimState(expect.requiredNewClaimProposition(),
                expect.requiredNewClaimStatus(), expect.requiredNewClaimVersion(), after,
                "the new claim the case names"));
        return problems;
    }

    private static List<String> claimState(String proposition, String status, Integer version,
            List<Claim> claims, String what) {
        List<String> problems = new ArrayList<>();
        if (proposition == null) {
            return problems;
        }
        Claim match = null;
        for (Claim claim : claims) {
            if (proposition.equals(claim.proposition())) {
                match = claim;
                break;
            }
        }
        if (match == null) {
            problems.add(what + " is not in the workspace");
            return problems;
        }
        if (status != null && !status.equals(match.status().name())) {
            problems.add(thatClaim(proposition) + " is " + match.status() + ", expected " + status);
        }
        if (version != null && match.version() != version) {
            problems.add(thatClaim(proposition) + " is at version " + match.version()
                    + ", expected " + version);
        }
        return problems;
    }

    private static String thatClaim(String proposition) {
        return "that claim (" + shorten(proposition) + ")";
    }

    /** A decision of the revision cycle, as the report carries it. */
    private static String shorten(String text) {
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }

    // ── the false-premise and revision rows ───────────────────────────────────

    private CaseResult runPremise(CaseSpec caseSpec, ScopeSpec scope) {
        PremiseAssessment.Assessment assessment = PremiseAssessment.assess(workbench,
                scope.workspace(), caseSpec.assertion());
        List<String> problems = new ArrayList<>();
        boolean passed = true;
        if (caseSpec.expect() != null) {
            problems.addAll(judgePremise(caseSpec.expect(), assessment));
            passed = problems.isEmpty();
        }
        String verdict = assessment.findings().isEmpty() ? "none"
                : assessment.findings().get(0).verdict().name();
        List<String> named = new ArrayList<>();
        for (PremiseAssessment.Finding finding : assessment.findings()) {
            for (PremiseAssessment.EvidenceRef ref : finding.evidence()) {
                named.add(ref.proposition());
            }
        }
        KnowledgeSnapshot snapshot = workbench.workspace(scope.workspace());
        String[] classification = new String[] {"", ""};
        if (!passed && caseSpec.expect() != null) {
            String verdictNow = assessment.findings().isEmpty() ? "none"
                    : assessment.findings().get(0).verdict().name();
            if (!caseSpec.expect().verdict().equals(verdictNow)) {
                classification = new String[] {"FALSE_PREMISE_MISMATCH",
                        "the verdict is " + verdictNow + ", expected " + caseSpec.expect().verdict()};
            } else {
                classification = new String[] {"FALSE_PREMISE_EVIDENCE_MISSING",
                        "the verdict is " + verdictNow + " and the case failed on: " + problems};
            }
        }
        return new CaseResult(caseSpec.id(), caseSpec.category(), scope.id(), scope.workspace(),
                BASELINE_PATH, passed, problems, "verdict=" + verdict + "; promoted="
                        + assessment.promoted() + "; " + named.size() + " evidence ref(s)", false,
                0, 0, named, List.of(), List.of(), 0, false, List.of(), 0, 0, 0, 0, 0, 0L, 0L,
                null, List.of(), List.of(), classification[0], classification[1], null,
                snapshot.claims().size(), "", goldConditionOf(caseSpec), false, List.of(), null,
                0);
    }

    private CaseResult runRevision(CaseSpec caseSpec, ScopeSpec scope) throws IOException {
        List<Claim> before = workbench.workspace(scope.workspace()).claims();
        AcquisitionCycle.RevisionReport report = AcquisitionCycle.acquireAndRevise(workbench,
                scope.workspace(), caseSpec.file());
        List<Claim> after = workbench.workspace(scope.workspace()).claims();
        List<String> problems = new ArrayList<>();
        boolean passed = true;
        if (caseSpec.expect() != null) {
            problems.addAll(judgeRevision(caseSpec.expect(), report, before, after));
            passed = problems.isEmpty();
        }
        List<String> reasons = new ArrayList<>();
        for (ModelRevisionDecider.Decision decision : report.decisions()) {
            reasons.add(decision.reason());
        }
        String[] classification = new String[] {"", ""};
        if (!passed && caseSpec.expect() != null) {
            boolean appliedWrong = ("none".equals(caseSpec.expect().revisionsApplied())
                    && report.revisionsApplied() != 0)
                    || ("atLeastOne".equals(caseSpec.expect().revisionsApplied())
                            && report.revisionsApplied() < 1);
            if (appliedWrong) {
                classification = new String[] {"REVISION_NOT_APPLIED",
                        report.revisionsApplied() + " supersession(s) applied, the case expects "
                                + caseSpec.expect().revisionsApplied()};
            } else {
                classification = new String[] {"REVISION_REASON_MISSING",
                        "the revision was applied and the case failed on: " + problems};
            }
        }
        return new CaseResult(caseSpec.id(), caseSpec.category(), scope.id(), scope.workspace(),
                BASELINE_PATH, passed, problems, "claimsAdded=" + report.claimsAdded() + "; "
                        + report.decisions().size() + " decisions; revisionsApplied="
                        + report.revisionsApplied(), false, 0, 0, List.of(), List.of(), List.of(),
                0, false, List.of(), 0, 0, 0, 0, 0, 0L, 0L, null, List.of(), List.of(),
                classification[0], classification[1], null, after.size(), "",
                goldConditionOf(caseSpec), false, List.of(), null, 0);
    }

    /** The decision of the revision cycle, as the report carries it. */
    public record ModelRevisionDecision(String reason) {
    }

    // ── the metrics ───────────────────────────────────────────────────────────

    /** The metrics of every candidate. */
    public static Map<String, CandidateMetrics> metrics(List<CaseResult> results,
            Dataset dataset) {
        Map<String, CandidateMetrics> metrics = new LinkedHashMap<>();
        List<String> candidates = new ArrayList<>();
        for (CaseResult result : results) {
            if (!candidates.contains(result.candidate())) {
                candidates.add(result.candidate());
            }
        }
        for (String candidate : candidates) {
            List<CaseResult> rows = results.stream()
                    .filter(result -> result.candidate().equals(candidate)).toList();
            List<String> failures = rows.stream().filter(row -> !row.passed())
                    .map(CaseResult::caseId).toList();
            List<CaseResult> answerable = rows.stream()
                    .filter(row -> isAnswerable(row.caseId(), dataset)).toList();
            List<CaseResult> unanswerable = rows.stream()
                    .filter(row -> isUnanswerable(row.caseId(), dataset)).toList();
            // THE ASK CASES: the cases the system is asked a question at — the answerable ones
            // and the unanswerable ones. The false-premise and the revision cases are not ask
            // cases.
            List<CaseResult> ask = new ArrayList<>(answerable);
            ask.addAll(unanswerable);
            double recall1 = mean(answerable.stream().mapToDouble(CaseResult::recallAt1).toArray());
            double recall5 = mean(answerable.stream().mapToDouble(CaseResult::recallAt5).toArray());
            double recall10 = mean(answerable.stream().mapToDouble(CaseResult::recallAt10).toArray());
            double recall20 = mean(answerable.stream().mapToDouble(CaseResult::recallAt20).toArray());
            double mrr = mean(answerable.stream().mapToDouble(CaseResult::mrr).toArray());
            // THE GLOBAL (MICRO) SHARE: every gold proposition of every answerable case in the
            // denominator, not the mean of the per-case shares.
            double micro1 = microRecall(answerable, 1);
            double micro5 = microRecall(answerable, 5);
            double micro10 = microRecall(answerable, 10);
            double micro20 = microRecall(answerable, 20);
            // THE SUPPORTED ANSWER: the answer was given AND the case's OWN gold condition was
            // met — all of the required propositions, or at least one of the alternatives.
            long supported = answerable.stream().filter(row -> row.answered()
                    && row.goldConditionMet()).count();
            long unsupported = answerable.stream().filter(row -> row.answered()
                    && !row.goldConditionMet()).count();
            // THE THIRD READING: the answer was given and cited NO gold at all.
            long zeroGold = answerable.stream().filter(row -> row.answered()
                    && row.goldCited() == 0).count();
            long abstained = unanswerable.stream().filter(row -> !row.answered()
                    && row.citedClaims() == 0 && row.citedSources() == 0).count();
            // THE UNSUPPORTED ANSWER OVER ALL THE ASK CASES: answering an unanswerable case
            // counts as an unsupported answer.
            long unsupportedOverAllAsk = 0L;
            for (CaseResult row : ask) {
                if (!row.answered()) {
                    continue;
                }
                boolean unanswerableCase = isUnanswerable(row.caseId(), dataset);
                if (unanswerableCase || !row.goldConditionMet()) {
                    unsupportedOverAllAsk++;
                }
            }
            double nonGold = answerable.stream().mapToDouble(row -> {
                long gold = row.goldCited();
                return row.citedClaims() - gold;
            }).average().orElse(0.0);
            double availability = answerable.stream().mapToDouble(row -> row.goldPropositions()
                    .isEmpty() ? 0.0 : (double) row.goldCited() / row.goldPropositions().size())
                    .average().orElse(0.0);
            // THE TOP-1 NOT GOLD: the claim at rank 1 of the floor-0.0 list is not a gold claim.
            int topRankNotGold = 0;
            for (CaseResult row : answerable) {
                if (row.topRankIsGold() != null && !row.topRankIsGold()) {
                    topRankNotGold++;
                }
            }
            long totalMillis = rows.stream().mapToLong(CaseResult::retrievalMillis).sum();
            long meanMillis = answerable.isEmpty() ? 0L
                    : (long) Math.round(answerable.stream().mapToLong(CaseResult::retrievalMillis)
                            .average().orElse(0.0));
            metrics.put(candidate, new CandidateMetrics(candidate, rows.size() - failures.size(),
                    rows.size(), failures, recall1, recall5, recall10, recall20, mrr,
                    answerable.isEmpty() ? 0.0 : (double) supported / answerable.size(),
                    unanswerable.isEmpty() ? 0.0 : (double) abstained / unanswerable.size(),
                    answerable.isEmpty() ? 0.0 : (double) unsupported / answerable.size(),
                    nonGold, topRankNotGold, meanMillis, totalMillis, 0.0, answerable.size(),
                    micro1, micro5, micro10, micro20, availability,
                    ask.isEmpty() ? 0.0 : (double) unsupportedOverAllAsk / ask.size(),
                    ask.size(), supported, unsupported, zeroGold, answerable.size()));
        }
        return metrics;
    }

    /** The global (micro) share of the golds found within the top K. */
    private static double microRecall(List<CaseResult> answerable, int k) {
        int found = 0;
        int total = 0;
        for (CaseResult row : answerable) {
            total += row.goldPropositions().size();
            for (Integer rank : row.goldRanks()) {
                if (rank > 0 && rank <= k) {
                    found++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) found / total;
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /** Whether a case expects an answer. */
    public static boolean isAnswerable(String caseId, Dataset dataset) {
        CaseSpec caseSpec = find(dataset, caseId);
        return caseSpec.expect() != null && Boolean.TRUE.equals(caseSpec.expect().answered());
    }

    /** Whether a case expects an abstention. */
    public static boolean isUnanswerable(String caseId, Dataset dataset) {
        CaseSpec caseSpec = find(dataset, caseId);
        return caseSpec.expect() != null && Boolean.FALSE.equals(caseSpec.expect().answered());
    }

    private static CaseSpec find(Dataset dataset, String caseId) {
        for (CaseSpec caseSpec : dataset.cases()) {
            if (caseSpec.id().equals(caseId)) {
                return caseSpec;
            }
        }
        throw new IllegalStateException("the dataset does not hold the case " + caseId);
    }

    private static int countAnswerable(Dataset dataset) {
        int count = 0;
        for (CaseSpec caseSpec : dataset.cases()) {
            if (isAnswerable(caseSpec.id(), dataset)) {
                count++;
            }
        }
        return count;
    }

    private static int countUnanswerable(Dataset dataset) {
        int count = 0;
        for (CaseSpec caseSpec : dataset.cases()) {
            if (isUnanswerable(caseSpec.id(), dataset)) {
                count++;
            }
        }
        return count;
    }

    /** The counts of the extraction, for the report. */
    public static Map<String, Object> extractionSummary(KnowledgeSnapshot snapshot,
            SourceGroundedEnrichment.Extraction extraction) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("concepts", extraction.conceptCount());
        summary.put("definitions", extraction.definitions().size());
        summary.put("supportedDefinitions", extraction.supportedDefinitions());
        summary.put("conceptsWithoutDefinition", extraction.conceptsWithoutDefinition().size());
        summary.put("relations", extraction.relations().size());
        summary.put("supportedRelations", extraction.supportedRelations());
        summary.put("rejections", extraction.rejections().size());
        Map<String, Long> byReason = new LinkedHashMap<>();
        for (SourceGroundedEnrichment.Rejection rejection : extraction.rejections()) {
            byReason.merge(rejection.reason(), 1L, Long::sum);
        }
        summary.put("rejectionsByReason", byReason);
        Map<String, Long> byType = new LinkedHashMap<>();
        for (SourceGroundedEnrichment.RelationCandidate relation : extraction.relations()) {
            byType.merge(relation.type().name(), 1L, Long::sum);
        }
        summary.put("relationsByType", byType);
        summary.put("extractionMillis", extraction.extractionMillis());
        summary.put("retainedBytes", extraction.retainedBytes());
        summary.put("workspaceClaims", snapshot.claims().size());
        return summary;
    }

    @Override
    public void close() {
        workbench.close();
    }
}
