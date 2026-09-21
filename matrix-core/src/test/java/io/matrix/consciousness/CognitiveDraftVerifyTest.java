package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveDraftVerifyTest {

    @Test
    void emptyHistoryReturnsEmpty() {
        CognitiveDraftVerify.DraftVerifyResult r =
            CognitiveDraftVerify.draftAndVerify(new ArrayList<>(), null, 3, 0.5);
        assertThat(r.candidates()).isEmpty();
    }

    @Test
    void nullActualReturnsEmpty() {
        List<CognitiveGenesisProfile> h = new ArrayList<>();
        h.add(makeProfile(0.5));
        CognitiveDraftVerify.DraftVerifyResult r =
            CognitiveDraftVerify.draftAndVerify(h, null, 3, 0.5);
        assertThat(r.candidates()).isEmpty();
    }

    @Test
    void invalidKReturnsEmpty() {
        List<CognitiveGenesisProfile> h = new ArrayList<>();
        h.add(makeProfile(0.5));
        CognitiveDraftVerify.DraftVerifyResult r =
            CognitiveDraftVerify.draftAndVerify(h, makeProfile(0.6), 0, 0.5);
        assertThat(r.candidates()).isEmpty();
    }

    @Test
    void draftAndVerifyReturnsKCandidates() {
        List<CognitiveGenesisProfile> h = new ArrayList<>();
        for (int i = 0; i < 5; i++) h.add(makeProfile(i / 5.0));
        CognitiveDraftVerify.DraftVerifyResult r =
            CognitiveDraftVerify.draftAndVerify(h, makeProfile(0.5), 3, 0.5);
        assertThat(r.candidates().size()).isEqualTo(3);
    }

    @Test
    void averageScoreBounded() {
        List<CognitiveGenesisProfile> h = new ArrayList<>();
        for (int i = 0; i < 5; i++) h.add(makeProfile(i / 5.0));
        CognitiveDraftVerify.DraftVerifyResult r =
            CognitiveDraftVerify.draftAndVerify(h, makeProfile(0.5), 3, 0.5);
        if (!Double.isNaN(r.averageScore())) {
            assertThat(Math.abs(r.averageScore())).isLessThanOrEqualTo(1.0 + 1e-9);
        }
    }

    @Test
    void acceptanceRateComputation() {
        List<CognitiveDraftVerify.DraftVerifyResult> results = new ArrayList<>();
        List<CognitiveGenesisProfile> h = new ArrayList<>();
        for (int i = 0; i < 5; i++) h.add(makeProfile(i / 5.0));
        CognitiveDraftVerify.DraftVerifyResult r1 =
            CognitiveDraftVerify.draftAndVerify(h, makeProfile(0.5), 3, 0.5);
        results.add(r1);
        double rate = CognitiveDraftVerify.acceptanceRate(results);
        assertThat(rate).isBetween(0.0, 1.0 + 1e-9);
    }

    @Test
    void emptyResultsRateZero() {
        assertThat(CognitiveDraftVerify.acceptanceRate(new ArrayList<>())).isEqualTo(0.0);
    }

    @Test
    void candidateScoresBounded() {
        List<CognitiveGenesisProfile> h = new ArrayList<>();
        for (int i = 0; i < 5; i++) h.add(makeProfile(i / 5.0));
        CognitiveDraftVerify.DraftVerifyResult r =
            CognitiveDraftVerify.draftAndVerify(h, makeProfile(0.5), 3, 0.5);
        for (CognitiveDraftVerify.DraftCandidate c : r.candidates()) {
            if (!Double.isNaN(c.verificationScore())) {
                assertThat(Math.abs(c.verificationScore())).isLessThanOrEqualTo(1.0 + 1e-9);
            }
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
