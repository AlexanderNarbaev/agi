package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RegimeTrajectoryAnalyzerTest {

    @Test
    void emptyProfilesReturnsZeroRunLengths() {
        int[] counts = RegimeTrajectoryAnalyzer.runLengths(new ArrayList<>());
        assertThat(counts).containsExactly(0, 0, 0);
    }

    @Test
    void constantRegimeHasSingleRun() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));  // EDGE
        int[] counts = RegimeTrajectoryAnalyzer.runLengths(profiles);
        assertThat(counts[1]).isEqualTo(1);  // One EDGE run
    }

    @Test
    void alternatingRegimesHaveMultipleRuns() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (i % 2 == 0) profiles.add(makeProfile(0.1));  // FROZEN
            else profiles.add(makeProfile(0.9));  // CHAOTIC
        }
        int[] counts = RegimeTrajectoryAnalyzer.runLengths(profiles);
        assertThat(counts[0]).isEqualTo(3);  // 3 FROZEN runs
        assertThat(counts[2]).isEqualTo(3);  // 3 CHAOTIC runs
    }

    @Test
    void stabilityForConstantIsOne() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));
        assertThat(RegimeTrajectoryAnalyzer.stability(profiles)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void stabilityForAlternatingIsZero() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i % 2 == 0) profiles.add(makeProfile(0.1));
            else profiles.add(makeProfile(0.9));
        }
        assertThat(RegimeTrajectoryAnalyzer.stability(profiles)).isEqualTo(0.0);
    }

    @Test
    void transitionMatrixForConstantIsDiagonal() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));  // EDGE
        int[][] trans = RegimeTrajectoryAnalyzer.transitionCounts(profiles);
        // All transitions within EDGE
        assertThat(trans[1][1]).isEqualTo(4);  // 4 self-transitions
    }

    @Test
    void avgRunLengthForConstantIsN() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));
        assertThat(RegimeTrajectoryAnalyzer.avgRunLength(profiles)).isCloseTo(5.0, within(1e-9));
    }

    /**
     * Build a profile whose regime is determined by {@code phi} alone.
     *
     * <p>RECON-W34.9: the previous helper hardcoded {@code stabilityPhi = 0.5}. But
     * {@link CognitiveGenesisProfile#regime()} requires {@code avgStability > 0.7} for FROZEN
     * and {@code avgStability < 0.3} for CHAOTIC, so 0.5 satisfied neither and EVERY profile
     * this helper produced classified as EDGE_OF_CHAOS.</p>
     *
     * <p>Three tests then asserted things about an "alternating" sequence that never
     * alternated: two expected FROZEN/CHAOTIC runs and got none, and one expected zero stability
     * and got a perfect 1.0 because nothing ever changed. Production code was correct
     * throughout -- the fixture could not construct the input it was reasoning about.</p>
     *
     * <p>Stability is now chosen from phi so that low phi is genuinely frozen and high phi
     * genuinely chaotic, which is what the calling tests mean.</p>
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
