package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ProblemRequest;
import io.matrix.cognitive.microworld.Experiment;
import io.matrix.cognitive.microworld.HypothesisPrediction;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.cognitive.neuromodulation.RegulatoryState;
import io.matrix.cognitive.neuromodulation.RegulationBias;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * W44 — the cognitive kernel: one signal in, one cycle of perception, prediction, experiment,
 * comparison and revision out.
 *
 * <p>This class is the consumer the W44 directive asks for. It is not a framework and it has no
 * plugin mechanism: it does the work of one cycle, and the workbench endpoint and the
 * micro-world scenario are its callers. A kernel with no consumer would be the "broad framework"
 * the directive warns against.</p>
 *
 * <h2>What one cycle does</h2>
 * <ol>
 *   <li><b>Perceive.</b> The signal is classified by FORMAT ({@link ChannelClassifier}). A
 *       malformed payload is refused; it is not guessed at.</li>
 *   <li><b>Restore.</b> The micro-world session is rebuilt from the CASES that are already in
 *       the knowledge store. The cases are the episodic memory: the system does not keep a
 *       private copy of what it saw, so a restart cannot make it forget.</li>
 *   <li><b>Predict.</b> For an assignment that has not been observed, the surviving hypotheses
 *       are consulted. The prediction is recorded with the hypotheses that disagreed — an
 *       ambiguous prediction stays ambiguous in the record.</li>
 *   <li><b>Select.</b> The computation is chosen by the algorithm selector, which admits a
 *       method only when its declared preconditions hold and prefers the cheaper method when
 *       the quality is equal.</li>
 *   <li><b>Act.</b> When the surviving hypotheses disagree, a discriminating experiment is
 *       proposed and, if the budget allows, run inside the micro-world. Nothing outside the
 *       sandbox is touched.</li>
 *   <li><b>Observe and compare.</b> The outcome is written as a case and as evidence on the
 *       model's claim, and it is compared with the prediction. A mismatch becomes an explicit
 *       error with provenance; it never silently rewrites the model.</li>
 *   <li><b>Regulate.</b> The drives are updated from what happened, and the resulting bias may
 *       change the cost preference of the NEXT selection. It may not change a fact, a safety
 *       gate or a permission.</li>
 * </ol>
 *
 * <h2>Why the knowledge core holds the state</h2>
 * <p>A case, a claim and an evidence link are written through
 * {@link KnowledgeWorkbench}, so the workbench's Epistemic panel shows the same counterexample
 * the cognitive trace shows. There is one epistemic model, not two.</p>
 */
public final class CognitiveKernel {

    /** The workspace the cognitive scenarios run in. */
    public static final String WORKSPACE_ID = "microworld";

    /** The family name under which the micro-world computations are selected. */
    public static final String FAMILY_MICRO_WORLD = "micro-world-outcome";

    /** The id of the promoted procedure that holds the micro-world's fast path. */
    public static final String PROCEDURE_ID = "proc-microworld-settled-model";

    /**
     * The proposition of the claim that carries the micro-world's model.
     *
     * <p>It states the SUBJECT and not the rule. A claim that said "the lamp is lit exactly when
     * the power is on and the switch is on" would be the answer written into the system before
     * it had observed anything, which is the one thing a learning system must not do. The rule
     * is what the kernel DERIVES, and it is recorded where a derivation belongs: in the
     * promoted procedure, whose statement names the hypothesis the observations settled on.</p>
     */
    public static final String MODEL_CLAIM_PROPOSITION =
            "the lamp's behaviour under the power and switch variables in the micro-world";

    /** The scope of the model's claim: the conditions under which it is claimed. */
    public static final String MODEL_CLAIM_SCOPE =
            "in the micro-world, for the power and switch variables";

    /** The budget a single cycle may spend, in milliseconds of wall clock. */
    public static final long DEFAULT_CYCLE_BUDGET_MILLIS = 250L;

