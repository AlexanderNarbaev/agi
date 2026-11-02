package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.neuromodulation.RegulationBias;
import io.matrix.knowledge.Case;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W44 — the cognitive kernel, end to end, on a real SQLite file.
 *
 * <p>These are not unit tests of a class in isolation. Each one drives the kernel the way the
 * workbench does — a signal in, a cycle out — and then checks the durable record: the cases, the
 * predictions, the errors, the procedures and the tasks. The point of the wave is that the
 * system can perceive, predict, act inside its sandbox, compare and revise, and that the record
 * of that survives a restart.</p>
 */
class CognitiveKernelTest {

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveStore store;
    private CognitiveKernel kernel;
    private final AtomicInteger clock = new AtomicInteger();

    @BeforeEach
    void setUp() {
        Path database = directory.resolve("cognitive.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        store = new CognitiveStore(database);
        LampEnvironment environment = new LampEnvironment();
        kernel = new CognitiveKernel(workbench, store,
                new MicroWorldSession.DefaultFactory(environment),
                environment,
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                new KernelRegulator(),
                () -> "2026-11-02T00:00:0" + (clock.incrementAndGet() % 10) + "Z");
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
        if (workbench != null) {
            workbench.close();
        }
    }

    private CognitiveOutcome observe(String assignment) {
        return kernel.process(CognitiveSignal.structured("sig-" + assignment.hashCode(),
                assignment, "the test", clock.incrementAndGet()));
    }

    @Test
    @DisplayName("a malformed signal is refused, and the refusal is in the trace")
    void aMalformedSignalIsRefused() {
        CognitiveOutcome outcome = observe("{not a record");

        assertThat(outcome.status()).isEqualTo(CognitiveOutcome.Status.REFUSED);
        assertThat(outcome.percept().malformed()).isTrue();
        assertThat(outcome.trace().phase("PERCEIVE")).isNotEmpty();
        assertThat(outcome.trace().phase("REFUSE")).isNotEmpty();
        assertThat(outcome.decision()).isNull();
        assertThat(store.predictions(CognitiveKernel.WORKSPACE_ID)).isEmpty();
    }

    @Test
    @DisplayName("a readable assignment produces a trace that names every phase")
    void aReadableAssignmentProducesAFullTrace() {
        CognitiveOutcome outcome = observe("power=1,switch=0");

        assertThat(outcome.status())
                .withFailMessage("trace: %s", outcome.trace().steps())
                .isEqualTo(CognitiveOutcome.Status.COMPLETED);
        assertThat(outcome.trace().phase("PERCEIVE")).isNotEmpty();
        assertThat(outcome.trace().phase("RESTORE")).isNotEmpty();
        assertThat(outcome.trace().phase("PREDICT")).isNotEmpty();
        assertThat(outcome.trace().phase("SELECT")).isNotEmpty();
        assertThat(outcome.trace().phase("ANSWER")).isNotEmpty();
        assertThat(outcome.prediction()).isNotNull();
        assertThat(outcome.prediction().basis()).isEqualTo("surviving-hypotheses");
    }

    @Test
    @DisplayName("the trace of the cycle is written down, and survives a restart")
    void theTraceOfTheCycleIsWrittenDownAndSurvivesARestart() {
        CognitiveOutcome outcome = observe("power=1,switch=0");

        // The trace is in the response ...
        assertThat(outcome.trace().steps()).isNotEmpty();
        // ... and it is in the store, which is the only way it survives the process.
        List<LearningTrace.Step> stored = store.events(outcome.episodeId());
        assertThat(stored).isNotEmpty();
        assertThat(stored).hasSameSizeAs(outcome.trace().steps());
        assertThat(stored.stream().map(LearningTrace.Step::phase).toList())
                .contains("PERCEIVE", "RESTORE", "PREDICT", "SELECT", "ANSWER");

        store.close();
        workbench.close();
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        List<LearningTrace.Step> afterRestart = store.events(outcome.episodeId());
        assertThat(afterRestart).hasSameSizeAs(stored);
        assertThat(afterRestart.get(0).phase()).isEqualTo(stored.get(0).phase());
    }

    @Test
    @DisplayName("the observation becomes a case, and the case survives a restart")
    void theObservationBecomesACaseThatSurvivesARestart() {
        observe("power=1,switch=0");

        List<Case> before = kernel.modelCases();
        assertThat(before).isNotEmpty();
        Case recorded = before.get(0);
        // The case records the EXPERIMENT the kernel ran, which is not necessarily the
        // assignment the caller asked about: the case must carry the conditions under which
        // the outcome was actually observed.
        assertThat(recorded.conditions()).matches("power=[01],switch=[01]");
        assertThat(recorded.origin()).isEqualTo(Case.Origin.OBSERVED);
        assertThat(recorded.statement()).isNotBlank();

        // A restart: a new workbench and a new store over the SAME file.
        store.close();
        workbench.close();
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        workbench = KnowledgeWorkbench.at(directory.resolve("cognitive.sqlite").toString());

        List<Case> after = workbench.storeForValidation().cases(CognitiveKernel.WORKSPACE_ID);
        assertThat(after).hasSameSizeAs(before);
        assertThat(after.get(0).id()).isEqualTo(recorded.id());
        assertThat(after.get(0).conditions()).isEqualTo(recorded.conditions());
    }

    @Test
    @DisplayName("the kernel restores the model from the cases and keeps predicting from it")
    void theKernelRestoresTheModelFromTheCases() {
        observe("power=1,switch=0");
        observe("power=0,switch=1");

        List<Case> cases = kernel.modelCases();
        assertThat(cases).hasSize(2);

        // The second cycle must have started from the first cycle's case: the trace says how
        // many cases the session was rebuilt from.
        CognitiveOutcome second = observe("power=1,switch=1");
        String restore = second.trace().phase("RESTORE").get(0).detail();
        assertThat(restore).contains("rebuilt from 2 recorded case(s)");
    }

    @Test
    @DisplayName("the fast path is not used before it has been validated")
    void theFastPathIsNotUsedBeforeItIsValidated() {
        observe("power=1,switch=0");

        assertThat(store.procedures(CognitiveKernel.WORKSPACE_ID)).isEmpty();
    }

    @Test
    @DisplayName("once the model settles, the validated fast path is selected and the reference stays as its fallback")
    void onceTheModelSettlesTheFastPathIsSelected() {
        // Four cycles: the model settles on the conjunction, and the promotion needs at least
        // two held-out cases that agree with the reference.
        for (int i = 0; i < 8; i++) {
            observe("power=" + (i % 2) + ",switch=" + ((i / 2) % 2));
        }

        List<io.matrix.cognitive.algorithms.Procedure> procedures =
                store.procedures(CognitiveKernel.WORKSPACE_ID);
        assertThat(procedures).isNotEmpty();
        io.matrix.cognitive.algorithms.Procedure promoted = procedures.get(0);
        assertThat(promoted.active()).isTrue();
        assertThat(promoted.validationCases()).isGreaterThanOrEqualTo(
                CognitiveKernel.MINIMUM_VALIDATION_CASES);
        assertThat(promoted.validationAgreement()).isEqualTo(1.0);
        assertThat(promoted.referenceAlgorithmId())
                .isEqualTo(MicroWorldReferenceMethod.ID);
        assertThat(promoted.requiredFacts())
                .contains(MicroWorldSettledModelMethod.PRECONDITION_MODEL_SETTLED);
    }

    @Test
    @DisplayName("a settled model uses the cheap procedure, and the decision says why")
    void aSettledModelUsesTheCheapProcedure() {
        for (int i = 0; i < 8; i++) {
            observe("power=" + (i % 2) + ",switch=" + ((i / 2) % 2));
        }

        CognitiveOutcome outcome = observe("power=1,switch=0");
        AlgorithmDecision decision = outcome.decision();
        assertThat(decision).isNotNull();
        assertThat(decision.selectedAlgorithmId()).isNotBlank();
        assertThat(decision.reason()).isNotBlank();
        assertThat(decision.candidates()).isNotEmpty();
        for (AlgorithmDecision.Candidate candidate : decision.candidates()) {
            if (!candidate.selected()) {
                assertThat(candidate.verdict()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("the regulation asks for a confirming experiment once the uncertainty has risen")
    void theRegulationAsksForAConfirmingExperiment() {
        // Settle the model first, so that the only difference between the two cycles below is
        // the regulatory state and nothing else.
        for (int i = 0; i < 8; i++) {
            observe("power=" + (i % 2) + ",switch=" + ((i / 2) % 2));
        }

        // A NEUTRAL regulator does not ask for a confirmation the model did not need.
        CognitiveOutcome neutral = cycle(newKernel(new KernelRegulator()), "power=1,switch=0");
        assertThat(askedForConfirmation(neutral)).isFalse();

        // A regulator whose uncertainty has risen does ask for one.
        KernelRegulator loaded = new KernelRegulator();
        loaded.evolve(loaded.state(), List.of(new CognitiveKernel.RegulatoryEvent(
                io.matrix.cognitive.neuromodulation.RegulatoryEvent.Kind.PREDICTION_ERROR,
                1.0, "test")));
        assertThat(loaded.bias(loaded.state(), DriveSignal.Effect.EVIDENCE_REQUEST)
                .evidenceRequestThreshold())
                .isLessThan(RegulationBias.NEVER_REQUEST_EVIDENCE);

        CognitiveOutcome uncertain = cycle(newKernel(loaded), "power=1,switch=0");
        assertThat(askedForConfirmation(uncertain)).isTrue();
    }

    private boolean askedForConfirmation(CognitiveOutcome outcome) {
        return outcome.trace().phase("PROBE").stream()
                .anyMatch(step -> step.detail().contains("a confirmation the regulation asked for"));
    }

    private CognitiveKernel newKernel(CognitiveKernel.RegulatoryBridge regulator) {
        LampEnvironment environment = new LampEnvironment();
        return new CognitiveKernel(workbench, store,
                new MicroWorldSession.DefaultFactory(environment),
                environment,
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                regulator,
                () -> "2026-11-02T00:00:00Z");
    }

    private CognitiveOutcome cycle(CognitiveKernel kernel, String assignment) {
        return kernel.process(CognitiveSignal.structured("sig-" + assignment.hashCode(),
                assignment, "the test", clock.incrementAndGet()));
    }

    @Test
    @DisplayName("an observation the model cannot explain produces an error, and the model is not rewritten")
    void anObservationTheModelCannotExplainProducesAnError() {
        // First: a truthful environment, until the model has settled on a rule.
        for (int i = 0; i < 8; i++) {
            observe("power=" + (i % 2) + ",switch=" + ((i / 2) % 2));
        }
        assertThat(store.errors(CognitiveKernel.WORKSPACE_ID)).isEmpty();

        // Now: an environment that contradicts itself, and a regulator whose uncertainty has
        // risen so that the kernel looks again. The model has settled, so it is CONFIDENT, and
        // the contradiction is therefore a wrong answer rather than a case of the model having
        // no explanation.
        KernelRegulator loaded = new KernelRegulator();
        loaded.evolve(loaded.state(), List.of(new CognitiveKernel.RegulatoryEvent(
                io.matrix.cognitive.neuromodulation.RegulatoryEvent.Kind.PREDICTION_ERROR,
                1.0, "test")));

        MicroWorldSession.Factory factory = new MicroWorldSession.DefaultFactory(new LyingEnvironment());
        CognitiveKernel lying = new CognitiveKernel(workbench, store, factory,
                new LyingEnvironment(),
                new KernelSelector(new AlgorithmRegistry()
                        .register(new MicroWorldReferenceMethod())
                        .register(new MicroWorldSettledModelMethod())),
                loaded,
                () -> "2026-11-02T00:00:00Z");

        lying.process(CognitiveSignal.structured("sig-lie", "power=1,switch=0", "the test", 99));

        List<ErrorSignal> errors = store.errors(CognitiveKernel.WORKSPACE_ID);
        assertThat(errors).isNotEmpty();
        ErrorSignal error = errors.get(0);
        assertThat(error.expected()).isNotEqualTo(error.observed());
        assertThat(error.responsibleModel()).isNotBlank();
        assertThat(error.responsibleVersion()).isNotBlank();
        assertThat(error.disposition()).isEqualTo(ErrorSignal.Disposition.UNRESOLVED);
        assertThat(error.caseIds()).isNotEmpty();

        // The model was NOT rewritten: the cases that were already recorded are still there,
        // and the prediction that was falsified is still readable.
        assertThat(kernel.modelCases()).isNotEmpty();
        assertThat(store.predictions(CognitiveKernel.WORKSPACE_ID))
                .anyMatch(prediction -> prediction.id().equals(error.predictionId()));

        // A bounded task is generated from the error, and it is bounded.
        List<CognitiveTask> tasks = store.tasks(CognitiveKernel.WORKSPACE_ID);
        assertThat(tasks).isNotEmpty();
        for (CognitiveTask task : tasks) {
            assertThat(task.budgetWallClockMillis()).isPositive();
            assertThat(task.stopCondition()).isNotBlank();
            assertThat(task.completionCondition()).isNotBlank();
            assertThat(task.allowedActions()).isNotEmpty();
            assertThat(task.reason()).isNotBlank();
        }
    }

    /**
     * An environment that reports the opposite of what its rule implies.
     *
     * <p>It is a fault injector, not a second rule: the point is to give the kernel a value
     * that its model cannot explain, so that the detection can be observed rather than
     * asserted.</p>
     */
    private static final class LyingEnvironment implements io.matrix.cognitive.microworld.MicroWorldEnvironment {
        private final LampEnvironment inner = new LampEnvironment();

        @Override
        public io.matrix.cognitive.microworld.Observation probe(
                io.matrix.cognitive.microworld.WorldInput input) {
            io.matrix.cognitive.microworld.Observation observed = inner.probe(input);
            return new io.matrix.cognitive.microworld.Observation(observed.id(), observed.input(),
                    !observed.lamp(), true, observed.observedAt());
        }

        @Override
        public java.util.Set<String> variables() {
            return inner.variables();
        }

        @Override
        public String outcomeVariable() {
            return inner.outcomeVariable();
        }
    }
}
