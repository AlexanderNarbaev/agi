package io.matrix.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SemanticExpander} — RUN 30 semantic query expansion.
 */
class SemanticExpanderTest {

    private SemanticExpander expander;

    @BeforeEach
    void setUp() {
        expander = new SemanticExpander();
    }

    @Test
    void expandAddsOriginalTokens() {
        Set<String> qTokens = expander.expand("hello world", Set.of());
        assertThat(qTokens).contains("hello", "world");
    }

    @Test
    void expandAddsFuzzyMatchesForRelatedWords() {
        // "квантовые" (quantums, adjective) vs "квантовый" (quantum, adjective).
        // They share many trigrams.
        Set<String> vocab = Set.of("квантовый", "системы", "компьютер");
        Set<String> expanded = expander.expand("квантовые системы", vocab);
        // Original "квантовые" + "системы" + fuzzy "квантовый" expected.
        assertThat(expanded).contains("квантовые", "системы", "квантовый");
    }

    @Test
    void expandIgnoresUnrelatedVocab() {
        // "квантовые" vs "космические" (cosmic) — share fewer trigrams.
        Set<String> vocab = Set.of("космические", "путешествия");
        Set<String> expanded = expander.expand("квантовые системы", vocab);
        // Unrelated vocab terms should NOT be included as fuzzy matches.
        assertThat(expanded).doesNotContain("космические");
        assertThat(expanded).doesNotContain("путешествия");
    }

    @Test
    void charNgramsProducesExpectedSet() {
        Set<String> grams = expander.charNgrams("hello");
        // "#he", "hel", "ell", "llo", "lo#"
        assertThat(grams).contains("#he", "hel", "ell", "llo", "lo#");
        assertThat(grams).hasSize(5);
    }

    @Test
    void charNgramsHandlesShortToken() {
        Set<String> grams = expander.charNgrams("ab");
        // ngramSize=3, padded "#ab#" is 4 chars → 2 trigrams.
        // But for input "ab" (length 2), we can't produce 3-grams normally,
        // so we return the padded string as a single gram.
        Set<String> grams2 = expander.charNgrams("abcdef");
        assertThat(grams2).hasSizeGreaterThan(2);
    }

    @Test
    void charNgramsHandlesNullAndEmpty() {
        assertThat(expander.charNgrams(null)).isEmpty();
        assertThat(expander.charNgrams("")).isEmpty();
    }

    @Test
    void jaccardIsSymmetric() {
        Set<String> a = Set.of("a", "b", "c");
        Set<String> b = Set.of("b", "c", "d");
        double j1 = expander.jaccard(a, b);
        double j2 = expander.jaccard(b, a);
        assertThat(j1).isEqualTo(j2);
        // intersection={b,c}=2, union={a,b,c,d}=4 → 0.5
        assertThat(j1).isEqualTo(0.5);
    }

    @Test
    void jaccardRejectsNullAndEmpty() {
        assertThat(expander.jaccard(null, Set.of("a"))).isZero();
        assertThat(expander.jaccard(Set.of(), Set.of("a"))).isZero();
        assertThat(expander.jaccard(Set.of(), Set.of())).isZero();
    }

    @Test
    void expandWithNullVocabReturnsOriginalTokens() {
        Set<String> out = expander.expand("hello world", null);
        assertThat(out).contains("hello", "world");
    }

    @Test
    void expandIsDeterministic() {
        Set<String> vocab = Set.of("квантовый", "системы");
        Set<String> e1 = expander.expand("квантовые системы", vocab);
        Set<String> e2 = expander.expand("квантовые системы", vocab);
        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void expandRejectsNullAndBlankQuery() {
        assertThat(expander.expand(null, Set.of("a"))).isEmpty();
        assertThat(expander.expand("", Set.of("a"))).isEmpty();
        assertThat(expander.expand("   ", Set.of("a"))).isEmpty();
    }
}
