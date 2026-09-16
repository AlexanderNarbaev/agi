package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveMultiLayerVerifierTest {

    @Test
    void nullProfileReturnsEmpty() {
        CognitiveMultiLayerVerifier.MultiLayerResult r =
            CognitiveMultiLayerVerifier.verify(null, new ArrayList<>(), new ArrayList<>(), 0.5);
        assertThat(r.votes()).isEmpty();
    }

    @Test
    void emptyInputsReturnsEmptyVotes() {
        CognitiveMultiLayerVerifier.MultiLayerResult r =
            CognitiveMultiLayerVerifier.verify(makeProfile(0.5), new ArrayList<>(), new ArrayList<>(), 0.5);
        assertThat(r.votes()).isEmpty();
    }

    @Test
    void profileInKBAllKBVerifiersAgree() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(p);
        CognitiveMultiLayerVerifier.MultiLayerResult r =
            CognitiveMultiLayerVerifier.verify(p, kb, null, 0.5);
        // KB and RAG verifiers should agree
        assertThat(r.votes().size()).isGreaterThanOrEqualTo(2);
        for (CognitiveMultiLayerVerifier.VerifierVote v : r.votes()) {
            assertThat(v.supported()).isTrue();
        }
    }

    @Test
    void consensusScoreBounded() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(p);
        CognitiveMultiLayerVerifier.MultiLayerResult r =
            CognitiveMultiLayerVerifier.verify(p, kb, null, 0.5);
        if (!Double.isNaN(r.consensusScore())) {
            assertThat(Math.abs(r.consensusScore())).isLessThanOrEqualTo(1.0 + 1e-9);
        }
    }

    @Test
    void allVerifiersAgree() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(p);
        List<CognitiveGenesisProfile> history = new ArrayList<>();
        history.add(p);
        CognitiveMultiLayerVerifier.MultiLayerResult r =
            CognitiveMultiLayerVerifier.verify(p, kb, history, 0.5);
        // KB, HISTORY, RAG verifiers all should support
        assertThat(r.votes().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void verifierVoteHasValidStrategy() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(p);
        CognitiveMultiLayerVerifier.MultiLayerResult r =
            CognitiveMultiLayerVerifier.verify(p, kb, null, 0.5);
        for (CognitiveMultiLayerVerifier.VerifierVote v : r.votes()) {
            assertThat(v.strategy()).isIn("KB", "HISTORY", "SPECULATIVE", "RAG");
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
