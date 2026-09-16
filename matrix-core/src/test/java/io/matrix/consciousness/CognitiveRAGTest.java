package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveRAGTest {

    @Test
    void emptyKnowledgeBaseReturnsQuery() {
        CognitiveGenesisProfile q = makeProfile(0.5);
        CognitiveRAG.RetrievalResult r = CognitiveRAG.retrieveAndAugment(q, new ArrayList<>(), 3, 0.5);
        assertThat(r.augmentedProfile()).isEqualTo(q);
        assertThat(r.retrievedIndices().length).isEqualTo(0);
    }

    @Test
    void nullQueryReturnsEmpty() {
        CognitiveRAG.RetrievalResult r = CognitiveRAG.retrieveAndAugment(null, new ArrayList<>(), 3, 0.5);
        assertThat(r.augmentedProfile()).isNull();
    }

    @Test
    void retrievalFindsSelf() {
        CognitiveGenesisProfile q = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.1));
        kb.add(q);
        kb.add(makeProfile(0.7));
        CognitiveRAG.RetrievalResult r = CognitiveRAG.retrieveAndAugment(q, kb, 1, 0.0);
        assertThat(r.retrievedIndices().length).isEqualTo(1);
        assertThat(r.retrievedIndices()[0]).isEqualTo(1);
    }

    @Test
    void augmentedProfileNotNull() {
        CognitiveGenesisProfile q = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < 5; i++) kb.add(makeProfile(i / 5.0));
        CognitiveRAG.RetrievalResult r = CognitiveRAG.retrieveAndAugment(q, kb, 3, 0.5);
        assertThat(r.augmentedProfile()).isNotNull();
    }

    @Test
    void mixingRatioZeroKeepsQuery() {
        CognitiveGenesisProfile q = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.9));
        kb.add(makeProfile(0.1));
        CognitiveRAG.RetrievalResult r = CognitiveRAG.retrieveAndAugment(q, kb, 2, 0.0);
        // Pure query
        assertThat(r.augmentedProfile().phiBinary()).isCloseTo(0.5, offset(1e-9));
    }

    @Test
    void mixingRatioOneIsPureRetrieved() {
        CognitiveGenesisProfile q = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.9));
        CognitiveRAG.RetrievalResult r = CognitiveRAG.retrieveAndAugment(q, kb, 1, 1.0);
        // Should be close to retrieved (0.9)
        assertThat(r.augmentedProfile().phiBinary()).isGreaterThan(0.7);
    }

    @Test
    void avgSimilarityBounded() {
        CognitiveGenesisProfile q = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < 5; i++) kb.add(makeProfile(i / 5.0));
        CognitiveRAG.RetrievalResult r = CognitiveRAG.retrieveAndAugment(q, kb, 3, 0.5);
        assertThat(r.avgSimilarity()).isBetween(-1.0, 1.0 + 1e-9);
    }

    @Test
    void topKReturnsValidIndices() {
        CognitiveGenesisProfile q = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < 10; i++) kb.add(makeProfile(i / 10.0));
        int[] top3 = CognitiveRAG.topK(q, kb, 3);
        assertThat(top3.length).isEqualTo(3);
        for (int idx : top3) {
            assertThat(idx).isBetween(0, 9);
        }
    }

    @Test
    void topKEmptyKnowledgeBaseReturnsEmpty() {
        assertThat(CognitiveRAG.topK(makeProfile(0.5), new ArrayList<>(), 3)).isEmpty();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
