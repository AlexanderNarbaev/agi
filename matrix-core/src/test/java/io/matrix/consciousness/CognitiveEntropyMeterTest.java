package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveEntropyMeterTest {

    @Test
    void emptyEntropyIsZero() {
        assertThat(CognitiveEntropyMeter.regimeEntropy(new ArrayList<>())).isEqualTo(0.0);
    }

    @Test
    void singleProfileEntropyIsZero() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThat(CognitiveEntropyMeter.regimeEntropy(profiles)).isEqualTo(0.0);
    }

    @Test
    void uniformRegimeDistributionIsMaxEntropy() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (i % 3 == 0) profiles.add(makeProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1));  // FROZEN
            else if (i % 3 == 1) profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));  // EDGE
            else profiles.add(makeProfile(0.9, 0.9, 0.9, 0.9, 0.9, 0.1, 0.9));  // CHAOTIC
        }
        double h = CognitiveEntropyMeter.regimeEntropy(profiles);
        double max = CognitiveEntropyMeter.maxEntropy();
        assertThat(h).isCloseTo(max, within(0.01));
    }

    @Test
    void allSameRegimeHasZeroEntropy() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThat(CognitiveEntropyMeter.regimeEntropy(profiles)).isEqualTo(0.0);
    }

    @Test
    void normalizedEntropyBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        double n = CognitiveEntropyMeter.normalizedRegimeEntropy(profiles);
        assertThat(n).isBetween(0.0, 1.0);
    }

    @Test
    void profileEntropyBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            profiles.add(makeProfile((i % 10) / 10.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        }
        double h = CognitiveEntropyMeter.profileEntropy(profiles);
        assertThat(h).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void maxEntropyIsLogThree() {
        assertThat(CognitiveEntropyMeter.maxEntropy()).isCloseTo(Math.log(3), within(1e-9));
    }

    private static CognitiveGenesisProfile makeProfile(
            double phiB, double phiF, double phiR, double phiLG,
            double iap, double stab, double clp) {
        return new CognitiveGenesisProfile(
            phiB, phiF, phiR, phiLG, iap, stab, clp,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
