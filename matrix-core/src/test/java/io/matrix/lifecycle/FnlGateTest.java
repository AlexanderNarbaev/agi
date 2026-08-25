package io.matrix.lifecycle;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link FnlGate} (DESIGN-12 §2).
 */
class FnlGateTest {

    @Test
    void fullPromotionPathRequiresTwoPassingScores() {
        FnlGate gate = new FnlGate();
        gate.admit("e1");
        assertThat(gate.state("e1")).contains(FnlGate.GateState.SHADOW);

        assertThat(gate.advance("e1", 0.9, 0.8))
                .isEqualTo(FnlGate.GateState.CANDIDATE);
        assertThat(gate.advance("e1", 0.85, 0.8))
                .isEqualTo(FnlGate.GateState.PROMOTED);
    }

    @Test
    void failingScoreDemotesFromAnyActiveStage() {
        FnlGate gate = new FnlGate();
        gate.admit("e2");
        assertThat(gate.advance("e2", 0.5, 0.8)).isEqualTo(FnlGate.GateState.DEMOTED);

        FnlGate gate3 = new FnlGate();
        gate3.admit("e3");
        gate3.advance("e3", 0.9, 0.8);
        assertThat(gate3.advance("e3", 0.1, 0.8)).isEqualTo(FnlGate.GateState.DEMOTED);
    }

    @Test
    void unadmittedElementRejectedAndTerminalsAreStable() {
        FnlGate gate = new FnlGate();
        assertThatThrownBy(() -> gate.advance("ghost", 1.0, 0.5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not admitted");

        gate.admit("p");
        gate.advance("p", 1.0, 0.5);
        gate.advance("p", 1.0, 0.5); // PROMOTED
        assertThat(gate.advance("p", 0.0, 0.5)).isEqualTo(FnlGate.GateState.PROMOTED);

        gate.admit("d");
        gate.advance("d", 0.0, 0.5); // DEMOTED
        assertThat(gate.advance("d", 1.0, 0.5)).isEqualTo(FnlGate.GateState.DEMOTED);
    }

    // --- Property: promotion strictly requires meeting every threshold ---

    @Provide
    Arbitrary<List<Double>> scorePairs() {
        return Arbitraries.doubles().between(0.0, 1.0).list().ofSize(2);
    }

    @Property
    void promotedOnlyWhenBothScoresMeetThresholds(
            @ForAll("scorePairs") List<Double> scores,
            @ForAll double threshold) {
        double t = Math.abs(threshold) % 1.0;
        FnlGate gate = new FnlGate();
        gate.admit("x");
        var s1 = gate.advance("x", scores.get(0), t);
        if (scores.get(0) >= t) {
            assertThat(s1).isEqualTo(FnlGate.GateState.CANDIDATE);
            var s2 = gate.advance("x", scores.get(1), t);
            if (scores.get(1) >= t) {
                assertThat(s2).isEqualTo(FnlGate.GateState.PROMOTED);
            } else {
                assertThat(s2).isEqualTo(FnlGate.GateState.DEMOTED);
            }
        } else {
            assertThat(s1).isEqualTo(FnlGate.GateState.DEMOTED);
        }
    }
}
