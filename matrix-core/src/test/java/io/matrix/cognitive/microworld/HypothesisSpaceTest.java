package io.matrix.cognitive.microworld;

import io.matrix.cognitive.MicroWorldCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.4 — the hypothesis space: complete, unbiased, and exact.
 */
class HypothesisSpaceTest {

    /** The number of Boolean functions over two variables. */
    private static final int FUNCTION_COUNT = 16;

    private static HypothesisSpace space() {
        return HypothesisSpace.unbiased();
    }

    @Test
    @DisplayName("the space holds all sixteen Boolean functions of the two variables, each with "
            + "a distinct identifier and a readable statement")
    void theSpaceIsCompleteAndUnbiased() {
        List<RuleHypothesis> functions = HypothesisSpace.allBooleanFunctionsOfTwoVariables();

        assertThat(functions).hasSize(FUNCTION_COUNT);
        assertThat(functions).extracting(RuleHypothesis::id).doesNotHaveDuplicates();
        assertThat(functions).extracting(RuleHypothesis::truthTable).doesNotHaveDuplicates();
        assertThat(functions).allSatisfy(hypothesis -> {
            assertThat(hypothesis.statement()).isNotBlank();
            assertThat(hypothesis.variables()).containsExactlyInAnyOrder("power", "switch");
            assertThat(hypothesis.truthTable()).hasSize(4);
        });
    }

    @Test
    @DisplayName("the conjunction the lamp world obeys is in the space, and it is the only "
            + "function that is true exactly at power=1,switch=1")
    void theConjunctionIsInTheSpace() {
        RuleHypothesis conjunction = HypothesisSpace.allBooleanFunctionsOfTwoVariables().stream()
                .filter(hypothesis -> hypothesis.id().equals("h-0001")).findFirst().orElseThrow();

        assertThat(conjunction.evaluate(WorldInput.of(true, true))).isTrue();
        assertThat(conjunction.evaluate(WorldInput.of(true, false))).isFalse();
        assertThat(conjunction.evaluate(WorldInput.of(false, true))).isFalse();
        assertThat(conjunction.evaluate(WorldInput.of(false, false))).isFalse();
        assertThat(conjunction.statement()).contains("power=1,switch=1");
    }

    @Test
    @DisplayName("evaluate agrees with the truth table for every function and every assignment")
    void evaluateAgreesWithTheTruthTableEverywhere() {
        HypothesisSpace space = space();

        for (RuleHypothesis hypothesis : space.candidates()) {
            for (WorldInput assignment : space.allAssignments()) {
                String key = hypothesis.keyFor(assignment);
                assertThat(hypothesis.truthTable()).containsKey(key);
                assertThat(hypothesis.evaluate(assignment))
                        .as("evaluate must agree with the truth table at " + key)
                        .isEqualTo(hypothesis.truthTable().get(key));
            }
        }
    }

    @Test
    @DisplayName("the key of an assignment is the format the kernel computes")
    void theKeyIsTheFormatTheKernelComputes() {
        HypothesisSpace space = space();
        RuleHypothesis hypothesis = space.candidates().get(0);

        for (WorldInput assignment : space.allAssignments()) {
            assertThat(hypothesis.keyFor(assignment)).isEqualTo(MicroWorldCodec.describe(assignment));
        }
        assertThat(hypothesis.keyFor(WorldInput.of(false, true))).isEqualTo("power=0,switch=1");
        assertThat(hypothesis.keyFor(WorldInput.of(true, false))).isEqualTo("power=1,switch=0");
    }

