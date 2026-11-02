package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.3 — the text layer speaks Russian and English.
 *
 * <p>RECON-W42.0 found the whole text layer English-only by construction:
 * an 80-word English stopword list, an English-only suffix fold, an
 * English negation list, English task-classification keywords. The W42
 * benchmark measured the consequence — the five Russian items scored 0/5.
 *
 * <p>These tests fix the behaviour that is now there, and they fix what is
 * still missing as a named limitation rather than a silent gap.
 */
class MultilingualTextTest {

    @Test
    @DisplayName("the language is detected from the script")
    void theLanguageIsDetectedFromTheScript() {
        assertThat(TextProfiles.detect("Что такое фотосинтез?")).isEqualTo("ru");
        assertThat(TextProfiles.detect("What is photosynthesis?")).isEqualTo("en");
        assertThat(TextProfiles.detect("12345 67890")).isEqualTo("unknown");
        assertThat(TextProfiles.detect("")).isEqualTo("unknown");
        // A text with one Cyrillic word in an English sentence is English.
        assertThat(TextProfiles.detect("What is the цикл?")).isEqualTo("en");
    }

    @Test
    @DisplayName("the Russian stopwords are dropped and the Russian ones are kept")
    void theRussianStopwordsAreDropped() {
        assertThat(TextFeatures.isStopWord("the")).isTrue();
        assertThat(TextProfiles.RUSSIAN.isStopWord("что")).isTrue();
        assertThat(TextProfiles.RUSSIAN.isStopWord("и")).isTrue();
        assertThat(TextProfiles.RUSSIAN.isStopWord("фотосинтез")).isFalse();

        // And the tokenizer drops them in a Russian text.
        assertThat(TextFeatures.tokens("Что такое фотосинтез?"))
                .contains("фотосинтез")
                .doesNotContain("что");
    }

    @Test
    @DisplayName("the Russian fold strips the case ending")
    void theRussianFoldStripsTheCaseEnding() {
        TextProfile russian = TextProfiles.RUSSIAN;

        // The three cases of one word meet.
        assertThat(russian.fold("фотосинтез")).isEqualTo("фотосинтез");
        assertThat(russian.fold("фотосинтеза")).isEqualTo("фотосинтез");
        assertThat(russian.fold("фотосинтезе")).isEqualTo("фотосинтез");
        // A word that is too short is left alone.
        assertThat(russian.fold("свет")).isEqualTo("свет");
    }

    @Test
    @DisplayName("the English fold is unchanged by the localisation")
    void theEnglishFoldIsUnchanged() {
        assertThat(TextFeatures.fold("molecules")).isEqualTo("molecul");
        assertThat(TextFeatures.fold("photosynthesis")).isEqualTo("photosynthesi");
        assertThat(TextProfiles.ENGLISH.fold("molecules")).isEqualTo("molecul");
    }

    @Test
    @DisplayName("the lexicon maps a term to its counterpart and holds nothing else")
    void theLexiconMapsATermToItsCounterpart() {
        BilingualLexicon lexicon = BilingualLexicon.instance();

        assertThat(lexicon.size()).isGreaterThan(100);
        // The lookup is on the folded stem, and the counterparts are stored
        // folded too - the lexicon holds stems on both sides.
        assertThat(lexicon.translations("фотосинтез")).contains("photosynthesi");
        assertThat(lexicon.translations("photosynthesi")).contains("фотосинтез");
        // And a term the lexicon does not hold has no counterpart.
        assertThat(lexicon.translations("нечто-чего-нет")).isEmpty();
        assertThat(lexicon.holds("фотосинтез")).isTrue();
        assertThat(lexicon.holds("нечто-чего-нет")).isFalse();
    }

    @Test
    @DisplayName("a Russian question reaches the English claim it is about")
    void aRussianQuestionReachesTheEnglishClaimItIsAbout(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer russian = workbench.ask("photosynthesis", "Что такое фотосинтез?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(russian.answered()).isTrue();
            assertThat(russian.concepts()).extracting(Concept::name).contains("Photosynthesis");

            // The same question in English, for the comparison.
            Answer english = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(english.answered()).isTrue();
            assertThat(english.concepts()).extracting(Concept::name).contains("Photosynthesis");
        }
    }

    @Test
    @DisplayName("a Russian question about a concept the workspace does not hold is still refused")
    void aRussianQuestionAboutAnUnknownConceptIsStillRefused(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis",
                    "Что такое квантовый компьютер?", AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isFalse();
        }
    }

    @Test
    @DisplayName("the lexicon does not change what an English question retrieves")
    void theLexiconDoesNotChangeWhatAnEnglishQuestionRetrieves(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // The support score of the W40 measurement is pinned by
            // SupportScoreTest: the lexicon must not move it.
            //
            // The value is 0.85 since W42.7, and the reason is recorded
            // in SupportScoreTest: the concept bridge added one more claim
            // to the answer's evidence, and the score is the mean of the
            // evidence's confidences. The lexicon itself still moves
            // nothing - the same value comes with and without it.
            Answer answer = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(answer.supportScore()).isEqualTo(0.85);
        }
    }

    @Test
    @DisplayName("the concept the question named comes first, in either language")
    void theConceptTheQuestionNamedComesFirst(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // The Russian question names the concept by its counterpart.
            // The name coverage used the RAW terms, so every concept scored
            // zero on it and the order fell back to the score - and the
            // answer to "Что такое фотосинтез?" was about cellular
            // respiration.
            Answer russian = workbench.ask("photosynthesis", "Что такое фотосинтез?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(russian.concepts()).isNotEmpty();
            assertThat(russian.concepts().get(0).name()).isEqualTo("Photosynthesis");
            assertThat(russian.statement()).contains("Photosynthesis");

            Answer russian2 = workbench.ask("photosynthesis", "Что такое клеточное дыхание?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(russian2.concepts()).isNotEmpty();
            assertThat(russian2.concepts().get(0).name()).isEqualTo("Cellular respiration");

            // And the English question is unchanged.
            Answer english = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(english.concepts()).isNotEmpty();
            assertThat(english.concepts().get(0).name()).isEqualTo("Photosynthesis");
        }
    }

    @Test
    @DisplayName("the profile's negations are the language's negations")
    void theProfilesNegationsAreTheLanguagesNegations() {
        assertThat(TextProfiles.ENGLISH.isNegation("not")).isTrue();
        assertThat(TextProfiles.ENGLISH.isNegation("never")).isTrue();
        assertThat(TextProfiles.RUSSIAN.isNegation("не")).isTrue();
        assertThat(TextProfiles.RUSSIAN.isNegation("нет")).isTrue();
        assertThat(TextProfiles.RUSSIAN.isNegation("никогда")).isTrue();
        assertThat(TextProfiles.RUSSIAN.isNegation("photosynthesis")).isFalse();
    }
}
