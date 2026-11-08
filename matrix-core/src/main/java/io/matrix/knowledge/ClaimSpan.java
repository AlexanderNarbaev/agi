package io.matrix.knowledge;

import io.matrix.contract.Provenance;

/**
 * W51 §6 — where a claim came from, in a form a reader can check.
 *
 * <p><b>The defect this class exists for.</b> The W50 evaluation measured
 * that none of the 689 claims an answer cited carried an exact location
 * inside its document: the source record's locator was the constant "the
 * whole document" for every record, and a reader who followed a claim's
 * source identifier landed on a document with no place in it. The claim row
 * itself carried a segment locator — the file, the heading and the starting
 * line — which is better than nothing and is still not a span: it does not
 * say which part of the segment the sentence came from, and it is a string
 * whose meaning is a convention nobody had written down.
 *
 * <p><b>The convention, declared.</b> A span here is
 * {@link io.matrix.contract.Provenance.Span} with
 * {@link io.matrix.contract.Provenance.Span.Unit#CHARACTERS}: the offsets
 * are counted in characters of the EXTRACTED TEXT of the document, the
 * start is inclusive and the end is exclusive. The convention is not
 * invented here — it is the one the canonical contract already declares,
 * and the same type carries it, so there is one convention in the
 * repository and not two.
 *
 * <p><b>What is stored.</b> The claim's {@code context} field carries the
 * human-readable place and, after it, the span in the declared form:
 *
 * <pre>
 * wikipedia-plate-tectonics.md: "Plate tectonics" (line 1) [characters 0-1023]
 * </pre>
 *
 * <p><b>What is stored when the document gives no exact span.</b> The word
 * {@value #WHOLE_DOCUMENT}, in the same place, and nothing that looks like
 * a precision the extractor does not have. A PDF whose text layer carries
 * no offsets gets "the whole document", not an offset that was invented.
 *
 * <p>The class is a value object over a string: it formats a span and
 * parses one back, so that the convention lives in one place and a reader
 * can see it.
 */
public final class ClaimSpan {

    /**
     * The text that says the whole document, and no span inside it.
     *
     * <p>It is written where a span would be written, so that the absence
     * of a span is a fact on the record rather than an empty field.
     */
    public static final String WHOLE_DOCUMENT = "the whole document";

    /** The marker that opens the span part of a context. */
    private static final String SPAN_MARKER = "[characters ";

    private ClaimSpan() {
    }

    /**
     * The context of a claim extracted from a document.
     *
     * @param location the human-readable place — the file, the heading and
     *     the line the segment starts at
     * @param span the exact span of the segment inside the extracted text,
     *     or null when the document gives none
     * @return the context, with the convention declared in it
     */
    public static String format(String location, Provenance.Span span) {
        String place = location == null || location.isBlank() ? WHOLE_DOCUMENT : location;
        if (span == null) {
            return place + " [" + WHOLE_DOCUMENT + "]";
        }
        return place + " [characters " + span.start() + "-"
                + (span.end() == null ? "" : String.valueOf(span.end())) + "]";
    }

    /**
     * The span a context carries, or null when it carries none.
     *
     * @param context the context
     * @return the span, or null
     */
    public static Provenance.Span parse(String context) {
        if (context == null) {
            return null;
        }
        int at = context.indexOf(SPAN_MARKER);
        if (at < 0) {
            return null;
        }
        int end = context.indexOf(']', at);
        if (end < 0) {
            return null;
        }
        String body = context.substring(at + SPAN_MARKER.length(), end).trim();
        int dash = body.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            long start = Long.parseLong(body.substring(0, dash).trim());
            String tail = body.substring(dash + 1).trim();
            Long stop = tail.isEmpty() ? null : Long.parseLong(tail);
            return new Provenance.Span(start, stop, Provenance.Span.Unit.CHARACTERS);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whether a context says that the whole document was read.
     *
     * @param context the context
     * @return whether it says so
     */
    public static boolean isWholeDocument(String context) {
        return context != null && context.contains("[" + WHOLE_DOCUMENT + "]");
    }
}
