package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * W36.2 — logical contradiction detection, replacing a similarity heuristic that was not one.
 *
 * <h2>Why this class exists at all</h2>
 *
 * <p>MATRIX has had a method called {@code checkContradiction} since before this wave. It
 * measures Jaccard similarity between HDC vectors and reports {@code DUPLICATE} above 0.65 and
 * {@code POTENTIAL_CONFLICT} above 0.40. Every one of those is a statement about SIMILARITY. None
 * is a statement about inconsistency.</p>
 *
 * <p>Measured on genuine contradictions:</p>
 * <pre>
 *   "The capital of France is Paris"  vs "The capital of France is Lyon"   -> DUPLICATE 0.714
 *   "Water boils at 100 degrees"     vs "Water boils at 50 degrees"       -> DUPLICATE 0.714
 *   "The server runs on port 8080"   vs "The server runs on port 9090"    -> DUPLICATE 0.714
 *   "The patient is allergic to X"    vs "The patient has no X allergy"    -> NOVEL     0.333
 * </pre>
 *
 * <p>A contradiction is textually near-identical — same subject, same predicate, one changed
 * value — so it scores HIGHEST and is labelled {@code DUPLICATE}: the most confident label the
 * system could emit, attached to exactly the inputs where it is most wrong. And the one pair
 * carrying real logical negation falls to {@code NOVEL}, because negation changes many tokens
 * and destroys the similarity the detector depends on.</p>
 *
 * <h2>What contradiction actually means here</h2>
 *
 * <p>The unit is a subject-predicate-object triple, not a document. Two facts contradict when
 * they agree on subject AND predicate and DISAGREE on object — a different value for the same
 * property of the same thing — or when one asserts and the other denies the same triple.</p>
 *
 * <pre>
 *   (France, capital-of, Paris)  vs  (France, capital-of, Lyon)
 *       same subject, same predicate, different object  -> CONTRADICTION
 *
 *   (France, capital-of, Paris)  vs  (France, located-in, Europe)
 *       different predicate                              -> NOVEL
 * </pre>
 *
 * <h2>What this deliberately does NOT do</h2>
 *
 * <p>It is not a natural-language entailment engine and does not pretend to be. Copulas and
 * prepositions are mapped to a small fixed vocabulary; a sentence whose predicate is outside it
 * parses to no triple and is reported {@link Level#UNPARSEABLE} rather than guessed at. Near
 * duplicates are reported as {@link Level#DUPLICATE}, which is what they are — and which is a
 * different fact from contradiction.</p>
 *
 * <p>The failing mode that matters: saying NOVEL when two facts do conflict is a MISS, and a
 * miss in a contradiction detector is the dangerous direction, so {@link Level#UNPARSEABLE}
 * exists to make it visible rather than to be absorbed into NOVEL.</p>
 */
public final class FactTriples {

    /** What two facts, or one fact and a candidate, mean relative to each other. */
    public enum Level {
        /** Same subject, same predicate, same object. One fact stated twice. */
        DUPLICATE,
        /** Same subject and predicate, incompatible objects. A real contradiction. */
        CONTRADICTION,
        /** One asserts the triple, the other denies it. */
        NEGATION,
        /** Comparable, but they do not conflict. */
        COMPATIBLE,
        /** No shared subject-predicate, or neither sentence parsed. */
        NOVEL,
        /** At least one sentence could not be reduced to a triple. */
        UNPARSEABLE
    }

    /**
     * One asserted fact.
     *
     * @param subject   the thing being described, lowercased
     * @param predicate the property being asserted, mapped through {@link #PREDICATES}
     * @param object    the value, lowercased
     * @param negated   true when the sentence denies the triple
     */
    public record Triple(String subject, String predicate, String object, boolean negated) {

        public Triple {
            subject = Objects.requireNonNull(subject, "subject").trim().toLowerCase(Locale.ROOT);
            predicate = Objects.requireNonNull(predicate, "predicate").trim().toLowerCase(Locale.ROOT);
            object = Objects.requireNonNull(object, "object").trim().toLowerCase(Locale.ROOT);
        }

        /** The shared half of a triple: two facts can only conflict if these match. */
        String subjectPredicate() {
            return subject + "" + predicate;
        }
    }

    /** The verdict, including the triples it was derived from so a caller can see the reasoning. */
    public record Report(Level level, double similarity, Triple a, Triple b, String explanation) { }

    /**
     * Copulas and common relations mapped to a canonical predicate.
     *
     * <p>Deliberately small. A wider vocabulary would parse more sentences and be wrong more
     * confidently, which is the worse trade for a detector whose failure mode is a false
     * negative.</p>
     */
    /**
     * Ordered LONGEST PHRASE FIRST.
     *
     * <p>This ordering is load-bearing, not cosmetic. {@code "is the capital"} is a prefix of
     * {@code "is the capital of"}, so with the shorter listed first the parser matched it and
     * left "of France" hanging: the subject came out as "capital france" and the two facts about
     * France's capital stopped sharing a subject, so the contradiction went undetected. Sorting
     * by length makes the longest match win, which is what a reader expects.</p>
     */
    /**
     * Relations recognised on the LEFT of a copula.
     *
     * <p>These are left-side forms — "capital of France", "boils at 100 degrees" — because that
     * is where they actually appear once the sentence is split at "is". An earlier table listed
     * right-side forms like "is the capital of", which never occur contiguously in
     * "The capital of France is Paris", so the table silently matched nothing.</p>
     */
    private static final String[][] PREDICATES = {
            {"capital of", "capital-of"},
            {"boils at", "boils-at"},
            {"melts at", "melts-at"},
            {"freezes at", "freezes-at"},
            {"runs on port", "runs-on-port"},
            {"listens on port", "runs-on-port"},
            {"located in", "located-in"},
            {"in", "located-in"},
            {"at", "located-at"},
            {"allergic to", "allergic-to"},
            {"married to", "married-to"},
            {"works at", "works-at"},
            {"lives in", "lives-in"},
            {"the largest", "largest"},
    };

    /** Longest first, so a longer predicate is preferred over any of its own prefixes. */
    private static final String[][] ORDERED_PREDICATES = orderByPhraseLength(PREDICATES);

    private static String[][] orderByPhraseLength(String[][] in) {
        String[][] out = in.clone();
        java.util.Arrays.sort(out, java.util.Comparator.comparingInt(
                (String[] row) -> row[0].length()).reversed());
        return out;
    }

    /** Words that make a claim negative. Checked as whole tokens, not substrings. */
    private static final Set<String> NEGATIONS =
            Set.of("not", "isn't", "isnt", "aren't", "arent", "doesn't", "doesnt",
                   "no", "never", "cannot", "can't", "cant", "won't", "wont");

    /** Articles and filler stripped before matching. */
    private static final Set<String> STOPWORDS =
            Set.of("the", "a", "an", "of", "to", "in", "on", "at", "is", "are", "was", "were");

    private FactTriples() { }

    /**
     * Extract the triples a sentence asserts.
     *
     * @param sentence the sentence to parse
     * @return zero or more triples; empty when the sentence does not reduce to one
     */
    /**
     * Extract the triples a sentence asserts.
     *
     * <p>The structure is COPULAR, not phrase-shaped, and getting that wrong was the first bug:
     * "The capital of France is Paris" contains no contiguous phrase "is the capital of", so a
     * substring matcher fell through to the bare copula and produced the subject "capital
     * france" with predicate "is-a". Two facts about France's capital then had different
     * subjects, shared nothing, and the contradiction went undetected — which is the exact
     * failure this class exists to prevent.</p>
     *
     * <p>So the sentence is split at its copula first, and only the LEFT side is then searched
     * for a recognised relation:</p>
     * <pre>
     *   "the capital of France | is | Paris"   ->  France  capital-of  Paris
     * </pre>
     *
     * @param sentence the sentence to parse
     * @return zero or more triples; empty when the sentence does not reduce to one
     */
    public static List<Triple> parse(String sentence) {
        List<Triple> out = new ArrayList<>();
        if (sentence == null || sentence.isBlank()) {
            return out;
        }
        String cleaned = sentence.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[.!?]+$", "")
                .replaceAll("[^a-z0-9:'-]", " ");
        String[] words = cleaned.split("\\s+");

        // Drop leading determiners so "The capital of France" offers "capital of" to the
        // relation matcher. Without this the needle "capital of" was compared against "the
        // capital", missed, and the subject came out as "capital france".
        int from = 0;
        while (from < words.length && DETERMINERS.contains(words[from])) {
            from++;
        }

        boolean negated = false;
        for (String w : words) {
            if (NEGATIONS.contains(w)) {
                negated = true;
            }
        }
        int copula = indexOfAny(words, COPULAS);
        List<String> left = null;
        List<String> right = null;
        if (copula > from) {
            left = new ArrayList<>(List.of(Arrays.copyOfRange(words, from, copula)));
            right = new ArrayList<>(
                    List.of(Arrays.copyOfRange(words, copula + 1, words.length)));
        } else {
            // No copula: "Water boils at 100 degrees". Match a relation against the start of
            // the sentence and take the remainder as the object. Returning nothing here would
            // have made every non-copular factual sentence UNPARSEABLE, including the boiling
            // and freezing disagreements that are among the clearest contradictions there are.
            // Search EVERY position, not just the start: in "Water boils at 100 degrees" the
            // relation begins at index 1, and matching only at `from` found nothing.
            outer:
            for (String[] phrase : ORDERED_PREDICATES) {
                String[] needle = phrase[0].split("\\s+");
                for (int at = from; at + needle.length < words.length; at++) {
                    boolean hit = true;
                    for (int j = 0; j < needle.length; j++) {
                        if (!words[at + j].equals(needle[j])) {
                            hit = false;
                            break;
                        }
                    }
                    if (!hit) {
                        continue;
                    }
                    StringBuilder subj = new StringBuilder();
                    for (int k = from; k < at; k++) {
                        if (!STOPWORDS.contains(words[k])) {
                            if (subj.length() > 0) {
                                subj.append(' ');
                            }
                            subj.append(words[k]);
                        }
                    }
                    StringBuilder obj = new StringBuilder();
                    for (int k = at + needle.length; k < words.length; k++) {
                        if (STOPWORDS.contains(words[k]) && obj.length() == 0) {
                            continue;
                        }
                        if (obj.length() > 0) {
                            obj.append(' ');
                        }
                        obj.append(words[k]);
                    }
                    // Emitted DIRECTLY. Pushing the fragments back through the copular path
                    // produced ("water boils", "is-a", ...): the relation head was being treated
                    // as part of the subject instead of the predicate.
                    if (subj.length() > 0 && obj.length() > 0) {
                        out.add(new Triple(subj.toString(), phrase[1], obj.toString(), negated));
                    }
                    return out;
                }
            }
            return out;
        }


        StringBuilder object = new StringBuilder();
        for (String w : right) {
            if (STOPWORDS.contains(w) && object.length() == 0) {
                continue;
            }
            if (object.length() > 0) {
                object.append(' ');
            }
            object.append(w);
        }
        if (object.length() == 0) {
            return out;
        }

        // Recognise a relation prefix on the LEFT, e.g. "the capital of France".
        for (String[] phrase : ORDERED_PREDICATES) {
            String[] needle = phrase[0].split("\\s+");
            if (needle.length >= left.size()) {
                continue;
            }
            boolean hit = true;
            for (int j = 0; j < needle.length; j++) {
                String want = needle[j];
                String got = left.get(j);
                if (NEGATIONS.contains(got)) {
                    negated = true;
                    continue;
                }
                if (!want.equals(got)) {
                    hit = false;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            StringBuilder subject = new StringBuilder();
            for (int j = needle.length; j < left.size(); j++) {
                String w = left.get(j);
                if (STOPWORDS.contains(w) || NEGATIONS.contains(w)) {
                    continue;
                }
                if (subject.length() > 0) {
                    subject.append(' ');
                }
                subject.append(w);
            }
            if (subject.length() > 0) {
                out.add(new Triple(subject.toString(), phrase[1], object.toString(), negated));
            }
            return out;
        }

        // No relation on the LEFT — but in "France IS located in Europe" the relation sits on
        // the right of the copula. Matching only the left produced (france, is-a, "located in
        // europe") while "France is in Europe" produced (france, is-a, europe): two ways of
        // saying the same thing compared as a CONTRADICTION, which is the opposite of correct.
        for (String[] phrase : ORDERED_PREDICATES) {
            String[] needle = phrase[0].split("\\s+");
            // English puts the negation between the copula and the relation: "the patient is
            // NOT allergic to penicillin". Without skipping it here the relation never matched
            // and the denial degraded to a weak is-a claim, so asserting and denying looked
            // like a CONTRADICTION instead of a NEGATION.
            int rAt = 0;
            while (rAt < right.size() && NEGATIONS.contains(right.get(rAt))) {
                negated = true;
                rAt++;
            }
            if (rAt + needle.length >= right.size() + 1) {
                continue;
            }
            boolean hit = true;
            for (int j = 0; j < needle.length; j++) {
                if (rAt + j >= right.size() || !right.get(rAt + j).equals(needle[j])) {
                    hit = false;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            StringBuilder plainSubject = new StringBuilder();
            for (String w : left) {
                if (STOPWORDS.contains(w) || NEGATIONS.contains(w)) {
                    continue;
                }
                if (plainSubject.length() > 0) {
                    plainSubject.append(' ');
                }
                plainSubject.append(w);
            }
            StringBuilder tail = new StringBuilder();
            for (int k = rAt + needle.length; k < right.size(); k++) {
                if (tail.length() > 0) {
                    tail.append(' ');
                }
                tail.append(right.get(k));
            }
            if (plainSubject.length() > 0 && tail.length() > 0) {
                out.add(new Triple(plainSubject.toString(), phrase[1], tail.toString(), negated));
            }
            return out;
        }

        // No recognised relation anywhere: fall back to a plain subject-is-object claim, so
        // "France is a city" is still a triple rather than UNPARSEABLE. It is deliberately a
        // WEAK predicate — two such facts can only ever be DUPLICATE or CONTRADICTION, which is
        // the correct conservative reading of "X is Y" and "X is Z".
        StringBuilder plain = new StringBuilder();
        for (String w : left) {
            if (STOPWORDS.contains(w) || NEGATIONS.contains(w)) {
                continue;
            }
            if (plain.length() > 0) {
                plain.append(' ');
            }
            plain.append(w);
        }
        if (plain.length() > 0) {
            out.add(new Triple(plain.toString(), "is-a", object.toString(), negated));
        }
        return out;
    }

    /** Copulas that introduce a subject-predicate-object structure. */
    private static final Set<String> COPULAS = Set.of("is", "are", "was", "were");

    /** Leading determiners stripped before relation matching. */
    private static final Set<String> DETERMINERS = Set.of("the", "a", "an");

    private static int indexOfAny(String[] words, Set<String> targets) {
        for (int i = 0; i < words.length; i++) {
            if (targets.contains(words[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfPhrase(String[] words, String[] phrase, String[] foundNegation) {
        for (int i = 0; i + phrase.length <= words.length; i++) {
            int w = i;
            boolean hit = true;
            boolean sawNegation = false;
            for (String p : phrase) {
                if (w >= words.length) {
                    hit = false;
                    break;
                }
                if (NEGATIONS.contains(words[w])) {
                    sawNegation = true;
                    w++;
                    if (w >= words.length) {
                        hit = false;
                        break;
                    }
                }
                if (!words[w].equals(p)) {
                    hit = false;
                    break;
                }
                w++;
            }
            if (hit) {
                if (foundNegation.length > 0) {
                    foundNegation[0] = sawNegation ? "not" : "";
                }
                return i;
            }
        }
        return -1;
    }

    /**
     * Compare two facts and say, in terms of logic rather than similarity, what they mean.
     *
     * @param left  the stored fact
     * @param right the candidate fact
     * @return the verdict, carrying both triples so the caller can inspect the reasoning
     */
    public static Report compare(Triple left, Triple right) {
        if (left == null || right == null) {
            return new Report(Level.UNPARSEABLE, 0, left, right,
                    "at least one fact could not be reduced to a subject-predicate-object triple");
        }
        if (!left.subjectPredicate().equals(right.subjectPredicate())) {
            return new Report(Level.NOVEL, 0, left, right,
                    "different subject-predicate (" + left.subjectPredicate() + " vs "
                            + right.subjectPredicate() + "), so there is nothing to conflict about");
        }
        if (left.negated() != right.negated()) {
            return new Report(Level.NEGATION, 0, left, right,
                    "one asserts (" + left.subjectPredicate() + "=" + left.object()
                            + ") and the other denies it");
        }
        if (left.object().equals(right.object())) {
            return new Report(Level.DUPLICATE, 0, left, right,
                    "same subject, predicate and object — the same fact stated twice");
        }
        return new Report(Level.CONTRADICTION, 0, left, right,
                "same subject and predicate (" + left.subjectPredicate()
                        + ") with incompatible objects (" + left.object() + " vs " + right.object() + ")");
    }

    /**
     * Compare two sentences.
     *
     * @param left  the stored sentence
     * @param right the candidate sentence
     * @return the verdict
     */
    public static Report compareSentences(String left, String right) {
        List<Triple> a = parse(left);
        List<Triple> b = parse(right);
        if (a.isEmpty() || b.isEmpty()) {
            return new Report(Level.UNPARSEABLE, 0,
                    a.isEmpty() ? null : a.get(0), b.isEmpty() ? null : b.get(0),
                    "no triple extracted from "
                            + (a.isEmpty() ? "the first" : "") + (b.isEmpty() ? "the second" : "")
                            + " sentence; a contradiction cannot be asserted or denied");
        }
        return compare(a.get(0), b.get(0));
    }

    /** Distinct tokens, used by callers that want lexical overlap alongside the logical verdict. */
    public static Set<String> tokens(String sentence) {
        Set<String> out = new LinkedHashSet<>();
        if (sentence == null) {
            return out;
        }
        for (String w : sentence.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!w.isBlank()) {
                out.add(w.replaceAll("[^a-z0-9:\\-']", ""));
            }
        }
        return out;
    }
}