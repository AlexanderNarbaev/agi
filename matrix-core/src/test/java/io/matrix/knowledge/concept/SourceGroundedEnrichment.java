package io.matrix.knowledge.concept;

import io.matrix.contract.Provenance;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimSpan;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.EpistemicPolicy;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.DocumentExtractorRegistry;
import io.matrix.knowledge.acquisition.DocumentFormat;
import io.matrix.knowledge.acquisition.DocumentImporter;
import io.matrix.knowledge.acquisition.ExtractedDocument;
import io.matrix.knowledge.acquisition.SourceRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * C2A (Workstream B3+B5) — the source-grounded extractor: definitions and typed relations,
 * each with its provenance.
 *
 * <p><b>This is research code.</b> It lives in the TEST source set on purpose: the production
 * retrieval path must not change, and the point of the experiment is that the candidates are
 * applied to the snapshot BEFORE the production retriever sees it, or to the question BEFORE it
 * is asked. Nothing here is written into {@code matrix-core/src/main/java}.</p>
 *
 * <p><b>The scientific contract is in {@code docs-v2/engineering/C2A-CANDIDATE-FREEZE.md}.</b>
 * This class implements §3 of that freeze and nothing else. In short:</p>
 *
 * <ul>
 *   <li>a <b>definition</b> candidate for a concept is the FIRST sentence of the concept's own
 *       source document that contains the concept's name as a word AND a definitional copula
 *       ("is", "are", "refers to", "means", "describes", "consists of", "is defined as",
 *       "is called"). The definition IS that sentence, verbatim. A concept with no such
 *       sentence has NO definition — nothing is ever invented or paraphrased;</li>
 *   <li>a <b>relation</b> candidate between two concepts is found ONLY when ONE sentence holds
 *       BOTH concept names AND a trigger phrase of the relation type, and the trigger phrase
 *       separates them: the concept before the trigger is the FROM endpoint, the concept after
 *       it is the TO endpoint. The vocabulary is closed: IS_A, PRODUCES, PART_OF, CAUSED_BY,
 *       MEASURED_BY;</li>
 *   <li>one hop only, no transitive closure, never a relation from co-occurrence alone;</li>
 *   <li>every candidate carries its source id, its verbatim span, the character offsets of that
 *       span in the extracted text of the source document, the concept ids involved, the relation
 *       type and direction, the extraction method name and the evidentiary status
 *       (CANDIDATE = extracted and not independently verified; SUPPORTED = the production
 *       validator has already promoted a claim carrying that very sentence).</li>
 * </ul>
 *
 * <p>The sentences are the production sentences: the propositions of the workspace's claims,
 * which are what {@code ClaimExtractor.propose} produced from the production
 * {@code ExtractedDocument}. The offsets are counted in characters of the extracted text, the
 * convention {@link ClaimSpan} already declares.</p>
 */
public final class SourceGroundedEnrichment {

    /** The name of the method that finds a definition candidate. */
    public static final String DEFINITION_METHOD = "definitional-copula-first-sentence-v1";

    /** The name of the method that finds a relation candidate. */
    public static final String RELATION_METHOD = "typed-relation-trigger-phrase-v1";

    /** The unit the offsets are counted in: characters of the extracted text. */
    public static final String OFFSET_UNIT = "CHARACTERS";

    /** The definitional copulas of the frozen rule. */
    public static final List<String> DEFINITIONAL_COPULAS = List.of(
            "is", "are", "refers to", "means", "describes", "consists of",
            "is defined as", "is called");

    /** The bounded relation vocabulary. */
    public enum RelationType {
        IS_A, PRODUCES, PART_OF, CAUSED_BY, MEASURED_BY
    }

    /** How much the candidate is worth. */
    public enum EvidentiaryStatus {
        /** Extracted from the source document; not independently verified. */
        CANDIDATE,
        /** The production validator has already promoted a claim carrying this sentence. */
        SUPPORTED
    }

