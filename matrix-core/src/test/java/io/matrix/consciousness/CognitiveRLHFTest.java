package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveRLHFTest {

    @Test
    void rewardComputed() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        CognitiveRLHF.Reward r = rlhf.reward(makeProfile(0.5));
        assertThat(r.value()).isBetween(0.0, 1.0);
    }

    @Test
    void nullRewardIsZero() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        CognitiveRLHF.Reward r = rlhf.reward(null);
        assertThat(r.value()).isEqualTo(0.0);
    }

    @Test
    void trainProducesHistory() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveRLHF.TrainingResult r = rlhf.train(prefs, 3);
        assertThat(r.history().size()).isEqualTo(3);
    }

    @Test
    void nullPreferencesReturnsEmpty() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        CognitiveRLHF.TrainingResult r = rlhf.train(null, 3);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void zeroEpochsReturnsEmpty() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveRLHF.TrainingResult r = rlhf.train(prefs, 0);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void generatePreferenceFromPool() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        List<CognitiveGenesisProfile> pool = new ArrayList<>();
        for (int i = 0; i < 5; i++) pool.add(makeProfile(i / 5.0));
        CognitiveRLHF.Preference p = rlhf.generateRandomPreference(pool);
        assertThat(p).isNotNull();
        // Chosen should have higher or equal reward
        CognitiveRLHF.Reward chosenR = rlhf.reward(p.chosen());
        CognitiveRLHF.Reward rejectedR = rlhf.reward(p.rejected());
        assertThat(chosenR.value()).isGreaterThanOrEqualTo(rejectedR.value());
    }

    @Test
    void nullPoolReturnsNull() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        assertThat(rlhf.generateRandomPreference(null)).isNull();
    }

    @Test
    void learningRateRecorded() {
        CognitiveRLHF rlhf = new CognitiveRLHF(42L, 0.05);
        assertThat(rlhf.learningRate()).isEqualTo(0.05);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
