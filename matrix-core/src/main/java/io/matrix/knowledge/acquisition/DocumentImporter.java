package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.Ids;
import io.matrix.knowledge.KnowledgeChanges;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Imports a document into the knowledge store (RECON-W42.2).
 *
 * <p>The mission's §5.3 names the pipeline:
 * `SOURCE → EXTRACTION → NORMALIZATION → SEGMENTATION → CONCEPT DISCOVERY →
 * CLAIM EXTRACTION → EVIDENCE LINKING → VALIDATION → KNOWLEDGE INTEGRATION`.
 * This class runs it, and it runs it into the W41 store — the store that
 * already holds the provenance, the revisions and the atomic imports.
 * **No second knowledge store is created.**
 *
 * <p><b>What lands where.</b> The document becomes a `Source` with its
 * content hash. Each proposed claim becomes a `Claim` in the CANDIDATE
 * status, with an `Evidence` record pointing at the source and carrying the
 * excerpt and the location. The claim's concept is created if the workspace
 * does not have it.
 *
 * <p><b>The rule that makes the import honest.</b> Two files with the same
 * content hash are the same document. The source id is derived from the
 * hash, so importing a copy of a document does not create a second source
 * and does not make a second "independent confirmation" — which is the
 * mission's §5.4 rule, enforced by construction rather than by a count
 * afterwards.
 */
public final class DocumentImporter {

    /** The bytes a document may be. */
    public static final long MAXIMUM_DOCUMENT_BYTES = 32L * 1024 * 1024;

    /**
     * How much of a source's text the stored excerpt keeps — as a FLOOR.
     *
     * <p><b>Why it is a floor and not a ceiling.</b> The excerpt is the source's text, and it
     * is what a reader — and the learner's containment gate — checks a claim's proposition
     * against. A claim whose span lies <b>past</b> the excerpt is therefore a claim the
     * workspace <b>cannot show</b>: it is asserted, and the evidence for it is not there.
     * So the excerpt must cover every claim the workspace asserts, and this constant is the
     * minimum it keeps when the document's claims lie inside it.</p>
     *
     * <p>The measurement that forced the change: over the research pool, <b>483</b> of the
     * <b>540</b> claims are genuine verbatim spans of their document's text, and the 8 000
     * character cut hid <b>213</b> of them — <b>44 %</b> of the corpus's real spans — so
     * the learner refused claims it could not see and called them paraphrases, which they
     * are not.</p>
     */
    public static final int EXCERPT_LIMIT = 8000;

    private final KnowledgeStore store;

    /**
     * The clock the import stamps its records with.
     *
     * <p>The stamp is not decoration: it becomes the {@code createdAt} of the
     * representations the import writes, and {@code Representations.activeAt}
     * picks the active account of a level by comparing those stamps. A wall
     * clock read there would decide WHICH account the system believes, so
     * the instant is injected (Article I) and the default is the system
     * clock.
     */
    private final Clock clock;

    public DocumentImporter(KnowledgeStore store) {
        this(store, Clock.systemUTC());
    }