    /** A definition candidate for one concept. */
    public record DefinitionCandidate(
            String conceptId,
            String conceptName,
            String definition,
            String sourceId,
            String sourceFile,
            String sourceSha256,
            int startOffset,
            int endOffset,
            String offsetUnit,
            String offsetPrecision,
            String extractionMethod,
            EvidentiaryStatus status,
            String supportingClaimId) {
    }

    /** A typed relation candidate between two concepts. */
    public record RelationCandidate(
            String fromConceptId,
            String fromConceptName,
            String toConceptId,
            String toConceptName,
            RelationType type,
            String direction,
            String sourceId,
            String sourceFile,
            String sourceSha256,
            int startOffset,
            int endOffset,
            String offsetUnit,
            String offsetPrecision,
            String span,
            String extractionMethod,
            EvidentiaryStatus status,
            String supportingClaimId) {
    }

    /** A candidate that was NOT accepted, with the reason why. */
    public record Rejection(
            String reason,
            String sourceId,
            String sourceFile,
            String span,
            String trigger,
            String detail) {
    }

    /** What the extraction found. */
    public record Extraction(
            List<DefinitionCandidate> definitions,
            List<RelationCandidate> relations,
            List<Rejection> rejections,
            List<String> conceptsWithoutDefinition,
            Map<String, List<String>> relatedByConcept,
            int conceptCount,
            int documentCount,
            int sentenceCount,
            long extractionMillis,
            long retainedBytes) {

        public Extraction {
            definitions = List.copyOf(definitions);
            relations = List.copyOf(relations);
            rejections = List.copyOf(rejections);
            conceptsWithoutDefinition = List.copyOf(conceptsWithoutDefinition);
            relatedByConcept = Map.copyOf(relatedByConcept);
        }

        /** The definitions that the validator has already promoted. */
        public long supportedDefinitions() {
            return definitions.stream()
                    .filter(d -> d.status() == EvidentiaryStatus.SUPPORTED).count();
        }

        /** The relations that the validator has already promoted. */
        public long supportedRelations() {
            return relations.stream()
                    .filter(r -> r.status() == EvidentiaryStatus.SUPPORTED).count();
        }

        /** How many candidates were rejected for a reason. */
        public long rejectedFor(String reason) {
            return rejections.stream().filter(r -> r.reason().equals(reason)).count();
        }
    }

    // ── the frozen relation vocabulary and its trigger phrases ─────────────────

    /** The trigger phrases of every relation type. */
    private static final Map<RelationType, List<String>> TRIGGERS = Map.of(
            RelationType.IS_A, List.of(
                    "is a", "is an", "are a", "are an", "is the", "are the", "is called",
                    "is known as", "refers to", "is defined as", "is classified as",
                    "is considered a", "is considered an", "is considered the",
                    "is a type of", "is a kind of", "is a form of", "is an example of",
                    "is an instance of", "is one of"),
            RelationType.PRODUCES, List.of(
                    "produces", "produce", "produced", "producing", "creates", "created",
                    "creating", "forming", "forms", "formed", "gives rise to", "give rise to",
                    "results in", "result in", "leading to", "leads to", "causes", "cause",
                    "causing", "triggers", "trigger", "is responsible for",
                    "are responsible for", "contributes to", "contribute to", "generates",
                    "generate", "yields", "yield", "emits", "emit"),
            RelationType.PART_OF, List.of(
                    "is part of", "are part of", "part of", "belongs to", "belong to",
                    "makes up", "make up", "comprises", "comprise", "includes", "include",
                    "consists of", "consist of", "is located in", "are located in",
                    "is found in", "are found in", "is situated in", "lies in",
                    "is located within", "is contained in", "is made of", "is made up of",
                    "is divided into", "are divided into", "within"),
            RelationType.CAUSED_BY, List.of(
                    "caused by", "resulting from", "results from", "resulted from", "due to",
                    "as a result of", "driven by", "produced by", "created by", "formed by",
                    "induced by", "owing to", "attributed to", "attributable to", "triggered by"),
            RelationType.MEASURED_BY, List.of(
                    "measured by", "measured with", "measured using", "measured through",
                    "determined by", "tracked by", "recorded by", "observed by", "dated by",
                    "estimated by", "calculated by", "quantified by", "monitored by"));

