package io.matrix.cognitive.neuromodulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import io.matrix.cognitive.DriveSignal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W44.3 — the dynamics of the drives.
 *
 * <p>What this asserts about the SYSTEM, and not about the code:</p>
 *
 * <ul>
 *   <li>an event moves the drives it feeds, and only those;</li>
 *   <li>pressure that no event maintains fades, at a rate that is stated per dimension rather
 *       than being one number for all of them;</li>
 *   <li>a drive that has never been observed is ABSENT rather than zero, because a drive that
 *       was measured to be zero and a drive that was never measured are different facts;</li>
 *   <li>the produced drive carries the effect the controller expects, so the permission to act
 *       travels with the signal;</li>
 *   <li>the same events in the same order produce the same state, and the caller supplies the
 *       step, so nothing here reads a clock.</li>
 * </ul>
 */
class DriveDynamicsTest {

    private static final String OBSERVED_AT = "2026-11-02T00:00:00Z";

    private final DriveDynamics dynamics = DriveDynamics.standard();

    private RegulatoryState advance(RegulatoryState current, List<RegulatoryEvent> events, int steps) {
        return dynamics.advance(current, events, steps, OBSERVED_AT);
    }

    private static RegulatoryEvent event(RegulatoryEvent.Kind kind) {
        return RegulatoryEvent.of(kind, "test");
    }

    @Test
    @DisplayName("every dimension has a profile: a dimension with no profile has no specification")
    void everyDimensionHasAProfile() {
        assertThat(dynamics.profiles().keySet())
                .containsExactlyInAnyOrderElementsOf(List.of(DriveSignal.Dimension.values()));
    }

