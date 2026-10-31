package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Explicit text features for retrieval (owner directive §21, §27).
 *
 * <p>The audit behind this mission found the repository's central weakness:
 * every learner in it consumes a HASH of the text — an FNV hash of the first
 * 64 characters, a 256-bit hash of "input + reply" — so two questions that
 * mean the same thing share no features at all. A knowledge application has
 * to do better than that, and the fix is not a cleverer hash: it is explicit
 * features, so that "photosynthesis" and "photosynthetic" share the tokens
 * that carry the meaning.
 *
 * <p>Everything here is deterministic and free of wall-clock time and random
 * numbers, as CONSTITUTION Article I requires of anything that participates
 * in a decision.
 */
public final class TextFeatures {

    /** Words that carry no retrieval signal in a question or a proposition. */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "of", "in", "to", "is", "are", "was", "were", "be", "been",
            "and", "or", "for", "with", "that", "this", "these", "those", "it", "its",
            "as", "by", "on", "at", "from", "into", "how", "what", "which", "who", "when",
            "where", "why", "do", "does", "did", "can", "could", "would", "should", "there",
            "their", "they", "them", "you", "your", "we", "our", "us", "i", "he", "she",
            "not", "but", "than", "then", "so", "if", "about", "also", "more", "most",
            "any", "all", "some", "such", "only", "other", "between", "during", "under",
            "over", "each", "per", "via", "using", "used");

    private TextFeatures() {
    }

    /**
     * Split a text into retrieval tokens: lower-cased, punctuation removed,
     * stopwords dropped, and a deliberately conservative suffix fold so that
     * a plural or a gerund still matches its stem.
     *
     * <p>The fold is intentionally minimal. A full stemmer would merge words
     * that are not the same word, and a retrieval system that confuses
     * "gases" with "gases' boundary" is worse than one that misses.
     */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        for (char ch : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                current.append(ch);
            } else {
                emit(current, out);
            }
        }
        emit(current, out);
        return out;
    }

    private static void emit(StringBuilder current, List<String> out) {
        if (current.length() == 0) {
            return;
        }
        String token = current.toString();
        current.setLength(0);
        if (token.length() < 3 && !Character.isDigit(token.charAt(0))) {
            return;
        }
        // The stopword test is on the word as it was written, not on the
        // folded stem: a fold would otherwise turn a stopword into a
        // fragment that no longer looks like one.
        if (STOPWORDS.contains(token)) {
            return;
        }
        out.add(fold(token));
    }

    /**
     * A minimal suffix fold: plural, gerund, past tense and a trailing
     * silent "e".
     *
     * <p>The trailing "e" matters for consistency: without it, "store",
     * "stored" and "stores" would fold to three different stems and a
     * question about memory storage would fail to match a claim about
     * stored energy.
     */
    static String fold(String token) {
        String folded = token;
        if (folded.length() > 5 && folded.endsWith("ing")) {
            folded = folded.substring(0, folded.length() - 3);
        } else if (folded.length() > 4 && folded.endsWith("ed")) {
            folded = folded.substring(0, folded.length() - 2);
        } else if (folded.length() > 4 && folded.endsWith("es")) {
            folded = folded.substring(0, folded.length() - 2);
        } else if (folded.length() > 3 && folded.endsWith("s") && !folded.endsWith("ss")) {
            folded = folded.substring(0, folded.length() - 1);
        }
        if (folded.length() > 3 && folded.endsWith("e")) {
            folded = folded.substring(0, folded.length() - 1);
        }
        return folded;
    }

    /**
     * The inverse document frequency of every term over a set of documents.
     *
     * @param documents the token sets of the documents, one per document
     * @return term to IDF weight; a term that appears in every document has a
     *     weight near zero, which is exactly the behaviour a retrieval
     *     system wants from a word like "the"
     */
    public static Map<String, Double> idf(List<Set<String>> documents) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Set<String> document : documents) {
            for (String term : document) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        int total = documents.size();
        Map<String, Double> weights = new HashMap<>();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            weights.put(entry.getKey(), Math.log(1.0 + (double) total / entry.getValue()));
        }
        return weights;
    }

    /** The distinct terms of a text, in first-seen order. */
    public static Set<String> terms(String text) {
        return new LinkedHashSet<>(tokens(text));
    }
}
