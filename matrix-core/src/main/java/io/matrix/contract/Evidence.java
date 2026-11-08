package io.matrix.contract;

/**
 * W51 — a piece of evidence bearing on a proposition.
 *
 * <p><b>Evidence is not a score.</b> The workbench's own {@code io.matrix.knowledge.Evidence}
 * carries a {@code quality} in {@code [0,100]}; that is an assessment of the SOURCE, and it is
 * not the same as the strength of the bearing this piece of evidence has on the proposition.
 * This record keeps the relation (supports, contradicts, qualifies) explicit and keeps the
 * quality of the source separate from it.</p>
 *
 * <p><b>The span is the part the W50 audit found missing.</b> "The audit trace of an imported
 * claim does not carry the byte offset of the quoted span" was an open owner question
 * (Q-W50-02). This record carries the span through the {@link Provenance}, with a declared
 * unit, so a reader can check the quotation against the source.</p>
 *
 * <p><b>An evidence record is immutable.</b> The W50 defect "source records could be rewritten"
 * applies here too: an evidence record that can be edited after the fact is not a record of
 * what was observed. Corrections are made by adding a contradicting record, never by editing
 * this one.</p>
 *
 * @param id         the identity of the evidence record
 * @param subjectId  the proposition the evidence bears on
 * @param sourceId   the source the evidence comes from
 * @param relation   how the evidence bears on the proposition
 * @param excerpt    the quoted part of the source, when there is one
 * @param quality    the authority of the SOURCE, in {@code [0,100]}, not the strength of the
 *                   bearing
 * @param recordedAt when the evidence was recorded
 * @param provenance where the evidence came from, including the span when known
 */
public record Evidence(
        String id,
        String subjectId,
        String sourceId,
        Relation relation,
        String excerpt,
        int quality,
        String recordedAt,
        Provenance provenance) {

    /** How a piece of evidence bears on a proposition. */
    public enum Relation {
        /** The evidence supports the proposition. */
        SUPPORTS,
        /** The evidence contradicts the proposition. */
        CONTRADICTS,
        /** The evidence narrows the proposition's scope without deciding it. */
        QUALIFIES,
        /** The evidence is about the proposition but decides nothing. */
        INCONCLUSIVE
    }

    public Evidence {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("evidence id must not be blank");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("evidence subject id must not be blank");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("evidence source id must not be blank");
        }
        if (relation == null) {
            throw new IllegalArgumentException("evidence relation must not be null");
        }
        quality = Math.max(0, Math.min(100, quality));
    }

    /** True when the evidence supports the proposition. */
    public boolean supports() {
        return relation == Relation.SUPPORTS;
    }

    /** True when the evidence contradicts the proposition. */
    public boolean contradicts() {
        return relation == Relation.CONTRADICTS;
    }

    /** True when the evidence names the exact region of the source it came from. */
    public boolean hasSpan() {
        return provenance != null && provenance.hasSpan();
    }

    @Override
    public String toString() {
        return "Evidence[" + id + " " + relation + " subject=" + subjectId + "]";
    }
}
