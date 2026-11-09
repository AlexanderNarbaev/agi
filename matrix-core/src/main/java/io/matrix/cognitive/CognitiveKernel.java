package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ValidationKind;
import io.matrix.cognitive.algorithms.ProblemRequest;
import io.matrix.cognitive.microworld.Experiment;
import io.matrix.cognitive.microworld.HypothesisPrediction;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.ObservationDisposition;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.cognitive.neuromodulation.RegulatoryState;
import io.matrix.cognitive.worldstate.MicroWorldStateAdapter;
import io.matrix.cognitive.worldstate.WorldStateMemory;
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
import java.util.function.LongSupplier;
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
     * W45.0 — the monotonic clock the cycle's DEADLINE is measured against.
     *
     * <p>This is not a cognitive input and it never reaches a decision about what is true. It
     * is read only to decide whether the cycle has run out of its budget, and it is injected so
     * that a test can make the budget expire deterministically instead of by sleeping.</p>
     */
    private final LongSupplier monotonicNanos;

    /**
     * W45.0 — why the last cycle promoted nothing, in words a reader can act on.
     *
     * <p>A refusal with no reason is a refusal a reader cannot check. The field is written by
     * the promotion check and read by the trace, so the reason ends up in the durable record of
     * the cycle rather than only in a log line.</p>
     */
    private String lastPromotionRefusal;

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

        /**
         * W51 §5 — runs a method and records what it cost and whether it succeeded.
         *
         * <p><b>Why the kernel has to ask for this.</b> The selector scores a method by its
         * measured cost when the ledger holds a measurement, and by its DECLARED complexity
         * when it does not. The kernel was the only place where a method is actually run,
         * and it never recorded anything: the ledger stayed empty for the life of the
         * process, so every decision in every cycle fell back on the declared complexity and
         * the system never learned what its methods actually cost. The adapter's own javadoc
         * promised that "a measurement that is thrown away at the end of the cycle would
         * mean the selector never learns anything" — and the measurement was thrown away.</p>
         *
         * <p>The default runs the body and measures nothing, so an adapter that has no
         * ledger still works; the one that has a ledger records through it.</p>
         *
         * @param methodId the method being run
         * @param body     the work
         * @param <T>      what the work produces
         * @return what the work produced
         */
        default <T> T measure(String methodId, java.util.function.Supplier<T> body) {
            return body.get();
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

        /**
         * W45.0 — adopts a persisted regulatory state.
         *
         * <p>ADR-W44-01 lists the drives among the cognitive state the store persists, and
         * until W45 they were the one item on that list that was not persisted: the state
         * lived in the adapter, so a restart reset every drive to neutral. The kernel
         * restores the persisted state at the start of a cycle and writes the new one back
         * at the end.</p>
         *
         * @param state the state to adopt, or null when nothing is persisted
         */
        void restore(RegulatoryState state);
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

    /**
     * W45.0 — a kernel with an explicit MONOTONIC clock for the cycle's deadline.
     *
     * <p>The wall-clock timestamp source and the monotonic clock are separate on purpose. The
     * timestamp is a record of WHEN something happened and it may be a wall-clock string. The
     * deadline is a resource boundary and it must not move when the wall clock is adjusted, so
     * it is measured against a monotonic clock, which a test can replace.</p>
     *
     * @param workbench the epistemic store
     * @param store the cognitive store
     * @param sessionFactory how a session is rebuilt
     * @param environment the world the cycle probes
     * @param selector the algorithm selector
     * @param regulator the regulator
     * @param timestamps the wall-clock timestamp source
     * @param monotonicNanos the monotonic clock, in nanoseconds
     */
    public CognitiveKernel(KnowledgeWorkbench workbench,
                           CognitiveStore store,
                           MicroWorldSession.Factory sessionFactory,
                           io.matrix.cognitive.microworld.MicroWorldEnvironment environment,
                           AlgorithmSelectorBridge selector,
                           RegulatoryBridge regulator,
                           Supplier<String> timestamps,
                           LongSupplier monotonicNanos) {
        this.workbench = workbench;
        this.store = store;
        this.sessionFactory = sessionFactory;
        this.environment = environment;
        this.selector = selector;
        this.regulator = regulator;
        this.timestamps = timestamps;
        this.monotonicNanos = monotonicNanos == null ? System::nanoTime : monotonicNanos;
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
        this.monotonicNanos = System::nanoTime;
    }

    /**
     * Runs one cognitive cycle.
     *
     * <p><b>W45.0: the cycle is a serial execution boundary.</b> The kernel holds mutable state
     * — the regulator's drives, and the store holds ONE JDBC connection — and two cycles
     * running at the same time would interleave their regulatory updates: each would read the
     * same drive state, each would evolve it, and the second write would silently discard the
     * first one's contribution. The store's methods are synchronised, so no two WRITES overlap,
     * but a write that is correct on its own is still a lost update when the state it was
     * derived from has already moved.</p>
     *
     * <p>So one cycle runs at a time per kernel. That is the smallest mechanism that makes the
     * contract correct: there is no actor, no message bus and no lock hierarchy — the cycle
     * simply does not overlap with itself. The cost is stated plainly: a slow cycle holds the
     * others back, and the concurrency guarantee is "one cycle at a time", not "the system
     * scales".</p>
     *
     * @param signal the raw input
     * @return the outcome, with the full trace
     */
    public synchronized CognitiveOutcome process(CognitiveSignal signal) {
        return process(signal, RequestProgressListener.NO_OP);
    }

    /**
     * Runs one cognitive cycle for a signal, reporting the durable checkpoints it reaches.
     *
     * <p>W45.1. The listener is called at the phase boundaries, AFTER the work of the phase has
     * been committed. It is how the request lifecycle knows how far the cycle got, which is the
     * fact a recovery needs: a request that died after committing an observation may not be
     * executed again, and a request that died before writing anything may be.</p>
     *
     * @param signal   the raw input
     * @param listener the listener of the durable checkpoints
     * @return the outcome, with the full trace
     */
    public synchronized CognitiveOutcome process(CognitiveSignal signal,
                                                 RequestProgressListener listener) {
        String episodeId = "ep-" + signal.id();
        String now = timestamps.get();
        LearningTrace.Builder trace = LearningTrace.builder(episodeId, episodeId);
        List<String> notes = new ArrayList<>();
        // W51 §4 — what the cycle TEACHED, collected as it happens, so that the reflection
        // at the end names the real things rather than a summary of them.
        List<String> learned = new ArrayList<>();
        store.startEpisode(episodeId, WORKSPACE_ID, "a cognitive cycle on " + signal.channel(), now);
        listener.at(RequestCheckpoint.EPISODE_OPENED);

        // W45.0. The cycle's budget is a DEADLINE, measured against a monotonic clock and
        // checked at the phase boundaries. The work below is enclosed so that a budget that
        // runs out ends the cycle in an explicit, persisted terminal state instead of
        // leaving the episode open.
        CycleDeadline deadline = new CycleDeadline(DEFAULT_CYCLE_BUDGET_MILLIS, monotonicNanos);
        Percept percept = null;
        Prediction prediction = null;
        ErrorSignal error = null;
        AlgorithmDecision decision = null;
        boolean experimentExecuted = false;
        boolean observationCommitted = false;
        boolean procedureChanged = false;
        try {
        // ── 1. PERCEIVE ───────────────────────────────────────────────────────
        percept = ChannelClassifier.classify(signal);
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
                    percept, null, null, null, persist(trace, episodeId, now), notes);
        }

        deadline.check("PERCEIVE");
        // ── 2. RESTORE ───────────────────────────────────────────────────────
        List<Observation> remembered = MicroWorldCodec.readCases(workbench.storeForValidation(),
                WORKSPACE_ID);
        MicroWorldSession session = sessionFactory.create(episodeId, remembered);
        trace.step("RESTORE", "MicroWorldSession",
                "the session was rebuilt from " + remembered.size()
                        + " recorded case(s); " + session.appliedObservations().size()
                        + " of them constrain the model and "
                        + session.quarantinedObservations().size()
                        + " are held as unresolved conflicts; the model holds "
                        + session.survivingHypotheses().size() + " surviving hypothesis(es)");
        // W51 §4 — the WORLD-STATE MEMORY. It is rebuilt by replaying the durable case
        // log, so it survives a restart without a database of its own, and it is the
        // component that answers "do we already know this situation?". The session holds
        // what the system BELIEVES; this holds what the system SAW.
        WorldStateMemory world = MicroWorldStateAdapter.replay(WORKSPACE_ID,
                io.matrix.contract.Provenance.of("cognitive-kernel", now), remembered);
        trace.step("RESTORE", "WorldStateMemory",
                "the world state was rebuilt: revision " + world.revision()
                        + ", " + world.state().entities().size() + " situation(s) known, "
                        + world.transitions().size() + " transition(s)");

        deadline.check("RESTORE");
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
                    percept, null, null, null, persist(trace, episodeId, now), notes);
        }

        // ── 4. PREDICT ────────────────────────────────────────────────────────
        HypothesisPrediction forecast = session.predict(requested);
        prediction = new Prediction("prd-" + episodeId, WORKSPACE_ID, episodeId,
                "lamp@" + MicroWorldCodec.describe(requested),
                Boolean.toString(forecast.value()),
                "surviving-hypotheses", "v1",
                forecast.unanimous() ? 1.0 : 0.0,
                false,
                forecast.supportingCaseIds(),
                now);
        store.recordPrediction(prediction);
        listener.at(RequestCheckpoint.PREDICTION_WRITTEN);
        trace.step("PREDICT", "MicroWorldSession",
                "predicted lamp=" + forecast.value() + " unanimous=" + forecast.unanimous()
                        + " disagreeing=" + forecast.disagreeingHypothesisIds().size()
                        + " supporting-cases=" + forecast.supportingCaseIds().size());
        if (!forecast.unanimous()) {
            notes.add("the surviving hypotheses disagree; the prediction is not settled");
        }

        deadline.check("PREDICT");
        // ── 5. SELECT A COMPUTATIONAL METHOD ──────────────────────────────────
        RegulationBias bias = regulator.bias(regulator.state(),
                DriveSignal.Effect.ALGORITHM_COST_PREFERENCE);
        // The world-state memory answers this, not a scan of the case list: the memory and
        // the estimate it holds cannot drift apart, because they are the same object.
        boolean alreadyObserved = world.knows(MicroWorldStateAdapter.entityId(requested),
                MicroWorldStateAdapter.PROPERTY_LAMP);
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
        decision = selection.decision();
        trace.step("SELECT", "AlgorithmSelector",
                "selected=" + decision.selectedAlgorithmId() + " status=" + decision.status()
                        + " reason=" + decision.reason()
                        + " rejected=" + decision.rejected().size());
        if (decision.status() == AlgorithmDecision.Status.REFUSED) {
            store.closeEpisode(episodeId, now);
            return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.REFUSED,
                    "No computation was permitted for this request: " + decision.reason(),
                    percept, prediction, null, decision, persist(trace, episodeId, now), notes);
        }

        deadline.check("SELECT");
        // ── 6. COMPUTE AND, WHEN THE MODEL IS UNSETTLED, ACT ────────────────
        Boolean computed = null;
        try {
            // W51 §5 — the method's cost is MEASURED, and the measurement goes into the
            // ledger that the next cycle's selection reads. Without this the cost term of
            // every decision was the declared complexity, forever.
            final AlgorithmSelectorBridge.Selection chosen = selection;
            computed = selector.measure(chosen.method().capability().id(),
                    () -> chosen.method().execute(computation, budget));
            trace.step("COMPUTE", selection.method().capability().id(),
                    "the method returned " + computed
                            + "; the cost was measured and recorded in the ledger");
        } catch (RuntimeException e) {
            trace.step("COMPUTE", selection.method().capability().id(),
                    "the method refused: " + e.getMessage());
            notes.add("the selected method refused: " + e.getMessage());
        }

        deadline.check("COMPUTE");
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

        deadline.check("PROPOSE");
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
            deadline.check("PREDICT");
            observed = environment.probe(probed);
            experimentExecuted = true;
            listener.at(RequestCheckpoint.EXPERIMENT_EXECUTED);
            trace.step("PROBE", "MicroWorldEnvironment",
                    "the sandbox answered lamp=" + observed.lamp() + " for "
                            + MicroWorldCodec.describe(probed)
                            + " (" + probeReason + ")");
            // The world-state memory records what the world DID, with the action that
            // caused it. The hypothesis space records what the system THINKS; the two are
            // separate on purpose, and a fact the session later holds as a conflict stays
            // in the world state because the world really did answer.
            io.matrix.contract.StateTransition transition = world.apply(
                    MicroWorldStateAdapter.fact(observed,
                            io.matrix.contract.StateTransition.Cause.ACTION,
                            "act-" + episodeId + "-" + probed));
            trace.step("WORLD", "WorldStateMemory",
                    "the transition " + transition.id() + " moved the state from "
                            + transition.fromStateId() + " to " + transition.toStateId()
                            + "; changed=" + transition.changedProperties()
                            + "; the state is now at revision " + world.revision());
        }

        deadline.check("PROBE");
        // ── 7. OBSERVE: write the case and the evidence ──────────────────────
        Claim model = modelClaim();
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

            // W45.0. The session decides FIRST whether the observation may constrain the
            // model, and the decision is what gets persisted. Before this change the case was
            // written before the session had seen the observation, and the case carried no
            // record of what it was allowed to do to the model — so a contradiction that the
            // session deliberately kept out of its hypothesis set was applied by the next
            // session, which rebuilt itself from these cases.
            session.applyOutcome(observed);
            boolean applied = session.isApplied(observed);
            ObservationDisposition disposition = session.dispositionOf(observed);
            String dispositionReason = applied
                    ? "the session applied the observation to its hypothesis set"
                    : "the observation contradicts a prediction the session made; its cause is "
                            + "unresolved, so it is held out of the active model until a review "
                            + "adjudicates it";
            // The evidence relation and the disposition are two different questions: the
            // relation says what the evidence is ABOUT, the disposition says what may be
            // BELIEVED because of it. The relation is the conservative signal here — an
            // observation the model does not explain, or one that is held, CONTRADICTS the
            // claim — but it is NOT the stronger of the two as a rule of classification.
            //
            // W45.1: the v16 -> v17 backfill used to read the relation alone, and that rule
            // preserved an OBSOLETE W44 rule: it held every observation the model did not
            // explain, while the live kernel applies such an observation. The backfill now
            // consults the cognitive error records — a disagreement with a UNANIMOUS
            // prediction is a conflict with a belief, and everything else is learning — and
            // falls back to the relation only when those records are not there. The relation
            // remains the conservative fallback, not the classification.
            boolean contradicts = !explained || !applied;
            workbench.recordObservation(WORKSPACE_ID, model.id(),
                    MicroWorldCodec.describeOutcome(observed.lamp()),
                    MicroWorldCodec.describe(observed.input()),
                    contradicts,
                    caseId,
                    disposition,
                    dispositionReason,
                    episodeId,
                    signal.id());
            observationCommitted = true;
            learned.add("the observation was written as case " + caseId + " and as evidence on "
                    + "claim " + model.id() + " (the model "
                    + (explained ? "explains it" : "does NOT explain it") + ")");
            listener.at(RequestCheckpoint.OBSERVATION_COMMITTED);
            trace.step("OBSERVE", "CognitiveStore",
                    "the outcome was written as case " + caseId
                            + " and as evidence on claim " + model.id()
                            + "; the model " + (explained ? "explifies it" : "does NOT explain it")
                            + "; the observation's disposition is " + disposition.name()
                            + (applied ? "" : " and it does NOT constrain the active model"));

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
                learned.add("the disagreement was recorded as an error and a bounded "
                        + "investigation task was generated: " + task.id());
            }
        }

        deadline.check("OBSERVE");
        // ── 8. LEARN: promote a fast path only after it is validated ─────────
        Procedure promotion = promoteIfValidated(session, remembered);
        procedureChanged = promotion != null;
        if (promotion != null) {
            trace.step("LEARN", "CognitiveKernel",
                    "the fast path was promoted after agreeing with the reference on "
                            + promotion.validationCases() + " INDEPENDENT case(s) — cases the "
                            + "model was not built from");
            notes.add("an independently validated procedure was promoted: " + promotion.id());
            learned.add("a procedure was promoted after agreeing with the reference on "
                    + promotion.validationCases() + " independent case(s): " + promotion.id());
        } else {
            trace.step("LEARN", "CognitiveKernel",
                    lastPromotionRefusal == null
                            ? "no procedure was promoted: the model has not settled on a single "
                                    + "hypothesis yet"
                            : lastPromotionRefusal);
            notes.add("the fast path is an in-sample derived shortcut of the reference; it is "
                    + "not a validated procedure");
        }

        deadline.check("LEARN");
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
        // W45.0. The new state and the provenance of the change are written in ONE
        // transaction, so the durable state and the reason for it cannot disagree. The event
        // ids carry the episode, so a replay of the same episode is refused rather than
        // applied twice.
        List<CognitiveStore.RegulatoryEventRecord> regulatoryRecords = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            regulatoryRecords.add(new CognitiveStore.RegulatoryEventRecord(
                    episodeId + "-reg-" + index, WORKSPACE_ID, episodeId,
                    events.get(index).kind().name(), events.get(index).magnitude(),
                    events.get(index).source(), now));
        }
        long regulatoryVersion = store.recordRegulatoryState(WORKSPACE_ID, next, now,
                regulatoryRecords);
        listener.at(RequestCheckpoint.REGULATION_PERSISTED);
        trace.step("REGULATE", "CognitiveStore",
                "the regulatory state was persisted at version " + regulatoryVersion
                        + " with " + regulatoryRecords.size() + " event(s) of provenance");

        // ── 10. REFLECT ───────────────────────────────────────────────────────
        // W51 §4 — the reflection. The cycle evaluates ITSELF: what it attempted, which
        // method it chose and why, what it predicted, what happened, what it learned and
        // what is still uncertain. The decisions it carries are things that can be carried
        // out — prefer a method, withdraw a method, leave a proposition unresolved — and
        // they are produced by the outcome, not by a feeling about the outcome.
        learned.add("the model holds " + session.survivingHypotheses().size()
                + " surviving hypothesis(es) over " + remembered.size() + " remembered case(s)");
        List<String> uncertainties = new ArrayList<>();
        if (!forecast.unanimous()) {
            uncertainties.add("the surviving hypotheses disagree: "
                    + forecast.disagreeingHypothesisIds().size() + " of them");
        }
        if (session.quarantinedObservations().size() > 0) {
            uncertainties.add("the session holds " + session.quarantinedObservations().size()
                    + " observation(s) as unresolved conflicts");
        }
        if (error != null) {
            uncertainties.add("the prediction and the observation disagree: "
                    + error.disposition());
        }
        io.matrix.contract.Reflection reflection = io.matrix.cognitive.reflection.CycleReflection.of(
                episodeId, "task-" + episodeId, decision, prediction, error, computed,
                learned, uncertainties, now);
        trace.step("REFLECT", "CycleReflection",
                "the cycle evaluated itself: the method " + reflection.methodId()
                        + " is " + (reflection.reuseMethod() ? "reusable" : "NOT reusable")
                        + "; " + reflection.learned().size() + " thing(s) learned; "
                        + reflection.uncertainties().size() + " uncertainty(ies) remaining; "
                        + reflection.decisions().size() + " decision(s) produced");

        // ── 11. ANSWER ────────────────────────────────────────────────────────
        deadline.check("REFLECT");
        String answer = answerFor(forecast, computed, observed, error);
        trace.step("ANSWER", "CognitiveKernel", answer);
        store.closeEpisode(episodeId, now);
        listener.at(RequestCheckpoint.EPISODE_CLOSED);
        LearningTrace completed = persist(trace, episodeId, now);
        listener.at(RequestCheckpoint.OUTCOME_PERSISTED);
        return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.COMPLETED, answer,
                percept, prediction, error, decision, completed, notes, reflection);
        } catch (CycleTimeoutException timeout) {
            // W45.0. The cycle ran out of its budget. The episode ends, the trace is kept, and
            // the notes say exactly what had already happened — so that a reader can tell an
            // interrupted cycle from a cycle that never ran, and from a cycle that finished.
            notes.add("the cycle ran out of its " + timeout.budgetMillis()
                    + "ms budget at the " + timeout.phase() + " phase, after "
                    + timeout.elapsedMillis() + "ms");
            notes.add("an experiment was executed: " + experimentExecuted);
            notes.add("an observation was committed: " + observationCommitted);
            notes.add("the procedure state changed: " + procedureChanged);
            notes.add("the side effects that had already happened are NOT rolled back");
            trace.step("TIMEOUT", "CycleDeadline", timeout.getMessage()
                    + "; experiment-executed=" + experimentExecuted
                    + " observation-committed=" + observationCommitted
                    + " procedure-changed=" + procedureChanged);
            store.closeEpisode(episodeId, now);
            return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.RESOURCE_LIMITED,
                    "The cognitive cycle ran out of its budget: " + timeout.getMessage(),
                    percept, prediction, error, decision, persist(trace, episodeId, now), notes);
        } catch (RuntimeException failure) {
            // W45.1. A controlled runtime failure is a TERMINAL state of the cycle, not a
            // normal answer and not an abandoned episode.
            //
            // Before W45.1 an exception after `startEpisode` left the episode OPEN and the
            // caller got a stack trace: the store said the cycle was still running, and the
            // request looked like it had never happened. Now the episode is closed, the
            // trace records where the cycle stopped, and the outcome says FAILED — which the
            // request lifecycle stores, so that a retry REPLAYS the failure instead of
            // repeating the work.
            notes.add("the cycle failed: " + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
            notes.add("an experiment was executed: " + experimentExecuted);
            notes.add("an observation was committed: " + observationCommitted);
            notes.add("the procedure state changed: " + procedureChanged);
            notes.add("the side effects that had already happened are NOT rolled back");
            trace.step("FAILURE", "CognitiveKernel",
                    failure.getClass().getSimpleName()
                            + (failure.getMessage() == null ? "" : ": " + failure.getMessage())
                            + "; experiment-executed=" + experimentExecuted
                            + " observation-committed=" + observationCommitted
                            + " procedure-changed=" + procedureChanged);
            closeAfterFailure(episodeId, now);
            return new CognitiveOutcome(episodeId, CognitiveOutcome.Status.FAILED,
                    "The cognitive cycle failed: " + failure.getClass().getSimpleName()
                            + (failure.getMessage() == null ? "" : ": " + failure.getMessage()),
                    percept, prediction, error, decision, persist(trace, episodeId, now), notes);
        }
    }

    /**
     * Closes an episode whose cycle failed.
     *
     * <p>The close must not fail the failure: an episode that cannot be closed because the
     * store is unhappy is a second problem, and the first one must still be reported. The
     * interruption is therefore recorded as well, so that the episode carries a terminal
     * event even when the close itself failed.</p>
     *
     * @param episodeId the episode of the failed cycle
     * @param now       the instant of the failure
     */
    private void closeAfterFailure(String episodeId, String now) {
        try {
            store.closeEpisode(episodeId, now);
        } catch (RuntimeException e) {
            store.interruptEpisode(episodeId,
                    "the episode could not be closed normally: " + e.getMessage(), now);
        }
    }


    /**
     * Writes the trace of a cycle into the episode's event stream.
     *
     * <p>The trace is the record of WHY an answer was reached, and a record that lives only in
     * the response is a record that disappears with the process. It is written here, step by
     * step, so that {@code GET /v1/cognitive/trace/{episodeId}} returns the same thing after a
     * restart as it did during the cycle.</p>
     *
     * @param builder the builder of the cycle's trace
     * @param episodeId the episode the trace belongs to
     * @param occurredAt the instant of the cycle
     * @return the trace, built
     */
    private LearningTrace persist(LearningTrace.Builder builder, String episodeId, String occurredAt) {
        LearningTrace trace = builder.build();
        long sequence = 0L;
        for (LearningTrace.Step step : trace.steps()) {
            store.appendEvent(episodeId + "-evt-" + sequence, episodeId, sequence,
                    step.phase(), step.actor(), step.detail(), occurredAt);
            sequence++;
        }
        return trace;
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
     * Promotes the fast path when, and only when, it agrees with the reference on INDEPENDENT
     * cases.
     *
     * <p><b>W45.0 changed what this method may do.</b> The W44 version validated the fast
     * path against the reference on {@code remembered} — the very observations the model was
     * built from — and then reported the agreement as a "held-out validation". That was an
     * in-sample consistency check wearing the name of a validation: it showed that the cheap
     * path computes what the expensive path computes, and it said nothing about whether the
     * model is right about the world.</p>
     *
     * <p>The candidate validation set is now computed honestly: it is the recorded
     * observations the model was <b>not</b> built from. A held observation is not in it — a
     * contradiction is a conflict to be adjudicated, not evidence — and an observation the
     * model already consumed is not in it either.</p>
     *
     * <p><b>In the lamp world that set is always empty, and that is a fact about the world
     * rather than a bug.</b> The domain has four assignments; a truth table over four rows is
     * determined only when all four have been observed; so a model that has SETTLED has seen
     * the whole domain, and there is nothing left to observe independently. Splitting the same
     * four rows in two and calling the halves independent is not something the domain
     * supports. So <b>nothing is promoted</b>, and the fast path is used as what it actually
     * is: an algebraic shortcut of the reference over the same model-eligible data, with the
     * reference still there to take over whenever the shortcut's precondition does not
     * hold.</p>
     *
     * @return the promoted procedure, or null when nothing was promoted
     */
    private Procedure promoteIfValidated(MicroWorldSession session, List<Observation> remembered) {
        if (session.survivingHypotheses().size() != 1) {
            return null;
        }
        List<Observation> cases = independentValidationCandidates(session, remembered);
        if (cases.size() < MINIMUM_VALIDATION_CASES) {
            // The refusal is the finding. The lamp world's input domain is exhausted once the
            // model settles, so no independent evidence exists and no procedure may be
            // promoted. The fast path still runs as a DERIVED SHORTCUT of the reference, and
            // the trace says so — but nothing calls it validated.
            lastPromotionRefusal = "no procedure was promoted: the validation set holds "
                    + cases.size() + " observation(s) the model was not built from, and "
                    + MINIMUM_VALIDATION_CASES + " independent observations are required; "
                    + "the fast path is used as an in-sample derived shortcut of the reference, "
                    + "not as a validated procedure";
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
                lastPromotionRefusal = "no procedure was promoted: the reference method refused "
                        + "on the independent validation set (" + e.getMessage() + ")";
                return null;
            }
            boolean fast = session.survivingHypotheses().get(0).evaluate(observation.input());
            if (reference == fast) {
                agreed++;
            }
        }
        double agreement = (double) agreed / (double) cases.size();
        if (agreement < 1.0) {
            lastPromotionRefusal = "no procedure was promoted: the fast path disagreed with the "
                    + "reference on " + (cases.size() - agreed) + " of " + cases.size()
                    + " independent observation(s)";
            return null;
        }
        Procedure promoted = new Procedure(PROCEDURE_ID,
                "the settled-model lookup for: "
                        + session.survivingHypotheses().get(0).statement(),
                FAMILY_MICRO_WORLD, "v1",
                "the model has settled on exactly one surviving hypothesis",
                Set.of(MicroWorldSettledModelMethod.PRECONDITION_MODEL_SETTLED),
                MicroWorldReferenceMethod.ID,
                cases.size(), agreement, 0, 0, timestamps.get(), null,
                ValidationKind.INDEPENDENT);
        store.recordProcedure(WORKSPACE_ID, promoted);
        lastPromotionRefusal = null;
        return promoted;
    }

    /**
     * W45.0 — the observations that may serve as INDEPENDENT validation data.
     *
     * <p>An observation qualifies only when the model was NOT built from it and it is not a
     * held conflict. The set is computed from the session's own record of what it applied,
     * so it is the same rule the reference method and the fast path use.</p>
     *
     * @param session    the session, holding what the model was built from
     * @param remembered every observation the workspace holds
     * @return the candidates for an independent validation
     */
    private List<Observation> independentValidationCandidates(MicroWorldSession session,
                                                              List<Observation> remembered) {
        Set<String> applied = new java.util.LinkedHashSet<>();
        for (Observation observation : session.appliedObservations()) {
            applied.add(observation.id());
        }
        List<Observation> candidates = new ArrayList<>();
        for (Observation observation : remembered) {
            if (applied.contains(observation.id())) {
                continue;
            }
            if (!observation.constrainsModel()) {
                continue;
            }
            candidates.add(observation);
        }
        return candidates;
    }

    /**
     * W45.0 — restores the persisted regulatory state.
     *
     * <p>ADR-W44-01 lists the drives among the cognitive state the cognitive store persists,
     * and until W45 they were the one item on that list that was not persisted: the state
     * lived in the in-memory adapter, so a restart reset every drive to neutral. The
     * application calls this once, when it starts, and the kernel persists the new state at
     * the end of every cycle.</p>
     *
     * @return the restored state, or null when nothing is persisted
     */
    public RegulatoryState restoreRegulatoryState() {
        RegulatoryState persisted = store.regulatoryState(WORKSPACE_ID);
        if (persisted != null) {
            regulator.restore(persisted);
        }
        return persisted;
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
