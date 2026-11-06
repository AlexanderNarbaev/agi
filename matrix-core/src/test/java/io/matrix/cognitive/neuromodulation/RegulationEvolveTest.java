package io.matrix.cognitive.neuromodulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.matrix.cognitive.DriveSignal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W44.3 — evolving the state and asking for the bias.
 *
 * <p>The kernel's pattern is:</p>
 *
 * <pre>
 *   RegulatoryState next = controller.evolve(current, events);
 *   RegulationBias bias  = controller.bias(next, events);
 * </pre>
 *
 * <p>What this asserts about the SYSTEM, and not about the code:</p>
 *
 * <ul>
 *   <li>the events are interpreted EXACTLY ONCE. {@code evolve} folds them into the levels and
 *       {@code bias} reads the levels, so following the pattern does not apply the same events
 *       twice — which is the failure a caller cannot see from the numbers;</li>
 *   <li>the events agree with the state on every knob they do not carry into it: the cost weight,
 *       the evidence threshold and the task priority are functions of the state alone;</li>
 *   <li>the reward is the one thing an event carries that is not a level, and it is applied once,
 *       bounded by the floor;</li>
 *   <li>evolving the same state with the same events produces the same state.</li>
 * </ul>
 */
class RegulationEvolveTest {

    private static final String OBSERVED_AT = "2026-11-02T00:00:00Z";
    private static final String LATER = "2026-11-02T00:05:00Z";

    private final RegulationController controller = new RegulationController();

    private static RegulatoryEvent loadSample() {
        return RegulatoryEvent.of(RegulatoryEvent.Kind.LOAD_SAMPLE, 1.0, "monitor");
    }

