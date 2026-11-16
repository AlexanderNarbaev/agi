package io.matrix.knowledge.concept.research;

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
    public static final Set<String> FUNCTION_WORDS = Set.of(
            "it", "its", "they", "them", "their", "these", "those", "this", "that", "he",
            "she", "his", "her", "we", "us", "our", "you", "your", "there", "here",
            "the", "a", "an", "one", "ones", "other", "others", "another", "such",
            "and", "or", "but", "nor", "yet", "so", "because", "although", "while",
            "whereas", "if", "when", "since", "unless", "until", "than", "as",
            "which", "who", "whom", "whose", "what", "how", "why", "to", "of", "in",
            "on", "at", "by", "for", "from", "with", "without", "into", "onto", "over",
            "under", "above", "below", "after", "before", "during", "between", "among",
            "through", "across", "along", "around", "behind", "beyond", "near",
            "toward", "towards", "upon", "within", "per", "via", "is", "are", "was",
            "were", "be", "been", "being", "am", "has", "have", "had", "do", "does",
            "did", "can", "could", "will", "would", "shall", "should", "may", "might",
            "must", "ought", "not", "no", "also", "even", "just", "rather", "quite",
            "very", "too", "only", "still", "already", "again", "further", "indeed", "perhaps");

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
    public static final Set<String> CLASS_OPENERS = Set.of(
            "many", "most", "some", "several", "various", "different", "other", "others",
            "such", "these", "those", "both", "all", "each", "every", "few", "more",
            "less", "numerous", "certain", "example", "examples", "another");

    /**
     * The heads that denote a class of persons or of unnamed things.
     *
     * <p>The hand-labelling protocol of the A1 instrument counts "Scientists" and
     * "Geologists" as <b>not</b> concepts: they name people in general, not a person
     * or a class of entity that a claim is about.</p>
     */
    public static final Set<String> GENERIC_HEADS = Set.of(
            "scientist", "scientists", "geologist", "geologists", "author", "authors",
            "researcher", "researchers", "student", "students", "people", "person",
            "persons", "man", "woman", "workers", "worker", "reader", "readers",
            "belief", "beliefs", "evidence", "idea", "ideas", "question", "questions",
            "answer", "answers", "problem", "problems", "conclusion", "conclusions",
            "finding", "findings", "result", "results", "example", "examples",
            "difference", "differences", "similarity", "relationship", "relationships",
            "importance", "significance", "understanding", "view", "views", "argument",
            "arguments", "issue", "issues", "factor", "factors", "effect", "effects",
            "process", "processes", "mechanism", "mechanisms", "system", "systems",
            "event", "events", "phenomenon", "phenomena", "period", "periods",
            "region", "regions", "area", "areas", "series", "group", "groups",
            "range", "ranges", "era", "eras", "episode", "episodes");

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
    public static final Set<String> DISCOURSE_ADVERBIALS = Set.of(
            "initially", "finally", "then", "later", "originally", "typically",
            "generally", "commonly", "usually", "however", "moreover", "furthermore",
            "nevertheless", "nonetheless", "thus", "therefore", "hence", "now", "today",
            "recently", "currently", "earlier", "subsequently", "previously");

    /** The most words a surface form may have and still be a phrase. */
    public static final int MAX_SURFACE_WORDS = 12;

    // ── the alias table ───────────────────────────────────────────────────────

    /** Why two lemmas are declared to be the same referent, or not. */
    public enum AliasKind {
        /** The same referent written differently: a hyphenation or a spelling variant. */
        SPELLING,
        /** The same referent under a different word: "slab pull" and "the slab pull force". */
        SYNONYM,
        /** The same referent under a different wording: "hotspot tracks" and "the tracks of hotspots". */
        PARAPHRASE,
        /** NOT the same referent, whatever the text suggests: a homonym. */
        DISTINCT
    }

    /**
     * One declared entry of the alias table.
     *
     * @param canonical the lemma the variant resolves TO
     * @param variant the lemma that resolves INTO the canonical one
     * @param kind why the two are (or are not) the same referent
     * @param reason the reason, in the words of the corpus
     */
    public record Alias(String canonical, String variant, AliasKind kind, String reason) {
    }

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
    public static final List<Alias> DECLARED_ALIASES = List.of(
            new Alias("seafloor spreading", "sea-floor spreading", AliasKind.SPELLING,
                    "the corpus writes the term with and without the hyphen"),
            new Alias("slab pull", "slab pull force", AliasKind.SYNONYM,
                    "the corpus writes 'the slab pull force' and 'slab pull' for the same force"),
            new Alias("lithosphere", "earth lithosphere", AliasKind.SYNONYM,
                    "the corpus's 'Earth's lithosphere' and 'the lithosphere' name the same layer"),
            new Alias("plate motion", "motion of the tectonic plate", AliasKind.PARAPHRASE,
                    "the corpus's 'the motion of the tectonic plates' and 'plate motion' name "
                            + "the same referent"),
            new Alias("hotspot track", "track of hotspot", AliasKind.PARAPHRASE,
                    "the corpus's 'the tracks of hotspots' and 'hotspot tracks' name the same "
                            + "feature"),
            new Alias("divergent boundary", "divergent plate boundary", AliasKind.SYNONYM,
                    "the corpus writes both 'divergent boundary' and 'divergent plate boundary'"),
            new Alias("continental drift", "wegener theory of continental drift", AliasKind.SYNONYM,
                    "the corpus's 'Wegener's theory of continental drift' and 'continental drift' "
                            + "name the same theory"),
            new Alias("crust", "oceanic crust", AliasKind.DISTINCT,
                    "the generic crust and the oceanic crust are not one referent"),
            new Alias("crust", "continental crust", AliasKind.DISTINCT,
                    "the generic crust and the continental crust are not one referent"),
            new Alias("plate", "nazca plate", AliasKind.DISTINCT,
                    "the generic plate and one named plate are not one referent"),
            new Alias("plate", "pacific plate", AliasKind.DISTINCT,
                    "the generic plate and one named plate are not one referent"),
            new Alias("trench", "middle america trench", AliasKind.DISTINCT,
                    "the generic trench and one named trench are not one referent"));

    // ── the rules ─────────────────────────────────────────────────────────────

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
        ARTEFACT
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
                || trimmed.contains(": ")) {
            return Rejection.ARTEFACT;
        }
        // The A1 labelling protocol strips a leading discourse adverbial before the
        // head is judged, and the policy judges the same thing.
        List<String> core = coreOf(words);
        String head = bare(core.get(core.size() - 1));
        if (CLAUSE_CUT_WORDS.contains(head)) {
            return Rejection.CLAUSE_CUT;
        }
        if (FUNCTION_WORDS.contains(head)) {
            return Rejection.FUNCTION_HEAD;
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
        return Rejection.NONE;
    }

    /**
     * Whether a surface form is accepted as a referent.
     *
     * @param surface the subject phrase
     * @return whether it denotes something
     */
    public static boolean accepted(String surface) {
        return rejection(surface) == Rejection.NONE;
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
            words.add(singular(possessive(word)));
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
    public static AliasKind declaredAs(String first, String second) {
        for (Alias alias : DECLARED_ALIASES) {
            if ((alias.canonical().equals(first) && alias.variant().equals(second))
                    || (alias.canonical().equals(second) && alias.variant().equals(first))) {
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
        return declaredAs(first, second) == AliasKind.DISTINCT;
    }

    /**
     * The lemma a variant resolves INTO, or the lemma itself when there is no entry.
     *
     * @param lemma the lemma
     * @return the canonical lemma
     */
    public static String canonicalLemma(String lemma) {
        for (Alias alias : DECLARED_ALIASES) {
            if (alias.kind() == AliasKind.DISTINCT) {
                continue;
            }
            if (alias.variant().equals(lemma)) {
                return alias.canonical();
            }
            if (alias.canonical().equals(lemma)) {
                return alias.canonical();
            }
        }
        return lemma;
    }

    // ── the helpers ───────────────────────────────────────────────────────────

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
     * Whether a surface form carries something that SPECIFIES its head.
     *
     * <p>"The theory of plate tectonics" specifies "theory"; "This theory" does not.
     * A determiner is not a specification.</p>
     */
    private static boolean hasSpecifier(List<String> words) {
        int content = 0;
        for (String word : words) {
            String bare = bare(word);
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
                while (end < text.length() && Character.isLetter(text.charAt(end))) {
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

    /** The word with its surrounding punctuation removed. */
    private static String bare(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[,.;:()\"'\\[\\]]", "");
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
