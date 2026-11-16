package io.matrix.brain.runtime.research;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * EPISTEMIC-INTEGRITY P3 — the term rules the outcome verifiers share.
 *
 * <p><b>What this is.</b> One place where "a content term" is defined, so a verifier's
 * coverage number means the same thing wherever it is computed, and so a reader can
 * check the rule by hand instead of trusting a proportion.</p>
 *
 * <p><b>The rule (mine — {@code [speculative]}).</b> A term is a word of at least three
 * characters, lower-cased, that is not in the stop list. The stop list is the ordinary
 * English function words; it is deliberately small, because a stop list that is too large
 * removes exactly the words that carry a factual claim ("which", "what", "does"). Numbers
 * are kept: "93" is a fact.</p>
 */
public final class OutcomeTerms {

    /** The function words that carry no content. */
    private static final Set<String> STOP = new java.util.LinkedHashSet<>(java.util.Arrays.asList(
            "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had",
            "her", "was", "one", "our", "out", "day", "get", "has", "him", "his", "how",
            "its", "may", "new", "now", "old", "see", "two", "who", "did", "yes", "she",
            "this", "that", "with", "from", "they", "them", "then", "than", "there",
            "these", "those", "what", "which", "when", "where", "while", "will", "would",
            "could", "should", "been", "being", "have", "into", "over", "such", "some",
            "only", "also", "does", "done", "each", "more", "most", "much", "very",
            "about", "because", "before", "after", "between", "under", "over", "into",
            "в", "и", "не", "на", "что", "как", "это", "какой", "какая"));

    private OutcomeTerms() {
    }

    /** The content terms of a text, in order of first appearance, without duplicates. */
    public static List<String> contentTerms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() < 3) continue;
            if (STOP.contains(word)) continue;
            if (seen.add(word)) out.add(word);
        }
        return out;
    }
}
