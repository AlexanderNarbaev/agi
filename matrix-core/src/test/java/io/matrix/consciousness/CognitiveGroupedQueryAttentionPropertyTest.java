package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveGroupedQueryAttentionPropertyTest {

    @Property(tries = 30)
    void propertyCompressionRatio(@ForAll("anySeed") int seed,
                                    @ForAll("anyQueryHeads") int qh,
                                    @ForAll("anyKVHeads") int kvh) {
        if (qh < 1 || kvh < 1 || qh % kvh != 0) return;
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, qh, kvh, seed);
        assertThat(gqa.compressionRatio()).isEqualTo((double) qh / kvh);
    }

    @Property(tries = 30)
    void propertyAttendPreservesDimensions(@ForAll("anySeed") int seed,
                                              @ForAll("anyDim") int dim) {
        if (dim < 1 || dim % 8 != 0) return;
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(dim, 8, 2, seed);
        double[] v = new double[dim];
        for (int i = 0; i < dim; i++) v[i] = i / dim;
        double[] result = gqa.attend(v);
        assertThat(result.length).isEqualTo(dim);
    }

    @Property(tries = 20)
    void propertySameSeedDeterministic(@ForAll("anySeed") int seed,
                                          @ForAll("anyDim") int dim) {
        if (dim < 1 || dim % 8 != 0) return;
        CognitiveGroupedQueryAttention g1 =
            new CognitiveGroupedQueryAttention(dim, 8, 2, seed);
        CognitiveGroupedQueryAttention g2 =
            new CognitiveGroupedQueryAttention(dim, 8, 2, seed);
        double[] v = new double[dim];
        for (int i = 0; i < dim; i++) v[i] = i / dim;
        double[] r1 = g1.attend(v);
        double[] r2 = g2.attend(v);
        for (int i = 0; i < dim; i++) {
            assertThat(r1[i]).isCloseTo(r2[i], offset(1e-9));
        }
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> anyQueryHeads() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Integer> anyKVHeads() {
        return Arbitraries.integers().between(1, 8);
    }

    @Provide
    Arbitrary<Integer> anyDim() {
        return Arbitraries.integers().between(8, 256);
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
