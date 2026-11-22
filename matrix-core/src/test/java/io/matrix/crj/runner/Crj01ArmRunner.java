package io.matrix.crj.runner;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.report.ReportProcedureLearner;
import io.matrix.cognitive.report.ReportProcedureMethod;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.SourceRecord;
import io.matrix.crj.evidence.SourceSpanOracle;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the ARM RUNNER: the instrument that produces the comparison's evidence.
 *
 * <p><b>Why it exists.</b> The protocol requires that "every run produces
 * {@code CRJ-01-EVIDENCE.json} with <b>one row per (case, arm)</b>, including the rows that
 * crashed, were refused, were inapplicable, or timed out", and that the arms run under
 * "identical and explicit" budgets, on "identical inputs", with each arm "against its
 * <b>own</b> workspace id and its <b>own</b> output root". None of that instrument existed:
 * the arms were built and measured one at a time, by hand, and no run could yet produce the
 * evidence a measurement is made of.</p>
 *
 * <p><b>What it does.</b> For each arm, in the pre-registered order <b>A, B, C</b>:
 * <ul>
 *   <li>it builds the arm's <b>own</b> workspace and imports the corpus through the
 *       <b>production</b> import path, so every arm sees the same documents in the same
 *       order;</li>
 *   <li>it runs every case of the split against the arm's <b>forced</b> method — arm A the
 *       reference, arm B the replay, arm C the promoted procedure — and <b>records a row for
 *       every one of them, whatever happened</b>;</li>
 *   <li>it scores the served answer with the <b>source-span oracle</b>, so a row says
 *       whether the answer was <b>grounded</b> in the document's own text and not merely
 *       plausible;</li>
 *   <li>it measures the <b>wall</b> and the <b>CPU</b> time of the call — the two G5
 *       metrics that were {@code NOT_INSTRUMENTED} at the baseline;</li>
 *   <li>it writes the evidence artefact, byte-reproducibly.</li>
 * </ul>
 *
 * <p><b>Arm B is measured twice, and the two readings are kept apart.</b> The replay
 * method's precondition is that the identity was already retained, so a replay on a
 * <b>novel</b> task is <b>inapplicable</b> and a replay on an <b>exact repeat</b> answers.
 * The contract requires the replay advantage to be "reported separately from the
 * genuinely-new-task subset", so the runner measures both and keeps them in separate
 * <b>variants</b> of the same arm.</p>
 *
 * <p><b>What it does not do.</b> It does not judge an arm, choose a case, or decide that a
 * result is good. It produces rows; the protocol's metrics read the rows.</p>
 */
final class Crj01ArmRunner {

    /** The arms, in the pre-registered order. */
    enum Arm {
        /** The stateless reference. */
        A,
        /** The exact replay. */
        B,
        /** The governed learned competence. */
        C
    }

    /**
     * One row: one case, one arm, one variant.
     *
     * @param caseId              the case
     * @param arm                 the arm
     * @param variant             the arm's variant: {@code measured}, or {@code novel} for
     *                            the arm B reading that cannot answer a new task
     * @param methodId            the method that ran
     * @param disposition         the action's disposition
     * @param epistemicDisposition the answer's epistemic disposition
     * @param grounded            whether the oracle grounded the answer in the document
     * @param oracleOutcome       the oracle's outcome
     * @param oracleReason        why the oracle decided so
     * @param citedClaimIds       the claims the answer cited
     * @param wallNanos           the wall time of the call
     * @param cpuNanos            the CPU time of the call
     * @param inapplicable        whether the arm could not run the case
     * @param reason              the route's own reason
     * @param error               the error text, when the call failed
     */
    record Row(String caseId,
               String arm,
               String variant,
               String methodId,
               String disposition,
               String epistemicDisposition,
               String premiseStatus,
               boolean grounded,
               String oracleOutcome,
               String oracleReason,
               List<String> citedClaimIds,
               int citations,
               int goldPropositionsMatched,
               long wallNanos,
               long cpuNanos,
               boolean inapplicable,
               boolean effectVerified,
               String artifactSha256,
               String traceRef,
               int conflictCount,
               String reason,
               String error) {
        Row {
            citedClaimIds = List.copyOf(citedClaimIds == null ? List.of() : citedClaimIds);
        }
    }

    /** The oracle's verdict for one case. */
    record Scored(SourceSpanOracle.Verdict verdict, List<SourceSpanOracle.CitedClaim> cited) {
    }