    /** The words that make a PRODUCES trigger passive. */
    private static final Set<String> PASSIVE_AUXILIARIES = Set.of(
            "is", "are", "was", "were", "be", "been", "being");

    /** The one-word concept names that may not carry a relation. */
    private static final Set<String> FUNCTION_WORDS = Set.of(
            "it", "this", "that", "there", "they", "these", "those", "most", "many", "other",
            "some", "such", "then", "than", "when", "where", "which", "what", "who", "how",
            "why");

    /** A trigger phrase and the type it states. */
    private record Trigger(RelationType type, List<String> words) {
    }

    /** The trigger phrases, indexed by their first word. */
    private static final Map<String, List<Trigger>> TRIGGERS_BY_FIRST_WORD = indexTriggers();

    private static Map<String, List<Trigger>> indexTriggers() {
        Map<String, List<Trigger>> index = new LinkedHashMap<>();
        for (Map.Entry<RelationType, List<String>> entry : TRIGGERS.entrySet()) {
            for (String phrase : entry.getValue()) {
                List<String> phraseWords = words(phrase);
                if (phraseWords.isEmpty()) {
                    continue;
                }
                index.computeIfAbsent(phraseWords.get(0), key -> new ArrayList<>())
                        .add(new Trigger(entry.getKey(), phraseWords));
            }
        }
        for (List<Trigger> triggers : index.values()) {
            triggers.sort((left, right) -> Integer.compare(right.words().size(),
                    left.words().size()));
        }
        return index;
    }

    // ── the documents ─────────────────────────────────────────────────────────

    /** One source document, with its extracted text. */
    private record CorpusDocument(String file, String sha256, String text) {
    }

    /** The corpus of the seed and of the research pool, by the hash of its bytes. */
    private static volatile Map<String, CorpusDocument> corpusCache;

    private static Map<String, CorpusDocument> corpus() {
        Map<String, CorpusDocument> cached = corpusCache;
        if (cached != null) {
            return cached;
        }
        Map<String, CorpusDocument> byHash = new LinkedHashMap<>();
        for (String directory : List.of(AcquisitionCycle.SEED_CORPUS, AcquisitionCycle.RESEARCH_POOL)) {
            try {
                ClasspathCorpus corpus = ClasspathCorpus.extract(directory);
                for (ClasspathCorpus.Entry entry : corpus.entries()) {
                    byte[] bytes = Files.readAllBytes(entry.path());
                    String hash = DocumentImporter.sha256(bytes);
                    if (!hash.equals(entry.sha256())) {
                        throw new IllegalStateException("the corpus file " + entry.file()
                                + " does not match the sha256 of the manifest: " + hash);
                    }
                    DocumentFormat format = DocumentFormat.of(entry.file());
                    SourceRecord.Builder builder = new SourceRecord.Builder(
                            "src-" + hash, entry.file(), hash, format)
                            .importedAt("1970-01-01T00:00:00Z")
                            .processed("the C2A extraction")
                            .location(entry.file() + " (whole document)");
                    ExtractedDocument document = new DocumentExtractorRegistry()
                            .extract(format, bytes, entry.file(), builder);
                    byHash.put(hash, new CorpusDocument(entry.file(), hash, document.text()));
                }
            } catch (IOException failure) {
                throw new UncheckedIOException("the corpus could not be read", failure);
            }
        }
        corpusCache = byHash;
        return byHash;
    }

