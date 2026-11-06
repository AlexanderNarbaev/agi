package io.matrix.knowledge.acquisition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Reads one document format into text (RECON-W42.2).
 *
 * <p>The mission's §13.2 lists the attacks an importer must survive:
 * malformed documents, zip bombs, oversized files, malicious HTML, prompt
 * injection in imported documents, path traversal, untrusted links,
 * corrupted packages. Every extractor therefore works on a stream with a
 * bound, and never follows a link out of the document.
 */
public interface DocumentExtractor {

    /** The format this extractor reads. */
    DocumentFormat format();

    /**
     * Reads a document.
     *
     * @param path the file to read
     * @param source the source record being built
     * @return the extracted document
     * @throws IOException when the document cannot be read
     */
    ExtractedDocument extract(Path path, SourceRecord.Builder source) throws IOException;

    /**
     * Reads a document from a stream.
     *
     * @param input the stream to read
     * @param filename the name, for the format's detection
     * @param source the source record being built
     * @return the extracted document
     * @throws IOException when the document cannot be read
     */
    ExtractedDocument extract(InputStream input, String filename, SourceRecord.Builder source)
            throws IOException;
}
