package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W42.1 — the tokenizer every path depends on, tested.
 *
 * <p>RECON-W42.0 found that `TextFeatures` had **zero tests in the entire
 * repository**, while thirty-eight call sites depend on it: the retriever,
 * the grader, the contradiction detector, the reasoning engine, the feature
 * extractor. Every one of them inherits its behaviour, including its
 * English-only assumptions.
 *
 * <p>These tests fix the behaviour that is here and on purpose. The
 * English-only parts are asserted as English-only rather than hidden, so
 * that the W42.3 work knows exactly what it has to change.
 */
class TextFeaturesTest {

    @Test
    @DisplayName("the tokenizer splits on non-letters and drops the stopwords")
    void theTokenizerSplitsOnNonLettersAndDropsTheStopwords() {
        // The name says "original", but the stopwords are dropped here too:
        // both token sets are content terms, and the "original" one keeps
        // the words as they were WRITTEN rather than folded.
        assertThat(TextFeatures.originalTokens("What is photosynthesis?"))
                .containsExactly("photosynthesis");
        assertThat(TextFeatures.originalTokens("The Calvin cycle (dark reaction)"))
                .contains("calvin", "cycle", "dark", "reaction");
    }

    @Test
    @DisplayName("the tokenizer is Unicode-safe: Cyrillic survives it")
    void theTokenizerIsUnicodeSafe() {
        List<String> tokens = TextFeatures.originalTokens("Что такое фотосинтез?");

        assertThat(tokens).contains("фотосинтез");
        assertThat(tokens).noneMatch(String::isEmpty);
    }

    @Test
    @DisplayName("the retrieval tokens are folded and the originals are not")
    void theRetrievalTokensAreFoldedAndTheOriginalsAreNot() {
        // The retrieval tokens are the folded stems the matcher works with;
        // the originals are the words as they were written, so that the
        // answer can report the question in the user's own words.
        assertThat(TextFeatures.tokens("What is photosynthesis?"))
                .containsExactly("photosynthesi");
        assertThat(TextFeatures.originalTokens("What is photosynthesis?"))
                .containsExactly("photosynthesis");
    }

    @Test
    @DisplayName("the stopword list is English-only, and says so")
    void theStopwordListIsEnglishOnly() {
        assertThat(TextFeatures.isStopWord("the")).isTrue();
        assertThat(TextFeatures.isStopWord("photosynthesis")).isFalse();
        // A Russian function word is not a stopword here. This is a known
        // limitation of the W41 text layer, and the W42.3 localisation has
        // to change it - but until then it is asserted, not hidden.
        assertThat(TextFeatures.isStopWord("что")).isFalse();
    }

    @Test
    @DisplayName("the suffix fold is English-only and conservative")
    void theSuffixFoldIsEnglishOnlyAndConservative() {
        // The fold strips the plural and then a trailing "e": "molecules"
        // folds to "molecul", which is a stem rather than a word - and a
        // stem is what the matcher compares.
        assertThat(TextFeatures.fold("molecules")).isEqualTo("molecul");
        assertThat(TextFeatures.fold("releases")).isEqualTo("releas");
        assertThat(TextFeatures.fold("photosynthesis")).isEqualTo("photosynthesi");
        // A single letter is a token, not dropped - "a" is dropped because
        // it is a stopword, not because it is short.
        assertThat(TextFeatures.tokens("a b c")).contains("b", "c").doesNotContain("a");
    }

    @Test
    @DisplayName("the inverse document frequency ranks a rare term above a common one")
    void theInverseDocumentFrequencyRanksARareTermAboveACommonOne() {
        Map<String, Double> idf = TextFeatures.idf(List.of(
                Set.of("photosynthesis", "oxygen", "sugar"),
                Set.of("photosynthesis", "carbon", "dioxide"),
                Set.of("photosynthesis", "light")));

        assertThat(idf.get("photosynthesis")).isLessThan(idf.get("sugar"));
        assertThat(idf.get("sugar")).isGreaterThan(0.0);
    }
}
