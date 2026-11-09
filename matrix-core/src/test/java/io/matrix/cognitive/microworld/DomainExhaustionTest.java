package io.matrix.cognitive.microworld;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — the exhaustion of the micro-world's input domain, PROVED.
 *
 * <p><b>What was asserted and what is proved.</b> The W45 finding said that the lamp world's
 * input domain is exhausted once the model settles, and every refusal since then rested on
 * that sentence. A refusal that rests on an assertion is a refusal a reader has to trust.
 * This test turns the sentence into a count.</p>
 *
 * <p><b>The argument.</b> The hypothesis space is the set of ALL functions of the
 * environment's inputs. With V inputs there are 2^(2^V) such functions — sixteen for the
 * lamp world's two variables. An observation of a distinct assignment eliminates exactly the
 * half of the candidates that disagree with it, so after k distinct assignments the number
 * of survivors is 2^(2^V − k), and the model settles only when k = 2^V: <b>every assignment
 * the domain has</b>. A model that has settled has therefore seen the whole domain, and
 * there is nothing left to observe that the model was not built from. The set the promotion
 * rule asks for — the observations the model was NOT built from — is empty, and it is empty
 * because of the arithmetic of the space, not because the lamp is unlucky.</p>
 *
 * <p><b>Why the count matters to the reader.</b> "The independent validation set is empty"
 * is a fact about one run; "the model cannot settle before it has seen every assignment, and
 * the domain has no more" is a fact about the design. The second one is what the wave's
 * report may assert, and this test is where the assertion is checked.</p>
 */
class DomainExhaustionTest {

    /**
     * The assignments the lamp world has, in the order the test walks them.
     *
     * @return the assignments
     */
    private static List<WorldInput> everyAssignment() {
        List<WorldInput> assignments = new ArrayList<>();
        for (boolean power : new boolean[] {false, true}) {
            for (boolean toggle : new boolean[] {false, true}) {
                assignments.add(WorldInput.of(power, toggle));
            }
        }
        return assignments;
    }

    @Test
    @DisplayName("the space holds every function of the inputs, and each distinct assignment "
            + "halves it")
    void eachDistinctAssignmentHalvesTheSpace() {
        MicroWorldEnvironment environment = new LampEnvironment();
        HypothesisSpace space = HypothesisSpace.unbiased();

        // 2^(2^V) with V = 2: sixteen Boolean functions over two variables.
        assertThat(space.variables()).containsExactlyInAnyOrder("power", "switch");
        assertThat(space.candidates()).hasSize(16);
        // The domain has 2^V assignments: four for the lamp world's two variables.
        assertThat(everyAssignment()).hasSize(4);

        MicroWorldSession session = new MicroWorldSession("ep-exhaustion", environment, space);
        assertThat(session.survivingHypotheses()).hasSize(16);

        int index = 0;
        for (WorldInput assignment : everyAssignment()) {
            Observation observation = environment.probe(assignment);
            session.observe(observation);
            index++;
            // The survivors are 2^(2^V − k): 16, 8, 4, 2, 1.
            assertThat(session.survivingHypotheses())
                    .withFailMessage("after " + index + " distinct assignment(s) the space must "
                            + "hold 2^(4−" + index + ") candidates")
                    .hasSize(16 >> index);
        }
    }

    @Test
    @DisplayName("the model settles exactly when the domain is exhausted, so no observation "
            + "can be both independent of the model and available")
    void theModelSettlesWhenTheDomainIsExhausted() {
        MicroWorldEnvironment environment = new LampEnvironment();
        MicroWorldSession session = new MicroWorldSession("ep-exhaustion", environment);

        // Every assignment the model was built from, and the validation set beside it: the
        // observations the model was NOT built from. The promotion rule asks for the second.
        Set<String> applied = new LinkedHashSet<>();
        List<Observation> remembered = new ArrayList<>();
        for (WorldInput assignment : everyAssignment()) {
            Observation observation = environment.probe(assignment);
            remembered.add(observation);
            session.observe(observation);
            for (Observation appliedObservation : session.appliedObservations()) {
                applied.add(appliedObservation.id());
            }
            List<Observation> independent = remembered.stream()
                    .filter(candidate -> !applied.contains(candidate.id()))
                    .toList();
            assertThat(independent)
                    .withFailMessage("before the model settles there is nothing to validate "
                            + "against either; the observation set and the applied set are "
                            + "the same")
                    .isEmpty();
        }

        // The model has settled, and it settled on the last assignment of the domain.
        assertThat(session.survivingHypotheses()).hasSize(1);
        assertThat(session.appliedObservations()).hasSize(everyAssignment().size());
        assertThat(remembered).hasSize(everyAssignment().size());

        // The validation set — the observations the model was NOT built from — is empty,
        // and it stays empty: there is no assignment left to observe.
        assertThat(remembered.stream().filter(o -> !applied.contains(o.id())).toList())
                .withFailMessage("the domain is exhausted; the validation set can never hold "
                        + "an observation")
                .isEmpty();
    }
}
