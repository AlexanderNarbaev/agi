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
     * The copulas, the LONGEST first.
     *
     * <p>The order matters: "A trench is defined as not a canyon" must be read
     * through "is defined as", not through "is" — with the short copula first
     * the denial hides behind the next word and the sentence looks like a
     * definition.</p>
     */
    private static final List<String> COPULAS_LONGEST_FIRST = List.of(
            "is defined as", "is called", "consists of", "refers to",
            "describes", "is", "are", "means");

    /**
     * The words that open a predicate naming what the term denotes.
     *
     * <p>A genus ("the deepest parts of the ocean floor"), an identity ("one of
     * the few transform faults exposed on land") and a classification ("of two
     * types") are what a definition says. An action, a comparison, a
     * measurement, an origin and a state are not: "are compressed", "is denser
     * than", "is 100 km thick", "is formed at", "is still open".</p>
     */
    public static final Set<String> DEFINING_DETERMINERS = Set.of(
            "the", "a", "an", "this", "that", "these", "those", "one", "two",
            "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "twenty", "all", "some", "each", "every",
            "both", "another", "such", "several", "many", "numerous", "certain",
            "part", "parts", "kind", "kinds", "type", "types", "piece", "pieces");

    /**
     * The words that number a set, so that "of two types" reads as a
     * classification.
     */
    public static final Set<String> NUMBER_WORDS = Set.of(
            "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "twenty", "thirty", "forty",
            "fifty", "hundred", "thousand", "several", "many", "few", "dozen");

    /**
     * The adverbs and hedges that may stand between the copula and the
     * predicate: "is therefore most widely thought to be ...".
     */
    public static final Set<String> PREDICATE_HEDGES = Set.of(
            "therefore", "thus", "hence", "generally", "typically", "usually",
            "often", "commonly", "mostly", "largely", "chiefly", "principally",
            "mainly", "also", "still", "simply", "merely", "now", "then",
            "currently", "only", "even", "already", "indeed", "perhaps",
            "probably", "virtually", "practically");

    /**
     * How many tokens after the copula the predicate's head may stand.
     *
     * <p>A copula's complement follows it: "X is the deepest parts ...". When
     * the noun phrase stands far from the copula the copula is not the verb of
     * the definition but the verb of the frame ("X is thought to be ...",
     * "X is therefore most widely thought to be ..."), and the sentence reports
     * a belief rather than denoting a kind. The window is bounded so that the
     * gate stays a structural test.</p>
     */
    public static final int MAX_PREDICATE_OFFSET = 4;

    /**
     * The verbs that report what is thought, said or estimated.
     *
     * <p>"is thought to be", "is believed to be", "is said to be": the
     * sentence reports a belief about the referent instead of denoting it.</p>
     */
    public static final Set<String> REPORTING_VERBS = Set.of(
            "thought", "believed", "considered", "claimed", "said", "reputed",
            "alleged", "reported", "argued", "estimated", "assumed",
            "supposed", "suggested", "held", "viewed");

    /**
     * How many tokens after the copula may hide a negation.
     *
     * <p>A qualifier may stand between the copula and the negation: "A trench
     * is largely not a long narrow depression ...", "A trench is sometimes not
     * a deep-ocean trench." The window is bounded so that a denial near the
     * copula is caught and a denial far away is not invented.</p>
     */
    public static final int NEGATION_WINDOW = 3;

    /**
     * The words that open a name which is a question, not a referent.
     */
    public static final Set<String> QUESTION_OPENERS = Set.of(
            "how", "what", "why", "when", "where", "whether", "which", "who");

    /**
     * The possessives that open a name whose referent is not the name itself.
     */
    public static final Set<String> POSSESSIVE_OPENERS = Set.of(
            "its", "their", "his", "her", "our", "your", "my");

    /**
     * The words that may stand between the start of a sentence and its subject.
     *
     * <p>A sentence may open with a discourse marker or a subordinate clause
     * ("However, ...", "In this understanding, ..."), and the subject follows
     * it. A name that is only a MENTION further along the sentence is not the
     * subject, and a sentence about something else is not its definition.</p>
     */
    public static final Set<String> SUBJECT_MARKERS = Set.of(
            "furthermore", "moreover", "however", "as", "in", "because", "since",
            "when", "while", "whenever", "if", "thus", "therefore", "so", "and",
            "but", "yet", "also", "this", "that", "these", "those", "it", "they",
            "he", "she", "we", "you", "i");

    /**
     * How far the subject may sit from the start of the sentence, in tokens.
     *
     * <p>A subject further away than this is behind a clause, and a name
     * behind a clause is not what the sentence is about.</p>
     */
    public static final int MAXIMUM_SUBJECT_OFFSET = 12;

    /**
     * The words that denote nothing that can be a concept: a pronoun, an
     * article, a quantifier, a preposition, a conjunction, an auxiliary.
     *
     * <p>A name that is one of these is a reference or a connective, never a
     * referent: "It", "There", "The", "To".</p>
     */
    public static final Set<String> FUNCTION_WORDS = Set.of(
            "it", "its", "they", "them", "their", "these", "those", "this", "that",
            "he", "she", "his", "her", "we", "us", "our", "you", "your", "there",
            "here", "the", "a", "an", "one", "ones", "other", "others", "another",
            "such", "and", "or", "but", "nor", "yet", "so", "because", "although",
            "while", "whereas", "if", "when", "since", "unless", "until", "than",
            "as", "which", "who", "whom", "whose", "what", "how", "why", "to",
            "of", "in", "on", "at", "by", "for", "from", "with", "without", "into",
            "onto", "over", "under", "above", "below", "after", "before", "during",
            "between", "among", "through", "across", "along", "around", "behind",
            "beyond", "near", "toward", "towards", "upon", "within", "per", "via",
            "is", "are", "was", "were", "be", "been", "being", "am", "has", "have",
            "had", "do", "does", "did", "can", "could", "will", "would", "shall",
            "should", "may", "might", "must", "ought", "not", "no", "also",
            "though", "thus", "therefore", "hence", "furthermore", "moreover",
            "otherwise", "instead", "nearly", "several");

    /**
     * The words that cannot sit INSIDE a concept's name.
     *
     * <p>A determiner, a preposition, a subordinating conjunction or a
     * relative pronoun inside a name means the name was cut inside a clause:
     * "the fact that the North American plate", "Plate boundary zones -- broad
     * belts in which boundaries". The coordinating conjunction "and" is NOT in
     * this list, because a proper name carries it: "San Andreas".</p>
     */
    public static final Set<String> CLAUSE_INNER_WORDS = Set.of(
            "the", "a", "an", "this", "that", "these", "those", "its", "their", "his",
            "her", "our", "your", "my", "any", "each", "every", "no", "some", "of",
            "in", "on", "at", "by", "for", "from", "with", "without", "into", "onto",
            "over", "under", "above", "below", "after", "before", "during", "between",
            "among", "through", "across", "along", "around", "behind", "beyond",
            "near", "toward", "towards", "upon", "within", "per", "via", "because",
            "although", "while", "whereas", "where", "when", "since", "unless",
            "until", "than", "as", "if", "which", "who", "whom", "whose", "what",
            "how", "why", "though", "however", "thus", "therefore", "hence");

    /**
     * The words that open a name which denotes a CLASS of things rather than a
     * thing.
     *
     * <p>"Such volcanoes are typically strung out in chains" and "One method of
     * dealing with this problem is ..." name classes and manners, and a
     * definition attached to a class states nothing about any member of it.</p>
     */
    public static final Set<String> CLASS_DETERMINERS = Set.of(
            "such", "one", "some", "many", "several", "various", "different",
            "other", "others", "another", "both", "all", "each", "every", "few",
            "numerous", "certain", "example", "examples");

    /**
     * The nouns that name a discourse rather than a thing.
     *
     * <p>The discourse nominals of the hand-labelling protocol: "the question",
     * "An important conclusion", "The mechanism", "The latter", "The division".
     * The sentence that follows them is about something else.</p>
     */
    public static final Set<String> DISCOURSE_HEADS = Set.of(
            "mechanism", "latter", "former", "division", "question", "answer",
            "result", "conclusion", "idea", "issue", "problem", "way", "case",
            "fact", "point", "sense", "process", "evidence", "reason",
            "implication", "difference", "role", "effect", "impact", "pattern",
            "concept", "definition", "thing", "something", "anything",
            "everything", "nothing", "someone", "everyone", "people", "authors",
            "scientists", "others", "kind", "sort", "type", "types", "amount",
            "number", "part", "parts", "lot", "lots");

    /**
     * The words the subject extractor stops at, so a name that ENDS with one
     * of them was cut inside a clause.
     *
     * <p>"This division should not", "Convergence can", "Plate tectonics came
     * to" are the fragments the extractor produced.</p>
     */
    public static final Set<String> CLAUSE_CUT_WORDS = Set.of(
            "is", "are", "was", "were", "be", "been", "being", "has", "have", "had",
            "means", "refer", "consist", "include", "require", "need", "produce",
            "generate", "release", "absorb", "convert", "transform", "use", "build",
            "happen", "occur", "form", "create", "make", "contain", "provide",
            "depend", "result", "lead", "cause", "enable", "allow", "determine",
            "control", "represent", "describe", "define", "equal", "become",
            "remain", "stay", "work", "function", "act", "react", "respond",
            "grow", "move", "split", "come", "go", "get", "give", "take", "show",
            "seem", "appear", "may", "might", "must", "can", "could", "would",
            "should", "shall", "will");

    /**
     * The words that deny the sentence: a definition is not a denial.
     *
     * <p>"Plate tectonics is not a theory of expanding Earth" states that the
     * concept is something it is not, and that is not what the concept is.</p>
     */
    public static final Set<String> NEGATIONS = Set.of("not", "no", "never",
            "neither", "nor");

    /**
     * The suffix of a past participle, which marks a name that was cut inside
     * a clause: "Dates derived", "The results obtained".
     */
    public static final String PARTICIPLE_SUFFIX = "ed";

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
        if (!isReferent(name)) {
            return false;
        }
        List<String> sentenceWords = words(sentence);
        List<String> nameWords = words(name);
        int subject = subjectOffset(sentenceWords, nameWords);
        if (subject < 0) {
            // The name is not the subject: the sentence is about something else,
            // and a mention of the name in it does not define it.
            return false;
        }
        int after = subject + nameWords.size();
        for (String copula : COPULAS_LONGEST_FIRST) {
            List<String> copulaWords = words(copula);
            if (!matches(sentenceWords, after, copulaWords)) {
                continue;
            }
            // The copula matched: its reading decides the sentence, and a
            // shorter copula must not rescue a sentence the longer one rejects.
            int next = after + copulaWords.size();
            // A DENIAL IS NOT A DEFINITION: "X is not Y" says what X is not —
            // and a qualifier may stand between the copula and the denial.
            if (denies(sentenceWords, next)) {
                return false;
            }
            // THE DEFINITIONAL PREDICATE: what follows the copula must say
            // what the term denotes, not what it does or how it compares.
            return isDefinitionalPredicate(sentenceWords, next);
        }
        return false;
    }

    /**
     * Whether a denial stands within the window after the copula.
     *
     * @param sentenceWords the words of the sentence
     * @param from where the copula ended
     * @return whether the sentence denies
     */
    private static boolean denies(List<String> sentenceWords, int from) {
        int limit = Math.min(sentenceWords.size(), from + NEGATION_WINDOW);
        for (int index = from; index < limit; index++) {
            if (NEGATIONS.contains(sentenceWords.get(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the predicate names what the term denotes.
     *
     * <p>A definition says what a thing IS: a genus ("the deepest parts of the
     * ocean floor"), an identity ("one of the few transform faults exposed on
     * land") or a classification ("of two types"). It does not say what the
     * thing does ("are compressed, folded, and uplifted"), how it compares
     * ("is cooler and more rigid"), how big it is ("is 100 km thick"), where it
     * comes from ("is formed at sea-floor spreading centers") or what state it
     * is in ("is still open").</p>
     *
     * @param sentenceWords the words of the sentence
     * @param from where the copula ended
     * @return whether the predicate is definitional
     */
    private static boolean isDefinitionalPredicate(List<String> sentenceWords, int from) {
        int limit = Math.min(sentenceWords.size(), from + MAX_PREDICATE_OFFSET);
        for (int index = from; index < limit; index++) {
            if (REPORTING_VERBS.contains(sentenceWords.get(index))) {
                return false;
            }
        }
        int index = from;
        while (index < limit && PREDICATE_HEDGES.contains(sentenceWords.get(index))) {
            index++;
        }
        if (index >= limit) {
            return false;
        }
        String head = sentenceWords.get(index);
        if (DEFINING_DETERMINERS.contains(head)) {
            return true;
        }
        if ("of".equals(head) && index + 1 < sentenceWords.size()
                && (DEFINING_DETERMINERS.contains(sentenceWords.get(index + 1))
                        || NUMBER_WORDS.contains(sentenceWords.get(index + 1)))) {
            return true;
        }
        return false;
    }

    /**
     * Where the subject of a sentence sits, when the name is the subject.
     *
     * <p>The subject is the name's token sequence at the position the discourse
     * markers end: a sentence may open with "However," or "In this
     * understanding," and the subject follows. A name that appears only later
     * in the sentence is a mention, not the subject.</p>
     *
     * @param sentenceWords the words of the sentence
     * @param nameWords the words of the name
     * @return the index of the subject, or -1 when the name is not the subject
     */
    private static int subjectOffset(List<String> sentenceWords, List<String> nameWords) {
        if (nameWords.isEmpty() || nameWords.size() > sentenceWords.size()) {
            return -1;
        }
        int offset = 0;
        while (offset < sentenceWords.size() && offset <= MAXIMUM_SUBJECT_OFFSET
                && SUBJECT_MARKERS.contains(sentenceWords.get(offset))) {
            offset++;
        }
        if (offset > MAXIMUM_SUBJECT_OFFSET) {
            return -1;
        }
        return matches(sentenceWords, offset, nameWords) ? offset : -1;
    }

    /**
     * Whether a concept's name denotes a referent rather than a fragment.
     *
     * <p>This is the hand-labelling protocol's test, made mechanical: a
     * definition attached to a fragment is a definition of a fragment. A name
     * is a referent when it is not a single function word, does not open with a
     * class word, does not carry a function word inside it (the extractor cut
     * it inside a clause), does not end with a word the extractor stops at or
     * with a past participle, and does not end with a discourse noun.</p>
     *
     * @param name the concept's name
     * @return whether the name can denote a concept
     */
    public static boolean isReferent(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        List<String> nameWords = words(name);
        if (nameWords.isEmpty()) {
            return false;
        }
        if (nameWords.size() == 1 && FUNCTION_WORDS.contains(nameWords.get(0))) {
            return false;
        }
        if (CLASS_DETERMINERS.contains(nameWords.get(0))) {
            return false;
        }
        if (QUESTION_OPENERS.contains(nameWords.get(0))
                || POSSESSIVE_OPENERS.contains(nameWords.get(0))) {
            return false;
        }
        for (int index = 1; index < nameWords.size(); index++) {
            if (CLAUSE_INNER_WORDS.contains(nameWords.get(index))) {
                return false;
            }
        }
        if (isRunOn(name)) {
            return false;
        }
        String last = nameWords.get(nameWords.size() - 1);
        if (CLAUSE_CUT_WORDS.contains(last) || DISCOURSE_HEADS.contains(last)) {
            return false;
        }
        // A name that ENDS with a function word was cut inside the clause: "At
        // continental collision zones, there".
        if (FUNCTION_WORDS.contains(last) || CLAUSE_INNER_WORDS.contains(last)) {
            return false;
        }
        if (nameWords.size() > 1 && last.length() > PARTICIPLE_SUFFIX.length()
                && last.endsWith(PARTICIPLE_SUFFIX)) {
            return false;
        }
        return true;
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
     * Whether a name is a RUN-ON: a lower-cased word followed by a capitalised
     * one.
     *
     * <p>The extractor cut the name inside a caption and the caption's second
     * unit was glued to it: "Defining plate boundaries Active plate boundaries",
     * "Key principles The outer layers of Earth", "Icy moons Exoplanets On
     * Earth-sized planets, plate tectonics". A proper name does not look like
     * that — "Jason Morgan", "San Andreas" — so the case decides it.</p>
     *
     * @param name the concept's name, in its original case
     * @return whether the name is a run-on
     */
    public static boolean isRunOn(String name) {
        List<String> raw = rawWords(name);
        for (int index = 1; index < raw.size(); index++) {
            String previous = raw.get(index - 1);
            String current = raw.get(index);
            if (!previous.isEmpty() && !current.isEmpty()
                    && Character.isLowerCase(previous.charAt(0))
                    && Character.isUpperCase(current.charAt(0))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The words of a text, in their original case.
     *
     * @param text the text
     * @return the words, with their case
     */
    private static List<String> rawWords(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        java.util.regex.Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
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
        java.util.regex.Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /**
     * The unit of a word: a run of Latin letters and digits.
     *
     * <p>The token is the word the rule matches, and it is the same unit the
     * reviewer's probe uses, so that the rule and the probe judge a sentence
     * the same way.</p>
     */
    private static final java.util.regex.Pattern TOKEN =
            java.util.regex.Pattern.compile("[A-Za-z0-9]+");

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
