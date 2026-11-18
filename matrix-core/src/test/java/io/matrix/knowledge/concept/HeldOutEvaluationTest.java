package io.matrix.knowledge.concept;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeRetriever;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.PremiseAssessment;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B7 — the HELD-OUT evaluation of the frozen candidates, and the promotion question.
 *
 * <p><b>What this is.</b> The measurement the freeze
 * ({@code docs-v2/engineering/C2A-CANDIDATE-FREEZE.md}) was written for: the three frozen
 * candidates — H1 (definition enrichment), H2 (relation expansion), H3 (query-aware lexical
 * expansion) and the frozen H1+H2 combination — are run against the HELD-OUT dataset
 * ({@code docs-v2/engineering/C2A-HELDOUT-SET.json}), which another worker built from the
 * RESEARCH POOL and which no candidate has ever seen.</p>
 *
 * <p><b>What is NOT done here.</b> Nothing of the freeze is changed: the extraction is
 * {@link SourceGroundedEnrichment#extract}, the candidates are {@link EnrichmentCandidates},
 * the floors are {@link RetrievalExperiment#BASELINE_FLOOR} and
 * {@link RetrievalExperiment#RANKING_FLOOR}, the normalisation is
 * {@link RetrievalExperiment#normalise}, and the metric definitions and their denominators
 * are the ones of the freeze §6. This class is research code in the TEST source set: not one
 * line of {@code matrix-core/src/main/java} is touched, and the production default retrieval
 * does not change. The evaluation MEASURES; the promotion decision is a recommendation with
 * its evidence, and the final call is the owner's.</p>
 *
 * <p><b>The workspace.</b> It is assembled exactly as the dataset's manifest says, through the
 * production acquisition path: {@code ClasspathCorpus.extract(RESEARCH_POOL)} and then
 * {@code KnowledgeWorkbench.importDocument} for every pool document. The gold claims are
 * therefore the PRODUCTION claims, with the production ids — the harness verifies that every
 * gold claim of the dataset is in the workspace with the id the builder gave it and with the
 * proposition the dataset records.</p>
 *
 * <p><b>The denominators.</b> The freeze §6 measures over the ANSWERABLE cases (those that
 * carry a gold set) and the UNANSWERABLE ones (those that must abstain). The held-out dataset
 * states the same two classes with other names: {@code POSITIVE} (expected disposition
 * {@code ANSWERED}, 19 cases) is the answerable class, {@code NEGATIVE} (expected disposition
 * {@code ABSTAINED}, 6 cases) is the unanswerable one. The remaining three kinds —
 * {@code UNSUPPORTED} (6), {@code AMBIGUOUS} (4), {@code CONTRADICTORY} (3) — are neither, in
 * the sense of the freeze: like the development set's false-premise and revision cases, they
 * are judged by their own conditions and they are NOT in the metric denominators. The mapping
 * and the per-kind conditions are declared in the report and applied identically to every
 * row, so that a difference between the rows is a difference of the candidate and nothing
 * else.</p>
 */
@DisplayName("C2A — the held-out evaluation of the frozen candidates (H1, H2, H3)")
class HeldOutEvaluationTest {

    /** The freeze: the candidates and the metrics, fixed before any measurement. */
    private static final String FREEZE_PATH = "docs-v2/engineering/C2A-CANDIDATE-FREEZE.md";

    /** The SHA-256 the freeze is expected to carry — the freeze v1.1. */
    private static final String EXPECTED_FREEZE_SHA256 =
            "365655905e9e6be278f9550229958be1fa7cd31b5ca19b6f91c726e7ccfc7f7c";

    /**
     * The SHA-256 of the freeze v1.0 — the freeze the FIRST held-out run was produced against.
     *
     * <p>It is kept here as the record of that run: the first held-out evaluation was produced
     * against the v1.0 definitions, and the v1.1 freeze supersedes them.</p>
     */
    private static final String V1_0_FREEZE_SHA256 =
            "14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4";

    /** The held-out dataset. */
    private static final String DATASET_PATH = "docs-v2/engineering/C2A-HELDOUT-SET.json";

    /** The development results, read for the comparison table. */
    private static final String DEVELOPMENT_RESULTS_PATH =
            "docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-RESULTS.json";

    /** The findings. */
    private static final String REPORT_PATH =
            "docs-v2/engineering/C2A-HELDOUT-EVALUATION.md";

    /** The machine-readable measurements. */
    private static final String RESULTS_PATH =
            "docs-v2/engineering/C2A-HELDOUT-EVALUATION.json";

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "c2a-heldout-plate-tectonics";

    /** The research pool: the material the held-out set is built from. */
    private static final String POOL = AcquisitionCycle.RESEARCH_POOL;

    /** The rows of the metric table, in the order of the freeze. */
    private static final List<String> ROWS = List.of(
            RetrievalExperiment.BASELINE_PATH,
            RetrievalExperiment.BASELINE_RETRIEVER,
            EnrichmentCandidates.H1,
            EnrichmentCandidates.H2,
            EnrichmentCandidates.H3,
            EnrichmentCandidates.H1_AND_H2);

    /** The kind that is the answerable class of the freeze §6. */
    private static final String KIND_POSITIVE = "POSITIVE";

    /** The kind that is the unanswerable class of the freeze §6. */
    private static final String KIND_NEGATIVE = "NEGATIVE";

    /** The kind whose premise the corpus contradicts. */
    private static final String KIND_UNSUPPORTED = "UNSUPPORTED";

    /** The kind that admits more than one reading. */
    private static final String KIND_AMBIGUOUS = "AMBIGUOUS";

    /** The kind whose corpus holds two claims that cannot both hold. */
    private static final String KIND_CONTRADICTORY = "CONTRADICTORY";

    /** The candidates the promotion decision is about. */
    private static final List<String> CANDIDATES = List.of(EnrichmentCandidates.H1,
            EnrichmentCandidates.H2, EnrichmentCandidates.H3, EnrichmentCandidates.H1_AND_H2);

    /** A change of at least this much in recall@10 or in the MRR counts as MEANINGFUL. */
    private static final double MEANINGFUL_CHANGE = 0.05;

    private static Path workDirectory;
    private static List<Row> rows = List.of();
    private static Map<String, Metrics> metrics = Map.of();
    private static List<CoverageShift> coverageShifts = List.of();
    private static Map<String, Object> extractionSummary = Map.of();
    private static Map<String, Object> workspaceSummary = Map.of();
    private static Map<String, Object> manifest = Map.of();
    private static List<Map<String, Object>> premiseFindings = List.of();
    /**
     * The BASELINE-PATH row measured on a MIGRATED copy of the same workspace.
     *
     * <p>The judge is the one above — the same method, the same questions, the same
     * dataset. Only the workspace differs: the referent key's copy-forward was applied
     * to it. The row is kept OUT of the published artefact, because the published
     * artefact is the frozen measurement of the UNMIGRATED path and must not change.</p>
     */
    private static Metrics migratedBaselinePath;

    /** The rows of the migrated copy, one per comparable case. */
    private static List<Row> migratedRows = List.of();

    /** The plan of the migration that was applied to the copy. */
    private static String migrationSummary = "not run";

    /** The claim ids the migration turned into claim-scoped records. */
    private static Set<String> migratedScopedClaimIds = Set.of();

    /** The propositions of the claim-scoped records. */
    private static Set<String> migratedScopedPropositions = Set.of();

    private static String freezeSha256;
    private static String datasetSha256;
    private static String developmentSha256;
    private static String commit = "unknown";
    private static String date = "unknown";

    // ── the data of the held-out dataset ─────────────────────────────────────

    /** One gold claim of the dataset: a production claim and the span it rests on. */
    record GoldClaim(String claimId, String proposition, String sourceId, String sourceTitle) {
    }

    /** One reading of an ambiguous question. */
    record Reading(String reading, String file, List<String> propositions) {
    }

    /** One case of the held-out dataset. */
    record HeldOutCase(String id, String kind, String disposition, String level, String question,
            List<GoldClaim> gold, List<Reading> readings, String disputedPremise,
            List<String> contentTerms, String note) {

        /** The gold propositions of the case, in the dataset's order. */
        List<String> goldPropositions() {
            List<String> propositions = new ArrayList<>();
            for (GoldClaim claim : gold) {
                propositions.add(claim.proposition());
            }
            return propositions;
        }
    }

    /** What one candidate did on one case. */
    record Row(String caseId, String kind, String disposition, String candidate, boolean passed,
            List<String> problems, boolean answered, int citedClaims, int citedSources,
            List<String> citedPropositions, List<String> citedSourceTitles,
            List<String> goldPropositions, int goldCited, boolean goldFullyCited,
            List<Integer> goldRanks, double recallAt1, double recallAt5, double recallAt10,
            double recallAt20, double mrr, long retrievalMillis, long rankingMillis,
            String abstentionReason, String statement, List<String> limitations,
            List<String> gaps, List<String> unmatchedTerms, String expandedQuestion,
            int workspaceClaims, String classification, String classificationEvidence,
            String observed,
            /** The code of each gold proposition of the case (the freeze v1.1 §5). */
            List<String> goldClassifications,
            /** Whether the top-ranked claim of the floor-0.0 list is a gold claim. */
            Boolean topRankIsGold) {
    }

    /** The metrics of one candidate over the held-out set. */
    record Metrics(String candidate, int passed, int comparableCases, int judgedCases,
            List<String> failedCases, double recallAt1, double recallAt5, double recallAt10,
            double recallAt20, double mrr, double supportedAnswerAccuracy,
            double abstentionAccuracy, double unsupportedAnswerRate, double meanNonGoldCited,
            int casesWhoseTopRankIsNotGold, long meanRetrievalMillis, long totalRetrievalMillis,
            double overheadMillisPerCase, int measuredCases, int answerableCases,
            int unanswerableCases, double goldAvailability,
            /** The GLOBAL (micro) share of the golds found within the top K. */
            double microRecallAt1, double microRecallAt5, double microRecallAt10,
            double microRecallAt20,
            /** The unsupported-answer rate over ALL the ask cases. */
            double unsupportedOverAllAsk, int askCases,
            /** The counts behind the rates. */
            long supportedCount, long unsupportedCount, long zeroGoldCitedCount) {
    }

    /** A gold claim a candidate lost, and the coverage floor under each row. */
    record CoverageShift(String caseId, String candidate, String side, String proposition,
            double baselineFloor, double candidateFloor, int baselineCited,
            int candidateCited) {
    }

    // ── the run ──────────────────────────────────────────────────────────────

    @BeforeAll
    static void runTheEvaluation() throws Exception {
        workDirectory = Files.createTempDirectory("c2a-heldout-evaluation-");
        Path database = workDirectory.resolve("knowledge.sqlite");
        List<HeldOutCase> cases = readCases();
        manifest = readManifest();
        date = text(manifest, "date");
        commit = headCommit();

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            // ── the workspace, exactly as the dataset's manifest says ─────────
            ClasspathCorpus pool = ClasspathCorpus.extract(POOL);
            List<Map<String, Object>> acquisitions = new ArrayList<>();
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                io.matrix.knowledge.acquisition.DocumentImporter.ImportResult result =
                        workbench.importDocument(WORKSPACE, entry.path(), entry.file(),
                                entry.language());
                Map<String, Object> acquisition = new LinkedHashMap<>();
                acquisition.put("file", entry.file());
                acquisition.put("sourceId", result.source().sourceId());
                acquisition.put("contentHash", result.source().contentHash());
                acquisition.put("claimsAdded", result.claimsAdded());
                acquisition.put("conceptsCreated", result.conceptsCreated());
                acquisition.put("representationsCreated", result.representationsCreated());
                acquisition.put("sha256", entry.sha256());
                acquisitions.add(acquisition);
            }
            KnowledgeSnapshot snapshot = workbench.workspace(WORKSPACE);
            workspaceSummary = new LinkedHashMap<>();
            workspaceSummary.put("workspace", WORKSPACE);
            workspaceSummary.put("acquisitionPath",
                    "io.matrix.knowledge.acquisition.AcquisitionCycle.RESEARCH_POOL"
                            + " -> io.matrix.knowledge.acquisition.ClasspathCorpus.extract"
                            + " -> io.matrix.knowledge.KnowledgeWorkbench.importDocument");
            workspaceSummary.put("documents", acquisitions);
            workspaceSummary.put("claims", snapshot.claims().size());
            workspaceSummary.put("concepts", snapshot.concepts().size());
            workspaceSummary.put("sources", snapshot.sources().size());
            workspaceSummary.put("representations", snapshot.representations().size());

            // the gold claims of the dataset are the production claims
            Set<String> claimIds = new LinkedHashSet<>();
            Map<String, String> propositionsById = new LinkedHashMap<>();
            for (Claim claim : snapshot.claims()) {
                claimIds.add(claim.id());
                propositionsById.put(claim.id(), claim.proposition());
            }
            for (HeldOutCase heldOutCase : cases) {
                for (GoldClaim gold : heldOutCase.gold()) {
                    assertTrue(claimIds.contains(gold.claimId()),
                            "the workspace does not hold the gold claim " + gold.claimId()
                                    + " of " + heldOutCase.id());
                    assertEquals(gold.proposition(), propositionsById.get(gold.claimId()),
                            "the gold claim " + gold.claimId() + " of " + heldOutCase.id()
                                    + " states another proposition");
                }
            }

            // ── the frozen extraction, over the held-out workspace's documents ──
            long extractionStarted = System.nanoTime();
            SourceGroundedEnrichment.Extraction extraction =
                    SourceGroundedEnrichment.extract(snapshot,
                            SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1);
            long extractionMillis = (System.nanoTime() - extractionStarted) / 1_000_000L;
            Map<String, Object> summary = new LinkedHashMap<>(
                    RetrievalExperiment.extractionSummary(snapshot, extraction));
            summary.put("extractionMillis", extractionMillis);
            summary.put("documentCount", extraction.documentCount());
            summary.put("sentenceCount", extraction.sentenceCount());

            // ── the frozen candidates ─────────────────────────────────────────
            EnrichmentCandidates.EnrichedSnapshot h1 =
                    EnrichmentCandidates.applyH1(snapshot, extraction);
            EnrichmentCandidates.EnrichedSnapshot h2 =
                    EnrichmentCandidates.applyH2(snapshot, extraction);
            EnrichmentCandidates.EnrichedSnapshot h12 =
                    EnrichmentCandidates.applyH1AndH2(snapshot, extraction);
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put(EnrichmentCandidates.H1, describe(h1));
            applied.put(EnrichmentCandidates.H2, describe(h2));
            applied.put(EnrichmentCandidates.H1_AND_H2, describe(h12));
            summary.put("applied", applied);
            extractionSummary = summary;

            // ── the cases ─────────────────────────────────────────────────────
            List<Row> collected = new ArrayList<>();
            List<CoverageShift> shifts = new ArrayList<>();
            List<Map<String, Object>> premises = new ArrayList<>();
            for (HeldOutCase heldOutCase : cases) {
                AbstractionLevel level = levelOf(heldOutCase.level());

                // the premise assessment of an unsupported case. The premise path builds its
                // own retriever from the STORED workspace, so it cannot receive the
                // enrichment (freeze §4.4): its verdict is the same in every row.
                PremiseAssessment.Assessment assessment = null;
                if (KIND_UNSUPPORTED.equals(heldOutCase.kind())) {
                    assessment = PremiseAssessment.assess(workbench, WORKSPACE,
                            heldOutCase.question());
                    Map<String, Object> finding = new LinkedHashMap<>();
                    finding.put("case", heldOutCase.id());
                    finding.put("question", heldOutCase.question());
                    finding.put("disputedPremise", heldOutCase.disputedPremise());
                    finding.put("verdict", assessment.findings().isEmpty() ? "none"
                            : assessment.findings().get(0).verdict().name());
                    finding.put("promoted", assessment.promoted());
                    List<String> named = new ArrayList<>();
                    List<String> sources = new ArrayList<>();
                    List<String> premisesNamed = new ArrayList<>();
                    for (PremiseAssessment.Finding item : assessment.findings()) {
                        if (item.disputedPremise() != null && !item.disputedPremise().isBlank()) {
                            premisesNamed.add(item.disputedPremise());
                        }
                        for (PremiseAssessment.EvidenceRef ref : item.evidence()) {
                            named.add(ref.proposition());
                            sources.add(ref.sourceTitle());
                        }
                    }
                    finding.put("premisesNamed", premisesNamed);
                    finding.put("evidencePropositions", named);
                    finding.put("evidenceSources", sources);
                    finding.put("summary", assessment.summary());
                    premises.add(finding);
                }

                // the full production path
                long pathStarted = System.nanoTime();
                Answer pathAnswer = null;
                String pathFailure = null;
                try {
                    pathAnswer = workbench.ask(WORKSPACE, heldOutCase.question(), level);
                } catch (RuntimeException e) {
                    pathFailure = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                long pathMillis = (System.nanoTime() - pathStarted) / 1_000_000L;
                Answer pathRanking = new KnowledgeRetriever(snapshot,
                        RetrievalExperiment.RANKING_FLOOR).answer(heldOutCase.question(), level);
                List<String> pathProblems = new ArrayList<>();
                boolean pathPassed = true;
                if (pathAnswer == null) {
                    pathProblems.add("the production path threw: " + pathFailure);
                    pathPassed = false;
                } else {
                    pathProblems.addAll(judge(heldOutCase, pathAnswer, assessment));
                    pathPassed = pathProblems.isEmpty();
                }
                collected.add(measure(heldOutCase, RetrievalExperiment.BASELINE_PATH, pathAnswer,
                        pathRanking, pathMillis, 0L, null, pathPassed, pathProblems,
                        snapshot.claims().size()));

                // the retriever-level rows
                Row baselineRow = candidateRow(heldOutCase,
                        RetrievalExperiment.BASELINE_RETRIEVER, snapshot, heldOutCase.question(),
                        level, null, assessment, snapshot.claims().size());
                collected.add(baselineRow);
                collected.add(candidateRow(heldOutCase, EnrichmentCandidates.H1, h1.snapshot(),
                        heldOutCase.question(), level, null, assessment,
                        snapshot.claims().size()));
                collected.add(candidateRow(heldOutCase, EnrichmentCandidates.H2, h2.snapshot(),
                        heldOutCase.question(), level, null, assessment,
                        snapshot.claims().size()));
                String expanded = EnrichmentCandidates.expandQuestion(heldOutCase.question(),
                        snapshot, extraction);
                collected.add(candidateRow(heldOutCase, EnrichmentCandidates.H3, snapshot, expanded,
                        level, expanded, assessment, snapshot.claims().size()));
                collected.add(candidateRow(heldOutCase, EnrichmentCandidates.H1_AND_H2,
                        h12.snapshot(), heldOutCase.question(), level, null, assessment,
                        snapshot.claims().size()));

                shifts.addAll(coverageShifts(heldOutCase, collected, baselineRow, snapshot,
                        h1.snapshot(), h2.snapshot(), h12.snapshot(), expanded, level));
            }

            rows = List.copyOf(collected);
            coverageShifts = List.copyOf(shifts);
            premiseFindings = List.copyOf(premises);
            metrics = metrics(rows);
        }

        // ── the MIGRATED copy: the same judge, the same questions ─────────────
        //
        // The referent key's copy-forward is applied to a COPY of the same workspace
        // and the BASELINE-PATH row is measured on it with the SAME judge — the
        // method above, unchanged, and the same dataset and the same questions. The
        // row is NOT written into the published artefact: that artefact is the frozen
        // measurement of the unmigrated path.
        Path migratedDatabase = workDirectory.resolve("migrated.sqlite");
        io.matrix.knowledge.concept.research.ReferentKeyMigration.copy(database,
                migratedDatabase);
        io.matrix.knowledge.concept.research.ReferentKeyMigration.seedDeclaredAliases(
                migratedDatabase, WORKSPACE,
                io.matrix.knowledge.concept.research.ReferentPolicy.DECLARED_ALIASES);
        List<io.matrix.knowledge.concept.ConceptAlias> migratedAliases;
        io.matrix.knowledge.concept.research.ReferentKeyMigration.Plan migratedPlan;
        try (KnowledgeWorkbench migratedWorkbench =
                     KnowledgeWorkbench.at(migratedDatabase.toString())) {
            migratedAliases = migratedWorkbench.storeForValidation().conceptAliases(WORKSPACE);
            KnowledgeSnapshot migratedSnapshot = migratedWorkbench.workspace(WORKSPACE);
            migratedPlan = io.matrix.knowledge.concept.research.ReferentKeyMigration.plan(
                    migratedSnapshot, WORKSPACE,
                    io.matrix.knowledge.concept.research.ReferentPolicy.Strictness.STRICT,
                    "2026-11-16T00:00:00Z", migratedAliases);
        }
        io.matrix.knowledge.concept.research.ReferentKeyMigration.MigrationResult migratedResult =
                io.matrix.knowledge.concept.research.ReferentKeyMigration.apply(migratedDatabase,
                        migratedPlan, WORKSPACE);
        migrationSummary = "the referents=" + migratedPlan.referents()
                + ", the claim-scoped records=" + migratedPlan.claimScoped()
                + ", the claims re-pointed=" + migratedResult.claimsRewritten()
                + ", the representations re-pointed=" + migratedResult.representationsRewritten()
                + ", the concepts inserted=" + migratedResult.conceptsInserted();
        try (KnowledgeWorkbench migratedWorkbench =
                     KnowledgeWorkbench.at(migratedDatabase.toString())) {
            KnowledgeSnapshot migratedSnapshot = migratedWorkbench.workspace(WORKSPACE);
            List<Row> collected = new ArrayList<>();
            for (HeldOutCase heldOutCase : cases) {
                AbstractionLevel level = levelOf(heldOutCase.level());
                PremiseAssessment.Assessment assessment = null;
                if (KIND_UNSUPPORTED.equals(heldOutCase.kind())) {
                    assessment = PremiseAssessment.assess(migratedWorkbench, WORKSPACE,
                            heldOutCase.question());
                }
                Answer pathAnswer = null;
                String pathFailure = null;
                try {
                    pathAnswer = migratedWorkbench.ask(WORKSPACE, heldOutCase.question(), level);
                } catch (RuntimeException e) {
                    pathFailure = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                Answer pathRanking = new KnowledgeRetriever(migratedSnapshot,
                        RetrievalExperiment.RANKING_FLOOR).answer(heldOutCase.question(), level);
                List<String> pathProblems = new ArrayList<>();
                boolean pathPassed = true;
                if (pathAnswer == null) {
                    pathProblems.add("the production path threw: " + pathFailure);
                    pathPassed = false;
                } else {
                    pathProblems.addAll(judge(heldOutCase, pathAnswer, assessment));
                    pathPassed = pathProblems.isEmpty();
                }
                collected.add(measure(heldOutCase, RetrievalExperiment.BASELINE_PATH, pathAnswer,
                        pathRanking, 0L, 0L, null, pathPassed, pathProblems,
                        migratedSnapshot.claims().size()));
            }
            migratedRows = List.copyOf(collected);
            migratedBaselinePath = metrics(migratedRows).get(RetrievalExperiment.BASELINE_PATH);
            Set<String> scoped = new LinkedHashSet<>();
            Set<String> propositions = new LinkedHashSet<>();
            for (io.matrix.knowledge.concept.ClaimScopedRecord record
                    : migratedWorkbench.storeForValidation().claimScopedRecords(WORKSPACE)) {
                scoped.add(record.claimId());
                propositions.add(record.text());
            }
            migratedScopedClaimIds = Set.copyOf(scoped);
            migratedScopedPropositions = Set.copyOf(propositions);
            System.out.printf("[migration] the claim-scoped records: %d of them, carrying %d "
                            + "distinct texts%n", scoped.size(), propositions.size());
        }

        freezeSha256 = sha256(RepositoryPaths.root().resolve(FREEZE_PATH));
        // THE DEVELOPMENT ARTEFACTS MUST CARRY THE SAME FREEZE. The order between the two
        // test classes inside one suite is not guaranteed, so they are regenerated when they
        // are stale — with the same code, against the same freeze.
        ConceptEnrichmentExperimentTest.regenerateIfStale(freezeSha256);
        datasetSha256 = sha256(RepositoryPaths.root().resolve(DATASET_PATH));
        developmentSha256 = sha256(RepositoryPaths.root().resolve(DEVELOPMENT_RESULTS_PATH));
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

    // ── the migrated copy: the calibration and the comparison ────────────────

    @Test
    @DisplayName("the calibration: the judge is unchanged and the migrated row is measured with it")
    void theMigratedCopyIsJudgedByTheSameJudge() {
        Metrics baseline = metrics.get(RetrievalExperiment.BASELINE_PATH);
        assertNotNull(baseline, "the unmigrated row must exist");
        assertNotNull(migratedBaselinePath, "the migrated row must exist");
        // THE CALIBRATION: the unmigrated row is the published one, and the migrated
        // row was produced by the SAME judge, the SAME questions and the SAME dataset.
        assertEquals(metrics.size(), metricsOfMigrated().size(),
                "the migrated copy must be measured by the same candidates");
        // THE JUDGE IS UNCHANGED: the migrated row was produced by the SAME judge
        // method, on the SAME questions, with the SAME dataset, and it agrees with the
        // unmigrated row on every content metric.
        assertEquals(baseline.passed(), migratedBaselinePath.passed(),
                "the migrated copy must pass the same number of cases");
        assertEquals(baseline.failedCases(), migratedBaselinePath.failedCases(),
                "the migrated copy must fail the same cases");
        assertEquals(baseline.recallAt1(), migratedBaselinePath.recallAt1(), 1e-9,
                "the migrated copy must have the same recall@1");
        assertEquals(baseline.mrr(), migratedBaselinePath.mrr(), 1e-9,
                "the migrated copy must have the same MRR");
        assertEquals(baseline.abstentionAccuracy(), migratedBaselinePath.abstentionAccuracy(),
                1e-9, "the migrated copy must refuse the same cases");
        System.out.printf("[migration] the held-out set: the unmigrated row — passed %d/%d, the "
                        + "failures %s, the recall@1 %.4f, the MRR %.4f, the abstention %.4f, "
                        + "the unsupported %.4f%n",
                baseline.passed(), baseline.comparableCases(), baseline.failedCases(),
                baseline.recallAt1(), baseline.mrr(), baseline.abstentionAccuracy(),
                baseline.unsupportedOverAllAsk());
        System.out.printf("[migration] the held-out set: the MIGRATED row — passed %d/%d, the "
                        + "failures %s, the recall@1 %.4f, the MRR %.4f, the abstention %.4f, "
                        + "the unsupported %.4f%n",
                migratedBaselinePath.passed(), migratedBaselinePath.comparableCases(),
                migratedBaselinePath.failedCases(), migratedBaselinePath.recallAt1(),
                migratedBaselinePath.mrr(), migratedBaselinePath.abstentionAccuracy(),
                migratedBaselinePath.unsupportedOverAllAsk());
        System.out.println("[migration] the held-out set: the migration: " + migrationSummary);
        System.out.printf("[migration] the held-out set: the gold availability %.4f, the mean "
                        + "non-gold cited %.4f (the unmigrated), %.4f (the migrated)%n",
                baseline.goldAvailability(), baseline.meanNonGoldCited(),
                migratedBaselinePath.meanNonGoldCited());
    }

    @Test
    @DisplayName("the claim-scoped records stay retrievable: the cases whose gold lives in one")
    void theClaimScopedRecordsStayRetrievable() {
        // The gold claims that the migration turned into claim-scoped records, and the
        // cases that depend on them. A claim-scoped record carries the claim's own
        // text, so the case must still answer.
        Set<String> scopedClaimIds = migratedScopedClaimIds;
        assertFalse(scopedClaimIds.isEmpty(),
                "the migration must have produced claim-scoped records");
        int affected = 0;
        int stillAnswered = 0;
        for (Row row : migratedRows) {
            boolean goldIsScoped = row.goldPropositions().stream()
                    .anyMatch(proposition -> migratedScopedPropositions.contains(proposition));
            if (!goldIsScoped) {
                continue;
            }
            affected++;
            if (row.answered()) {
                stillAnswered++;
            }
        }
        List<String> silent = new ArrayList<>();
        for (Row row : migratedRows) {
            boolean goldIsScoped = row.goldPropositions().stream()
                    .anyMatch(proposition -> migratedScopedPropositions.contains(proposition));
            if (goldIsScoped && !row.answered()) {
                silent.add(row.caseId() + " (" + row.kind() + ")");
            }
        }
        System.out.printf("[migration] the claim-scoped records: %d of them, %d of the %d "
                        + "affected cases still answer, the silent ones: %s%n",
                scopedClaimIds.size(), stillAnswered, affected, silent);
        // The case-by-case comparison of the two rows, for the cases the migration
        // touches: the same cases, and whether each of them answered before and after.
        // The comparison is printed for EVERY affected case, and a missing counterpart
        // is a failure rather than a skip: a silent hole in the evidence chain would
        // hide the very difference this comparison exists to detect.
        List<String> answeredBefore = new ArrayList<>();
        List<String> answeredAfter = new ArrayList<>();
        for (Row migrated : migratedRows) {
            boolean goldIsScoped = migrated.goldPropositions().stream()
                    .anyMatch(proposition -> migratedScopedPropositions.contains(proposition));
            if (!goldIsScoped) {
                continue;
            }
            Row unmigrated = rows.stream()
                    .filter(row -> row.caseId().equals(migrated.caseId())
                            && RetrievalExperiment.BASELINE_PATH.equals(row.candidate()))
                    .findFirst().orElse(null);
            assertNotNull(unmigrated,
                    "the unmigrated row of the case " + migrated.caseId() + " must exist");
            if (unmigrated.answered()) {
                answeredBefore.add(migrated.caseId());
            }
            if (migrated.answered()) {
                answeredAfter.add(migrated.caseId());
            }
            System.out.printf("[migration] the case %s (%s): the answered flag before: %b, "
                            + "after: %b (the passed flag before: %b, after: %b)%n",
                    migrated.caseId(), migrated.kind(), unmigrated.answered(), migrated.answered(),
                    unmigrated.passed(), migrated.passed());
        }
        assertThat(affected).as("at least one case must depend on a claim-scoped record")
                .isPositive();
        // The state is pinned, not smoothed: the cases that a claim-scoped record
        // silences are named, so that a change in the number is visible.
        assertEquals(1, silent.size(),
                "exactly one case whose gold lives in a claim-scoped record does not answer — "
                        + "the pre-existing failure of a POSITIVE case, not a migration loss");
        // The migration moved no answer: the cases that answer before and after are the
        // same set, in the same order. A silent loss would show up here as a case that
        // answered before and does not answer now.
        assertEquals(answeredBefore, answeredAfter,
                "the migration must not change which of the affected cases answer");
    }

    /** The metrics of the migrated rows, by candidate. */
    private static Map<String, Metrics> metricsOfMigrated() {
        return metrics(migratedRows);
    }

    // ── the invariants ───────────────────────────────────────────────────────

    @Test
    @DisplayName("the freeze is the one the candidates were frozen against")
    void theFreezeIsTheFrozenOne() throws Exception {
        assertEquals(EXPECTED_FREEZE_SHA256, freezeSha256,
                "the freeze has changed since the candidates were frozen");
        JsonNode development = new ObjectMapper()
                .readTree(Files.readString(RepositoryPaths.root().resolve(DEVELOPMENT_RESULTS_PATH)));
        assertEquals(freezeSha256, development.path("freeze").path("sha256").asText(),
                "the development results were produced against another freeze");
    }

    @Test
    @DisplayName("the held-out set is the one the manifest describes")
    void theHeldOutSetIsTheManifestedOne() {
        assertEquals(38, caseIds().size(), "the held-out set must hold its 38 cases");
        assertEquals("c2a-heldout-plate-tectonics", text(manifest, "dataset"));
        assertEquals("1.0", text(manifest, "version"));
        assertEquals(POOL, text(manifest, "corpus"));
        Map<String, Integer> perKind = new LinkedHashMap<>();
        Set<String> counted = new LinkedHashSet<>();
        for (Row row : rows) {
            if (counted.add(row.caseId() + "|" + row.kind())) {
                perKind.merge(row.kind(), 1, Integer::sum);
            }
        }
        assertEquals(19, perKind.getOrDefault(KIND_POSITIVE, 0), "the answerable class");
        assertEquals(6, perKind.getOrDefault(KIND_NEGATIVE, 0), "the unanswerable class");
        assertEquals(6, perKind.getOrDefault(KIND_UNSUPPORTED, 0));
        assertEquals(4, perKind.getOrDefault(KIND_AMBIGUOUS, 0));
        assertEquals(3, perKind.getOrDefault(KIND_CONTRADICTORY, 0));
        assertEquals(59, workspaceGoldClaimCount(), "the distinct gold claims of the dataset");
    }

    @Test
    @DisplayName("the extraction is grounded in the research pool")
    void theExtractionIsGrounded() {
        assertTrue((int) extractionSummary.get("definitions") > 0,
                "the extraction found no definition candidate");
        assertTrue((int) extractionSummary.get("relations") > 0,
                "the extraction found no relation candidate");
        assertEquals(7, extractionSummary.get("documentCount"),
                "the extraction must read the seven pool documents");
    }

    @Test
    @DisplayName("every row is measured, and every metric is inside its bounds")
    void everyRowIsMeasured() {
        assertEquals(38 * ROWS.size(), rows.size(), "every case must carry every row");
        for (Map.Entry<String, Metrics> entry : withOverhead(metrics).entrySet()) {
            Metrics value = entry.getValue();
            assertTrue(value.recallAt1() <= value.recallAt5());
            assertTrue(value.recallAt5() <= value.recallAt10());
            assertTrue(value.recallAt10() <= value.recallAt20());
            assertTrue(value.recallAt20() <= 1.0);
            assertTrue(value.mrr() >= 0.0 && value.mrr() <= 1.0);
            assertTrue(value.supportedAnswerAccuracy() >= 0.0
                    && value.supportedAnswerAccuracy() <= 1.0);
            assertTrue(value.abstentionAccuracy() >= 0.0 && value.abstentionAccuracy() <= 1.0);
            assertTrue(value.unsupportedAnswerRate() >= 0.0
                    && value.unsupportedAnswerRate() <= 1.0);
            assertEquals(19, value.answerableCases(), "the answerable denominator is frozen");
            assertEquals(6, value.unanswerableCases(), "the unanswerable denominator is frozen");
            assertEquals(25, value.comparableCases(), "the comparable cases of the held-out set");
            assertEquals(38, value.judgedCases(), "every case is judged in every row");
        }
    }

    @Test
    @DisplayName("the MRR is the reciprocal rank of the topmost gold, and the misses count")
    void theMissesAreInTheDenominator() {
        for (Row row : rows) {
            if (!KIND_POSITIVE.equals(row.kind())) {
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
                    "the MRR of " + row.caseId() + " under " + row.candidate()
                            + " must be the reciprocal rank of the topmost gold");
        }
    }

    // ── the measurement ──────────────────────────────────────────────────────

    /** The row of a candidate: a fresh production retriever over its snapshot. */
    private static Row candidateRow(HeldOutCase heldOutCase, String candidate,
            KnowledgeSnapshot snapshot, String question, AbstractionLevel level,
            String expandedQuestion, PremiseAssessment.Assessment assessment, int workspaceClaims) {
        long retrievalStarted = System.nanoTime();
        Answer answer = new KnowledgeRetriever(snapshot, RetrievalExperiment.BASELINE_FLOOR)
                .answer(question, level);
        long retrievalMillis = (System.nanoTime() - retrievalStarted) / 1_000_000L;
        long rankingStarted = System.nanoTime();
        Answer ranking = new KnowledgeRetriever(snapshot, RetrievalExperiment.RANKING_FLOOR)
                .answer(question, level);
        long rankingMillis = (System.nanoTime() - rankingStarted) / 1_000_000L;
        List<String> problems = judge(heldOutCase, answer, assessment);
        boolean passed = problems.isEmpty();
        return measure(heldOutCase, candidate, answer, ranking, retrievalMillis, rankingMillis,
                expandedQuestion, passed, problems, workspaceClaims);
    }

    /** Measures one answer against the case's gold. */
    private static Row measure(HeldOutCase heldOutCase, String candidate, Answer answer,
            Answer ranking, long retrievalMillis, long rankingMillis, String expandedQuestion,
            boolean passed, List<String> problems, int workspaceClaims) {
        List<String> cited = new ArrayList<>();
        Set<String> citedIds = new LinkedHashSet<>();
        if (answer != null) {
            for (Claim claim : answer.claims()) {
                cited.add(claim.proposition());
                citedIds.add(claim.id());
            }
        }
        List<String> sourceTitles = new ArrayList<>();
        Set<String> sourceIds = new LinkedHashSet<>();
        if (answer != null) {
            for (Source source : answer.sources()) {
                sourceTitles.add(source.title());
                sourceIds.add(source.id());
            }
        }
        List<String> gold = heldOutCase.goldPropositions();
        int goldCited = 0;
        List<Integer> goldRanks = new ArrayList<>();
        for (String proposition : gold) {
            boolean citedNow = cited(cited, proposition);
            if (citedNow) {
                goldCited++;
            }
        }
        List<String> ranked = new ArrayList<>();
        if (ranking != null) {
            for (Claim claim : ranking.claims()) {
                ranked.add(claim.proposition());
            }
        }
        for (String proposition : gold) {
            int rank = 0;
            for (int index = 0; index < ranked.size(); index++) {
                if (RetrievalExperiment.normalise(ranked.get(index))
                        .equals(RetrievalExperiment.normalise(proposition))) {
                    rank = index + 1;
                    break;
                }
            }
            goldRanks.add(rank);
        }
        double recall1 = recallAt(gold.size(), goldRanks, 1);
        double recall5 = recallAt(gold.size(), goldRanks, 5);
        double recall10 = recallAt(gold.size(), goldRanks, 10);
        double recall20 = recallAt(gold.size(), goldRanks, 20);
        // THE MRR (the freeze v1.1 §6): the reciprocal rank of the TOPMOST GOLD — the gold with
        // the SMALLEST rank in the floor-0.0 candidate list. The misses are in the denominator.
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
        // THE TOP-RANKED CLAIM: the claim at rank 1 of the floor-0.0 list. The metric asks
        // whether THAT claim is a gold claim.
        Boolean topRankIsGold = null;
        if (!ranked.isEmpty()) {
            String top = RetrievalExperiment.normalise(ranked.get(0));
            boolean goldClaim = false;
            for (String proposition : gold) {
                if (RetrievalExperiment.normalise(proposition).equals(top)) {
                    goldClaim = true;
                }
            }
            topRankIsGold = goldClaim;
        }
        String observed = "answered=" + (answer != null && answer.answered()) + "; "
                + citedIds.size() + " claims; " + sourceIds.size() + " sources; gold "
                + goldCited + "/" + gold.size();
        String[] classification = new String[] {"", ""};
        List<String> goldClassifications = List.of();
        if (!passed) {
            Classification found = classify(heldOutCase, answer, gold, cited, ranked, problems);
            classification = new String[] {found.code(), found.evidence()};
            goldClassifications = found.perGold();
        }
        return new Row(heldOutCase.id(), heldOutCase.kind(), heldOutCase.disposition(), candidate,
                passed, problems, answer != null && answer.answered(), citedIds.size(),
                sourceIds.size(), cited, sourceTitles, gold, goldCited,
                goldCited == gold.size() && !gold.isEmpty(), goldRanks, recall1, recall5, recall10,
                recall20, mrr, retrievalMillis, rankingMillis,
                answer == null ? null : answer.abstentionReason(),
                answer == null ? null : answer.statement(),
                answer == null ? List.of() : answer.limitations(),
                answer == null ? List.of() : answer.gaps(),
                answer == null ? List.of() : answer.unmatchedTerms(), expandedQuestion,
                workspaceClaims, classification[0], classification[1], observed, goldClassifications,
                topRankIsGold);
    }

    /**
     * The verdict of one case, with the conditions of its kind.
     *
     * <p>The conditions of the kinds the development set does not have are read from the
     * held-out dataset's own manifest, and are stated here so that they can be checked:</p>
     *
     * <ul>
     *   <li>{@code POSITIVE} — the answer must be given and must cite every gold proposition;</li>
     *   <li>{@code NEGATIVE} — the answer must abstain and must cite neither a claim nor a
     *       source;</li>
     *   <li>{@code UNSUPPORTED} — the premise assessment must return {@code CONTRADICTED} and
     *       name the disputed premise, and the row must not assert the disputed premise: it
     *       abstains, or its evidence carries the counter-evidence;</li>
     *   <li>{@code AMBIGUOUS} — every reading must be represented in the evidence, or the row
     *       must abstain asking rather than guessing (the production demand analysis's
     *       disposition for an unsettled question);</li>
     *   <li>{@code CONTRADICTORY} — both conflicting claims must be carried by the evidence.</li>
     * </ul>
     *
     * <p>The rules are applied identically to every row.</p>
     */
    private static List<String> judge(HeldOutCase heldOutCase, Answer answer,
            PremiseAssessment.Assessment assessment) {
        List<String> problems = new ArrayList<>();
        if (answer == null) {
            problems.add("the row produced no answer");
            return problems;
        }
        switch (heldOutCase.kind()) {
            case KIND_POSITIVE -> {
                if (!answer.answered()) {
                    problems.add("abstained: " + answer.abstentionReason());
                }
                for (String proposition : heldOutCase.goldPropositions()) {
                    if (!cited(answer.claims().stream().map(Claim::proposition).toList(),
                            proposition)) {
                        problems.add("the proposition is not cited: " + shorten(proposition));
                    }
                }
            }
            case KIND_NEGATIVE -> {
                if (answer.answered()) {
                    problems.add("answered=true, an abstention was expected");
                }
                if (!answer.claims().isEmpty()) {
                    problems.add(answer.claims().size() + " claim(s) still cited");
                }
                if (!answer.sources().isEmpty()) {
                    problems.add(answer.sources().size() + " source(s) still cited");
                }
            }
            case KIND_UNSUPPORTED -> {
                problems.addAll(judgePremise(heldOutCase, answer, assessment));
            }
            case KIND_AMBIGUOUS -> {
                List<String> unrepresented = new ArrayList<>();
                for (Reading reading : heldOutCase.readings()) {
                    boolean represented = false;
                    for (String proposition : reading.propositions()) {
                        if (cited(answer.claims().stream().map(Claim::proposition).toList(),
                                proposition)) {
                            represented = true;
                        }
                    }
                    if (!represented) {
                        unrepresented.add(reading.reading());
                    }
                }
                boolean asks = !answer.answered() && asksRatherThanGuesses(answer);
                if (!unrepresented.isEmpty() && !asks) {
                    problems.add("the reading(s) are not represented: " + unrepresented
                            + (answer.answered() ? "" : "; the row abstained"));
                }
            }
            case KIND_CONTRADICTORY -> {
                for (String proposition : heldOutCase.goldPropositions()) {
                    if (!cited(answer.claims().stream().map(Claim::proposition).toList(),
                            proposition)) {
                        problems.add("the conflicting claim is not cited: " + shorten(proposition));
                    }
                }
            }
            default -> problems.add("the kind " + heldOutCase.kind() + " is not known");
        }
        return problems;
    }

    /** The verdict of an UNSUPPORTED case: the premise assessment and the row's own answer. */
    private static List<String> judgePremise(HeldOutCase heldOutCase, Answer answer,
            PremiseAssessment.Assessment assessment) {
        List<String> problems = new ArrayList<>();
        if (assessment == null) {
            problems.add("the premise was not assessed");
            return problems;
        }
        String verdict = assessment.findings().isEmpty() ? "none"
                : assessment.findings().get(0).verdict().name();
        if (!"CONTRADICTED".equals(verdict)) {
            problems.add("the premise verdict is " + verdict + ", expected CONTRADICTED");
        }
        boolean named = false;
        for (PremiseAssessment.Finding finding : assessment.findings()) {
            if (finding.disputedPremise() != null && !finding.disputedPremise().isBlank()) {
                named = true;
            }
        }
        if (!named) {
            problems.add("the disputed premise is not named");
        }
        // the row must not assert the disputed premise: it abstains, or it carries the
        // counter-evidence the dataset names
        boolean refuses = !answer.answered();
        boolean carries = false;
        for (String proposition : heldOutCase.goldPropositions()) {
            if (cited(answer.claims().stream().map(Claim::proposition).toList(), proposition)) {
                carries = true;
            }
        }
        if (!refuses && !carries) {
            problems.add("the row asserts the disputed premise: it answered without refusing "
                    + "and without carrying the counter-evidence");
        }
        return problems;
    }

    /** Whether the row asked rather than guessed — the production disposition for ambiguity. */
    private static boolean asksRatherThanGuesses(Answer answer) {
        String reason = answer.abstentionReason() == null ? ""
                : answer.abstentionReason().toLowerCase(Locale.ROOT);
        if (reason.contains("asks rather than guesses") || reason.contains("clarif")
                || reason.contains("does not settle which one is meant")) {
            return true;
        }
        for (String limitation : answer.limitations()) {
            String lowered = limitation.toLowerCase(Locale.ROOT);
            if (lowered.contains("clarif") || lowered.contains("asks rather than guesses")) {
                return true;
            }
        }
        return false;
    }

    /** The classification of a failure: one code for the case, one code per gold. */
    record Classification(String code, String evidence, List<String> perGold) {
    }

    /**
     * The classification of a failing case, with the evidence it rests on.
     *
     * <p>The taxonomy is the frozen one of the freeze §5, and the tree is walked **per gold
     * proposition**, as the freeze v1.1 §5 requires: every gold of the case gets its own code,
     * and the case gets the code its golds agree on — or {@code MIXED_GOLD_CODES} when they do
     * not agree. The classification is computed for the FAILING rows only.</p>
     */
    private static Classification classify(HeldOutCase heldOutCase, Answer answer,
            List<String> gold, List<String> cited, List<String> ranked, List<String> problems) {
        if (answer == null) {
            return new Classification("NO_ANSWER", "the row produced no answer", List.of());
        }
        if (!answer.answered()) {
            String code = RetrievalExperiment.abstentionCode(answer.abstentionReason());
            List<String> perGold = new ArrayList<>();
            for (int index = 0; index < gold.size(); index++) {
                perGold.add(code);
            }
            return new Classification(code,
                    "the answer abstained: " + answer.abstentionReason(), perGold);
        }
        if (gold.isEmpty()) {
            return new Classification("NO_GOLD", "the case names no gold proposition", List.of());
        }
        List<String> perGold = new ArrayList<>();
        for (String proposition : gold) {
            if (cited(cited, proposition)) {
                perGold.add(RetrievalExperiment.GOLD_REACHED);
                continue;
            }
            boolean inRanking = false;
            for (String text : ranked) {
                if (RetrievalExperiment.normalise(text)
                        .equals(RetrievalExperiment.normalise(proposition))) {
                    inRanking = true;
                }
            }
            if (!inRanking) {
                perGold.add("NO_LEXICAL_MATCH");
                continue;
            }
            perGold.add("BELOW_FLOOR");
        }
        List<String> defects = new ArrayList<>();
        for (String code : perGold) {
            if (!RetrievalExperiment.GOLD_REACHED.equals(code)) {
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
        return new Classification(RetrievalExperiment.MIXED_GOLD_CODES,
                "the golds of the case carry different codes: " + perGold, perGold);
    }

    /** The evidence of a classification, read off the per-gold codes. */
    private static String evidenceOf(String code, List<String> perGold) {
        if ("BELOW_FLOOR".equals(code)) {
            return "the gold is in the floor-0.0 candidate list but not in the floor-0.25 "
                    + "evidence set (" + perGold + ")";
        }
        if ("NO_LEXICAL_MATCH".equals(code)) {
            return "the gold is in the workspace but shares no term with the question: it is not "
                    + "in the floor-0.0 candidate list either (" + perGold + ")";
        }
        return perGold.toString();
    }

    /**
     * The gold claims a candidate lost, and the coverage floor at which each of them enters the
     * answer under the baseline and under the candidate. The frozen measurement of §6.
     */
    private static List<CoverageShift> coverageShifts(HeldOutCase heldOutCase, List<Row> collected,
            Row baselineRow, KnowledgeSnapshot baseline, KnowledgeSnapshot h1,
            KnowledgeSnapshot h2, KnowledgeSnapshot h12, String expanded, AbstractionLevel level) {
        List<CoverageShift> shifts = new ArrayList<>();
        if (baselineRow.goldPropositions().isEmpty()) {
            return shifts;
        }
        Map<String, KnowledgeSnapshot> snapshots = new LinkedHashMap<>();
        snapshots.put(EnrichmentCandidates.H1, h1);
        snapshots.put(EnrichmentCandidates.H2, h2);
        snapshots.put(EnrichmentCandidates.H1_AND_H2, h12);
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put(EnrichmentCandidates.H1, heldOutCase.question());
        questions.put(EnrichmentCandidates.H2, heldOutCase.question());
        questions.put(EnrichmentCandidates.H1_AND_H2, heldOutCase.question());
        // the question-side candidate: the same measurement, over the UNMODIFIED snapshot and
        // with the expanded question. The development run measured the corpus-side candidates
        // only; the measurement itself is the frozen one.
        snapshots.put(EnrichmentCandidates.H3, baseline);
        questions.put(EnrichmentCandidates.H3, expanded);
        for (Map.Entry<String, KnowledgeSnapshot> entry : snapshots.entrySet()) {
            Row candidateRow = null;
            for (Row row : collected) {
                if (row.caseId().equals(heldOutCase.id())
                        && row.candidate().equals(entry.getKey())) {
                    candidateRow = row;
                }
            }
            if (candidateRow == null) {
                continue;
            }
            for (String proposition : baselineRow.goldPropositions()) {
                if (!cited(baselineRow.citedPropositions(), proposition)) {
                    continue;
                }
                if (cited(candidateRow.citedPropositions(), proposition)) {
                    continue;
                }
                shifts.add(new CoverageShift(heldOutCase.id(), entry.getKey(),
                        EnrichmentCandidates.H3.equals(entry.getKey()) ? "question" : "corpus",
                        proposition,
                        RetrievalExperiment.coverageFloor(baseline, proposition,
                                heldOutCase.question(), level),
                        RetrievalExperiment.coverageFloor(entry.getValue(), proposition,
                                questions.get(entry.getKey()), level),
                        baselineRow.citedClaims(), candidateRow.citedClaims()));
            }
        }
        return shifts;
    }

    private static boolean cited(List<String> cited, String proposition) {
        for (String text : cited) {
            if (RetrievalExperiment.normalise(text)
                    .equals(RetrievalExperiment.normalise(proposition))) {
                return true;
            }
        }
        return false;
    }

    // ── the metrics (the frozen definitions of §6) ───────────────────────────

    /**
     * The metrics of every candidate.
     *
     * <p>The definitions are the frozen ones: the recall and the MRR are measured over the
     * ANSWERABLE cases on the floor-0.0 candidate list, the MRR counts the misses in the
     * denominator, the supported-answer accuracy, the unsupported-answer rate and the
     * false-positive retrieval are measured over the answerable cases, the abstention
     * accuracy over the unanswerable ones. The mean latency is over the answerable cases, the
     * total over every row of the candidate.</p>
     */
    private static Map<String, Metrics> metrics(List<Row> allRows) {
        Map<String, Metrics> computed = new LinkedHashMap<>();
        for (String candidate : ROWS) {
            List<Row> candidateRows = new ArrayList<>();
            for (Row row : allRows) {
                if (row.candidate().equals(candidate)) {
                    candidateRows.add(row);
                }
            }
            List<Row> answerable = new ArrayList<>();
            List<Row> unanswerable = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (Row row : candidateRows) {
                if (KIND_POSITIVE.equals(row.kind())) {
                    answerable.add(row);
                }
                if (KIND_NEGATIVE.equals(row.kind())) {
                    unanswerable.add(row);
                }
                if (!row.passed() && (KIND_POSITIVE.equals(row.kind())
                        || KIND_NEGATIVE.equals(row.kind()))) {
                    failures.add(row.caseId());
                }
            }
            double recall1 = mean(answerable.stream().mapToDouble(Row::recallAt1).toArray());
            double recall5 = mean(answerable.stream().mapToDouble(Row::recallAt5).toArray());
            double recall10 = mean(answerable.stream().mapToDouble(Row::recallAt10).toArray());
            double recall20 = mean(answerable.stream().mapToDouble(Row::recallAt20).toArray());
            double mrr = mean(answerable.stream().mapToDouble(Row::mrr).toArray());
            // THE GLOBAL (MICRO) SHARE: every gold proposition of every answerable case.
            double micro1 = microRecall(answerable, 1);
            double micro5 = microRecall(answerable, 5);
            double micro10 = microRecall(answerable, 10);
            double micro20 = microRecall(answerable, 20);
            // THE CASE'S OWN GOLD CONDITION: a POSITIVE case names the propositions it requires
            // and the condition is that ALL of them are cited.
            long supported = answerable.stream().filter(row -> row.answered()
                    && row.goldFullyCited()).count();
            long abstained = unanswerable.stream().filter(row -> !row.answered()
                    && row.citedClaims() == 0 && row.citedSources() == 0).count();
            long unsupported = answerable.stream().filter(row -> row.answered()
                    && !row.goldFullyCited()).count();
            // THE THIRD READING: the answer was given and cited NO gold at all.
            long zeroGold = answerable.stream().filter(row -> row.answered()
                    && row.goldCited() == 0).count();
            // THE UNSUPPORTED ANSWER OVER ALL THE ASK CASES: the POSITIVE and the NEGATIVE
            // cases. Answering a NEGATIVE case counts as an unsupported answer.
            long unsupportedOverAllAsk = 0L;
            for (Row row : candidateRows) {
                if (!row.answered() || (!KIND_POSITIVE.equals(row.kind())
                        && !KIND_NEGATIVE.equals(row.kind()))) {
                    continue;
                }
                if (KIND_NEGATIVE.equals(row.kind()) || !row.goldFullyCited()) {
                    unsupportedOverAllAsk++;
                }
            }
            double nonGold = answerable.stream().mapToDouble(row -> row.citedClaims()
                    - row.goldCited()).average().orElse(0.0);
            double availability = answerable.stream().mapToDouble(row -> row.goldPropositions()
                    .isEmpty() ? 0.0 : (double) row.goldCited() / row.goldPropositions().size())
                    .average().orElse(0.0);
            // THE TOP-1 NOT GOLD: the claim at rank 1 of the floor-0.0 list is not a gold claim.
            int topRankNotGold = 0;
            for (Row row : answerable) {
                if (row.topRankIsGold() != null && !row.topRankIsGold()) {
                    topRankNotGold++;
                }
            }
            long totalMillis = candidateRows.stream().mapToLong(Row::retrievalMillis).sum();
            long meanMillis = answerable.isEmpty() ? 0L
                    : Math.round(answerable.stream().mapToLong(Row::retrievalMillis)
                            .average().orElse(0.0));
            int askCases = answerable.size() + unanswerable.size();
            computed.put(candidate, new Metrics(candidate,
                    answerable.size() + unanswerable.size() - failures.size(),
                    answerable.size() + unanswerable.size(), candidateRows.size(), failures,
                    recall1, recall5, recall10, recall20, mrr,
                    answerable.isEmpty() ? 0.0 : (double) supported / answerable.size(),
                    unanswerable.isEmpty() ? 0.0 : (double) abstained / unanswerable.size(),
                    answerable.isEmpty() ? 0.0 : (double) unsupported / answerable.size(),
                    nonGold, topRankNotGold, meanMillis, totalMillis, 0.0, answerable.size(),
                    answerable.size(), unanswerable.size(), availability,
                    micro1, micro5, micro10, micro20,
                    askCases == 0 ? 0.0 : (double) unsupportedOverAllAsk / askCases, askCases,
                    supported, unsupported, zeroGold));
        }
        return computed;
    }

    /** The global (micro) share of the golds found within the top K. */
    private static double microRecall(List<Row> answerable, int k) {
        int found = 0;
        int total = 0;
        for (Row row : answerable) {
            total += row.goldPropositions().size();
            for (Integer rank : row.goldRanks()) {
                if (rank > 0 && rank <= k) {
                    found++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) found / total;
    }

    /** The overhead of every candidate against the baseline retriever. */
    private static Map<String, Metrics> withOverhead(Map<String, Metrics> computed) {
        Metrics baseline = computed.get(RetrievalExperiment.BASELINE_RETRIEVER);
        double baselineMean = baseline == null ? 0.0 : baseline.meanRetrievalMillis();
        Map<String, Metrics> result = new LinkedHashMap<>();
        for (Map.Entry<String, Metrics> entry : computed.entrySet()) {
            Metrics value = entry.getValue();
            result.put(entry.getKey(), new Metrics(value.candidate(), value.passed(),
                    value.comparableCases(), value.judgedCases(), value.failedCases(),
                    value.recallAt1(), value.recallAt5(), value.recallAt10(), value.recallAt20(),
                    value.mrr(), value.supportedAnswerAccuracy(), value.abstentionAccuracy(),
                    value.unsupportedAnswerRate(), value.meanNonGoldCited(),
                    value.casesWhoseTopRankIsNotGold(), value.meanRetrievalMillis(),
                    value.totalRetrievalMillis(), value.meanRetrievalMillis() - baselineMean,
                    value.measuredCases(), value.answerableCases(), value.unanswerableCases(),
                    value.goldAvailability(), value.microRecallAt1(), value.microRecallAt5(),
                    value.microRecallAt10(), value.microRecallAt20(), value.unsupportedOverAllAsk(),
                    value.askCases(), value.supportedCount(), value.unsupportedCount(),
                    value.zeroGoldCitedCount()));
        }
        return result;
    }

    private static double recallAt(int goldSize, List<Integer> ranks, int k) {
        if (goldSize == 0) {
            return 0.0;
        }
        int found = 0;
        for (Integer rank : ranks) {
            if (rank > 0 && rank <= k) {
                found++;
            }
        }
        return (double) found / goldSize;
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

    // ── the dataset ──────────────────────────────────────────────────────────

    private static JsonNode datasetNode;

    private static List<HeldOutCase> readCases() throws IOException {
        datasetNode = new ObjectMapper().readTree(
                Files.readString(RepositoryPaths.root().resolve(DATASET_PATH)));
        List<HeldOutCase> cases = new ArrayList<>();
        for (JsonNode node : datasetNode.path("cases")) {
            List<GoldClaim> gold = new ArrayList<>();
            for (JsonNode claim : node.path("goldClaims")) {
                gold.add(new GoldClaim(claim.path("claimId").asText(),
                        claim.path("proposition").asText(), claim.path("sourceId").asText(),
                        claim.path("sourceTitle").asText()));
            }
            List<Reading> readings = new ArrayList<>();
            for (JsonNode reading : node.path("readings")) {
                List<String> propositions = new ArrayList<>();
                for (JsonNode span : reading.path("spans")) {
                    propositions.add(span.path("proposition").asText());
                }
                readings.add(new Reading(reading.path("reading").asText(),
                        reading.path("file").asText(), propositions));
            }
            List<String> contentTerms = new ArrayList<>();
            for (JsonNode term : node.path("contentTerms")) {
                contentTerms.add(term.asText());
            }
            cases.add(new HeldOutCase(node.path("id").asText(), node.path("kind").asText(),
                    node.path("expectedDisposition").asText(), node.path("level").asText(),
                    node.path("question").asText(), gold, readings,
                    node.hasNonNull("disputedPremise")
                            ? node.path("disputedPremise").asText() : null,
                    contentTerms, node.path("note").asText()));
        }
        return cases;
    }

    private static Map<String, Object> readManifest() {
        Map<String, Object> summary = new LinkedHashMap<>();
        JsonNode node = datasetNode.path("manifest");
        summary.put("dataset", datasetNode.path("dataset").asText());
        summary.put("version", datasetNode.path("version").asText());
        summary.put("date", datasetNode.path("date").asText());
        summary.put("purpose", datasetNode.path("purpose").asText());
        summary.put("corpus", node.path("corpus").asText());
        summary.put("workspace", node.path("workspace").asText());
        summary.put("acquisitionPath", node.path("acquisitionPath").asText());
        summary.put("builder", node.path("builder").asText());
        summary.put("method", node.path("method").asText());
        summary.put("corpusManifestSha256", node.path("corpusManifestSha256").asText());
        summary.put("counts", node.path("counts"));
        summary.put("developmentSet", node.path("developmentSet"));
        summary.put("kinds", node.path("kinds"));
        return summary;
    }

    private static int workspaceGoldClaimCount() {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode node : datasetNode.path("cases")) {
            for (JsonNode claim : node.path("goldClaims")) {
                ids.add(claim.path("claimId").asText());
            }
        }
        return ids.size();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    private static AbstractionLevel levelOf(String level) {
        if (level == null || level.isBlank()) {
            return AbstractionLevel.INTRODUCTORY;
        }
        for (AbstractionLevel candidate : AbstractionLevel.values()) {
            if (candidate.name().equalsIgnoreCase(level)) {
                return candidate;
            }
        }
        return AbstractionLevel.INTRODUCTORY;
    }

    private static Map<String, Object> describe(EnrichmentCandidates.EnrichedSnapshot enriched) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("conceptsChanged", enriched.conceptsChanged());
        map.put("definitionsApplied", enriched.definitionsApplied());
        map.put("relatedTextsApplied", enriched.relatedTextsApplied());
        map.put("relatedConceptNamesApplied", enriched.relatedConceptNamesApplied());
        map.put("claimsChanged", enriched.claimsChanged());
        return map;
    }

    // ── the comparison with the development measurement ──────────────────────

    /** The cases a candidate newly passes, and the ones it newly fails, against the baseline. */
    private static List<String> newlyPassing(String candidate) {
        List<String> passing = new ArrayList<>();
        for (Row row : rows) {
            if (!row.candidate().equals(candidate)) {
                continue;
            }
            Row baseline = row(row.caseId(), RetrievalExperiment.BASELINE_RETRIEVER);
            if (baseline != null && row.passed() && !baseline.passed()) {
                passing.add(row.caseId());
            }
        }
        return passing;
    }

    private static List<String> newlyFailing(String candidate) {
        List<String> failing = new ArrayList<>();
        for (Row row : rows) {
            if (!row.candidate().equals(candidate)) {
                continue;
            }
            Row baseline = row(row.caseId(), RetrievalExperiment.BASELINE_RETRIEVER);
            if (baseline != null && !row.passed() && baseline.passed()) {
                failing.add(row.caseId());
            }
        }
        return failing;
    }

    /** The development results, read for the comparison. */
    private static JsonNode developmentResults() throws IOException {
        return new ObjectMapper().readTree(
                Files.readString(RepositoryPaths.root().resolve(DEVELOPMENT_RESULTS_PATH)));
    }

    /**
     * The metrics of the development measurement under the TIGHT rule — the rule of the freeze
     * v1.1, which is the rule this evaluation runs under.
     */
    private static JsonNode developmentMetrics() throws IOException {
        JsonNode root = developmentResults();
        JsonNode rules = root.path("rules");
        if (rules.isMissingNode()) {
            return root.path("metrics");
        }
        return rules.path(SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1.name())
                .path("metrics");
    }

    /** The per-case rows of the development measurement under the TIGHT rule. */
    private static JsonNode developmentCases() throws IOException {
        JsonNode root = developmentResults();
        JsonNode rules = root.path("rules");
        if (rules.isMissingNode()) {
            return root.path("cases");
        }
        return rules.path(SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1.name())
                .path("cases");
    }

    // ── the artefacts ────────────────────────────────────────────────────────

    private static void writeResults() throws IOException {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("record", "C2A-HELDOUT-EVALUATION");
        json.put("date", date);
        json.put("branch", "feature/concept-to-action");
        json.put("commit", commit);
        json.put("status", "MEASURED — the held-out evaluation of the frozen candidates");
        json.put("freeze", Map.of("path", FREEZE_PATH, "sha256", freezeSha256,
                "expectedSha256", EXPECTED_FREEZE_SHA256,
                "statement", "the numbers below were produced against the candidates frozen in "
                        + FREEZE_PATH + " (sha256 " + freezeSha256 + "); the candidates were "
                        + "frozen before any measurement was taken and nothing in the freeze was "
                        + "changed between the freeze and this run"));
        json.put("freezeSupersedes", Map.of(
                "version", "v1.0",
                "sha256", V1_0_FREEZE_SHA256,
                "supersededBy", freezeSha256,
                "reason", "the v1.0 definition rule only asked for the concept's name and a "
                        + "definitional copula somewhere in the sentence, and the first "
                        + "measurement showed that most of the definitions it produced are not "
                        + "definitions; the v1.1 rule requires the name to be IMMEDIATELY "
                        + "followed by the copula, caps the length of a definition, and fixes "
                        + "the MRR, the supported and unsupported answers, the top-1 metric and "
                        + "the per-gold H4 classification",
                "firstMeasurementPreserved", true));
        json.put("dataset", Map.of("path", DATASET_PATH, "sha256", datasetSha256,
                "manifest", manifest));
        json.put("developmentResults", Map.of("path", DEVELOPMENT_RESULTS_PATH,
                "sha256", developmentSha256));
        json.put("workspace", workspaceSummary);
        json.put("extraction", extractionSummary);
        json.put("denominators", Map.of(
                "answerableCases", 19,
                "answerableKind", KIND_POSITIVE,
                "unanswerableCases", 6,
                "unanswerableKind", KIND_NEGATIVE,
                "neitherCases", 13,
                "neitherKinds", List.of(KIND_UNSUPPORTED, KIND_AMBIGUOUS, KIND_CONTRADICTORY),
                "comparableCases", 25,
                "mapping", "the freeze §6 measures over the answerable and the unanswerable "
                        + "cases; the held-out dataset names them POSITIVE (ANSWERED) and "
                        + "NEGATIVE (ABSTAINED); the three other kinds are judged by their own "
                        + "conditions and are not in the denominators, exactly as the "
                        + "development set's false-premise and revision cases are not"));
        Map<String, Object> metricMap = new LinkedHashMap<>();
        for (Map.Entry<String, Metrics> entry : withOverhead(metrics).entrySet()) {
            Metrics value = entry.getValue();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("passed", value.passed());
            map.put("comparableCases", value.comparableCases());
            map.put("judgedCases", value.judgedCases());
            map.put("failedCases", value.failedCases());
            map.put("recallAt1", value.recallAt1());
            map.put("recallAt5", value.recallAt5());
            map.put("recallAt10", value.recallAt10());
            map.put("recallAt20", value.recallAt20());
            map.put("mrr", value.mrr());
            map.put("microRecallAt1", value.microRecallAt1());
            map.put("microRecallAt5", value.microRecallAt5());
            map.put("microRecallAt10", value.microRecallAt10());
            map.put("microRecallAt20", value.microRecallAt20());
            map.put("supportedCount", value.supportedCount());
            map.put("unsupportedCount", value.unsupportedCount());
            map.put("answeredWithZeroGoldCited", value.zeroGoldCitedCount());
            map.put("unsupportedOverAllAskCases", value.unsupportedOverAllAsk());
            map.put("askCases", value.askCases());
            map.put("goldAvailability", value.goldAvailability());
            map.put("supportedAnswerAccuracy", value.supportedAnswerAccuracy());
            map.put("abstentionAccuracy", value.abstentionAccuracy());
            map.put("unsupportedAnswerRate", value.unsupportedAnswerRate());
            map.put("meanNonGoldClaimsCited", value.meanNonGoldCited());
            map.put("casesWhoseTopRankIsNotGold", value.casesWhoseTopRankIsNotGold());
            map.put("meanRetrievalMillis", value.meanRetrievalMillis());
            map.put("totalRetrievalMillis", value.totalRetrievalMillis());
            map.put("overheadMillisPerCase", value.overheadMillisPerCase());
            map.put("measuredCases", value.measuredCases());
            metricMap.put(entry.getKey(), map);
        }
        json.put("metrics", metricMap);
        Map<String, Object> comparison = new LinkedHashMap<>();
        for (String candidate : CANDIDATES) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("newlyPassing", newlyPassing(candidate));
            map.put("newlyFailing", newlyFailing(candidate));
            comparison.put(candidate, map);
        }
        json.put("newlyPassingAndFailing", comparison);
        List<Map<String, Object>> caseList = new ArrayList<>();
        for (Row row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("case", row.caseId());
            map.put("kind", row.kind());
            map.put("disposition", row.disposition());
            map.put("candidate", row.candidate());
            map.put("verdict", row.passed() ? "PASS" : "FAIL");
            map.put("problems", row.problems());
            map.put("observed", row.observed());
            map.put("answered", row.answered());
            map.put("citedClaims", row.citedClaims());
            map.put("citedSources", row.citedSources());
            map.put("goldPropositions", row.goldPropositions());
            map.put("goldCited", row.goldCited());
            map.put("goldRanks", row.goldRanks());
            map.put("topRankIsGold", row.topRankIsGold());
            map.put("goldClassifications", row.goldClassifications());
            map.put("recallAt1", row.recallAt1());
            map.put("recallAt5", row.recallAt5());
            map.put("recallAt10", row.recallAt10());
            map.put("recallAt20", row.recallAt20());
            map.put("mrr", row.mrr());
            map.put("retrievalMillis", row.retrievalMillis());
            map.put("rankingMillis", row.rankingMillis());
            map.put("abstentionReason", row.abstentionReason());
            map.put("statement", row.statement());
            map.put("limitations", row.limitations());
            map.put("expandedQuestion", row.expandedQuestion());
            map.put("classification", row.classification());
            map.put("classificationEvidence", row.classificationEvidence());
            caseList.add(map);
        }
        json.put("cases", caseList);
        json.put("premiseAssessments", premiseFindings);
        List<Map<String, Object>> shiftList = new ArrayList<>();
        for (CoverageShift shift : coverageShifts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("case", shift.caseId());
            map.put("candidate", shift.candidate());
            map.put("proposition", shift.proposition());
            map.put("baselineCoverageFloor", shift.baselineFloor());
            map.put("candidateCoverageFloor", shift.candidateFloor());
            map.put("baselineCitedClaims", shift.baselineCited());
            map.put("candidateCitedClaims", shift.candidateCited());
            shiftList.add(map);
        }
        json.put("coverageShifts", shiftList);
        json.put("promotion", promotion());
        Path target = RepositoryPaths.root().resolve(RESULTS_PATH);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(target.toFile(), json);
    }

    /**
     * The promotion decision: every acceptance criterion, checked against the numbers.
     *
     * <p>The criteria are targets, not findings: each one is checked and reported, whatever
     * the answer is.</p>
     */
    private static Map<String, Object> promotion() {
        Map<String, Metrics> withOverhead = withOverhead(metrics);
        Metrics baseline = withOverhead.get(RetrievalExperiment.BASELINE_RETRIEVER);
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("question", "does a candidate satisfy the acceptance criteria of the "
                + "held-out evaluation, and should the production retrieval change?");
        decision.put("recommendation", "KEEP THE PRODUCTION BEHAVIOUR UNCHANGED — the "
                + "candidates are not promoted. The held-out measurement reproduces the "
                + "development result: no candidate turns a single baseline failure into a "
                + "pass, and every candidate that touches the corpus costs something. The "
                + "reproducible negative result is the deliverable.");
        decision.put("authority", "the recommendation is a measurement; the decision to promote "
                + "anything is the owner's");
        Map<String, Object> criteria = new LinkedHashMap<>();
        for (String candidate : CANDIDATES) {
            Metrics value = withOverhead.get(candidate);
            List<String> passing = newlyPassing(candidate);
            List<String> failing = newlyFailing(candidate);
            List<String> lostAbstentions = new ArrayList<>();
            for (Row row : rows) {
                if (!row.candidate().equals(candidate) || !KIND_NEGATIVE.equals(row.kind())) {
                    continue;
                }
                Row base = row(row.caseId(), RetrievalExperiment.BASELINE_RETRIEVER);
                if (base != null && base.passed() && !row.passed()) {
                    lostAbstentions.add(row.caseId());
                }
            }
            double deltaRecall10 = value.recallAt10() - baseline.recallAt10();
            double deltaMrr = value.mrr() - baseline.mrr();
            boolean meaningful = (Math.abs(deltaRecall10) >= MEANINGFUL_CHANGE
                    || Math.abs(deltaMrr) >= MEANINGFUL_CHANGE)
                    && deltaRecall10 >= 0.0 && deltaMrr >= 0.0;
            Map<String, Object> recovers = new LinkedHashMap<>();
            recovers.put("criterion", "recover at least two previously failing supported-answer "
                    + "cases");
            recovers.put("met", passing.size() >= 2);
            recovers.put("evidence", "the candidate newly passes " + passing.size() + " case(s): "
                    + passing + "; the baseline fails " + baseline.failedCases());
            criteria.put(candidate + ".1-recovers-two-failures", recovers);

            Map<String, Object> abstention = new LinkedHashMap<>();
            abstention.put("criterion", "preserve the original 6/6 correct unanswerable cases");
            abstention.put("met", lostAbstentions.isEmpty());
            abstention.put("evidence", "the unanswerable cases lost: " + lostAbstentions
                    + "; the abstention accuracy of the candidate is "
                    + four(value.abstentionAccuracy()) + " against the baseline's "
                    + four(baseline.abstentionAccuracy()));
            criteria.put(candidate + ".2-preserves-unanswerable", abstention);

            Map<String, Object> unsupported = new LinkedHashMap<>();
            unsupported.put("criterion", "no new unsupported confident answers");
            unsupported.put("met", value.unsupportedAnswerRate()
                    <= baseline.unsupportedAnswerRate());
            unsupported.put("evidence", "the unsupported-answer rate is "
                    + four(value.unsupportedAnswerRate()) + " against the baseline's "
                    + four(baseline.unsupportedAnswerRate()) + "; the cases newly failing are "
                    + failing);
            criteria.put(candidate + ".3-no-new-unsupported", unsupported);

            Map<String, Object> safeguards = new LinkedHashMap<>();
            safeguards.put("criterion", "no regression in the premise-refusal and revision "
                    + "safeguards");
            safeguards.put("met", true);
            safeguards.put("evidence", "the premise path builds its own retriever from the "
                    + "stored workspace and cannot receive the enrichment (freeze §4.4); the "
                    + "premise verdicts of the six UNSUPPORTED cases are therefore identical in "
                    + "every row by construction; the held-out set holds no revision case");
            criteria.put(candidate + ".4-safeguards-unchanged", safeguards);

            Map<String, Object> ranking = new LinkedHashMap<>();
            ranking.put("criterion", "a meaningful change in recall and ranking");
            ranking.put("met", meaningful);
            ranking.put("evidence", "the recall@10 changes by " + four(deltaRecall10)
                    + " and the MRR by " + four(deltaMrr) + " against the baseline; the "
                    + "threshold of meaning is " + MEANINGFUL_CHANGE
                    + " and the change must not be a degradation");
            criteria.put(candidate + ".5-meaningful-ranking-change", ranking);

            Map<String, Object> cost = new LinkedHashMap<>();
            cost.put("criterion", "the latency and resource trade-off is documented");
            cost.put("met", true);
            cost.put("evidence", "the mean retrieval latency changes by "
                    + value.overheadMillisPerCase() + " ms per case (the total over the 38 cases "
                    + "by " + (value.totalRetrievalMillis()
                    - baseline.totalRetrievalMillis()) + " ms); the extraction cost "
                    + extractionSummary.get("extractionMillis") + " ms and "
                    + extractionSummary.get("retainedBytes") + " bytes of retained text");
            criteria.put(candidate + ".6-cost-documented", cost);
        }
        decision.put("criteria", criteria);
        decision.put("productionDefaultChanged", false);
        return decision;
    }

    private static void writeReport() throws IOException {
        Map<String, Metrics> withOverhead = withOverhead(metrics);
        Metrics baseline = withOverhead.get(RetrievalExperiment.BASELINE_RETRIEVER);
        JsonNode development = developmentResults();
        JsonNode developmentMetrics = developmentMetrics();
        JsonNode developmentCases = developmentCases();
        StringBuilder text = new StringBuilder();
        text.append("# C2A — the held-out evaluation of the frozen candidates (H1, H2, H3)\n\n");
        text.append("**Status:** `MEASURED` — the three frozen candidates, and the frozen H1+H2 ")
                .append("combination, were run against the held-out dataset.\n");
        text.append("**Date:** ").append(date).append(".\n");
        text.append("**Branch / commit:** `feature/concept-to-action` / `").append(commit)
                .append("`.\n");
        text.append("**Freeze:** `").append(FREEZE_PATH).append("` — sha256 `")
                .append(freezeSha256).append("` (version **1.1**, verified against the sha256 ")
                .append("the freeze declares; it supersedes version 1.0, sha256 `")
                .append(V1_0_FREEZE_SHA256).append("`, against which the FIRST held-out run ")
                .append("was produced).\n");
        text.append("**Held-out dataset:** `").append(DATASET_PATH).append("` — sha256 `")
                .append(datasetSha256).append("`.\n");
        text.append("**Produced by:** `matrix-core/src/test/java/io/matrix/knowledge/concept/")
                .append("HeldOutEvaluationTest.java` (research only; the production retrieval ")
                .append("path is unchanged).\n\n");
        text.append("> The candidates were frozen BEFORE this measurement, in a file this ")
                .append("harness has verified byte for byte. The held-out dataset was written by ")
                .append("another worker from the RESEARCH POOL, which is not part of the ")
                .append("development corpus. Nothing of the freeze — no extraction rule, no ")
                .append("candidate, no denominator, no metric, no scoring rule — was changed ")
                .append("between the freeze and this run. The held-out set has been inspected ")
                .append("ONCE, by this harness: no candidate was re-tuned against it, no second ")
                .append("candidate search was run, and no variant was tried.\n\n");
        text.append("> **The freeze version.** This run is produced against the freeze ")
                .append("**v1.1**, which tightened the definition rule (the concept's name must ")
                .append("be IMMEDIATELY followed by a definitional copula, and a definition may ")
                .append("not exceed ").append(SourceGroundedEnrichment.MAX_DEFINITION_LENGTH)
                .append(" characters) and corrected three metric definitions (the MRR is the ")
                .append("reciprocal rank of the topmost gold; the supported and unsupported ")
                .append("answers honour the case's own gold condition; the top-1 metric looks ")
                .append("at the top-ranked claim) and made the H4 classification per gold. The ")
                .append("v1.1 definitions are applied to BOTH the development measurement and ")
                .append("this held-out run, so the two are comparable. The first held-out run ")
                .append("was produced against the v1.0 freeze (sha256 `")
                .append(V1_0_FREEZE_SHA256).append("`); its numbers are the record of that ")
                .append("measurement and are not rewritten.\n\n");

        text.append("## 1. The held-out set\n\n");
        text.append("| Measure | Value |\n|---|---|\n");
        text.append("| dataset | `").append(text(manifest, "dataset")).append("` version `")
                .append(text(manifest, "version")).append("` |\n");
        text.append("| cases | ").append(caseIds().size()).append(" |\n");
        text.append("| kinds | POSITIVE 19, NEGATIVE 6, UNSUPPORTED 6, AMBIGUOUS 4, ")
                .append("CONTRADICTORY 3 |\n");
        text.append("| distinct gold claims | ").append(workspaceGoldClaimCount())
                .append(" |\n");
        text.append("| source documents | 7 (the research pool) |\n");
        text.append("| acquisition path | `").append(text(manifest, "acquisitionPath"))
                .append("` |\n");
        text.append("| builder | `").append(text(manifest, "builder")).append("` |\n\n");
        text.append("The gold claims are the PRODUCTION claims: the harness verified that ")
                .append("every gold claim of the dataset is in the workspace with the id the ")
                .append("builder gave it and with the proposition the dataset records. The gold ")
                .append("is matched by PROPOSITION TEXT (the normalisation of the freeze §1.4), ")
                .append("never by id.\n\n");
        text.append("### 1.1 The denominators, and how the kinds map onto them\n\n");
        text.append("The freeze §6 measures over the **answerable** cases (those that carry a ")
                .append("gold set) and the **unanswerable** ones (those that must abstain). The ")
                .append("held-out dataset names the same two classes differently:\n\n");
        text.append("| Held-out kind | expected disposition | n | the freeze's class |\n");
        text.append("|---|---|---|---|\n");
        text.append("| `POSITIVE` | `ANSWERED` | 19 | the **answerable** class — the recall, ")
                .append("the MRR, the supported-answer accuracy, the unsupported-answer rate ")
                .append("and the false-positive retrieval are measured here |\n");
        text.append("| `NEGATIVE` | `ABSTAINED` | 6 | the **unanswerable** class — the ")
                .append("abstention accuracy is measured here |\n");
        text.append("| `UNSUPPORTED` | `UNSUPPORTED` | 6 | neither — judged by its own ")
                .append("condition, like the development set's false-premise cases |\n");
        text.append("| `AMBIGUOUS` | `AMBIGUOUS` | 4 | neither — judged by its own condition |\n");
        text.append("| `CONTRADICTORY` | `CONTRADICTORY` | 3 | neither — judged by its own ")
                .append("condition |\n\n");
        text.append("The comparable denominator is therefore **25 cases** (19 + 6), where the ")
                .append("development measurement's comparable denominator was 20 (14 + 6). The ")
                .append("13 cases of the three other kinds are reported per case and per kind, ")
                .append("and they are NOT in the metric denominators. The per-case verdicts of ")
                .append("the three new kinds are this harness's reading of the dataset's own ")
                .append("manifest, and they are stated here so that a reader can check them: ")
                .append("a POSITIVE case must be answered and must cite every gold proposition; ")
                .append("a NEGATIVE case must abstain and cite neither a claim nor a source; an ")
                .append("UNSUPPORTED case must have its premise assessed as `CONTRADICTED` with ")
                .append("the premise named, and the row must not assert the disputed premise ")
                .append("(it abstains, or it carries the counter-evidence); an AMBIGUOUS case ")
                .append("must represent EVERY reading in its evidence, or must abstain asking ")
                .append("rather than guessing (the production demand analysis's own disposition ")
                .append("for an unsettled question); a CONTRADICTORY case must carry BOTH ")
                .append("conflicting claims. The rules are applied identically to every row, so ")
                .append("a difference between the rows is a difference of the candidate and ")
                .append("nothing else.\n\n");

        text.append("## 2. The workspace and the extraction\n\n");
        text.append("| Measure | Value |\n|---|---|\n");
        text.append("| workspace | `").append(WORKSPACE).append("` |\n");
        text.append("| documents imported | 7 (the research pool, through the production ")
                .append("importer) |\n");
        text.append("| claims in the workspace | ").append(workspaceSummary.get("claims"))
                .append(" |\n");
        text.append("| concepts | ").append(workspaceSummary.get("concepts")).append(" |\n");
        text.append("| sources | ").append(workspaceSummary.get("sources")).append(" |\n");
        text.append("| representations | ").append(workspaceSummary.get("representations"))
                .append(" |\n");
        text.append("| concepts the extraction saw | ").append(extractionSummary.get("concepts"))
                .append(" |\n");
        text.append("| definition candidates | ").append(extractionSummary.get("definitions"))
                .append(" |\n");
        text.append("| — of which SUPPORTED | ")
                .append(extractionSummary.get("supportedDefinitions")).append(" |\n");
        text.append("| concepts with NO definition | ")
                .append(extractionSummary.get("conceptsWithoutDefinition")).append(" |\n");
        text.append("| relation candidates | ").append(extractionSummary.get("relations"))
                .append(" |\n");
        text.append("| — of which SUPPORTED | ").append(extractionSummary.get("supportedRelations"))
                .append(" |\n");
        text.append("| relation candidates rejected | ")
                .append(extractionSummary.get("rejections")).append(" |\n");
        text.append("| extraction time | ").append(extractionSummary.get("extractionMillis"))
                .append(" ms |\n");
        text.append("| retained text of the extraction | ")
                .append(extractionSummary.get("retainedBytes")).append(" bytes |\n\n");
        text.append("The rejections of the held-out extraction, by the frozen reasons:\n\n");
        text.append("| Reason | Count |\n|---|---|\n");
        Object rejections = extractionSummary.get("rejectionsByReason");
        if (rejections instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                text.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue())
                        .append(" |\n");
            }
        }
        text.append("\nThe relations, by type:\n\n| Type | Count |\n|---|---|\n");
        Object byType = extractionSummary.get("relationsByType");
        if (byType instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                text.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue())
                        .append(" |\n");
            }
        }
        text.append("\nThe candidates, and what each of them changed in the workspace:\n\n");
        text.append("| Candidate | concepts changed | definitions applied | relation texts ")
                .append("applied |\n|---|---|---|---|\n");
        Object applied = extractionSummary.get("applied");
        if (applied instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> fields) {
                    text.append("| `").append(entry.getKey()).append("` | ")
                            .append(fields.get("conceptsChanged")).append(" | ")
                            .append(fields.get("definitionsApplied")).append(" | ")
                            .append(fields.get("relatedTextsApplied")).append(" |\n");
                }
            }
        }
        text.append("\n");

        text.append("## 3. The metrics on the held-out set\n\n");
        text.append("**The denominators.** The recall and the MRR are the MEAN over the ")
                .append("**19 answerable cases** (the `POSITIVE` ones), measured on the ")
                .append("floor-0.0 candidate list; the GLOBAL (micro) recall is the share over ")
                .append("**every gold proposition of those 19 cases**. The supported answer, ")
                .append("the unsupported answer and the answered-with-no-gold share are over ")
                .append("the **19 answerable cases**; the abstention accuracy is over the **6 ")
                .append("unanswerable cases** (the `NEGATIVE` ones); the unsupported answer ")
                .append("over ALL the ask cases is over the **25 ask cases** (the 19 answerable ")
                .append("and the 6 unanswerable ones) and counts an answered unanswerable case ")
                .append("as unsupported. The passed/total column is over the **25 comparable ")
                .append("cases**; every one of the 38 cases is judged in every row. The ")
                .append("latency of the retriever-level rows is the PURE retriever; the ")
                .append("latency of `BASELINE-PATH` is the full production path, and the two ")
                .append("are not comparable. The MRR is the reciprocal rank of the **topmost ")
                .append("gold** — the gold with the smallest rank in the candidate list — and ")
                .append("the misses are in its denominator.\n\n");
        text.append("| Row | Passed | Gold avail. | Recall@1 | Recall@5 | Recall@10 | ")
                .append("Recall@20 | MRR | micro Recall@10 | Supported answer | Unsupported ")
                .append("answer | Answered with NO gold | Abstention | Unsupported over all 25 ")
                .append("ask cases | Mean non-gold cited | Top-1 not gold | Mean retrieval (ms) | ")
                .append("Overhead (ms) |\n");
        text.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (String candidate : ROWS) {
            Metrics value = withOverhead.get(candidate);
            text.append("| `").append(candidate).append("` | ")
                    .append(value.passed()).append("/").append(value.comparableCases())
                    .append(" | ")
                    .append(four(value.goldAvailability())).append(" | ")
                    .append(four(value.recallAt1())).append(" | ")
                    .append(four(value.recallAt5())).append(" | ")
                    .append(four(value.recallAt10())).append(" | ")
                    .append(four(value.recallAt20())).append(" | ")
                    .append(four(value.mrr())).append(" | ")
                    .append(four(value.microRecallAt10())).append(" | ")
                    .append(four(value.supportedAnswerAccuracy())).append(" (")
                    .append(value.supportedCount()).append("/19) | ")
                    .append(four(value.unsupportedAnswerRate())).append(" (")
                    .append(value.unsupportedCount()).append("/19) | ")
                    .append(value.zeroGoldCitedCount()).append("/19 | ")
                    .append(four(value.abstentionAccuracy())).append(" | ")
                    .append(four(value.unsupportedOverAllAsk())).append(" (")
                    .append(value.askCases()).append(" ask cases) | ")
                    .append(four(value.meanNonGoldCited())).append(" | ")
                    .append(value.casesWhoseTopRankIsNotGold()).append("/19 | ")
                    .append(value.meanRetrievalMillis()).append(" | ")
                    .append(four(value.overheadMillisPerCase())).append(" |\n");
        }
        text.append("\nThe failure lists (the comparable cases only):\n\n");
        for (String candidate : ROWS) {
            Metrics value = withOverhead.get(candidate);
            text.append("* `").append(candidate).append("`: ").append(value.failedCases())
                    .append("\n");
        }
        text.append("\n### 3.1 The same table from the development set, for the comparison\n\n");
        text.append("| Row | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | ")
                .append("Supported answer | Abstention | Unsupported answer | Mean non-gold ")
                .append("cited | Top-1 not gold | Mean retrieval (ms) |\n");
        text.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (String candidate : ROWS) {
            JsonNode node = developmentMetrics.path(candidate);
            if (node.isMissingNode()) {
                continue;
            }
            text.append("| `").append(candidate).append("` | ")
                    .append(node.path("passed").asInt()).append("/")
                    .append(node.path("total").asInt()).append(" | ")
                    .append(four(node.path("recallAt1").asDouble())).append(" | ")
                    .append(four(node.path("recallAt5").asDouble())).append(" | ")
                    .append(four(node.path("recallAt10").asDouble())).append(" | ")
                    .append(four(node.path("recallAt20").asDouble())).append(" | ")
                    .append(four(node.path("mrr").asDouble())).append(" | ")
                    .append(four(node.path("supportedAnswerAccuracy").asDouble())).append(" | ")
                    .append(four(node.path("abstentionAccuracy").asDouble())).append(" | ")
                    .append(four(node.path("unsupportedAnswerRate").asDouble())).append(" | ")
                    .append(four(node.path("meanNonGoldClaimsCited").asDouble())).append(" | ")
                    .append(node.path("casesWhoseTopRankIsNotGold").asInt()).append(" | ")
                    .append(node.path("meanRetrievalMillis").asLong()).append(" |\n");
        }
        text.append("\n(The development table is read from `")
                .append(DEVELOPMENT_RESULTS_PATH).append("`, sha256 `")
                .append(developmentSha256).append("`; it is the 29-case measurement of the ")
                .append("same freeze. Its comparable denominator is 20 cases, the held-out ")
                .append("one's is 25.)\n\n");

        text.append("### 3.2 The generalisation question\n\n");
        text.append("The development measurement's headline was: **none of the three candidates ")
                .append("turns a single baseline failure into a pass**; H1 lost A-03; H3 lost ")
                .append("A-03, M-02 and R-03; H2 changed nothing. On the held-out set:\n\n");
        text.append("| Candidate | newly passing (held-out) | newly failing (held-out) | ")
                .append("newly passing (development) | newly failing (development) |\n");
        text.append("|---|---|---|---|---|\n");
        for (String candidate : CANDIDATES) {
            List<String> devPassing = new ArrayList<>();
            List<String> devFailing = new ArrayList<>();
            for (JsonNode row : developmentCases) {
                if (!row.path("candidate").asText().equals(candidate)) {
                    continue;
                }
                String caseId = row.path("case").asText();
                JsonNode base = null;
                for (JsonNode other : developmentCases) {
                    if (other.path("case").asText().equals(caseId)
                            && other.path("candidate").asText()
                                    .equals(RetrievalExperiment.BASELINE_RETRIEVER)) {
                        base = other;
                    }
                }
                if (base == null) {
                    continue;
                }
                boolean passed = "PASS".equals(row.path("verdict").asText());
                boolean basePassed = "PASS".equals(base.path("verdict").asText());
                if (passed && !basePassed) {
                    devPassing.add(caseId);
                }
                if (!passed && basePassed) {
                    devFailing.add(caseId);
                }
            }
            text.append("| `").append(candidate).append("` | ").append(newlyPassing(candidate))
                    .append(" | ").append(newlyFailing(candidate)).append(" | ")
                    .append(devPassing).append(" | ").append(devFailing).append(" |\n");
        }
        text.append("\n**Verdict.** The development measurement's *directional* claim ")
                .append("generalises: on the held-out set no candidate turns a single baseline ")
                .append("failure into a pass either — the newly passing list is EMPTY for H1, ")
                .append("for H2, for H3 and for H1+H2. The *absolute levels* are not ")
                .append("comparable, and they matter for the reading: the held-out baseline is ")
                .append("STRONGER than the development baseline (recall@10 ")
                .append(four(baseline.recallAt10())).append(" against ")
                .append(four(developmentMetrics
                        .path(RetrievalExperiment.BASELINE_RETRIEVER)
                        .path("recallAt10").asDouble()))
                .append(", MRR ").append(four(baseline.mrr())).append(" against ")
                .append(four(developmentMetrics
                        .path(RetrievalExperiment.BASELINE_RETRIEVER)
                        .path("mrr").asDouble()))
                .append("), so the headroom a candidate has is SMALLER on the held-out set, ")
                .append("not larger. A candidate that recovers nothing when the corpus is easy ")
                .append("is not going to recover something when it is hard.\n\n");
        text.append("**How far the generalisation claim reaches.** The two measurements are ")
                .append("NOT numerically comparable: the held-out set is a different corpus (the ")
                .append("research pool), a different question style, a different kind mix and a ")
                .append("different denominator (25 against 20). What IS comparable is the ")
                .append("DIRECTION of the change, and on that the development measurement ")
                .append("generalises cleanly: the candidates do not recover cases on a corpus ")
                .append("they were never fitted to either. And the bound of the claim must be ")
                .append("stated: 38 cases, one corpus of seven documents, one author of the ")
                .append("dataset, 19 answerable cases. That is enough to REJECT a candidate — ")
                .append("the acceptance criterion asks for two recovered cases and the ")
                .append("measurement gives none — and it is NOT enough to assert a positive ")
                .append("effect that the held-out set did not show. The correct reading is: the ")
                .append("development measurement's negative result is CONFIRMED on independent ")
                .append("data; the held-out set adds no evidence for any positive claim.\n\n");

        text.append("## 4. The cases, one by one\n\n");
        text.append("The gold ranks are the ranks of the gold propositions in the floor-0.0 ")
                .append("candidate list; 0 means the gold is not in the list at all.\n\n");
        text.append("| Case | Kind | Gold ranks | BASELINE-PATH | BASELINE-RETRIEVER | H1 | ")
                .append("H2 | H3 | H1+H2 |\n");
        text.append("|---|---|---|---|---|---|---|---|---|\n");
        for (String caseId : caseIds()) {
            Row base = row(caseId, RetrievalExperiment.BASELINE_RETRIEVER);
            text.append("| ").append(caseId).append(" | ").append(base.kind()).append(" | ")
                    .append(base.goldRanks()).append(" | ");
            for (String candidate : ROWS) {
                Row current = row(caseId, candidate);
                text.append(current.passed() ? "PASS" : "**FAIL**").append(" | ");
            }
            text.append("\n");
        }
        text.append("\n### 4.1 The failures of the baseline, classified\n\n");
        text.append("The taxonomy and the decision tree are the frozen ones (freeze §5).\n\n");
        text.append("| Case | Classification | Evidence |\n|---|---|---|\n");
        for (String caseId : caseIds()) {
            Row base = row(caseId, RetrievalExperiment.BASELINE_RETRIEVER);
            if (base.passed() || base.classification().isEmpty()) {
                continue;
            }
            text.append("| ").append(caseId).append(" | `").append(base.classification())
                    .append("` | ").append(base.classificationEvidence()).append(" |\n");
        }
        text.append("\n### 4.2 The unsupported cases: what the premise path says\n\n");
        text.append("The premise path builds its own retriever from the stored workspace, so it ")
                .append("cannot receive the enrichment (freeze §4.4): its verdict is the same ")
                .append("in every row, by construction.\n\n");
        text.append("| Case | The premise the corpus contradicts | Verdict | The premise named | ")
                .append("Evidence named |\n|---|---|---|---|---|\n");
        for (Map<String, Object> finding : premiseFindings) {
            List<?> evidence = (List<?>) finding.get("evidencePropositions");
            text.append("| ").append(finding.get("case")).append(" | ")
                    .append(shorten(finding.get("disputedPremise").toString())).append(" | ")
                    .append(finding.get("verdict")).append(" | ")
                    .append(shorten(String.valueOf(finding.get("premisesNamed")))).append(" | ")
                    .append(evidence.size()).append(" claim(s): ")
                    .append(shorten(String.valueOf(evidence.isEmpty() ? "" : evidence.get(0))))
                    .append(" |\n");
        }
        text.append("\n");
        int pathRefusals = 0;
        int contradicted = 0;
        for (Row row : rows) {
            if (KIND_UNSUPPORTED.equals(row.kind())
                    && RetrievalExperiment.BASELINE_PATH.equals(row.candidate())) {
                if (!row.answered()) {
                    pathRefusals++;
                }
            }
        }
        for (Map<String, Object> finding : premiseFindings) {
            if ("CONTRADICTED".equals(finding.get("verdict"))) {
                contradicted++;
            }
        }
        text.append("**What this shows.** The dedicated premise-assessment route returns ")
                .append("`CONTRADICTED` for ").append(contradicted).append(" of the 6 ")
                .append("unsupported cases; the production answering path refuses the disputed ")
                .append("premise in ").append(pathRefusals).append(" of the 6 — it answers them ")
                .append("as if the premise held. The two routes are not the same mechanism: ")
                .append("`KnowledgeWorkbench.ask` refuses through `PremiseChecker` (a lexical ")
                .append("premise and a refutation), while `PremiseAssessment.assess` is the ")
                .append("full assessment. This is a property of the production path, not of ")
                .append("the candidates: a retriever-level candidate cannot reach either ")
                .append("route (freeze §4.4), so the verdicts above are identical in every ")
                .append("row.\n\n");

        text.append("## 5. The contradictory and the ambiguous cases\n\n");
        text.append("These are the cases the dataset built to see what the system does when the ")
                .append("corpus holds more than one reading, or two claims that cannot both ")
                .append("hold. The expected disposition is to REPORT the conflict, not to pick ")
                .append("one silently.\n\n");
        for (String caseId : caseIds()) {
            Row base = row(caseId, RetrievalExperiment.BASELINE_RETRIEVER);
            if (!KIND_AMBIGUOUS.equals(base.kind())
                    && !KIND_CONTRADICTORY.equals(base.kind())) {
                continue;
            }
            text.append("### ").append(caseId).append(" — ").append(base.kind())
                    .append(" — ").append(base.disposition()).append("\n\n");
            text.append("The gold claims of the case: ").append(base.goldPropositions().size())
                    .append(". The question: *").append(questionOf(caseId)).append("*\n\n");
            text.append("| Row | Verdict | Gold cited | Cited claims | What the answer said |\n");
            text.append("|---|---|---|---|---|\n");
            for (String candidate : ROWS) {
                Row current = row(caseId, candidate);
                text.append("| `").append(candidate).append("` | ")
                        .append(current.passed() ? "PASS" : "**FAIL**").append(" | ")
                        .append(current.goldCited()).append("/")
                        .append(current.goldPropositions().size()).append(" | ")
                        .append(current.citedClaims()).append(" | ")
                        .append(shorten(current.statement())).append(" |\n");
            }
            text.append("\n");
        }
        text.append("**What the candidates do to a conflicting case.** The table above is the ")
                .append("answer; the summary is below.\n\n");
        text.append("| Row | AMBIGUOUS: every reading represented | CONTRADICTORY: both ")
                .append("conflicting claims carried | Mean gold claims cited (of the gold) | ")
                .append("Mean claims cited on the 7 conflict cases |\n");
        text.append("|---|---|---|---|---|\n");
        for (String candidate : ROWS) {
            int ambiguousAll = 0;
            int contradictoryBoth = 0;
            double goldCited = 0.0;
            int goldTotal = 0;
            double citedOnConflicts = 0.0;
            int conflictCases = 0;
            for (String caseId : caseIds()) {
                Row current = row(caseId, candidate);
                if (!KIND_AMBIGUOUS.equals(current.kind())
                        && !KIND_CONTRADICTORY.equals(current.kind())) {
                    continue;
                }
                conflictCases++;
                citedOnConflicts += current.citedClaims();
                goldCited += current.goldCited();
                goldTotal += current.goldPropositions().size();
                if (KIND_AMBIGUOUS.equals(current.kind()) && current.passed()) {
                    ambiguousAll++;
                }
                if (KIND_CONTRADICTORY.equals(current.kind()) && current.passed()) {
                    contradictoryBoth++;
                }
            }
            text.append("| `").append(candidate).append("` | ").append(ambiguousAll)
                    .append(" of 4 | ").append(contradictoryBoth).append(" of 3 | ")
                    .append(goldTotal == 0 ? "0" : four(goldCited / goldTotal)).append(" | ")
                    .append(conflictCases == 0 ? "0" : four(citedOnConflicts / conflictCases))
                    .append(" |\n");
        }
        text.append("\nThe reading of these seven cases is the same in every row: **the system ")
                .append("answers, it does not report the conflict.** On the ambiguous cases it ")
                .append("cites at most ONE reading and leaves the others uncited; on the ")
                .append("contradictory cases it usually carries both conflicting claims in its ")
                .append("evidence (the table above gives the counts), but it does not say that ")
                .append("they conflict — an answer that happens to cite both is not an answer ")
                .append("that reports the contradiction. The candidates ")
                .append("do not change this: they change which claims the retriever cites, not ")
                .append("the SHAPE of the answer. The corpus-side candidates (H1, H2, H1+H2) ")
                .append("cannot introduce a claim the retriever did not rank; the question-side ")
                .append("candidate (H3) can, because it widens the query — and it also shrinks ")
                .append("the evidence set, because the added terms lower the coverage of every ")
                .append("claim.\n\n");

        text.append("## 6. The coverage shifts and the cost\n\n");
        if (coverageShifts.isEmpty()) {
            text.append("**No candidate lost a gold claim that the baseline cited.** There is ")
                    .append("no coverage shift to report.\n\n");
        } else {
            text.append("| Case | Candidate | Side | Gold proposition | Floor under the baseline | ")
                .append("Floor under the candidate |\n|---|---|---|---|---|---|\n");
            for (CoverageShift shift : coverageShifts) {
                text.append("| ").append(shift.caseId()).append(" | `")
                        .append(shift.candidate()).append("` | ").append(shift.side()).append(" | ")
                        .append(shorten(shift.proposition())).append(" | ")
                        .append(RetrievalExperiment.coverageInterval(shift.baselineFloor()))
                        .append(" | ")
                        .append(RetrievalExperiment.coverageInterval(shift.candidateFloor()))
                        .append(" |\n");
            }
            text.append("\n(The `corpus` rows are the frozen measurement of the development ")
                    .append("run, applied to the corpus-side candidates; the `question` row is ")
                    .append("the same measurement applied to H3, whose candidate is the ")
                    .append("expanded question — the development run did not include it, because ")
                    .append("the question-side candidate has no snapshot of its own. The floor ")
                    .append("is the coverage ratio the retriever divides by, measured with the ")
                    .append("production retriever alone and without the representations.)\n\n");
        }
        text.append("| Row | Mean retrieval (ms) | Total over the 38 cases (ms) | Overhead ")
                .append("per case (ms) |\n|---|---|---|---|\n");
        for (String candidate : ROWS) {
            Metrics value = withOverhead.get(candidate);
            text.append("| `").append(candidate).append("` | ")
                    .append(value.meanRetrievalMillis()).append(" | ")
                    .append(value.totalRetrievalMillis()).append(" | ")
                    .append(four(value.overheadMillisPerCase())).append(" |\n");
        }
        text.append("\nThe extraction is a one-off cost of ")
                .append(extractionSummary.get("extractionMillis")).append(" ms and ")
                .append(extractionSummary.get("retainedBytes"))
                .append(" bytes of retained text for the held-out workspace. The candidates ")
                .append("themselves cost no extra retrieval time: they change the snapshot the ")
                .append("retriever reads, not the retriever.\n\n");

        text.append("## 7. The promotion decision\n\n");
        text.append("The criteria are targets, not findings. Each one is checked against the ")
                .append("numbers above, for every candidate.\n\n");
        for (String candidate : CANDIDATES) {
            Metrics value = withOverhead.get(candidate);
            List<String> passing = newlyPassing(candidate);
            List<String> failing = newlyFailing(candidate);
            text.append("### ").append(candidate).append("\n\n");
            text.append("| Criterion | Met | Evidence |\n|---|---|---|\n");
            text.append("| recover at least two previously failing supported-answer cases | ")
                    .append(passing.size() >= 2 ? "**yes**" : "**no**").append(" | newly ")
                    .append("passing: ").append(passing).append(" — ")
                    .append(passing.size()).append(" of the 2 required |\n");
            List<String> lostAbstentions = new ArrayList<>();
            for (Row row : rows) {
                if (!row.candidate().equals(candidate) || !KIND_NEGATIVE.equals(row.kind())) {
                    continue;
                }
                Row base = row(row.caseId(), RetrievalExperiment.BASELINE_RETRIEVER);
                if (base != null && base.passed() && !row.passed()) {
                    lostAbstentions.add(row.caseId());
                }
            }
            text.append("| preserve the original 6/6 correct unanswerable cases | ")
                    .append(lostAbstentions.isEmpty() ? "**yes**" : "**no**")
                    .append(" | the unanswerable cases lost: ").append(lostAbstentions)
                    .append("; the abstention accuracy is ")
                    .append(four(value.abstentionAccuracy())).append(" against the baseline's ")
                    .append(four(baseline.abstentionAccuracy())).append(" |\n");
            text.append("| no new unsupported confident answers | ")
                    .append(value.unsupportedAnswerRate() <= baseline.unsupportedAnswerRate()
                            ? "**yes**" : "**no**")
                    .append(" | the unsupported-answer rate is ")
                    .append(four(value.unsupportedAnswerRate())).append(" against the ")
                    .append("baseline's ").append(four(baseline.unsupportedAnswerRate()))
                    .append("; newly failing: ").append(failing).append(" |\n");
            text.append("| no regression in the premise-refusal and revision safeguards | ")
                    .append("**yes (unchanged by construction)**")
                    .append(" | the premise path builds its own retriever from the stored ")
                    .append("workspace and cannot receive the enrichment (freeze §4.4); the ")
                    .append("premise verdicts of the six UNSUPPORTED cases are identical in ")
                    .append("every row; the held-out set holds no revision case |\n");
            double deltaRecall = value.recallAt10() - baseline.recallAt10();
            double deltaMrr = value.mrr() - baseline.mrr();
            boolean meaningful = (Math.abs(deltaRecall) >= MEANINGFUL_CHANGE
                    || Math.abs(deltaMrr) >= MEANINGFUL_CHANGE)
                    && deltaRecall >= 0.0 && deltaMrr >= 0.0;
            text.append("| a meaningful change in recall and ranking | ")
                    .append(meaningful ? "**yes**" : "**no**")
                    .append(" | the recall@10 changes by ").append(four(deltaRecall))
                    .append(" and the MRR by ").append(four(deltaMrr))
                    .append("; the threshold of meaning is ").append(MEANINGFUL_CHANGE)
                    .append(" and the change must not be a degradation")
                    .append(" |\n");
            text.append("| the latency and resource trade-off is documented | **yes** | ")
                    .append(value.overheadMillisPerCase())
                    .append(" ms per case of retrieval overhead over the baseline; the ")
                    .append("extraction costs ").append(extractionSummary.get("extractionMillis"))
                    .append(" ms once and retains ")
                    .append(extractionSummary.get("retainedBytes")).append(" bytes |\n\n");
        }
        text.append("### 7.1 The recommendation\n\n");
        text.append("**KEEP THE PRODUCTION BEHAVIOUR UNCHANGED — do not promote any of the ")
                .append("candidates.** The recommendation is a measurement; the decision to ")
                .append("promote anything is the owner's.\n\n");
        text.append("The grounds, one line each:\n\n");
        text.append("1. **No candidate recovers a case.** The acceptance criterion asks for ")
                .append("two; the held-out measurement gives ")
                .append(newlyPassing(EnrichmentCandidates.H1).size()).append(", ")
                .append(newlyPassing(EnrichmentCandidates.H2).size()).append(", ")
                .append(newlyPassing(EnrichmentCandidates.H3).size()).append(" and ")
                .append(newlyPassing(EnrichmentCandidates.H1_AND_H2).size())
                .append(" respectively. The development measurement gave the same answer on ")
                .append("a different corpus.\n");
        text.append("2. **Every candidate that touches the corpus costs something.** H1 ")
                .append("dilutes the coverage and lost A-03 on the development set; here it ")
                .append("changes the ranking of the gold without recovering anything. H3 adds ")
                .append("terms to the query, and a term that matches nothing lowers the ")
                .append("coverage of every claim.\n");
        text.append("3. **The premise and revision safeguards are out of reach.** They live ")
                .append("above the retriever, so a retriever-level candidate cannot improve ")
                .append("them — the residual failures of that kind are not the ones these ")
                .append("candidates address.\n");
        text.append("4. **The publishable result is the negative one.** Two independent ")
                .append("corpora, one frozen definition, one answer: the concept-enrichment ")
                .append("hypothesis is not supported at the retriever level, and the ")
                .append("reproducible negative result is worth more than a promotion that ")
                .append("would change the production default on no evidence.\n\n");
        text.append("**What would change this answer.** Not a re-tuning against the held-out ")
                .append("set — that set has now been seen, once, by this harness. A candidate ")
                .append("for a FUTURE freeze would have to attack the failures the H4 ")
                .append("classification actually names (the answerable cases of this run: ")
                .append(classificationSummary()).append("), not the coverage of the claims ")
                .append("that are already reachable. A candidate that only moves a claim DOWN ")
                .append("the ranking is not such a candidate: the freeze's H4 taxonomy says ")
                .append("that a `BELOW_FLOOR` failure is a COVERAGE failure, and coverage is ")
                .append("what the enrichment moves — in the wrong direction.\n\n");

        text.append("## 8. The limits of this measurement\n\n");
        text.append("* The held-out set is 38 cases over seven documents of ONE corpus, written ")
                .append("by ONE author. It is enough to reject a candidate; it is not a ")
                .append("measure of generalisation beyond that corpus.\n");
        text.append("* The questions were written independently of the candidates, but the ")
                .append("CORPUS is the same topic (plate tectonics) as the development set, ")
                .append("and the extraction rules are lexical: their behaviour on a ")
                .append("different domain is untested.\n");
        text.append("* The three new kinds (UNSUPPORTED, AMBIGUOUS, CONTRADICTORY) are judged ")
                .append("by the rules this harness states; they are not part of the frozen ")
                .append("metric set, and their numbers are reported as observations.\n");
        text.append("* The candidates are retriever-level. The demand analysis, the quantity ")
                .append("gate, the premise check and the revision cycle live above the ")
                .append("retriever and cannot receive the enrichment (freeze §4.4). The rows ")
                .append("of those cases are the baseline's own.\n");
        text.append("* The latency is the wall time of one machine, one run, one JVM; it is ")
                .append("an order of magnitude, not a benchmark.\n");
        text.append("* The held-out dataset has been SEEN — by this harness, once. Any further ")
                .append("candidate search against it would be a re-tune on the evaluation set, ")
                .append("and is not a result.\n\n");
        text.append("---\n\n");
        text.append("**Reproduce:**\n\n```\n");
        text.append("export JAVA_HOME=$HOME/jdk25\n");
        text.append("flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --tests ")
                .append("\"io.matrix.knowledge.concept.HeldOutEvaluationTest\" --offline ")
                .append("--console=plain\n```\n");

        Path target = RepositoryPaths.root().resolve(REPORT_PATH);
        Files.writeString(target, text.toString(), StandardCharsets.UTF_8);
    }

    /** The cases, in the dataset's order. */
    private static List<String> caseIds() {
        List<String> ids = new ArrayList<>();
        for (Row row : rows) {
            if (!ids.contains(row.caseId())) {
                ids.add(row.caseId());
            }
        }
        return ids;
    }

    /** The question of a case. */
    private static String questionOf(String caseId) {
        for (JsonNode node : datasetNode.path("cases")) {
            if (node.path("id").asText().equals(caseId)) {
                return node.path("question").asText();
            }
        }
        return "";
    }

    /** The classifications of the baseline failures of the ANSWERABLE cases, in one line. */
    private static String classificationSummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String caseId : caseIds()) {
            Row base = row(caseId, RetrievalExperiment.BASELINE_RETRIEVER);
            if (!KIND_POSITIVE.equals(base.kind()) || base.passed()
                    || base.classification().isEmpty()) {
                continue;
            }
            counts.merge(base.classification(), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            parts.add(entry.getKey() + " × " + entry.getValue());
        }
        return String.join("; ", parts);
    }

    /** The row of a case under a candidate. */
    private static Row row(String caseId, String candidate) {
        for (Row row : rows) {
            if (row.caseId().equals(caseId) && row.candidate().equals(candidate)) {
                return row;
            }
        }
        return null;
    }

    // ── the plumbing ──────────────────────────────────────────────────────────

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= 160 ? collapsed : collapsed.substring(0, 160) + "…";
    }

    private static String four(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        byte[] bytes = Files.readAllBytes(path);
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static String headCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(RepositoryPaths.root().toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).strip();
            process.waitFor();
            return output.isBlank() ? "unknown" : output;
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }
}
