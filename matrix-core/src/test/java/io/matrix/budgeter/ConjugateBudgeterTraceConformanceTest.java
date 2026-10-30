package io.matrix.budgeter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W39 — trace conformance between {@code formal/ConjugateBudgeterDP.tla}
 * and {@link ConjugateBudgeter}.
 *
 * <p>The model's shadow price is the slope of the value function over the
 * LAST GRID UNIT, where the grid is reduced by the gcd of the row costs -
 * the same reduction the Java applies to its DP table. The two used to
 * disagree whenever that gcd was greater than one: the model reported the
 * marginal value of ONE raw unit, the Java the marginal value of G raw
 * units.
 *
 * <p>The instance below is the counterexample that made the difference
 * visible. The expected numbers are the MODEL's values, confirmed by TLC
 * over the configuration
 * {@code Rows = {1,2}, Values = {1,100}, Costs = {4,6}}: the model reports
 * {@code V*(6) - V*(4) = 100 - 1 = 99}. The Java must report the same
 * number, otherwise the formal gate describes a different algorithm than
 * the one that allocates the brain's budget.
 */
class ConjugateBudgeterTraceConformanceTest {

    private final ConjugateBudgeter budgeter = new ConjugateBudgeter();

    @Test
    @DisplayName("the shadow price is the slope over the reduced grid, as the model computes it")
    void shadowPriceMatchesTheModelOnAGridWithGcdTwo() {
        ConjugateBudgeter.Row[] rows = {
                new ConjugateBudgeter.Row("a", 1, 4),
                new ConjugateBudgeter.Row("b", 100, 6)};

        ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, 7);

        // The model's GridShadow(7): GridUnit = gcd(4, 6) = 2, so the last
        // grid unit spans the raw budget 4 -> 6.
        //   V*(6) = 100 (row b costs 6), V*(4) = 1 (row a costs 4)
        assertThat(alloc.shadowPrice()).isCloseTo(99.0, within(1e-6));
        assertThat(alloc.objective()).isCloseTo(100.0, within(1e-6));
    }

    @Test
    @DisplayName("with a gcd of one the grid unit is one raw unit")
    void shadowPriceMatchesTheModelOnAGridWithGcdOne() {
        ConjugateBudgeter.Row[] rows = {
                new ConjugateBudgeter.Row("a", 3, 2),
                new ConjugateBudgeter.Row("b", 5, 3),
                new ConjugateBudgeter.Row("c", 9, 4)};

        ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, 7);

        // GridUnit = gcd(2,3,4) = 1, so the slope is over one raw unit:
        //   V*(7) = 14 (b and c, cost 3+4), V*(6) = 12 (c and a, cost 4+2)
        //   -> the slope is 2, NOT 0. The model's own comment records the
        //   same figures (V*(7) = 14, V*(8) = 14, V*(9) = 17).
        assertThat(alloc.shadowPrice()).isCloseTo(2.0, within(1e-6));
        assertThat(alloc.objective()).isCloseTo(14.0, within(1e-6));
    }

    @Test
    @DisplayName("the model and the Java agree on the whole envelope sweep")
    void shadowPriceMatchesTheModelAcrossTheEnvelope() {
        ConjugateBudgeter.Row[] rows = {
                new ConjugateBudgeter.Row("a", 1, 4),
                new ConjugateBudgeter.Row("b", 100, 6)};

        // The model's DPValue(e) for the same rows: V*(e) = 0 for e < 4,
        // 1 for 4 <= e < 6, 100 for 6 <= e. With GridUnit = 2 the model's
        // shadow price at envelope e is V*(GridCap(e)) - V*(GridCap(e) - 2).
        double[] modelShadow = {0, 0, 0, 0, 1, 1, 99, 99};

        for (int envelope = 0; envelope <= 7; envelope++) {
            ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, envelope);
            if (envelope < 4) {
                // The envelope is below the cheapest row. The Java takes its
                // proportional fallback, whose shadow price is NaN; the
                // model's fallback sets 0, because TLA+ has no NaN. The
                // divergence is recorded in formal/spec-implementation-map.yml
                // and is not a shadow-price agreement case.
                assertThat(alloc.shadowPrice())
                        .as("fallback shadow price at envelope %d", envelope)
                        .isNaN();
                continue;
            }
            assertThat(alloc.shadowPrice())
                    .as("shadow price at envelope %d", envelope)
                    .isCloseTo(modelShadow[envelope], within(1e-6));
        }
    }
}