    /** One case of the split. */
    record Case(String id,
                String caseClass,
                String level,
                String question,
                String expectedDisposition,
                String corpusFile,
                String corpusSha256,
                List<String> goldPropositions,
                List<String> goldClaimIds,
                String idempotencyKey,
                boolean positive,
                String expectedField,
                String expectedProvenance) {
        Case {
            goldPropositions = List.copyOf(goldPropositions == null ? List.of() : goldPropositions);
            goldClaimIds = List.copyOf(goldClaimIds == null ? List.of() : goldClaimIds);
        }
    }

    /** The split: the cases and the corpus they are drawn from. */
    record Split(String name, String version, List<Case> cases) {
        Split {
            cases = List.copyOf(cases == null ? List.of() : cases);
        }
    }

    /** The runner's own result: the rows and the summary. */
    record Run(List<Row> rows, Map<String, Map<String, Object>> summary) {
        Run {
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    /** The thread's CPU clock. */
    private static final ThreadMXBean CPU = ManagementFactory.getThreadMXBean();

    /**
     * The candidate questions the arm C procedure is observed on.
     *
     * <p>They are questions of the CORPUS the arm runs over — the plate-tectonics pool —
     * and they are held out from each other: the learner refuses a candidate whose
     * validation case asks the question the procedure was proposed from.</p>
     */
    private static final List<String> CANDIDATE_QUESTIONS = List.of(
            "What drives the movement of tectonic plates at subduction zones?",
            "What evidence shows that the seafloor spreads?",
            "What did the early continental drift theory propose?",
            "How are the Earth's magnetic reversals recorded in rocks?",
            "What happens where two tectonic plates move apart?",
            "What is known about the sinking of dense oceanic crust into the mantle?",
            "How do geologists determine the age of rocks from their magnetism?");

    /** The corpus the split is drawn from. */
    private final ClasspathCorpus corpus;

    /** The corpus files on disk, where the oracle reads them. */
    private final Path corpusDirectory;

    /** The oracle that grounds an answer in the document's own text. */
    private final SourceSpanOracle oracle;

    /**
     * Creates the runner over the research pool.
     *
     * @param corpusDirectory where the corpus files are written for the oracle to read
     * @throws Exception when the corpus cannot be extracted
     */
    Crj01ArmRunner(Path corpusDirectory) throws Exception {
        this.corpus = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        this.corpusDirectory = corpusDirectory;
        Files.createDirectories(corpusDirectory);
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            Files.copy(entry.path(), corpusDirectory.resolve(entry.file()));
        }
        this.oracle = new SourceSpanOracle(corpusDirectory);
    }

