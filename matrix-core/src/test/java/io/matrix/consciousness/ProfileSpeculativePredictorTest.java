package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileSpeculativePredictorTest {

    @Test
    void draftPredictWithSingleProfileReturnsIt() {
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        history.add(makeProfile(0.5));
        CognitiveGenesisProfile draft = ProfileSpeculativePredictor.draftPredict(history, 2);
        assertThat(draft).isEqualTo(history.get(0));
    }

    @Test
    void draftPredictLinearExtrapolation() {
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        history.add(makeProfile(0.2));
        history.add(makeProfile(0.5));
        CognitiveGenesisProfile draft = ProfileSpeculativePredictor.draftPredict(history, 2);
        // Delta = 0.3, predicted = 0.5 + 0.3 = 0.8
        assertThat(draft.phiBinary()).isEqualTo(0.8);
    }

    @Test
    void draftPredictNullHistoryReturnsNull() {
        assertThat(ProfileSpeculativePredictor.draftPredict(null, 2)).isNull();
    }

    @Test
    void speculateReturnsValidResult() {
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        history.add(makeProfile(0.2));
        history.add(makeProfile(0.5));
        CognitiveGenesisProfile actual = makeProfile(0.8);
        ProfileSpeculativePredictor.PredictionResult r =
            ProfileSpeculativePredictor.speculate(history, actual, 2, 0.5);
        assertThat(r.draft()).isNotNull();
        assertThat(r.similarity()).isGreaterThan(0.0);
    }

    @Test
    void speculativeAcceptanceWithLinearSequence() {
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        // Linear sequence: 0.0, 0.1, 0.2, 0.3, 0.4, 0.5
        for (int i = 0; i < 6; i++) history.add(makeProfile(i / 10.0));
        // Predict next: should match well
        ProfileSpeculativePredictor.PredictionResult r =
            ProfileSpeculativePredictor.speculate(history.subList(0, 5), makeProfile(0.5), 2, 0.5);
        // Linear sequence should predict well
        assertThat(r.similarity()).isGreaterThan(0.0);
    }

    @Test
    void acceptanceRateIsBounded() {
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) history.add(makeProfile(i / 10.0));
        double rate = ProfileSpeculativePredictor.acceptanceRate(history, 2, 0.5);
        assertThat(rate).isBetween(0.0, 1.0 + 1e-9);
    }

    @Test
    void emptyHistoryReturnsZeroAcceptance() {
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        assertThat(ProfileSpeculativePredictor.acceptanceRate(history, 2, 0.5)).isEqualTo(0.0);
    }

    @Test
    void nullActualGivesZeroSimilarity() {
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        history.add(makeProfile(0.2));
        history.add(makeProfile(0.5));
        ProfileSpeculativePredictor.PredictionResult r =
            ProfileSpeculativePredictor.speculate(history, null, 2, 0.5);
        assertThat(r.accepted()).isFalse();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
