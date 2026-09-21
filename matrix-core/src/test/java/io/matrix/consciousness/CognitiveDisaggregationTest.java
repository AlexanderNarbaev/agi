package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveDisaggregationTest {

    @Test
    void emptyProfilesReturnEmptyResult() {
        CognitiveDisaggregation.DisaggregationResult r =
            CognitiveDisaggregation.process(new ArrayList<>(), 16, 1L);
        assertThat(r.prefillResult().profilesProcessed()).isEqualTo(0);
        assertThat(r.decodeResult().lastProfile()).isNull();
    }

    @Test
    void prefillProcessesAllProfiles() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        CognitiveDisaggregation.PrefillResult p =
            CognitiveDisaggregation.prefillPhase(profiles, 16, 1L);
        assertThat(p.profilesProcessed()).isEqualTo(5);
        assertThat(p.batchEmbeddings().length).isEqualTo(5);
    }

    @Test
    void decodeLastProfileCorrect() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        CognitiveDisaggregation.DecodeResult d =
            CognitiveDisaggregation.decodePhase(profiles, 16, 1L, 2, 0.5);
        assertThat(d.lastProfile().phiBinary()).isEqualTo(0.8);
    }

    @Test
    void batchEntropyBounded() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        CognitiveDisaggregation.PrefillResult p =
            CognitiveDisaggregation.prefillPhase(profiles, 16, 1L);
        assertThat(p.batchEntropy()).isBetween(0.0, 10.0);
    }

    @Test
    void speedupRatioPositive() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        CognitiveDisaggregation.DisaggregationResult r =
            CognitiveDisaggregation.process(profiles, 16, 1L);
        double speedup = CognitiveDisaggregation.speedupRatio(r.prefillResult(), r.decodeResult());
        assertThat(speedup).isGreaterThan(0.0);
    }

    @Test
    void fullPipeline() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 8; i++) profiles.add(makeProfile(i / 8.0));
        CognitiveDisaggregation.DisaggregationResult r =
            CognitiveDisaggregation.process(profiles, 16, 42L);
        assertThat(r.prefillResult().profilesProcessed()).isEqualTo(8);
        assertThat(r.decodeResult().lastProfile()).isNotNull();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
