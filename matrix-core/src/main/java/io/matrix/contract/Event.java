package io.matrix.contract;

import java.util.Map;

/**
 * W51 — something that happened, recorded as a fact.
 *
 * <p><b>An event is not an envelope.</b> The envelope ({@link CognitiveEvent}) governs how a
 * message travels between processes; this record is what the message is about. Keeping them
 * apart is what lets the transport change — a different protocol, a different queue, a
 * different process — without the system's own history changing shape.</p>
 *
 * <p><b>An event is immutable and append-only.</b> The repository's own audit chain already
 * works this way, and the W50 defect "source records could be rewritten" is what happens when
 * a record that describes the past can be edited. A correction is a later event, not an edit
 * of an earlier one.</p>
 *
 * <p><b>The subject is named.</b> {@link #subjectId()} says what the event is about, so that
 * the events of one entity can be read together without reading the whole stream.</p>
 *
 * @param id          the identity of the event
 * @param kind        the type of the event
 * @param subjectId   what the event is about
 * @param payload     the typed detail of the event
 * @param occurredAt  when it happened
 * @param correlation the causality metadata of the request that produced it
 * @param provenance  where the event came from
 */
public record Event(
        String id,
        String kind,
        String subjectId,
        Map<String, Object> payload,
        String occurredAt,
        Correlation correlation,
        Provenance provenance) {

    /** The kinds of event the cognitive path records. */
    public static final String KIND_OBSERVED = "OBSERVED";
    /** The kind of an event that records a belief revision. */
    public static final String KIND_BELIEF_REVISED = "BELIEF_REVISED";
    /** The kind of an event that records a learned skill. */
    public static final String KIND_SKILL_LEARNED = "SKILL_LEARNED";
    /** The kind of an event that records a reflection. */
    public static final String KIND_REFLECTED = "REFLECTED";
    /** The kind of an event that records a refusal. */
    public static final String KIND_REFUSED = "REFUSED";

    public Event {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("event id must not be blank");
        }
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("event kind must not be blank");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /**
     * An event about a subject.
     *
     * @param id         the identity of the event
     * @param kind       the type of the event
     * @param subjectId  what the event is about
     * @param occurredAt when it happened
     * @return the event
     */
    public static Event of(String id, String kind, String subjectId, String occurredAt) {
        return new Event(id, kind, subjectId, Map.of(), occurredAt, null, null);
    }

    @Override
    public String toString() {
        return "Event[" + id + " " + kind + " " + subjectId + "]";
    }
}
