package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W308 — Adaptive compute integration test.
 *
 * <p>Combines W303-W306 to demonstrate full adaptive compute pipeline.
 */
class AdaptiveComputeIntegrationTest {

    @Test
    void fullPipelineTestTimeComputeThenEarlyExit() {
        // 1. Test-time compute to find good candidate
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 3);
        CognitiveTestTimeCompute.TestTimeResult ttcResult =
            ttc.solve(makeProfile(0.5), 3);

        // 2. Apply early exit
        if (ttcResult.bestAttempt() != null) {
            CognitiveGenesisProfile candidate =
                ttcResult.bestAttempt().trajectory().get(
                    ttcResult.bestAttempt().trajectory().size() - 1);

            CognitiveEarlyExit.EarlyExitResult eeResult =
                CognitiveEarlyExit.process(candidate, 3, 0.7, null, p -> p.phiBinary());
            assertThat(eeResult.layersUsed()).isLessThanOrEqualTo(3);
        }
    }

    @Test
    void pipelineMultiTokenThenAdaptive() {
        // 1. Multi-token prediction
        CognitiveMultiTokenPrediction.MultiTokenPrediction mtp =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), 3, 42L);

        // 2. Apply adaptive compute to all predictions
        List<CognitiveGenesisProfile> predictions = mtp.predictions();
        CognitiveAdaptiveCompute.AdaptiveResult adaptResult =
            CognitiveAdaptiveCompute.process(predictions, 5, 0.5);

        assertThat(adaptResult.processedProfiles()).isNotEmpty();
        assertThat(adaptResult.speedup()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void pipelineWithAcceptanceRate() {
        // Multi-token + verify with acceptance rate
        CognitiveMultiTokenPrediction.MultiTokenPrediction mtp =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), 10, 42L);

        List<CognitiveGenesisProfile> accepted =
            CognitiveMultiTokenPrediction.verify(
                mtp.predictions(), p -> p.phiBinary() > 0.5);

        double rate = CognitiveMultiTokenPrediction.acceptanceRate(
            mtp.predictions().size(), accepted.size());
        assertThat(rate).isBetween(0.0, 1.0);
    }

    @Test
    void computeSavedCalculated() {
        List<CognitiveEarlyExit.EarlyExitResult> results = new ArrayList<>();
        // Mix of early and late exits
        for (int i = 0; i < 5; i++) {
            results.add(new CognitiveEarlyExit.EarlyExitResult(
                makeProfile(0.9), 1, 0.9, true, new ArrayList<>()));
        }
        for (int i = 0; i < 5; i++) {
            results.add(new CognitiveEarlyExit.EarlyExitResult(
                makeProfile(0.3), 5, 0.3, false, new ArrayList<>()));
        }
        double saved = CognitiveEarlyExit.computeSaved(results, 5);
        // Mix should give ~50% savings
        assertThat(saved).isBetween(0.3, 0.7);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
