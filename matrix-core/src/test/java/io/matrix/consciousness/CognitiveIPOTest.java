package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveIPOTest {

    @Test
    void lossNonNegative() {
        CognitiveIPO ipo = new CognitiveIPO(0.1);
        double loss = ipo.loss(Math.log(0.8), Math.log(0.2),
                                Math.log(0.5), Math.log(0.5));
        assertThat(loss).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void trainProducesHistory() {
        CognitiveIPO ipo = new CognitiveIPO(0.1);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveIPO.IPOResult r = ipo.train(prefs, 3);
        assertThat(r.history().size()).isEqualTo(3);
    }

    @Test
    void nullReturnsEmpty() {
        CognitiveIPO ipo = new CognitiveIPO(0.1);
        CognitiveIPO.IPOResult r = ipo.train(null, 3);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void zeroEpochsReturnsEmpty() {
        CognitiveIPO ipo = new CognitiveIPO(0.1);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveIPO.IPOResult r = ipo.train(prefs, 0);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void finalLossNonNegative() {
        CognitiveIPO ipo = new CognitiveIPO(0.1);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(makeProfile(0.8), makeProfile(0.2)));
        CognitiveIPO.IPOResult r = ipo.train(prefs, 2);
        assertThat(r.finalLoss()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void betaRecorded() {
        CognitiveIPO ipo = new CognitiveIPO(0.5);
        assertThat(ipo.beta()).isEqualTo(0.5);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
