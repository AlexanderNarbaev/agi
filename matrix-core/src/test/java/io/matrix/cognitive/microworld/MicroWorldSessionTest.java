package io.matrix.cognitive.microworld;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.4 — the micro-world session: what the learner believes, what it ruled out, and what it
 * refuses to rewrite.
 *
 * <p>Every test here is an assertion about the LEARNER, not about the fixture: the survivors are
 * checked against the sixteen functions of two variables, so a test that claimed "the conjunction
 * survives" would fail the moment the fixture changed — and it cannot be made to pass by
 * choosing a friendlier starting set.</p>
 */
class MicroWorldSessionTest {

    /** The identifier of the function that is true only at power=1,switch=1. */
    private static final String CONJUNCTION_ID = "h-0001";

    /** The identifier of the function that is true at power=0,switch=0 and power=1,switch=1. */
    private static final String XNOR_ID = "h-1001";

    private static MicroWorldSession sessionWith(LampEnvironment environment, Observation... observations) {
        MicroWorldSession session = new MicroWorldSession("ep-test", environment);
        for (Observation observation : observations) {
            session.observe(observation);
        }
        return session;
    }

    private static LampEnvironment environment() {
        return new LampEnvironment();
    }

    @Test
    @DisplayName("TEST-LEARN-001 — two observations leave several hypotheses, the prediction is "
            + "not settled and the proposal separates the survivors")
    void learn001AmbiguitySurvivesAndTheProposalDiscriminates() {
        LampEnvironment environment = environment();
        Observation first = environment.probe(WorldInput.of(false, true));
        Observation second = environment.probe(WorldInput.of(true, true));
        assertThat(first.lamp()).isFalse();
        assertThat(second.lamp()).isTrue();

        MicroWorldSession session = sessionWith(environment, first, second);

        // MORE THAN ONE hypothesis survives: the two observations constrain two of the four
        // cells and say nothing about the other two.
        assertThat(session.survivingHypotheses()).hasSizeGreaterThan(1);
        assertThat(session.survivingHypotheses()).extracting(RuleHypothesis::id)
                .containsExactlyInAnyOrder(CONJUNCTION_ID, "h-0011", XNOR_ID, "h-1011");

        // The prediction for the UNOBSERVED assignment is not settled.
        HypothesisPrediction prediction = session.predict(WorldInput.of(true, false));
        assertThat(prediction.unanimous()).isFalse();
        assertThat(prediction.disagreeingHypothesisIds()).isNotEmpty();

        // The proposal is an assignment that actually discriminates.
        Experiment proposal = session.proposeExperiment();
        assertThat(proposal.expectedInformationGain()).isGreaterThan(0.0);
        assertThat(proposal.input().key()).isIn("power=0,switch=0", "power=1,switch=0");

        // And the discrimination is real: applying the outcome strictly reduces the survivors.
        int before = session.survivingHypotheses().size();
        session.applyOutcome(environment.probe(proposal.input()));
        assertThat(session.survivingHypotheses().size()).isLessThan(before);
    }

