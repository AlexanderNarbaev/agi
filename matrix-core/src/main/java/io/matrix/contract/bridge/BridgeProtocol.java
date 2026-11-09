package io.matrix.contract.bridge;

/**
 * W51 — the name and the version of the bridge protocol (BRIDGE-1, BRIDGE-2).
 *
 * <p><b>Why a name and a version exist at all.</b> BRIDGE-1 requires the bridge to be an
 * explicit, named, opt-in component with one owner: a reader must be able to say, from the
 * bytes alone, which protocol a message belongs to. BRIDGE-2 requires every message to carry a
 * version and the reader to refuse a version it does not know — "a change of the protocol is a
 * new version, never a silent change of the meaning of an old one". A name without a version
 * would let a future meaning drift under an old name, which is exactly what BRIDGE-2 forbids.
 * The two together are what make a future change of this protocol reviewable rather than
 * invisible.</p>
 *
 * <p><b>The header names are part of the contract, not a convention.</b> They are constants
 * because chain B and chain A are two different modules: a spelling that differs between them
 * is a bridge that silently drops traffic — the sender writes the header, the reader does not
 * find it, and the call is refused for a reason nobody can see. BRIDGE-6 says every failure
 * produces a refusal and an audit record; a misspelled header must be one of those refusals,
 * not a silence.</p>
 *
 * <p><b>This class touches nothing.</b> It has no state, no clock and no dependency. The
 * bridge protocol is a pure protocol: chain B may read it without pulling in anything of chain
 * A, which is the property BRIDGE-10 needs — the bridge is the only class allowed to reach
 * both chains, and it can only be that if it reaches neither on its own.</p>
 */
public final class BridgeProtocol {

    /** The name of the bridge protocol, carried in every message. */
    public static final String NAME = "matrix.bridge";

    /** The version of the bridge protocol this build speaks (BRIDGE-2). */
    public static final int VERSION = 2;

    /**
     * W52 — the oldest version this build still reads.
     *
     * <p><b>Why an old version is still readable.</b> Version 1 carried the first direction
     * of the bridge — an observation, a candidate, an evidence link — and every message of
     * that version says exactly what it means in a form this build understands. Version 2
     * adds the second direction: a task. A reader that understands both does not have to
     * <i>guess</i> what a version-1 message meant, so reading it is not the guess BRIDGE-2
     * forbids; refusing it would break a peer that has not been upgraded yet, for no gain.</p>
     *
     * <p><b>What is still refused.</b> A version this build has never heard of is refused
     * with its number named, exactly as before: the rule is not "close enough", it is "this
     * reader understands this version".</p>
     */
    public static final int MINIMUM_READABLE_VERSION = 1;

    /** The header carrying {@link #NAME}. */
    public static final String HEADER_PROTOCOL = "X-Matrix-Bridge-Protocol";

    /** The header carrying {@link #VERSION}. */
    public static final String HEADER_VERSION = "X-Matrix-Bridge-Version";

    /** The header carrying the request id, which is also the idempotency key (BRIDGE-8). */
    public static final String HEADER_REQUEST_ID = "X-Matrix-Bridge-Request-Id";

    /** The header carrying the instant the sender wrote the message. */
    public static final String HEADER_SENT_AT = "X-Matrix-Bridge-Sent-At";

    /** The header carrying the identity of the sender (BRIDGE-3, BRIDGE-4). */
    public static final String HEADER_SENDER_ID = "X-Matrix-Bridge-Sender-Id";

    /** The header carrying the kind of the payload — one of the three BRIDGE-5 permits. */
    public static final String HEADER_KIND = "X-Matrix-Bridge-Kind";

    /** The header carrying the canonical JSON of the payload. */
    public static final String HEADER_PAYLOAD = "X-Matrix-Bridge-Payload";

    /** The header carrying the signature over the canonical bytes of the message. */
    public static final String HEADER_SIGNATURE = "X-Matrix-Bridge-Signature";

    /** The algorithm the signature is computed with (BRIDGE-3). */
    public static final String SIGNATURE_ALGORITHM = "HMAC-SHA256";

    /**
     * True when a reader that speaks {@code supported} can read a message that offers
     * {@code offered} (BRIDGE-2).
     *
     * <p>The comparison is exact. A protocol is not "close enough": a reader that does not
     * know the offered version must refuse the message, because the alternative is to guess
     * what the sender meant, and a bridge that guesses is a bridge that writes whatever it
     * guessed into the epistemic store.</p>
     *
     * @param offered   the version the message carries
     * @param supported the version the reader speaks
     * @return true when the two are the same version
     */
    public static boolean compatible(int offered, int supported) {
        return offered == supported;
    }

    /**
     * True when this build can read a message that offers the given version (BRIDGE-2).
     *
     * @param offered the version the message carries
     * @return true when the message may be read
     */
    public static boolean supportedVersion(int offered) {
        return offered >= MINIMUM_READABLE_VERSION && offered <= VERSION;
    }

    private BridgeProtocol() {
        // A constant holder, not a thing that exists.
    }
}
