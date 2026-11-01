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
import java.time.Instant;
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

    public DocumentImporter(KnowledgeStore store) {
        this.store = store;
    }

    /** What an import did. */
    public record ImportResult(
            String workspaceId,
            SourceRecord source,
            int segments,
            int claimsProposed,
            int claimsAdded,
            int conceptsCreated,
            List<String> skipped) {

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
                .importedAt(Instant.now().toString())
                .processed("read " + size + " bytes");

        ExtractedDocument document = new DocumentExtractorRegistry().extract(format, content,
                filename, source);
        if (declaredLanguage != null && !declaredLanguage.isBlank()) {
            source.language(declaredLanguage);
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
                record.importedAt(), record.contentHash(), record.publicationDate());

        List<Claim> claims = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        Set<String> conceptIds = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        Set<String> conceptsBefore = new LinkedHashSet<>();
        for (Concept concept : store.concepts(workspaceId)) {
            conceptsBefore.add(concept.id());
        }

        for (ClaimExtractor.Proposal proposal : ClaimExtractor.propose(document)) {
            Concept concept = store.ensureConcept(workspaceId, proposal.subject(), null, null);
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

        store.applyChanges(workspaceId, new KnowledgeChanges(List.of(storeSource), claims,
                evidence, List.of(), List.of()));

        return new ImportResult(workspaceId, record, document.segments().size(),
                claims.size(), claims.size(), created, skipped);
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