    /**
     * Whether a recorded span really holds the recorded text.
     *
     * <p>The offsets are counted in the EXTRACTED TEXT of the source document, the convention
     * {@link ClaimSpan} declares. This method reads the document's bytes again and checks the
     * span against them: a candidate whose offsets do not hold its text is not grounded, and
     * the experiment says so rather than asserting it.</p>
     *
     * @param sha256 the hash of the source document
     * @param start the start offset, inclusive
     * @param end the end offset, exclusive
     * @param expected the text the span must hold
     * @return whether the span holds the text
     */
    public static boolean spanMatches(String sha256, int start, int end, String expected) {
        CorpusDocument document = corpus().get(sha256);
        if (document == null || expected == null) {
            return false;
        }
        if (start < 0 || end > document.text().length() || end - start != expected.length()) {
            return false;
        }
        return document.text().regionMatches(start, expected, 0, expected.length());
    }

    /**
     * Whether a span HOLDS a text, allowing for the whitespace the extractor normalised.
     *
     * <p>When the extractor joined the lines of a segment, the sentence it produced is not a
     * literal substring of the extracted text; the span of the SEGMENT is what can be located
     * exactly, and the sentence is inside it once the whitespace is normalised. This method
     * checks that.</p>
     *
     * @param sha256 the hash of the source document
     * @param start the start offset, inclusive
     * @param end the end offset, exclusive
     * @param expected the text the span must hold
     * @return whether the span holds the text
     */
    public static boolean spanHolds(String sha256, int start, int end, String expected) {
        CorpusDocument document = corpus().get(sha256);
        if (document == null || expected == null) {
            return false;
        }
        if (start < 0 || end > document.text().length() || end < start) {
            return false;
        }
        String region = document.text().substring(start, end).replaceAll("\\s+", " ").strip();
        String wanted = expected.replaceAll("\\s+", " ").strip();
        return region.contains(wanted);
    }

    // ── the extraction ────────────────────────────────────────────────────────

    /**
     * Extracts the definitions and the typed relations of a workspace.
     *
     * @param snapshot the workspace as the production store exports it
     * @return what was found, with every count the report needs
     */
    public static Extraction extract(KnowledgeSnapshot snapshot) {
        long started = System.nanoTime();
        Map<String, CorpusDocument> documents = corpus();

        Map<String, Concept> conceptsById = new LinkedHashMap<>();
        for (Concept concept : snapshot.concepts()) {
            conceptsById.put(concept.id(), concept);
        }
        Map<String, Source> sourcesById = new LinkedHashMap<>();
        for (Source source : snapshot.sources()) {
            sourcesById.put(source.id(), source);
        }
        Map<String, List<Concept>> conceptsByFirstWord = indexConcepts(conceptsById);

        // THE SENTENCES OF THE WORKSPACE, IN DOCUMENT ORDER.
        //
        // A sentence IS a claim of the workspace: the importer writes one
        // claim per extracted proposition. The sentences are grouped by their
        // source and ordered by the offset of the sentence inside the
        // extracted text, which is the document order.
        Map<String, List<Sentence>> sentencesBySource = new LinkedHashMap<>();
        Map<String, List<String>> sourceIdsByConcept = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            String sourceId = claim.introducedBySourceId();
            if (sourceId == null) {
                continue;
            }
            sourceIdsByConcept.computeIfAbsent(claim.conceptId(), key -> new ArrayList<>())
                    .add(sourceId);
            Source source = sourcesById.get(sourceId);
            CorpusDocument document = source == null ? null : documents.get(source.contentHash());
            if (document == null) {
                continue;
            }
            Provenance.Span segment = ClaimSpan.parse(claim.context());
            int start = segment == null ? 0 : (int) segment.start();
            int end = segment == null || segment.end() == null
                    ? document.text().length() : segment.end().intValue();
            int[] offsets = locate(document.text(), claim.proposition(), start, end);
            sentencesBySource.computeIfAbsent(sourceId, key -> new ArrayList<>())
                    .add(new Sentence(claim, document, offsets[0], offsets[1], offsets[2] == 1
                            ? "EXACT" : "SEGMENT"));
        }
        for (List<Sentence> sentences : sentencesBySource.values()) {
            sentences.sort((left, right) -> Integer.compare(left.start(), right.start()));
        }

