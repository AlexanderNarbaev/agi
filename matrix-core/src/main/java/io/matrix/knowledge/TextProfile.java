package io.matrix.knowledge;

import java.util.List;
import java.util.Set;

/**
 * The text layer of one language (RECON-W42.3).
 *
 * <p>RECON-W42.0 found that the whole text layer was English-only by
 * construction: an 80-word English stopword list, an English-only suffix
 * fold, an English negation list, and English task-classification keywords.
 * The W42 benchmark measured the consequence — **the five Russian items
 * scored 0/5**, because a Russian question shares no token with the English
 * knowledge it asks about.
 *
 * <p><b>A profile is a set of language-specific rules, not a model.</b> It
 * is deterministic, offline, and inspectable: a reader can see exactly
 * which words are dropped and which suffixes are stripped. That matters
 * more than a larger dictionary would: the mission's §6 warns that
 * "translation is not evidence that two propositions are equivalent", and
 * a rule set that a human can read is a rule set whose mistakes a human can
 * find.
 *
 * @param language the language's code
 * @param stopwords the words that carry no retrieval signal
 * @param negations the words that negate
 * @param questionWords the words that open an interrogative
 * @param suffixes the endings that are stripped, longest first
 */
public record TextProfile(
        String language,
        Set<String> stopwords,
        Set<String> negations,
        Set<String> questionWords,
        List<String> suffixes) {

    public TextProfile {
        stopwords = Set.copyOf(stopwords == null ? Set.of() : stopwords);
        negations = Set.copyOf(negations == null ? Set.of() : negations);
        questionWords = Set.copyOf(questionWords == null ? Set.of() : questionWords);
        suffixes = List.copyOf(suffixes == null ? List.of() : suffixes);
    }

    /** Whether a word carries no retrieval signal in this language. */
    public boolean isStopWord(String word) {
        return word != null && stopwords.contains(word);
    }

    /** Whether a word negates in this language. */
    public boolean isNegation(String word) {
        return word != null && negations.contains(word);
    }

    /**
     * The fold of a word in this language.
     *
     * <p>The English fold strips a plural, a gerund, a past tense and a
     * silent "e". The Russian fold strips the case endings, because Russian
     * marks the case on the word and a question about "фотосинтезе" asks
     * about "фотосинтез".
     *
     * @param token the word as it was written
     * @return the stem
     */
    public String fold(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        for (String suffix : suffixes) {
            if (token.length() > suffix.length() + 2 && token.endsWith(suffix)) {
                // The stem keeps what is left, vowel or not: "растение" and
                // "растения" both fold to "растен", and a Russian stem that
                // ends in a vowel is a normal stem.
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    /** Whether the word is written in the Cyrillic script. */
    public static boolean isCyrillic(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        char first = word.charAt(0);
        return (first >= 'а' && first <= 'я') || (first >= 'ё' && first <= 'ё')
                || (first >= 'А' && first <= 'Я') || (first >= 'Ё' && first <= 'Ё');
    }

}
