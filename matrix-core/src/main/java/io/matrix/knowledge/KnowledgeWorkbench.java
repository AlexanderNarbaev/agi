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

    private final String databasePath;
    private final String packResource;
    private final String assessmentResource;

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
            String assessmentResource) {
        this.databasePath = databasePath;
        this.packResource = packResource;
        this.assessmentResource = assessmentResource;
    }

    /** The workbench over a store at a chosen path, for tests and tools. */
    public static KnowledgeWorkbench at(String databasePath) {
        return new KnowledgeWorkbench(databasePath,
                "knowledge/photosynthesis.pack.json", "knowledge/photosynthesis.assessment.json");
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
        KnowledgeSnapshot snapshot = new KnowledgeImporter().importResource(packResource);
        store().importWorkspace(snapshot);
        return snapshot;
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
            cached = AssessmentDataset.loadResource(assessmentResource, workspace(workspaceId));
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
        recordAsk(workspaceId, question, answer.unmatchedTerms());
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
    public AskedQuestion recordAsk(String workspaceId, String question, List<String> uncoveredTerms) {
        return store.recordAskedQuestion(workspaceId, question, uncoveredTerms,
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