    @Test
    @DisplayName("TEST-LEARN-002 — the incompatible candidates are eliminated, the observations "
            + "and the earlier prediction are preserved, and the conjunction survives")
    void learn002TheIncompatibleCandidatesAreEliminatedAndTheHistoryIsPreserved() {
        LampEnvironment environment = environment();
        Observation first = environment.probe(WorldInput.of(false, true));
        Observation second = environment.probe(WorldInput.of(true, true));
        MicroWorldSession session = sessionWith(environment, first, second);

        HypothesisPrediction earlier = session.predict(WorldInput.of(true, false));
        assertThat(earlier.unanimous()).isFalse();

        Observation third = environment.probe(WorldInput.of(true, false));
        assertThat(third.lamp()).isFalse();
        session.applyOutcome(third);

        // The candidates that contradict the third observation are gone from the surviving set
        // and are KEPT, with the observation that eliminated them.
        assertThat(session.survivingHypotheses()).extracting(RuleHypothesis::id)
                .containsExactlyInAnyOrder(CONJUNCTION_ID, XNOR_ID);
        assertThat(session.rejectedHypotheses()).extracting(RejectedHypothesis::hypothesisId)
                .contains("h-0011", "h-1011");
        assertThat(session.rejectedHypotheses())
                .allSatisfy(rejection -> assertThat(rejection.reason()).contains("observation"));

        // The original observations are still there.
        assertThat(session.observations()).hasSize(3);
        assertThat(session.observations()).extracting(Observation::id)
                .containsExactly(first.id(), second.id(), third.id());

        // The earlier prediction is preserved.
        assertThat(session.predictions()).hasSize(1);
        assertThat(session.predictions().get(0)).isEqualTo(earlier);

        // And nothing was recorded as an error: the outcome agreed with what was predicted.
        assertThat(session.predictionErrors()).isEmpty();

        // The conjunction is what survives, and it says what it says.
        RuleHypothesis conjunction = session.survivingHypotheses().stream()
                .filter(hypothesis -> hypothesis.id().equals(CONJUNCTION_ID)).findFirst().orElseThrow();
        assertThat(conjunction.evaluate(WorldInput.of(true, true))).isTrue();
        assertThat(conjunction.evaluate(WorldInput.of(true, false))).isFalse();
        assertThat(conjunction.statement()).contains("power=1,switch=1");
    }

    @Test
    @DisplayName("TEST-LEARN-003 — a prediction is marked as PREDICTED and not observed")
    void learn003APredictionIsNotAnObservation() {
        LampEnvironment environment = environment();
        MicroWorldSession session = sessionWith(environment,
                environment.probe(WorldInput.of(false, true)),
                environment.probe(WorldInput.of(true, true)),
                environment.probe(WorldInput.of(true, false)));

        HypothesisPrediction prediction = session.predict(WorldInput.of(false, false));

        assertThat(prediction.value()).isFalse();
        assertThat(prediction.subject()).isEqualTo("lamp@power=0,switch=0");

        assertThat(session.observedAssignments()).contains("power=0,switch=1", "power=1,switch=0",
                "power=1,switch=1");
        assertThat(session.observedAssignments()).doesNotContain("power=0,switch=0");
        assertThat(session.predictedButUnobservedAssignments()).containsExactly("power=0,switch=0");

        // The same fact, asked about one assignment at a time.
        assertThat(session.hasObserved(WorldInput.of(true, true))).isTrue();
        assertThat(session.hasObserved(WorldInput.of(false, false))).isFalse();
        assertThat(session.hasObserved(null)).isFalse();

        // The prediction is in the prediction list and NOT in the observation list.
        assertThat(session.observations()).extracting(Observation::assignmentKey)
                .doesNotContain("power=0,switch=0");
        assertThat(session.predictions()).extracting(HypothesisPrediction::subject)
                .containsExactly("lamp@power=0,switch=0");
    }

