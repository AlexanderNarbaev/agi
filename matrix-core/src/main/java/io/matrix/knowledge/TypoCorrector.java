package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Corrects the typos in a question, and keeps the original (RECON-W42.3).
 *
 * <p>The mission's §5 lists the cases a dialogue must survive: misspellings,
 * missing spaces, transposed characters, punctuation errors, speech
 * recognition errors, mixed scripts, transliteration, abbreviations,
 * incomplete sentences, ambiguous references and informal expressions. §14
 * asks for at least 95 % intent preservation on a typo benchmark.
 *
 * <p><b>The original is never replaced.</b> The mission's §6 is explicit:
 * "must preserve the original message alongside any normalized
 * representation. Do not silently change meaning during spelling
 * correction." Every correction is therefore reported with the word it
 * changed and the word it changed it to, and the question as the user
 * wrote it is what the rest of the system sees.
 *
 * <p><b>What is corrected, and what is not.</b> A word is corrected only
 * when it is close enough to a word the workspace actually holds: a
 * deletion, an insertion, a substitution or a transposition of one or two
 * characters. The threshold is deliberately tight — a word that could be
 * several different words is left alone, because a wrong correction
 * answers a question the user did not ask.
 *
 * <p>The vocabulary is the workspace's own: the terms its claims use. A
 * word the workspace has never heard of is not corrected, because there is
 * nothing to correct it to — and a proper noun outside the knowledge is
 * exactly what that rule protects.
 */
public final class TypoCorrector {

    /** The largest edit distance that may be corrected. */
    public static final int MAXIMUM_EDITS = 2;

    /**
     * The edits that may be made to a word of a given length.
     *
     * <p>The threshold scales with the length, because a fixed threshold is
     * reckless on a short word. The first version of this corrector used a
     * flat distance of two, and it turned "work" into "core" and "point"
     * into "plant" - wrong corrections that then RESCUED questions the
     * workspace should have refused. A word of five letters is not half
     * rewritten for the sake of two edits.
     *
     * @param length the word's length
     * @return the largest distance that may be corrected
     */
    public static int maximumEdits(int length) {
        if (length <= 4) {
            return 0;
        }
        if (length <= 7) {
            return 1;
        }
        return MAXIMUM_EDITS;
    }

    /** One correction. */
    public record Correction(String original, String corrected, int distance) {
    }

    private final Set<String> vocabulary;
    /**
     * The names of the concepts the workspace holds.
     *
     * <p>A correction of two edits may change a word into a DIFFERENT word
     * entirely: "coefficient" is two edits from "inefficient", and that
     * correction is wrong. A two-edit correction is therefore accepted
     * only when it lands on one of the workspace's own concepts - the
     * words the workspace is about - while a one-edit correction is
     * accepted on its own, because one edit is very unlikely to be a
     * different word.
     */
    private final Set<String> conceptNames;
    private final Map<String, String> cache = new LinkedHashMap<>();

    /**
     * A corrector over a vocabulary.
     *
     * @param vocabulary the words the workspace holds
     */
    public TypoCorrector(Set<String> vocabulary) {
        this(vocabulary, Set.of());
    }

    /**
     * A corrector over a vocabulary and a set of concept names.
     *
     * @param vocabulary the words the workspace holds
     * @param conceptNames the names of the concepts the workspace holds
     */
    public TypoCorrector(Set<String> vocabulary, Set<String> conceptNames) {
        this.vocabulary = new LinkedHashSet<>();
        for (String word : vocabulary) {
            if (word != null && !word.isBlank()) {
                this.vocabulary.add(word.toLowerCase(Locale.ROOT));
            }
        }
        this.conceptNames = new LinkedHashSet<>();
        for (String name : conceptNames) {
            if (name != null && !name.isBlank()) {
                this.conceptNames.add(name.toLowerCase(Locale.ROOT));
            }
        }
    }

    /** A corrector over a workspace's claims. */
    public static TypoCorrector over(KnowledgeSnapshot snapshot) {
        Set<String> vocabulary = new LinkedHashSet<>();
        Set<String> conceptNames = new LinkedHashSet<>();
        for (Claim claim : snapshot.claims()) {
            vocabulary.addAll(TextFeatures.originalTokens(claim.proposition()));
        }
        for (Concept concept : snapshot.concepts()) {
            vocabulary.addAll(TextFeatures.originalTokens(concept.name()));
            conceptNames.add(concept.name().toLowerCase(Locale.ROOT));
            if (concept.definition() != null) {
                vocabulary.addAll(TextFeatures.originalTokens(concept.definition()));
            }
        }
        // W42.9: a Russian question contains Russian words, and the workspace
        // holds English ones. Without the bilingual terms in the vocabulary, a
        // Russian typo has nothing to be corrected towards, so the corrector
        // silently did nothing.
        for (String term : BilingualLexicon.instance().terms()) {
            vocabulary.add(term);
        }
        return new TypoCorrector(vocabulary, conceptNames);
    }