    @Test
    @DisplayName("a hypothesis that has no entry for an assignment refuses to answer rather than "
            + "answering false")
    void aHypothesisWithoutAnEntryRefusesToAnswer() {
        RuleHypothesis partial = new RuleHypothesis("h-partial", "a partial function",
                Map.of("power=1,switch=1", true), 0, 0);

        assertThat(partial.valueAt(WorldInput.of(true, false))).isEmpty();
        assertThatThrownBy(() -> partial.evaluate(WorldInput.of(true, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no truth-table entry");
    }

    @Test
    @DisplayName("consistentWith is exact: every candidate is checked against EVERY observation")
    void consistentWithIsExact() {
        HypothesisSpace space = space();
        LampEnvironment environment = new LampEnvironment();
        Observation first = environment.probe(WorldInput.of(false, true));
        Observation second = environment.probe(WorldInput.of(true, true));

        List<RuleHypothesis> consistent = space.consistentWith(List.of(first, second));

        assertThat(consistent).extracting(RuleHypothesis::id)
                .containsExactlyInAnyOrder("h-0001", "h-0011", "h-1001", "h-1011");
        assertThat(consistent).allSatisfy(hypothesis -> {
            assertThat(hypothesis.supportingCases()).isEqualTo(2);
            assertThat(hypothesis.contradictingCases()).isZero();
        });

        // A third observation removes the two candidates that contradict it.
        Observation third = environment.probe(WorldInput.of(true, false));
        assertThat(space.consistentWith(List.of(first, second, third)))
                .extracting(RuleHypothesis::id)
                .containsExactlyInAnyOrder("h-0001", "h-1001");
    }

    @Test
    @DisplayName("the information gain is zero when the candidates agree and maximal when they "
            + "split evenly")
    void theInformationGainIsComputed() {
        HypothesisSpace space = space();
        List<RuleHypothesis> agreeing = List.of(space.candidates().get(0));
        List<RuleHypothesis> split = List.of(space.candidates().get(0), space.candidates().get(1));

        assertThat(HypothesisSpace.informationGain(WorldInput.of(true, true), agreeing)).isZero();
        assertThat(HypothesisSpace.informationGain(WorldInput.of(true, true), split)).isEqualTo(0.5);
        assertThat(HypothesisSpace.informationGain(WorldInput.of(true, true), List.of())).isZero();
        assertThat(HypothesisSpace.informationGain(null, split)).isZero();
    }

    @Test
    @DisplayName("the information gain of a balanced split is the same for every assignment")
    void theInformationGainIsSymmetric() {
        HypothesisSpace space = space();
        // The never-true function and the always-true one differ at EVERY assignment, so every
        // assignment splits them evenly.
        List<RuleHypothesis> split = space.candidates().stream()
                .filter(hypothesis -> hypothesis.id().equals("h-0000")
                        || hypothesis.id().equals("h-1111"))
                .toList();

        for (WorldInput assignment : space.allAssignments()) {
            assertThat(HypothesisSpace.informationGain(assignment, split)).isEqualTo(0.5);
        }
    }

    @Test
    @DisplayName("the information gain is zero where the candidates agree and positive only "
            + "where they differ")
    void theInformationGainIsZeroWhereTheyAgree() {
        HypothesisSpace space = space();
        // The conjunction and its negation differ at exactly one assignment.
        List<RuleHypothesis> split = space.candidates().stream()
                .filter(hypothesis -> hypothesis.id().equals("h-0001")
                        || hypothesis.id().equals("h-1001"))
                .toList();

        assertThat(HypothesisSpace.informationGain(WorldInput.of(false, false), split)).isEqualTo(0.5);
        assertThat(HypothesisSpace.informationGain(WorldInput.of(false, true), split)).isZero();
        assertThat(HypothesisSpace.informationGain(WorldInput.of(true, false), split)).isZero();
        assertThat(HypothesisSpace.informationGain(WorldInput.of(true, true), split)).isZero();
    }

    @Test
    @DisplayName("the unanimous value is empty exactly when the candidates disagree")
    void theUnanimousValueIsEmptyWhenTheyDisagree() {
        HypothesisSpace space = space();
        List<RuleHypothesis> agreeing = List.of(space.candidates().get(0));
        List<RuleHypothesis> split = List.of(space.candidates().get(0), space.candidates().get(1));

        assertThat(HypothesisSpace.unanimousValueAt(WorldInput.of(true, true), agreeing)).isPresent();
        assertThat(HypothesisSpace.unanimousValueAt(WorldInput.of(true, true), split)).isEmpty();
        assertThat(HypothesisSpace.unanimousValueAt(WorldInput.of(true, true), List.of())).isEmpty();
    }

    @Test
    @DisplayName("forAssignment returns a candidate that has an opinion, and the space does not "
            + "resolve an ambiguity by itself")
    void forAssignmentReturnsACandidateWithoutResolvingAmbiguity() {
        HypothesisSpace space = space();

        Optional<RuleHypothesis> found = space.forAssignment(WorldInput.of(true, true));

        assertThat(found).isPresent();
        assertThat(found.get().valueAt(WorldInput.of(true, true))).isPresent();
        assertThat(space.forAssignment(new WorldInput(Map.of("flux", true)))).isEmpty();
    }

    @Test
    @DisplayName("the space enumerates the four assignments in canonical key order")
    void theSpaceEnumeratesItsAssignments() {
        List<WorldInput> assignments = space().allAssignments();

        assertThat(assignments).extracting(WorldInput::key).containsExactly(
                "power=0,switch=0", "power=0,switch=1", "power=1,switch=0", "power=1,switch=1");
    }

    @Test
    @DisplayName("the counts are the evidence a hypothesis has been measured against")
    void theCountsAreTheEvidence() {
        HypothesisSpace space = space();
        LampEnvironment environment = new LampEnvironment();
        Observation observation = environment.probe(WorldInput.of(false, true));

        List<RuleHypothesis> consistent = space.consistentWith(List.of(observation));

        assertThat(consistent).isNotEmpty();
        assertThat(consistent).allSatisfy(hypothesis -> {
            assertThat(hypothesis.supportingCases()).isEqualTo(1);
            assertThat(hypothesis.contradictingCases()).isZero();
        });
        assertThat(HypothesisSpace.supportingCount(consistent.get(0), List.of(observation)))
                .isEqualTo(1);
        assertThat(HypothesisSpace.contradictingCount(consistent.get(0), List.of(observation)))
                .isZero();
        assertThat(HypothesisSpace.firstContradiction(consistent.get(0), List.of(observation)))
                .isEmpty();
    }

    @Test
    @DisplayName("the space refuses to be built without variables or without candidates")
    void theSpaceIsChecked() {
        assertThatThrownBy(() -> new HypothesisSpace(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HypothesisSpace(List.of("power"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the candidates of a space are in identifier order, whatever order they arrive in")
    void theCandidatesAreOrdered() {
        List<RuleHypothesis> functions = new ArrayList<>(
                HypothesisSpace.allBooleanFunctionsOfTwoVariables());
        java.util.Collections.reverse(functions);

        HypothesisSpace space = new HypothesisSpace(List.of("power", "switch"), functions);

        List<String> ids = space.candidates().stream().map(RuleHypothesis::id).toList();
        List<String> sorted = new ArrayList<>(ids);
        java.util.Collections.sort(sorted);
        assertThat(ids).isEqualTo(sorted);
    }
}
