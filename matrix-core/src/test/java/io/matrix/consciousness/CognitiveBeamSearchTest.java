package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveBeamSearchTest {

    @Test
    void emptyCandidatesReturnsEmpty() {
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(new ArrayList<>(), 3, 5, null);
        assertThat(r.topBeams()).isEmpty();
        assertThat(r.bestScore()).isEqualTo(0.0);
    }

    @Test
    void invalidBeamWidthReturnsEmpty() {
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(new ArrayList<>(), 0, 5, null);
        assertThat(r.topBeams()).isEmpty();
    }

    @Test
    void searchReturnsTopBeams() {
        List<CognitiveGenesisProfile> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) candidates.add(makeProfile(i / 5.0));
        CognitiveBeamSearch.BeamScorer scorer = (prefix, candidate) ->
            candidate.phiBinary();
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(candidates, 2, 3, scorer);
        assertThat(r.topBeams().size()).isLessThanOrEqualTo(2);
    }

    @Test
    void bestScorePositive() {
        List<CognitiveGenesisProfile> candidates = new ArrayList<>();
        for (int i = 0; i < 3; i++) candidates.add(makeProfile(0.5));
        CognitiveBeamSearch.BeamScorer scorer = (prefix, candidate) ->
            candidate.phiBinary();
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(candidates, 2, 2, scorer);
        assertThat(r.bestScore()).isGreaterThan(0.0);
    }

    @Test
    void nullScorerUsesZero() {
        List<CognitiveGenesisProfile> candidates = new ArrayList<>();
        candidates.add(makeProfile(0.5));
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(candidates, 1, 1, null);
        // Score should be 0 (null scorer → 0)
        assertThat(r.bestScore()).isEqualTo(0.0);
    }

    @Test
    void beamsSortedByScore() {
        List<CognitiveGenesisProfile> candidates = new ArrayList<>();
        for (int i = 0; i < 4; i++) candidates.add(makeProfile(i / 4.0));
        CognitiveBeamSearch.BeamScorer scorer = (prefix, candidate) ->
            candidate.phiBinary();
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(candidates, 3, 2, scorer);
        for (int i = 1; i < r.topBeams().size(); i++) {
            assertThat(r.topBeams().get(i).score())
                .isLessThanOrEqualTo(r.topBeams().get(i - 1).score());
        }
    }

    @Test
    void maxStepsRespected() {
        List<CognitiveGenesisProfile> candidates = new ArrayList<>();
        for (int i = 0; i < 3; i++) candidates.add(makeProfile(i / 3.0));
        CognitiveBeamSearch.BeamScorer scorer = (prefix, candidate) -> 1.0;
        CognitiveBeamSearch.BeamSearchResult r =
            CognitiveBeamSearch.search(candidates, 1, 2, scorer);
        // After 2 steps, max sequence length is 2
        for (CognitiveBeamSearch.Beam b : r.topBeams()) {
            assertThat(b.sequence().size()).isLessThanOrEqualTo(2);
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
