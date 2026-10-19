package io.matrix.brain.runtime.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * RECON-W38.2 — BDD tests for fatigue/uncertainty aggregation into the hub.
 *
 * Binding rule 0(3) honoured throughout: attention is never altered, so the retrieval floor is a
 * pure function of the attention carried through; aggregation may only drive fatigue, the proposed
 * uncertainty-tolerance and the derived sleep-demand signal (shadow mode).
 *
 * <p>Golden values below are asserted EXACTLY (AssertJ double isEqualTo is IEEE-754 exact). The
 * fixtures were chosen so the arithmetic is binary-exact, so these assertions fail if the model is
 * replaced by a stub that ignores its input.</p>
 */
class FatigueUncertaintyTest {

    private static final List<Double> TWO_CANDIDATES = List.of(0.9, 0.1);
    private static final List<Double> THREE_NOISY = List.of(0.95, 0.05, 0.45);

    /** variance(1.0, 0.0) = 0.25 exactly; 0.5 - 0.25 * 0.05 = 0.4875 exactly. */
    private static final List<Double> WIDEST_BINARY_EXACT = List.of(1.0, 0.0);
    private static final double TOLERANCE_AFTER_ONE_WIDEST = 0.4875;

    @Test
    void fatigue_rises_monotonically_with_served_query_volume() {
        NeuroModulatorHub h = NeuroModulatorHub.productionDefault();

        double start = h.fatigueIndex();
        double prev = start - 1.0;
        for (int i = 0; i < 5; i++) {
            h = FatigueUncertaintyAggregator.observe(h, TWO_CANDIDATES);
            assertThat(h.fatigueIndex()).isGreaterThan(prev);
            assertThat(h.fatigueIndex()).isGreaterThanOrEqualTo(start);
            prev = h.fatigueIndex();
        }
        assertThat(h.fatigueIndex()).isEqualTo(0.25);
    }

    @Test
    void noisy_stream_fires_sleep_demand_and_fatigue_never_exceeds_one() {
        NeuroModulatorHub h = NeuroModulatorHub.productionDefault();
        boolean demanded = false;
        for (int i = 0; i < 30; i++) {
            h = FatigueUncertaintyAggregator.observe(h, THREE_NOISY);
            assertThat(h.fatigueIndex()).isBetween(0.0, 1.0);
            if (h.shouldSleep()) {
                demanded = true;
            }
        }
        assertThat(demanded).as("sleep demand must fire on a noisy stream").isTrue();
        assertThat(h.fatigueIndex()).isEqualTo(1.0);
        assertThat(h.attentionLevel()).isEqualTo(NeuroModulatorHub.NEUTRAL_ATTENTION);
    }

