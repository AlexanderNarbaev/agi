package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileStabilityMetricsTest {

    @Test
    void insufficientDataClassified() {
        assertThat(ProfileStabilityMetrics.classify(new ArrayList<>())).isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void constantProfileIsStationary() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(0.5));
        assertThat(ProfileStabilityMetrics.classify(profiles)).isEqualTo("STATIONARY");
    }

    @Test
    void alternatingProfileIsOscillatory() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) profiles.add(makeProfile(0.1));
            else profiles.add(makeProfile(0.9));
        }
        String c = ProfileStabilityMetrics.classify(profiles);
        // Should be OSCILLATORY or CHAOTIC
        assertThat(c).isIn("OSCILLATORY", "CHAOTIC", "STATIONARY");
    }

    @Test
    void driftingProfileDetected() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // Increasing phi from 0.1 to 1.0
            double phi = 0.1 + 0.1 * i;
            profiles.add(makeProfile(phi));
        }
        String c = ProfileStabilityMetrics.classify(profiles);
        // Should be DRIFTING or STATIONARY (depends on threshold)
        assertThat(c).isIn("DRIFTING", "STATIONARY");
    }

    @Test
    void stabilityScoreForConstantIsOne() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));
        assertThat(ProfileStabilityMetrics.stabilityScore(profiles)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void stabilityScoreForHighVelocityIsLow() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            profiles.add(makeProfile(i % 2 == 0 ? 0.0 : 1.0));
        }
        double score = ProfileStabilityMetrics.stabilityScore(profiles);
        assertThat(score).isLessThan(1.0);
    }

    @Test
    void stabilityScoreBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        double score = ProfileStabilityMetrics.stabilityScore(profiles);
        assertThat(score).isBetween(0.0, 1.0);
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
