package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitivePhaseDetectorTest {

    @Test
    void emptyProfilesReturnsEmptyTransitions() {
        assertThat(CognitivePhaseDetector.detectTransitions(new ArrayList<>())).isEmpty();
        assertThat(CognitivePhaseDetector.dominantRegime(new ArrayList<>())).isEqualTo("UNKNOWN");
    }

    @Test
    void nullProfilesReturnsEmptyTransitions() {
        assertThat(CognitivePhaseDetector.detectTransitions(null)).isEmpty();
        assertThat(CognitivePhaseDetector.dominantRegime(null)).isEqualTo("UNKNOWN");
    }

    @Test
    void singleProfileReturnsEmptyTransitions() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThat(CognitivePhaseDetector.detectTransitions(profiles)).isEmpty();
    }

    @Test
    void detectsFrozenToEdgeTransition() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1));  // FROZEN
        profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));  // EDGE_OF_CHAOS
        List<CognitivePhaseDetector.PhaseTransition> transitions =
            CognitivePhaseDetector.detectTransitions(profiles);
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).fromRegime()).isEqualTo("FROZEN");
        assertThat(transitions.get(0).toRegime()).isEqualTo("EDGE_OF_CHAOS");
    }

    @Test
    void dominantRegimeSelectsMostCommon() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));  // EDGE
        profiles.add(makeProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1));  // FROZEN
        profiles.add(makeProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1));  // FROZEN
        assertThat(CognitivePhaseDetector.dominantRegime(profiles)).isEqualTo("EDGE_OF_CHAOS");
    }

    @Test
    void transitionRateForAllChangesIsOne() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1));  // FROZEN
        profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));  // EDGE
        profiles.add(makeProfile(0.9, 0.9, 0.9, 0.9, 0.9, 0.1, 0.9));  // CHAOTIC
        assertThat(CognitivePhaseDetector.transitionRate(profiles)).isEqualTo(1.0);
    }

    @Test
    void transitionRateForNoChangesIsZero() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThat(CognitivePhaseDetector.transitionRate(profiles)).isEqualTo(0.0);
    }

    private static CognitiveGenesisProfile makeProfile(
            double phiB, double phiF, double phiR, double phiLG,
            double iap, double stab, double clp) {
        return new CognitiveGenesisProfile(
            phiB, phiF, phiR, phiLG, iap, stab, clp,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
