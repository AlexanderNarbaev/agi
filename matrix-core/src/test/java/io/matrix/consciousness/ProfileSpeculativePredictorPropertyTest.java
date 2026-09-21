package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W212 — ProfileSpeculativePredictor property-based tests.
 */
class ProfileSpeculativePredictorPropertyTest {

    @Property(tries = 30)
    void propertyDraftPredictBoundedPhi(@ForAll("anySeed") int seed,
                                          @ForAll("historySizes") int n,
                                          @ForAll("kValues") int k) {
        if (n < 2 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        for (int i = 0; i < n; i++) history.add(randomProfile(rng));
        CognitiveGenesisProfile draft = ProfileSpeculativePredictor.draftPredict(history, k);
        assertThat(draft).isNotNull();
        assertThat(draft.phiBinary()).isBetween(-10.0, 10.0);
    }

    @Property(tries = 30)
    void propertyAcceptanceRateBounded(@ForAll("anySeed") int seed,
                                          @ForAll("historySizes") int n,
                                          @ForAll("kValues") int k) {
        if (n < k + 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        for (int i = 0; i < n; i++) history.add(randomProfile(rng));
        double rate = ProfileSpeculativePredictor.acceptanceRate(history, k, 0.5);
        assertThat(rate).isBetween(0.0, 1.0 + 1e-9);
    }

    @Property(tries = 30)
    void propertySimilarityBounded(@ForAll("anySeed") int seed,
                                     @ForAll("historySizes") int n,
                                     @ForAll("kValues") int k) {
        if (n < k + 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        for (int i = 0; i < n; i++) history.add(randomProfile(rng));
        CognitiveGenesisProfile actual = history.get(n - 1);
        List<CognitiveGenesisProfile> past = history.subList(0, n - 1);
        ProfileSpeculativePredictor.PredictionResult r =
            ProfileSpeculativePredictor.speculate(past, actual, k, 0.5);
        if (!Double.isNaN(r.similarity())) {
            assertThat(Math.abs(r.similarity())).isLessThanOrEqualTo(1.0 + 1e-9);
        }
    }

    @Property(tries = 30)
    void propertyEmptyHistoryZeroAcceptance() {
        assertThat(ProfileSpeculativePredictor.acceptanceRate(new ArrayList<>(), 2, 0.5))
            .isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyThresholdMonotonic(@ForAll("anySeed") int seed,
                                       @ForAll("historySizes") int n) {
        if (n < 5) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        for (int i = 0; i < n; i++) history.add(randomProfile(rng));
        double highThreshold = ProfileSpeculativePredictor.acceptanceRate(history, 2, 0.99);
        double lowThreshold = ProfileSpeculativePredictor.acceptanceRate(history, 2, 0.01);
        // Higher threshold → lower or equal acceptance
        assertThat(highThreshold).isLessThanOrEqualTo(lowThreshold + 1e-9);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> historySizes() {
        return Arbitraries.integers().between(2, 16);
    }

    @Provide
    Arbitrary<Integer> kValues() {
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
}