    @Test
    @DisplayName("a load sample raises the load, and nothing else that was not also fed")
    void aLoadSampleRaisesTheLoad() {
        RegulatoryState state = advance(RegulatoryState.neutral(),
                List.of(event(RegulatoryEvent.Kind.LOAD_SAMPLE)), 1);

        assertThat(state.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isCloseTo(DriveDynamics.RATE_MEDIUM, within(1e-9));
        assertThat(state.drive(DriveSignal.Dimension.SUSTAINED_LOAD))
                .hasValueSatisfying(drive -> assertThat(drive.source()).isEqualTo("test"));
    }

    @Test
    @DisplayName("a failed task adds to the outstanding error and to the load")
    void aFailedTaskAddsToTheErrorAndTheLoad() {
        RegulatoryState state = advance(RegulatoryState.neutral(),
                List.of(event(RegulatoryEvent.Kind.TASK_FAILED)), 1);

        assertThat(state.level(DriveSignal.Dimension.REWARD_PREDICTION_ERROR))
                .isCloseTo(DriveDynamics.RATE_DOMINANT, within(1e-9));
        assertThat(state.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isCloseTo(DriveDynamics.RATE_LOW, within(1e-9));
        assertThat(state.level(DriveSignal.Dimension.VIGILANCE))
                .isCloseTo(DriveDynamics.RATE_MEDIUM, within(1e-9));
    }

    @Test
    @DisplayName("a completed task relieves the load and the outstanding error")
    void aCompletedTaskRelievesTheLoadAndTheError() {
        RegulatoryState loaded = new RegulatoryState(Map.of(
                DriveSignal.Dimension.SUSTAINED_LOAD, new DriveSignal(
                        DriveSignal.Dimension.SUSTAINED_LOAD, 0.5, "test",
                        RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD),
                        OBSERVED_AT),
                DriveSignal.Dimension.REWARD_PREDICTION_ERROR, new DriveSignal(
                        DriveSignal.Dimension.REWARD_PREDICTION_ERROR, 0.5, "test",
                        RegulationController.effectFor(DriveSignal.Dimension.REWARD_PREDICTION_ERROR),
                        OBSERVED_AT)));

        RegulatoryState state = advance(loaded, List.of(event(RegulatoryEvent.Kind.TASK_COMPLETED)), 1);

        assertThat(state.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isCloseTo(0.5 * (1.0 - DriveDynamics.DECAY_LOAD) + DriveDynamics.RATE_RELIEF, within(1e-9));
        assertThat(state.level(DriveSignal.Dimension.REWARD_PREDICTION_ERROR))
                .isCloseTo(0.5 * (1.0 - DriveDynamics.DECAY_ERROR) + DriveDynamics.RATE_STRONG_RELIEF,
                        within(1e-9));
    }

    @Test
    @DisplayName("pressure that no event maintains fades, by the decay rate of its dimension")
    void pressureThatNoEventMaintainsFades() {
        RegulatoryState loaded = new RegulatoryState(Map.of(
                DriveSignal.Dimension.SUSTAINED_LOAD, new DriveSignal(
                        DriveSignal.Dimension.SUSTAINED_LOAD, 0.5, "test",
                        RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD),
                        OBSERVED_AT)));

        RegulatoryState afterOneStep = advance(loaded, List.of(), 1);
        assertThat(afterOneStep.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isCloseTo(0.5 * (1.0 - DriveDynamics.DECAY_LOAD), within(1e-9));

        RegulatoryState afterTenSteps = advance(loaded, List.of(), 10);
        assertThat(afterTenSteps.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .as("ten steps shed more pressure than one")
                .isLessThan(afterOneStep.level(DriveSignal.Dimension.SUSTAINED_LOAD));
    }

    @Test
    @DisplayName("a drive that was never observed is absent, not zero")
    void aDriveThatWasNeverObservedIsAbsent() {
        RegulatoryState state = advance(RegulatoryState.neutral(), List.of(), 1);

        assertThat(state.held()).isEmpty();
        assertThat(state.level(DriveSignal.Dimension.SUSTAINED_LOAD)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a drive never leaves the interval its record enforces")
    void aDriveNeverLeavesTheIntervalItsRecordEnforces() {
        RegulatoryState saturated = new RegulatoryState(Map.of(
                DriveSignal.Dimension.SUSTAINED_LOAD, new DriveSignal(
                        DriveSignal.Dimension.SUSTAINED_LOAD, 1.0, "test",
                        RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD),
                        OBSERVED_AT)));

        RegulatoryState state = advance(saturated,
                List.of(event(RegulatoryEvent.Kind.LOAD_SAMPLE)), 0);

        assertThat(state.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isEqualTo(DriveDynamics.MAXIMUM_LEVEL);
    }

    @Test
    @DisplayName("the produced drive carries the effect the controller expects")
    void theProducedDriveCarriesTheExpectedEffect() {
        RegulatoryState state = advance(RegulatoryState.neutral(),
                List.of(event(RegulatoryEvent.Kind.LOAD_SAMPLE),
                        event(RegulatoryEvent.Kind.NOVEL_INPUT),
                        event(RegulatoryEvent.Kind.TASK_COMPLETED)), 1);

        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            DriveSignal drive = state.drive(dimension).orElse(null);
            if (drive == null) {
                continue;
            }
            assertThat(drive.effect())
                    .as("the permission travels with the signal")
                    .isEqualTo(RegulationController.effectFor(dimension));
        }
    }

    @Test
    @DisplayName("the same events in the same order produce the same state")
    void theSameEventsInTheSameOrderProduceTheSameState() {
        List<RegulatoryEvent> events = List.of(
                event(RegulatoryEvent.Kind.LOAD_SAMPLE),
                event(RegulatoryEvent.Kind.PREDICTION_ERROR),
                event(RegulatoryEvent.Kind.NOVEL_INPUT));

        RegulatoryState first = advance(RegulatoryState.neutral(), events, 2);
        RegulatoryState second = advance(RegulatoryState.neutral(), events, 2);

        assertThat(second).isEqualTo(first);
        assertThat(second.drives()).isEqualTo(first.drives());
    }

    @Test
    @DisplayName("the step count changes the state, because an interval of work is not an interval of time")
    void theStepCountChangesTheState() {
        List<RegulatoryEvent> events = List.of(event(RegulatoryEvent.Kind.LOAD_SAMPLE));
        // The state already carries pressure. A step count decays what is ALREADY there; the
        // events then add on top of it. Starting from zero there would be nothing to decay, and
        // the step count would be invisible — which is why the step count is a property of the
        // state being aged, not of the event being applied.
        RegulatoryState loaded = new RegulatoryState(Map.of(
                DriveSignal.Dimension.SUSTAINED_LOAD, new DriveSignal(
                        DriveSignal.Dimension.SUSTAINED_LOAD, 0.5, "test",
                        RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD),
                        OBSERVED_AT)));

        double afterOne = advance(loaded, events, 1)
                .level(DriveSignal.Dimension.SUSTAINED_LOAD);
        double afterFive = advance(loaded, events, 5)
                .level(DriveSignal.Dimension.SUSTAINED_LOAD);

        assertThat(afterFive).isLessThan(afterOne);
        assertThat(afterOne).isCloseTo(0.5 * (1.0 - DriveDynamics.DECAY_LOAD)
                + DriveDynamics.RATE_MEDIUM, within(1e-9));
    }

    @Test
    @DisplayName("the update leaves the state it was given alone")
    void theUpdateLeavesTheStateItWasGivenAlone() {
        RegulatoryState before = new RegulatoryState(Map.of(
                DriveSignal.Dimension.SUSTAINED_LOAD, new DriveSignal(
                        DriveSignal.Dimension.SUSTAINED_LOAD, 0.5, "test",
                        RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD),
                        OBSERVED_AT)));

        advance(before, List.of(event(RegulatoryEvent.Kind.LOAD_SAMPLE)), 1);

        assertThat(before.level(DriveSignal.Dimension.SUSTAINED_LOAD)).isEqualTo(0.5);
    }

    @Test
    @DisplayName("one step is the smallest useful interval, and it is what the convenience update applies")
    void oneStepIsTheSmallestUsefulInterval() {
        List<RegulatoryEvent> events = List.of(event(RegulatoryEvent.Kind.LOAD_SAMPLE));

        RegulatoryState oneStep = dynamics.advance(RegulatoryState.neutral(), events, OBSERVED_AT);

        assertThat(oneStep.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isCloseTo(DriveDynamics.RATE_MEDIUM, within(1e-9));
    }

    @Test
    @DisplayName("a negative step count and a missing timestamp are refused")
    void aNegativeStepCountAndAMissingTimestampAreRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> dynamics.advance(RegulatoryState.neutral(), List.of(), -1, OBSERVED_AT))
                .withMessageContaining("steps");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> dynamics.advance(RegulatoryState.neutral(), List.of(), 1, " "))
                .withMessageContaining("observedAt");
    }

    @Test
    @DisplayName("a profile with impossible bounds is refused")
    void aProfileWithImpossibleBoundsIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DriveDynamics.DriveProfile(
                        0.0, 0.1, 0.9, 0.2, Map.of()))
                .withMessageContaining("bounds");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DriveDynamics.DriveProfile(
                        0.0, 1.5, 0.0, 1.0, Map.of()))
                .withMessageContaining("decay");
    }
}
