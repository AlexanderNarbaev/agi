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
            ClaimValidator.Result validation) {

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
                .processed("read " + size + " bytes");

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
        Source storeSource = new Source(record.sourceId(), workspaceId, SourceKind.HUMAN_ASSERTION,
                record.filename(), record.location(), 50, 50,
                record.processingHistory().isEmpty() ? "" : record.processingHistory().get(0),
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
        for (ClaimExtractor.Proposal proposal : ClaimExtractor.propose(document)) {
            Concept concept = conceptsById.get(conceptId(workspaceId, proposal.subject()));
            if (concept == null) {
                concept = new Concept(conceptId(workspaceId, proposal.subject()), workspaceId,
                        proposal.subject().strip(), null, null, record.importedAt());
                conceptsById.put(concept.id(), concept);
            }
            conceptIds.add(concept.id());
            String claimId = claimId(workspaceId, record.contentHash(), proposal.proposition());
            claims.add(new Claim(claimId, workspaceId, concept.id(), proposal.proposition(),
                    proposal.subject(), proposal.location(),
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
                validation);
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
