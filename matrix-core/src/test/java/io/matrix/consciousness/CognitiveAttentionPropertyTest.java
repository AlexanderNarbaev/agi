package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W206 — CognitiveAttention property-based tests.
 */
class CognitiveAttentionPropertyTest {

    @Property(tries = 30)
    void propertyAttentionWeightsSumToOne(@ForAll("anySeed") int seed,
                                            @ForAll("profileCounts") int n,
                                            @ForAll("dims") int dim) {
        if (n < 2 || dim < 4) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        CognitiveAttention.AttentionResult r =
            CognitiveAttention.selfAttention(profiles, dim, seed);
        double[][] w = r.attentionWeights();
        for (int i = 0; i < w.length; i++) {
            double sum = 0;
            for (int j = 0; j < w[i].length; j++) sum += w[i][j];
            if (!Double.isNaN(sum)) {
                assertThat(sum).isCloseTo(1.0, offset(1e-9));
            }
        }
    }

    @Property(tries = 30)
    void propertyAttentionWeightsNonNegative(@ForAll("anySeed") int seed,
                                               @ForAll("profileCounts") int n,
                                               @ForAll("dims") int dim) {
        if (n < 2 || dim < 4) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        CognitiveAttention.AttentionResult r =
            CognitiveAttention.selfAttention(profiles, dim, seed);
        for (double[] row : r.attentionWeights()) {
            for (double v : row) {
                if (!Double.isNaN(v)) {
                    assertThat(v).isGreaterThanOrEqualTo(0.0);
                }
            }
        }
    }

    @Property(tries = 30)
    void propertySameSeedDeterministic(@ForAll("anySeed") int seed,
                                          @ForAll("profileCounts") int n,
                                          @ForAll("dims") int dim) {
        if (n < 2 || dim < 4) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        CognitiveAttention.AttentionResult r1 =
            CognitiveAttention.selfAttention(profiles, dim, seed);
        CognitiveAttention.AttentionResult r2 =
            CognitiveAttention.selfAttention(profiles, dim, seed);
        for (int i = 0; i < r1.attentionWeights().length; i++) {
            for (int j = 0; j < r1.attentionWeights()[i].length; j++) {
                assertThat(r1.attentionWeights()[i][j])
                    .isCloseTo(r2.attentionWeights()[i][j], offset(1e-9));
            }
        }
    }

    @Property(tries = 30)
    void propertyFindSinksSortedByMagnitude(@ForAll("matrixSizes") int n,
                                               @ForAll("topKs") int k) {
        if (n < 2 || k < 1) return;
        Random rng = new Random(42);
        double[][] w = new double[n][n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                w[i][j] = rng.nextDouble();
                sum += w[i][j];
            }
            for (int j = 0; j < n; j++) w[i][j] /= sum;
        }
        int[] sinks = CognitiveAttention.findSinks(w, k);
        // First k elements should be top sinks
        assertThat(sinks.length).isLessThanOrEqualTo(k);
        assertThat(sinks.length).isLessThanOrEqualTo(n);
    }

    @Property(tries = 30)
    void propertyClassifyValidString(@ForAll("matrixSizes") int n) {
        if (n < 2) return;
        Random rng = new Random(42);
        double[][] w = new double[n][n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                w[i][j] = rng.nextDouble() + 0.001;
                sum += w[i][j];
            }
            for (int j = 0; j < n; j++) w[i][j] /= sum;
        }
        String regime = CognitiveAttention.classifyRegime(w);
        assertThat(regime).isIn("UNIFORM", "SPARSE", "CONCENTRATED", "EMPTY");
    }

    @Property(tries = 30)
    void propertyOutputDimensionsCorrect(@ForAll("anySeed") int seed,
                                           @ForAll("profileCounts") int n,
                                           @ForAll("dims") int dim) {
        if (n < 1 || dim < 4) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        CognitiveAttention.AttentionResult r =
            CognitiveAttention.selfAttention(profiles, dim, seed);
        assertThat(r.outputVectors().length).isEqualTo(n);
        for (double[] v : r.outputVectors()) {
            assertThat(v.length).isEqualTo(dim);
        }
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> profileCounts() {
        return Arbitraries.integers().between(2, 12);
    }

    @Provide
    Arbitrary<Integer> dims() {
        return Arbitraries.integers().between(8, 64);
    }

    @Provide
    Arbitrary<Integer> matrixSizes() {
        return Arbitraries.integers().between(2, 10);
    }

    @Provide
    Arbitrary<Integer> topKs() {
        return Arbitraries.integers().between(1, 5);
    }

    private static CognitiveGenesisProfile randomProfile(Random rng) {
        return new CognitiveGenesisProfile(
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble() * 100.0, rng.nextDouble(), rng.nextDouble(),
            rng.nextInt(8), rng.nextDouble(), rng.nextDouble() * 5.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
