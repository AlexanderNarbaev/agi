package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileVelocityTrackerTest {

    @Test
    void emptyProfilesReturnsEmptyVelocity() {
        assertThat(ProfileVelocityTracker.velocity(new ArrayList<>())).isEmpty();
    }

    @Test
    void singleProfileReturnsEmptyVelocity() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        assertThat(ProfileVelocityTracker.velocity(profiles)).isEmpty();
    }

    @Test
    void identicalProfilesHasZeroVelocity() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));
        double[] v = ProfileVelocityTracker.velocity(profiles);
        for (double vi : v) {
            assertThat(vi).isCloseTo(0.0, within(1e-9));
        }
    }

    @Test
    void differentProfilesHasPositiveVelocity() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.1));
        profiles.add(makeProfile(0.9));
        double[] v = ProfileVelocityTracker.velocity(profiles);
        assertThat(v[0]).isGreaterThan(0.5);
    }

    @Test
    void maxVelocityReturnsPeak() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.0));
        profiles.add(makeProfile(1.0));
        profiles.add(makeProfile(0.0));
        profiles.add(makeProfile(1.0));
        double max = ProfileVelocityTracker.maxVelocity(profiles);
        assertThat(max).isGreaterThan(1.0);
    }

    @Test
    void meanVelocityForConstantSequenceIsZero() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));
        assertThat(ProfileVelocityTracker.meanVelocity(profiles)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void isRapidlyChangingDetectsHighVelocity() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.0));
        profiles.add(makeProfile(1.0));
        assertThat(ProfileVelocityTracker.isRapidlyChanging(profiles, 0.5)).isTrue();
        assertThat(ProfileVelocityTracker.isRapidlyChanging(profiles, 5.0)).isFalse();
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
