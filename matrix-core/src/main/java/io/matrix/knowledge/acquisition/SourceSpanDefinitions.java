package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimSpan;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.Source;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * EPISTEMIC-INTEGRITY P1 — the SOURCE-SPAN-ANCHORED DEFINITION of a concept.
 *
 * <p><b>Why this exists.</b> The importer writes every concept with
 * {@code definition = null}: the concept layer had 348 claims, 326 concepts and
 * <b>0 usable definitions</b> (the measured baseline in
 * {@code docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md}). A concept that
 * cannot say what it is cannot be judged against anything, and the retriever's
 * searchable text carries only the claim's proposition and the concept's
 * name.</p>
 *
 * <p><b>THE RULE — the tight v1.1 rule, unchanged.</b> This class implements the
 * frozen rule of {@code docs-v2/engineering/C2A-CANDIDATE-FREEZE.md} §3.1 as the
 * freeze v1.1 froze it, and nothing looser:</p>
 *
 * <ol>
 *   <li>the concept's <b>own source documents</b> are the sources of the
 *       concept's claims, taken in the order the snapshot lists the sources;</li>
 *   <li>the sentences of those documents are scanned <b>in document order</b> —
 *       a sentence here is the claim's own proposition, and the document order
 *       is the order of the claim's span in the extracted text;</li>
 *   <li>a sentence <b>qualifies</b> when it contains the concept's name as a
 *       word (for a multi-word name: the whole token sequence, case
 *       insensitively, as a contiguous subsequence of the sentence's tokens)
 *       <b>and the name is immediately followed by a definitional copula</b>
 *       (the adjacency rule), and the sentence is at most
 *       {@link #MAX_DEFINITION_LENGTH} characters long;</li>
 *   <li>the <b>first</b> qualifying sentence wins, and the definition is that
 *       sentence <b>verbatim</b>: never paraphrased, never shortened, never
 *       invented. A concept with no qualifying sentence has <b>no definition</b>
 *       — that is an honest state, not a gap to be filled.</li>
 * </ol>
 *
 * <p><b>Why the adjacency.</b> The loose v1.0 rule asked only whether the
 * sentence held the name <i>and</i> a copula somewhere; it could not tell a
 * definition from a sentence that names the concept and says something else
 * about it. The tight rule asks whether the copula <b>follows the name</b>.</p>
 *
 * <p><b>What is NOT decided here.</b> The identity of a concept — the referent
 * key, the alias and the resolution policy — is owner-gated and is untouched:
 * this class reads the concept's name and writes the concept's definition, and
 * it never renames a concept, never changes a concept id and never moves a
 * claim.</p>
 *
 * <p><b>Determinism.</b> No clock, no randomness, no network: the same claims
 * and the same sources always yield the same definition.</p>
 */
public final class SourceSpanDefinitions {

    /**
     * The name of the extraction method, recorded on every definition.
     *
     * <p>The name is the freeze's, so a definition can be traced to the rule
     * that produced it.</p>
     */
    public static final String DEFINITION_METHOD = "definitional-adjacency-first-sentence-v1.1";

    /**
     * The longest a definition may be, in characters.
     *
     * <p>A "sentence" here is the proposition of a claim, and the extractor
     * joins the lines of a segment, so a sentence can be a whole paragraph.
     * The cap keeps a paragraph out of a concept's definition. The cap is a
     * property of the RULE, not of the corpus: it is stated in the freeze
     * v1.1 §3.1 and applied to every sentence.</p>
     */
    public static final int MAX_DEFINITION_LENGTH = 400;

    /**
     * The definitional copulas, a closed vocabulary.
     *
     * <p>The freeze v1.1 §3.1 lists exactly these, and the list is closed: a
     * word that is not here does not make a definition.</p>
     */
    public static final List<String> DEFINITIONAL_COPULAS = List.of(
            "is", "are", "refers to", "means", "describes", "consists of",
            "is defined as", "is called");

    /**
     * One definition, with the span it was read from.
     *
     * @param conceptId the concept the definition belongs to
     * @param conceptName the concept's name, as the concept carries it
     * @param text the definition — the qualifying sentence, verbatim
     * @param sourceId the source the sentence was read from
     * @param startOffset where the sentence starts in the extracted text
     * @param endOffset where the sentence ends in the extracted text
     * @param method the name of the extraction method
     */
    public record Definition(
            String conceptId,
            String conceptName,
            String text,
            String sourceId,
            int startOffset,
            int endOffset,
            String method) {
    }

    /**
     * One sentence of a source document: a claim, and where it sits in the text.
     *
     * @param claim the claim the sentence was extracted as
     * @param startOffset where the sentence starts in the extracted text
     * @param endOffset where the sentence ends in the extracted text
     */
    public record Sentence(Claim claim, int startOffset, int endOffset) {

        /** The text of the sentence: the claim's own proposition, verbatim. */
        public String text() {
            return claim.proposition();
        }
    }

    /**
     * Derives the definitions of the given concepts.
     *
     * <p>The inputs are the workspace's own state: the concepts, the sources in
     * the order the snapshot lists them, and the sentences. Nothing else is read,
     * and nothing outside the workspace is consulted.</p>
     *
     * @param concepts the concepts to derive a definition for
     * @param sources the workspace's sources, in the snapshot's order
     * @param sentences the workspace's sentences, in any order: the rule sorts them
     * @return one definition per concept that has one, in the concept order
     */
    public static List<Definition> derive(List<Concept> concepts, List<Source> sources,
                                          List<Sentence> sentences) {
        Map<String, List<Sentence>> sentencesOfSource = new HashMap<>();
        Map<String, Set<String>> ownSources = new HashMap<>();
        for (Sentence sentence : sentences) {
            Claim claim = sentence.claim();
            sentencesOfSource.computeIfAbsent(claim.introducedBySourceId(), key -> new ArrayList<>())
                    .add(sentence);
            ownSources.computeIfAbsent(claim.conceptId(), key -> new LinkedHashSet<>())
                    .add(claim.introducedBySourceId());
        }
        for (List<Sentence> ofSource : sentencesOfSource.values()) {
            ofSource.sort(Comparator.comparingInt(Sentence::startOffset));
        }

        List<Definition> definitions = new ArrayList<>();
        for (Concept concept : concepts) {
            Set<String> mine = ownSources.get(concept.id());
            if (mine == null || mine.isEmpty()) {
                continue;
            }
            Definition found = null;
            for (Source source : sources) {
                if (!mine.contains(source.id())) {
                    continue;
                }
                for (Sentence sentence : sentencesOfSource.getOrDefault(source.id(), List.of())) {
                    if (!holdsName(sentence.text(), concept.name())) {
                        continue;
                    }
                    if (!statesDefinition(sentence.text(), concept.name())) {
                        continue;
                    }
                    found = new Definition(concept.id(), concept.name(), sentence.text(),
                            source.id(), sentence.startOffset(), sentence.endOffset(),
                            DEFINITION_METHOD);
                    break;
                }
                if (found != null) {
                    break;
                }
            }
            if (found != null) {
                definitions.add(found);
            }
        }
        return definitions;
    }

    /**
     * The sentences of a set of claims, each with the place it sits in the text.
     *
     * <p><b>WHY THE TEXT IS NEEDED.</b> The rule scans "the sentences of those
     * documents in document order", and the document order is the ORDER OF THE
     * SENTENCES IN THE TEXT. A claim's own context carries the span of its
     * SEGMENT — the extractor splits a segment into sentences and every claim of
     * that segment carries the segment's span — so the segment's span alone
     * cannot say which of a segment's sentences comes first. When the extracted
     * text is given, the proposition is therefore located INSIDE its segment's
     * span, exactly as the research instrument does; when it is not given (a
     * source imported earlier, whose text is not in hand), the segment's span is
     * used and the sentences of that segment keep the order they were extracted
     * in.</p>
     *
     * @param claims the claims
     * @param extractedText the extracted text of the claims' source, or null
     * @return the sentences, with their positions
     */
    public static List<Sentence> sentencesOf(List<Claim> claims, String extractedText) {
        List<Sentence> sentences = new ArrayList<>();
        for (Claim claim : claims) {
            int from = startOf(claim);
            int to = endOf(claim);
            int[] located = locate(extractedText, claim.proposition(), from, to);
            sentences.add(new Sentence(claim, located[0], located[1]));
        }
        return sentences;
    }

    /**
     * Where a proposition sits in the extracted text.
     *
     * <p>The sentence is looked for inside the span of its segment. When it
     * cannot be found there — the extractor joined the lines and normalised the
     * whitespace — the segment's own span is used, and the offset is the
     * segment's.</p>
     *
     * @param text the extracted text, or null when it is not in hand
     * @param proposition the claim's proposition
     * @param from the start of the claim's segment
     * @param to the end of the claim's segment
     * @return the start, the end and whether the sentence was located exactly
     */
    private static int[] locate(String text, String proposition, int from, int to) {
        if (text == null || text.isEmpty() || proposition == null || proposition.isEmpty()) {
            return new int[] {from, to, 0};
        }
        int start = Math.max(0, Math.min(from, text.length()));
        int end = Math.max(start, Math.min(to, text.length()));
        int at = text.indexOf(proposition, start);
        if (at >= 0 && at + proposition.length() <= end) {
            return new int[] {at, at + proposition.length(), 1};
        }
        String trimmed = proposition.strip();
        at = text.indexOf(trimmed, start);
        if (at >= 0 && at + trimmed.length() <= end) {
            return new int[] {at, at + trimmed.length(), 1};
        }
        return new int[] {start, end, 0};
    }


    /**
     * Whether a sentence states the definition of a concept, under the tight rule.
     *
     * @param sentence the sentence
     * @param name the concept's name
     * @return whether the sentence states the definition
     */
    public static boolean statesDefinition(String sentence, String name) {
        if (sentence == null || sentence.isEmpty() || name == null || name.isEmpty()) {
            return false;
        }
        if (sentence.length() > MAX_DEFINITION_LENGTH) {
            return false;
        }
        return holdsCopulaAfterName(sentence, name);
    }

    /**
     * Whether a sentence holds a name, as a word or as a contiguous token sequence.
     *
     * @param sentence the sentence
     * @param name the concept's name
     * @return whether the sentence holds the name
     */
    public static boolean holdsName(String sentence, String name) {
        List<String> nameWords = words(name);
        if (nameWords.isEmpty()) {
            return false;
        }
        return containsSequence(words(sentence), nameWords);
    }

    /**
     * Whether the concept's name is IMMEDIATELY followed by a definitional copula.
     *
     * <p>The adjacency rule of the freeze v1.1 §3.1: the name must be ADJACENT
     * to the copula, not merely in the same sentence. A sentence that names the
     * concept and states something else about it — "A trench forms and, as the
     * subducted plate is heated, ..." — does not state its definition.</p>
     *
     * @param sentence the sentence
     * @param name the concept's name
     * @return whether the name is immediately followed by a definitional copula
     */
    public static boolean holdsCopulaAfterName(String sentence, String name) {
        List<String> nameWords = words(name);
        List<String> sentenceWords = words(sentence);
        if (nameWords.isEmpty() || nameWords.size() > sentenceWords.size()) {
            return false;
        }
        for (int start = 0; start + nameWords.size() <= sentenceWords.size(); start++) {
            if (!containsAt(sentenceWords, start, nameWords)) {
                continue;
            }
            int after = start + nameWords.size();
            for (String copula : DEFINITIONAL_COPULAS) {
                if (matches(sentenceWords, after, words(copula))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Where the claim's sentence starts in the extracted text. */
    private static int startOf(Claim claim) {
        io.matrix.contract.Provenance.Span span = ClaimSpan.parse(claim.context());
        return span == null ? 0 : (int) span.start();
    }

    /** Where the claim's sentence ends in the extracted text. */
    private static int endOf(Claim claim) {
        io.matrix.contract.Provenance.Span span = ClaimSpan.parse(claim.context());
        if (span == null || span.end() == null) {
            return startOf(claim);
        }
        return span.end().intValue();
    }

    /** Whether a sequence of words holds another sequence, contiguously. */
    private static boolean containsSequence(List<String> haystack, List<String> needle) {
        if (needle.isEmpty() || needle.size() > haystack.size()) {
            return false;
        }
        for (int start = 0; start + needle.size() <= haystack.size(); start++) {
            if (containsAt(haystack, start, needle)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the words at the given index are the given sequence. */
    private static boolean containsAt(List<String> words, int start, List<String> phrase) {
        if (phrase.isEmpty() || start < 0 || start + phrase.size() > words.size()) {
            return false;
        }
        for (int offset = 0; offset < phrase.size(); offset++) {
            if (!words.get(start + offset).equals(phrase.get(offset))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the words at the given index start the given phrase. */
    private static boolean matches(List<String> words, int index, List<String> phrase) {
        return containsAt(words, index, phrase);
    }

    /**
     * The words of a text: maximal runs of letters or digits, lower-cased.
     *
     * <p>The unit is the token, not the character: a name that is a word of the
     * sentence is a contiguous run of tokens.</p>
     *
     * @param text the text
     * @return the words, in order
     */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        int index = 0;
        int length = text.length();
        while (index < length) {
            if (Character.isLetterOrDigit(text.charAt(index))) {
                int end = index;
                while (end < length && Character.isLetterOrDigit(text.charAt(end))) {
                    end++;
                }
                words.add(text.substring(index, end).toLowerCase(Locale.ROOT));
                index = end;
            } else {
                index++;
            }
        }
        return words;
    }

    /**
     * The concepts of a workspace that carry claims, by id — the lookup the
     * importer uses to find the concepts it has to write a definition for.
     *
     * @param concepts the workspace's concepts
     * @param claims the workspace's claims
     * @return the concepts that have at least one claim
     */
    public static List<Concept> conceptsWithClaims(List<Concept> concepts, List<Claim> claims) {
        Set<String> withClaims = new LinkedHashSet<>();
        for (Claim claim : claims) {
            withClaims.add(claim.conceptId());
        }
        Map<String, Concept> byId = new LinkedHashMap<>();
        for (Concept concept : concepts) {
            byId.put(concept.id(), concept);
        }
        List<Concept> selected = new ArrayList<>();
        for (String id : withClaims) {
            Concept concept = byId.get(id);
            if (concept != null) {
                selected.add(concept);
            }
        }
        return selected;
    }

    private SourceSpanDefinitions() {
    }
}
