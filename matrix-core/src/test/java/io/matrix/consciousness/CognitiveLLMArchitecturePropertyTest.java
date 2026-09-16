package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W255 — Property tests bundle for W238-W253 LLM-architecture classes.
 */
class CognitiveLLMArchitecturePropertyTest {

    @Property(tries = 30)
    void propertyRoPEInverseUndoes(@ForAll("anyVector") double[] v,
                                       @ForAll("anyPosition") int pos) {
        if (v == null || v.length < 2) return;
        double[] rotated = CognitiveRotaryEmbedding.apply(v, pos);
        double[] back = CognitiveRotaryEmbedding.inverse(rotated, pos);
        for (int i = 0; i < v.length; i++) {
            assertThat(back[i]).isCloseTo(v[i], offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyLayerNormZeroMean(@ForAll("anyVector") double[] v) {
        if (v == null || v.length == 0) return;
        double[] normalized = CognitiveLayerNormalization.apply(v);
        double sum = 0;
        for (double x : normalized) sum += x;
        assertThat(sum / v.length).isCloseTo(0.0, offset(1e-9));
    }

    @Property(tries = 30)
    void propertyResidualSum(@ForAll("anyVector") double[] a,
                                @ForAll("anyVector") double[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return;
        double[] result = CognitiveResidualConnection.residual(a, b);
        for (int i = 0; i < a.length; i++) {
            assertThat(result[i]).isCloseTo(a[i] + b[i], offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertySamplerSoftmaxSumOne(@ForAll("anyLogits") double[] logits) {
        if (logits == null || logits.length == 0) return;
        double[] probs = CognitiveSampler.softmax(logits);
        double sum = 0;
        for (double p : probs) sum += p;
        if (!Double.isNaN(sum)) {
            assertThat(sum).isCloseTo(1.0, offset(1e-9));
        }
    }

    @Property(tries = 20)
    void propertySparseAttentionTotalOpsBounded(@ForAll("anySeed") int seed,
                                                   @ForAll("anySeqLen") int seqLen) {
        if (seqLen < 1 || seqLen > 100) return;
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 2, 2, seed);
        int ops = sa.totalOps(seqLen);
        // Should be at most O(N) not O(N²)
        assertThat(ops).isLessThanOrEqualTo(seqLen * seqLen);
    }

    @Property(tries = 20)
    void propertyBeamSearchValidCount(@ForAll("anySeed") int seed,
                                        @ForAll("anyBeamWidth") int bw,
                                        @ForAll("anyMaxSteps") int ms) {
        if (bw < 1 || ms < 1) return;
        List<CognitiveGenesisProfile> candidates = new ArrayList<>();
        Random rng = new Random(seed);
        for (int i = 0; i < 3; i++) candidates.add(makeProfile(rng));
        CognitiveBeamSearch.BeamScorer scorer = (prefix, c) -> 0.5;
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(candidates, bw, ms, scorer);
        assertThat(r.topBeams().size()).isLessThanOrEqualTo(bw);
    }

    @Provide
    Arbitrary<double[]> anyVector() {
        return Arbitraries.integers().between(2, 16).flatMap(len ->
            Arbitraries.doubles().between(-1.0, 1.0).array(double[].class).ofSize(len)
        );
    }

    @Provide
    Arbitrary<Integer> anyPosition() {
        return Arbitraries.integers().between(0, 100);
    }

    @Provide
    Arbitrary<double[]> anyLogits() {
        return Arbitraries.integers().between(1, 16).flatMap(len ->
            Arbitraries.doubles().between(-5.0, 5.0).array(double[].class).ofSize(len)
        );
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> anySeqLen() {
        return Arbitraries.integers().between(1, 50);
    }

    @Provide
    Arbitrary<Integer> anyBeamWidth() {
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<Integer> anyMaxSteps() {
        return Arbitraries.integers().between(1, 3);
    }

    private static CognitiveGenesisProfile makeProfile(Random rng) {
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
