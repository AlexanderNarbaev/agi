package io.matrix.knowledge;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The workbench: the local learning application, without the HTTP surface
 * (owner directive §18, §19).
 *
 * <p>Everything the user can do goes through here, so the HTTP layer stays a
 * thin translation and the behaviour can be tested without a server. The
 * workbench owns exactly one knowledge store and one bundled package; it
 * needs no model provider, no database server and no credentials.
 *
 * <p>The store is opened once and kept open for the life of the process.
 * Everything it holds is durable, so closing the application and starting a
 * new one recovers the workspace exactly as it was — which is the acceptance
 * criterion, not the existence of a save method.
 */
@ApplicationScoped
public class KnowledgeWorkbench implements AutoCloseable {

    /** The identifier of the package that ships with the application. */
    public static final String DEFAULT_WORKSPACE = "photosynthesis";

    /** The identifier of the second bundled domain. */
    public static final String LOGIC_WORKSPACE = "logic";

    private final String databasePath;
    private final String packResource;
    private final String assessmentResource;
    private final String adversarialResource;
    private final String logicPackResource;
    private final String logicAssessmentResource;

    private KnowledgeStore store;
    private final Map<String, AssessmentDataset> datasets = new LinkedHashMap<>();

    @Inject
    public KnowledgeWorkbench(
            @ConfigProperty(name = "matrix.knowledge.path", defaultValue = "data/knowledge/workbench.sqlite")
            String databasePath,
            @ConfigProperty(name = "matrix.knowledge.pack", defaultValue = "knowledge/photosynthesis.pack.json")
            String packResource,
            @ConfigProperty(name = "matrix.knowledge.assessment",
                    defaultValue = "knowledge/photosynthesis.assessment.json")
            String assessmentResource,
            @ConfigProperty(name = "matrix.knowledge.adversarial",
                    defaultValue = "knowledge/photosynthesis.adversarial.json")
            String adversarialResource,
            @ConfigProperty(name = "matrix.knowledge.logic.pack",
                    defaultValue = "knowledge/logic.pack.json")
            String logicPackResource,
            @ConfigProperty(name = "matrix.knowledge.logic.assessment",
                    defaultValue = "knowledge/logic.assessment.json")
            String logicAssessmentResource) {
        this.databasePath = databasePath;
        this.packResource = packResource;
        this.assessmentResource = assessmentResource;
        this.adversarialResource = adversarialResource;
        this.logicPackResource = logicPackResource;
        this.logicAssessmentResource = logicAssessmentResource;
    }

    /** The workbench over a store at a chosen path, for tests and tools. */
    public static KnowledgeWorkbench at(String databasePath) {
        return new KnowledgeWorkbench(databasePath,
                "knowledge/photosynthesis.pack.json", "knowledge/photosynthesis.assessment.json",
                "knowledge/photosynthesis.adversarial.json",
                "knowledge/logic.pack.json", "knowledge/logic.assessment.json");
    }

    /** The knowledge store, opened on first use. */
    synchronized KnowledgeStore store() {
        if (store == null) {
            store = new KnowledgeStore(Paths.get(databasePath));
        }
        return store;
    }

    /** The path of the knowledge store on disk. */
    public String databasePath() {
        return databasePath;
    }

    /**
     * Load the bundled package into a workspace, replacing whatever was
     * there. The import is atomic, so a failed load leaves the previous
     * content intact.
     */
    public KnowledgeSnapshot loadBundledPackage(String workspaceId) {
        KnowledgeSnapshot snapshot = new KnowledgeImporter().importResource(packFor(workspaceId));
        store().importWorkspace(snapshot);
        return snapshot;
    }

    /**
     * The bundled package that belongs to a workspace.
     *
     * <p>Two packages ship with the application: the biology domain and the
     * logic domain. The second exists so that the engine can be shown to
     * work outside the curated package it was first demonstrated on.
     */
    public String packFor(String workspaceId) {
        return LOGIC_WORKSPACE.equals(workspaceId) ? logicPackResource : packResource;
    }

    /** The assessment dataset that belongs to a workspace. */
    public String assessmentFor(String workspaceId) {
        return LOGIC_WORKSPACE.equals(workspaceId) ? logicAssessmentResource : assessmentResource;
    }

