package io.matrix.cognitive.neuromodulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.matrix.cognitive.DriveSignal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W44.3 — the measured downstream effect of a drive.
 *
 * <p>The W44 directive does not accept "a modulator is wired in" as evidence that a modulator
 * does anything. What it accepts is a difference that can be attributed: two runs of the same
 * scenario that differ in one drive, and a difference in the result that follows from it and from
 * nothing else.</p>
 *
 * <p>That is what this test does, and it prints the numbers it measured so that the claim can be
 * checked rather than believed.</p>
 *
 * <p>What this asserts about the SYSTEM, and not about the code:</p>
 *
 * <ul>
 *   <li>the sustained load changes the cost weight, and the change is exactly the declared one;</li>
 *   <li>nothing else in the bias moves, so the difference is attributable to that one drive;</li>
 *   <li>the drive is visible in the state before the bias is computed, so the effect is a chain
 *       and not a coincidence;</li>
 *   <li>the load at which the cost preference starts to bite is stated as a measured number
 *       rather than as an assumption.</li>
 * </ul>
 */
class RegulationEffectMeasurementTest {

    private static final String OBSERVED_AT = "2026-11-02T00:00:00Z";

    private final DriveDynamics dynamics = DriveDynamics.standard();
    private final RegulationController controller = new RegulationController();

    /**
     * One scenario, deliberately mixed: work that finished, pressure that was sampled, a
     * prediction that was wrong, and input the model had not seen.
     */
    private static final List<RegulatoryEvent> SCENARIO = List.of(
            RegulatoryEvent.of(RegulatoryEvent.Kind.TASK_COMPLETED, 1.0, "runner"),
            RegulatoryEvent.of(RegulatoryEvent.Kind.LOAD_SAMPLE, 1.0, "monitor"),
            RegulatoryEvent.of(RegulatoryEvent.Kind.PREDICTION_ERROR, 0.5, "evaluator"),
            RegulatoryEvent.of(RegulatoryEvent.Kind.NOVEL_INPUT, 1.0, "perception"),
            RegulatoryEvent.of(RegulatoryEvent.Kind.LOAD_SAMPLE, 1.0, "monitor"),
            RegulatoryEvent.of(RegulatoryEvent.Kind.TASK_FAILED, 1.0, "runner"));

    private static RegulatoryState withLoad(RegulatoryState base, double level) {
        return base.with(new DriveSignal(
                DriveSignal.Dimension.SUSTAINED_LOAD, level, "scenario",
                RegulationController.effectFor(DriveSignal.Dimension.SUSTAINED_LOAD), OBSERVED_AT));
    }

    @Test
    @DisplayName("the sustained load changes the cost weight, and nothing else changes")
    void theSustainedLoadChangesTheCostWeightAndNothingElse() {
        RegulatoryState base = dynamics.advance(RegulatoryState.neutral(), SCENARIO, 1, OBSERVED_AT);
        RegulatoryState withoutLoad = withLoad(base, 0.0);
        RegulatoryState withFullLoad = withLoad(base, 1.0);

        RegulationBias low = controller.bias(withoutLoad, SCENARIO);
        RegulationBias high = controller.bias(withFullLoad, SCENARIO);

        System.out.printf("scenario drives: %s%n", base.held());
        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            System.out.printf("  %-24s level=%.4f effect=%s%n",
                    dimension, base.level(dimension),
                    RegulationController.effectFor(dimension));
        }
        System.out.printf("costWeightMultiplier  load=0.0 -> %.6f%n", low.costWeightMultiplier());
        System.out.printf("costWeightMultiplier  load=1.0 -> %.6f%n", high.costWeightMultiplier());
        System.out.printf("difference attributable to SUSTAINED_LOAD = %.6f%n",
                high.costWeightMultiplier() - low.costWeightMultiplier());
        System.out.printf("evidenceRequestThreshold %.6f -> %.6f, admitNonCriticalTask %s -> %s, "
                        + "taskPriorityMultiplier %.6f -> %.6f%n",
                low.evidenceRequestThreshold(), high.evidenceRequestThreshold(),
                low.admitNonCriticalTask(), high.admitNonCriticalTask(),
                low.taskPriorityMultiplier(), high.taskPriorityMultiplier());

        // The effect is real: the two runs differ in the cost weight.
        assertThat(high.costWeightMultiplier())
                .as("a system under full load pays more for the expensive method")
                .isGreaterThan(low.costWeightMultiplier());
        assertThat(low.costWeightMultiplier())
                .as("and a system under no load pays exactly the neutral price")
                .isEqualTo(RegulationBias.NEUTRAL_MULTIPLIER);
        assertThat(high.costWeightMultiplier())
                .as("by exactly the declared amount")
                .isCloseTo(RegulationBias.NEUTRAL_MULTIPLIER
                                + (1.0 - RegulationController.LOAD_COST_THRESHOLD)
                                * RegulationController.LOAD_COST_GAIN,
                        within(1e-9));

