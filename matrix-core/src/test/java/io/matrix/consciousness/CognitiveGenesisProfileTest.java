package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveGenesisProfileTest {

    @Test
    void recordStoresAllFields() {
        var p = new CognitiveGenesisProfile(
            0.5, 0.3, 0.4, 0.6, 0.2, 0.7, 0.5,
            32.0, 0.8, 0.4, 2, 0.6, 3.0
        );
        assertThat(p.phiBinary()).isEqualTo(0.5);
        assertThat(p.kolmogorovK()).isEqualTo(32.0);
        assertThat(p.nkEdgeOfChaosK()).isEqualTo(2);
    }

    @Test
    void unifiedComplexityScoreInZeroOne() {
        var p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        double score = p.unifiedComplexityScore();
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    void frozenRegimeClassification() {
        var p = new CognitiveGenesisProfile(
            0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1, // high stability, low Φ
            10.0, 0.5, 0.5, 1, 0.5, 1.0
        );
        assertThat(p.regime()).isEqualTo("FROZEN");
    }

    @Test
    void chaoticRegimeClassification() {
        var p = new CognitiveGenesisProfile(
            0.9, 0.9, 0.9, 0.9, 0.9, 0.1, 0.9, // low stability, high Φ
            100.0, 0.5, 0.5, 7, 0.5, 5.0
        );
        assertThat(p.regime()).isEqualTo("CHAOTIC");
    }

    @Test
    void edgeOfChaosRegimeClassification() {
        var p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, // balanced
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        assertThat(p.regime()).isEqualTo("EDGE_OF_CHAOS");
    }
}
