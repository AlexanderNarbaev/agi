package io.matrix.contract;

import java.util.List;

/**
 * W51 — where a record came from, and who is responsible for it.
 *
 * <p><b>What this replaces.</b> Provenance was spread over five unrelated fields: a claim's
 * {@code introducedBySourceId}, a source's {@code locator}, an observation's
 * {@code provenance} string, a cognitive signal's {@code provenance} string, and a procedure's
 * {@code origin}. Each carried a fragment of the truth and none of them carried the whole. The
 * W50 audit found the consequence: an imported claim's audit trail did not carry the byte offset
 * of the quoted span, and a status route reported counts it could not compute.</p>
 *
 * <p><b>What a provenance names.</b> Five separate facts, kept separate on purpose:</p>
 * <ol>
 *   <li><b>the source</b> — the document, dataset, observation, experiment or named human
 *       utterance the content came from;</li>
 *   <li><b>the origin</b> — the process, module or plugin that produced the record. A plugin's
 *       own origin is recorded here, so a generated artefact can never hide who made it;</li>
 *   <li><b>the method</b> — the procedure or algorithm that produced the content, and not the
 *       procedure that merely carried it;</li>
 *   <li><b>the span</b> — the precise region of the source the content came from, when the
 *       source representation permits it;</li>
 *   <li><b>the chain</b> — the earlier records this one was derived from, so a reader can walk
 *       backwards from a conclusion to the evidence.</li>
 * </ol>
 *
 * <p><b>The offset convention is declared, not implied.</b> {@link Span} says whether its
 * offsets are characters, UTF-8 bytes or lines, and whether they are inclusive. The W50 defect
 * was a trail that did not say which, and a reader could not tell a correct offset from an
 * incorrect one.</p>
 *
 * @param sourceId      the identifier of the source, or {@code null} when the record was not
 *                      derived from a source document
 * @param origin        the process, module or plugin that produced the record
 * @param method        the procedure or algorithm that produced the content
 * @param span          the region of the source the content came from, or {@code null}
 * @param derivedFrom   the identifiers of the records this one was derived from
 * @param recordedAt    when the record was made; a record of WHEN, never an input to a decision
 */
public record Provenance(
        String sourceId,
        String origin,
        String method,
        Span span,
        List<String> derivedFrom,
        String recordedAt) {

    /**
     * The precise region of a source that a piece of content came from.
     *
     * <p>The unit is named because "offset 120" is not a fact until the reader knows whether
     * the number counts characters, bytes or lines.</p>
     *
     * @param start    the first offset of the span, inclusive
     * @param end      the last offset of the span, exclusive, or {@code null} for an open span
     * @param unit     the unit the offsets are counted in
     */
    public record Span(long start, Long end, Unit unit) {

        /** The unit an offset is counted in. */
        public enum Unit {
            /** Unicode code points of the extracted text. */
            CHARACTERS,
            /** UTF-8 bytes of the extracted text. */
            BYTES,
            /** One-based line numbers. */
            LINES
        }

        public Span {
            if (start < 0) {
                throw new IllegalArgumentException("span start must not be negative");
            }
            if (end != null && end <= start) {
                throw new IllegalArgumentException("span end must be greater than its start");
            }
            if (unit == null) {
                throw new IllegalArgumentException("span unit must not be null");
            }
        }

        /** A span of characters, with an exclusive end. */
        public static Span characters(long start, long endExclusive) {
            return new Span(start, endExclusive, Unit.CHARACTERS);
        }

        /** True when the span names an end. */
        public boolean bounded() {
            return end != null;
        }
    }

    public Provenance {
        derivedFrom = derivedFrom == null ? List.of() : List.copyOf(derivedFrom);
    }

    /** A provenance that names nothing but an origin and an instant. */
    public static Provenance of(String origin, String recordedAt) {
        return new Provenance(null, origin, null, null, List.of(), recordedAt);
    }

    /** True when the record names the source it came from. */
    public boolean hasSource() {
        return sourceId != null && !sourceId.isBlank();
    }

    /** True when the record names the exact region of the source it came from. */
    public boolean hasSpan() {
        return span != null;
    }

    @Override
    public String toString() {
        return "Provenance[origin=" + origin + " source=" + sourceId
                + (span == null ? "" : " span=" + span.start() + ".." + span.end() + " " + span.unit())
                + "]";
    }
}
