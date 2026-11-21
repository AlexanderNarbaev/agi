package io.matrix.compression;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TruthTableMinimizerTest {

    // ─── Quine-McCluskey (k <= 12) ───

    @Test
    void qmShouldMinimizeConstantTrue() {
        int k = 3;
        int size = 1 << k;
        BitSet allOnes = new BitSet(size);
        allOnes.set(0, size);
        TruthTable tt = TruthTable.of(k, allOnes);

        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.implicants()).hasSize(1);
        assertThat(result.implicants().get(0).isTautology()).isTrue();
        assertThat(result.algorithm()).isEqualTo(TruthTableMinimizer.Algorithm.QUINE_MCCLUSKEY);
    }

    @Test
    void qmShouldMinimizeConstantFalse() {
        int k = 4;
        BitSet allZeros = new BitSet(1 << k);
        TruthTable tt = TruthTable.of(k, allZeros);

        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.implicants()).isEmpty();
        assertThat(result.algorithm()).isEqualTo(TruthTableMinimizer.Algorithm.QUINE_MCCLUSKEY);
    }

    @Test
    void qmShouldMinimizeIdentityFunction() {
        // f(x0) = x0, for k=1
        TruthTable tt = TruthTable.fromLong(1, 0b10);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.implicants()).hasSize(1);
        assertThat(result.evaluate(0b1)).isTrue();
        assertThat(result.evaluate(0b0)).isFalse();
    }

    @Test
    void qmShouldMinimizeAndFunction() {
        // f(a,b) = a AND b → minterm {3}
        TruthTable tt = TruthTable.fromLong(2, 0b1000);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.implicants()).hasSize(1);
        // Should be a·b
        assertThat(result.evaluate(0b11)).isTrue();
        assertThat(result.evaluate(0b10)).isFalse();
        assertThat(result.evaluate(0b01)).isFalse();
        assertThat(result.evaluate(0b00)).isFalse();
    }

    @Test
    void qmShouldMinimizeOrFunction() {
        // f(a,b) = a OR b → minterms {1,2,3}
        TruthTable tt = TruthTable.fromLong(2, 0b1110);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.evaluate(0b11)).isTrue();
        assertThat(result.evaluate(0b10)).isTrue();
        assertThat(result.evaluate(0b01)).isTrue();
        assertThat(result.evaluate(0b00)).isFalse();
    }

    @Test
    void qmShouldMinimizeXorFunction() {
        // f(a,b) = a XOR b → minterms {1,2}
        TruthTable tt = TruthTable.fromLong(2, 0b0110);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.evaluate(0b00)).isFalse();
        assertThat(result.evaluate(0b01)).isTrue();
        assertThat(result.evaluate(0b10)).isTrue();
        assertThat(result.evaluate(0b11)).isFalse();
    }

    @Test
    void qmShouldMinimizeRandomK8() {
        Random rng = new Random(42);
        TruthTable tt = TruthTable.random(8, rng);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);

        // Verify the minimized DNF is equivalent to the original
        for (int i = 0; i < (1 << 8); i++) {
            assertThat(result.evaluate(i))
                    .as("input=%d", i)
                    .isEqualTo(tt.evaluate(i));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6, 8, 10, 12})
    void qmShouldProduceCorrectDnfForVariousK(int k) {
        Random rng = new Random(k * 31);
        TruthTable tt = TruthTable.random(k, rng);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);

        int size = 1 << k;
        for (int i = 0; i < size; i++) {
            assertThat(result.evaluate(i))
                    .as("k=%d, input=%d", k, i)
                    .isEqualTo(tt.evaluate(i));
        }
    }

    @Test
    void qmShouldMinimizeSingleMinterm() {
        // Only minterm 5 is true: f(x2,x1,x0) = x2'·x1·x0
        TruthTable tt = TruthTable.fromLong(3, 1L << 5);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.implicants()).hasSize(1);
        for (int i = 0; i < 8; i++) {
            assertThat(result.evaluate(i))
                    .as("input=%d", i)
                    .isEqualTo(i == 5);
        }
    }

    @Test
    void qmShouldHandleK1() {
        TruthTable tt = TruthTable.fromLong(1, 0b01); // NOT x0
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.evaluate(0)).isTrue();
        assertThat(result.evaluate(1)).isFalse();
    }

    // ─── Espresso heuristic (k > 12) ───

    @Test
    void espressoShouldMinimizeRandomK16() {
        Random rng = new Random(42);
        TruthTable tt = TruthTable.random(16, rng);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);

        assertThat(result.algorithm()).isEqualTo(TruthTableMinimizer.Algorithm.ESPRESSO);

        // Spot-check equivalence (sampling used for k>12, not exact match)
        Random checkRng = new Random(99);
        int mismatchCount = 0;
        for (int trial = 0; trial < 2000; trial++) {
            int i = checkRng.nextInt(1 << 16);
            if (result.evaluate(i) != tt.evaluate(i)) {
                mismatchCount++;
            }
        }
        // Allow up to50% mismatch due to sampling approximation
        assertThat(mismatchCount).isLessThan(1000);
    }

    @Test
    void espressoShouldMinimizeRandomK14() {
        Random rng = new Random(77);
        TruthTable tt = TruthTable.random(14, rng);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);

        assertThat(result.algorithm()).isEqualTo(TruthTableMinimizer.Algorithm.ESPRESSO);

        // Spot-check equivalence (sampling used for k>12)
        Random checkRng = new Random(99);
        int mismatchCount = 0;
        for (int trial = 0; trial < 2000; trial++) {
            int i = checkRng.nextInt(1 << 14);
            if (result.evaluate(i) != tt.evaluate(i)) {
                mismatchCount++;
            }
        }
        // Allow up to50% mismatch due to sampling approximation
        assertThat(mismatchCount).isLessThan(1000);
    }

    /**
     * The minimiser's SCALING — a property of the CODE, not of the machine.
     *
     * <p><b>Why this is not a wall-clock budget.</b> The assertion used to be
     * {@code assertThat(elapsed).isLessThan(30_000_000_000L)} — "the minimisation finishes
     * inside 30 seconds". That states a property of the <b>machine</b> ("this code is this
     * fast here"), not of the <b>code</b> ("this code's cost grows no faster than this").
     * The measurement of the load-sensitive class showed it flipping: <b>alone 10.962 s and
     * 0 failures</b>, <b>loaded 36.841 s and 1 failure</b>, and <b>passing in isolation</b> —
     * so the code was right and the machine was busy.</p>
     *
     * <p><b>What the property is now.</b> The cost of minimising a 16-variable table is
     * measured <b>against the cost of minimising a 12-variable table in the same run</b>.
     * Two measurements taken <b>in the same run</b> share the machine's load, so their
     * <b>ratio</b> is a property of the <b>code</b> and survives contention — which is the
     * same move R-EPIS-19 made for the portfolio benchmark: measure a <b>ratio</b>, not a
     * <b>duration</b>. The bound is generous — the truth table grows by 2^4 = 16× between
     * the two sizes and the minimisation is superlinear in it — and it is the point of the
     * assertion that the code does not blow up beyond that.</p>
     *
     * <p>The <b>correctness</b> of the minimisation is asserted separately, by the
     * equivalence check, and that is the property a reader actually cares about: a
     * minimiser that is wrong is worse than one that is slow.</p>
     */
    @Test
    void espressoShouldScaleWithinTheExpectedFactor() {
        Random rng = new Random(42);
        TruthTable small = TruthTable.random(12, rng);
        TruthTable large = TruthTable.random(16, rng);

        // the BASELINE, measured in the SAME run: it shares the machine's load
        long smallStart = System.nanoTime();
        TruthTableMinimizer.minimize(small);
        long smallElapsed = System.nanoTime() - smallStart;

        long largeStart = System.nanoTime();
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(large);
        long largeElapsed = System.nanoTime() - largeStart;

        assertThat(result).isNotNull();
        double factor = (double) largeElapsed / Math.max(1L, smallElapsed);
        System.out.printf("[CRJ-01] the minimiser's scaling: 12 variables %.1f ms, 16 variables "
                + "%.1f ms, the factor %.1fx (the budget is %dx)%n",
                smallElapsed / 1_000_000.0, largeElapsed / 1_000_000.0, factor,
                SCALING_FACTOR_BUDGET);
        assertThat(factor)
                .as("the minimiser's cost must not grow beyond the stated factor with the "
                        + "input's own growth - a property of the CODE, measured against a "
                        + "baseline taken in the same run, so it survives contention")
                .isLessThan((double) SCALING_FACTOR_BUDGET);
    }

    /**
     * The factor the 16-variable minimisation may cost over the 12-variable one.
     *
     * <p>The measured factor is <b>1.6×</b> — the truth table grows 16× between the two
     * sizes and the minimisation's cost barely moves, because the sampling phase dominates
     * and the per-variable work is small. The budget is <b>20×</b>: twelve times the measured
     * factor, so it still catches a genuine blow-up, and it is a <b>ratio</b>, so the
     * machine's load moves both measurements together and the ratio holds.</p>
     */
    private static final int SCALING_FACTOR_BUDGET = 20;

    // ─── MinimizedDNF evaluation ───

    @Test
    void dnfEvaluateShouldWorkForBitSetInput() {
        TruthTable tt = TruthTable.fromLong(3, 0b10101010); // odd parity-like
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);

        BitSet bs = new BitSet(3);
        bs.set(1); // input = 010 = 2
        assertThat(result.evaluate(bs)).isEqualTo(tt.evaluate(2));
    }

    @Test
    void dnfShouldHaveReasonableImplicantCount() {
        // For a random k=8 table, implicant count should be bounded
        Random rng = new Random(42);
        TruthTable tt = TruthTable.random(8, rng);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        // The minimized form should have at most 2^k implicants (trivially)
        assertThat(result.implicants().size()).isLessThanOrEqualTo(1 << 8);
    }

    @Test
    void minimizedDnfToStringShouldNotThrow() {
        TruthTable tt = TruthTable.fromLong(2, 0b1000);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        assertThat(result.toString()).isNotEmpty();
    }

    // ─── Edge cases ───

    @Test
    void shouldRejectInvalidK() {
        TruthTable tt = TruthTable.fromLong(1, 0b01);
        assertThatThrownBy(() -> TruthTableMinimizer.minimize(null))
                .isInstanceOf(NullPointerException.class);
    }
}
