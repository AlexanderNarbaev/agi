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
            // RECON-W34.9: this ramp ran 0.1 -> 1.0. Now that the fixture produces real
            // regimes, that range crosses both boundaries, so the profile changes regime part
            // way through and classify() hits its OSCILLATORY branch -- which it checks
            // BEFORE the drift test -- and reports oscillation for a monotonically rising
            // signal.
            //
            // Both observations are true and the test was conflating them. A trend and a
            // regime change are different phenomena, and a test named for drift should isolate
            // drift. The ramp is therefore confined to 0.35..0.65, which stays inside the
            // EDGE_OF_CHAOS band (regime() needs phi below 0.3 or above 0.7 to leave it), so
            // the regime is constant and only the trend varies.
            double phi = 0.35 + 0.03 * i;
            profiles.add(makeProfile(phi));
        }
        String c = ProfileStabilityMetrics.classify(profiles);
        // Isolated drift: a monotone trend with no regime crossing.
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

    /**
     * Build a profile whose regime is determined by {@code phi} alone.
     *
     * <p>RECON-W34.9: this helper hardcoded {@code stabilityPhi = 0.5}, exactly as the
     * identical helper in {@code RegimeTrajectoryAnalyzerTest} did. Since
     * {@link CognitiveGenesisProfile#regime()} needs stability above 0.7 for FROZEN and below
     * 0.3 for CHAOTIC, every profile built here classified as EDGE_OF_CHAOS — so the
     * "alternating" sequence never alternated.</p>
     *
     * <p>{@code ProfileStabilityMetrics.classify} reads {@code transitionRate} and
     * {@code normalizedRegimeEntropy}, both of which are derived from {@code regime()}. With
     * one regime throughout, transition rate was 0 and entropy 0, no oscillatory branch could
     * fire, and {@code alternatingProfileIsOscillatory} fell through to the drift check and
     * reported DRIFTING for a series with no trend at all.</p>
     *
     * @param phi average phase value in 0..1
     * @return a profile whose regime follows from phi
     */
    private static CognitiveGenesisProfile makeProfile(double phi) {
        // Low phi -> high stability -> FROZEN. High phi -> low stability -> CHAOTIC.
        double stability = phi < 0.3 ? 0.9 : (phi > 0.7 ? 0.1 : 0.5);
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, stability, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