    /**
     * Runs every arm over every case and returns the rows.
     *
     * <p>The arms run in the pre-registered order <b>A, B, C</b>, and each arm's workspace
     * and output root are its own, so that no arm can see another's state.</p>
     *
     * @param split    the split
     * @param root     the directory the arms' databases and output roots live in
     * @param budget   the budget every arm runs under — the SAME one, for comparability
     * @return the rows and the summary
     * @throws Exception when an arm's workspace cannot be built
     */
    Run run(Split split, Path root, io.matrix.cognitive.algorithms.ComputeBudget budget)
            throws Exception {
        List<Row> rows = new ArrayList<>();
        for (Arm arm : Arm.values()) {
            Path armRoot = Files.createDirectories(root.resolve(arm.name().toLowerCase()));
            Path database = armRoot.resolve("workbench.sqlite");
            Path outputRoot = Files.createDirectories(armRoot.resolve("output"));
            // the writer's own rule: the output root must not be writable by other users.
            // The repository's own tests set the permissions explicitly for the same
            // reason, and a root that the umask left group-writable is refused by the
            // action service before anything is read.
            Files.setPosixFilePermissions(outputRoot, java.nio.file.attribute.PosixFilePermissions
                    .fromString("rwx------"));
            KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
            try {
                // the SAME corpus, imported through the production path, in the SAME order,
                // into the arm's OWN workspace
                Map<String, SourceRecord> importedSources = new LinkedHashMap<>();
                for (ClasspathCorpus.Entry entry : corpus.entries()) {
                    SourceRecord source = bench.importDocument(
                            "crj01-arm-" + arm.name().toLowerCase(), entry.path(),
                            entry.file(), entry.language()).source();
                    if (!entry.sha256().equals(source.contentHash())
                            || importedSources.putIfAbsent(entry.file(), source) != null) {
                        throw new IllegalStateException("Invalid corpus import for " + entry.file());
                    }
                }
                // the kernel's service is the route's own: the arm's OWN store holds the
                // arm's OWN procedures and the arm's OWN trace, so no arm can see another's
                CognitiveService cognitive = new CognitiveService();
                cognitive.workbench(bench);
                ArtifactReportResource route = routeFor(bench, cognitive, outputRoot, budget);
                String methodId = methodOf(arm, route, bench, cognitive);
                // the oracle resolves the gold's claim ids in THIS arm's workspace: the
                // production claim id embeds the workspace id, and the protocol requires
                // each arm to have its own
                var snapshot = bench.workspace("crj01-arm-" + arm.name().toLowerCase());
                Map<String, String> idsByProposition = new LinkedHashMap<>();
                for (var claim : snapshot.claims()) {
                    idsByProposition.put(claim.proposition(), claim.id());
                }
                SourceSpanOracle armOracle = new SourceSpanOracle(corpusDirectory, idsByProposition);

                for (Case testCase : split.cases()) {
                    if (arm == Arm.B) {
                        // arm B is measured twice: once on a NOVEL task, where the replay
                        // method's precondition cannot hold, and once on an exact REPEAT,
                        // where it can. The two readings are kept in separate variants, and
                        // the contract requires the replay advantage to be reported
                        // separately from the genuinely-new-task subset.
                        rows.add(measure(arm, "novel", route, testCase, methodId, false,
                                armOracle, importedSources));
                        rows.add(measure(arm, "repeat", route, testCase, methodId, true,
                                armOracle, importedSources));
                    } else {
                        rows.add(measure(arm, "measured", route, testCase, methodId, false,
                                armOracle, importedSources));
                    }
                }
            } finally {
                bench.close();
            }
        }
        return new Run(rows, summarise(rows));
    }

    /**
     * The method each arm runs — the FORCED one, because a comparison that lets the kernel
     * choose is a comparison of the kernel's choices, not of the arms.
     *
     * @param arm   the arm
     * @param route the route
     * @param bench the arm's workbench
     * @return the method's id
     */
    private String methodOf(Arm arm, ArtifactReportResource route, KnowledgeWorkbench bench,
                            CognitiveService cognitive) {
        return switch (arm) {
            case A -> "report.journey.reference.v1";
            case B -> "report.journey.replay.v1";
            case C -> armCMethod(route, bench, cognitive);
        };
    }