    @Test
    @DisplayName("TEST-LEARN-004 — an adversarial observation creates a prediction error and the "
            + "model is NOT silently rewritten")
    void learn004AnAdversarialObservationCreatesAnErrorAndDoesNotRewriteTheModel() {
        LampEnvironment environment = environment();
        MicroWorldSession session = sessionWith(environment,
                environment.probe(WorldInput.of(false, true)),
                environment.probe(WorldInput.of(true, true)),
                environment.probe(WorldInput.of(true, false)));

        // W45.0. The prediction must be a BELIEF for the observation to contradict it. The
        // two survivors agree about power=1,switch=0, so the prediction there is unanimous;
        // about power=0,switch=0 they disagree, and an observation that differs from an
        // unsettled prediction is ordinary learning, not a conflict.
        HypothesisPrediction prediction = session.predict(WorldInput.of(true, false));
        assertThat(prediction.value()).isFalse();
        assertThat(prediction.unanimous())
                .withFailMessage("the prediction must be settled for the case to be a conflict")
                .isTrue();

        Observation adversarial = environment.adversarialProbe(WorldInput.of(true, false));
        assertThat(adversarial.lamp()).isTrue();
        assertThat(adversarial.adversarial()).isTrue();

        session.applyOutcome(adversarial);

        // The error is recorded, with the hypothesis that is responsible for it.
        assertThat(session.predictionErrors()).hasSize(1);
        PredictionError error = session.predictionErrors().get(0);
        assertThat(error.predicted()).isFalse();
        assertThat(error.observed()).isTrue();
        assertThat(error.responsibleHypothesisId()).isEqualTo(CONJUNCTION_ID);
        assertThat(error.note()).isNotBlank();

        // The model was NOT rewritten: the conjunction is still standing.
        assertThat(session.survivingHypotheses()).extracting(RuleHypothesis::id)
                .contains(CONJUNCTION_ID);

        // And the hypotheses that made the prediction are flagged for review.
        assertThat(session.hypothesesNeedingReview()).isNotEmpty();
        assertThat(session.hypothesesNeedingReview()).extracting(RuleHypothesis::id)
                .contains(CONJUNCTION_ID);

        // The observation is recorded, but it was NOT applied to the model.
        assertThat(session.observations()).hasSize(4);
        assertThat(session.appliedObservations()).hasSize(3);
        assertThat(session.appliedObservations()).extracting(Observation::id)
                .doesNotContain(adversarial.id());
    }

    @Test
    @DisplayName("TEST-LEARN-005 — a training set that supports two hypotheses shows the "
            + "ambiguity, and the proposal separates them")
    void learn005TwoSupportedHypothesesShowTheAmbiguity() {
        LampEnvironment environment = environment();
        MicroWorldSession session = sessionWith(environment,
                environment.probe(WorldInput.of(false, false)),
                environment.probe(WorldInput.of(true, true)));

        assertThat(session.survivingHypotheses()).hasSizeGreaterThanOrEqualTo(2);

        // The survivors disagree about the two assignments nobody has looked at.
        HypothesisPrediction first = session.predict(WorldInput.of(false, true));
        HypothesisPrediction second = session.predict(WorldInput.of(true, false));
        assertThat(first.unanimous()).isFalse();
        assertThat(second.unanimous()).isFalse();

        Experiment proposal = session.proposeExperiment();
        assertThat(proposal.expectedInformationGain()).isGreaterThan(0.0);

        int before = session.survivingHypotheses().size();
        session.applyOutcome(environment.probe(proposal.input()));
        assertThat(session.survivingHypotheses().size()).isLessThan(before);
    }

    @Test
    @DisplayName("the proposal is deterministic: the same state always proposes the same "
            + "assignment")
    void theProposalIsDeterministic() {
        LampEnvironment environment = environment();
        MicroWorldSession session = sessionWith(environment,
                environment.probe(WorldInput.of(false, true)),
                environment.probe(WorldInput.of(true, true)));

        Experiment first = session.proposeExperiment();
        Experiment second = session.proposeExperiment();

        assertThat(second.input().key()).isEqualTo(first.input().key());
        assertThat(second.expectedInformationGain()).isEqualTo(first.expectedInformationGain());
        assertThat(second.rationale()).isEqualTo(first.rationale());
    }

    @Test
    @DisplayName("an observation is never deleted, and a rejected hypothesis is never forgotten")
    void nothingIsEverDeleted() {
        LampEnvironment environment = environment();
        Observation first = environment.probe(WorldInput.of(false, true));
        Observation second = environment.probe(WorldInput.of(true, true));
        MicroWorldSession session = sessionWith(environment, first, second);

        assertThat(session.observations()).containsExactly(first, second);
        assertThat(session.rejectedHypotheses()).isNotEmpty();

        int rejectedCount = session.rejectedHypotheses().size();
        session.observe(environment.probe(WorldInput.of(true, false)));

        assertThat(session.observations()).hasSize(3);
        assertThat(session.rejectedHypotheses()).hasSizeGreaterThanOrEqualTo(rejectedCount);
        assertThat(session.rejectedHypotheses()).extracting(RejectedHypothesis::hypothesisId)
                .doesNotContain(CONJUNCTION_ID);
    }