    /** Whether a workspace exists. */
    public boolean hasWorkspace(String workspaceId) {
        return store().workspaces().contains(workspaceId);
    }

    /** The identifiers of the workspaces that exist. */
    public List<String> workspaces() {
        return store().workspaces();
    }

    /** The whole workspace as it is stored. */
    public KnowledgeSnapshot workspace(String workspaceId) {
        return store().exportWorkspace(workspaceId);
    }

    /** The assessment dataset bound to a workspace. */
    public AssessmentDataset dataset(String workspaceId) {
        AssessmentDataset cached = datasets.get(workspaceId);
        if (cached == null) {
            cached = AssessmentDataset.loadResource(assessmentFor(workspaceId), workspace(workspaceId));
            datasets.put(workspaceId, cached);
        }
        return cached;
    }

    /**
     * Ask a question of a workspace.
     *
     * <p>The asking is recorded, including the questions the workspace had
     * to refuse: a learning application that only remembers what it knew
     * cannot tell the user what it does not know. The record is a tombstone
     * log - it is kept when the question becomes answerable.
     */
    public Answer ask(String workspaceId, String question, AbstractionLevel level) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        Answer answer = new KnowledgeRetriever(snapshot).answer(question, level);
        recordAsk(workspaceId, question, answer.unmatchedTerms(), answer.answered());
        return answer;
    }

    /** What the workspace does not know. */
    public KnowledgeGapDetector gapDetector(String workspaceId) {
        return new KnowledgeGapDetector(workspace(workspaceId));
    }

    /**
     * Grade one answer and record the outcome.
     *
     * @return the outcome that was recorded
     */
    public AssessmentOutcome assess(String workspaceId, String itemId, String answer,
                                    double claimedConfidence, String sessionId) {
        AssessmentDataset dataset = dataset(workspaceId);
        AssessmentItem item = dataset.items().stream()
                .filter(i -> i.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No assessment item " + itemId));

        double score = AssessmentGrader.score(item, answer);
        boolean correct = AssessmentGrader.isCorrect(item, answer);
        AssessmentOutcome outcome = new AssessmentOutcome(itemId, correct, score, claimedConfidence,
                answer, sessionId == null ? "default" : sessionId, System.currentTimeMillis());
        store().recordOutcome(workspaceId, item, outcome);
        return outcome;
    }

    /** Every outcome recorded for a workspace. */
    public List<AssessmentOutcome> outcomes(String workspaceId) {
        return store().outcomes(workspaceId);
    }

    /**
     * The learning state of every concept in every dimension.
     *
     * <p>The state is DERIVED from the recorded outcomes every time it is
     * asked for, so it cannot drift away from the evidence that produced it.
     */
    public List<LearningState> learningStates(String workspaceId) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        AssessmentDataset dataset = dataset(workspaceId);
        LearningStateEvaluator evaluator = new LearningStateEvaluator(dataset.items());
        List<AssessmentOutcome> outcomes = outcomes(workspaceId);

        List<LearningState> states = new ArrayList<>();
        for (Concept concept : snapshot.concepts()) {
            for (LearningDimension dimension : LearningDimension.values()) {
                LearningState state = evaluator.evaluate(concept.id(), dimension, outcomes,
                        criteriaFor(dimension));
                if (state.stage() != LearningStage.UNKNOWN) {
                    states.add(state);
                }
            }
        }
        return states;
    }

    private static MasteryCriteria criteriaFor(LearningDimension dimension) {
        return dimension == LearningDimension.CALIBRATION
                ? new MasteryCriteria(4, 2, 0, 0.7, 0.8, false)
                : MasteryCriteria.defaults();
    }

    /**
     * The teaching plan for a concept.
     *
     * <p>The composer is given the knowledge and the dataset, but the
     * dataset is only used for its identifier and version: the plan is built
     * from the authored representations and never from the assessment items.
     */
    public TeachingPlan teachingPlan(String workspaceId, String conceptId, AbstractionLevel level) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        TeachingComposer composer = new TeachingComposer(snapshot, dataset(workspaceId));
        return composer.compose(conceptId, level);
    }

    /**
     * Hand the knowledge of one workspace to another.
     *
     * <p>A transfer, not a backup: the identifiers are regenerated for the
     * receiving workspace, because a claim's identifier is derived from the
     * workspace that holds it. The learner's own history is NOT
     * transferred - knowledge that was transferred has not been demonstrated
     * by the new learner, and the receiving workspace's learning state says
     * so by starting empty.
     */
    public KnowledgeTransfer.TransferResult transferKnowledge(String fromWorkspace,
                                                               String toWorkspace) {
        return KnowledgeTransfer.transfer(store(), fromWorkspace, toWorkspace);
    }

    /**
     * Where the learner stands on the ladder of §9 for one concept.
     *
     * <p>The per-dimension states are what §10 asks for; this is the ladder
     * that §9 asks for. The top two stages need evidence that a score
     * cannot provide: understanding needs competence on the causal, transfer
     * and evaluation dimensions, and the teachable stage needs a teach-back
     * that met its expectation.
     *
     * @param workspaceId the workspace
     * @param conceptId the concept
     * @param includeTeachBack whether to run the teach-back, which is the
     *     evidence the highest stage needs
     */
    public CapabilityState capabilityState(String workspaceId, String conceptId,
                                            boolean includeTeachBack) {
        List<LearningState> states = learningStates(workspaceId).stream()
                .filter(s -> s.conceptId().equals(conceptId))
                .toList();

        TeachBackEvaluator.TeachBackResult teachBack = null;
        if (includeTeachBack) {
            teachBack = teachBack(workspaceId, conceptId, AbstractionLevel.MECHANISTIC,
                    CapabilityEvaluator.DEFAULT_EXPECTED_GAIN);
        }
        return CapabilityEvaluator.evaluate(conceptId, states, teachBack);
    }

    /**
     * Where the learner stands on the ladder for every concept that has
     * been assessed.
     *
     * <p>Without a teach-back the highest reachable stage is UNDERSTANDING,
     * and the record says why: the highest stage needs the evidence of a
     * teach-back, and it is not run for every concept on every request.
     */
    public List<CapabilityState> capabilityStates(String workspaceId) {
        List<String> conceptIds = learningStates(workspaceId).stream()
                .map(LearningState::conceptId)
                .distinct()
                .toList();
        return conceptIds.stream()
                .map(conceptId -> capabilityState(workspaceId, conceptId, false))
                .toList();
    }

    /**
     * Register what the system has learned about the learner as knowledge.
     *
     * <p>The rule the acceptance model induced is written into the store as
     * a claim with a source and evidence, so that it is subject to the same
     * rules as everything else: it has a status, a scope, a provenance that
     * can be read, and it can be superseded later.
     *
     * @param workspaceId the workspace to write into
     * @param conceptId the concept the claim is about
     */
    public LearnedRuleRegistrar.Registration registerLearnedRule(String workspaceId, String conceptId) {
        AssessmentDataset dataset = dataset(workspaceId);
        AcceptanceModel model = new AcceptanceModel(dataset.items(), new Random(42));
        AcceptanceModel.Evaluation evaluation = model.trainAndEvaluate(outcomes(workspaceId));

        LearnedRuleRegistrar.Registration registration = LearnedRuleRegistrar.registration(
                workspaceId, conceptId, dataset.id(), evaluation);

        store().applyChanges(workspaceId, new KnowledgeChanges(
                List.of(registration.source()),
                List.of(registration.claim()),
                List.of(registration.evidence()),
                List.of(),
                List.of()));

        return registration;
    }

    /**
     * The metrics of this workspace.
     *
     * <p>The owner directive requires a metrics report that carries the
     * dataset version, the software version, the test procedure and the
     * sample size, that is not a single opaque intelligence score, and that
     * does not hide the metrics that were not measured. The last of those
     * is the one that is easiest to get wrong, so the report ends with the
     * list of what it does NOT measure.
     */
    public MetricsReport metrics(String workspaceId) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        AssessmentDataset dataset = dataset(workspaceId);
        List<LearningState> states = learningStates(workspaceId);
        List<AskedQuestion> asked = askedQuestions(workspaceId);

        List<MetricsReport.DimensionMetric> dimensions = new ArrayList<>();
        for (LearningState state : states) {
            dimensions.add(new MetricsReport.DimensionMetric(
                    state.conceptId(), state.dimension().name(), state.stage().name(), state.score(),
                    state.evidenceCount(), state.variantCount(), state.heldOutCount(),
                    state.evidenceCount() > 0, state.note()));
        }

        int established = 0;
        int contradicted = 0;
        for (Claim claim : snapshot.claims()) {
            switch (claim.status()) {
                case DRAFT, UNKNOWN -> {
                }
                case CONTESTED -> contradicted++;
                default -> established++;
            }
        }
        // The share is of CLAIMS, not of links: a claim with three links
        // still counts once. Counting the links would let the share exceed
        // one, which is nonsense.
        java.util.Set<String> claimsWithEvidence = new java.util.LinkedHashSet<>();
        for (Evidence evidence : snapshot.evidence()) {
            if (snapshot.claims().stream().anyMatch(c -> c.id().equals(evidence.claimId()))) {
                claimsWithEvidence.add(evidence.claimId());
            }
        }
        int withEvidence = claimsWithEvidence.size();

        int answered = 0;
        int refused = 0;
        for (AskedQuestion record : asked) {
            if (record.answered()) {
                answered++;
            } else {
                refused++;
            }
        }
        int distinct = asked.size();
        int recurring = (int) asked.stream()
                .filter(r -> r.timesAsked() >= QuestionConsolidator.PROMOTION_THRESHOLD)
                .count();

        MetricsReport.CoverageMetric coverage = new MetricsReport.CoverageMetric(
                snapshot.concepts().size(), snapshot.claims().size(), established, withEvidence,
                contradicted, snapshot.sources().size(), snapshot.evidence().size(),
                snapshot.representations().size(), snapshot.revisions().size(),
                snapshot.prerequisites().size(),
                snapshot.claims().isEmpty() ? 0.0 : (double) withEvidence / snapshot.claims().size());

        MetricsReport.AskingMetric asking = new MetricsReport.AskingMetric(
                asked.stream().mapToInt(AskedQuestion::timesAsked).sum(), answered, refused,
                distinct, recurring,
                asked.isEmpty() ? 0.0 : (double) refused / asked.size());

        List<String> notes = new ArrayList<>();
        notes.add("The learning state is derived only from recorded outcomes. A dimension with no "
                + "outcome is not reported at all, rather than reported as zero.");
        notes.add("The teach-back measurement is machine-to-machine. It says that the lesson CONTAINED "
                + "the assessed content; it does not say that a human would have learned it.");
        notes.add("The evidence-backed share counts the claims that at least one evidence link points at. "
                + "It says nothing about whether the source is correct.");

        return new MetricsReport(
                workspaceId,
                softwareVersion().value(),
                softwareVersion().source(),
                dataset.id(),
                String.valueOf(dataset.version()),
                "Each dimension is scored from the recorded outcomes of its assessment items: the "
                        + "key-term coverage of the answer against the item's key terms, over the items "
                        + "of that dimension, with the held-out items counted separately and the "
                        + "variant items counted as evidence of transfer. The stage is the lowest "
                        + "stage whose criteria the score and the evidence counts meet.",
                dimensions,
                coverage,
                asking,
                MetricsReport.unmeasuredMetrics(),
                notes);
    }

    /** The version of the running build, and where it was read from. */
    private static VersionInfo softwareVersion() {
        Package pkg = MetricsReport.class.getPackage();
        String fromManifest = pkg == null ? null : pkg.getImplementationVersion();
        if (fromManifest != null && !fromManifest.isBlank()) {
            return new VersionInfo(fromManifest, "the package manifest of the running build");
        }
        try {
            String fromConfig = org.eclipse.microprofile.config.ConfigProvider
                    .getConfig().getOptionalValue("quarkus.application.version", String.class)
                    .orElse(null);
            if (fromConfig != null && !fromConfig.isBlank()) {
                return new VersionInfo(fromConfig, "the quarkus.application.version configuration");
            }
        } catch (RuntimeException e) {
            // The configuration is not reachable in every context; the
            // report says that it could not be read rather than guessing.
        }
        return new VersionInfo("unknown", "the version could not be read from the running build");
    }

    /** A version and the place it was read from. */
    private record VersionInfo(String value, String source) {
    }

    /**
     * The questions the workspace was asked and could not answer.
     *
     * <p>This is the workspace's episodic log, reduced to what a learner
     * needs: what it does not know, and how often it was asked.
     */
    public List<AskedQuestion> askedQuestions(String workspaceId) {
        return store().askedQuestions(workspaceId);
    }

    /**
     * Consolidate the episodic log.
     *
     * <p>A question the workspace can now answer is marked RESOLVED and
     * KEPT - the record of the gap is the evidence that the workspace
     * learned something. A question asked more than once and still
     * unanswered is promoted into the explicit list of gaps.
     */
    public QuestionConsolidator.ConsolidationReport consolidateQuestions(String workspaceId) {
        List<AskedQuestion> asked = store.askedQuestions(workspaceId);
        QuestionConsolidator.ConsolidationReport report =
                new QuestionConsolidator().consolidate(asked, new KnowledgeRetriever(workspace(workspaceId)));

        // The resolutions are written back, so that the tombstone survives a
        // restart and the gap is not rediscovered on every launch.
        for (AskedQuestion resolved : report.resolved()) {
            if (resolved.status() == AskedQuestion.Status.RESOLVED) {
                store.resolveAskedQuestion(workspaceId, resolved.canonicalKey(),
                        resolved.resolutionNote());
            }
        }
        return report;
    }

    /**
     * Record that a question was asked, and whether it was answered.
     *
     * <p>Called by the ask path, so that the workspace learns what it was
     * asked - including the questions it had to refuse.
     */
    public AskedQuestion recordAsk(String workspaceId, String question, List<String> uncoveredTerms,
                                   boolean answered) {
        return store().recordAskedQuestion(workspaceId, question, uncoveredTerms, answered,
                System.currentTimeMillis());
    }

    /**
     * Train the supervised learner on the recorded outcomes and measure it on
     * the held-out ones.
     *
     * <p>This is the supervised path the owner directive asks for, with the
     * labels of the deterministic grader and explicit text features instead
     * of a hash. The grader stays in the production path: it is exact and
     * needs no training. The learned rule is kept because it is INSPECTABLE
     * - it says which shape of answer tends to be accepted, which a lookup
     * table cannot.
     */
    public Map<String, Object> acceptanceModel(String workspaceId) {
        AssessmentDataset dataset = dataset(workspaceId);
        List<AssessmentOutcome> outcomes = outcomes(workspaceId);
        AcceptanceModel model = new AcceptanceModel(dataset.items(), new Random(42));
        AcceptanceModel.Evaluation evaluation = model.trainAndEvaluate(outcomes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluation", evaluation);
        result.put("trainedOn", evaluation.trainedOn());
        result.put("measuredOn", evaluation.measuredOn());
        result.put("heldOutAccuracy", evaluation.heldOutAccuracy());
        result.put("deterministicAccuracy", evaluation.deterministicAccuracy());
        result.put("beatsDeterministic", evaluation.beatsDeterministic());
        result.put("conclusion", evaluation.conclusion());
        result.put("comparison", evaluation.comparison());
        result.put("datasetId", dataset.id());
        result.put("datasetVersion", dataset.version());
        return result;
    }

    /**
     * Apply a correction to what is known.
     *
     * <p>The earlier claim is kept and marked superseded, the new claim
     * carries its source, and the revision records what changed. The changes
     * are applied in one transaction, so a refinement that fails validation
     * leaves the workspace exactly as it was.
     */
    public KnowledgeChanges refine(String workspaceId, KnowledgeRefiner.Refinement request) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        KnowledgeChanges changes = new KnowledgeRefiner().refine(workspaceId, snapshot, request);
        store().applyChanges(workspaceId, changes);
        return changes;
    }

    /**
     * Run the teaching experiment and measure the result.
     *
     * <p>The result is a machine-to-machine measurement. It says whether the
     * lesson contained the content the held-out items ask for, which is
     * necessary for transfer and not sufficient for it.
     */
    public TeachBackEvaluator.TeachBackResult teachBack(String workspaceId, String conceptId,
                                                        AbstractionLevel level, double expectedGain) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        AssessmentDataset dataset = dataset(workspaceId);
        TeachingPlan plan = new TeachingComposer(snapshot, dataset).compose(conceptId, level);
        return new TeachBackEvaluator().run(plan, dataset, snapshot, expectedGain);
    }

    /**
     * Reasons over a workspace and answers a question.
     *
     * <p>This is the evidence-grounded path: the pipeline of the mission's
     * §6.1, with every stage recorded in the result.
     */
    public ReasoningEngine.Result reason(String workspaceId, String question, AbstractionLevel level) {
        return new ReasoningEngine().reason(workspace(workspaceId), question, level);
    }

    /** The recorded inferences of a workspace. */
    public List<Derivation> derivations(String workspaceId) {
        return store().derivations(workspaceId);
    }

    /** The recorded conflicts of a workspace. */
    public List<Contradiction> contradictions(String workspaceId) {
        return store().contradictions(workspaceId);
    }

    /** The goals of a workspace, with their subtasks. */
    public List<LearningGoal> goals(String workspaceId) {
        return new GoalManager(store()).goalsWithSubtasks(workspaceId);
    }

    /** The goals that were under way, for a restarted process to pick up. */
    public List<LearningGoal> resumableGoals(String workspaceId) {
        return new GoalManager(store()).resumable(workspaceId);
    }

    /** Starts a goal. */
    public LearningGoal startGoal(String workspaceId, String description,
                                   List<String> successCriteria, List<String> prerequisiteConceptIds,
                                   List<LearningGoal.Subtask> subtasks, int budgetUnits,
                                   String recoveryInstructions) {
        return new GoalManager(store()).start(workspaceId, description, successCriteria,
                prerequisiteConceptIds, subtasks, budgetUnits, recoveryInstructions);
    }

    /** Records that a subtask was completed. */
    public LearningGoal completeSubtask(String workspaceId, String goalId, String subtaskId,
                                         List<String> evidenceIds, String note) {
        return new GoalManager(store()).completeSubtask(workspaceId, goalId, subtaskId, evidenceIds, note);
    }

    /** Writes a checkpoint for a goal. */
    public LearningGoal checkpointGoal(String workspaceId, String goalId, String checkpoint,
                                        String remainingWork) {
        return new GoalManager(store()).checkpoint(workspaceId, goalId, checkpoint, remainingWork);
    }

    /**
     * The rebuilt teach-back experiment.
     *
     * <p>Four phases over disjoint item sets, an independent closed-book
     * learner, a leakage audit and a calibration measurement. The result is
     * a machine-to-machine measurement and says so.
     */
    public TeachBackExperiment.Result teachBackExperiment(String workspaceId, String conceptId,
                                                          double expectedGain) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        AssessmentDataset dataset = AssessmentDataset.loadResource(
                adversarialResource, snapshot);
        TeachingPlan plan = new TeachingComposer(snapshot, dataset)
                .compose(conceptId, AbstractionLevel.INTRODUCTORY);
        List<Claim> taught = snapshot.claims().stream()
                .filter(claim -> claim.conceptId().equals(conceptId))
                .toList();
        StringBuilder lesson = new StringBuilder();
        for (TeachingStep step : plan.steps()) {
            lesson.append(step.statement()).append(' ');
        }
        return TeachBackExperiment.run(conceptId, snapshot, dataset, taught, lesson.toString(),
                expectedGain);
    }

    /** The storage status, as the persistence screen shows it. */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        Path path = Paths.get(databasePath);
        status.put("databasePath", path.toAbsolutePath().toString());
        status.put("exists", Files.exists(path));
        try {
            status.put("sizeBytes", Files.exists(path) ? Files.size(path) : 0L);
        } catch (IOException e) {
            status.put("sizeBytes", -1L);
        }
        status.put("schemaVersion", KnowledgeStore.SCHEMA_VERSION);
        List<String> workspaces = workspaces();
        status.put("workspaces", workspaces);
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String workspace : workspaces) {
            Map<String, Object> per = new LinkedHashMap<>();
            per.put("concepts", store().concepts(workspace).size());
            per.put("claims", store().claims(workspace).size());
            per.put("sources", store().sources(workspace).size());
            per.put("evidence", store().evidence(workspace).size());
            per.put("representations", store().representations(workspace).size());
            per.put("revisions", store().revisions(workspace).size());
            per.put("assessmentOutcomes", store().outcomeCount(workspace));
            counts.put(workspace, per);
        }
        status.put("counts", counts);
        status.put("ok", true);
        return status;
    }

    @PreDestroy
    void shutdown() {
        close();
    }

    @Override
    public void close() {
        if (store != null) {
            store.close();
            store = null;
        }
    }
}
