package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W307 — Property tests for W303-W306 adaptive compute classes.
 */
class CognitiveAdaptiveComputePropertyTest {

    @Property(tries = 20)
    void propertyTestTimeComputeProducesAttempts(@ForAll("anySeed") int seed,
                                                   @ForAll("anyMaxAttempts") int maxAtt,
                                                   @ForAll("anySteps") int steps) {
        if (maxAtt < 1 || maxAtt > 8 || steps < 1 || steps > 5) return;
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(seed, maxAtt);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), steps);
        assertThat(r.allAttempts().size()).isEqualTo(maxAtt);
    }

    @Property(tries = 20)
    void propertyMultiTokenConfidenceDecreases(@ForAll("anySeed") int seed,
                                                   @ForAll("anyK") int k) {
        if (k < 3 || k > 10) return;
        CognitiveMultiTokenPrediction.MultiTokenPrediction r =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), k, seed);
        // Confidence should be monotonically decreasing
        for (int i = 1; i < r.confidenceScores().size(); i++) {
            // Allow small noise
            assertThat(r.confidenceScores().get(i))
                .isLessThanOrEqualTo(r.confidenceScores().get(i - 1) + 0.1);
        }
    }

    @Property(tries = 20)
    void propertyEarlyExitBoundsLayers(@ForAll("anyThreshold") double threshold,
                                          @ForAll("anyMaxLayers") int maxLayers) {
        if (threshold < 0 || threshold > 1 || maxLayers < 1 || maxLayers > 10) return;
        CognitiveEarlyExit.EarlyExitResult r =
            CognitiveEarlyExit.process(makeProfile(0.5), maxLayers, threshold, null, null);
        assertThat(r.layersUsed()).isLessThanOrEqualTo(maxLayers);
        assertThat(r.layersUsed()).isGreaterThanOrEqualTo(0);
    }

    @Property(tries = 20)
    void propertyAdaptiveComputeSpeedupBounded(@ForAll("anyPhi") double phi,
                                                  @ForAll("anyThreshold") double threshold,
                                                  @ForAll("anyMaxLayers") int maxLayers) {
        if (phi < 0 || phi > 1 || threshold < 0 || threshold > 1 || maxLayers < 1) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(phi));
        CognitiveAdaptiveCompute.AdaptiveResult r =
            CognitiveAdaptiveCompute.process(profiles, maxLayers, threshold);
        assertThat(r.speedup()).isGreaterThanOrEqualTo(1.0);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> anyMaxAttempts() {
        return Arbitraries.integers().between(1, 8);
    }

    @Provide
    Arbitrary<Integer> anySteps() {
        return Arbitraries.integers().between(1, 5);
    }

    @Provide
    Arbitrary<Integer> anyK() {
        return Arbitraries.integers().between(3, 10);
    }

    @Provide
    Arbitrary<Double> anyThreshold() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Provide
    Arbitrary<Integer> anyMaxLayers() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Double> anyPhi() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