    /**
     * Arm C's method: the promoted procedure.
     *
     * <p>Arm C has no method of its own until a procedure has been <b>promoted</b> — the
     * promotion is what makes the arm an arm rather than a plan. So the runner promotes one
     * from the corpus itself, on the reference's own reading, and records the procedure's
     * id as the method it runs.</p>
     *
     * @param route the route
     * @param bench the arm's workbench
     * @return the method's id
     */
    private String armCMethod(ArtifactReportResource route, KnowledgeWorkbench bench,
                              CognitiveService cognitive) {
        // the store is NOT closed here: the route reads the journey's trace from it, and a
        // closed store turns every later journey into "failed to read the events"
        CognitiveStore store = cognitive.store();
        {
            var snapshot = bench.workspace("crj01-arm-c");

            // the questions are the corpus's own; the learner refuses a candidate whose
            // validation case asks the question the procedure was proposed from, so the
            // observation and the held-out cases must be DIFFERENT questions
            List<String> questions = new ArrayList<>();
            List<List<String>> claimIdsPerQuestion = new ArrayList<>();
            List<Set<String>> sourceSets = new ArrayList<>();
            for (String question : CANDIDATE_QUESTIONS) {
                var answer = bench.ask("crj01-arm-c", question, AbstractionLevel.INTRODUCTORY);
                if (answer.claims().isEmpty()) {
                    continue;
                }
                questions.add(question);
                claimIdsPerQuestion.add(answer.claims().stream()
                        .map(io.matrix.knowledge.Claim::id).toList());
                Set<String> sources = new LinkedHashSet<>();
                for (var claim : answer.claims()) {
                    sources.add(claim.introducedBySourceId());
                }
                sourceSets.add(sources);
            }
            assertThat(questions.size())
                    .as("the corpus answers at least six of the candidate questions")
                    .isGreaterThanOrEqualTo(6);

            // the SUPPORT: the sources the reference drew on for the held-out cases, and
            // the five questions whose union is the SMALLEST.
            //
            // The restricted reading must cite EXACTLY the reference's claims, so the
            // support must cover every source the reference used for those cases. And the
            // mechanism's premise is that a narrower support suffices: a support that
            // covers most of the corpus saves nothing, which is what the cost curve
            // measured. So the five held-out questions are the narrowest ones available.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                order.add(i);
            }
            order.sort((a, b) -> Integer.compare(sourceSets.get(a).size(),
                    sourceSets.get(b).size()));
            List<Integer> held = new ArrayList<>();
            Set<String> support = new LinkedHashSet<>();
            for (int index : order) {
                if (held.size() == 5) {
                    break;
                }
                held.add(index);
                support.addAll(sourceSets.get(index));
            }
            Integer observed = null;
            for (int i = 0; i < questions.size(); i++) {
                if (!held.contains(i)) {
                    observed = i;
                    break;
                }
            }
            assertThat(observed).as("a question outside the held-out five").isNotNull();

            List<ReportProcedureLearner.Observation> observations = new ArrayList<>();
            List<ReportProcedureLearner.Case> cases = new ArrayList<>();
            int caseIndex = 0;
            for (Integer index : held) {
                cases.add(new ReportProcedureLearner.Case("dev-case-" + (caseIndex++),
                        questions.get(index), AbstractionLevel.INTRODUCTORY));
            }
            observations.add(new ReportProcedureLearner.Observation("dev-episode-0",
                    questions.get(observed), AbstractionLevel.INTRODUCTORY,
                    claimIdsPerQuestion.get(observed), List.copyOf(support)));

            ReportProcedureLearner learner = new ReportProcedureLearner(bench, store);
            ReportProcedureLearner.Outcome outcome = learner.learn("crj01-arm-c", observations,
                    cases, Set.of(), "2026-11-21T00:00:00Z");
            if (!outcome.promoted()) {
                throw new IllegalStateException("arm C's procedure was not promoted: "
                        + outcome.reason());
            }
            ReportProcedureMethod method = new ReportProcedureMethod(outcome.procedure());
            return method.capability().id();
        }
    }

    /**
     * Measures one case on one arm.
     *
     * @param arm       the arm
     * @param variant   the arm's variant
     * @param route     the route
     * @param testCase  the case
     * @param methodId  the method to force
     * @param prime     whether the request is run once before the measured run, so that the
     *                  replay method's precondition holds
     * @return the row
     */
    private Row measure(Arm arm, String variant, ArtifactReportResource route, Case testCase,
                        String methodId, boolean prime, SourceSpanOracle armOracle,
                        Map<String, SourceRecord> importedSources) {
        String workspaceId = "crj01-arm-" + arm.name().toLowerCase();
        // the report name is NOT the case's id: a name binds to ONE action identity, and
        // every case carries a FRESH idempotency key, so a shared name would make every
        // case after the first be refused with "the report name is already bound to
        // another action identity". The route derives the name from the identity, which is
        // unique per case and the SAME for the priming run and the measured one.
        // the record's order is (question, reportName, consent, idempotencyKey, level)
        ArtifactReportResource.ReportRequest request = new ArtifactReportResource.ReportRequest(
                testCase.question(), null, Boolean.TRUE, testCase.idempotencyKey(),
                testCase.level());
        if (prime) {
            // The priming run: the replay method's precondition is that the identity was
            // already retained, and the priming run is what retains it. It is NOT measured.
            //
            // And it is primed with the REFERENCE method, not with the replay: the replay
            // method cannot prime itself, because its own precondition is that the identity
            // was ALREADY retained — a replay that primed itself would refuse every time
            // and the arm would measure nothing but its own refusal.
            route.journey(workspaceId, request, "report.journey.reference.v1");
        }
        long cpuBefore = CPU.getCurrentThreadCpuTime();
        long wallStart = System.nanoTime();
        ArtifactReportResource.JourneyResult result;
        try {
            result = route.journey(workspaceId, request, methodId);
        } catch (RuntimeException failure) {
            long wall = System.nanoTime() - wallStart;
            long cpu = CPU.getCurrentThreadCpuTime() - cpuBefore;
            // a CRASH is a ROW, not a missing row: the protocol retains it with its error
            return new Row(testCase.id(), arm.name(), variant, methodId, "", "", "", false,
                    "CRASHED", failure.getMessage(), List.of(), 0, 0, wall, cpu, true, false,
                    "", "", 0, "the arm threw", failure.toString());
        }
        long wall = System.nanoTime() - wallStart;
        long cpu = CPU.getCurrentThreadCpuTime() - cpuBefore;

        var outcome = result.outcome();
        List<SourceSpanOracle.CitedClaim> cited = new ArrayList<>();
        for (var evidence : evidenceOf(result)) {
            cited.add(new SourceSpanOracle.CitedClaim(evidence.claimId(), evidence.proposition(),
                    evidence.status(), evidence.sourceId()));
        }
        SourceSpanOracle.Verdict verdict = score(testCase, result, cited, armOracle, importedSources);
        boolean inapplicable = "REFUSED".equals(outcome.disposition())
                && !verdict.grounded();
        // a REFUSED row carries WHY it was refused: the refusal's own text is the row's
        // reason, and a row that says only "REFUSED" is a row that cannot be read
        // the row carries BOTH reasons: the PLAN's (why this method was chosen or forced)
        // and the REFUSAL's (why it could not run). A row that carries only one of them
        // cannot be read.
        String reason = outcome.reason();
        if (outcome.refusalReasons() != null && !outcome.refusalReasons().isEmpty()) {
            reason = (reason == null ? "" : reason + " | ")
                    + "refused: " + String.join("; ", outcome.refusalReasons());
        }
        return new Row(testCase.id(), arm.name(), variant, outcome.methodId(),
                outcome.disposition(), outcome.epistemicDisposition(), outcome.premiseStatus(),
                verdict.grounded(), verdict.outcome().name(), verdict.reason(),
                outcome.evidenceClaimIds(), cited.size(), matchedGoldPropositions(testCase, cited),
                wall, cpu, inapplicable, outcome.effectVerified(), outcome.sha256(),
                String.join(",", outcome.tracePhases()), outcome.conflictCount(), reason, null);
    }