    /**
     * The importer over a store, stamping its records with a chosen clock.
     *
     * @param store the store to import into
     * @param clock the instant the import stamps its records with; the system
     *     clock when null
     */
    public DocumentImporter(KnowledgeStore store, Clock clock) {
        this.store = store;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** What an import did. */
    public record ImportResult(
            String workspaceId,
            SourceRecord source,
            int segments,
            int claimsProposed,
            int claimsAdded,
            int conceptsCreated,
            int representationsCreated,
            List<String> skipped,
            ClaimValidator.Result validation,
            /**
             * EPISTEMIC-INTEGRITY P1 — how many concept definitions this import
             * wrote from the source spans (see {@link SourceSpanDefinitions}).
             */
            int definitionsWritten) {

        public ImportResult {
            skipped = List.copyOf(skipped == null ? List.of() : skipped);
        }
    }

    /**
     * Imports a document.
     *
     * @param workspaceId the workspace to import into
     * @param path the file
     * @param declaredLanguage the language of the document, when the caller
     *     knows it; null means the pipeline guesses it
     * @return what the import did
     * @throws IOException when the document cannot be read
     */
    public ImportResult importDocument(String workspaceId, Path path, String declaredLanguage)
            throws IOException {
        return importDocument(workspaceId, path, path.getFileName().toString(), declaredLanguage);
    }

    /**
     * Imports a document under a name of the caller's choosing.
     *
     * <p>The name is what the source is recorded under, and it is what the
     * format is detected from. A caller that reads the document from a
     * temporary file passes the name the user gave — the temporary name
     * would be a lie about where the document came from.
     *
     * @param workspaceId the workspace to import into
     * @param path the file to read
     * @param filename the name to record
     * @param declaredLanguage the language, when the caller knows it
     * @return what the import did
     * @throws IOException when the document cannot be read
     */
    public ImportResult importDocument(String workspaceId, Path path, String filename,
                                       String declaredLanguage) throws IOException {
        DocumentFormat format = DocumentFormat.of(filename);
        if (format == null) {
            throw new IOException("the format of " + path.getFileName() + " is not one this "
                    + "pipeline reads");
        }
        if (format == DocumentFormat.JSON) {
            throw new IOException("a knowledge package is imported through the package importer, "
                    + "not through the document pipeline");
        }
        long size = Files.size(path);
        if (size > MAXIMUM_DOCUMENT_BYTES) {
            throw new IOException("the document is " + size + " bytes, which is over the limit of "
                    + MAXIMUM_DOCUMENT_BYTES);
        }
        byte[] content = Files.readAllBytes(path);
        String hash = sha256(content);

        SourceRecord.Builder source = new SourceRecord.Builder(
                sourceId(workspaceId, hash), filename, hash, format)
                .importedAt(clock.instant().toString())
                .processed("read " + size + " bytes")
                // W51 §6 — THE EXTENT OF THE SOURCE IS SAID EXPLICITLY.
                // The source IS the whole document, and the claim's own
                // span says where in it the sentence sat. The locator used
                // to be the bare constant "the whole document", which said
                // the same thing and named nothing.
                .location(filename + " (" + SourceRecord.WHOLE_DOCUMENT + ")");

        ExtractedDocument document = new DocumentExtractorRegistry().extract(format, content,
                filename, source);
        if (declaredLanguage != null && !declaredLanguage.isBlank()) {
            // The declaration wins: the caller has said what the document
            // is, and the registry reports what the workspace was told.
            source.language(declaredLanguage);
        } else {
            // Otherwise the language is DETECTED from the extracted text.
            // The importer used to leave it "unknown", so the corpus
            // registry could not say what language a source was written in
            // - and the mission's §5.5 asks for the structure of the
            // source.
            String detected = io.matrix.knowledge.TextProfiles.detect(document.text());
            if (detected != null && !detected.isBlank() && !"unknown".equals(detected)) {
                source.language(detected);
            }
        }
        SourceRecord record = source.build();

        // The source. The store already knows the content hash, which is
        // what makes the independence rule computable.
        // The kind is HUMAN_ASSERTION: a document is an explicitly identified
        // human statement. The importer cannot know whether the document is
        // authoritative, and it does not pretend to - the authority and the
        // freshness are middling, and the promotion rules are what decide
        // what the document is worth.
        // THE EXCERPT IS THE TEXT OF THE SOURCE.
        //
        // The excerpt used to hold the processing note ("read 17144 bytes"),
        // which says nothing about what the source says and cannot be
        // shown as evidence. The source's own text is what a reader needs
        // to check a claim against, so that is what the excerpt carries.
        String text = document.text().strip();
        // THE EXCERPT MUST COVER EVERY CLAIM THE WORKSPACE ASSERTS.
        //
        // The proposals are computed before the source is written, so that the excerpt can
        // be sized to the claims: a claim whose span lies past the excerpt is a claim the
        // workspace cannot show, and the containment gate would refuse it with a reason
        // that is false - it is a span of the document, just not of the text the workspace
        // kept. The span's offsets are in CODE POINTS of the extracted text, so the cut is
        // measured in the same unit.
        List<ClaimExtractor.Proposal> proposals = ClaimExtractor.propose(document);
        int excerptLimit = EXCERPT_LIMIT;
        for (ClaimExtractor.Proposal proposal : proposals) {
            io.matrix.contract.Provenance.Span span = proposal.span();
            if (span == null || span.end() == null) {
                continue;
            }
            long end = span.end();
            int codePoints = text.codePointCount(0, (int) Math.min(end, text.length()));
            excerptLimit = Math.max(excerptLimit, codePoints);
        }
        String excerpt = text.length() > excerptLimit ? text.substring(0, excerptLimit) : text;
        Source storeSource = new Source(record.sourceId(), workspaceId, SourceKind.HUMAN_ASSERTION,
                record.filename(), record.location(), 50, 50, excerpt,
                record.importedAt(), record.contentHash(), record.publicationDate(),
                record.language(), record.format().name().toLowerCase(java.util.Locale.ROOT));

        List<Claim> claims = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        Set<String> conceptIds = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        Set<String> conceptsBefore = new LinkedHashSet<>();
        for (Concept concept : store.concepts(workspaceId)) {
            conceptsBefore.add(concept.id());
        }

        // THE CONCEPTS ARE BUILT, NOT WRITTEN.
        //
        // The concepts were created by a write of their own, before the
        // transaction of the claims began, so a failure in between left
        // the workspace holding concepts with no claims — a half-imported
        // document. The mission's §14 asks for an ATOMIC import, and the
        // concepts now go in with the claims, in one transaction.
        java.util.Map<String, Concept> conceptsById = new java.util.LinkedHashMap<>();
        for (Concept existing : store.concepts(workspaceId)) {
            conceptsById.put(existing.id(), existing);
        }
        for (ClaimExtractor.Proposal proposal : proposals) {
            Concept concept = conceptsById.get(conceptId(workspaceId, proposal.subject()));
            if (concept == null) {
                concept = new Concept(conceptId(workspaceId, proposal.subject()), workspaceId,
                        proposal.subject().strip(), null, null, record.importedAt());
                conceptsById.put(concept.id(), concept);
            }
            conceptIds.add(concept.id());
            String claimId = claimId(workspaceId, record.contentHash(), proposal.proposition());
            claims.add(new Claim(claimId, workspaceId, concept.id(), proposal.proposition(),
                    proposal.subject(),
                    // W51 §6 — THE CLAIM CARRIES ITS SPAN. The context is
                    // the place the claim came from, and the place is now
                    // exact: the file, the heading, the line and the
                    // characters of the extracted text the sentence covers.
                    io.matrix.knowledge.ClaimSpan.format(proposal.location(), proposal.span()),
                    ClaimStatus.CANDIDATE, 0.0, 1, record.sourceId(),
                    "document extraction", "not validated", null, null,
                    record.importedAt(), record.importedAt()));
            evidence.add(new Evidence(Ids.digest(claimId + "|evidence|" + record.sourceId()),
                    workspaceId, claimId, record.sourceId(), EvidenceRelation.SUPPORTS,
                    proposal.proposition(), qualityOf(document), record.importedAt()));
        }

        // The concepts that were created by this import, for the report.
        // The count is taken BEFORE the import creates them, because after
        // it they all exist - and a report that always says zero is a
        // report nobody can use.
        int created = 0;
        for (String conceptId : conceptIds) {
            if (!conceptsBefore.contains(conceptId)) {
                created++;
            }
        }

        // THE REPRESENTATIONS.
        //
        // A concept that came from a document had claims and evidence but
        // no authored account, so the system's answer about it was the
        // claim itself - which is not an explanation. The composer writes
        // the account the document already contains, at the levels the
        // document's own sentences support, and at no others.
        List<io.matrix.knowledge.Representation> representations = new ArrayList<>();
        java.util.Map<String, List<Claim>> claimsByConcept = new java.util.LinkedHashMap<>();
        for (Claim claim : claims) {
            claimsByConcept.computeIfAbsent(claim.conceptId(), key -> new ArrayList<>())
                    .add(claim);
        }
        List<String> conceptNames = new ArrayList<>();
        for (Concept concept : conceptsById.values()) {
            conceptNames.add(concept.name());
        }
        for (Concept concept : conceptsById.values()) {
            List<Claim> conceptClaims = claimsByConcept.get(concept.id());
            if (conceptClaims == null || conceptClaims.isEmpty()) {
                continue;
            }
            representations.addAll(io.matrix.knowledge.RepresentationComposer.compose(
                    concept, conceptClaims, conceptNames, workspaceId, record.sourceId(),
                    record.importedAt()));
        }

        store.applyChanges(workspaceId, new KnowledgeChanges(List.of(storeSource), claims,
                evidence, representations, List.of()),
                new ArrayList<>(conceptsById.values()));

        // EPISTEMIC-INTEGRITY P1 — THE DEFINITION IS DERIVED FROM THE SOURCE SPAN.
        //
        // The concept layer had 348 claims, 326 concepts and 0 usable definitions:
        // the importer wrote every concept with `definition = null`, because
        // nothing in the pipeline derived one. The definition is now the
        // qualifying sentence of the concept's OWN source document, verbatim,
        // under the frozen tight v1.1 rule (the name must be IMMEDIATELY
        // followed by a definitional copula).
        //
        // The derivation runs over the WORKSPACE, not over this document alone:
        // a concept's definition may be found in a source it was imported with
        // earlier, and the rule takes the first qualifying sentence in the
        // order the snapshot lists the sources. Only the concepts this import
        // TOUCHED are re-derived, and that is exact rather than an economy: a
        // source becomes one of a concept's own sources only through a claim
        // of that concept, so an untouched concept's rule cannot change.
        int definitionsWritten = writeDefinitions(workspaceId, claims, document.text());

        // THE VALIDATION STAGE OF THE PIPELINE.
        //
        // The mission's §5.4 names the pipeline: SOURCE -> EXTRACTION ->
        // NORMALIZATION -> SEGMENTATION -> CONCEPT DISCOVERY -> CLAIM
        // EXTRACTION -> EVIDENCE LINKING -> VALIDATION -> KNOWLEDGE
        // INTEGRATION. The validation is the stage that decides what the
        // extracted claims are worth, and it was not part of the import: a
        // document that stated what the workspace already held left the
        // workspace with two claims and no promotion, and the claim the
        // document contributed stayed CANDIDATE for ever.
        //
        // The validation runs here, on the workspace as it now stands -
        // the new document's claims and evidence included - so the group
        // of claims that state the same thing is measured with the new
        // source in it.
        ClaimValidator.Result validation = ClaimValidator.validate(store, workspaceId);

        return new ImportResult(workspaceId, record, document.segments().size(),
                claims.size(), claims.size(), created, representations.size(), skipped,
                validation, definitionsWritten);
    }

    /**
     * Derives and writes the definitions of the concepts this import touched.
     *
     * <p>The rule is {@link SourceSpanDefinitions}: the first sentence of the
     * concept's own source document that holds the concept's name immediately
     * followed by a definitional copula, at most
     * {@link SourceSpanDefinitions#MAX_DEFINITION_LENGTH} characters long. The
     * definition is that sentence verbatim; a concept with no such sentence
     * keeps no definition, which is an honest state.</p>
     *
     * <p>A definition that is already stored is not written again: the import
     * is idempotent in what it writes.</p>
     *
     * @param workspaceId the workspace
     * @param imported the claims this import wrote
     * @param extractedText the extracted text of the document, which says where
     *     each of its sentences sits
     * @return how many definitions were written
     */
    private int writeDefinitions(String workspaceId, List<Claim> imported, String extractedText) {
        Set<String> touched = new LinkedHashSet<>();
        for (Claim claim : imported) {
            touched.add(claim.conceptId());
        }
        if (touched.isEmpty()) {
            return 0;
        }
        List<Concept> concepts = store.concepts(workspaceId);
        List<Concept> selected = new ArrayList<>();
        for (Concept concept : concepts) {
            if (touched.contains(concept.id())) {
                selected.add(concept);
            }
        }
        if (selected.isEmpty()) {
            return 0;
        }
        // The sentences of the workspace. The text of THIS document is in hand, so
        // its sentences are located exactly; the sentences of a source imported
        // earlier keep the span of their segment.
        java.util.Map<String, SourceSpanDefinitions.Sentence> byClaimId =
                new java.util.LinkedHashMap<>();
        for (SourceSpanDefinitions.Sentence sentence
                : SourceSpanDefinitions.sentencesOf(store.claims(workspaceId), null)) {
            byClaimId.put(sentence.claim().id(), sentence);
        }
        for (SourceSpanDefinitions.Sentence sentence
                : SourceSpanDefinitions.sentencesOf(imported, extractedText)) {
            byClaimId.put(sentence.claim().id(), sentence);
        }
        List<SourceSpanDefinitions.Sentence> sentences = new ArrayList<>(byClaimId.values());
        List<SourceSpanDefinitions.Definition> definitions = SourceSpanDefinitions.derive(
                selected, store.sources(workspaceId), sentences);
        // The definitions that are already recorded, so that a definition which
        // has not changed is not written again.
        java.util.Map<String, io.matrix.knowledge.ConceptDefinition> recorded =
                new java.util.LinkedHashMap<>();
        for (io.matrix.knowledge.ConceptDefinition definition
                : store.conceptDefinitions(workspaceId)) {
            recorded.put(definition.conceptId(), definition);
        }
        int written = 0;
        for (SourceSpanDefinitions.Definition definition : definitions) {
            io.matrix.knowledge.ConceptDefinition existing = recorded.get(definition.conceptId());
            if (existing != null && existing.definition().equals(definition.text())) {
                continue;
            }
            String claimId = null;
            for (SourceSpanDefinitions.Sentence sentence : sentences) {
                if (sentence.claim().conceptId().equals(definition.conceptId())
                        && sentence.text().equals(definition.text())) {
                    claimId = sentence.claim().id();
                    break;
                }
            }
            store.saveConceptDefinition(new io.matrix.knowledge.ConceptDefinition(workspaceId,
                    definition.conceptId(), definition.text(), definition.sourceId(),
                    definition.startOffset(), definition.endOffset(), claimId,
                    definition.method(), clock.instant().toString()));
            written++;
        }
        return written;
    }

    /** The quality of the evidence: the extraction's own quality. */
    private int qualityOf(ExtractedDocument document) {
        return (int) Math.round(document.source().extractionQuality() * 100);
    }

    /**
     * The source id for a document.
     *
     * <p>Derived from the content hash, so that the same document — under
     * any name, in any file — is one source.
     */
    public static String sourceId(String workspaceId, String contentHash) {
        return "src-" + Ids.digest(workspaceId + "|document|" + contentHash);
    }

    /** The claim id for a proposition from a document. */
    /**
     * The id of a concept of a workspace.
     *
     * <p>The id is a pure function of the workspace and the name, so it
     * can be computed before the concept is written.
     *
     * @param workspaceId the workspace
     * @param name the concept's name
     * @return the id
     */
    static String conceptId(String workspaceId, String name) {
        return io.matrix.knowledge.Ids.digest(workspaceId + "|concept|"
                + name.strip().toLowerCase(java.util.Locale.ROOT));
    }

    public static String claimId(String workspaceId, String contentHash, String proposition) {
        return "clm-" + Ids.digest(workspaceId + "|" + contentHash + "|"
                + proposition.strip().toLowerCase(java.util.Locale.ROOT));
    }

    /** The SHA-256 of the document's bytes, in lower-case hex. */
    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }

    /** The independent-source count behind a set of sources, by their content hashes. */
    public static int independentSources(List<Source> sources) {
        List<String> hashes = new ArrayList<>();
        for (Source source : sources) {
            hashes.add(source.contentHash());
        }
        return PromotionPolicy.independentSources(hashes);
    }

    /** Reads a stream into memory, for a caller that has one. */
    static byte[] readAll(InputStream input) throws IOException {
        return input.readAllBytes();
    }
}