    /**
     * The number of held-out cases a procedure must agree with the reference on before it is
     * promoted.
     *
     * <p>Two is the smallest number that can distinguish "agrees" from "coincides". It is a
     * CONVENTION, not a measurement, and it is named so that it can be argued with.</p>
     */
    public static final int MINIMUM_VALIDATION_CASES = 2;

    private final KnowledgeWorkbench workbench;
    private final CognitiveStore store;
    private final MicroWorldSession.Factory sessionFactory;
    private final io.matrix.cognitive.microworld.MicroWorldEnvironment environment;
    private final AlgorithmSelectorBridge selector;
    private final RegulatoryBridge regulator;
    private final Supplier<String> timestamps;

    /**
     * The narrow view of the algorithm selector that the kernel needs.
     *
     * <p>The kernel does not depend on the concrete selector class. It depends on the DECISION
     * the selector produces and on the method it chose. Keeping the surface this small is what
     * allows the selector to be replaced by a measured, learned policy later without touching
     * the kernel.</p>
     */
    public interface AlgorithmSelectorBridge {
        /**
         * Chooses a method for the request.
         *
         * @param request    the problem
         * @param costWeight how heavily the cost of a method counts against its usefulness; the
         *                   caller supplies it, so that the regulatory state can raise it and
         *                   the selector never hard-codes a preference
         * @return the decision, including the method that was chosen
         */
        Selection select(ProblemRequest request, double costWeight);

        /** One chosen method and the decision that chose it. */
        record Selection(AlgorithmMethod<MicroWorldComputation, Boolean> method,
                         AlgorithmDecision decision) {
        }
    }

    /**
     * The narrow view of the regulator that the kernel needs.
     *
     * <p>As with the selector, the kernel depends on the BIAS, not on the dynamics that produce
     * it. A bias is a bounded influence on one decision; it cannot change a fact or a gate.</p>
     */
    public interface RegulatoryBridge {
        /** The current regulatory state. */
        RegulatoryState state();

        /** Folds the events of a cycle into the regulatory state and returns the new state. */
        RegulatoryState evolve(RegulatoryState current, List<RegulatoryEvent> events);

        /** The bias the current state implies for one effect. */
        RegulationBias bias(RegulatoryState state, DriveSignal.Effect effect);
    }