    /**
     * The evidence a response carries.
     *
     * @param result the journey's result
     * @return the evidence references
     */
    private static List<ArtifactReportResource.EvidenceReference> evidenceOf(
            ArtifactReportResource.JourneyResult result) {
        Object body = result.response() == null ? null : result.response().getEntity();
        if (body instanceof ArtifactReportResource.ReportResponse response) {
            return response.evidence();
        }
        return List.of();
    }

    /**
     * Scores a served answer with the oracle.
     *
     * @param testCase the case
     * @param result   the journey's result
     * @param cited    the claims the answer cited
     * @param armOracle the oracle for this workspace
     * @param importedSources canonical import receipts keyed by manifest file
     * @return the verdict
     */
    static SourceSpanOracle.Verdict score(Case testCase, ArtifactReportResource.JourneyResult result,
                                          List<SourceSpanOracle.CitedClaim> cited,
                                          SourceSpanOracle armOracle,
                                          Map<String, SourceRecord> importedSources) {
        String expectedSource = null;
        if (testCase.expectedProvenance() != null && !testCase.expectedProvenance().isBlank()) {
            // CRJ-01 v1 pins the refuting document through the case's corpus file and digest.
            // Its historical source ID was assigned in a different workspace.
            SourceRecord imported = importedSources.get(testCase.corpusFile());
            if (imported == null || !testCase.corpusFile().equals(imported.filename())
                    || !testCase.corpusSha256().equals(imported.contentHash())) {
                throw new IllegalArgumentException("No matching workspace import for "
                        + testCase.id() + ": " + testCase.corpusFile());
            }
            expectedSource = imported.sourceId();
        }
        SourceSpanOracle.GoldCase gold = new SourceSpanOracle.GoldCase(testCase.id(),
                testCase.corpusFile(), testCase.corpusSha256(), testCase.goldPropositions(),
                testCase.goldClaimIds(), testCase.positive(), fieldOf(testCase.expectedField()),
                testCase.expectedDisposition(), expectedSource);
        Object entity = result.response() == null ? null : result.response().getEntity();
        List<String> refutingSources = List.of();
        if (entity instanceof ArtifactReportResource.ReportResponse response
                && response.premise() != null) {
            refutingSources = response.premise().refutingSourceIds();
        }
        var outcome = result.outcome();
        return armOracle.score(gold, new SourceSpanOracle.ServedAnswer(outcome.disposition(),
                outcome.epistemicDisposition(), outcome.premiseStatus(), cited, refutingSources));
    }

