package io.matrix.knowledge;

import java.util.List;
import java.util.Set;

/**
 * The text profiles of the languages the system speaks (RECON-W42.3).
 *
 * <p>The mission's §6 asks for Russian and English first: "multilingual
 * dialogue (RU + EN first)". A profile holds the four language-dependent
 * things the text layer needs — the stopwords, the negations, the
 * question words and the suffix rules — and nothing else.
 *
 * <p><b>The language is detected, not assumed.</b> The detector counts the
 * scripts: a text written in Cyrillic is Russian, and one written in Latin
 * is English. That is a guess in the sense that "ru" is a convention
 * rather than a fact, and the profile says "unknown" when neither script
 * dominates — which is what a text of pure digits is.
 */
public final class TextProfiles {

    /** The English profile: the W41 rules, unchanged. */
    public static final TextProfile ENGLISH = new TextProfile(
            "en",
            Set.of(
                    "the", "a", "an", "of", "in", "to", "is", "are", "was", "were", "be", "been",
                    "and", "or", "for", "with", "that", "this", "these", "those", "it", "its",
                    "as", "by", "on", "at", "from", "into", "how", "what", "which", "who", "when",
                    "where", "why", "do", "does", "did", "can", "could", "would", "should",
                    "there", "their", "they", "them", "you", "your", "we", "our", "us", "i",
                    "he", "she", "not", "but", "than", "then", "so", "if", "about", "also",
                    "more", "most", "any", "all", "some", "such", "only", "other", "between",
                    "during", "under", "over", "each", "per", "via", "using", "used"),
            Set.of("not", "no", "never", "none", "neither", "nor", "without", "cannot"),
            Set.of("what", "which", "who", "whom", "whose", "when", "where", "why", "how",
                    "is", "are", "was", "were", "do", "does", "did", "can", "could"),
            List.of("ing", "ed", "es", "s"));

    /**
     * The Russian profile.
     *
     * <p>The stopwords are the Russian function words. The negations are the
     * Russian negations — and note that "не" is a prefix, so it is checked
     * as a prefix as well as a word: "невозможно" is a negation and "нет"
     * is a word.
     *
     * <p>The suffixes are the case endings, longest first. They are
     * stripped conservatively: the stem must keep at least three
     * characters, and a stem that would end in a vowel keeps it, because
     * the ending of "растение" is not the ending of "растени".
     */
    public static final TextProfile RUSSIAN = new TextProfile(
            "ru",
            Set.of(
                    "и", "в", "во", "не", "что", "он", "на", "я", "с", "со",
                    "как", "а", "то", "все", "она", "так", "его", "но", "да", "ты",
                    "к", "у", "же", "вы", "за", "бы", "по", "только", "ее", "мне",
                    "было", "вот", "от", "меня", "еще", "нет", "о", "из", "ему", "теперь",
                    "когда", "даже", "ну", "вдруг", "ли", "если", "уже", "или", "ни", "быть",
                    "был", "него", "до", "вас", "нибудь", "опять", "уж", "вам", "ведь", "там",
                    "потом", "себя", "ничего", "ей", "может", "они", "тут", "где", "есть", "надо",
                    "ней", "для", "мы", "тебя", "их", "чем", "была", "сам", "чтоб", "без",
                    "будто", "чего", "раз", "тоже", "себе", "под", "будет", "ж", "тогда", "кто",
                    "этот", "того", "потому", "этого", "какой", "совсем", "ним", "здесь", "этом", "один",
                    "почти", "мой", "тем", "чтобы", "нее", "были", "куда", "зачем", "всех", "никогда",
                    // RECON-W42.7: "откуда" is the interrogative of the same
                    // class as "куда" and "где", and it was missing while
                    // its neighbours were present. A Russian question that
                    // began with it reported the word as uncovered by the
                    // workspace.
                    "откуда",
                    "можно", "при", "наконец", "два", "об", "другой", "хоть", "после", "над", "больше",
                    "тот", "через", "эти", "нас", "про", "всего", "них", "какая", "много", "разве",
                    "три", "эту", "моя", "впрочем", "хорошо", "свою", "этой", "перед", "иногда", "лучше",
                    "чуть", "том", "нельзя", "такой", "им", "более", "всегда", "конечно", "всю", "между",
                    "что такое", "такое", "такие", "такого", "какие", "каких", "чей", "чьи", "сколько"),
            Set.of("не", "нет", "ни", "никогда", "ничего", "нельзя", "без"),
            Set.of("что", "кто", "где", "когда", "почему", "зачем", "как", "какой", "какая",
                    "какие", "чей", "чья", "чьи", "сколько", "разве", "неужели"),
            List.of("иями", "ями", "ами",
                    // RECON-W42.9: the genitive of an adjective is "-ого",
                    // and the list had "-его" without it. So "углекислого"
                    // (the genitive of "углекислый") kept its ending, and
                    // the premise of a Russian question never reached the
                    // lexicon's entry for "углекислый" — the two are the
                    // same word in different cases, and the case is
                    // exactly what the fold is for.
                    "ого", "его", "ому", "ыми", "ими", "ах", "ях", "ой",
                    "ей", "ый", "ий", "ая", "яя", "ое", "ее", "ые", "ие", "ов", "ев",
                    "ам", "ям", "ом", "ем", "ах", "ях", "ой", "ей", "у", "ю", "а", "я",
                    "ы", "и", "ь", "е"));

    /** The profile used when the language cannot be determined. */
    public static final TextProfile UNKNOWN = new TextProfile(
            "unknown", Set.of(), Set.of(), Set.of(), List.of());

    private TextProfiles() {
    }

    /**
     * The language of a text, detected from the scripts it is written in.
     *
     * @param text the text to read
     * @return "ru", "en", or "unknown"
     */
    public static String detect(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        int cyrillic = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'а' && c <= 'я') || (c >= 'ё' && c <= 'ё')
                    || (c >= 'А' && c <= 'Я') || (c >= 'Ё' && c <= 'Ё')) {
                cyrillic++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                latin++;
            }
        }
        if (cyrillic == 0 && latin == 0) {
            return "unknown";
        }
        // The script with the most letters wins, but a text that is mostly
        // one script with a few letters of the other is still that script.
        if (cyrillic > latin * 2) {
            return "ru";
        }
        if (latin > cyrillic * 2) {
            return "en";
        }
        return cyrillic >= latin ? "ru" : "en";
    }

    /** The profile of a language code. */
    public static TextProfile ofLanguage(String language) {
        if ("ru".equals(language)) {
            return RUSSIAN;
        }
        if ("en".equals(language)) {
            return ENGLISH;
        }
        return UNKNOWN;
    }

    /** The profile of a text, detected. */
    public static TextProfile ofText(String text) {
        return ofLanguage(detect(text));
    }
}
