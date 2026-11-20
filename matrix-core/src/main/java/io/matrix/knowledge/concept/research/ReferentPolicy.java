package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.concept.ConceptAlias;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RESEARCH ONLY — the resolution and alias policy of the referent key.
 *
 * <p><b>What this is.</b> The research that measured the concept-identity defect
 * ({@code docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md} §6.3) recommended the
 * <b>shape</b> of the referent key and said, in the same sentence, that
 * <i>"the resolution and alias policy is the unsolved part"</i>. This class is a
 * candidate for that part: a bounded, deterministic, closed-vocabulary policy that
 * decides
 * <ul>
 *   <li>which surface forms are <b>not</b> referents at all (the <b>reject-only</b>
 *       rule the research insisted on: a name that cannot be a concept is <i>not
 *       claimed as one</i>, never truncated into one — the NP filter was rejected
 *       because it dropped conjuncts and silently narrowed 34 referents);</li>
 *   <li>which two occurrences are the <b>same referent</b> (the resolution);</li>
 *   <li>which two <b>different</b> words are the same referent (the alias layer).</li>
 * </ul>
 *
 * <p><b>What the policy is FOR, in the directive's words.</b> The owner's directive
 * (§3 P1) requires that a concept-model repair <i>"distinguish the same word in
 * different contexts"</i>, and that the repair <i>"preserve original claims, evidence
 * provenance and ID stability or explicit migration lineage"</i>. The four properties
 * this class is designed against are therefore:
 * <ol>
 *   <li>the same word in different contexts stays <b>distinct</b>;</li>
 *   <li>a <b>synonym</b> does not fragment a concept;</li>
 *   <li>a <b>paraphrase</b> of the same term does not fragment it;</li>
 *   <li>a genuine <b>homonym</b> does not merge.</li>
 * </ol>
 *
 * <p><b>Every rule here is a HYPOTHESIS until the measurement says otherwise.</b> The
 * closed lists were written against one corpus (nine plate-tectonics documents, one
 * domain, one language) and the alias table was hand-written from the same corpus.
 * The measurement in {@code matrix-core/src/test/java/io/matrix/knowledge/concept/
 * research/**} reports the precision, the fragmentation and the four properties; the
 * write-up is {@code docs-v2/engineering/EPISTEMIC-REFERENT-KEY.md}. Everything in this
 * class is marked <b>[speculative]</b> in the sense the repository uses: a candidate
 * fitted to one corpus, not a generalisation.
 *
 * <p><b>Nothing here is wired into production.</b> No class outside this research
 * package references it; no store is written; no concept id is derived from it.
 */
public final class ReferentPolicy {

    // ── the closed vocabularies ───────────────────────────────────────────────

    /**
     * The words that cannot be a referent's head: a pronoun, an article, a
     * quantifier, a preposition, a conjunction, an auxiliary.
     *
     * <p>A surface form whose last word is one of these denotes nothing that can be
     * a concept: "It", "There", "The", "To".</p>
     */
    public static final Set<String> FUNCTION_WORDS = Set.of(            "it", "its", "they", "them", "their", "these", "those", "this", "that", "he",
            "she", "his", "her", "we", "us", "our", "you", "your", "there", "here", "the", "a",
            "an", "one", "ones", "other", "others", "another", "such", "and", "or", "but",
            "nor", "yet", "so", "because", "although", "while", "whereas", "if", "when",
            "since", "unless", "until", "than", "as", "which", "who", "whom", "whose", "what",
            "how", "why", "to", "of", "in", "on", "at", "by", "for", "from", "with", "without",
            "into", "onto", "over", "under", "above", "below", "after", "before", "during",
            "between", "among", "through", "across", "along", "around", "behind", "beyond",
            "near", "toward", "towards", "upon", "within", "per", "via", "is", "are", "was",
            "were", "be", "been", "being", "am", "has", "have", "had", "do", "does", "did",
            "can", "could", "will", "would", "shall", "should", "may", "might", "must",
            "ought", "not", "no", "also", "even", "just", "rather", "quite", "very", "too",
            "only", "still", "already", "again", "further", "indeed", "perhaps", "though",
            "few", "most", "many", "much", "commonly", "therefore", "thus", "hence",
            "furthermore", "moreover", "otherwise", "instead", "almost", "nearly", "several",
            "various", "certain");


    /**
     * The words the production subject extractor stops at, so that a surface form
     * ending with one of them was cut inside a clause.
     *
     * <p>This is the subset of {@code ClaimExtractor.DECLARATIVE_VERBS} the research
     * measured, written out because that list is private to the extractor and this
     * research must not change production code to read it. It catches the names the
     * research named: "This division should not", "Convergence can", "Plate tectonics
     * came to", "Dates derived".</p>
     */
    public static final Set<String> CLAUSE_CUT_WORDS = Set.of(
            "is", "are", "was", "were", "be", "been", "being", "has", "have", "had",
            "means", "refer", "consist", "include", "require", "need", "produce",
            "generate", "release", "absorb", "convert", "transform", "use", "build",
            "happen", "occur", "form", "create", "make", "contain", "provide", "depend",
            "result", "lead", "cause", "enable", "allow", "determine", "control",
            "represent", "describe", "define", "equal", "become", "remain", "stay",
            "work", "function", "act", "react", "respond", "grow", "move", "split",
            "come", "go", "get", "give", "take", "show", "seem", "appear", "derived",
            "imply", "implies", "reach", "reached", "based", "called", "known");

    /**
     * The words that open a surface form which denotes a CLASS of things.
     *
     * <p>"Such volcanoes", "Many were published", "These concepts" name classes; a
     * definition attached to a class states nothing about any member of it, and the
     * hand-labelling protocol of the A1 instrument says the same.</p>
     */
    public static final Set<String> CLASS_OPENERS = Set.of(            "many", "most", "some", "several", "various", "different", "others", "such",
            "these", "those", "both", "all", "each", "every", "few", "more", "less",
            "numerous", "certain", "example", "examples", "another");


    /**
     * The heads that denote a class of persons or of unnamed things.
     *
     * <p>The hand-labelling protocol of the A1 instrument counts "Scientists" and
     * "Geologists" as <b>not</b> concepts: they name people in general, not a person
     * or a class of entity that a claim is about.</p>
     */
    public static final Set<String> GENERIC_HEADS = Set.of(            "scientist", "scientists", "geologist", "geologists", "author", "authors",
            "proposal", "proposals", "discussion", "discussions", "displacement",
            "displacements", "outcome", "outcomes", "explanation", "explanations",
            "instrument", "instruments", "dataset", "datasets", "data", "point", "points",
            "square", "squares", "research", "latter", "former", "division", "divisions",
            "importance", "interaction", "researcher", "researchers", "student", "students",
            "people", "person", "persons", "man", "woman", "workers", "worker", "reader",
            "readers", "belief", "beliefs", "evidence", "idea", "ideas", "question",
            "questions", "answer", "answers", "problem", "problems", "conclusion",
            "conclusions", "finding", "findings", "result", "results", "example", "examples",
            "difference", "differences", "similarity", "relationship", "relationships",
            "significance", "understanding", "view", "views", "argument", "arguments", "issue",
            "issues", "factor", "factors", "effect", "effects", "process", "processes",
            "mechanism", "mechanisms", "system", "systems", "event", "events", "phenomenon",
            "phenomena", "period", "periods", "region", "regions", "area", "areas", "series",
            "group", "groups", "range", "ranges", "era", "eras", "episode", "episodes",
            "seismologist", "seismologists", "force", "forces", "method", "methods",
            "approach", "approaches", "way", "ways");





    /**
     * The heads that denote the DISCUSSION rather than a thing in it.
     *
     * <p>"The mechanism", "This theory", "This research", "The division" — the
     * hand-labelling protocol counts them as not concepts. A head that is a discourse
     * nominal is only accepted when the surface form carries a modifier that
     * specifies it ("the theory <b>of plate tectonics</b>").</p>
     */
    public static final Set<String> DISCOURSE_HEADS = Set.of(
            "theory", "theories", "hypothesis", "hypotheses", "model", "models",
            "concept", "concepts", "idea", "ideas", "view", "views", "principle",
            "principles", "law", "laws", "rule", "rules", "case", "cases", "point",
            "points", "part", "parts", "number", "numbers", "kind", "kinds", "sort",
            "sorts", "type", "types", "way", "ways", "fact", "facts", "term", "terms");

    /**
     * The leading discourse adverbials the A1 labelling protocol strips before the
     * head of a span is judged.
     *
     * <p>The protocol of the frozen hand-labelled set says so explicitly: <i>"a leading
     * discourse adverbial ('For example,', 'In 1912,') is stripped before the head is
     * judged"</i>. The policy strips the same words, so that it judges the same thing
     * the labels judge.</p>
     */
    public static final Set<String> DISCOURSE_ADVERBIALS = Set.of(            "initially", "finally", "then", "later", "originally", "typically", "generally",
            "commonly", "usually", "however", "moreover", "furthermore", "nevertheless",
            "nonetheless", "thus", "therefore", "hence", "now", "today", "recently",
            "currently", "earlier", "subsequently", "previously", "often");


    /**
     * The subordinating conjunctions that open a clause.
     *
     * <p>A span that begins with one of them is a clause, whatever its head says. The
     * list is a closed class of the language, not a list of this corpus's words.</p>
     */
    public static final Set<String> SUBORDINATORS = Set.of(            "because", "when", "while", "although", "though", "if", "unless", "before",
            "after", "despite", "whereas", "whenever", "whether", "until", "lest", "provided",
            "supposing", "once");


    /**
     * The relative pronouns and adverbs that open an embedded clause.
     *
     * <p>The list is a closed class of the language. A span that contains one of them
     * followed by more words contains an embedded clause, and its head belongs to that
     * clause rather than to the span.</p>
     */
    public static final Set<String> RELATIVISERS = Set.of(
            "that", "which", "who", "whom", "whose", "where", "when");

    /**
     * The suffixes that mark a participle.
     *
     * <p>A span that carries a participle carries a clause: the participle modifies the
     * head and the span is not a noun phrase.</p>
     */
    public static final Set<String> PARTICIPLE_SUFFIXES = Set.of("ing", "ed");

    /**
     * The prepositions that open a sentence modifier.
     *
     * <p>A closed class of the language. A span that opens with one of them and
     * carries a comma is an adverbial plus a subject, not a noun phrase.</p>
     */
    public static final Set<String> PREPOSITIONS = Set.of(            "at", "on", "in", "by", "for", "from", "with", "without", "under", "over",
            "through", "between", "among", "across", "along", "around", "behind", "beyond",
            "near", "toward", "towards", "upon", "within", "into", "onto", "during", "before",
            "after", "despite", "throughout", "following", "as");


    /**
     * The proper nouns of the corpus, for the bare-plural rule.
     *
     * <p>A plural that is a proper noun ("Wegener's") is not a class. The list is
     * small and closed, and it is this corpus's.</p>
     */
    public static final Set<String> PROPER_NOUNS = Set.of(
            "himalayas", "andes", "alps", "nazca", "pacific", "american", "atlantic",
            "african", "eurasian", "incan", "juan", "de", "fuji", "vesuvius", "etna",
            "yellowstone", "hawaiian", "mid", "moresby", "reykjavik");

    /**
     * The determiners: a capitalised word right after one of them is a proper
     * adjective, not the start of a new sentence.
     */
    public static final Set<String> DETERMINERS = Set.of(
            "the", "a", "an", "this", "that", "these", "those", "its", "their", "his",
            "her", "our", "your", "my", "any", "each", "every", "no", "some");

    /**
     * The function words that a caption may capitalise at the start of its second
     * unit.
     */
    public static final Set<String> CAPITALISED_FUNCTION_WORDS = Set.of(
            "the", "on", "in", "at", "a", "an", "this", "these", "those", "it", "its",
            "their", "his", "her", "as", "by", "for", "with", "from", "to", "and", "but",
            "or", "if", "when", "while", "after", "before", "during", "since");

    /**
     * The prepositions that introduce a complement: the noun before them is the head.
     */
    public static final Set<String> COMPLEMENT_PREPOSITIONS = Set.of(
            "of", "for", "from", "about");

    /** The most words a surface form may have and still be a phrase. */
    public static final int MAX_SURFACE_WORDS = 12;

    // ── the alias table ───────────────────────────────────────────────────────

    /**
     * Why two lemmas are declared to be the same referent, or not.
     *
     * <p>The vocabulary is the production record's own
     * ({@link io.matrix.knowledge.concept.ConceptAlias.AliasKind}): the alias layer is
     * DATA in the committed schema (the owner's decision OD-064), and the research
     * package reads the same vocabulary the table holds.</p>
     */
    public static final io.matrix.knowledge.concept.ConceptAlias.AliasKind SPELLING =
            io.matrix.knowledge.concept.ConceptAlias.AliasKind.SPELLING;

    /** The same referent under a different word. */
    public static final io.matrix.knowledge.concept.ConceptAlias.AliasKind SYNONYM =
            io.matrix.knowledge.concept.ConceptAlias.AliasKind.SYNONYM;

    /** The same referent under a different wording. */
    public static final io.matrix.knowledge.concept.ConceptAlias.AliasKind PARAPHRASE =
            io.matrix.knowledge.concept.ConceptAlias.AliasKind.PARAPHRASE;

    /** NOT the same referent, whatever the text suggests: a homonym. */
    public static final io.matrix.knowledge.concept.ConceptAlias.AliasKind DISTINCT =
            io.matrix.knowledge.concept.ConceptAlias.AliasKind.DISTINCT;

    /**
     * The DECLARED alias table: the hand-written equivalences and distinctions.
     *
     * <p><b>This table is a hypothesis, and it is the weakest part of the policy.</b>
     * It was written by hand against the nine-document corpus, so it is fitted to
     * that corpus and to nothing else. The measurement reports how many entries the
     * corpus actually exercises — an entry that never fires is not evidence of
     * anything — and the write-up marks the table <b>[speculative]</b>.</p>
     *
     * <p>The entries are stated over <b>lemmas</b>, never over a claim id, a case id
     * or a corpus phrase that happens to occur once.</p>
     */
    public static final List<io.matrix.knowledge.concept.ConceptAlias> DECLARED_ALIASES = List.of(
            ConceptAlias.declared("seafloor spreading", "sea-floor spreading", ConceptAlias.AliasKind.SPELLING,
                    "the corpus writes the term with and without the hyphen"),
            ConceptAlias.declared("slab pull", "slab pull force", ConceptAlias.AliasKind.SYNONYM,
                    "the corpus writes 'the slab pull force' and 'slab pull' for the same force"),
            ConceptAlias.declared("lithosphere", "earth lithosphere", ConceptAlias.AliasKind.SYNONYM,
                    "the corpus's 'Earth's lithosphere' and 'the lithosphere' name the same layer"),
            ConceptAlias.declared("plate motion", "motion of the tectonic plate", ConceptAlias.AliasKind.PARAPHRASE,
                    "the corpus's 'the motion of the tectonic plates' and 'plate motion' name "
                            + "the same referent"),
            ConceptAlias.declared("hotspot track", "track of hotspot", ConceptAlias.AliasKind.PARAPHRASE,
                    "the corpus's 'the tracks of hotspots' and 'hotspot tracks' name the same "
                            + "feature"),
            ConceptAlias.declared("divergent boundary", "divergent plate boundary", ConceptAlias.AliasKind.SYNONYM,
                    "the corpus writes both 'divergent boundary' and 'divergent plate boundary'"),
            ConceptAlias.declared("continental drift", "wegener theory of continental drift", ConceptAlias.AliasKind.SYNONYM,
                    "the corpus's 'Wegener's theory of continental drift' and 'continental drift' "
                            + "name the same theory"),
            ConceptAlias.declared("crust", "oceanic crust", ConceptAlias.AliasKind.DISTINCT,
                    "the generic crust and the oceanic crust are not one referent"),
            ConceptAlias.declared("crust", "continental crust", ConceptAlias.AliasKind.DISTINCT,
                    "the generic crust and the continental crust are not one referent"),
            ConceptAlias.declared("plate", "nazca plate", ConceptAlias.AliasKind.DISTINCT,
                    "the generic plate and one named plate are not one referent"),
            ConceptAlias.declared("plate", "pacific plate", ConceptAlias.AliasKind.DISTINCT,
                    "the generic plate and one named plate are not one referent"),
            ConceptAlias.declared("trench", "middle america trench", ConceptAlias.AliasKind.DISTINCT,
                    "the generic trench and one named trench are not one referent"));

    // ── the rules ─────────────────────────────────────────────────────────────

    /**
     * The strictness of the policy: where it stands on the precision/recall trade.
     *
     * <p>The structural rules do not come free. Each one removes false positives and
     * also removes true referents, and which error matters more is an OWNER's
     * question, not a researcher's. The two levels are therefore measured, not
     * chosen silently:</p>
     * <ul>
     *   <li>{@link #LENIENT} — only the rules that, on the frozen A1 set, remove no
     *       referent the hand labelling marks true;</li>
     *   <li>{@link #STRICT} — additionally the concatenation rule and the "-ing"
     *       participle rule, which remove more false positives and more true
     *       referents.</li>
     * </ul>
     */
    public enum Strictness {
        /** The rules that keep every referent the hand labelling marks true. */
        LENIENT,
        /** The rules that remove the concatenations and the "-ing" participles too. */
        STRICT
    }

    /**
     * Why a surface form is not a referent.
     */
    public enum Rejection {
        /** The surface form was accepted: it denotes something. */
        NONE,
        /** The head is a function word: the surface denotes nothing. */
        FUNCTION_HEAD,
        /** The head is a word the extractor stops at: the surface was cut inside a clause. */
        CLAUSE_CUT,
        /** The surface opens with a class word: it denotes a class. */
        CLASS_OPENER,
        /** The head is a class of persons or of unnamed things. */
        GENERIC_HEAD,
        /** The head is a discourse nominal with nothing that specifies it. */
        DISCOURSE_HEAD,
        /** The surface form is too long to be a phrase. */
        TOO_LONG,
        /** The surface form carries a caption marker or a dash artefact. */
        ARTEFACT,
        /** The span contains an embedded relative or complement clause. */
        CLAUSE_RELATIVE,
        /** The span contains a participial modifier: it is a clause, not a phrase. */
        CLAUSE_PARTICIPLE,
        /** The span begins with a subordinating conjunction: it is a clause. */
        CLAUSE_SUBORDINATOR,
        /** The span concatenates two units: a heading and a sentence. */
        CONCATENATION,
        /** The span repeats a phrase: it is a caption. */
        CAPTION_REPEAT,
        /** The head is a bare plural with no modifier: it denotes a class. */
        BARE_PLURAL,
        /** The span opens with a preposition and a determiner: it is a modifier. */
        PREPOSITIONAL_MODIFIER
    }

    /**
     * Whether a surface form denotes something that can be a concept.
     *
     * <p>This is the <b>reject-only</b> rule: the surface form is either accepted whole
     * or not claimed at all. It never truncates. The claim that carried it keeps its
     * own text, and nothing is deleted.</p>
     *
     * @param surface the subject phrase the production importer produced
     * @return the reason for the rejection, or {@link Rejection#NONE}
     */
    public static Rejection rejection(String surface) {
        return rejection(surface, Strictness.LENIENT);
    }

    /**
     * The rejection reason of a surface form at a given strictness.
     *
     * @param surface the surface form
     * @param strictness the strictness of the policy
     * @return the reason, or NONE when the surface form is a referent
     */
    public static Rejection rejection(String surface, Strictness strictness) {
        if (surface == null || surface.isBlank()) {
            return Rejection.FUNCTION_HEAD;
        }
        String trimmed = surface.strip();
        List<String> words = words(trimmed);
        if (words.isEmpty()) {
            return Rejection.FUNCTION_HEAD;
        }
        if (words.size() > MAX_SURFACE_WORDS) {
            return Rejection.TOO_LONG;
        }
        if (trimmed.contains("[") || trimmed.contains("]") || trimmed.contains(" -- ")
                || trimmed.contains(": ") || trimmed.startsWith("\"")
                || trimmed.endsWith("\"")) {
            return Rejection.ARTEFACT;
        }
        // ── the structural pre-pass ───────────────────────────────────────────
        // These four tests look at the SHAPE of the span, not at the words it holds.
        if (isConcatenation(trimmed)) {
            return Rejection.CONCATENATION;
        }
        if (repeatsAPhrase(words)) {
            return Rejection.CAPTION_REPEAT;
        }
        if (beginsWithASubordinator(words)) {
            return Rejection.CLAUSE_SUBORDINATOR;
        }
        if (beginsWithAPrepositionalModifier(words, trimmed)) {
            return Rejection.PREPOSITIONAL_MODIFIER;
        }
        // The head phrase: when the span opens with a sentence modifier and carries a
        // comma, the part before the comma modifies the whole sentence and the head
        // phrase is what follows.
        List<String> core = coreOf(headPhraseWords(words));
        if (containsAnEmbeddedClause(core)) {
            return Rejection.CLAUSE_RELATIVE;
        }
        if (carriesAParticiple(words, trimmed, strictness)) {
            return Rejection.CLAUSE_PARTICIPLE;
        }
        String head = phraseHead(core);
        String last = bare(core.get(core.size() - 1));
        // The last word and the phrase head are both tested: the span can be cut at a
        // verb ("the closure of ocean basins <b>can</b>") while its head phrase is a
        // noun, and the cut is what makes it a fragment.
        for (String candidate : List.of(last, head)) {
            if (CLAUSE_CUT_WORDS.contains(candidate)) {
                return Rejection.CLAUSE_CUT;
            }
            if (FUNCTION_WORDS.contains(candidate)) {
                return Rejection.FUNCTION_HEAD;
            }
        }
        String first = bare(core.get(0));
        if (CLASS_OPENERS.contains(first)) {
            return Rejection.CLASS_OPENER;
        }
        if (GENERIC_HEADS.contains(head)) {
            return Rejection.GENERIC_HEAD;
        }
        if (DISCOURSE_HEADS.contains(head) && !hasSpecifier(core)) {
            return Rejection.DISCOURSE_HEAD;
        }
        if (isABarePlural(core)) {
            return Rejection.BARE_PLURAL;
        }
        if (isAPossessiveWithoutANoun(trimmed)) {
            return Rejection.FUNCTION_HEAD;
        }
        return Rejection.NONE;
    }

    /**
     * Whether a surface form is accepted as a referent.
     *
     * @param surface the subject phrase
     * @return whether it denotes something
     */
    public static boolean accepted(String surface) {
        return accepted(surface, Strictness.LENIENT);
    }

    /**
     * Whether a surface form is a referent at a given strictness.
     *
     * @param surface the surface form
     * @param strictness the strictness of the policy
     * @return whether it is accepted
     */
    public static boolean accepted(String surface, Strictness strictness) {
        return rejection(surface, strictness) == Rejection.NONE;
    }

    /**
     * The lemma of a surface form: the whole normalised phrase.
     *
     * <p>The normalisation is the research's own: the case folded, the leading
     * "the" stripped, the possessive stripped, and the plural of every word
     * normalised. It is kept in that shape so that the numbers this policy produces
     * are comparable with the research's (323 distinct lemmas over 326 concepts on
     * the seed corpus).</p>
     *
     * <p>The lemma keeps the modifier chain, which is what makes "continental crust"
     * and "oceanic crust" two lemmas and therefore two referents.</p>
     *
     * @param surface the subject phrase
     * @return the normalised lemma
     */
    public static String lemma(String surface) {
        if (surface == null) {
            return "";
        }
        String text = surface.strip().toLowerCase(Locale.ROOT).replaceFirst("^the\\s+", "");
        List<String> words = new java.util.ArrayList<>();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            words.add(bare(singular(possessive(word))));
        }
        return String.join(" ", words);
    }

    /**
     * The head of a surface form: its last word, normalised.
     *
     * @param surface the subject phrase
     * @return the normalised head
     */
    public static String head(String surface) {
        if (surface == null || surface.isBlank()) {
            return "";
        }
        List<String> words = words(surface.strip());
        if (words.isEmpty()) {
            return "";
        }
        return singular(bare(words.get(words.size() - 1)));
    }

    /**
     * Whether a lemma is SPECIFIC: its head is not a class, not a discourse nominal
     * and not a bare function word.
     *
     * <p>This is the rule that lets the same referent be recognised in two documents
     * without an explicit alias entry.</p>
     *
     * @param lemma the normalised lemma
     * @return whether the lemma denotes one specific thing
     */
    public static boolean specific(String lemma) {
        if (lemma == null || lemma.isBlank()) {
            return false;
        }
        List<String> words = words(lemma);
        if (words.isEmpty()) {
            return false;
        }
        String head = bare(words.get(words.size() - 1));
        return !GENERIC_HEADS.contains(head) && !DISCOURSE_HEADS.contains(head)
                && !FUNCTION_WORDS.contains(head) && !CLAUSE_CUT_WORDS.contains(head);
    }

    /**
     * The context signature of a claim: the content words of its own sentence, with
     * the lemma's own words removed.
     *
     * <p>This is the mechanism that keeps <b>the same word in different contexts</b>
     * distinct. Two occurrences of a BARE head ("crust", "plate", "trench") are one
     * referent only when their sentences say the same thing around them; two
     * occurrences that carry a modifier are already distinguished by the lemma.</p>
     *
     * @param sentence the claim's own sentence
     * @param lemma the claim's lemma
     * @return the signature, or an empty string when the sentence carries none
     */
    public static String contextSignature(String sentence, String lemma) {
        if (sentence == null || sentence.isBlank()) {
            return "";
        }
        List<String> lemmaWords = words(lemma == null ? "" : lemma);
        List<String> kept = new java.util.ArrayList<>();
        for (String word : words(sentence.toLowerCase(Locale.ROOT))) {
            String bare = bare(word);
            if (bare.isEmpty() || FUNCTION_WORDS.contains(bare) || CLAUSE_CUT_WORDS.contains(bare)) {
                continue;
            }
            if (lemmaWords.contains(bare)) {
                continue;
            }
            kept.add(bare);
        }
        kept.sort(String::compareTo);
        return String.join(" ", kept);
    }

    /**
     * Whether the alias table declares two lemmas to be the same referent.
     *
     * @param first the first lemma
     * @param second the second lemma
     * @return the kind of the declaration, or null when there is none
     */
    public static ConceptAlias.AliasKind declaredAs(String first, String second) {
        return declaredAs(DECLARED_ALIASES, first, second);
    }

    /**
     * Whether an alias table declares two lemmas to be the same referent.
     *
     * @param aliases the alias table — the store's rows, or the declared list
     * @param first the first lemma
     * @param second the second lemma
     * @return the kind of the declaration, or null when there is none
     */
    public static ConceptAlias.AliasKind declaredAs(List<ConceptAlias> aliases, String first,
                                                    String second) {
        for (ConceptAlias alias : aliases) {
            if ((alias.canonicalLemma().equals(first) && alias.variantLemma().equals(second))
                    || (alias.canonicalLemma().equals(second)
                            && alias.variantLemma().equals(first))) {
                return alias.kind();
            }
        }
        return null;
    }

    /**
     * Whether the alias table declares two lemmas to be DIFFERENT referents.
     *
     * @param first the first lemma
     * @param second the second lemma
     * @return whether they are declared distinct
     */
    public static boolean declaredDistinct(String first, String second) {
        return declaredDistinct(DECLARED_ALIASES, first, second);
    }

    /**
     * Whether an alias table declares two lemmas to be DIFFERENT referents.
     *
     * @param aliases the alias table
     * @param first the first lemma
     * @param second the second lemma
     * @return whether they are declared distinct
     */
    public static boolean declaredDistinct(List<ConceptAlias> aliases, String first,
                                           String second) {
        return declaredAs(aliases, first, second) == ConceptAlias.AliasKind.DISTINCT;
    }

    /**
     * The lemma a variant resolves INTO, or the lemma itself when there is no entry.
     *
     * @param lemma the lemma
     * @return the canonical lemma
     */
    public static String canonicalLemma(String lemma) {
        return canonicalLemma(DECLARED_ALIASES, lemma);
    }

    /**
     * The lemma a variant resolves INTO, under an alias table.
     *
     * @param aliases the alias table
     * @param lemma the lemma
     * @return the canonical lemma
     */
    public static String canonicalLemma(List<ConceptAlias> aliases, String lemma) {
        for (ConceptAlias alias : aliases) {
            if (alias.kind() == ConceptAlias.AliasKind.DISTINCT) {
                continue;
            }
            if (alias.variantLemma().equals(lemma)) {
                return alias.canonicalLemma();
            }
            if (alias.canonicalLemma().equals(lemma)) {
                return alias.canonicalLemma();
            }
        }
        return lemma;
    }

    // ── the helpers ───────────────────────────────────────────────────────────

    /**
     * The head phrase of a span: the words after the sentence modifier.
     *
     * <p>A span that opens with a preposition, a subordinating conjunction or a
     * discourse adverbial AND carries a comma is an adverbial followed by a subject.
     * The part before the comma modifies the whole sentence; the head phrase is what
     * follows it.</p>
     *
     * @param words the words of the span
     * @return the words of the head phrase
     */
    private static List<String> headPhraseWords(List<String> words) {
        if (words.size() < 2) {
            return words;
        }
        String first = bare(words.get(0));
        boolean opensWithAModifier = PREPOSITIONS.contains(first)
                || SUBORDINATORS.contains(first) || DISCOURSE_ADVERBIALS.contains(first);
        for (int index = 0; index < words.size() - 1; index++) {
            if (!words.get(index).endsWith(",")) {
                continue;
            }
            if (opensWithAModifier) {
                // An adverbial before the comma modifies the whole sentence: the
                // head phrase is what follows it.
                return words.subList(index + 1, words.size());
            }
            // Otherwise the comma opens an appositive: the head phrase is what
            // precedes it, the comma included.
            return words.subList(0, index + 1);
        }
        return words;
    }

    /**
     * Whether a span concatenates two units: a heading and a sentence.
     *
     * <p>The structural signal is a capitalised CONTENT word in the middle of the span,
     * in a span whose content words are not all capitalised. A proper noun
     * ("Geological Survey", "Jason Morgan") has every content word capitalised and is
     * not a concatenation.</p>
     *
     * @param surface the span
     * @return whether the span concatenates two units
     */
    private static boolean isConcatenation(String surface) {
        List<String> words = words(surface);
        List<String> content = new ArrayList<>();
        for (String word : words) {
            String bare = bare(word);
            if (bare.isEmpty() || FUNCTION_WORDS.contains(bare)) {
                continue;
            }
            content.add(bare);
        }
        if (content.size() < 2) {
            return false;
        }
        boolean allCapitalised = true;
        for (String word : content) {
            if (!isCapitalised(surface, word)) {
                allCapitalised = false;
                break;
            }
        }
        if (allCapitalised) {
            return false;
        }
        // A capitalised word in the MIDDLE of the span, between two lower-case
        // words: the span concatenates a heading and a sentence.
        //
        // Three refinements keep the rule off the proper nouns, which are legitimate
        // concepts: a capitalised word right after a determiner is a proper adjective
        // ("the African plate"), a capitalised word that ends in "'s" is a name's
        // possessive ("Earth's interior"), and a capitalised word followed by another
        // capitalised word is a name ("Alfred Wegener").
        List<String> all = words(surface);
        List<Boolean> flags = capitalisation(surface);
        for (int index = 1; index < all.size() - 1; index++) {
            if (!flags.get(index) || flags.get(index - 1)) {
                continue;
            }
            String word = all.get(index);
            if (word.endsWith("'s") || word.endsWith("\u2019s")) {
                continue;
            }
            String previous = bare(all.get(index - 1));
            if (DETERMINERS.contains(previous)) {
                continue;
            }
            if (flags.get(index + 1) && !CAPITALISED_FUNCTION_WORDS.contains(bare(all.get(index + 1)))) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Whether a word occurs in the surface with its first letter capitalised.
     *
     * @param surface the span
     * @param word the word, lower-cased
     * @return whether the word is capitalised in the span
     */
    private static boolean isCapitalised(String surface, String word) {
        String lower = surface.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(word);
        while (at >= 0) {
            char first = surface.charAt(at);
            if (Character.isUpperCase(first)) {
                return true;
            }
            at = lower.indexOf(word, at + 1);
        }
        return false;
    }

    /**
     * The capitalisation of every word of a span, position by position.
     *
     * @param surface the span
     * @return one flag per word
     */
    private static List<Boolean> capitalisation(String surface) {
        List<Boolean> flags = new ArrayList<>();
        int index = 0;
        while (index < surface.length()) {
            if (Character.isLetter(surface.charAt(index))) {
                int end = index;
                while (end < surface.length() && Character.isLetter(surface.charAt(end))) {
                    end++;
                }
                flags.add(Character.isUpperCase(surface.charAt(index)));
                index = end;
            } else {
                index++;
            }
        }
        return flags;
    }

    /**
     * Whether a span repeats a phrase: it is a caption.
     *
     * @param words the words of the span
     * @return whether a phrase of two or more words occurs twice
     */
    private static boolean repeatsAPhrase(List<String> words) {
        for (int size = 2; size <= 3; size++) {
            for (int start = 0; start + size <= words.size(); start++) {
                String phrase = String.join(" ", bare(words.subList(start, start + size)));
                String rest = String.join(" ", bare(words.subList(start + size, words.size())));
                if (!rest.isEmpty() && rest.contains(phrase)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a span begins with a subordinating conjunction.
     *
     * @param words the words of the span
     * @return whether it does
     */
    private static boolean beginsWithASubordinator(List<String> words) {
        return !words.isEmpty() && SUBORDINATORS.contains(bare(words.get(0)));
    }

    /**
     * Whether a span opens with a prepositional modifier.
     *
     * <p>A span that opens with a preposition and a determiner — "In <b>the</b> assignment
     * under consideration", "For <b>the</b> organism" — modifies something outside itself:
     * it says <i>where</i> or <i>for what</i>, not <i>what</i>. A concept names a thing, so
     * such a span cannot be one.
     *
     * <p>Two conditions keep the rule narrow. The span must carry <b>no comma</b>: a span
     * that opens with a modifier AND carries a comma is an adverbial plus a subject, and
     * the head phrase is what follows the comma — that case is already cut elsewhere. And
     * the preposition must be followed by a <b>determiner</b>: "In vitro photosynthesis"
     * opens with a preposition too, but it names a thing (the qualifier is a noun, not a
     * determiner), so it stays a referent.</p>
     *
     * @param words the words of the span
     * @param surface the span itself
     * @return whether the span is a prepositional modifier
     */
    private static boolean beginsWithAPrepositionalModifier(List<String> words, String surface) {
        if (words.size() < 2 || surface.contains(",")) {
            return false;
        }
        if (!PREPOSITIONS.contains(bare(words.get(0)))) {
            return false;
        }
        return DETERMINERS.contains(bare(words.get(1)));
    }

    /**
     * Whether the head phrase contains an embedded relative or complement clause.
     *
     * @param core the words of the head phrase
     * @return whether it does
     */
    private static boolean containsAnEmbeddedClause(List<String> core) {
        for (int index = 0; index < core.size() - 1; index++) {
            if (RELATIVISERS.contains(bare(core.get(index)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the head phrase carries a participial modifier.
     *
     * @param core the words of the head phrase
     * @return whether it does
     */
    private static boolean carriesAParticiple(List<String> all, String surface,
                                              Strictness strictness) {
        List<Boolean> flags = capitalisation(surface);
        for (int index = 0; index < all.size(); index++) {
            String word = bare(all.get(index));
            if (flags.get(index)) {
                // A capitalised word is a name, not a participle ("Alfred Wegener").
                continue;
            }
            if (word.length() > 5 && word.endsWith("ed")) {
                // A past participle is almost never a noun in this corpus.
                return true;
            }
            if (strictness == Strictness.STRICT && word.length() > 5
                    && word.endsWith("ing")) {
                // A present participle is often a technical noun ("seafloor
                // spreading"), so this rule belongs to the strict level only.
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the head phrase is a bare plural with no modifier and no determiner.
     *
     * <p>"plates" names the plates as a class; "subduction zones" names a kind. The
     * difference is structural: the bare plural has no modifier and no determiner.</p>
     *
     * @param core the words of the head phrase
     * @return whether it is a bare plural
     */
    private static boolean isABarePlural(List<String> core) {
        if (core.size() != 1) {
            return false;
        }
        String word = bare(core.get(0));
        if (word.length() < 4 || !word.endsWith("s") || word.endsWith("ss")) {
            return false;
        }
        return !PROPER_NOUNS.contains(word);
    }

    /**
     * The core of a surface form: the words after the leading discourse adverbials.
     *
     * @param words the words of the surface form
     * @return the core
     */
    private static List<String> coreOf(List<String> words) {
        int index = 0;
        while (index < words.size() - 1
                && DISCOURSE_ADVERBIALS.contains(bare(words.get(index)))) {
            index++;
        }
        return words.subList(index, words.size());
    }

    /**
     * Whether a span is a possessive without a noun ("Wegener's").
     *
     * <p>It denotes nothing: the possessor is named, but not what is possessed.</p>
     *
     * @param words the words of the span
     * @return whether it is a bare possessive
     */
    private static boolean isAPossessiveWithoutANoun(String surface) {
        return surface.matches("(?i)\\p{Lu}\\p{Ll}+['\u2019]s");
    }

    /**
     * The head of a head phrase.
     *
     * <p>When the phrase carries a prepositional complement ("the theory <b>of</b>
     * plate tectonics"), the head is the noun BEFORE the preposition: the complement
     * modifies the head, it does not become it. When there is no complement, the head
     * is the last word.</p>
     *
     * @param core the words of the head phrase
     * @return the normalised head
     */
    private static String phraseHead(List<String> core) {
        for (int index = 1; index < core.size(); index++) {
            if (!COMPLEMENT_PREPOSITIONS.contains(bare(core.get(index)))) {
                continue;
            }
            for (int back = index - 1; back >= 0; back--) {
                String word = bare(core.get(back));
                if (word.isEmpty() || FUNCTION_WORDS.contains(word)) {
                    continue;
                }
                return singular(word);
            }
        }
        return singular(bare(core.get(core.size() - 1)));
    }

    /**
     * Whether a surface form carries something that SPECIFIES its head.
     *
     * <p>"The theory of plate tectonics" specifies "theory"; "This theory" does not.
     * A determiner is not a specification.</p>
     */
    private static boolean hasSpecifier(List<String> words) {
        int content = 0;
        boolean insideAnAppositive = false;
        for (String word : words) {
            String bare = bare(word);
            if (word.endsWith(",")) {
                insideAnAppositive = true;
                continue;
            }
            if (insideAnAppositive) {
                // An appositive is part of the phrase, but it does not SPECIFY the
                // head: "this theory, called 'surge tectonics'" names a discourse
                // nominal, not a referent.
                continue;
            }
            if (FUNCTION_WORDS.contains(bare) || CLAUSE_CUT_WORDS.contains(bare)) {
                continue;
            }
            content++;
        }
        return content > 1;
    }

    /** The words of a text: the runs of letters, lower-cased. */
    private static List<String> words(String text) {
        List<String> words = new java.util.ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (Character.isLetter(text.charAt(index))) {
                int end = index;
                while (end < text.length() && (Character.isLetter(text.charAt(end))
                        || (text.charAt(end) == '\'' && end > index))) {
                    end++;
                }
                // The trailing punctuation travels with the word: the comma and the
                // parenthesis are the structural signals of the policy and they must
                // survive the tokenisation.
                int after = end;
                while (after < text.length() && ",.;:()[]\"".indexOf(text.charAt(after)) >= 0) {
                    after++;
                }
                words.add(text.substring(index, after).toLowerCase(Locale.ROOT));
                index = after;
            } else {
                index++;
            }
        }
        return words;
    }

    /** The word with its surrounding punctuation removed. */
    private static String bare(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[,.;:()\"'\\[\\]]", "");
    }

    /** The words with their surrounding punctuation removed. */
    private static List<String> bare(List<String> words) {
        List<String> bare = new ArrayList<>(words.size());
        for (String word : words) {
            bare.add(bare(word));
        }
        return bare;
    }

    /**
     * The base of a possessive word: "earth's" becomes "earth".
     *
     * @param word the word
     * @return the base
     */
    private static String possessive(String word) {
        if (word.endsWith("'s") || word.endsWith("\u2019s")) {
            return word.substring(0, word.length() - 2);
        }
        return word;
    }

    /**
     * The singular of a word.
     *
     * <p><b>This differs from the research's normalisation in one place, and the
     * difference is a fix.</b> The research's rule stripped "es" from every word that
     * ends in "es" and does not end in "ses", so "plates" became "plat" and "zones"
     * became "zon" — lemmas that are not words. The rule here is the English one:
     * "-ies" becomes "-y", "-sses", "-xes", "-ches" and "-shes" lose the "es", and
     * everything else that ends in a single "s" loses it.</p>
     *
     * @param word the word
     * @return the singular
     */
    private static String singular(String word) {
        if (word.length() > 3 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 3 && (word.endsWith("sses") || word.endsWith("xes")
                || word.endsWith("ches") || word.endsWith("shes"))) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private ReferentPolicy() {
    }
}