    /**
     * How many of the case's gold propositions the answer cited, byte-identically.
     *
     * <p>This is the numerator of the protocol's M-A1 and the denominator of M-A3, and it is
     * counted here, against the ANSWER's own cited claims — not against the oracle's
     * verdict, so that a row carries the number a reader can check.</p>
     *
     * @param testCase the case
     * @param cited    the claims the answer cited
     * @return the count
     */
    private static int matchedGoldPropositions(Case testCase,
                                               List<SourceSpanOracle.CitedClaim> cited) {
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

    /**
     * The field a negative case's expected disposition lives in.
     *
     * @param name the field's name
     * @return the field
     */
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

    /**
     * The route an arm runs through.
     *
     * @param bench      the arm's workbench
     * @param outputRoot the arm's own output root
     * @param budget     the budget every arm runs under
     * @return the route
     */
    private static ArtifactReportResource routeFor(KnowledgeWorkbench bench,
                                                   CognitiveService cognitive, Path outputRoot,
                                                   io.matrix.cognitive.algorithms.ComputeBudget budget) {
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = new ArtifactActionService(true, outputRoot.toString());
        route.planner = new io.matrix.cognitive.report.ReportJourneyPlanner();
        route.cognitive = cognitive;
        route.budget = budget;
        return route;
    }

    /**
     * The summary: one line per arm and variant.
     *
     * @param rows the rows
     * @return the summary
     */
    private static Map<String, Map<String, Object>> summarise(List<Row> rows) {
        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        for (Row row : rows) {
            String key = row.arm() + "/" + row.variant();
            Map<String, Object> line = summary.computeIfAbsent(key, k -> {
                Map<String, Object> fresh = new LinkedHashMap<>();
                fresh.put("cases", 0);
                fresh.put("grounded", 0);
                fresh.put("inapplicable", 0);
                fresh.put("crashed", 0);
                fresh.put("wallNanos", 0L);
                fresh.put("cpuNanos", 0L);
                return fresh;
            });
            line.put("cases", (int) line.get("cases") + 1);
            if (row.grounded()) {
                line.put("grounded", (int) line.get("grounded") + 1);
            }
            if (row.inapplicable()) {
                line.put("inapplicable", (int) line.get("inapplicable") + 1);
            }
            if ("CRASHED".equals(row.oracleOutcome())) {
                line.put("crashed", (int) line.get("crashed") + 1);
            }
            line.put("wallNanos", (long) line.get("wallNanos") + row.wallNanos());
            line.put("cpuNanos", (long) line.get("cpuNanos") + row.cpuNanos());
        }
        return summary;
    }

    /**
     * Reads a split from a JSON artefact.
     *
     * @param path the artefact
     * @return the split
     * @throws Exception when the artefact cannot be read
     */
    static Split readSplit(Path path) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper json =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        Map<?, ?> parsed = json.readValue(Files.readString(path, StandardCharsets.UTF_8),
                Map.class);
        List<Case> cases = new ArrayList<>();
        Object raw = parsed.get("cases");
        if (raw instanceof List<?> entries) {
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                List<String> propositions = new ArrayList<>();
                List<String> claimIds = new ArrayList<>();
                if (map.get("goldClaims") instanceof List<?> golds) {
                    for (Object gold : golds) {
                        if (gold instanceof Map<?, ?> goldMap) {
                            propositions.add(String.valueOf(goldMap.get("proposition")));
                            claimIds.add(String.valueOf(goldMap.get("claimId")));
                        }
                    }
                }
                boolean positive = !Boolean.FALSE.equals(map.get("positive"));
                cases.add(new Case(String.valueOf(map.get("id")),
                        String.valueOf(map.get("class")), String.valueOf(map.get("level")),
                        String.valueOf(map.get("question")),
                        String.valueOf(map.get("expectedDisposition")),
                        String.valueOf(map.get("corpusFile")),
                        String.valueOf(map.get("corpusSha256")), propositions, claimIds,
                        String.valueOf(map.get("idempotencyKey")), positive,
                        String.valueOf(map.get("expectedField")),
                        map.get("expectedProvenance") == null ? null
                                : String.valueOf(map.get("expectedProvenance"))));
            }
        }
        return new Split(String.valueOf(parsed.get("split")), String.valueOf(parsed.get("version")),
                cases);
    }
}
