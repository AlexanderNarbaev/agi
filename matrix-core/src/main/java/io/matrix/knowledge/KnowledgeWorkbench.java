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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * The assessment dataset bound to a workspace.
     *
     * <p>RECON-W42.7. The dataset is the package's items PLUS the items
     * the generator wrote for the concepts the package does not cover. A
     * concept the workspace acquired from a document had nothing to
     * practise with before, and the mission's goal says the system must
     * "practise applying the concept".
     */
    public AssessmentDataset dataset(String workspaceId) {
        AssessmentDataset cached = datasets.get(workspaceId);
        if (cached == null) {
            cached = AssessmentDataset.loadResource(assessmentFor(workspaceId), workspace(workspaceId));
            datasets.put(workspaceId, cached);
        }
        // The merge must be IDEMPOTENT: the cached dataset already holds the
        // generated items after the first call, and adding them again
        // would give the workspace two items with the same id. The
        // evaluator refuses such a dataset, and it is right to.
        List<AssessmentItem> generated = store().assessmentItems(workspaceId);
        if (!generated.isEmpty()) {
            Set<String> present = new LinkedHashSet<>();
            for (AssessmentItem item : cached.items()) {
                present.add(item.id());
            }
            List<AssessmentItem> missing = new ArrayList<>();
            for (AssessmentItem item : generated) {
                if (present.add(item.id())) {
                    missing.add(item);
                }
            }
            if (!missing.isEmpty()) {
                cached = AssessmentDataset.withAdditionalItems(cached, missing);
                datasets.put(workspaceId, cached);
            }
        }
        return cached;
    }

    /**
     * Generates the assessment items of the concepts the dataset does not
     * cover.
     *
     * <p>The generation is deterministic: the same claims produce the same
     * items, with the same ids, in the same order. The items are written
     * to the store, so they survive a restart.
     *
     * @param workspaceId the workspace
     * @return what the generation did
     */
    public AssessmentGenerator.Result generateAssessment(String workspaceId) {
        // The covered concepts are the PACKAGE's concepts. The first
        // version read them from the cache, and the cache is empty before
        // the dataset is loaded - so the generator wrote items for the
        // concepts the package already covers, and the learner would have
        // practised the same concept twice.
        AssessmentDataset packaged = AssessmentDataset.loadResource(assessmentFor(workspaceId),
                workspace(workspaceId));
        Set<String> covered = new LinkedHashSet<>();
        for (AssessmentItem item : packaged.items()) {
            covered.add(item.conceptId());
        }
        AssessmentGenerator.Result result = AssessmentGenerator.generate(workspace(workspaceId),
                covered);
        if (result.wroteAnything()) {
            store().recordAssessmentItems(workspaceId, result.items());
            datasets.remove(workspaceId);
        }
        return result;
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
        // The conversation resolves the reference BEFORE the retrieval, so
        // that "Explain this in more detail" reaches the concept the last
        // turn was about. The original question is what the answer carries.
        DialogueContext context = context(workspaceId);
        String expanded = context.expand(question);
        String resolvedSubject = expanded.equals(question) ? null : context.resolveSubject(question);
        Answer answer = new KnowledgeRetriever(snapshot)
                .answer(expanded.equals(question) ? question : expanded, level);


        // WHAT THE QUESTION DEMANDS IS CHECKED BEFORE THE ANSWER IS.
        //
        // RECON-W42.1. The support score could not tell an answerable
        // question from an unanswerable one: on the W42 benchmark the two
        // distributions overlap (the answerable items scored 0.25 to 0.88,
        // the unanswerable ones the system wrongly answered scored 0.22 to
        // 0.63), so no threshold separates them. The decision needs
        // information the score does not carry - what KIND of answer the
        // question asks for.
        // The referent is settled when the CONVERSATION settled it, or
        // when the question names a subject of its own - and a question
        // that names a subject is covered by the workspace, so the
        // coverage says so.
        Set<String> contentTerms = QuestionDemand.contentTerms(question);
        Set<String> unmatchedTerms = new LinkedHashSet<>(answer.unmatchedTerms());
        boolean referentSettled = !expanded.equals(question)
                || context.hasSubject()
                || QuestionDemand.coverage(unmatchedTerms, contentTerms) > 0.0;
        QuestionDemand.Analysis demand = QuestionDemand.analyse(question,
                unmatchedTerms, contentTerms, referentSettled);
        answer = applyDemand(answer, demand);
        answer = applyPremise(answer, question, snapshot);

        if (!expanded.equals(question)) {
            // The answer carries the question the USER wrote, and says what
            // it was read as. The mission's §6 requires the original message
            // to be preserved alongside any normalized representation.
            answer = new Answer(question, answer.level(), answer.answered(),
                    answer.abstentionReason(), answer.statement(), answer.supportScore(),
                    answer.support(), answer.concepts(), answer.claims(), answer.sources(),
                    withNote(answer.limitations(),
                            ResponseFrame.template(TextProfiles.detect(question),
                                    "The reference was resolved against the conversation: the "
                                            + "question was read as being about \"{concept}\".",
                                    Map.of("concept", resolvedSubject))),
                    answer.gaps(), answer.unmatchedTerms());
        }
        answer = applyCorrection(context, question, answer);
        answer = applyLanguageNote(question, answer);
        answer = applyWeakenedBindings(workspaceId, answer);
        recordAsk(workspaceId, question, answer.unmatchedTerms(), answer.answered());
        context.record(question, answer);
        persistTurn(workspaceId, context);
        return answer;
    }

    /**
     * Persists the last turn of the conversation.
     *
     * <p>The turns that fall out of the working memory are MARKED, not
     * deleted: the mission's §8 forbids destroying the historical
     * evidence, and the record of what was asked and how it was classified
     * is evidence.
     *
     * @param workspaceId the workspace
     * @param context the conversation
     */
    private void persistTurn(String workspaceId, DialogueContext context) {
        List<DialogueContext.Turn> turns = context.turns();
        if (turns.isEmpty()) {
            return;
        }
        DialogueContext.Turn last = turns.get(turns.size() - 1);
        store().recordDialogueTurn(workspaceId, last);
        store().forgetOldDialogueTurns(workspaceId, DialogueContext.MAXIMUM_TURNS);
    }

    /**
     * The answer says which language the knowledge is in.
     *
     * <p>The mission's §6 asks for a multilingual generation. The honest
     * version of that, with the knowledge in one language and the question
     * in another, is to SAY SO: the answer is written in the language the
     * workspace holds, and a question in another language is told that.
     *
     * <p>What the system does not do is substitute the terms of a lexicon
     * into the answer text. A word-by-word substitution of a few hundred
     * terms produces a text that reads as though it were translated and is
     * not — and the mission's §6 warns that a translation is not evidence
     * that two propositions are equivalent. A note is honest; a
     * substituted answer is not.
     */
    private Answer applyLanguageNote(String question, Answer answer) {
        String questionLanguage = TextProfiles.detect(question);
        if (!answer.answered() || "unknown".equals(questionLanguage)) {
            return answer;
        }
        String knowledgeLanguage = knowledgeLanguage();
        if (knowledgeLanguage.equals(questionLanguage)) {
            return answer;
        }
        return new Answer(answer.question(), answer.level(), answer.answered(),
                answer.abstentionReason(), answer.statement(), answer.supportScore(),
                answer.support(), answer.concepts(), answer.claims(), answer.sources(),
                withNote(answer.limitations(),
                        ResponseFrame.template(questionLanguage,
                                "The question is in {question} and the knowledge is in {knowledge}.",
                                Map.of("question", questionLanguage,
                                        "knowledge", knowledgeLanguage))
                                + " The terms are bridged by the bilingual lexicon, which says "
                                + "which WORDS name the same thing; it does not translate the "
                                + "answer."),
                answer.gaps(), answer.unmatchedTerms());
    }

    /**
     * The language the workspace's knowledge is in.
     *
     * <p>Detected from the knowledge itself, and reported as unknown when
     * the detection is not decisive.
     */
    public String knowledgeLanguage() {
        StringBuilder text = new StringBuilder();
        KnowledgeSnapshot snapshot = workspace(DEFAULT_WORKSPACE);
        for (Claim claim : snapshot.claims()) {
            text.append(claim.proposition()).append(' ');
        }
        String detected = TextProfiles.detect(text.toString());
        return "unknown".equals(detected) ? "en" : detected;
    }

    /**
     * What happens when the user corrects the system.
     *
     * <p>The mission's §5: "if the user corrects an answer, the system
     * re-evaluates affected knowledge and updates internal models" — and
     * never overwrites it. The re-evaluation here is the refinement path,
     * which supersedes the claim and records a new one with its evidence;
     * it cannot happen without the user's correction and its evidence, so
     * the system's answer names the claims that are open to correction and
     * asks for the corrected statement.
     *
     * <p>Nothing is overwritten here: the claims are named, and the
     * knowledge changes only when the correction arrives with evidence.
     */
    private Answer applyCorrection(DialogueContext context, String question, Answer answer) {
        if (!DialogueAct.isCorrection(question)) {
            return answer;
        }
        List<String> claimIds = context.lastTurnClaimIds();
        if (claimIds.isEmpty()) {
            return answer;
        }
        List<String> gaps = new ArrayList<>(answer.gaps());
        gaps.add("The correction names " + claimIds.size() + " claim(s) of the previous answer: "
                + String.join(", ", claimIds) + ". A correction needs the corrected statement AND "
                + "the evidence for it; until both arrive, the claims stand unchanged and nothing "
                + "is overwritten.");
        // The answer is NOT a knowledge answer: the system did not answer a
        // question, it asked for the correction's evidence. Saying so is
        // honest; answering the question anyway would be answering a
        // question the user did not ask.
        return new Answer(answer.question(), answer.level(), false,
                "The user corrected the previous answer. The affected claims are named, and "
                        + "nothing is overwritten until the correction and its evidence arrive.",
                "", answer.supportScore(), answer.support(), answer.concepts(), answer.claims(),
                answer.sources(), answer.limitations(), gaps, answer.unmatchedTerms());
    }

    /** The limitations with one more note. */
    private List<String> withNote(List<String> limitations, String note) {
        List<String> extended = new ArrayList<>(limitations);
        extended.add(note);
        return extended;
    }

    /**
     * The conversation of a workspace.
     *
     * <p>The mission's §6 asks for a bounded PERSISTED conversation
     * history. The context is bounded in the number of turns and in the
     * number of concepts each turn keeps, and it is now PERSISTED: the
     * first time a workspace's context is asked for, the turns the store
     * holds are loaded into it, so a restart does not lose the referent
     * that "explain this in more detail" depends on.
     *
     * <p>The store's own {@code asked_question} table remains the durable
     * record of what was asked and whether the workspace could answer it.
     */
    public DialogueContext context(String workspaceId) {
        return contexts.computeIfAbsent(workspaceId, key -> {
            DialogueContext context = new DialogueContext(key);
            context.load(store().dialogueTurns(workspaceId, DialogueContext.MAXIMUM_TURNS));
            return context;
        });
    }

    /**
     * Applies the demand analysis to a retrieved answer.
     *
     * <p>Three of the four demands are refusals, each with its reason; the
     * fourth — an ambiguous question — becomes a clarification, because the
     * mission's §6 says an ambiguous request should ask rather than guess.
     */
    /**
     * The premise of the question is checked against the workspace.
     *
     * <p>RECON-W42.5. A question that asserts a premise - "Since X, ..." -
     * asks its question UNDER that premise. Answering it as if the premise
     * held would take the premise for granted, and the premise is the part
     * the user may have got wrong.
     *
     * <p>The workspace already holds what is needed: the photosynthesis
     * package holds the misconception as a REFUTED claim, and the logic
     * package holds a verified claim that denies the second premise. The
     * check is against those claims and not against a world model.
     */
    private Answer applyPremise(Answer answer, String question, KnowledgeSnapshot snapshot) {
        String premise = PremiseChecker.premiseOf(question);
        if (premise == null) {
            return answer;
        }
        Claim refutation = PremiseChecker.refutation(premise, snapshot);
        if (refutation == null) {
            return answer;
        }
        List<String> limitations = new ArrayList<>(answer.limitations());
        limitations.add("The question asserts a premise that this workspace contradicts: \""
                + premise + "\".");
        limitations.add("The claim that contradicts it: \"" + refutation.proposition() + "\".");
        return new Answer(answer.question(), answer.level(), false,
                "The premise of the question is contradicted by the workspace: \"" + premise
                        + "\". The workspace holds \"" + refutation.proposition() + "\", "
                        + "so answering the question under that premise would take it for granted.",
                "", answer.supportScore(), answer.support(), answer.concepts(), answer.claims(),
                answer.sources(), limitations, answer.gaps(), answer.unmatchedTerms());
    }

    private Answer applyDemand(Answer answer, QuestionDemand.Analysis demand) {
        if (demand.kind() == QuestionDemand.Kind.KNOWLEDGE
                && demand.demandCoverage() >= QuestionDemand.MINIMUM_DEMAND_COVERAGE) {
            return answer;
        }
        if (demand.kind() == QuestionDemand.Kind.AMBIGUOUS) {
            List<String> limitations = new ArrayList<>(answer.limitations());
            limitations.add(demand.reason());
            limitations.add(demand.clarification());
            return new Answer(answer.question(), answer.level(), false,
                    demand.reason() + " The system asks rather than guesses: "
                            + demand.clarification(),
                    "", answer.supportScore(), answer.support(), answer.concepts(),
                    answer.claims(), answer.sources(), limitations, answer.gaps(),
                    answer.unmatchedTerms());
        }
        String reason = demand.kind() == QuestionDemand.Kind.KNOWLEDGE
                ? "The workspace covers only " + Math.round(demand.demandCoverage() * 100)
                        + "% of the question's content terms, and a question the workspace does "
                        + "not cover is not one to guess at."
                : demand.reason();
        List<String> limitations = new ArrayList<>(answer.limitations());
        limitations.add(reason);
        if (demand.kind() == QuestionDemand.Kind.KNOWLEDGE
                && !demand.unmatchedTerms().isEmpty()) {
            limitations.add("Not covered by the workspace: "
                    + String.join(", ", demand.unmatchedTerms()));
        }
        return new Answer(answer.question(), answer.level(), false, reason,
                "", answer.supportScore(), answer.support(), answer.concepts(),
                answer.claims(), answer.sources(), limitations, answer.gaps(),
                answer.unmatchedTerms());
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
                answer, sessionId == null ? "default" : sessionId, System.currentTimeMillis(),
                item.heldOut());
        store().recordOutcome(workspaceId, item, outcome);
        // The state machine reconciles after every outcome: this is the
        // moment the evidence changed, so this is the moment a capability
        // can advance or regress.
        reconcileLearningStates(workspaceId, outcome.itemId());
        return outcome;
    }

    /**
     * Reconciles the recorded learning states with the evidence and records
     * the transitions.
     *
     * <p>The computed state is the reading of the evidence now; the recorded
     * state is the reading last time. Where they differ, the capability
     * moved, and the move is written down with its cause — including a
     * regression, which is recorded as a regression and never erased.
     *
     * @param workspaceId the workspace to reconcile
     * @param causeId the outcome that caused the reconciliation, or null
     * @return the transitions that happened
     */
    public List<LearningTransition> reconcileLearningStates(String workspaceId, String causeId) {
        List<LearningState> computed = learningStates(workspaceId);
        Map<String, LearningState> recorded = store().recordedLearningStates(workspaceId);
        Map<String, LearningState> current = new LinkedHashMap<>();
        for (LearningState state : computed) {
            current.put(LearningStateMachine.key(state.conceptId(), state.dimension()), state);
        }
        String occurredAt = java.time.Instant.now().toString();
        List<LearningTransition> transitions =
                new LearningStateMachine().reconcile(workspaceId, computed, recorded, causeId,
                        occurredAt);
        if (!transitions.isEmpty() || !current.equals(recorded)) {
            store().recordLearningStates(workspaceId, current, transitions);
        }
        return transitions;
    }

    /** The conversations of the workspaces. */
    private final Map<String, DialogueContext> contexts = new LinkedHashMap<>();

    /** The recorded transitions of a workspace, oldest first. */
    public List<LearningTransition> learningTransitions(String workspaceId) {
        return store().learningTransitions(workspaceId);
    }

    /**
     * The store, for the acquisition's stages that need it.
     *
     * <p>RECON-W42.7. The validation stage of the acquisition runs against
     * the store, so that the importer and the workbench can both call it.
     *
     * @return the store
     */
    public KnowledgeStore storeForValidation() {
        return store();
    }

    /**
     * Imports a document into a workspace.
     *
     * <p>RECON-W42.7. The workbench exposes the import so that the
     * acquisition does not need the store itself.
     *
     * @param workspaceId the workspace
     * @param path the document
     * @param declaredLanguage the declared language, or null
     * @return what the import did
     */
    public io.matrix.knowledge.acquisition.DocumentImporter.ImportResult importDocument(
            String workspaceId, java.nio.file.Path path, String declaredLanguage) {
        return importDocument(workspaceId, path, path.getFileName().toString(), declaredLanguage);
    }

    /**
     * Imports a document into a workspace, under a known file name.
     *
     * @param workspaceId the workspace
     * @param path the document
     * @param filename the name the document was uploaded under
     * @param declaredLanguage the declared language, or null
     * @return what the import did
     */
    public io.matrix.knowledge.acquisition.DocumentImporter.ImportResult importDocument(
            String workspaceId, java.nio.file.Path path, String filename, String declaredLanguage) {
        try {
            io.matrix.knowledge.acquisition.DocumentImporter.ImportResult result =
                    new io.matrix.knowledge.acquisition.DocumentImporter(store())
                            .importDocument(workspaceId, path, filename, declaredLanguage);
            // The concept the document brought in has to be PRACTISABLE:
            // the assessment items are generated for the concepts the
            // dataset does not cover.
            generateAssessment(workspaceId);
            return result;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to import the document " + path, e);
        }
    }

    /**
     * Validates a workspace: the claims that several independent sources
     * state are promoted, and the duplicates are superseded.
     *
     * @param workspaceId the workspace
     * @return what the validation did
     */
    public io.matrix.knowledge.acquisition.ClaimValidator.Result validate(String workspaceId) {
        return io.matrix.knowledge.acquisition.ClaimValidator.validate(this, workspaceId);
    }

    /**
     * Writes the claims and the evidence of a validation.
     *
     * <p>RECON-W42.7. The validation stage of the acquisition changes the
     * claims' statuses and adds the evidence of the sources that state
     * them. The store's rules still hold: a claim that asserts an
     * established status must carry the evidence for it, and the change
     * is atomic.
     *
     * @param workspaceId the workspace
     * @param claims the claims with their new statuses
     * @param evidence the evidence that was added
     */
    public void applyValidated(String workspaceId, List<Claim> claims, List<Evidence> evidence) {
        store().applyChanges(workspaceId, new KnowledgeChanges(List.of(), claims, evidence,
                List.of(), List.of()));
    }

    /**
     * The next exercise for the learner.
     *
     * <p>The mission's §10.1 asks for an exercise generator and a learner
     * practice loop: RECON-W41 had neither, and the caller had to supply
     * the item id. The selection is derived from the learner's persisted
     * state and the workspace's gaps.
     */
    public ExerciseSelector.Selection nextExercise(String workspaceId) {
        Set<String> attempted = new LinkedHashSet<>();
        Set<String> failed = new LinkedHashSet<>();
        collectAttempts(outcomes(workspaceId), attempted, failed);
        return new ExerciseSelector(dataset(workspaceId), learningStates(workspaceId),
                workspace(workspaceId), attempted, failed, Set.of(),
                ErrorIntegrator.conceptsWithVerifiedErrors(this, workspaceId)).next();
    }

    /**
     * The exercises for the next session.
     *
     * @param workspaceId the workspace
     * @param count how many to select
     * @return the selections, in order
     */
    public List<ExerciseSelector.Selection> nextExercises(String workspaceId, int count) {
        Set<String> attempted = new LinkedHashSet<>();
        Set<String> failed = new LinkedHashSet<>();
        collectAttempts(outcomes(workspaceId), attempted, failed);
        return new ExerciseSelector(dataset(workspaceId), learningStates(workspaceId),
                workspace(workspaceId), attempted, failed, Set.of(),
                ErrorIntegrator.conceptsWithVerifiedErrors(this, workspaceId)).next(count);
    }

    /**
     * The items the learner has attempted, and the ones they got wrong.
     *
     * <p><b>An item is failed when its LAST outcome is wrong.</b> The
     * first version asked whether ANY outcome was wrong, and an item that
     * was answered wrongly once and rightly afterwards stayed in the
     * failed set for ever - so the error-driven selection kept asking the
     * item the learner had already learned, and the learner never moved
     * on. The error is the current state, not the history.
     *
     * @param outcomes the outcomes, in the order they were recorded
     * @param attempted the items the learner has attempted
     * @param failed the items the learner has answered wrongly LAST
     */
    private void collectAttempts(List<AssessmentOutcome> outcomes, Set<String> attempted,
                                  Set<String> failed) {
        Map<String, Boolean> lastCorrect = new LinkedHashMap<>();
        for (AssessmentOutcome outcome : outcomes) {
            attempted.add(outcome.itemId());
            lastCorrect.put(outcome.itemId(), outcome.correct());
        }
        for (Map.Entry<String, Boolean> entry : lastCorrect.entrySet()) {
            if (!entry.getValue()) {
                failed.add(entry.getKey());
            }
        }
    }

    /**
     * Marks a record of the dialogue memory as forgotten.
     *
     * <p>RECON-W42.5. The record is MARKED, never deleted: the mission's
     * §8 forbids destroying the historical evidence, and the record of
     * what the user asked and what the system could not answer is exactly
     * that evidence.
     *
     * @param workspaceId the workspace
     * @param questionId the record's id
     * @param kind the rule that named the record
     * @param reason why the rule named it
     * @param nowMs the instant of the forgetting
     * @return true when a record was marked
     */
    public boolean forgetQuestion(String workspaceId, String questionId, String kind, String reason,
                                   long nowMs) {
        return store().forgetAskedQuestion(workspaceId, questionId, kind, reason, nowMs);
    }

    /**
     * Says in the answer that a concept's bindings were weakened.
     *
     * <p>RECON-W42.7. The error-driven decay is only worth doing if
     * something reads its result. The answer does: when it speaks about a
     * concept the learner has a VERIFIED error about, it says so, and it
     * says that the bindings were weakened — so the user knows that the
     * answer rests on an association the system has itself weakened.
     *
     * @param answer the answer
     * @return the answer
     */
    private Answer applyWeakenedBindings(String workspaceId, Answer answer) {
        if (answer == null || !answer.answered() || answer.concepts().isEmpty()) {
            return answer;
        }
        List<LearningError> verified = store().learningErrors(workspaceId).stream()
                .filter(LearningError::verified).toList();
        if (verified.isEmpty()) {
            return answer;
        }
        java.util.Map<String, Long> byConcept = new java.util.LinkedHashMap<>();
        for (LearningError error : verified) {
            byConcept.merge(error.conceptId(), 1L, Long::sum);
        }
        List<String> notes = new ArrayList<>();
        for (Concept concept : answer.concepts()) {
            Long count = byConcept.get(concept.id());
            if (count == null || count == 0L) {
                continue;
            }
            notes.add("the learner has " + count + " verified error"
                    + (count == 1L ? "" : "s") + " about " + concept.name()
                    + ", and the error-driven decay weakened the bindings of that concept");
        }
        if (notes.isEmpty()) {
            return answer;
        }
        return new Answer(answer.question(), answer.level(), answer.answered(),
                answer.abstentionReason(), answer.statement(), answer.supportScore(),
                answer.support(), answer.concepts(), answer.claims(), answer.sources(),
                withNote(answer.limitations(), String.join("; ", notes)),
                answer.gaps(), answer.unmatchedTerms());
    }

    /**
     * The result of the error-driven decay over a workspace's bindings.
     *
     * @param concepts the concepts the decay touched
     * @param errors the verified errors it acted on
     * @param bindingsBefore the active bindings before
     * @param bindingsAfter the active bindings after
     */
    public record DecayResult(List<String> concepts, int errors, double strengthBefore,
                              double strengthAfter, int activeBefore, int activeAfter) {
    }

    /**
     * Applies the error-driven decay to the workspace's Hebbian bindings.
     *
     * <p>The owner's decision D-012: the shadow classes are wired to the
     * live engines. This is the {@link io.matrix.cognitive.ErrorDrivenLearner}
     * in the consolidation — the live phase that forgets — acting on the
     * workspace's own bindings, over the VERIFIED learning errors only.
     *
     * <p>The state survives a restart: it is written to the store, so the
     * weakened bindings are still weakened after a forced termination.
     *
     * @param workspaceId the workspace
     * @return what the decay did
     */
    public DecayResult applyErrorDrivenDecay(String workspaceId) {
        List<LearningError> errors = store().learningErrors(workspaceId);
        List<LearningError> verified = errors.stream()
                .filter(LearningError::verified)
                .toList();
        if (verified.isEmpty()) {
            return new DecayResult(List.of(), 0, 0.0, 0.0, 0, 0);
        }
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        List<String> touched = new ArrayList<>();
        double strengthBefore = 0.0;
        double strengthAfter = 0.0;
        int activeBefore = 0;
        int activeAfter = 0;
        // The decay is applied ONCE per concept, over the errors about
        // THAT concept. The first version applied the whole stream to
        // every concept, once per error: a concept with two errors was
        // decayed four times, and the report listed it twice.
        java.util.Map<String, List<LearningError>> byConcept = new java.util.LinkedHashMap<>();
        for (LearningError error : verified) {
            String conceptId = error.conceptId();
            if (conceptId == null || conceptId.isBlank()) {
                continue;
            }
            byConcept.computeIfAbsent(conceptId, key -> new ArrayList<>()).add(error);
        }
        for (java.util.Map.Entry<String, List<LearningError>> entry : byConcept.entrySet()) {
            String conceptId = entry.getKey();
            List<LearningError> errorsOfConcept = entry.getValue();
            io.matrix.cognitive.CognitiveErrorStream stream =
                    HebbianBindings.streamOf(errorsOfConcept);
            io.matrix.neuron.HebbianUpdater.State state = store().hebbianState(workspaceId, conceptId);
            if (state == null) {
                state = HebbianBindings.stateFor(snapshot, conceptId);
            }
            float[] direction = HebbianBindings.observation(
                    HebbianBindings.termsOf(errorsOfConcept.get(0).learnerAnswer()));
            double strength = HebbianBindings.strengthAt(state, direction);
            int active = io.matrix.cognitive.ErrorDrivenLearner.totalActiveBindings(state);
            io.matrix.cognitive.ErrorDrivenLearner.applyDecay(state, stream,
                    HebbianBindings.DECAY_ETA, HebbianBindings.THRESHOLD);
            store().saveHebbianState(workspaceId, conceptId, state);
            touched.add(conceptId);
            strengthBefore += strength;
            strengthAfter += HebbianBindings.strengthAt(state, direction);
            activeBefore += active;
            activeAfter += io.matrix.cognitive.ErrorDrivenLearner.totalActiveBindings(state);
        }
        return new DecayResult(List.copyOf(touched), verified.size(), strengthBefore, strengthAfter,
                activeBefore, activeAfter);
    }

    /**
     * The active bindings of a workspace's concept: how many of its
     * associations are still standing.
     *
     * @param workspaceId the workspace
     * @param conceptId the concept
     * @return the count
     */
    public int activeBindings(String workspaceId, String conceptId) {
        io.matrix.neuron.HebbianUpdater.State state = store().hebbianState(workspaceId, conceptId);
        if (state == null) {
            state = HebbianBindings.stateFor(workspace(workspaceId), conceptId);
        }
        return io.matrix.cognitive.ErrorDrivenLearner.totalActiveBindings(state);
    }

    /**
     * Brings a forgotten record back.
     *
     * @param workspaceId the workspace
     * @param questionId the record
     * @return true when a record was brought back
     */
    public boolean reviveQuestion(String workspaceId, String questionId) {
        return store().reviveAskedQuestion(workspaceId, questionId);
    }

    /**
     * The records that were forgotten, and why.
     *
     * @param workspaceId the workspace
     * @return the forgotten records
     */
    public List<AskedQuestion> forgottenQuestions(String workspaceId) {
        return store().forgottenAskedQuestions(workspaceId);
    }

    /**
     * The next bounded learning action for every active goal.
     *
     * <p>The mission's §4.6: something has to decide what to do next. Until
     * this wave, nothing did — the goals recorded what a human had already
     * done, and the pieces that would make a planner possible
     * ({@code remainingSubtasks}, {@code blockedSubtasks},
     * {@code hasDependencyCycle}, {@code missingPrerequisites}) were written
     * and never called.
     *
     * <p>The plan is derived from what the workspace holds, and its cost
     * estimate is recomputable by reading the store.
     */
    public List<LearningPlanner.Plan> plan(String workspaceId) {
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        List<LearningPlanner.Plan> plans = new ArrayList<>();
        for (LearningGoal goal : store().goalsWithSubtasks(workspaceId)) {
            plans.add(new LearningPlanner(snapshot, goal).plan());
        }
        return plans;
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

        // THE TEACH-BACK THAT GATES THE TOP STAGE IS THE ONE THAT CAN FAIL.
        //
        // RECON-W42.1. The legacy containment measurement scores whether the
        // lesson CONTAINED the held-out items' key terms - and the lesson
        // and the items were written from the same account, so it cannot
        // fail. The rebuilt experiment measures an independent closed-book
        // learner over four disjoint phases, and its delayed phase DOES
        // fail. A capability stage gated on a measurement that cannot fail
        // is a stage that cannot record a regression.
        Boolean taught = null;
        String conclusion = null;
        if (includeTeachBack) {
            TeachBackExperiment.Result experiment = teachBackExperiment(workspaceId, conceptId,
                    CapabilityEvaluator.DEFAULT_EXPECTED_GAIN);
            taught = experiment.metExpectation();
            conclusion = experiment.conclusion();
        }
        return CapabilityEvaluator.evaluate(conceptId, states, taught, conclusion);
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
        KnowledgeSnapshot snapshot = workspace(workspaceId);
        ReasoningEngine.Result result = new ReasoningEngine().reason(snapshot, question, level);

        // WHAT WAS CHECKED IS NOW RECORDED.
        //
        // RECON-W41 built the contradiction detector and the entailment
        // checker, and wired neither to the store: the `contradiction` table
        // was therefore always empty, and every stored derivation kept the
        // `UNDECIDED` status it was imported with, so `GET /contradictions`
        // always answered `[]` and the stored verdicts said nothing.
        //
        // A verdict and a conflict are not generated content and are not
        // promoted to trusted knowledge: they are computed facts ABOUT
        // claims that are already in the store, with their own provenance.
        // Recording them is what makes the knowledge inspectable - which is
        // the whole point of the store holding a verdict at all.
        for (Derivation derivation : result.derivations()) {
            // The verdict carries the time of the CHECK, not the time the
            // rule was recorded: what the store holds is "this was checked
            // at this moment, and it came out this way".
            store().recordDerivation(new Derivation(derivation.id(), derivation.workspaceId(),
                    derivation.ruleId(), derivation.ruleName(), derivation.connective(),
                    derivation.conclusionClaimId(), derivation.premiseClaimIds(),
                    derivation.status(), java.time.Instant.now().toString()));
        }
        for (Contradiction conflict : new ContradictionDetector().detect(snapshot)) {
            store().recordContradiction(conflict);
        }
        return result;
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
        status.put("integrity", integrity());
        status.put("ok", true);
        return status;
    }

    /**
     * The integrity of the store, as the System Integrity View shows it.
     *
     * <p>Four things are reported, and none of them is a claim that cannot
     * be checked: when the last durable write happened, what state the
     * backup is in, what the recovery warnings are, and which goals are
     * waiting to be picked up.
     */
    public Map<String, Object> integrity() {
        Map<String, Object> integrity = new LinkedHashMap<>();
        Path path = Paths.get(databasePath);
        List<String> warnings = new ArrayList<>();

        // The last durable write: the newest record the store holds.
        //
        // The records do not all carry the same shape of time - an
        // assessment outcome carries epoch milliseconds, a goal carries an
        // ISO instant - so each is read as an Instant and the newest is
        // chosen among the Instants. Comparing the two forms as strings
        // would be a comparison of two alphabets.
        java.time.Instant lastWrite = null;
        String lastWorkspace = null;
        for (String workspace : workspaces()) {
            for (AssessmentOutcome outcome : store().outcomes(workspace)) {
                java.time.Instant at = java.time.Instant.ofEpochMilli(outcome.answeredAt());
                if (lastWrite == null || at.isAfter(lastWrite)) {
                    lastWrite = at;
                    lastWorkspace = workspace;
                }
            }
            for (LearningGoal goal : store().goalsWithSubtasks(workspace)) {
                if (goal.updatedAt() == null) {
                    continue;
                }
                java.time.Instant at;
                try {
                    at = java.time.Instant.parse(goal.updatedAt());
                } catch (java.time.format.DateTimeParseException e) {
                    continue;
                }
                if (lastWrite == null || at.isAfter(lastWrite)) {
                    lastWrite = at;
                    lastWorkspace = workspace;
                }
            }
        }
        integrity.put("lastDurableWrite", lastWrite == null ? null : lastWrite.toString());
        integrity.put("lastDurableWriteWorkspace", lastWorkspace);
        integrity.put("lastDurableWriteMeaning",
                "The newest record the store holds: the answer to \"is what I see what was "
                        + "written?\", not a promise that the disk is healthy.");

        // The backup state: there is none, and saying so is the honest report.
        //
        // The store runs in WAL mode, so the committed rows may live in the
        // write-ahead log rather than in the database file. Copying the
        // database file ALONE therefore loses whatever has not yet been
        // checkpointed - and that is not a small thing: it is the assessment
        // outcomes and the asked questions, the records the interface calls
        // "the evidence that something was learned". An experiment on this
        // wave's own store showed the naive copy losing every one of them.
        // The instruction must therefore name all three files.
        Path wal = path.resolveSibling(path.getFileName() + "-wal");
        Path shm = path.resolveSibling(path.getFileName() + "-shm");
        long walBytes = 0L;
        try {
            walBytes = Files.exists(wal) ? Files.size(wal) : 0L;
        } catch (IOException e) {
            walBytes = -1L;
        }
        integrity.put("backupState",
                "none: the store is a single SQLite database in WAL mode, so a backup is THREE "
                        + "files, not one. Copy " + path.getFileName() + ", "
                        + path.getFileName() + "-wal and " + path.getFileName() + "-shm "
                        + "together, while the application is not writing. Copying only "
                        + path.getFileName() + " loses everything the write-ahead log has not "
                        + "yet checkpointed into it - the assessment outcomes and the asked "
                        + "questions among it. Taking the copy is the operator's job.");
        integrity.put("backupFiles", List.of(path.getFileName().toString(),
                path.getFileName() + "-wal", path.getFileName() + "-shm"));
        integrity.put("writeAheadLogBytes", walBytes);
        if (walBytes > 8 * 1024 * 1024) {
            warnings.add("The write-ahead log holds " + walBytes + " bytes. The data is durable, "
                    + "but a checkpoint would fold it back into the database file.");
        }
        if (walBytes > 0) {
            warnings.add("The write-ahead log holds " + walBytes + " bytes of committed rows. "
                    + "A backup must include " + path.getFileName() + "-wal, or those rows "
                    + "are not in the backup.");
        }

        // The recovery warnings.
        if (!Files.exists(path)) {
            warnings.add("There is no database file yet: the workspaces will be empty until a "
                    + "package is loaded.");
        }
        for (String workspace : workspaces()) {
            for (LearningGoal goal : new GoalManager(store()).resumable(workspace)) {
                warnings.add("The goal \"" + goal.description() + "\" was under way in the "
                        + workspace + " and is waiting to be picked up. "
                        + (goal.recoveryInstructions() == null ? ""
                                : "Recovery: " + goal.recoveryInstructions()));
            }
        }
        integrity.put("recoveryWarnings", warnings);

        // The schema version and what it means.
        integrity.put("schemaVersion", KnowledgeStore.SCHEMA_VERSION);
        integrity.put("schemaVersionMeaning",
                "The version of the store's shape. A store written by a newer schema is not read "
                        + "by an older one, so this number belongs in an operator's notes.");
        return integrity;
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