    @Test
    @DisplayName("evolving folds the events into the state")
    void evolvingFoldsTheEventsIntoTheState() {
        List<RegulatoryEvent> events = List.of(loadSample(), loadSample(), loadSample());

        RegulatoryState next = controller.evolve(RegulatoryState.neutral(), events, 0, OBSERVED_AT);

        assertThat(next.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isCloseTo(3 * DriveDynamics.RATE_MEDIUM, within(1e-9));
    }

    @Test
    @DisplayName("the kernel's pattern applies the events once, not twice")
    void theKernelPatternAppliesTheEventsOnce() {
        // Six samples of load: one application puts the load at 0.6, which is above the cost
        // threshold and moves the cost weight to 1.1. Two applications would put it at 1.2,
        // which the drive would clamp to 1.0 and the cost weight to 1.5. The two are different
        // numbers, so the test can tell them apart.
        List<RegulatoryEvent> events = List.of(loadSample(), loadSample(), loadSample(),
                loadSample(), loadSample(), loadSample());

        RegulatoryState next = controller.evolve(RegulatoryState.neutral(), events, 0, OBSERVED_AT);
        RegulationBias bias = controller.bias(next, events);

        assertThat(next.level(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isCloseTo(6 * DriveDynamics.RATE_MEDIUM, within(1e-9));
        assertThat(bias.costWeightMultiplier())
                .as("the events were applied once, by evolve")
                .isCloseTo(RegulationBias.NEUTRAL_MULTIPLIER
                        + (6 * DriveDynamics.RATE_MEDIUM - RegulationController.LOAD_COST_THRESHOLD)
                        * RegulationController.LOAD_COST_GAIN,
                        within(1e-9));
    }

    @Test
    @DisplayName("the state and the events agree on every knob the events do not carry into the state")
    void theStateAndTheEventsAgreeOnEveryKnobTheyDoNotCarryIntoTheState() {
        List<RegulatoryEvent> events = List.of(
                loadSample(),
                RegulatoryEvent.of(RegulatoryEvent.Kind.NOVEL_INPUT, 1.0, "perception"),
                RegulatoryEvent.of(RegulatoryEvent.Kind.PREDICTION_ERROR, 1.0, "evaluator"));

        RegulatoryState next = controller.evolve(RegulatoryState.neutral(), events, 0, OBSERVED_AT);
        RegulationBias withEvents = controller.bias(next, events);
        RegulationBias stateOnly = controller.bias(next);

        assertThat(withEvents.costWeightMultiplier())
                .isEqualTo(stateOnly.costWeightMultiplier());
        assertThat(withEvents.evidenceRequestThreshold())
                .isEqualTo(stateOnly.evidenceRequestThreshold());
        assertThat(withEvents.taskPriorityMultiplier())
                .isEqualTo(stateOnly.taskPriorityMultiplier());
        assertThat(withEvents.admitNonCriticalTask())
                .as("the events carried no reward, so the demand is not relieved either")
                .isEqualTo(stateOnly.admitNonCriticalTask());
    }

    @Test
    @DisplayName("the reward is applied once, and never takes the demand below the floor")
    void theRewardIsAppliedOnceAndNeverTakesTheDemandBelowTheFloor() {
        RegulatoryState current = new RegulatoryState(Map.of(
                DriveSignal.Dimension.CONSOLIDATION_DEMAND, new DriveSignal(
                        DriveSignal.Dimension.CONSOLIDATION_DEMAND, 1.0, "test",
                        DriveSignal.Effect.TASK_ADMISSION, OBSERVED_AT)));
        List<RegulatoryEvent> rewarded = List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 0.1, "environment"));

        RegulatoryState next = controller.evolve(current, rewarded, 0, OBSERVED_AT);
        RegulationBias bias = controller.bias(next, rewarded);

        assertThat(controller.effectiveConsolidationDemand(next, rewarded))
                .as("the reward relieved the demand by exactly one relief")
                .isCloseTo(1.0 - 0.1 * RegulationController.MAX_REWARD_CONSOLIDATION_RELIEF, within(1e-9));
        assertThat(controller.effectiveConsolidationDemand(next, rewarded))
                .as("but not below the floor")
                .isGreaterThanOrEqualTo(RegulationController.CONSOLIDATION_DEMAND_FLOOR);
        assertThat(bias.admitNonCriticalTask())
                .as("and the work still waits")
                .isFalse();
    }

    @Test
    @DisplayName("evolving the same state with the same events produces the same state")
    void evolvingTheSameStateWithTheSameEventsProducesTheSameState() {
        List<RegulatoryEvent> events = List.of(loadSample(),
                RegulatoryEvent.of(RegulatoryEvent.Kind.TASK_FAILED, 1.0, "runner"));

        RegulatoryState first = controller.evolve(RegulatoryState.neutral(), events, OBSERVED_AT);
        RegulatoryState second = controller.evolve(RegulatoryState.neutral(), events, OBSERVED_AT);

        assertThat(second).isEqualTo(first);
        assertThat(controller.bias(second, events)).isEqualTo(controller.bias(first, events));
    }

    @Test
    @DisplayName("evolving remembers the timestamp the state carries, and accepts the caller's")
    void evolvingRemembersTheTimestampTheStateCarries() {
        RegulatoryState stamped = new RegulatoryState(Map.of(
                DriveSignal.Dimension.SUSTAINED_LOAD, new DriveSignal(
                        DriveSignal.Dimension.SUSTAINED_LOAD, 0.5, "test",
                        RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD),
                        OBSERVED_AT)));

        RegulatoryState remembered = controller.evolve(stamped, List.of(loadSample()));
        assertThat(remembered.drive(DriveSignal.Dimension.SUSTAINED_LOAD))
                .hasValueSatisfying(drive -> assertThat(drive.updatedAt()).isEqualTo(OBSERVED_AT));

        RegulatoryState explicit = controller.evolve(stamped, List.of(loadSample()), LATER);
        assertThat(explicit.drive(DriveSignal.Dimension.SUSTAINED_LOAD))
                .hasValueSatisfying(drive -> assertThat(drive.updatedAt()).isEqualTo(LATER));
    }

    @Test
    @DisplayName("a controller can be built with the dynamics it is to fold events with")
    void aControllerCanBeBuiltWithItsDynamics() {
        DriveDynamics dynamics = DriveDynamics.standard();
        RegulationController custom = new RegulationController(dynamics);

        assertThat(custom.dynamics()).isSameAs(dynamics);
        assertThat(custom.evolve(RegulatoryState.neutral(), List.of(loadSample()), 0, OBSERVED_AT))
                .isEqualTo(controller.evolve(RegulatoryState.neutral(), List.of(loadSample()), 0, OBSERVED_AT));
    }
}