        // The difference is attributable: the states agree on every other drive.
        Map<DriveSignal.Dimension, DriveSignal> otherDrives = new EnumMap<>(DriveSignal.Dimension.class);
        otherDrives.putAll(withoutLoad.drives());
        otherDrives.remove(DriveSignal.Dimension.SUSTAINED_LOAD);
        Map<DriveSignal.Dimension, DriveSignal> otherDrivesHigh = new EnumMap<>(DriveSignal.Dimension.class);
        otherDrivesHigh.putAll(withFullLoad.drives());
        otherDrivesHigh.remove(DriveSignal.Dimension.SUSTAINED_LOAD);
        assertThat(otherDrivesHigh)
                .as("the two runs differ in the load and in nothing else")
                .isEqualTo(otherDrives);

        // And the bias agrees on every other knob.
        assertThat(high.evidenceRequestThreshold()).isEqualTo(low.evidenceRequestThreshold());
        assertThat(high.admitNonCriticalTask()).isEqualTo(low.admitNonCriticalTask());
        assertThat(high.taskPriorityMultiplier()).isEqualTo(low.taskPriorityMultiplier());
    }

    @Test
    @DisplayName("the load at which the cost preference starts to bite is measured, not assumed")
    void theLoadAtWhichTheCostPreferenceBitesIsMeasured() {
        RegulatoryState state = RegulatoryState.neutral();
        List<Double> levels = new ArrayList<>();
        List<Double> multipliers = new ArrayList<>();

        for (int sample = 1; sample <= 10; sample++) {
            state = dynamics.advance(state,
                    List.of(RegulatoryEvent.of(RegulatoryEvent.Kind.LOAD_SAMPLE, 1.0, "monitor")),
                    0, OBSERVED_AT);
            levels.add(state.level(DriveSignal.Dimension.SUSTAINED_LOAD));
            multipliers.add(controller.bias(state).costWeightMultiplier());
        }

        System.out.printf("%-12s %-12s %-12s%n", "samples", "load", "costMultiplier");
        for (int i = 0; i < levels.size(); i++) {
            System.out.printf("%-12d %-12.4f %-12.6f%n", i + 1, levels.get(i), multipliers.get(i));
        }

        assertThat(levels.get(3))
                .as("four samples of load leave the cost preference untouched")
                .isLessThan(RegulationController.LOAD_COST_THRESHOLD);
        assertThat(multipliers.get(3)).isEqualTo(RegulationBias.NEUTRAL_MULTIPLIER);
        assertThat(multipliers.get(5))
                .as("six samples of load cross the threshold and the cost weight moves")
                .isGreaterThan(RegulationBias.NEUTRAL_MULTIPLIER);
        assertThat(levels.get(9))
                .as("and the drive is bounded by the interval the record enforces")
                .isCloseTo(DriveDynamics.MAXIMUM_LEVEL, within(1e-9));
        assertThat(multipliers.get(9))
                .isCloseTo(RegulationBias.NEUTRAL_MULTIPLIER
                                + (DriveDynamics.MAXIMUM_LEVEL - RegulationController.LOAD_COST_THRESHOLD)
                                * RegulationController.LOAD_COST_GAIN,
                        within(1e-9));
    }

    @Test
    @DisplayName("the uncertainty in the same scenario lowers the evidence threshold")
    void theUncertaintyInTheSameScenarioLowersTheEvidenceThreshold() {
        RegulatoryState base = dynamics.advance(RegulatoryState.neutral(), SCENARIO, 1, OBSERVED_AT);
        double uncertainLevel = base.level(DriveSignal.Dimension.UNCERTAINTY);

        RegulationBias fromScenario = controller.bias(base, SCENARIO);
        RegulationBias withoutUncertainty = controller.bias(
                base.with(new DriveSignal(DriveSignal.Dimension.UNCERTAINTY, 0.0, "scenario",
                        RegulationController.effectFor(DriveSignal.Dimension.UNCERTAINTY), OBSERVED_AT)),
                SCENARIO);

        System.out.printf("scenario uncertainty=%.6f -> evidenceRequestThreshold %.6f "
                        + "(without uncertainty %.6f)%n",
                uncertainLevel, fromScenario.evidenceRequestThreshold(),
                withoutUncertainty.evidenceRequestThreshold());

        assertThat(fromScenario.evidenceRequestThreshold())
                .as("the scenario really did leave an uncertainty behind")
                .isLessThan(withoutUncertainty.evidenceRequestThreshold());
        assertThat(withoutUncertainty.evidenceRequestThreshold())
                .isEqualTo(RegulationBias.NEVER_REQUEST_EVIDENCE);
    }
}
