package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveDPOTest {

    @Test
    void lossNonNegative() {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        double loss = dpo.loss(Math.log(0.8), Math.log(0.2),
                                Math.log(0.5), Math.log(0.5));
        assertThat(loss).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void accuracyBounded() {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        assertThat(dpo.accuracy(1.0, 0.0)).isEqualTo(1.0);
        assertThat(dpo.accuracy(0.0, 1.0)).isEqualTo(0.0);
    }

    @Test
    void trainProducesHistory() {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveDPO.DPOResult r = dpo.train(prefs, 3);
        assertThat(r.history().size()).isEqualTo(3);
    }

    @Test
    void nullPreferencesReturnsEmpty() {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        CognitiveDPO.DPOResult r = dpo.train(null, 3);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void zeroEpochsReturnsEmpty() {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveDPO.DPOResult r = dpo.train(prefs, 0);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void finalLossComputed() {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveDPO.DPOResult r = dpo.train(prefs, 2);
        assertThat(r.finalLoss()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void finalAccuracyBounded() {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveDPO.DPOResult r = dpo.train(prefs, 2);
        assertThat(r.finalAccuracy()).isBetween(0.0, 1.0 + 1e-9);
    }

    @Test
    void betaRecorded() {
        CognitiveDPO dpo = new CognitiveDPO(42L, 0.5);
        assertThat(dpo.beta()).isEqualTo(0.5);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
