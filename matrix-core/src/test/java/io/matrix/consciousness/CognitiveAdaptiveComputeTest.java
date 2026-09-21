package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveAdaptiveComputeTest {

    @Test
    void nullReturnsEmpty() {
        CognitiveAdaptiveCompute.AdaptiveResult r =
            CognitiveAdaptiveCompute.process(null, 3, 0.5);
        assertThat(r.processedProfiles()).isEmpty();
        assertThat(r.totalCompute()).isEqualTo(0);
    }

    @Test
    void emptyProfilesReturnsEmpty() {
        CognitiveAdaptiveCompute.AdaptiveResult r =
            CognitiveAdaptiveCompute.process(new ArrayList<>(), 3, 0.5);
        assertThat(r.processedProfiles()).isEmpty();
    }

    @Test
    void zeroLayersReturnsEmpty() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        CognitiveAdaptiveCompute.AdaptiveResult r =
            CognitiveAdaptiveCompute.process(profiles, 0, 0.5);
        assertThat(r.totalCompute()).isEqualTo(0);
    }

    @Test
    void processAllProfiles() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        CognitiveAdaptiveCompute.AdaptiveResult r =
            CognitiveAdaptiveCompute.process(profiles, 3, 0.5);
        assertThat(r.processedProfiles().size()).isEqualTo(5);
    }

    @Test
    void speedupPositive() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(0.9));
        CognitiveAdaptiveCompute.AdaptiveResult r =
            CognitiveAdaptiveCompute.process(profiles, 5, 0.5);
        // High-phi profiles should trigger early exit → speedup
        assertThat(r.speedup()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void averageLayersTracked() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        CognitiveAdaptiveCompute.AdaptiveResult r =
            CognitiveAdaptiveCompute.process(profiles, 5, 0.5);
        assertThat(r.avgLayersUsed()).isGreaterThanOrEqualTo(1.0);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
