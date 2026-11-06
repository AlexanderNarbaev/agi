package io.matrix.brain.runtime.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * RECON-W38.2 (M4) S4.2 — the attention safety floor and the aggregation feedback loop.
 *
 * <p><b>What this class proves.</b> Two properties, both about bounds rather than about benefit:</p>
 * <ol>
 * <li><b>Attention safety floor.</b> {@link NeuroModulatorHub#retrievalFloor()} is monotone
 * non-decreasing over the constructor-clamped attention domain and never falls below
 * {@link NeuroModulatorHub#BASE_RETRIEVAL_FLOOR}. The aggregator is the identity on the attention
 * channel, so it can neither raise nor lower the floor of the hub it is given.</li>
 * <li><b>Feedback loop.</b> Successive observations feed hub state forward: fatigue accumulates
 * and saturates, the derived sleep-demand signal latches once crossed, the proposed tolerance is
 * non-increasing -- decreasing while it is above
 * {@link FatigueUncertaintyAggregator#MIN_UNCERTAINTY_TOLERANCE} and otherwise holding once that
 * floor is reached -- and the attention, floor, reward and threshold components pass through
 * unchanged.</li>
 * </ol>
 *
 * <p><b>What this class does NOT prove.</b> The loop is closed over aggregator state only — there
 * is no environment observation feeding it, no scheduler consuming sleep demand, and no reader of
 * {@code TAG_UNCERTAINTY_TOLERANCE}. Neutral attention in the live path is a wiring-time obligation
 * on the caller, NOT something this package enforces: {@link FatigueUncertaintyAggregator#observe}
 * carries whatever attention it is handed, so a caller that passes attention 0.5 gets floor 0.275
 * and the aggregator will not object.</p>
 *
 * <p>Some tests below restate state transitions that {@code FatigueUncertaintyTest} also checks in
 * isolation. The difference is that these drive the hub in a sequence, so they observe that state
 * actually carries forward between observations rather than only that one call is well behaved.</p>
 *
 * <p>No {@code Random}, wall-clock or LLM is used: every input below is a fixed literal or an
 * index-derived value (CONSTITUTION.md Article I).</p>
 */
class AttentionSafetyFloorTest {

    /**
     * Candidate scores with maximal variance for values in [0,1], i.e. the largest step the
     * uncertainty channel can take. The variance here is 0.25, not 1.0 — the spread is taken over
     * similarities, whose domain is [0,1].
     */
    private static final List<Double> WIDEST = List.of(1.0, 0.0);

    /** Candidates clustered tightly, so the spread is zero and the uncertainty channel is idle. */
    private static final List<Double> NO_SPREAD = List.of(0.5, 0.5);

    private static NeuroModulatorHub hubWithAttention(double attention) {
        return new NeuroModulatorHub(attention, 0.5, 0.0, 0.0,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
    }

    // ---------------------------------------------------------------------
    // Attention safety floor
    // ---------------------------------------------------------------------

    @Test
    void retrieval_floor_never_falls_below_the_base_floor() {
        // Deterministic sweep of the whole attention domain, including both endpoints and
        // out-of-range inputs (the hub constructor clamps attention to [0,1]).
        double[] probes = {0.0, 1e-12, 0.001, 0.01, 0.1, 1.0 / 3.0, 0.25, 0.5, 0.75, 0.999, 1.0,
                -0.5, -1e-9, 1.0000001, 2.0, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY};

        for (int i = 0; i < probes.length; i++) {
            double floor = hubWithAttention(probes[i]).retrievalFloor();
            assertThat(floor)
                    .as("attention=%s must not yield a floor below BASE_RETRIEVAL_FLOOR", probes[i])
                    .isGreaterThanOrEqualTo(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
            // The contract is that the floor is a real number inside the published band.
            // Finiteness is asserted explicitly rather than left to the bounds comparison: the
            // bounds state what the value must be, whereas isFinite() states that it is a real
            // number at all.
            assertThat(floor).as("floor must be finite at probe %d", i).isFinite();
            assertThat(floor)
                    .as("floor must stay inside the published band at probe %d", i)
                    .isBetween(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR,
                            NeuroModulatorHub.ATTENTIVE_RETRIEVAL_FLOOR);
        }
    }

    @Test
    void the_floor_band_is_pinned_to_measured_values_not_only_to_constants() {
        // The literals below are the guard. Comparing against BASE_RETRIEVAL_FLOOR /
        // ATTENTIVE_RETRIEVAL_FLOOR alone would stay green if those constants were lowered, which
        // is exactly the regression this milestone exists to prevent.
        assertThat(hubWithAttention(NeuroModulatorHub.NEUTRAL_ATTENTION).retrievalFloor())
                .isEqualTo(0.20);
        assertThat(hubWithAttention(1.0).retrievalFloor()).isEqualTo(0.35);
        assertThat(hubWithAttention(0.5).retrievalFloor()).isEqualTo(0.275);
        assertThat(hubWithAttention(0.25).retrievalFloor()).isCloseTo(0.2375, within(1e-12));
        assertThat(hubWithAttention(0.75).retrievalFloor()).isCloseTo(0.3125, within(1e-12));
    }

    @Test
    void retrieval_floor_is_monotone_non_decreasing_in_attention() {
        // 101 evenly spaced probes; the published direction is "up with attention", so any
        // non-monotone pair would break the safety story. A quadratic or otherwise non-linear but
        // increasing map also passes this, which is why the band is pinned separately above.
        double previous = hubWithAttention(0.0).retrievalFloor();
        for (int step = 1; step <= 100; step++) {
            double attention = step / 100.0;
            double floor = hubWithAttention(attention).retrievalFloor();
            assertThat(floor)
                    .as("floor must not decrease when attention rises from step %d", step - 1)
                    .isGreaterThanOrEqualTo(previous);
            previous = floor;
        }
    }

    @Test
    void aggregation_is_the_identity_on_the_attention_channel() {
        // The aggregator does not write attention, so it can neither raise nor lower the floor of
        // the hub it is given. Asserted against absolute literals rather than against
        // input.retrievalFloor(), which would be implied by the carry-through alone.
        assertThat(FatigueUncertaintyAggregator.observe(hubWithAttention(0.0), WIDEST).retrievalFloor())
                .isEqualTo(0.20);
        assertThat(FatigueUncertaintyAggregator.observe(hubWithAttention(0.1), WIDEST).retrievalFloor())
                .isCloseTo(0.215, within(1e-12));
        assertThat(FatigueUncertaintyAggregator.observe(hubWithAttention(0.5), WIDEST).retrievalFloor())
                .isEqualTo(0.275);
        assertThat(FatigueUncertaintyAggregator.observe(hubWithAttention(0.75), WIDEST).retrievalFloor())
                .isCloseTo(0.3125, within(1e-12));
        assertThat(FatigueUncertaintyAggregator.observe(hubWithAttention(1.0), WIDEST).retrievalFloor())
                .isEqualTo(0.35);
    }

    // ---------------------------------------------------------------------
    // Feedback loop (aggregator state only — no environment observation)
    // ---------------------------------------------------------------------

    @Test
    void feedback_loop_accumulates_fatigue_until_it_saturates() {
        NeuroModulatorHub h = NeuroModulatorHub.productionDefault();
        double previous = h.fatigueIndex();

        for (int step = 1; step <= 40; step++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST);
            assertThat(h.fatigueIndex())
                    .as("fatigue must not decrease at step %d", step)
                    .isGreaterThanOrEqualTo(previous);
            assertThat(h.fatigueIndex()).isLessThanOrEqualTo(1.0);
            previous = h.fatigueIndex();
        }
        assertThat(h.fatigueIndex()).as("fatigue saturates, it does not wrap").isEqualTo(1.0);
    }

    @Test
    void feedback_loop_lowers_proposed_tolerance_and_stops_at_its_floor() {
        NeuroModulatorHub h = NeuroModulatorHub.productionDefault();
        double previous = h.uncertaintyTolerance();

        // One step is pinned to its exact golden value, which fixes the magnitude of the first
        // decrement for this fixture. It is the zero-spread case below, not this loop, that rules
        // out a variance-independent constant decrement.
        h = FatigueUncertaintyAggregator.observe(h, WIDEST);
        assertThat(h.uncertaintyTolerance()).isEqualTo(0.4875);

        for (int step = 2; step <= 200; step++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST);
            assertThat(h.uncertaintyTolerance())
                    .as("tolerance must never rise at step %d", step)
                    .isLessThanOrEqualTo(previous);
            previous = h.uncertaintyTolerance();
        }
        assertThat(h.uncertaintyTolerance())
                .isEqualTo(FatigueUncertaintyAggregator.MIN_UNCERTAINTY_TOLERANCE);
    }

    @Test
    void zero_spread_leaves_the_tolerance_untouched() {
        // The complement of the case above: with no spread there is no uncertainty signal, so the
        // tolerance must hold exactly rather than merely "not rise".
        NeuroModulatorHub h = NeuroModulatorHub.productionDefault();

        for (int step = 1; step <= 50; step++) {
            h = FatigueUncertaintyAggregator.observe(h, NO_SPREAD);
            assertThat(h.uncertaintyTolerance())
                    .as("tolerance must hold flat at step %d", step)
                    .isEqualTo(0.5);
        }
    }

    @Test
    void sleep_demand_latches_once_crossed_and_stays_latched() {
        // No reset exists anywhere in the aggregator, so the demand is a one-way latch. This is a
        // property of the current design; the reset contract belongs to RealSleepScheduler (M6).
        NeuroModulatorHub h = NeuroModulatorHub.productionDefault();
        int firstSleepingObservation = -1;

        for (int step = 1; step <= 30; step++) {
            NeuroModulatorHub previous = h;
            h = FatigueUncertaintyAggregator.observe(h, WIDEST);
            if (firstSleepingObservation < 0 && h.shouldSleep()) {
                firstSleepingObservation = step;
                // The latch is a demand comparison, not a fatigue counter, so assert the demand
                // itself rather than trusting the crossing index alone.
                assertThat(h.sleepDemand())
                        .isGreaterThanOrEqualTo(
                                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
                assertThat(previous.shouldSleep())
                        .as("the previous observation must still have been awake")
                        .isFalse();
            }
            if (firstSleepingObservation > 0) {
                assertThat(h.shouldSleep())
                        .as("sleep demand latched at observation %d but cleared at %d",
                                firstSleepingObservation, step)
                        .isTrue();
            }
        }

        assertThat(firstSleepingObservation)
                .as("sleep demand must be reachable from query volume alone")
                .isPositive();
        // Default threshold 0.7 with FATIGUE_PER_SERVED_QUERY 0.05 puts the crossing at 14.
        // This literal pins the accumulation; the assertions above pin the semantics.
        assertThat(firstSleepingObservation).isEqualTo(14);
    }

    @Test
    void feedback_loop_leaves_reward_and_threshold_untouched() {
        NeuroModulatorHub h =
                new NeuroModulatorHub(0.0, 0.5, 0.4, 0.0, 0.85);

        for (int step = 1; step <= 25; step++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST);
            assertThat(h.rewardSignal()).isEqualTo(0.4);
            assertThat(h.sleepDemandThreshold()).isEqualTo(0.85);
            assertThat(h.attentionLevel()).isEqualTo(NeuroModulatorHub.NEUTRAL_ATTENTION);
            assertThat(h.retrievalFloor()).isEqualTo(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
        }
    }

    @Test
    void accumulated_fatigue_still_requests_sleep_under_maximal_reward() {
        // The severe-fatigue floor is the guard against a reward-rich environment suppressing
        // consolidation forever. At reward 1.0 the cancellation takes demand down to 0.5 on its own,
        // so this test fails if either the reward cancellation is ignored OR the floor is deleted.
        NeuroModulatorHub h =
                new NeuroModulatorHub(NeuroModulatorHub.NEUTRAL_ATTENTION, 0.5, 1.0, 0.0,
                        NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
        // Step index, not a threshold condition: fatigue crosses 0.5 at step 11 because of
        // floating-point accumulation, so a "fatigue >= 0.5" predicate would sample 0.55 and
        // assert the wrong number. Pinned to the literal 0.5 / 0.25 pair instead.
        for (int step = 1; step <= 10; step++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST);
        }
        // Reward is genuinely applied here: an implementation that ignored rewardSignal would
        // report demand 0.5, and one that dropped the severe-fatigue floor is caught below.
        assertThat(h.fatigueIndex()).isCloseTo(0.5, within(1e-12));
        assertThat(h.sleepDemand()).isLessThan(h.fatigueIndex());
        assertThat(h.sleepDemand()).isCloseTo(0.25, within(1e-12));

        for (int step = 11; step <= 60; step++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST);
        }

        assertThat(h.fatigueIndex()).isEqualTo(1.0);
        assertThat(h.rewardSignal()).isEqualTo(1.0);
        // The literal 0.85 is the guard, not the constant: asserting
        // MIN_DEMAND_AT_SEVERE_FATIGUE here would stay green if that constant were demoted to the
        // threshold, which is precisely the regression this milestone exists to prevent. The second
        // assertion pins the constant's own published contract -- it is documented as sitting ABOVE
        // the default threshold -- as a relationship, so lowering it onto 0.7 also turns this red.
        assertThat(h.sleepDemand())
                .as("severe fatigue must hold demand above the sleep threshold despite reward")
                .isEqualTo(0.85);
        assertThat(h.sleepDemand())
                .as("the severe-fatigue demand must sit strictly above the default threshold")
                .isGreaterThan(NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
        assertThat(h.shouldSleep()).isTrue();
    }

    @Test
    void severe_fatigue_floor_still_forces_sleep_when_the_threshold_is_raised_to_the_floor() {
        // This case is load-bearing on the VALUE 0.85, which the case above cannot test on its own.
        // Reward 0.4 against saturated fatigue leaves a residual of 1.0 - 0.2 = 0.8, which is
        // already below the 0.85 threshold chosen here. Only the severe-fatigue floor lifts demand
        // back above it. Demote MIN_DEMAND_AT_SEVERE_FATIGUE to 0.80 or below and demand stays at
        // 0.8, shouldSleep() goes false, and this test fails -- the one configuration in which a
        // demoted floor silently stops forcing consolidation.
        NeuroModulatorHub h =
                new NeuroModulatorHub(NeuroModulatorHub.NEUTRAL_ATTENTION, 0.5, 0.4, 0.0, 0.85);

        for (int step = 1; step <= 25; step++) {
            h = FatigueUncertaintyAggregator.observe(h, WIDEST);
        }

        assertThat(h.fatigueIndex()).isEqualTo(1.0);
        // 0.8 is what the reward cancellation alone would leave behind; the floor is what moves it.
        assertThat(h.sleepDemand())
                .as("the floor must lift demand above the reward-relieved residual")
                .isGreaterThan(1.0 - 0.4 * NeuroModulatorHub.MAX_REWARD_FATIGUE_CANCEL);
        assertThat(h.sleepDemand()).isEqualTo(0.85);
        assertThat(h.shouldSleep())
                .as("a demoted severe-fatigue floor must not silence the sleep signal here")
                .isTrue();
    }
}