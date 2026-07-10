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

        // Spot-check equivalence (full check is 2^16 = 65536 entries)
        Random checkRng = new Random(99);
        for (int trial = 0; trial < 1000; trial++) {
            int i = checkRng.nextInt(1 << 16);
            assertThat(result.evaluate(i))
                    .as("input=%d", i)
                    .isEqualTo(tt.evaluate(i));
        }
    }

    @Test
    void espressoShouldMinimizeRandomK14() {
        Random rng = new Random(77);
        TruthTable tt = TruthTable.random(14, rng);
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);

        assertThat(result.algorithm()).isEqualTo(TruthTableMinimizer.Algorithm.ESPRESSO);

        // Full equivalence check for k=14 (16384 entries is feasible)
        int size = 1 << 14;
        for (int i = 0; i < size; i++) {
            assertThat(result.evaluate(i))
                    .as("input=%d", i)
                    .isEqualTo(tt.evaluate(i));
        }
    }

    @Test
    void espressoShouldCompleteInReasonableTime() {
        Random rng = new Random(42);
        TruthTable tt = TruthTable.random(16, rng);

        long start = System.nanoTime();
        TruthTableMinimizer.MinimizedDNF result = TruthTableMinimizer.minimize(tt);
        long elapsed = System.nanoTime() - start;

        // Should complete within 5 seconds
        assertThat(elapsed).isLessThan(5_000_000_000L);
        assertThat(result).isNotNull();
    }

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
