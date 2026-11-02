package io.matrix.cognitive.neuromodulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W44.3 — the shape of a regulatory event.
 *
 * <p>What this asserts about the SYSTEM, and not about the code:</p>
 *
 * <ul>
 *   <li>an event says WHAT happened, HOW MUCH, and WHO reported it — and nothing else;</li>
 *   <li>an event carries no timestamp and no step count, because the caller supplies the step to
 *       the dynamics and a decision path that reads a clock cannot be replayed;</li>
 *   <li>the magnitude is a share of a unit event, bounded to [0,1], so that one event of large
 *       magnitude cannot dominate every drive at once;</li>
 *   <li>an event with no measured size says "unit", and does not pretend to have measured
 *       anything.</li>
 * </ul>
 */
class RegulatoryEventTest {

    @Test
    @DisplayName("an event carries what happened, how much, and who reported it")
    void anEventCarriesWhatHappenedHowMuchAndWhoReportedIt() {
        RegulatoryEvent event = RegulatoryEvent.of(
                RegulatoryEvent.Kind.PREDICTION_ERROR, 0.25, "evaluator");

        assertThat(event.kind()).isEqualTo(RegulatoryEvent.Kind.PREDICTION_ERROR);
        assertThat(event.magnitude()).isEqualTo(0.25);
        assertThat(event.source()).isEqualTo("evaluator");
    }

    @Test
    @DisplayName("an event of unmeasured size says unit and claims nothing more")
    void anEventOfUnmeasuredSizeSaysUnit() {
        RegulatoryEvent event = RegulatoryEvent.of(
                RegulatoryEvent.Kind.NOVEL_INPUT, "perception");

        assertThat(event.magnitude()).isEqualTo(RegulatoryEvent.UNIT_MAGNITUDE);
    }

    @Test
    @DisplayName("an event with no kind is refused: a nameless event has no production")
    void anEventWithNoKindIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegulatoryEvent(null, 0.5, "source"))
                .withMessageContaining("kind");
    }

    @Test
    @DisplayName("a magnitude outside [0,1] is refused")
    void aMagnitudeOutsideTheUnitIntervalIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegulatoryEvent(
                        RegulatoryEvent.Kind.LOAD_SAMPLE, 1.5, "source"))
                .withMessageContaining("magnitude");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegulatoryEvent(
                        RegulatoryEvent.Kind.LOAD_SAMPLE, -0.1, "source"))
                .withMessageContaining("magnitude");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegulatoryEvent(
                        RegulatoryEvent.Kind.LOAD_SAMPLE, Double.NaN, "source"))
                .withMessageContaining("magnitude");
    }

    @Test
    @DisplayName("an event with no source is refused: an unattributed event cannot be audited")
    void anEventWithNoSourceIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegulatoryEvent(
                        RegulatoryEvent.Kind.TASK_COMPLETED, 0.5, " "))
                .withMessageContaining("source");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegulatoryEvent(
                        RegulatoryEvent.Kind.TASK_COMPLETED, 0.5, null))
                .withMessageContaining("source");
    }

    @Test
    @DisplayName("the event kinds are the small set the dynamics knows, and no more")
    void theEventKindsAreTheSmallSetTheDynamicsKnows() {
        assertThat(RegulatoryEvent.Kind.values())
                .containsExactly(
                        RegulatoryEvent.Kind.TASK_COMPLETED,
                        RegulatoryEvent.Kind.TASK_FAILED,
                        RegulatoryEvent.Kind.PREDICTION_ERROR,
                        RegulatoryEvent.Kind.NOVEL_INPUT,
                        RegulatoryEvent.Kind.LOAD_SAMPLE,
                        RegulatoryEvent.Kind.REWARD,
                        RegulatoryEvent.Kind.MODEL_UNSETTLED);
    }
}