        // THE DEFINITIONS.
        List<DefinitionCandidate> definitions = new ArrayList<>();
        List<String> withoutDefinition = new ArrayList<>();
        for (Concept concept : snapshot.concepts()) {
            List<String> own = sourceIdsByConcept.getOrDefault(concept.id(), List.of());
            DefinitionCandidate candidate = definitionOf(concept, own, snapshot, sentencesBySource);
            if (candidate == null) {
                withoutDefinition.add(concept.name());
            } else {
                definitions.add(candidate);
            }
        }

        // THE RELATIONS.
        List<RelationCandidate> relations = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        Map<String, Set<String>> related = new LinkedHashMap<>();
        Set<String> pairTypes = new LinkedHashSet<>();
        for (Source source : snapshot.sources()) {
            List<Sentence> sentences = sentencesBySource.getOrDefault(source.id(), List.of());
            for (Sentence sentence : sentences) {
                relations(conceptsById, conceptsByFirstWord, sentence, relations, rejections,
                        pairTypes, related);
            }
        }
        Map<String, List<String>> relatedByConcept = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : related.entrySet()) {
            relatedByConcept.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        long millis = (System.nanoTime() - started) / 1_000_000L;
        return withRetainedSize(new Extraction(definitions, relations, rejections, withoutDefinition,
                relatedByConcept, conceptsById.size(), sentencesBySource.size(),
                sentencesBySource.values().stream().mapToInt(List::size).sum(), millis, 0L));
    }

    /** One sentence of a source document: a claim, with the place it was read from. */
    private record Sentence(Claim claim, CorpusDocument document, int start, int end,
            String precision) {
        String text() {
            return claim.proposition();
        }
    }

    /**
     * The definition candidate of a concept, or null when it has none.
     *
     * <p>The concept's own source documents are the sources of its claims, taken in the order the
     * snapshot lists the sources; the sentences are scanned in document order; the FIRST
     * sentence that carries the concept's name AND a definitional copula wins.</p>
     */
    private static DefinitionCandidate definitionOf(Concept concept, List<String> own,
            KnowledgeSnapshot snapshot, Map<String, List<Sentence>> sentencesBySource) {
        for (Source source : snapshot.sources()) {
            if (!own.contains(source.id())) {
                continue;
            }
            for (Sentence sentence : sentencesBySource.getOrDefault(source.id(), List.of())) {
                if (!holdsName(sentence.text(), concept.name())) {
                    continue;
                }
                if (!holdsCopula(sentence.text())) {
                    continue;
                }
                boolean supported = EpistemicPolicy.isCitable(sentence.claim().status());
                return new DefinitionCandidate(concept.id(), concept.name(), sentence.text(),
                        sentence.claim().introducedBySourceId(),
                        sentence.document() == null ? "" : sentence.document().file(),
                        sentence.document() == null ? "" : sentence.document().sha256(),
                        sentence.start(), sentence.end(), OFFSET_UNIT, sentence.precision(),
                        DEFINITION_METHOD,
                        supported ? EvidentiaryStatus.SUPPORTED : EvidentiaryStatus.CANDIDATE,
                        sentence.claim().id());
            }
        }
        return null;
    }

    /** Whether a sentence holds a definitional copula. */
    static boolean holdsCopula(String sentence) {
        List<String> sentenceWords = words(sentence);
        for (String copula : DEFINITIONAL_COPULAS) {
            if (containsSequence(sentenceWords, words(copula))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a sentence holds a name, as a word or as a contiguous token sequence. */
    static boolean holdsName(String sentence, String name) {
        List<String> nameWords = words(name);
        if (nameWords.isEmpty()) {
            return false;
        }
        return containsSequence(words(sentence), nameWords);
    }

    /** Whether a sequence of words holds another sequence, contiguously. */
    private static boolean containsSequence(List<String> haystack, List<String> needle) {
        if (needle.isEmpty() || needle.size() > haystack.size()) {
            return false;
        }
        outer:
        for (int start = 0; start + needle.size() <= haystack.size(); start++) {
            for (int offset = 0; offset < needle.size(); offset++) {
                if (!haystack.get(start + offset).equals(needle.get(offset))) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    // ── the relation extraction ───────────────────────────────────────────────

    /** A concept name found in a sentence. */
    private record NameHit(String conceptId, String conceptName, int start, int end) {
    }

    /** The relation candidates of one sentence. */
    private static void relations(Map<String, Concept> conceptsById,
            Map<String, List<Concept>> conceptsByFirstWord, Sentence sentence,
            List<RelationCandidate> accepted, List<Rejection> rejections, Set<String> pairTypes,
            Map<String, Set<String>> related) {
        String text = sentence.text();
        List<String> sentenceWords = words(text);
        if (sentenceWords.isEmpty()) {
            return;
        }
        int[] starts = new int[sentenceWords.size()];
        int[] ends = new int[sentenceWords.size()];
        wordOffsets(text, starts, ends);

        List<NameHit> hits = nameHits(conceptsByFirstWord, sentenceWords, starts, ends);
        if (hits.size() < 2) {
            return;
        }

        for (int index = 0; index < sentenceWords.size(); index++) {
            List<Trigger> candidates = TRIGGERS_BY_FIRST_WORD.get(sentenceWords.get(index));
            if (candidates == null) {
                continue;
            }
            Trigger trigger = null;
            boolean ambiguous = false;
            for (Trigger candidate : candidates) {
                if (!matches(sentenceWords, index, candidate.words())) {
                    continue;
                }
                if (trigger == null || candidate.words().size() > trigger.words().size()) {
                    trigger = candidate;
                    ambiguous = false;
                } else if (candidate.words().size() == trigger.words().size()
                        && candidate.type() != trigger.type()) {
                    ambiguous = true;
                }
            }
            if (trigger == null) {
                continue;
            }
            int triggerStart = starts[index];
            int triggerEnd = ends[index + trigger.words().size() - 1];
            String phrase = String.join(" ", trigger.words());
            if (ambiguous) {
                rejections.add(new Rejection("ambiguous trigger phrase",
                        sentence.claim().introducedBySourceId(), fileOf(sentence), text, phrase,
                        "two relation types match the same phrase"));
                continue;
            }
            if (trigger.type() == RelationType.PRODUCES && index > 0
                    && PASSIVE_AUXILIARIES.contains(sentenceWords.get(index - 1))) {
                rejections.add(new Rejection("passive voice",
                        sentence.claim().introducedBySourceId(), fileOf(sentence), text, phrase,
                        "the trigger follows the passive auxiliary \""
                                + sentenceWords.get(index - 1) + "\""));
                continue;
            }
            NameHit from = null;
            NameHit to = null;
            for (NameHit hit : hits) {
                if (hit.end() <= triggerStart && (from == null || hit.end() > from.end())) {
                    from = hit;
                }
                if (hit.start() >= triggerEnd && (to == null || hit.start() < to.start())) {
                    to = hit;
                }
            }
            if (from == null) {
                rejections.add(new Rejection("no concept before the trigger",
                        sentence.claim().introducedBySourceId(), fileOf(sentence), text, phrase,
                        "nothing that names a concept precedes the trigger phrase"));
                continue;
            }
            if (to == null) {
                rejections.add(new Rejection("no concept after the trigger",
                        sentence.claim().introducedBySourceId(), fileOf(sentence), text, phrase,
                        "nothing that names a concept follows the trigger phrase"));
                continue;
            }
            if (from.conceptId().equals(to.conceptId())) {
                rejections.add(new Rejection("the same concept on both sides",
                        sentence.claim().introducedBySourceId(), fileOf(sentence), text, phrase,
                        "the concept before and the concept after the trigger are the same"));
                continue;
            }
            if (!eligible(from.conceptName()) || !eligible(to.conceptName())) {
                rejections.add(new Rejection("the concept name is a single function word",
                        sentence.claim().introducedBySourceId(), fileOf(sentence), text, phrase,
                        "a one-word function word may not carry a relation"));
                continue;
            }
            String pair = from.conceptId() + ">" + to.conceptId();
            if (pairTypes.contains(pair)) {
                rejections.add(new Rejection(
                        "a relation of another type already exists for this pair",
                        sentence.claim().introducedBySourceId(), fileOf(sentence), text, phrase,
                        "the ordered pair already carries a candidate of another type"));
                continue;
            }
            pairTypes.add(pair);
            boolean supported = EpistemicPolicy.isCitable(sentence.claim().status());
            accepted.add(new RelationCandidate(from.conceptId(), from.conceptName(),
                    to.conceptId(), to.conceptName(), trigger.type(),
                    from.conceptName() + " --" + trigger.type() + "--> " + to.conceptName(),
                    sentence.claim().introducedBySourceId(), fileOf(sentence),
                    sentence.document() == null ? "" : sentence.document().sha256(),
                    sentence.start(), sentence.end(), OFFSET_UNIT, sentence.precision(), text,
                    RELATION_METHOD,
                    supported ? EvidentiaryStatus.SUPPORTED : EvidentiaryStatus.CANDIDATE,
                    sentence.claim().id()));
            related.computeIfAbsent(from.conceptId(), key -> new LinkedHashSet<>())
                    .add(to.conceptId());
            related.computeIfAbsent(to.conceptId(), key -> new LinkedHashSet<>())
                    .add(from.conceptId());
        }
    }

    /** The concepts of the workspace, indexed by the first word of their name. */
    private static Map<String, List<Concept>> indexConcepts(Map<String, Concept> conceptsById) {
        Map<String, List<Concept>> index = new LinkedHashMap<>();
        for (Concept concept : conceptsById.values()) {
            List<String> nameWords = words(concept.name());
            if (nameWords.isEmpty()) {
                continue;
            }
            index.computeIfAbsent(nameWords.get(0), key -> new ArrayList<>()).add(concept);
        }
        return index;
    }

    /** The name hits of a sentence: the concept names it holds, with their places. */
    private static List<NameHit> nameHits(Map<String, List<Concept>> conceptsByFirstWord,
            List<String> sentenceWords, int[] starts, int[] ends) {
        List<NameHit> hits = new ArrayList<>();
        for (int index = 0; index < sentenceWords.size(); index++) {
            List<Concept> candidates = conceptsByFirstWord.get(sentenceWords.get(index));
            if (candidates == null) {
                continue;
            }
            for (Concept concept : candidates) {
                List<String> nameWords = words(concept.name());
                if (nameWords.size() > sentenceWords.size() - index) {
                    continue;
                }
                boolean matched = true;
                for (int offset = 0; offset < nameWords.size(); offset++) {
                    if (!sentenceWords.get(index + offset).equals(nameWords.get(offset))) {
                        matched = false;
                        break;
                    }
                }
                if (!matched) {
                    continue;
                }
                int last = index + nameWords.size() - 1;
                hits.add(new NameHit(concept.id(), concept.name(), starts[index], ends[last]));
            }
        }
        return hits;
    }

    /** Whether a concept name may take part in a relation. */
    static boolean eligible(String name) {
        List<String> nameWords = words(name);
        if (nameWords.size() >= 2) {
            return true;
        }
        String only = nameWords.isEmpty() ? "" : nameWords.get(0);
        return only.length() >= 4 && !FUNCTION_WORDS.contains(only);
    }

    /** The file of the sentence's document. */
    private static String fileOf(Sentence sentence) {
        return sentence.document() == null ? "" : sentence.document().file();
    }

    // ── text helpers ──────────────────────────────────────────────────────────

    /** The words of a text, lower-cased. */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        int index = 0;
        int length = text.length();
        while (index < length) {
            char letter = text.charAt(index);
            if (Character.isLetterOrDigit(letter)) {
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

    /** The character offsets of the words of a text. */
    private static void wordOffsets(String text, int[] starts, int[] ends) {
        int index = 0;
        int length = text.length();
        int word = 0;
        while (index < length && word < starts.length) {
            char letter = text.charAt(index);
            if (Character.isLetterOrDigit(letter)) {
                int end = index;
                while (end < length && Character.isLetterOrDigit(text.charAt(end))) {
                    end++;
                }
                starts[word] = index;
                ends[word] = end;
                word++;
                index = end;
            } else {
                index++;
            }
        }
    }

    /** Whether the words at an index match a phrase. */
    private static boolean matches(List<String> words, int index, List<String> phrase) {
        if (index + phrase.size() > words.size()) {
            return false;
        }
        for (int offset = 0; offset < phrase.size(); offset++) {
            if (!words.get(index + offset).equals(phrase.get(offset))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The offsets of a sentence inside the extracted text.
     *
     * <p>The sentence is looked for inside the span of its segment. When it cannot be found
     * there — the extractor joined the lines and normalised the whitespace — the segment's own
     * span is used, and the offset is the segment's.</p>
     */
    private static int[] locate(String text, String proposition, int start, int end) {
        int from = Math.max(0, Math.min(start, text.length()));
        int to = Math.max(from, Math.min(end, text.length()));
        if (proposition != null && !proposition.isEmpty()) {
            int at = text.indexOf(proposition, from);
            if (at >= 0 && at + proposition.length() <= to) {
                return new int[] {at, at + proposition.length(), 1};
            }
            String trimmed = proposition.strip();
            at = text.indexOf(trimmed, from);
            if (at >= 0 && at + trimmed.length() <= to) {
                return new int[] {at, at + trimmed.length(), 1};
            }
        }
        // THE EXTRACTOR JOINED THE LINES: the sentence is not a literal substring of the
        // extracted text, and the span of its SEGMENT is what can be located exactly.
        return new int[] {from, to, 0};
    }

    /** The size of the retained extraction, measured on its text. */
    private static Extraction withRetainedSize(Extraction extraction) {
        long bytes = 0L;
        for (DefinitionCandidate definition : extraction.definitions()) {
            bytes += definition.definition() == null ? 0 : definition.definition().length();
            bytes += definition.conceptName() == null ? 0 : definition.conceptName().length();
        }
        for (RelationCandidate relation : extraction.relations()) {
            bytes += relation.span() == null ? 0 : relation.span().length();
            bytes += relation.fromConceptName() == null ? 0 : relation.fromConceptName().length();
            bytes += relation.toConceptName() == null ? 0 : relation.toConceptName().length();
        }
        for (Rejection rejection : extraction.rejections()) {
            bytes += rejection.span() == null ? 0 : rejection.span().length();
        }
        return new Extraction(extraction.definitions(), extraction.relations(), extraction.rejections(),
                extraction.conceptsWithoutDefinition(), extraction.relatedByConcept(),
                extraction.conceptCount(), extraction.documentCount(), extraction.sentenceCount(),
                extraction.extractionMillis(), bytes);
    }

    private SourceGroundedEnrichment() {
    }

    /** The trigger phrases of every relation type, for the report. */
    public static Map<RelationType, List<String>> triggerPhrases() {
        Map<RelationType, List<String>> copy = new LinkedHashMap<>();
        for (RelationType type : RelationType.values()) {
            copy.put(type, List.copyOf(TRIGGERS.get(type)));
        }
        return copy;
    }

    /** The words that make a PRODUCES trigger passive. */
    public static Set<String> passiveAuxiliaries() {
        return Set.copyOf(PASSIVE_AUXILIARIES);
    }

    /** The one-word concept names that may not carry a relation. */
    public static Set<String> functionWords() {
        return Set.copyOf(FUNCTION_WORDS);
    }
}
