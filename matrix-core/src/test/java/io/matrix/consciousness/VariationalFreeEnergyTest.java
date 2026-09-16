package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VariationalFreeEnergyTest {

    @Test
    void vfeOfIdenticalProfileIsZero() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        double vfe = VariationalFreeEnergy.vfe(p, p);
        assertThat(vfe).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void vfeIsNonNegative() {
        CognitiveGenesisProfile a = makeProfile(0.1);
        CognitiveGenesisProfile b = makeProfile(0.9);
        double vfe = VariationalFreeEnergy.vfe(a, b);
        assertThat(vfe).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void efeForIdenticalIsZero() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        assertThat(VariationalFreeEnergy.expectedFreeEnergy(p, p)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void efeForDifferentIsPositive() {
        CognitiveGenesisProfile a = makeProfile(0.0);
        CognitiveGenesisProfile b = makeProfile(1.0);
        double efe = VariationalFreeEnergy.expectedFreeEnergy(a, b);
        assertThat(efe).isGreaterThan(0.0);
    }

    @Test
    void selectActionReturnsBestIndex() {
        java.util.List<CognitiveGenesisProfile> profiles = new java.util.ArrayList<>();
        profiles.add(makeProfile(0.1));
        profiles.add(makeProfile(0.5));  // closest to target 0.5
        profiles.add(makeProfile(0.9));
        CognitiveGenesisProfile target = makeProfile(0.5);
        int idx = VariationalFreeEnergy.selectAction(profiles, target);
        assertThat(idx).isEqualTo(1);
    }

    @Test
    void nullProfilesReturnZero() {
        assertThat(VariationalFreeEnergy.vfe(null, makeProfile(0.5))).isEqualTo(0.0);
        assertThat(VariationalFreeEnergy.expectedFreeEnergy(null, null)).isEqualTo(0.0);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
