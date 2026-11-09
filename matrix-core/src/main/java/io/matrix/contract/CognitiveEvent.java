package io.matrix.contract;

/**
 * W51 — an event crossing a boundary.
 *
 * <p><b>What this replaces.</b> Nothing. The repository had an event journal
 * ({@code io.matrix.events.EventJournal}) that is an append-only log with a Kafka and an
 * in-memory implementation, but it carried no contract name, no version and no correlation, and
 * it was never mounted on a serving path — the Kafka implementation depends on a broker that
 * the running system does not have. The W51 requirement for "cross-process event delivery"
 * with "correlation IDs and causality metadata" needs a record that carries them, and that is
 * this one.</p>
 *
 * <p><b>An event is a statement about the past.</b> It is never a request, and it is never
 * re-issued as one. A delivered event may be delivered again — the W51 failure list names "an
 * event being delivered more than once" — and the consumer must be able to recognise the
 * repeat. {@link #eventId()} is that recogniser, and it is stable across deliveries of the
 * SAME event. A consumer that receives an event id it has already applied must not apply it
 * twice.</p>
 *
 * <p><b>The delivery count is recorded, not guessed.</b> {@link #deliveryAttempt()} says which
 * attempt this delivery is. A consumer that sees attempt two knows the first delivery was
 * lost or timed out — which is a different situation from a consumer that sees the same event
 * twice because the publisher crashed after publishing.</p>
 *
 * @param contract         the name of the contract the payload speaks
 * @param version          the version of that contract
 * @param eventId          the identity of the event; stable across redeliveries
 * @param correlation      the causality metadata
 * @param payload          the JSON payload of the event
 * @param occurredAt       when the thing described happened
 * @param deliveryAttempt  the one-based number of this delivery attempt
 * @param originProcess    the process that produced the event
 */
public record CognitiveEvent(
        String contract,
        int version,
        String eventId,
        Correlation correlation,
        String payload,
        String occurredAt,
        int deliveryAttempt,
        String originProcess) {

    /** The delivery attempt number of a first delivery. */
    public static final int FIRST_DELIVERY = 1;

    public CognitiveEvent {
        if (contract == null || contract.isBlank()) {
            throw new IllegalArgumentException("event contract must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("event contract version must be at least one");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("event id must not be blank");
        }
        if (correlation == null) {
            throw new IllegalArgumentException("event correlation must not be null");
        }
        if (payload == null) {
            payload = "{}";
        }
        if (deliveryAttempt < 1) {
            throw new IllegalArgumentException("delivery attempt must be at least one");
        }
    }

    /** True when this is a redelivery of an event the consumer may already have applied. */
    public boolean redelivery() {
        return deliveryAttempt > FIRST_DELIVERY;
    }

    /** True when the payload speaks a contract version this build implements. */
    public boolean contractSupported(int supportedVersion) {
        return CognitiveContract.compatible(version, supportedVersion);
    }

    @Override
    public String toString() {
        return "CognitiveEvent[" + contract + " v" + version + " " + eventId
                + " attempt=" + deliveryAttempt + "]";
    }
}
