package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveMultiTokenPredictionTest {

    @Test
    void nullReturnsEmpty() {
        CognitiveMultiTokenPrediction.MultiTokenPrediction r =
            CognitiveMultiTokenPrediction.predict(null, 3, 42L);
        assertThat(r.predictions()).isEmpty();
    }

    @Test
    void zeroKReturnsEmpty() {
        CognitiveMultiTokenPrediction.MultiTokenPrediction r =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), 0, 42L);
        assertThat(r.predictions()).isEmpty();
    }

    @Test
    void kPredictions() {
        CognitiveMultiTokenPrediction.MultiTokenPrediction r =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), 5, 42L);
        assertThat(r.predictions().size()).isEqualTo(5);
        assertThat(r.confidenceScores().size()).isEqualTo(5);
    }

    @Test
    void averageConfidenceBounded() {
        CognitiveMultiTokenPrediction.MultiTokenPrediction r =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), 5, 42L);
        if (!Double.isNaN(r.averageConfidence())) {
            assertThat(r.averageConfidence()).isBetween(0.0, 1.0 + 1e-9);
        }
    }

    @Test
    void confidenceDecreasesWithDistance() {
        CognitiveMultiTokenPrediction.MultiTokenPrediction r =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), 10, 42L);
        // First few should have higher confidence than later ones
        assertThat(r.confidenceScores().get(0))
            .isGreaterThan(r.confidenceScores().get(r.confidenceScores().size() - 1));
    }

    @Test
    void verifyFiltersPredictions() {
        List<CognitiveGenesisProfile> predictions = new ArrayList<>();
        predictions.add(makeProfile(0.7));
        predictions.add(makeProfile(0.3));
        List<CognitiveGenesisProfile> accepted =
            CognitiveMultiTokenPrediction.verify(predictions, p -> p.phiBinary() > 0.5);
        assertThat(accepted.size()).isEqualTo(1);
    }

    @Test
    void verifyNullVerifierAcceptsAll() {
        List<CognitiveGenesisProfile> predictions = new ArrayList<>();
        predictions.add(makeProfile(0.7));
        predictions.add(makeProfile(0.3));
        List<CognitiveGenesisProfile> accepted =
            CognitiveMultiTokenPrediction.verify(predictions, null);
        assertThat(accepted.size()).isEqualTo(2);
    }

    @Test
    void acceptanceRateCorrect() {
        assertThat(CognitiveMultiTokenPrediction.acceptanceRate(10, 5)).isEqualTo(0.5);
        assertThat(CognitiveMultiTokenPrediction.acceptanceRate(0, 0)).isEqualTo(0.0);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
