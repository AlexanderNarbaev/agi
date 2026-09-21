package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveKTOTest {

    @Test
    void lossNonNegative() {
        CognitiveKTO kto = new CognitiveKTO(0.1, 1.0, 1.0, 0.0);
        double loss = kto.loss(Math.log(0.5), true);
        assertThat(loss).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void desirableVsUndesirable() {
        CognitiveKTO kto = new CognitiveKTO(0.1, 1.0, 2.0, 0.0);
        double desirableLoss = kto.loss(Math.log(0.5), true);
        double undesirableLoss = kto.loss(Math.log(0.5), false);
        // With lambdaU=2, undesirable should be higher
        assertThat(undesirableLoss).isGreaterThan(desirableLoss);
    }

    @Test
    void trainProducesHistory() {
        CognitiveKTO kto = new CognitiveKTO(0.1, 1.0, 1.0, 0.0);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        List<Boolean> labels = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            profiles.add(makeProfile(0.5));
            labels.add(i % 2 == 0);
        }
        CognitiveKTO.KTOResult r = kto.train(profiles, labels, 3);
        assertThat(r.history().size()).isEqualTo(3);
    }

    @Test
    void nullReturnsEmpty() {
        CognitiveKTO kto = new CognitiveKTO(0.1, 1.0, 1.0, 0.0);
        CognitiveKTO.KTOResult r = kto.train(null, null, 3);
        assertThat(r.history()).isEmpty();
    }

    @Test
    void countDesirableUndesirable() {
        CognitiveKTO kto = new CognitiveKTO(0.1, 1.0, 1.0, 0.0);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        List<Boolean> labels = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            profiles.add(makeProfile(0.5));
            labels.add(i < 2); // 2 desirable, 2 undesirable
        }
        CognitiveKTO.KTOResult r = kto.train(profiles, labels, 1);
        assertThat(r.history().get(0).desirableCount()).isEqualTo(2);
        assertThat(r.history().get(0).undesirableCount()).isEqualTo(2);
    }

    @Test
    void hyperparametersRecorded() {
        CognitiveKTO kto = new CognitiveKTO(0.2, 1.5, 2.5, 0.5);
        assertThat(kto.beta()).isEqualTo(0.2);
        assertThat(kto.lambdaD()).isEqualTo(1.5);
        assertThat(kto.lambdaU()).isEqualTo(2.5);
        assertThat(kto.z0()).isEqualTo(0.5);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
