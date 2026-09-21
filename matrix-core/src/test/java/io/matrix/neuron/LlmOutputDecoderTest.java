package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmOutputDecoderTest {

    @Test
    void constructorRejectsNullRng() {
        assertThatThrownBy(() -> new LlmOutputDecoder(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyDecoderReturnsEmpty() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        assertThat(dec.size()).isEqualTo(0);
        assertThat(dec.extractConcepts(new int[]{1, 2, 3}, 5)).isEmpty();
    }

    @Test
    void registerConceptStoresCode() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConcept("AI", new int[]{1, 2, 3});
        assertThat(dec.size()).isEqualTo(1);
        assertThat(dec.getCode("AI")).isNotNull();
    }

    @Test
    void registerConceptRejectsBadArgs() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        assertThatThrownBy(() -> dec.registerConcept(null, new int[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dec.registerConcept("x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerConceptFromTextStores() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConceptFromText("topic", "machine learning");
        assertThat(dec.size()).isEqualTo(1);
    }

    @Test
    void extractConceptsFindsExactMatch() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(42));
        int[] tokens = {1, 2, 3, 4, 5};
        dec.registerConcept("exact", tokens);
        List<LlmOutputDecoder.Match> matches = dec.extractConcepts(tokens, 1);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).concept).isEqualTo("exact");
        assertThat(matches.get(0).distance).isEqualTo(0);
    }

    @Test
    void extractConceptsReturnsTopKSorted() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(42));
        dec.registerConcept("a", new int[]{1, 2, 3});
        dec.registerConcept("b", new int[]{4, 5, 6});
        dec.registerConcept("c", new int[]{7, 8, 9});
        List<LlmOutputDecoder.Match> matches = dec.extractConcepts(new int[]{1, 2, 3}, 3);
        assertThat(matches).hasSize(3);
        // First should be "a" (exact match)
        assertThat(matches.get(0).concept).isEqualTo("a");
        assertThat(matches.get(0).distance).isEqualTo(0);
    }

    @Test
    void extractConceptsRejectsBadArgs() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        assertThatThrownBy(() -> dec.extractConcepts(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dec.extractConcepts(new int[]{1, 2}, 0)).isEmpty();
        assertThat(dec.extractConcepts(new int[]{1, 2}, -1)).isEmpty();
    }

    @Test
    void extractConceptsFromTextFindsMatch() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(42));
        dec.registerConceptFromText("hello", "hello");
        List<LlmOutputDecoder.Match> matches = dec.extractConceptsFromText("hello", 1);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).concept).isEqualTo("hello");
        assertThat(matches.get(0).distance).isEqualTo(0);
    }

    @Test
    void extractConceptsFromTextRejectsNull() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        assertThatThrownBy(() -> dec.extractConceptsFromText(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conceptsReturnsAllLabels() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConcept("a", new int[]{1});
        dec.registerConcept("b", new int[]{2});
        dec.registerConcept("c", new int[]{3});
        assertThat(dec.concepts()).containsExactly("a", "b", "c");
    }

    @Test
    void removeRemovesConcept() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConcept("a", new int[]{1});
        assertThat(dec.remove("a")).isTrue();
        assertThat(dec.remove("a")).isFalse();
        assertThat(dec.size()).isEqualTo(0);
    }

    @Test
    void clearRemovesAll() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConcept("a", new int[]{1});
        dec.registerConcept("b", new int[]{2});
        dec.clear();
        assertThat(dec.size()).isEqualTo(0);
    }

    @Test
    void matchToStringContainsFields() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConcept("a", new int[]{1});
        List<LlmOutputDecoder.Match> matches = dec.extractConcepts(new int[]{1}, 1);
        LlmOutputDecoder.Match m = matches.get(0);
        assertThat(m.toString()).contains("a").contains("dist=0");
    }

    @Test
    void matchSimilarityIsOneForExactMatch() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConcept("a", new int[]{1, 2, 3});
        List<LlmOutputDecoder.Match> matches = dec.extractConcepts(new int[]{1, 2, 3}, 1);
        assertThat(matches.get(0).similarity).isEqualTo(1.0);
    }

    @Test
    void matchSimilarityIsLowerForUnrelatedTokens() {
        LlmOutputDecoder dec = new LlmOutputDecoder(new Random(1));
        dec.registerConcept("a", new int[]{1, 2, 3});
        // Query with completely different tokens → low similarity
        List<LlmOutputDecoder.Match> matches = dec.extractConcepts(
                new int[]{100, 200, 300}, 1);
        // Similarity should be much lower than 1.0
        assertThat(matches.get(0).similarity).isLessThan(0.5);
    }
}
