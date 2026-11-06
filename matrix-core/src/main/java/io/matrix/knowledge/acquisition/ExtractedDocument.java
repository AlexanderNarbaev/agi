package io.matrix.knowledge.acquisition;

import java.util.List;

/**
 * The result of reading a document (RECON-W42.2).
 *
 * <p>The mission's §5.3 names the pipeline:
 * `SOURCE → EXTRACTION → NORMALIZATION → SEGMENTATION → CONCEPT DISCOVERY →
 * CLAIM EXTRACTION → EVIDENCE LINKING → VALIDATION → KNOWLEDGE INTEGRATION`.
 * This record is the state of the document after EXTRACTION and
 * NORMALIZATION, and it carries the segments that SEGMENTATION produces.
 *
 * <p><b>Every segment keeps its location.</b> A claim extracted from a
 * document must be traceable to the place in the document it came from —
 * the page of a PDF, the section of a Markdown file, the paragraph of an
 * HTML page. A claim that cannot be traced to a location is not evidence.
 */
public record ExtractedDocument(
        SourceRecord source,
        String text,
        List<Segment> segments) {

    public ExtractedDocument {
        segments = List.copyOf(segments == null ? List.of() : segments);
    }

    /** One located piece of the document. */
    public record Segment(
            String id,
            String text,
            String location,
            int order,
            String heading,
            String language) {

        public Segment {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("a segment must have an id");
            }
            if (text == null) {
                throw new IllegalArgumentException("a segment must have text");
            }
        }
    }

    /** The document's text, for the whole thing. */
    public boolean isEmpty() {
        return text == null || text.isBlank();
    }
}
