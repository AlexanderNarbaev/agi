package io.matrix.cognitive.neuromodulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.matrix.cognitive.DriveSignal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W44.3 — what a regulatory state is allowed to do to a decision.
 *
 * <p>What this asserts about the SYSTEM, and not about the code:</p>
 *
 * <ul>
 *   <li>a system with no regulatory state behaves exactly as a system with neutral regulation;</li>
 *   <li>a drive that carries no effect moves nothing, whatever its level;</li>
 *   <li>sustained load makes the expensive method relatively more expensive and NEVER less;</li>
 *   <li>uncertainty makes the system ask for a discriminating test sooner, and never touches a
 *       confidence or a truth value;</li>
 *   <li>a large reward may relieve the consolidation demand but may never take it below the
 *       floor, so a reward-rich environment can slow consolidation down but cannot prevent it;</li>
 *   <li>a drive cannot move a knob by claiming an effect it was not given.</li>
 * </ul>
 */
class RegulationControllerTest {

    private static final String OBSERVED_AT = "2026-11-02T00:00:00Z";

    private final RegulationController controller = new RegulationController();

    private static RegulatoryState stateOf(DriveSignal.Dimension dimension,
                                           double level,
                                           DriveSignal.Effect effect) {
        return new RegulatoryState(Map.of(dimension, new DriveSignal(
                dimension, level, "test", effect, OBSERVED_AT)));
    }

    private static RegulatoryState stateOf(DriveSignal.Dimension dimension,
                                           double level,
                                           DriveSignal.Effect effect,
                                           DriveSignal.Dimension second,
                                           double secondLevel,
                                           DriveSignal.Effect secondEffect) {
        return new RegulatoryState(Map.of(
                dimension, new DriveSignal(dimension, level, "test", effect, OBSERVED_AT),
                second, new DriveSignal(second, secondLevel, "test", secondEffect, OBSERVED_AT)));
    }

    @Test
    @DisplayName("a system with no regulatory state behaves exactly as a neutral one")
    void aSystemWithNoRegulatoryStateIsNeutral() {
        assertThat(controller.bias(RegulatoryState.neutral()))
                .isEqualTo(RegulationBias.neutral());
        assertThat(controller.bias(RegulatoryState.neutral(), List.of()))
                .isEqualTo(RegulationBias.neutral());
    }

