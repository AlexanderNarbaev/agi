package io.matrix.knowledge.acquisition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Chooses the extractor for a format (RECON-W42.2).
 *
 * <p>One place decides which library reads which format, so that the
 * choice is visible rather than spread over the callers.
 */
public final class DocumentExtractorRegistry {

    private final TextDocumentExtractor text = new TextDocumentExtractor(DocumentFormat.TXT);
    private final TextDocumentExtractor markdown = new TextDocumentExtractor(DocumentFormat.MARKDOWN);
    private final HtmlDocumentExtractor html = new HtmlDocumentExtractor();
    private final PdfDocumentExtractor pdf = new PdfDocumentExtractor();
    private final EpubDocumentExtractor epub = new EpubDocumentExtractor();

    /** The extractor for a format. */
    public DocumentExtractor extractor(DocumentFormat format) {
        return switch (format) {
            case TXT -> text;
            case MARKDOWN -> markdown;
            case HTML -> html;
            case PDF -> pdf;
            case EPUB -> epub;
            case JSON -> throw new IllegalArgumentException(
                    "a knowledge package is not read by the document pipeline");
        };
    }

    /**
     * Reads a document from its bytes.
     *
     * @param format the format
     * @param content the bytes
     * @param filename the name, for the messages
     * @param source the source record being built
     * @return the extracted document
     */
    public ExtractedDocument extract(DocumentFormat format, byte[] content, String filename,
                                     SourceRecord.Builder source) {
        try {
            return extractor(format).extract(new ByteArrayInputStream(content), filename, source);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + filename, e);
        }
    }
}
