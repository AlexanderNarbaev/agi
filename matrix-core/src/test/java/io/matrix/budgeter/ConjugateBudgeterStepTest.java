package io.matrix.budgeter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * W-B unit tests for the per-period step() API on {@link ConjugateBudgeter}.
 * Covers the three documented invariants:
 * <ul>
 *   <li>lambda monotonicity — verified at the single-DP layer;</li>
 *   <li>shadow-price bounds — clamp to {@code [0, maxVperC]} across epochs;</li>
 *   <li>finite horizon — epoch counter monotonic and bounded by caller's horizon.</li>
 * </ul>
 */
class ConjugateBudgeterStepTest {

    private static ConjugateBudgeter.Row[] canonicalRows() {
        return new ConjugateBudgeter.Row[]{
            new ConjugateBudgeter.Row("a", 60, 10),
            new ConjugateBudgeter.Row("b", 100, 20),
            new ConjugateBudgeter.Row("c", 120, 30)
        };
    }

    @Test
    void initialStateIsEpochZeroAndShadowZero() {
        ConjugateBudgeter b = new ConjugateBudgeter();
        ConjugateBudgeter.BudgeterState s = b.state();
        assertThat(s.epoch()).isEqualTo(0L);
        assertThat(s.shadowPrice()).isEqualTo(0.0);
    }

    @Test
    void shadowPriceAlwaysInBoundsAcrossSteps() {
        ConjugateBudgeter b = new ConjugateBudgeter();
        ConjugateBudgeter.Row[] rows = canonicalRows();
        double maxVperC = ConjugateBudgeter.maxValuePerCost(rows);
        // feed wildly out-of-range observations; clamp must hold
        double[] observations = {1e6, -1e6, 1e9, -1e9, 42.0, 0.0, maxVperC * 100};
        for (int i = 0; i < observations.length; i++) {
            ConjugateBudgeter.BudgeterState s = b.step(rows, i + 1L, observations[i]);
            assertThat(s.shadowPrice()).isGreaterThanOrEqualTo(0.0);
            assertThat(s.shadowPrice()).isLessThanOrEqualTo(maxVperC + 1e-9);
        }
    }

    @Test
    void epochCounterIsMonotonicAndStartsAtOne() {
        ConjugateBudgeter b = new ConjugateBudgeter();
        ConjugateBudgeter.Row[] rows = canonicalRows();
        ConjugateBudgeter.BudgeterState s1 = b.step(rows, 1L, 0.0);
        ConjugateBudgeter.BudgeterState s2 = b.step(rows, 2L, 0.0);
        ConjugateBudgeter.BudgeterState s3 = b.step(rows, 100L, 0.0);
        assertThat(s1.epoch()).isEqualTo(1L);
        assertThat(s2.epoch()).isEqualTo(2L);
        assertThat(s3.epoch()).isEqualTo(100L);
    }

    @Test
    void rejectsNonMonotonicEpoch() {
        ConjugateBudgeter b = new ConjugateBudgeter();
        ConjugateBudgeter.Row[] rows = canonicalRows();
        b.step(rows, 5L, 0.0);
        assertThatThrownBy(() -> b.step(rows, 5L, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> b.step(rows, 3L, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void smoothingConvergesToObservedWhenAlphaIsSmall() {
        // with many repeated observations, smoothing should approach them
        ConjugateBudgeter b = new ConjugateBudgeter();
        ConjugateBudgeter.Row[] rows = canonicalRows();
        double observed = 1.0;
        for (int i = 1; i <= 50; i++) {
            b.step(rows, i, observed);
        }
        ConjugateBudgeter.BudgeterState s = b.state();
        assertThat(s.shadowPrice()).isCloseTo(observed, within(1e-3));
    }

    @Test
    void dpOptimalValueIsMonotonicallyNonDecreasingInEnvelope() {
        // W-B invariant 1 (revised): for 0/1 knapsack the optimal VALUE
        // V*(e) is non-decreasing in the envelope. Strict shadow-price
        // monotonicity (λ non-increasing) holds only for the fractional
        // knapsack relaxation — not for 0/1 with mixed item ratios.
        // Documented in formal/ConjugateBudgeterDP.tla header.
        ConjugateBudgeter b = new ConjugateBudgeter();
        ConjugateBudgeter.Row[] rows = canonicalRows();
        double prev = -1.0;
        for (long envelope = 0; envelope <= 60; envelope++) {
            ConjugateBudgeter.Allocation alloc = b.allocate(rows, envelope);
            double value = (alloc.mode() == ConjugateBudgeter.Mode.CONJUGATE)
                    ? alloc.objective() : 0.0;
            assertThat(value).isGreaterThanOrEqualTo(prev - 1e-9);
            prev = value;
        }
    }

    @Test
    void resetClearsStateButKeepsBudgeterUsable() {
        ConjugateBudgeter b = new ConjugateBudgeter();
        ConjugateBudgeter.Row[] rows = canonicalRows();
        b.step(rows, 5L, 2.0);
        assertThat(b.state().epoch()).isEqualTo(5L);
        b.resetPeriodState();
        assertThat(b.state().epoch()).isEqualTo(0L);
        // budgeter still usable after reset
        ConjugateBudgeter.BudgeterState fresh = b.step(rows, 1L, 0.5);
        assertThat(fresh.epoch()).isEqualTo(1L);
    }
}