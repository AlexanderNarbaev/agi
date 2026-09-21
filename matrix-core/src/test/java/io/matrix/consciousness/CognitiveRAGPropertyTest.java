package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W214 — CognitiveRAG property-based tests.
 */
class CognitiveRAGPropertyTest {

    @Property(tries = 30)
    void propertyTopKRespectsLimit(@ForAll("anySeed") int seed,
                                      @ForAll("kbSizes") int n,
                                      @ForAll("kValues") int k) {
        if (n < 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < n; i++) kb.add(randomProfile(rng));
        CognitiveGenesisProfile q = randomProfile(rng);
        int[] top = CognitiveRAG.topK(q, kb, k);
        int expected = Math.min(k, n);
        assertThat(top.length).isEqualTo(expected);
    }

    @Property(tries = 30)
    void propertyTopKReturnsValidIndices(@ForAll("anySeed") int seed,
                                            @ForAll("kbSizes") int n,
                                            @ForAll("kValues") int k) {
        if (n < 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < n; i++) kb.add(randomProfile(rng));
        CognitiveGenesisProfile q = randomProfile(rng);
        int[] top = CognitiveRAG.topK(q, kb, k);
        for (int idx : top) {
            assertThat(idx).isBetween(0, n - 1);
        }
    }

    @Property(tries = 30)
    void propertyRetrievalValidIndices(@ForAll("anySeed") int seed,
                                          @ForAll("kbSizes") int n,
                                          @ForAll("kValues") int k) {
        if (n < 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < n; i++) kb.add(randomProfile(rng));
        CognitiveRAG.RetrievalResult r =
            CognitiveRAG.retrieveAndAugment(randomProfile(rng), kb, k, 0.5);
        for (int idx : r.retrievedIndices()) {
            assertThat(idx).isBetween(0, n - 1);
        }
    }

    @Property(tries = 30)
    void propertyAvgSimilarityBounded(@ForAll("anySeed") int seed,
                                         @ForAll("kbSizes") int n,
                                         @ForAll("kValues") int k) {
        if (n < 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < n; i++) kb.add(randomProfile(rng));
        CognitiveRAG.RetrievalResult r =
            CognitiveRAG.retrieveAndAugment(randomProfile(rng), kb, k, 0.5);
        if (!Double.isNaN(r.avgSimilarity())) {
            assertThat(Math.abs(r.avgSimilarity())).isLessThanOrEqualTo(1.0 + 1e-9);
        }
    }

    @Property(tries = 30)
    void propertyMixingZeroPreservesQuery(@ForAll("anySeed") int seed,
                                            @ForAll("kbSizes") int n,
                                            @ForAll("kValues") int k) {
        if (n < 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < n; i++) kb.add(randomProfile(rng));
        CognitiveGenesisProfile q = randomProfile(rng);
        CognitiveRAG.RetrievalResult r =
            CognitiveRAG.retrieveAndAugment(q, kb, k, 0.0);
        // Mixing 0 → query preserved
        assertThat(r.augmentedProfile().phiBinary()).isCloseTo(q.phiBinary(), offset(1e-9));
    }

    @Property(tries = 30)
    void propertyEmptyKBReturnsQuery(@ForAll("anySeed") int seed,
                                        @ForAll("kValues") int k) {
        if (k < 1) return;
        Random rng = new Random(seed);
        CognitiveGenesisProfile q = randomProfile(rng);
        CognitiveRAG.RetrievalResult r =
            CognitiveRAG.retrieveAndAugment(q, new ArrayList<>(), k, 0.5);
        assertThat(r.augmentedProfile()).isEqualTo(q);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> kbSizes() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Integer> kValues() {
        return Arbitraries.integers().between(1, 8);
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
