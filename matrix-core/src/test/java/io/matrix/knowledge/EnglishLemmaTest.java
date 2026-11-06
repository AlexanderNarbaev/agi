package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W42.9 — the lemma of an English word.
 *
 * <p>Each rule has its own test, because the WAL's conclusion about this
 * defect was explicit: a rule that is changed needs a test that pins the
 * rule itself, not only a test that pins the outcome.
 */
class EnglishLemmaTest {

    @Test
    @DisplayName("a plural after a sibilant takes the -es")
    void aPluralAfterASibilantTakesTheEs() {
        // The standard English rule: the base ends in a sibilant, so the
        // plural is "-es" and the base survives whole.
        assertThat(EnglishLemma.base("processes")).isEqualTo("process");
        assertThat(EnglishLemma.base("boxes")).isEqualTo("box");
        assertThat(EnglishLemma.base("watches")).isEqualTo("watch");
        assertThat(EnglishLemma.base("dishes")).isEqualTo("dish");
    }

    @Test
    @DisplayName("a plural after a silent e keeps the e")
    void aPluralAfterASilentEKeepsTheE() {
        // THE RULE THAT WAS MISSING. "releases" is "release" and "-s", not
        // "releas" and "-es": the base ends in a silent "e", and the plural
        // of such a base is "-s".
        assertThat(EnglishLemma.base("releases")).isEqualTo("release");
        assertThat(EnglishLemma.base("phases")).isEqualTo("phase");
        assertThat(EnglishLemma.base("trees")).isEqualTo("tree");
    }

    @Test
    @DisplayName("a word that is not an -es plural is left alone")
    void aWordThatIsNotAnEsPluralIsLeftAlone() {
        // The fold already removes a plain "-s", so the lemma does not
        // repeat it: repeating it would make the lemma disagree with the
        // fold on words such as "photosynthesis" for no gain.
        assertThat(EnglishLemma.base("plants")).isEqualTo("plants");
        assertThat(EnglishLemma.base("oxygen")).isEqualTo("oxygen");
        assertThat(EnglishLemma.base("photosynthesis")).isEqualTo("photosynthesis");
    }

    @Test
    @DisplayName("the lemma of a word and of its plural agree")
    void theLemmaOfAWordAndOfItsPluralAgree() {
        // THE PROPERTY THE DEFECT BROKE. The folded terms differ ("release"
        // and "releas"), so the comparison has to happen on the lemma.
        assertThat(EnglishLemma.lemma("release")).isEqualTo(EnglishLemma.lemma("releases"));
        assertThat(EnglishLemma.lemma("process")).isEqualTo(EnglishLemma.lemma("processes"));
        assertThat(EnglishLemma.lemma("plant")).isEqualTo(EnglishLemma.lemma("plants"));
        assertThat(EnglishLemma.lemma("molecule")).isEqualTo(EnglishLemma.lemma("molecules"));
        assertThat(EnglishLemma.lemma("phase")).isEqualTo(EnglishLemma.lemma("phases"));
    }

    @Test
    @DisplayName("the lemma of a word that is not a plural is the word")
    void theLemmaOfAWordThatIsNotAPluralIsTheWord() {
        assertThat(EnglishLemma.lemma("oxygen")).isEqualTo("oxygen");
        // A short word is left alone: the rule needs a stem to work on.
        assertThat(EnglishLemma.lemma("is")).isEqualTo("is");
    }

    @Test
    @DisplayName("the lemma agrees with the fold on a word the fold does not shorten")
    void theLemmaAgreesWithTheFoldOnAWordTheFoldDoesNotShorten() {
        // The lemma is a SECOND channel, not a replacement: on a word the
        // fold handles, the two must not disagree.
        assertThat(EnglishLemma.lemma("photosynthesis"))
                .isEqualTo(TextProfiles.ENGLISH.fold("photosynthesis"));
        assertThat(EnglishLemma.lemma("plants"))
                .isEqualTo(TextProfiles.ENGLISH.fold("plants"));
    }

    @Test
    @DisplayName("a Russian word is not lemmatised by an English rule")
    void aRussianWordIsNotLemmatisedByAnEnglishRule() {
        // The Russian fold is exact — the case endings are stripped and the
        // stem is kept — so an English rule must not decide what a Russian
        // word is.
        assertThat(EnglishLemma.base("растений")).isEqualTo("растений");
        assertThat(EnglishLemma.lemma("фотосинтеза")).isEqualTo("фотосинтеза");
    }

    @Test
    @DisplayName("the lemmas of a text are the lemmas of its words, folded")
    void theLemmasOfATextAreTheLemmasOfItsWordsFolded() {
        assertThat(EnglishLemma.lemmasOf("Oxygenic photosynthesis releases oxygen."))
                .contains("release", "oxygen");
        // The stopwords carry no signal and their lemmas would be noise.
        assertThat(EnglishLemma.lemmasOf("the of a")).isEmpty();
        assertThat(EnglishLemma.lemmasOf(null)).isEmpty();
    }
}