    @Test
    @DisplayName("a drive that carries no effect moves nothing, whatever its level")
    void aDriveThatCarriesNoEffectMovesNothing() {
        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            for (double level : List.of(0.0, 0.5, 1.0)) {
                RegulatoryState state = stateOf(dimension, level, DriveSignal.Effect.NONE);
                assertThat(controller.bias(state))
                        .as("dimension=%s level=%s effect=NONE", dimension, level)
                        .isEqualTo(RegulationBias.neutral());
            }
        }
    }

    @Test
    @DisplayName("sustained load makes the expensive method relatively more expensive, never less")
    void sustainedLoadRaisesTheCostWeight() {
        double below = controller.bias(stateOf(DriveSignal.Dimension.SUSTAINED_LOAD, 0.25,
                DriveSignal.Effect.ALGORITHM_COST_PREFERENCE)).costWeightMultiplier();
        double atThreshold = controller.bias(stateOf(DriveSignal.Dimension.SUSTAINED_LOAD,
                RegulationController.LOAD_COST_THRESHOLD,
                DriveSignal.Effect.ALGORITHM_COST_PREFERENCE)).costWeightMultiplier();
        double above = controller.bias(stateOf(DriveSignal.Dimension.SUSTAINED_LOAD, 1.0,
                DriveSignal.Effect.ALGORITHM_COST_PREFERENCE)).costWeightMultiplier();

        assertThat(below).isEqualTo(RegulationBias.NEUTRAL_MULTIPLIER);
        assertThat(atThreshold).isEqualTo(RegulationBias.NEUTRAL_MULTIPLIER);
        assertThat(above).isGreaterThan(RegulationBias.NEUTRAL_MULTIPLIER);
        assertThat(above).isCloseTo(
                RegulationBias.NEUTRAL_MULTIPLIER
                        + (1.0 - RegulationController.LOAD_COST_THRESHOLD)
                        * RegulationController.LOAD_COST_GAIN,
                within(1e-9));
    }

    @Test
    @DisplayName("the cost weight is never below the neutral value, at any load")
    void theCostWeightIsNeverBelowTheNeutralValue() {
        for (double level = 0.0; level <= 1.0; level += 0.05) {
            RegulationBias bias = controller.bias(stateOf(DriveSignal.Dimension.SUSTAINED_LOAD, level,
                    DriveSignal.Effect.ALGORITHM_COST_PREFERENCE));
            assertThat(bias.costWeightMultiplier())
                    .as("load=%s", level)
                    .isGreaterThanOrEqualTo(RegulationBias.NEUTRAL_MULTIPLIER);
        }
    }

    @Test
    @DisplayName("uncertainty lowers the evidence threshold and touches nothing else")
    void uncertaintyLowersTheEvidenceThreshold() {
        RegulationBias neutral = controller.bias(RegulatoryState.neutral());
        RegulationBias uncertain = controller.bias(stateOf(DriveSignal.Dimension.UNCERTAINTY, 1.0,
                DriveSignal.Effect.EVIDENCE_REQUEST));

        assertThat(uncertain.evidenceRequestThreshold())
                .isCloseTo(RegulationBias.NEVER_REQUEST_EVIDENCE
                        - RegulationController.UNCERTAINTY_EVIDENCE_GAIN, within(1e-9))
                .isLessThan(neutral.evidenceRequestThreshold());
        assertThat(uncertain.costWeightMultiplier()).isEqualTo(neutral.costWeightMultiplier());
        assertThat(uncertain.admitNonCriticalTask()).isEqualTo(neutral.admitNonCriticalTask());
        assertThat(uncertain.taskPriorityMultiplier()).isEqualTo(neutral.taskPriorityMultiplier());
    }

    @Test
    @DisplayName("the evidence threshold is never raised above the neutral value")
    void theEvidenceThresholdIsNeverRaised() {
        for (double level = 0.0; level <= 1.0; level += 0.05) {
            RegulationBias bias = controller.bias(stateOf(DriveSignal.Dimension.UNCERTAINTY, level,
                    DriveSignal.Effect.EVIDENCE_REQUEST));
            assertThat(bias.evidenceRequestThreshold())
                    .as("uncertainty=%s", level)
                    .isLessThanOrEqualTo(RegulationBias.NEVER_REQUEST_EVIDENCE);
        }
    }

    @Test
    @DisplayName("a consolidation demand above the threshold stops the admission of new work")
    void aConsolidationDemandAboveTheThresholdStopsAdmission() {
        RegulationBias low = controller.bias(stateOf(DriveSignal.Dimension.CONSOLIDATION_DEMAND, 0.2,
                DriveSignal.Effect.TASK_ADMISSION));
        RegulationBias high = controller.bias(stateOf(DriveSignal.Dimension.CONSOLIDATION_DEMAND, 1.0,
                DriveSignal.Effect.TASK_ADMISSION));

        assertThat(low.admitNonCriticalTask()).isTrue();
        assertThat(high.admitNonCriticalTask()).isFalse();
    }

    @Test
    @DisplayName("a large reward may not reduce the consolidation demand below the floor")
    void aLargeRewardMayNotReduceTheConsolidationDemandBelowTheFloor() {
        RegulatoryState demanded = stateOf(DriveSignal.Dimension.CONSOLIDATION_DEMAND, 1.0,
                DriveSignal.Effect.TASK_ADMISSION);
        List<RegulatoryEvent> rewarded = List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 1.0, "environment"));

        double withReward = controller.effectiveConsolidationDemand(demanded, rewarded);
        double withoutReward = controller.effectiveConsolidationDemand(demanded, List.of());

        assertThat(withReward)
                .as("the reward relieved the demand")
                .isLessThan(withoutReward);
        assertThat(withReward)
                .as("but not below the floor")
                .isCloseTo(RegulationController.CONSOLIDATION_DEMAND_FLOOR, within(1e-9));
        assertThat(withReward)
                .as("and the floor is above the admission threshold, so the work still waits")
                .isGreaterThan(RegulationController.CONSOLIDATION_ADMISSION_THRESHOLD);
        assertThat(controller.bias(demanded, rewarded).admitNonCriticalTask())
                .as("a reward-rich environment may slow consolidation, never prevent it")
                .isFalse();
    }

    @Test
    @DisplayName("the floor is never crossed by any reward, at any demand")
    void theFloorIsNeverCrossedByAnyReward() {
        List<RegulatoryEvent> rewarded = List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 1.0, "environment"));
        for (double demand = 0.0; demand <= 1.0; demand += 0.05) {
            RegulatoryState state = stateOf(DriveSignal.Dimension.CONSOLIDATION_DEMAND, demand,
                    DriveSignal.Effect.TASK_ADMISSION);
            double effective = controller.effectiveConsolidationDemand(state, rewarded);

            assertThat(effective)
                    .as("the demand is never raised above what the state holds: demand=%s", demand)
                    .isLessThanOrEqualTo(demand + 1e-9);
            assertThat(effective)
                    .as("the demand is never reduced below the floor that applies: demand=%s", demand)
                    .isGreaterThanOrEqualTo(
                            Math.min(demand, RegulationController.CONSOLIDATION_DEMAND_FLOOR) - 1e-9);
            if (demand >= RegulationController.CONSOLIDATION_DEMAND_FLOOR) {
                assertThat(effective)
                        .as("a demand at or above the floor stays at or above it: demand=%s", demand)
                        .isGreaterThanOrEqualTo(RegulationController.CONSOLIDATION_DEMAND_FLOOR - 1e-9);
            }
        }
    }

    @Test
    @DisplayName("a demand below the floor is not relieved at all")
    void aDemandBelowTheFloorIsNotRelievedAtAll() {
        RegulatoryState state = stateOf(DriveSignal.Dimension.CONSOLIDATION_DEMAND, 0.6,
                DriveSignal.Effect.TASK_ADMISSION);
        List<RegulatoryEvent> rewarded = List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 1.0, "environment"));

        assertThat(controller.effectiveConsolidationDemand(state, rewarded))
                .isCloseTo(0.6, within(1e-9));
    }

    @Test
    @DisplayName("a curiosity drive raises the priority of a task and never lowers it")
    void aCuriosityDriveRaisesThePriorityOfATask() {
        RegulationBias neutral = controller.bias(RegulatoryState.neutral());
        RegulationBias curious = controller.bias(stateOf(DriveSignal.Dimension.CURIOSITY, 1.0,
                DriveSignal.Effect.TASK_PRIORITY));
        RegulationBias novel = controller.bias(stateOf(DriveSignal.Dimension.NOVELTY, 1.0,
                DriveSignal.Effect.TASK_PRIORITY));

        assertThat(curious.taskPriorityMultiplier())
                .isCloseTo(RegulationBias.NEUTRAL_MULTIPLIER
                        + RegulationController.TASK_PRIORITY_GAIN, within(1e-9))
                .isGreaterThan(neutral.taskPriorityMultiplier());
        assertThat(novel.taskPriorityMultiplier())
                .isEqualTo(curious.taskPriorityMultiplier());
    }

    @Test
    @DisplayName("two priority drives of half are not one drive of one")
    void twoPriorityDrivesOfHalfAreNotOneDriveOfOne() {
        RegulatoryState state = stateOf(DriveSignal.Dimension.CURIOSITY, 0.5,
                DriveSignal.Effect.TASK_PRIORITY, DriveSignal.Dimension.NOVELTY, 0.5,
                DriveSignal.Effect.TASK_PRIORITY);

        assertThat(controller.bias(state).taskPriorityMultiplier())
                .isCloseTo(RegulationBias.NEUTRAL_MULTIPLIER
                        + 0.5 * RegulationController.TASK_PRIORITY_GAIN, within(1e-9));
    }

    @Test
    @DisplayName("a drive cannot move a knob by claiming an effect it was not given")
    void aDriveCannotMoveAKnobByClaimingAnEffect() {
        RegulatoryState mismatched = stateOf(DriveSignal.Dimension.SUSTAINED_LOAD, 1.0,
                DriveSignal.Effect.EVIDENCE_REQUEST);

        assertThat(controller.bias(mismatched)).isEqualTo(RegulationBias.neutral());
    }

    @Test
    @DisplayName("the reward signal is the sum of the rewards, capped at one")
    void theRewardSignalIsTheSumCappedAtOne() {
        List<RegulatoryEvent> events = List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 0.4, "environment"),
                RegulatoryEvent.of(RegulatoryEvent.Kind.TASK_COMPLETED, 1.0, "runner"),
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 0.3, "environment"));

        assertThat(RegulationController.rewardSignal(events)).isCloseTo(0.7, within(1e-9));
        assertThat(RegulationController.rewardSignal(List.of(
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 0.8, "environment"),
                RegulatoryEvent.of(RegulatoryEvent.Kind.REWARD, 0.8, "environment"))))
                .isEqualTo(1.0);
        assertThat(RegulationController.rewardSignal(List.of())).isEqualTo(0.0);
    }

    @Test
    @DisplayName("the effect of a dimension is fixed, and three dimensions carry none")
    void theEffectOfADimensionIsFixed() {
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD))
                .isEqualTo(DriveSignal.Effect.ALGORITHM_COST_PREFERENCE);
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.UNCERTAINTY))
                .isEqualTo(DriveSignal.Effect.EVIDENCE_REQUEST);
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.CONSOLIDATION_DEMAND))
                .isEqualTo(DriveSignal.Effect.TASK_ADMISSION);
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.CURIOSITY))
                .isEqualTo(DriveSignal.Effect.TASK_PRIORITY);
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.NOVELTY))
                .isEqualTo(DriveSignal.Effect.TASK_PRIORITY);
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.ATTENTION))
                .as("attention must not move a retrieval floor, so it carries no effect at all")
                .isEqualTo(DriveSignal.Effect.NONE);
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.VIGILANCE))
                .isEqualTo(DriveSignal.Effect.NONE);
        assertThat(RegulationController.effectFor(DriveSignal.Dimension.REWARD_PREDICTION_ERROR))
                .isEqualTo(DriveSignal.Effect.NONE);
    }
}
