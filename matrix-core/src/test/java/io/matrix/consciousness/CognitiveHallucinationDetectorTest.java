package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveHallucinationDetectorTest {

    @Test
    void nullProfileIsHallucinated() {
        CognitiveHallucinationDetector.HallucinationReport r =
            CognitiveHallucinationDetector.detect(null, new ArrayList<>(), new ArrayList<>(), 0.5);
        assertThat(r.hallucinated()).isTrue();
        assertThat(r.reason()).isEqualTo("NULL_PROFILE");
    }

    @Test
    void profileInKBIsNotHallucinated() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(p);
        CognitiveHallucinationDetector.HallucinationReport r =
            CognitiveHallucinationDetector.detect(p, kb, null, 0.5);
        assertThat(r.hallucinated()).isFalse();
        assertThat(r.reason()).isEqualTo("SUPPORTED");
    }

    @Test
    void profileNotInKBIsHallucinated() {
        CognitiveGenesisProfile p = makeProfile(0.1);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < 5; i++) kb.add(makeProfile(0.9 + i / 10.0));
        CognitiveHallucinationDetector.HallucinationReport r =
            CognitiveHallucinationDetector.detect(p, kb, null, 0.99); // very strict
        assertThat(r.hallucinated()).isTrue();
    }

    @Test
    void profileInHistoryIsSupported() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        history.add(p);
        CognitiveHallucinationDetector.HallucinationReport r =
            CognitiveHallucinationDetector.detect(p, null, history, 0.5);
        assertThat(r.hallucinated()).isFalse();
    }

    @Test
    void filterRemovesHallucinated() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.0));
        List<CognitiveGenesisProfile> filtered =
            CognitiveHallucinationDetector.filter(profiles, kb, 0.99);
        // With very strict threshold, most should be filtered
        assertThat(filtered.size()).isLessThan(profiles.size());
    }

    @Test
    void filterAllIfNotInKB() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.0));
        List<CognitiveGenesisProfile> filtered =
            CognitiveHallucinationDetector.filter(profiles, kb, 0.99);
        assertThat(filtered).isEmpty();
    }

    @Test
    void confidenceBounded() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(p);
        CognitiveHallucinationDetector.HallucinationReport r =
            CognitiveHallucinationDetector.detect(p, kb, null, 0.5);
        if (!Double.isNaN(r.confidence())) {
            assertThat(Math.abs(r.confidence())).isLessThanOrEqualTo(1.0 + 1e-9);
        }
    }

    @Test
    void emptyKBAndHistoryIsHallucinated() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveHallucinationDetector.HallucinationReport r =
            CognitiveHallucinationDetector.detect(p, new ArrayList<>(), new ArrayList<>(), 0.5);
        // No KB and no history → hallucinated (nothing to support it)
        assertThat(r.hallucinated()).isTrue();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
