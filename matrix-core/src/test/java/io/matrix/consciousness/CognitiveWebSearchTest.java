package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveWebSearchTest {

    @Test
    void emptyKnowledgeBaseReturnsEmpty() {
        CognitiveWebSearch.WebSearchResponse r =
            CognitiveWebSearch.search(makeProfile(0.5), new ArrayList<>(), 3);
        assertThat(r.results()).isEmpty();
        assertThat(r.totalResults()).isEqualTo(0);
    }

    @Test
    void nullQueryReturnsEmpty() {
        CognitiveWebSearch.WebSearchResponse r =
            CognitiveWebSearch.search(null, new ArrayList<>(), 3);
        assertThat(r.results()).isEmpty();
    }

    @Test
    void searchReturnsTopK() {
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        for (int i = 0; i < 10; i++) kb.add(makeProfile(i / 10.0));
        CognitiveWebSearch.WebSearchResponse r =
            CognitiveWebSearch.search(makeProfile(0.5), kb, 3);
        assertThat(r.results().size()).isEqualTo(3);
    }

    @Test
    void invalidKReturnsEmpty() {
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.5));
        CognitiveWebSearch.WebSearchResponse r =
            CognitiveWebSearch.search(makeProfile(0.5), kb, 0);
        assertThat(r.results()).isEmpty();
    }

    @Test
    void searchFindsSelf() {
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.1));
        CognitiveGenesisProfile query = makeProfile(0.5);
        kb.add(query);
        kb.add(makeProfile(0.9));
        CognitiveWebSearch.WebSearchResponse r =
            CognitiveWebSearch.search(query, kb, 1);
        // Top result should be the query itself (highest similarity)
        assertThat(r.results().get(0).title()).isEqualTo("Profile_1");
    }

    @Test
    void augmentWithSearchReturnsValidProfile() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.5));
        CognitiveWebSearch.WebSearchResponse r = CognitiveWebSearch.search(p, kb, 1);
        CognitiveGenesisProfile augmented = CognitiveWebSearch.augmentWithSearch(p, r, 0.5);
        assertThat(augmented).isNotNull();
    }

    @Test
    void augmentEmptyResponseReturnsOriginal() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveWebSearch.WebSearchResponse r =
            CognitiveWebSearch.search(p, new ArrayList<>(), 1);
        CognitiveGenesisProfile augmented = CognitiveWebSearch.augmentWithSearch(p, r, 0.5);
        assertThat(augmented).isEqualTo(p);
    }

    @Test
    void hallucinationRiskReducedWhenRelevant() {
        List<CognitiveGenesisProfile> kb = new ArrayList<>();
        kb.add(makeProfile(0.5));
        CognitiveWebSearch.WebSearchResponse r =
            CognitiveWebSearch.search(makeProfile(0.5), kb, 1);
        // Self-match should be highly relevant → hallucination risk reduced
        assertThat(r.hallucinationRiskReduced()).isTrue();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
