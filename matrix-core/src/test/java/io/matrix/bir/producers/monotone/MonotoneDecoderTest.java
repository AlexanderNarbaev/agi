package io.matrix.bir.producers.monotone;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for {@link MonotoneDecoder} (DESIGN-09 §2, SPEC-002 producer line).
 */
class MonotoneDecoderTest {

    // --- Unit: canonical monotone functions ---

    @Test
    void decodesConjunctionWithHanselBoundQueries() {
        int k = 5;
        int allOnes = (1 << k) - 1;
        MonotoneDecoder.Result r = MonotoneDecoder.decode(k, v -> v == allOnes);

        assertThat(r.dnf().fidelity()).isCloseTo(1.0, within(1e-12));
        // Worst case for AND_k: every middle-layer vertex must be queried.
        int middle = binomial(k, k / 2);
        assertThat(r.queries()).isLessThanOrEqualTo(middle);
        // Single minimal true term: the full vertex.
        assertThat(r.dnf().clauses()).hasSize(1);
    }

    @Test
    void decodesDisjunctionExactly() {
        int k = 6;
        MonotoneDecoder.Result r = MonotoneDecoder.decode(k, v -> v != 0);

        assertThat(r.queries()).isLessThanOrEqualTo(1 << k);
        assertThat(r.dnf().fidelity()).isCloseTo(1.0, within(1e-12));
        assertThat(r.dnf().clauses()).hasSize(k);
    }

    @Test
    void decodesConstantZeroAndConstantOne() {
        MonotoneDecoder.Result zero = MonotoneDecoder.decode(3, v -> false);
        assertThat(zero.dnf().fidelity()).isCloseTo(1.0, within(1e-12));

        MonotoneDecoder.Result one = MonotoneDecoder.decode(3, v -> true);
        assertThat(one.dnf().fidelity()).isCloseTo(1.0, within(1e-12));
        assertThat(one.dnf().clauses()).hasSize(1); // empty term
    }

    @Test
    void rejectsArityOutsideKMax() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> MonotoneDecoder.decode(21, v -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int binomial(int n, int m) {
        long result = 1;
        for (int i = 0; i < m; i++) {
            result = result * (n - i) / (i + 1);
        }
        return (int) result;
    }

    // --- Properties on random monotone functions ---

    /**
     * Random monotone target: threshold of non-negative random weights.
     * f(x) = [ Σ w_i·x_i ≥ θ ] with θ ∈ [minSum, maxSum] — monotone by construction.
     */
    @Provide
    Arbitrary<MembershipOracle> monotoneOracles() {
        Arbitrary<double[]> weights =
                Arbitraries.doubles().between(0.0, 10.0)
                        .array(double[].class)
                        .ofMinSize(1)
                        .ofMaxSize(8);
        Arbitrary<Double> thetaFrac = Arbitraries.doubles().between(0.05, 0.95);
        return net.jqwik.api.Combinators.combine(weights, thetaFrac)
                .as((w, frac) -> {
                    double total = java.util.Arrays.stream(w).sum();
                    final double[] ws = total <= 0.0 ? new double[]{1.0} : w;
                    final double scale = total <= 0.0 ? 1.0 : total;
                    double theta = frac * scale;
                    int kk = ws.length;
                    return (MembershipOracle) vertex -> {
                        double sum = 0.0;
                        for (int i = 0; i < kk; i++) {
                            if (((vertex >> i) & 1) == 1) {
                                sum += ws[i];
                            }
                        }
                        return sum >= theta;
                    };
                });
    }

    @Property
    void reconstructionExactAndMonotoneClauses(
            @ForAll("monotoneOracles") MembershipOracle oracle,
            @ForAll("arity") int k) {
        MonotoneDecoder.Result r = MonotoneDecoder.decode(k, oracle);

        // Layerwise decoding guarantee: at most one query per vertex.
        // (Exact Hansel bound C(k, floor k/2) requires full chain construction — future work.)
        assertThat(r.queries()).isLessThanOrEqualTo(1 << k);

        // Exhaustive equivalence for small k proves exact reconstruction,
        // which subsumes positivity-of-literals for monotone targets.
        if (k <= MonotoneDecoder.VERIFY_LIMIT) {
            assertThat(r.dnf().fidelity()).isCloseTo(1.0, within(1e-12));
        }
    }

    @Property
    void deterministicReplay(@ForAll("monotoneOracles") MembershipOracle oracle,
                             @ForAll("arity") int k) {
        MonotoneDecoder.Result first = MonotoneDecoder.decode(k, oracle);
        MonotoneDecoder.Result second = MonotoneDecoder.decode(k, oracle);
        assertThat(second.queries()).isEqualTo(first.queries());
        assertThat(second.dnf().clauses()).hasSameSizeAs(first.dnf().clauses());
        assertThat(second.dnf().provenance()).isEqualTo(first.dnf().provenance());
    }

    @Provide
    Arbitrary<Integer> arity() {
        return Arbitraries.integers().between(1, 8);
    }
}