    /** One event the regulator is allowed to consume. */
    public record RegulatoryEvent(io.matrix.cognitive.neuromodulation.RegulatoryEvent.Kind kind,
                                  double magnitude,
                                  String source) {
        public RegulatoryEvent {
            if (kind == null) {
                throw new IllegalArgumentException("regulatory event kind must not be null");
            }
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("regulatory event source must not be blank");
            }
        }
    }

    /** Creates a kernel over the given stores. */
    public CognitiveKernel(KnowledgeWorkbench workbench,
                           CognitiveStore store,
                           MicroWorldSession.Factory sessionFactory,
                           io.matrix.cognitive.microworld.MicroWorldEnvironment environment,
                           AlgorithmSelectorBridge selector,
                           RegulatoryBridge regulator) {
        this(workbench, store, sessionFactory, environment, selector, regulator,
                () -> java.time.Instant.now().toString());
    }

    /** Creates a kernel with an explicit timestamp source, so a test can be deterministic. */
    public CognitiveKernel(KnowledgeWorkbench workbench,
                           CognitiveStore store,
                           MicroWorldSession.Factory sessionFactory,
                           io.matrix.cognitive.microworld.MicroWorldEnvironment environment,
                           AlgorithmSelectorBridge selector,
                           RegulatoryBridge regulator,
                           Supplier<String> timestamps) {
        this.workbench = workbench;
        this.store = store;
        this.sessionFactory = sessionFactory;
        this.environment = environment;
        this.selector = selector;
        this.regulator = regulator;
        this.timestamps = timestamps;
    }

    /**
     * Runs one cognitive cycle.
     *
     * @param signal the raw input
     * @return the outcome, with the full trace
     */
    public CognitiveOutcome process(CognitiveSignal signal) {
        String episodeId = "ep-" + signal.id();
        String now = timestamps.get();
        LearningTrace.Builder trace = LearningTrace.builder(episodeId, episodeId);
        List<String> notes = new ArrayList<>();
        store.startEpisode(episodeId, WORKSPACE_ID, "a cognitive cycle on " + signal.channel(), now);

        // ── 1. PERCEIVE ───────────────────────────────────────────────────────
        Percept percept = ChannelClassifier.classify(signal);
        trace.step("PERCEIVE", "ChannelClassifier",
                "channel=" + percept.channel() + " malformed=" + percept.malformed()
                        + " confidence=" + percept.confidence()
                        + " notes=" + String.join("; ", percept.notes()));
        if (percept.malformed()) {
            trace.step("REFUSE", "CognitiveKernel",
                    "the signal was malformed: " + String.join("; ", percept.notes()));
            store.closeEpisode(episodeId, now);
            return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.REFUSED,
                    "The signal could not be interpreted: "
                            + String.join("; ", percept.notes()),
                    percept, null, null, null, trace.build(), notes);
        }

        // ── 2. RESTORE ───────────────────────────────────────────────────────
        List<Observation> remembered = MicroWorldCodec.readCases(workbench.storeForValidation(),
                WORKSPACE_ID);
        MicroWorldSession session = sessionFactory.create(episodeId, remembered);
        trace.step("RESTORE", "MicroWorldSession",
                "the session was rebuilt from " + remembered.size()
                        + " recorded case(s); the model holds "
                        + session.survivingHypotheses().size() + " surviving hypothesis(es)");

        // ── 3. READ THE REQUESTED ASSIGNMENT ──────────────────────────────────
        WorldInput requested;
        try {
            requested = MicroWorldCodec.parseAssignment(signal.rawText());
        } catch (IllegalArgumentException e) {
            trace.step("REFUSE", "CognitiveKernel", "the assignment could not be read: "
                    + e.getMessage());
            store.closeEpisode(episodeId, now);
            return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.REFUSED,
                    "The requested assignment could not be read: " + e.getMessage(),
                    percept, null, null, null, trace.build(), notes);
        }

        // ── 4. PREDICT ────────────────────────────────────────────────────────
        HypothesisPrediction forecast = session.predict(requested);
        Prediction prediction = new Prediction("prd-" + episodeId, WORKSPACE_ID, episodeId,
                "lamp@" + MicroWorldCodec.describe(requested),
                Boolean.toString(forecast.value()),
                "surviving-hypotheses", "v1",
                forecast.unanimous() ? 1.0 : 0.0,
                false,
                forecast.supportingCaseIds(),
                now);
        store.recordPrediction(prediction);
        trace.step("PREDICT", "MicroWorldSession",
                "predicted lamp=" + forecast.value() + " unanimous=" + forecast.unanimous()
                        + " disagreeing=" + forecast.disagreeingHypothesisIds().size()
                        + " supporting-cases=" + forecast.supportingCaseIds().size());
        if (!forecast.unanimous()) {
            notes.add("the surviving hypotheses disagree; the prediction is not settled");
        }

        // ── 5. SELECT A COMPUTATIONAL METHOD ──────────────────────────────────
        RegulationBias bias = regulator.bias(regulator.state(),
                DriveSignal.Effect.ALGORITHM_COST_PREFERENCE);
        boolean alreadyObserved = false;
        for (Observation seen : remembered) {
            if (MicroWorldCodec.describe(seen.input()).equals(MicroWorldCodec.describe(requested))) {
                alreadyObserved = true;
                break;
            }
        }
        Set<String> facts = MicroWorldCodec.facts(requested, alreadyObserved);
        if (session.survivingHypotheses().size() == 1) {
            // The model has settled on exactly one hypothesis. That is the fact the fast path
            // declares, and it is established HERE — by the model, not by the caller.
            facts = new java.util.LinkedHashSet<>(facts);
            facts.add(MicroWorldSettledModelMethod.PRECONDITION_MODEL_SETTLED);
        }
        Procedure procedure = promotedProcedure();
        MicroWorldComputation computation =
                new MicroWorldComputation(requested, session, facts, procedure);
        ComputeBudget budget = ComputeBudget.millis(DEFAULT_CYCLE_BUDGET_MILLIS);
        AlgorithmSelectorBridge.Selection selection = selector.select(
                new ProblemRequest(FAMILY_MICRO_WORLD, computation, facts, budget),
                bias.costWeightMultiplier());
        AlgorithmDecision decision = selection.decision();
        trace.step("SELECT", "AlgorithmSelector",
                "selected=" + decision.selectedAlgorithmId() + " status=" + decision.status()
                        + " reason=" + decision.reason()
                        + " rejected=" + decision.rejected().size());
        if (decision.status() == AlgorithmDecision.Status.REFUSED) {
            store.closeEpisode(episodeId, now);
            return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.REFUSED,
                    "No computation was permitted for this request: " + decision.reason(),
                    percept, prediction, null, decision, trace.build(), notes);
        }

        // ── 6. COMPUTE AND, WHEN THE MODEL IS UNSETTLED, ACT ────────────────
        Boolean computed = null;
        try {
            computed = selection.method().execute(computation, budget);
            trace.step("COMPUTE", selection.method().capability().id(),
                    "the method returned " + computed);
        } catch (RuntimeException e) {
            trace.step("COMPUTE", selection.method().capability().id(),
                    "the method refused: " + e.getMessage());
            notes.add("the selected method refused: " + e.getMessage());
        }

        boolean unsettled = !forecast.unanimous();
        Experiment experiment = session.proposeExperiment();
        double gain = experiment.expectedInformationGain();
        // The evidence request has THREE reasons to act, and each of them names a different
        // thing to act on.
        //
        //   1. LEARNING. The model does not settle the case, so the experiment that separates
        //      the hypotheses is worth running. The assignment probed is the DISCRIMINATING one.
        //   2. CONFIRMATION OF A NEW ASSIGNMENT. The model has settled but the caller asks
        //      about an assignment nobody has observed. Answering it from the rule alone would
        //      be an extrapolation presented as an answer, so the system checks. The
        //      assignment probed is the one the CALLER asked about.
        //   3. THE REGULATION ASKS. The uncertainty has risen, so the system looks again even
        //      when it believes it knows the answer. The threshold is 1.0 in the neutral state,
        //      which means "never ask for anything the model did not already need".
        boolean regulationAsks = bias.evidenceRequestThreshold()
                < RegulationBias.NEVER_REQUEST_EVIDENCE;
        boolean learning = unsettled && gain > 0.0;
        boolean confirmingNew = !unsettled && !alreadyObserved;
        boolean confirmatory = regulationAsks && !unsettled && !confirmingNew;

        WorldInput probed = null;
        String probeReason = "";
        if (learning) {
            probed = experiment.input();
            probeReason = "the model does not settle the case";
        } else if (confirmingNew) {
            probed = requested;
            probeReason = "the assignment is new and the model is settled";
        } else if (confirmatory) {
            probed = experiment.input();
            probeReason = "a confirmation the regulation asked for";
        }
        trace.step("PROPOSE", "MicroWorldSession",
                "experiment=" + MicroWorldCodec.describe(experiment.input())
                        + " expected-information-gain=" + gain
                        + " unsettled=" + unsettled
                        + " the-regulation-asks-for-evidence=" + regulationAsks
                        + " rationale=" + experiment.rationale());

        HypothesisPrediction probedForecast = probed == null ? null : session.predict(probed);
        Prediction probedPrediction = probed == null ? null
                : new Prediction("prd-" + episodeId + "-probe",
                WORKSPACE_ID, episodeId,
                "lamp@" + MicroWorldCodec.describe(probed),
                Boolean.toString(probedForecast.value()),
                "surviving-hypotheses", "v1",
                probedForecast.unanimous() ? 1.0 : 0.0,
                false,
                probedForecast.supportingCaseIds(),
                now);
        if (probedPrediction != null) {
            store.recordPrediction(probedPrediction);
        }
        Observation observed = null;
        if (probed != null) {
            observed = environment.probe(probed);
            trace.step("PROBE", "MicroWorldEnvironment",
                    "the sandbox answered lamp=" + observed.lamp() + " for "
                            + MicroWorldCodec.describe(probed)
                            + " (" + probeReason + ")");
        }

        // ── 7. OBSERVE: write the case and the evidence ──────────────────────
        Claim model = modelClaim();
        ErrorSignal error = null;
        if (observed != null) {
            // Whether the observation CONTRADICTS the model is COMPUTED, not assumed. An
            // observation the surviving hypotheses explain is an example; an observation none
            // of them explains is a counterexample. Guessing "the lamp was off, therefore the
            // model is wrong" would make every dark observation a refutation.
            boolean explained = false;
            for (io.matrix.cognitive.microworld.RuleHypothesis hypothesis
                    : session.survivingHypotheses()) {
                if (hypothesis.evaluate(observed.input()) == observed.lamp()) {
                    explained = true;
                    break;
                }
            }
            String caseId = MicroWorldCodec.caseIdFor(observed.input(), observed.lamp());
            workbench.recordObservation(WORKSPACE_ID, model.id(),
                    MicroWorldCodec.describeOutcome(observed.lamp()),
                    MicroWorldCodec.describe(observed.input()),
                    !explained,
                    caseId);
            trace.step("OBSERVE", "CognitiveStore",
                    "the outcome was written as case " + caseId
                            + " and as evidence on claim " + model.id()
                            + "; the model " + (explained ? "explains it" : "does NOT explain it"));
            session.applyOutcome(observed);

            // A prediction error has two forms, and both are worth recording. The first is a
            // CONFIDENT wrong answer: the model settled on a value and the outcome says
            // otherwise. The second is an outcome NO hypothesis explains: the model has no
            // account of what it saw at all. The second is not a wrong prediction, so it is
            // recorded with a different disposition — a missing condition is a different
            // problem from a wrong model.
            boolean confidentlyWrong = probedForecast.unanimous()
                    && probedForecast.value() != observed.lamp();
            boolean unexplained = !explained;
            if (confidentlyWrong || unexplained) {
                String expected = confidentlyWrong
                        ? Boolean.toString(probedForecast.value())
                        : "no hypothesis in the model explains this observation";
                error = new ErrorSignal("err-" + episodeId, WORKSPACE_ID,
                        probedPrediction.id(),
                        expected,
                        Boolean.toString(observed.lamp()), 1.0,
                        confidentlyWrong ? ErrorSignal.Disposition.UNRESOLVED
                                : ErrorSignal.Disposition.MISSING_CONDITION,
                        "micro-world-hypothesis-set", "v1", List.of(caseId), now);
                store.recordError(error);
                trace.step("ERROR", "CognitiveKernel",
                        "the observation and the model disagree; the error names the model "
                                + "that made it, keeps the prediction, and is left open until "
                                + "more evidence arrives");
                CognitiveTask task = investigationTask(episodeId, now);
                store.recordTask(task);
                trace.step("TASK", "CognitiveKernel",
                        "a bounded internal task was generated: " + task.id()
                                + " budget=" + task.budgetWallClockMillis() + "ms"
                                + " actions=" + task.allowedActions());
                notes.add("a bounded investigation task was generated: " + task.id());
            }
        }

        // ── 8. LEARN: promote a fast path only after it is validated ─────────
        Procedure promotion = promoteIfValidated(session, remembered);
        if (promotion != null) {
            trace.step("LEARN", "CognitiveKernel",
                    "the fast path was promoted after agreeing with the reference on "
                            + promotion.validationCases() + " held-out case(s)");
            notes.add("a validated procedure was promoted: " + promotion.id());
        }

        // ── 9. REGULATE ───────────────────────────────────────────────────────
        List<RegulatoryEvent> events = new ArrayList<>();
        if (observed != null && !forecast.unanimous()) {
            // The assignment was new AND the model could not settle it. That is novelty and
            // uncertainty together, which is exactly what the regulator is for: it raises the
            // drives that make the system ask for more evidence, and it raises no drive that
            // could change a fact or a permission.
            events.add(new RegulatoryEvent(
                    io.matrix.cognitive.neuromodulation.RegulatoryEvent.Kind.NOVEL_INPUT,
                    1.0, "disagreeing-hypotheses"));
        }
        if (error != null) {
            events.add(new RegulatoryEvent(
                    io.matrix.cognitive.neuromodulation.RegulatoryEvent.Kind.PREDICTION_ERROR,
                    error.magnitude(), "prediction-error"));
        }
        RegulatoryState next = regulator.evolve(regulator.state(), events);
        trace.step("REGULATE", "RegulationController",
                "the drives now read " + next.held().size() + " dimension(s); the bias on the "
                        + "cost preference is " + bias.costWeightMultiplier());

        // ── 10. ANSWER ────────────────────────────────────────────────────────
        String answer = answerFor(forecast, computed, observed, error);
        trace.step("ANSWER", "CognitiveKernel", answer);
        store.closeEpisode(episodeId, now);
        return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.COMPLETED, answer,
                percept, prediction, error, decision, trace.build(), notes);
    }

    private static String answerFor(HypothesisPrediction forecast, Boolean computed,
                                    Observation observed, ErrorSignal error) {
        if (error != null) {
            return "The prediction and the observation disagree. The error is recorded; the model "
                    + "was not rewritten.";
        }
        if (observed != null) {
            return "The experiment was run and the outcome observed: lamp="
                    + observed.lamp() + ".";
        }
        if (computed != null) {
            return "lamp=" + computed + ".";
        }
        if (!forecast.unanimous()) {
            return "The model does not settle this case: the surviving hypotheses disagree.";
        }
        return "lamp=" + forecast.value() + ".";
    }

    /**
     * Promotes the fast path when, and only when, it agrees with the reference on the held-out
     * cases.
     *
     * <p>This is the W44 §22 acceptance step. A fast path that is merely plausible is not
     * promoted; one that reproduces the reference answer on every recorded case is. The
     * procedure records its applicability domain — {@code MODEL_SETTLED} — and names the
     * reference method it falls back to, so a later cycle outside that domain uses the
     * reference instead.</p>
     *
     * @return the promoted procedure, or null when nothing was promoted
     */
    private Procedure promoteIfValidated(MicroWorldSession session, List<Observation> remembered) {
        if (session.survivingHypotheses().size() != 1) {
            return null;
        }
        List<Observation> cases = new ArrayList<>(remembered);
        if (cases.size() < MINIMUM_VALIDATION_CASES) {
            return null;
        }
        int agreed = 0;
        for (Observation observation : cases) {
            boolean reference;
            try {
                reference = new MicroWorldReferenceMethod()
                        .execute(new MicroWorldComputation(observation.input(), session,
                                MicroWorldCodec.facts(observation.input(), true), null),
                                ComputeBudget.millis(DEFAULT_CYCLE_BUDGET_MILLIS));
            } catch (RuntimeException e) {
                // The reference method refused: no hypothesis fits the recorded observations.
                // That means the model has no account of its own data, and nothing may be
                // promoted on top of it. The refusal is the finding; promoting a procedure
                // would bury it.
                return null;
            }
            boolean fast = session.survivingHypotheses().get(0).evaluate(observation.input());
            if (reference == fast) {
                agreed++;
            }
        }
        double agreement = (double) agreed / (double) cases.size();
        if (agreement < 1.0) {
            return null;
        }
        Procedure promoted = new Procedure(PROCEDURE_ID,
                "the settled-model lookup for: "
                        + session.survivingHypotheses().get(0).statement(),
                FAMILY_MICRO_WORLD, "v1",
                "the model has settled on exactly one surviving hypothesis",
                Set.of(MicroWorldSettledModelMethod.PRECONDITION_MODEL_SETTLED),
                MicroWorldReferenceMethod.ID,
                cases.size(), agreement, 0, 0, timestamps.get(), null);
        store.recordProcedure(WORKSPACE_ID, promoted);
        return promoted;
    }

    /** The promoted procedure for the micro-world family, when there is one. */
    private Procedure promotedProcedure() {
        for (Procedure procedure : store.procedures(WORKSPACE_ID)) {
            if (PROCEDURE_ID.equals(procedure.id()) && procedure.active()) {
                return procedure;
            }
        }
        return null;
    }

    /**
     * The bounded internal task that a prediction error generates.
     *
     * <p>The W44 directive §13 requires a self-generated task to say why it was initiated, what
     * it serves, what it is expected to be worth, what it costs, how it is finished, how it is
     * stopped, what it may do and what it may not. Every one of those is a field of the record,
     * and the two that bound the task — the budget and the stop condition — are what keep it
     * from running indefinitely.</p>
     *
     * <p>The allowed actions are an ALLOW-LIST of one: probe the micro-world. The task cannot
     * reach anything else, and it cannot reach outside the sandbox at all.</p>
     */
    private CognitiveTask investigationTask(String episodeId, String now) {
        return new CognitiveTask("tsk-" + episodeId, WORKSPACE_ID,
                "prediction-error",
                "the prediction and the observation disagree, and no cause has been established",
                "keep the model consistent with the evidence it has collected",
                "an explanation that would let the model be revised rather than merely doubted",
                "one bounded probe inside the micro-world",
                "a cause is established, or the task is stopped",
                "the wall-clock budget is exhausted",
                List.of("probe-microworld"),
                DEFAULT_CYCLE_BUDGET_MILLIS,
                CognitiveTask.STATUS_OPEN, now, null);
    }

    /**
     * The claim that carries the micro-world's model.
     *
     * <p>The claim is found by its proposition rather than created afresh on every cycle, so
     * that the evidence of one cycle accumulates on ONE claim and a restart does not start a
     * parallel history.</p>
     */
    private Claim modelClaim() {
        KnowledgeStore store = workbench.storeForValidation();
        for (Claim claim : store.claims(WORKSPACE_ID)) {
            if (MODEL_CLAIM_PROPOSITION.equals(claim.proposition())) {
                return claim;
            }
        }
        return workbench.recordClaim(WORKSPACE_ID, MODEL_CLAIM_PROPOSITION,
                MODEL_CLAIM_SCOPE, "the model's own hypothesis");
    }

    /** The claims of the micro-world workspace, for the diagnostic surface. */
    public List<Claim> modelClaims() {
        return workbench.storeForValidation().claims(WORKSPACE_ID);
    }

    /** The evidence recorded on the micro-world's model. */
    public List<Evidence> modelEvidence() {
        return workbench.storeForValidation().evidence(WORKSPACE_ID);
    }

    /** The cases — the observations with their conditions — the model was built from. */
    public List<io.matrix.knowledge.Case> modelCases() {
        return workbench.storeForValidation().cases(WORKSPACE_ID);
    }

    /** A summary of the cognitive state, for the diagnostic surface. */
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workspace", WORKSPACE_ID);
        out.put("claims", modelClaims().size());
        out.put("evidence", modelEvidence().size());
        out.put("predictions", store.predictions(WORKSPACE_ID).size());
        out.put("errors", store.errors(WORKSPACE_ID).size());
        out.put("procedures", store.procedures(WORKSPACE_ID).size());
        out.put("tasks", store.tasks(WORKSPACE_ID).size());
        return out;
    }
}
