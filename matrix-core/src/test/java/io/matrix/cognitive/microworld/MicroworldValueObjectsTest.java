package io.matrix.cognitive.microworld;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.4 — the small value objects of the package: what they refuse and what they say.
 *
 * <p>These records carry the refusal rules of the micro-world. A record that accepts a blank
 * identifier or a missing timestamp is a record whose missing fields will be discovered later,
 * in the middle of an episode, as a null that nobody checked.</p>
 */
class MicroworldValueObjectsTest {

    private static final WorldInput ASSIGNMENT = WorldInput.of(true, false);

    @Test
    @DisplayName("the assignment answers whether it names a variable, and prints its key")
    void theAssignmentAnswersAndPrints() {
        assertThat(ASSIGNMENT.has("power")).isTrue();
        assertThat(ASSIGNMENT.has("flux")).isFalse();
        assertThat(ASSIGNMENT.variables()).containsExactly("power", "switch");
        assertThat(ASSIGNMENT.toString()).isEqualTo("WorldInput[power=1,switch=0]");
    }

    @Test
    @DisplayName("the observation prints its identity and its assignment")
    void theObservationPrintsItsIdentity() {
        Observation observation = new Observation("obs-1", ASSIGNMENT, true, false, "now");

        assertThat(observation.toString()).contains("obs-1").contains("power=1,switch=0")
                .contains("lamp=true");
        assertThat(new Observation("obs-2", ASSIGNMENT, true, true, "now").toString())
                .contains("ADVERSARIAL");
    }

    @Test
    @DisplayName("the hypothesis prints its evidence and answers an observation")
    void theHypothesisPrintsAndAnswers() {
        RuleHypothesis hypothesis = HypothesisSpace.allBooleanFunctionsOfTwoVariables().get(1);
        Observation observation = new Observation("obs-1", ASSIGNMENT, false, false, "now");

        assertThat(hypothesis.toString()).contains(hypothesis.id())
                .contains("supporting=0").contains("contradicting=0");
        assertThat(hypothesis.agreesWith(observation)).isTrue();
        assertThat(hypothesis.agreesWith(null)).isFalse();
        assertThat(hypothesis.withCounts(3, 1).supportingCases()).isEqualTo(3);
        assertThat(hypothesis.withCounts(3, 1).contradictingCases()).isEqualTo(1);
    }

    @Test
    @DisplayName("the hypothesis refuses a blank identity, an empty table and a negative count")
    void theHypothesisIsChecked() {
        Map<String, Boolean> table = Map.of("power=1,switch=1", true);

        assertThatThrownBy(() -> new RuleHypothesis(" ", "s", table, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleHypothesis("h-1", " ", table, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleHypothesis("h-1", "s", Map.of(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleHypothesis("h-1", "s", table, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the prediction prints itself and says whether it was a decision")
    void thePredictionPrintsItself() {
        HypothesisPrediction prediction = new HypothesisPrediction("lamp@power=1,switch=0", false,
                false, List.of("h-1001"), List.of("obs-1"));

        assertThat(prediction.decided()).isFalse();
        assertThat(prediction.toString()).contains("lamp@power=1,switch=0").contains("unanimous=false");
        assertThat(new HypothesisPrediction("lamp@power=1,switch=0", false, true, List.of(), List.of())
                .decided()).isTrue();
        assertThat(new HypothesisPrediction("lamp@x", false, true, null, null).disagreeingHypothesisIds())
                .isEmpty();
    }

    @Test
    @DisplayName("the experiment prints itself and names its assignment")
    void theExperimentPrintsItself() {
        Experiment experiment = new Experiment("exp-1", ASSIGNMENT, 0.5, "because");

        assertThat(experiment.assignmentKey()).isEqualTo("power=1,switch=0");
        assertThat(experiment.toString()).contains("exp-1").contains("gain=0.5");
    }

    @Test
    @DisplayName("the experiment refuses a gain outside [0,1] and an empty rationale")
    void theExperimentIsChecked() {
        assertThatThrownBy(() -> new Experiment("exp-1", ASSIGNMENT, 1.5, "because"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Experiment("exp-1", ASSIGNMENT, -0.1, "because"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Experiment("exp-1", ASSIGNMENT, 0.5, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Experiment("exp-1", null, 0.5, "because"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the rejection prints itself and names the hypothesis it rejects")
    void theRejectionPrintsItself() {
        RuleHypothesis hypothesis = HypothesisSpace.allBooleanFunctionsOfTwoVariables().get(0);
        RejectedHypothesis rejection = new RejectedHypothesis(hypothesis, "contradicted");

        assertThat(rejection.hypothesisId()).isEqualTo(hypothesis.id());
        assertThat(rejection.toString()).contains(hypothesis.id()).contains("contradicted");
        assertThatThrownBy(() -> new RejectedHypothesis(hypothesis, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RejectedHypothesis(null, "because"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the error prints itself and names its assignment")
    void theErrorPrintsItself() {
        PredictionError error = new PredictionError("err-1", ASSIGNMENT, false, true, "h-0001", "note");

        assertThat(error.assignmentKey()).isEqualTo("power=1,switch=0");
        assertThat(error.toString()).contains("err-1").contains("h-0001");
        assertThatThrownBy(() -> new PredictionError(" ", ASSIGNMENT, false, true, "h-0001", "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PredictionError("err-1", null, false, true, "h-0001", "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PredictionError("err-1", ASSIGNMENT, false, true, " ", "n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PredictionError("err-1", ASSIGNMENT, false, true, "h-0001", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the space prints itself and two spaces with the same candidates are equal")
    void theSpacePrintsAndCompares() {
        HypothesisSpace space = HypothesisSpace.unbiased();
        HypothesisSpace same = new HypothesisSpace(List.of("power", "switch"),
                HypothesisSpace.allBooleanFunctionsOfTwoVariables());
        HypothesisSpace other = new HypothesisSpace(List.of("power", "switch"),
                List.of(HypothesisSpace.allBooleanFunctionsOfTwoVariables().get(0)));

        assertThat(space.toString()).contains("variables=[power, switch]").contains("candidates=16");
        assertThat(space).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(space).isNotEqualTo(other);
        assertThat(space.variables()).containsExactly("power", "switch");
    }

    @Test
    @DisplayName("a proposal on a SETTLED model is harmless: an assignment with a gain of zero, "
            + "never an exception")
    void aProposalOnASettledModelIsHarmless() {
        LampEnvironment environment = new LampEnvironment();
        MicroWorldSession session = new MicroWorldSession("ep-settled", environment);
        session.observe(environment.probe(WorldInput.of(false, false)));
        session.observe(environment.probe(WorldInput.of(false, true)));
        session.observe(environment.probe(WorldInput.of(true, false)));
        session.observe(environment.probe(WorldInput.of(true, true)));

        assertThat(session.survivingHypotheses()).hasSize(1);

        Experiment proposal = session.proposeExperiment();

        assertThat(proposal).isNotNull();
        assertThat(proposal.expectedInformationGain()).isZero();
        assertThat(proposal.rationale()).isNotBlank();
    }
}
