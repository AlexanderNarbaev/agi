package io.matrix.contract.bridge;

/**
 * W51 — why the bridge refused a message (BRIDGE-6).
 *
 * <p><b>A refusal without a reason is a defect.</b> BRIDGE-6 says every failure of the bridge
 * "results in a refusal and in an audit record", and BRIDGE-7 says the audit record carries
 * "the reason for the decision". A boolean "refused" cannot be audited: a reader of the audit
 * chain could not tell an exhausted quota from a forged signature, and a bridge whose
 * refusals are indistinguishable is a bridge nobody can repair.</p>
 *
 * <p><b>Why an enum and not a free string.</b> A free string can carry a typo, and a typo
 * turns a refusal that the audit chain can count into one that no query finds. The set of
 * reasons is closed: the bridge refuses for the reasons below and for no other, and a new
 * reason is a new version of the protocol rather than a new string (BRIDGE-2).</p>
 *
 * <p><b>The default is "no".</b> There is no {@code OTHER} constant. A refusal that does not
 * fit any of these is a bug in the bridge, and the honest way to report a bug is to fail the
 * build, not to invent a bucket that hides it.</p>
 */
public enum BridgeRefusalReason {

    /** The message named a protocol this reader does not know (BRIDGE-1, BRIDGE-2). */
    UNKNOWN_PROTOCOL("the message names a protocol this reader does not know"),

    /** The message carries a version this reader does not know (BRIDGE-2). */
    UNSUPPORTED_VERSION("the message carries a protocol version this reader does not know"),

    /** The sender asked for a right this bridge did not grant (BRIDGE-5). */
    CAPABILITY_NOT_GRANTED("the sender asked for a right this bridge did not grant"),

    /** No verification secret is configured, so no call can be trusted (BRIDGE-3). */
    NO_SECRET_CONFIGURED("no verification secret is configured, so nothing can be trusted"),

    /** The signature does not verify against the configured secret (BRIDGE-3). */
    SIGNATURE_INVALID("the signature does not verify"),

    /** The message is not a message this reader can read (BRIDGE-6). */
    MALFORMED_MESSAGE("the message is not a message this reader can read"),

    /** The payload names a kind that is not one of the three the protocol permits (BRIDGE-5). */
    UNKNOWN_PAYLOAD_KIND("the payload names a kind the protocol does not define"),

    /** The payload's kind and the envelope's kind disagree (BRIDGE-5). */
    PAYLOAD_KIND_MISMATCH("the payload kind does not agree with the envelope kind"),

    /** The message carries no provenance (BRIDGE-4). */
    MISSING_PROVENANCE("the message carries no provenance"),

    /** The request id was already used for a different message (BRIDGE-8). */
    REQUEST_ID_CONFLICT("the request id was already used for a different message"),

    /** The message is larger than the bridge allows (BRIDGE-9). */
    MESSAGE_TOO_LARGE("the message is larger than the bridge allows"),

    /** The batch is larger than the bridge allows (BRIDGE-9). */
    BATCH_TOO_LARGE("the batch is larger than the bridge allows"),

    /** The bridge is receiving faster than it allows (BRIDGE-9). */
    RATE_LIMIT_EXCEEDED("the bridge is receiving faster than it allows"),

    /** The bridge has reached its ceiling for the day (BRIDGE-9). */
    DAILY_CEILING_EXCEEDED("the bridge has reached its ceiling for the day");

    private final String description;

    BridgeRefusalReason(String description) {
        this.description = description;
    }

    /** What this refusal means, in one sentence. */
    public String description() {
        return description;
    }
}
