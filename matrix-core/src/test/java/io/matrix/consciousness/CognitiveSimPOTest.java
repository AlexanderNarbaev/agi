package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveSimPOTest {

    @Test
    void lossNonNegative() {
        CognitiveSimPO simpo = new CognitiveSimPO(42L);
        double loss = simpo.loss(Math.log(0.8), Math.log(0.2), 1.0);
        assertThat(loss).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void trainProducesHistory() {
        CognitiveSimPO simpo = new CognitiveSimPO(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveSimPO.SimPOResult r = simpo.train(prefs, 3);
        assertThat(r.history().size()).isEqualTo(3);
    }

    @Test
    void nullReturnsEmpty() {
        CognitiveSimPO simpo = new CognitiveSimPO(42L);
        CognitiveSimPO.SimPOResult r = simpo.train(null, 3);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void finalLossComputed() {
        CognitiveSimPO simpo = new CognitiveSimPO(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveSimPO.SimPOResult r = simpo.train(prefs, 2);
        assertThat(r.finalLoss()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void betaAndGammaRecorded() {
        CognitiveSimPO simpo = new CognitiveSimPO(42L, 3.0, 2.0);
        assertThat(simpo.beta()).isEqualTo(3.0);
        assertThat(simpo.gamma()).isEqualTo(2.0);
    }

    @Test
    void lossWithDifferentSeqLength() {
        CognitiveSimPO simpo = new CognitiveSimPO(42L);
        double loss1 = simpo.loss(Math.log(0.8), Math.log(0.2), 1.0);
        double loss2 = simpo.loss(Math.log(0.8), Math.log(0.2), 10.0);
        // Different seq length should give different loss
        assertThat(loss1).isNotEqualTo(loss2);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
