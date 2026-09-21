package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileDistanceTest {

    @Test
    void l1IdenticalProfilesIsZero() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        assertThat(ProfileDistance.l1Distance(p, p)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void l2IdenticalProfilesIsZero() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        assertThat(ProfileDistance.l2Distance(p, p)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void cosineIdenticalProfilesIsZero() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        assertThat(ProfileDistance.cosineDistance(p, p)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void l1DifferentProfilesPositive() {
        CognitiveGenesisProfile a = makeProfile(0.0);
        CognitiveGenesisProfile b = makeProfile(1.0);
        double d = ProfileDistance.l1Distance(a, b);
        assertThat(d).isGreaterThan(0.0);
        assertThat(d).isLessThanOrEqualTo(1.0);
    }

    @Test
    void l2DifferentProfilesPositive() {
        CognitiveGenesisProfile a = makeProfile(0.0);
        CognitiveGenesisProfile b = makeProfile(1.0);
        double d = ProfileDistance.l2Distance(a, b);
        assertThat(d).isGreaterThan(0.0);
    }

    @Test
    void cosineDifferentProfilesPositive() {
        CognitiveGenesisProfile a = makeProfile(0.0);
        CognitiveGenesisProfile b = makeProfile(1.0);
        double d = ProfileDistance.cosineDistance(a, b);
        assertThat(d).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void compositeDistanceBounded() {
        CognitiveGenesisProfile a = makeProfile(0.0);
        CognitiveGenesisProfile b = makeProfile(1.0);
        double d = ProfileDistance.compositeDistance(a, b);
        assertThat(d).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void nullProfilesReturnZero() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        assertThat(ProfileDistance.l1Distance(null, p)).isEqualTo(0.0);
        assertThat(ProfileDistance.l2Distance(p, null)).isEqualTo(0.0);
        assertThat(ProfileDistance.cosineDistance(null, null)).isEqualTo(0.0);
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
