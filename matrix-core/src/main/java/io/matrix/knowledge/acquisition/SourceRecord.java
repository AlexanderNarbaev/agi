package io.matrix.knowledge.acquisition;

import java.util.ArrayList;
import java.util.List;

/**
 * The record of one acquired source (RECON-W42.2).
 *
 * <p>The mission's §5.2: "For every imported source, the system stores:
 * filename, source identifier, content hash, format, language, author (if
 * known), publication date (if known), import date, location within the
 * file, extracted structure, page references, extraction quality, and
 * processing history."
 *
 * <p><b>What is never invented.</b> The author and the publication date are
 * absent unless the document itself says so. A source whose author is
 * unknown says "unknown" — a guessed attribution is a false claim about a
 * document, and a false claim about a document becomes a false claim in
 * the knowledge store.
 *
 * <p><b>The content hash is the identity.</b> Two files with the same hash
 * are the same document, however they were named — which is what makes the
 * mission's rule enforceable: "ten documents copying the same source do not
 * constitute ten independent confirmations."
 */
public record SourceRecord(
        String sourceId,
        String filename,
        String contentHash,
        DocumentFormat format,
        String language,
        String author,
        String publicationDate,
        String importedAt,
        String location,
        String extractedStructure,
        List<String> pageReferences,
        double extractionQuality,
        List<String> processingHistory) {

    public SourceRecord {
        pageReferences = List.copyOf(pageReferences == null ? List.of() : pageReferences);
        processingHistory = List.copyOf(
                processingHistory == null ? List.of() : processingHistory);
    }

    /** The value used when the document does not say who wrote it. */
    public static final String UNKNOWN = "unknown";

    /**
     * The builder for a source record.
     *
     * <p>Every field that the document does not state is left absent, and
     * the builder says so.
     */
    public static final class Builder {
        private final String sourceId;
        private final String filename;
        private final String contentHash;
        private final DocumentFormat format;
        private String language = UNKNOWN;
        private String author = UNKNOWN;
        private String publicationDate = UNKNOWN;
        private String importedAt;
        private String location = "the whole document";
        private String extractedStructure = "plain text";
        private final List<String> pageReferences = new ArrayList<>();
        private double extractionQuality;
        private final List<String> processingHistory = new ArrayList<>();

        public Builder(String sourceId, String filename, String contentHash,
                       DocumentFormat format) {
            this.sourceId = sourceId;
            this.filename = filename;
            this.contentHash = contentHash;
            this.format = format;
        }

        public Builder language(String language) {
            this.language = language == null ? UNKNOWN : language;
            return this;
        }

        public Builder author(String author) {
            this.author = author == null ? UNKNOWN : author;
            return this;
        }

        public Builder publicationDate(String publicationDate) {
            this.publicationDate = publicationDate == null ? UNKNOWN : publicationDate;
            return this;
        }

        public Builder importedAt(String importedAt) {
            this.importedAt = importedAt;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder extractedStructure(String extractedStructure) {
            this.extractedStructure = extractedStructure;
            return this;
        }

        public Builder pageReference(String pageReference) {
            this.pageReferences.add(pageReference);
            return this;
        }

        public Builder extractionQuality(double extractionQuality) {
            this.extractionQuality = extractionQuality;
            return this;
        }

        public Builder processed(String step) {
            this.processingHistory.add(step);
            return this;
        }

        public SourceRecord build() {
            return new SourceRecord(sourceId, filename, contentHash, format, language,
                    author, publicationDate, importedAt, location, extractedStructure,
                    pageReferences, extractionQuality, processingHistory);
        }
    }
}