    @Test
    @DisplayName("recording the same observation twice changes nothing; a different observation "
            + "under the same identifier is refused")
    void aDuplicateObservationIsANoOpAndAConflictIsRefused() {
        LampEnvironment environment = environment();
        Observation observation = environment.probe(WorldInput.of(false, true));
        MicroWorldSession session = sessionWith(environment, observation);

        session.observe(observation);
        assertThat(session.observations()).hasSize(1);

        Observation conflicting = new Observation(observation.id(), observation.input(), true,
                false, observation.observedAt());
        assertThatThrownBy(() -> session.observe(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(observation.id());
    }

    @Test
    @DisplayName("TEST-LEARN-005 — an observation that differs from an UNSETTLED prediction is "
            + "learning, not a conflict")
    void learn005AnObservationAgainstAnUnsettledPredictionIsLearning() {
        LampEnvironment environment = environment();
        MicroWorldSession session = sessionWith(environment,
                environment.probe(WorldInput.of(false, true)),
                environment.probe(WorldInput.of(true, true)),
                environment.probe(WorldInput.of(true, false)));

        // About power=0,switch=0 the two survivors DISAGREE, so the prediction there is not a
        // belief.
        HypothesisPrediction prediction = session.predict(WorldInput.of(false, false));
        assertThat(prediction.unanimous()).isFalse();

        Observation observation = environment.probe(WorldInput.of(false, false));
        session.applyOutcome(observation);

        // No conflict is recorded: the model had no belief to contradict.
        assertThat(session.predictionErrors()).isEmpty();
        // And the observation IS applied: it is the data the model needed.
        assertThat(session.appliedObservations()).hasSize(4);
        assertThat(session.quarantinedObservations()).isEmpty();
        assertThat(session.survivingHypotheses()).hasSize(1);
    }

    @Test
    @DisplayName("the status line reports the observations, the survivors and the open errors")
    void theStatusLineSummarisesTheSession() {
        LampEnvironment environment = environment();
        MicroWorldSession session = sessionWith(environment,
                environment.probe(WorldInput.of(false, true)));

        String line = session.statusLine();
        assertThat(line).contains("ep-test");
        assertThat(line).contains("1 observations");
        assertThat(line).contains("surviving hypotheses");
        assertThat(line).contains("prediction errors");
        assertThat(line).doesNotContain("\n");
    }

    @Test
    @DisplayName("a session can be rebuilt from the observations it already holds")
    void aSessionCanBeRebuiltFromItsObservations() {
        LampEnvironment environment = environment();
        Observation first = environment.probe(WorldInput.of(false, true));
        Observation second = environment.probe(WorldInput.of(true, true));

        MicroWorldSession.Factory factory = new MicroWorldSession.DefaultFactory(environment);
        MicroWorldSession restored = factory.create("ep-restored", List.of(first, second));

        assertThat(restored.observations()).hasSize(2);
        assertThat(restored.survivingHypotheses()).hasSize(4);
        assertThat(restored.episodeId()).isEqualTo("ep-restored");
    }

    @Test
    @DisplayName("an environment whose variables are not the micro-world's is refused")
    void anEnvironmentWithTheWrongVariablesIsRefused() {
        MicroWorldEnvironment wrong = new MicroWorldEnvironment() {
            @Override
            public Observation probe(WorldInput input) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public java.util.Set<String> variables() {
                return java.util.Set.of("power");
            }

            @Override
            public String outcomeVariable() {
                return "lamp";
            }
        };

        assertThatThrownBy(() -> new MicroWorldSession("ep-test", wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    @DisplayName("a hypothesis space with no candidates is refused")
    void anEmptyHypothesisSpaceIsRefused() {
        assertThatThrownBy(() -> new HypothesisSpace(List.of("power", "switch"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one candidate");
    }
}