    @Test
    void fatigue_is_a_one_way_ratchet_so_sleep_demand_latches() {
        NeuroModulatorHub h = NeuroModulatorHub.productionDefault();
        for (int i = 0; i < 200; i++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST_BINARY_EXACT);
        }
        assertThat(h.fatigueIndex()).isEqualTo(1.0);
        assertThat(h.shouldSleep())
                .as("nothing in this class lowers fatigue, so sleep demand latches and stays latched")
                .isTrue();
    }

    @Test
    void uncertain_evidence_lowers_uncertainty_tolerance_but_never_below_floor() {
        NeuroModulatorHub start = NeuroModulatorHub.productionDefault();
        NeuroModulatorHub afterOne = FatigueUncertaintyAggregator.observe(
                start, List.of(0.99, 0.01));
        assertThat(afterOne.uncertaintyTolerance()).isLessThan(start.uncertaintyTolerance());

        NeuroModulatorHub h = afterOne;
        for (int i = 0; i < 100; i++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST_BINARY_EXACT);
        }
        assertThat(h.uncertaintyTolerance()).isEqualTo(FatigueUncertaintyAggregator.MIN_UNCERTAINTY_TOLERANCE);
    }

    @Test
    void variance_is_the_empirical_variance_not_a_constant() {
        // If varianceOf ignored its argument and returned a constant, these three assertions fail.
        assertThat(FatigueUncertaintyAggregator.varianceOf(WIDEST_BINARY_EXACT)).isEqualTo(0.25);
        assertThat(FatigueUncertaintyAggregator.varianceOf(List.of(0.5, 0.5))).isEqualTo(0.0);
        assertThat(FatigueUncertaintyAggregator.varianceOf(List.of(1.0, 0.5, 0.0)))
                .isEqualTo(0.16666666666666666);
    }

    @Test
    void one_widest_observation_lowers_tolerance_by_the_documented_step() {
        NeuroModulatorHub start = NeuroModulatorHub.productionDefault();
        NeuroModulatorHub after = FatigueUncertaintyAggregator.observe(start, WIDEST_BINARY_EXACT);

        assertThat(start.uncertaintyTolerance()).isEqualTo(0.5);
        assertThat(after.uncertaintyTolerance()).isEqualTo(TOLERANCE_AFTER_ONE_WIDEST);
    }

    @Test
    void tolerance_never_rises_even_when_the_supplied_tolerance_is_already_below_the_floor() {
        // The hub clamps tolerance to [0,1] only, so 0.05 is a legal input while MIN is 0.1.
        // A floor-only clamp would RAISE this to 0.1 and remove a potential refusal.
        NeuroModulatorHub belowFloor =
                new NeuroModulatorHub(NeuroModulatorHub.NEUTRAL_ATTENTION, 0.05, 0.0, 0.0,
                        NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);

        NeuroModulatorHub observed = FatigueUncertaintyAggregator.observe(belowFloor, null);

        assertThat(observed.uncertaintyTolerance())
                .as("refusal-only: aggregation may tighten a threshold, never loosen it")
                .isLessThanOrEqualTo(belowFloor.uncertaintyTolerance());
    }

    @Test
    void neutral_attention_and_retrieval_floor_are_untouched() {
        NeuroModulatorHub h = FatigueUncertaintyAggregator.observe(
                NeuroModulatorHub.productionDefault(), List.of(0.5, 0.5));
        assertThat(h.attentionLevel()).isEqualTo(NeuroModulatorHub.NEUTRAL_ATTENTION);
        assertThat(h.retrievalFloor()).isEqualTo(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
        assertThat(h.sleepDemandThreshold()).isEqualTo(NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
    }

    @Test
    void non_neutral_attention_reward_and_threshold_are_carried_through_unchanged() {
        // The load-bearing safety test: a non-neutral hub must keep its ATTENTIVE floor. An
        // implementation that reset attention to neutral would silently drop 0.35 -> 0.20 here.
        NeuroModulatorHub attentive =
                new NeuroModulatorHub(1.0, 0.5, 7.0, 0.0, 0.9);

        NeuroModulatorHub after = FatigueUncertaintyAggregator.observe(attentive, WIDEST_BINARY_EXACT);

        assertThat(after.attentionLevel()).isEqualTo(1.0);
        assertThat(after.rewardSignal()).isEqualTo(7.0);
        assertThat(after.sleepDemandThreshold()).isEqualTo(0.9);
        assertThat(after.retrievalFloor())
                .isEqualTo(NeuroModulatorHub.ATTENTIVE_RETRIEVAL_FLOOR)
                // The literal is the value guard, not a duplicate of the line above: only the
                // constant comparison would silently pass if ATTENTIVE_RETRIEVAL_FLOOR were
                // lowered back toward the 0.20 base floor.
                .isEqualTo(0.35);
    }

    @Test
    void degenerate_candidate_lists_carry_no_spread_signal() {
        assertThat(FatigueUncertaintyAggregator.varianceOf(null)).isEqualTo(0.0);
        assertThat(FatigueUncertaintyAggregator.varianceOf(Collections.emptyList())).isEqualTo(0.0);
        assertThat(FatigueUncertaintyAggregator.varianceOf(List.of(0.9))).isEqualTo(0.0);
    }

    @Test
    void null_and_non_finite_candidates_are_dropped_rather_than_poisoning_the_mean() {
        // Pinned semantics: filter unusable elements, recompute over the remainder, 0 if <2 remain.
        assertThat(FatigueUncertaintyAggregator.varianceOf(Arrays.asList(0.9, null)))
                .as("one usable element left after dropping null")
                .isEqualTo(0.0);
        assertThat(FatigueUncertaintyAggregator.varianceOf(Arrays.asList(null, null)))
                .as("no usable elements at all")
                .isEqualTo(0.0);
        assertThat(FatigueUncertaintyAggregator.varianceOf(List.of(0.9, Double.NaN)))
                .as("NaN is dropped as no-signal, not treated as 0.0")
                .isEqualTo(0.0);
        assertThat(FatigueUncertaintyAggregator.varianceOf(List.of(1.0, Double.POSITIVE_INFINITY)))
                .isEqualTo(0.0);
        assertThat(FatigueUncertaintyAggregator.varianceOf(Arrays.asList(1.0, 0.0, null)))
                .as("a bad element must not mask the two good ones")
                .isEqualTo(0.25);
    }

    @Test
    void out_of_range_similarities_are_clamped_into_a_unit_variance() {
        assertThat(FatigueUncertaintyAggregator.varianceOf(List.of(1.5, -0.5)))
                .isEqualTo(1.0);

        NeuroModulatorHub start = NeuroModulatorHub.productionDefault();
        NeuroModulatorHub after =
                FatigueUncertaintyAggregator.observe(start, List.of(1.5, -0.5));
        // Golden literal, not a recomputed expression: variance(1.5,-0.5) is exactly 1.0, so the
        // tolerance drops by exactly one full step to 0.45.
        assertThat(after.uncertaintyTolerance()).isEqualTo(0.45);
    }

    @Test
    void a_zero_spread_observation_leaves_tolerance_untouched() {
        NeuroModulatorHub start = NeuroModulatorHub.productionDefault();
        NeuroModulatorHub after = FatigueUncertaintyAggregator.observe(start, List.of(0.5, 0.5));

        assertThat(after.uncertaintyTolerance()).isEqualTo(start.uncertaintyTolerance());
        assertThat(after.fatigueIndex())
                .as("fatigue still advances: volume is charged even with no spread signal")
                .isEqualTo(FatigueUncertaintyAggregator.FATIGUE_PER_SERVED_QUERY);
    }
}
