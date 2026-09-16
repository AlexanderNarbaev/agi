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