    /**
     * The corrections a question needs.
     *
     * @param question the question as the user wrote it
     * @return the corrections, in the order the words appear
     */
    public List<Correction> corrections(String question) {
        List<Correction> corrections = new ArrayList<>();
        if (question == null || question.isBlank()) {
            return corrections;
        }
        for (String word : TextFeatures.originalTokens(question)) {
            if (vocabulary.contains(word)) {
                continue;
            }
            String corrected = closest(word);
            if (corrected != null) {
                corrections.add(new Correction(word, corrected, distance(word, corrected)));
            }
        }
        return corrections;
    }

    /**
     * The corrected question.
     *
     * <p>The corrected words replace the wrong ones; everything else is
     * untouched. The original is still available through
     * {@link #corrections(String)} — the caller reports both.
     */
    public String corrected(String question) {
        if (question == null || question.isBlank()) {
            return question;
        }
        List<Correction> corrections = corrections(question);
        if (corrections.isEmpty()) {
            return question;
        }
        String corrected = question;
        for (Correction correction : corrections) {
            corrected = replaceWord(corrected, correction.original(), correction.corrected());
        }
        return corrected;
    }

    /**
     * The query terms a question yields, corrected.
     *
     * <p>Both the original and the corrected forms are returned, so that a
     * word the workspace holds under a different spelling still matches.
     */
    public List<String> correctedTerms(String question) {
        List<String> terms = new ArrayList<>();
        for (String word : TextFeatures.originalTokens(question)) {
            if (!terms.contains(word)) {
                terms.add(word);
            }
        }
        for (Correction correction : corrections(question)) {
            if (!terms.contains(correction.corrected())) {
                terms.add(correction.corrected());
            }
        }
        return terms;
    }

    /** The vocabulary's size, for the report. */
    public int vocabularySize() {
        return vocabulary.size();
    }

    private String closest(String word) {
        String cached = cache.get(word);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        int allowed = maximumEdits(word.length());
        if (allowed == 0) {
            cache.put(word, "");
            return null;
        }
        String best = null;
        int bestDistance = allowed + 1;
        int secondDistance = allowed + 1;
        for (String candidate : vocabulary) {
            if (Math.abs(candidate.length() - word.length()) > allowed) {
                continue;
            }
            int distance = distance(word, candidate);
            if (distance < bestDistance) {
                secondDistance = bestDistance;
                bestDistance = distance;
                best = candidate;
            } else if (distance < secondDistance) {
                secondDistance = distance;
            }
        }
        // An AMBIGUOUS correction is not a correction: if two words of the
        // vocabulary are equally close, the word is left alone, because a
        // wrong correction answers a question the user did not ask.
        if (best == null || secondDistance <= bestDistance) {
            cache.put(word, "");
            return null;
        }
        // A two-edit correction must land on one of the workspace's own
        // concepts. Two edits can be a different word entirely, and a
        // wrong correction answers a question the user did not ask.
        if (bestDistance > 1 && !conceptNames.contains(best)) {
            cache.put(word, "");
            return null;
        }
        cache.put(word, best);
        return best;
    }

    /**
     * The Levenshtein distance between two words.
     *
     * <p>The classic edit distance, with a transposition counted as one
     * edit — the "Damerau" case, because "fotosynthesis" for
     * "photosynthesis" is a transposition and not two substitutions ✓ ✓.
     *
     * @param left the first word
     * @param right the second word
     * @return the number of edits that turn one into the other
     */
    public static int distance(String left, String right) {
        if (left == null || right == null) {
            return Integer.MAX_VALUE;
        }
        if (left.equals(right)) {
            return 0;
        }
        int n = left.length();
        int m = right.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        int[] previousTwo = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
                if (i > 1 && j > 1 && left.charAt(i - 1) == right.charAt(j - 2)
                        && left.charAt(i - 2) == right.charAt(j - 1)) {
                    current[j] = Math.min(current[j], previousTwo[j - 2] + 1);
                }
            }
            int[] swap = previousTwo;
            previousTwo = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    /** Replaces a word in the text, keeping the word's case. */
    private String replaceWord(String text, String original, String corrected) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        String target = original.toLowerCase(Locale.ROOT);
        while (cursor < text.length()) {
            int index = lower.indexOf(target, cursor);
            if (index < 0) {
                out.append(text, cursor, text.length());
                break;
            }
            out.append(text, cursor, index);
            out.append(matchCase(text.charAt(index), corrected));
            cursor = index + target.length();
        }
        return out.toString();
    }

    /** Keeps the first letter's case. */
    private String matchCase(char first, String word) {
        if (Character.isUpperCase(first) && !word.isEmpty()) {
            return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return word;
    }
}
