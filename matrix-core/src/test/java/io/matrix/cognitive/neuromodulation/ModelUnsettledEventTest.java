package io.matrix.cognitive.neuromodulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.matrix.cognitive.DriveSignal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W44.3 — an undecided model is its own event, and it moves one drive.
 *
 * <p>What this asserts about the SYSTEM, and not about the code:</p>
 *
 * <ul>
 *   <li>the model's hypotheses disagreeing raises the UNCERTAINTY drive, and it raises NOTHING
 *       else — not novelty, not the load, not vigilance, not the outstanding error;</li>
 *   <li>an undecided model is not a task failure, so a batch of it does not make the system
 *       think it has failed;</li>
 *   <li>an undecided model is not a reward, so a batch of it does not relieve the consolidation
 *       demand;</li>
 *   <li>every other dimension keeps the value it had, which is the stronger form of the claim:
 *       an event that changes one thing and no other thing is auditable.</li>
 * </ul>
 */
class ModelUnsettledEventTest {

    private static final String OBSERVED_AT = "2026-11-02T00:00:00Z";

    private final DriveDynamics dynamics = DriveDynamics.standard();
    private final RegulationController controller = new RegulationController();

    /** A state in which every drive is already at a distinct, non-zero level. */
    private static RegulatoryState loadedState() {
        Map<DriveSignal.Dimension, DriveSignal> drives = new EnumMap<>(DriveSignal.Dimension.class);
        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            drives.put(dimension, new DriveSignal(dimension, 0.4, "test",
                    RegulationController.effectFor(dimension), OBSERVED_AT));
        }
        return new RegulatoryState(drives);
    }

    @Test
    @DisplayName("a batch containing only an undecided model raises uncertainty and nothing else")
    void aBatchOfOnlyAnUndecidedModelRaisesUncertaintyAndNothingElse() {
        RegulatoryState before = loadedState();
        RegulatoryEvent unsettled = RegulatoryEvent.of(
                RegulatoryEvent.Kind.MODEL_UNSETTLED, 1.0, "kernel");

        // Zero decay steps, so that the only thing that can move a level is the event itself.
        // Otherwise the assertion below would be about the decay as well as about the event.
        RegulatoryState after = dynamics.advance(before, List.of(unsettled), 0, OBSERVED_AT);

        assertThat(after.level(DriveSignal.Dimension.UNCERTAINTY))
                .as("the undecided model is the uncertainty")
                .isCloseTo(0.4 + DriveDynamics.RATE_MEDIUM, within(1e-9));

        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            if (dimension == DriveSignal.Dimension.UNCERTAINTY) {
                continue;
            }
            assertThat(after.level(dimension))
                    .as("every other dimension keeps the value it had: %s", dimension)
                    .isCloseTo(before.level(dimension), within(1e-9));
        }
    }

    @Test
    @DisplayName("an undecided model is not a task failure")
    void anUndecidedModelIsNotATaskFailure() {
        RegulatoryState before = loadedState();
        RegulatoryState after = dynamics.advance(before, List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.MODEL_UNSETTLED, 1.0, "kernel")), 0, OBSERVED_AT);

        assertThat(after.level(DriveSignal.Dimension.REWARD_PREDICTION_ERROR))
                .as("nothing was attempted, so nothing failed")
                .isCloseTo(before.level(DriveSignal.Dimension.REWARD_PREDICTION_ERROR), within(1e-9));
        assertThat(after.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .as("an undecided model is not operational pressure")
                .isCloseTo(before.level(DriveSignal.Dimension.SUSTAINED_LOAD), within(1e-9));
        assertThat(after.level(DriveSignal.Dimension.NOVELTY))
                .as("the input may be perfectly familiar; the MODEL is what is undecided")
                .isCloseTo(before.level(DriveSignal.Dimension.NOVELTY), within(1e-9));
    }

    @Test
    @DisplayName("an undecided model is not a reward")
    void anUndecidedModelIsNotAReward() {
        RegulatoryState before = new RegulatoryState(Map.of(
                DriveSignal.Dimension.CONSOLIDATION_DEMAND, new DriveSignal(
                        DriveSignal.Dimension.CONSOLIDATION_DEMAND, 1.0, "test",
                        DriveSignal.Effect.TASK_ADMISSION, OBSERVED_AT)));
        List<RegulatoryEvent> unsettled = List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.MODEL_UNSETTLED, 1.0, "kernel"));

        assertThat(RegulationController.rewardSignal(unsettled))
                .as("there was no outcome, so there is nothing to have been rewarded with")
                .isEqualTo(0.0);

        RegulatoryState after = dynamics.advance(before, unsettled, 0, OBSERVED_AT);
        assertThat(controller.effectiveConsolidationDemand(after, unsettled))
                .as("and the consolidation demand is not relieved")
                .isCloseTo(1.0, within(1e-9));
    }
}
