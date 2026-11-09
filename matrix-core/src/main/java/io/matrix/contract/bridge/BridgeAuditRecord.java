package io.matrix.contract.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * W51 — the record the bridge writes for every call it makes or refuses (BRIDGE-7).
 *
 * <p><b>Every call, not every accepted call.</b> BRIDGE-7: "Every call the bridge makes or
 * refuses is written to the append-only audit chain with its request id, its decision, and
 * the reason for the decision. The refusal is recorded as carefully as the acceptance: a
 * bridge that silently drops traffic is indistinguishable from a bridge that never existed."
 * This record is therefore made for refusals too, and it carries the same fields for both.</p>
 *
 * <p><b>Why the payload is recorded as a digest and not as its content.</b> The audit chain
 * is a list of decisions, and a decision is a line. A record that carried the whole payload
 * would put an unbounded, possibly enormous, possibly sensitive body into a chain that is
 * read as a whole. The digest identifies the payload exactly — it is what lets a reader
 * prove that a replay carried the same bytes — without copying the bytes. The content
 * itself stays where it was written.</p>
 *
 * <p><b>Why the record is immutable.</b> A record that could be edited after the fact is not
 * a record of what happened. This is a record: every component is final, there is no setter,
 * and the collection that holds these records hands out copies rather than the live objects.
 * A correction is made by appending another record, never by editing this one.</p>
 *
 * @param auditId    the identifier of this record
 * @param requestId  the request the call was about (BRIDGE-8)
 * @param decision   whether the call was accepted or refused
 * @param reason     why the decision was made; never blank (BRIDGE-7)
 * @param contractVersion the protocol version the call carried (BRIDGE-2)
 * @param at         the instant the decision was made, as an ISO-8601 string
 * @param senderId   the identity of the sender (BRIDGE-3, BRIDGE-4)
 * @param payloadDigest the digest of the payload the call carried
 * @param kind       which of the three permitted contents the payload carried (BRIDGE-5)
 */
public record BridgeAuditRecord(
        String auditId,
        String requestId,
        BridgeDecision.Status decision,
        String reason,
        int contractVersion,
        String at,
        String senderId,
        String payloadDigest,
        BridgeCapability kind) {

    /** The prefix of every audit identifier. */
    public static final String AUDIT_ID_PREFIX = "audit-";

    public BridgeAuditRecord {
        if (auditId == null || auditId.isBlank()) {
            throw new IllegalArgumentException("an audit record needs an audit id");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("an audit record needs a request id");
        }
        if (decision == null) {
            throw new IllegalArgumentException("an audit record needs a decision");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("an audit record needs a reason");
        }
        if (senderId == null || senderId.isBlank()) {
            throw new IllegalArgumentException("an audit record needs the identity of the sender");
        }
        if (payloadDigest == null || payloadDigest.isBlank()) {
            throw new IllegalArgumentException("an audit record needs the digest of the payload");
        }
        if (kind == null) {
            throw new IllegalArgumentException("an audit record needs the kind of the payload");
        }
    }

    /**
     * The record of one call.
     *
     * @param decision the decision that was made
     * @param envelope the message the call carried
     * @param at       the instant the decision was made
     * @return the record
     */
    public static BridgeAuditRecord of(BridgeDecision decision, BridgeEnvelope envelope, String at) {
        return new BridgeAuditRecord(decision.auditId(), decision.requestId(), decision.status(),
                decision.reason(), envelope.version(), at, envelope.senderId(),
                digestOf(envelope.payload()), envelope.kind());
    }

    /**
     * The identifier of the audit record of a request.
     *
     * <p>The identifier is derived from the request id and a sequence number, so that it is
     * reproducible and unique. The durable chain and its own identifiers are the server
     * side's business; this protocol only has to be able to name a record.</p>
     *
     * @param requestId the request the record is about
     * @param sequence  the number of the record in this bridge's chain
     * @return the identifier
     */
    public static String auditIdFor(String requestId, long sequence) {
        return AUDIT_ID_PREFIX + requestId + "-" + sequence;
    }

    /**
     * The digest of a payload (BRIDGE-7).
     *
     * @param payload the payload, as it was carried
     * @return the digest, in lower-case hex
     */
    public static String digestOf(String payload) {
        byte[] bytes = (payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HmacBridgeSigner.toHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("this JVM does not provide SHA-256", e);
        }
    }
}
