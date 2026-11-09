package io.matrix.contract.bridge;

import java.nio.charset.StandardCharsets;

/**
 * W51 — the wire envelope of a bridge message (BRIDGE-2, BRIDGE-3, BRIDGE-4, BRIDGE-8).
 *
 * <p><b>What the envelope is.</b> The whole message, in the order it is put on the wire:
 * the protocol and its version, the request id, the instant the sender wrote it, the identity
 * of the sender, the kind of the payload, the payload as canonical JSON, and the signature
 * over the canonical bytes of everything before it. Nothing of the message is outside the
 * envelope, so there is nothing that can be added to it in transit without changing the bytes
 * the signature covers.</p>
 *
 * <p><b>Why the request id is here and not only in a header.</b> BRIDGE-8 makes the bridge
 * idempotent <i>per request id</i>, and the request id is the idempotency key. It is part of
 * the signed bytes, so a sender cannot change it after signing: a message whose id was
 * rewritten in flight would not verify, and the bridge would refuse it rather than record the
 * effect under a key the sender did not sign for.</p>
 *
 * <p><b>Why {@link #canonicalBytes()} exists and what "canonical" means here.</b> The
 * signature is over bytes, so both sides must produce the same bytes for the same message.
 * The form is: the seven fields in a fixed order, separated by a line feed, encoded as UTF-8,
 * with the signature itself excluded. The three identifier fields — the request id, the
 * instant and the sender id — are required not to contain a line feed, and the constructor
 * refuses to build an envelope that breaks that rule; the payload is the last field, so
 * whatever it contains cannot be mistaken for one of the others. The result is that two
 * envelopes with the same content always produce the same bytes, in this process and in any
 * other, and a receiver in a different module can reproduce them without sharing code
 * beyond this record.</p>
 *
 * <p><b>Why the version is not checked here.</b> The constructor accepts any version, because
 * a reader must be able to <i>receive</i> a version it does not know in order to refuse it
 * (BRIDGE-2). A constructor that rejected the wrong version would move the refusal into the
 * sender, and a sender that cannot build a message it does not understand would never tell
 * the reader that it does not understand it. The check belongs to the reader, and it lives in
 * {@link BridgeReceiver}.</p>
 *
 * @param protocol   the name of the protocol (BRIDGE-1)
 * @param version    the version of the protocol (BRIDGE-2)
 * @param requestId  the identity of the call, and its idempotency key (BRIDGE-8)
 * @param sentAt     the instant the sender wrote the message (BRIDGE-4)
 * @param senderId   the identity of the sender (BRIDGE-3, BRIDGE-4)
 * @param kind       which of the three permitted contents the payload carries (BRIDGE-5)
 * @param payload    the payload as canonical JSON
 * @param signature  the signature over the canonical bytes, in lower-case hex, or {@code null}
 *                   when the envelope has not been signed yet
 */
public record BridgeEnvelope(
        String protocol,
        int version,
        String requestId,
        String sentAt,
        String senderId,
        BridgeCapability kind,
        String payload,
        String signature) {

    public BridgeEnvelope {
        if (protocol == null || protocol.isBlank()) {
            throw new IllegalArgumentException("bridge protocol name must not be blank");
        }
        if (version < 0) {
            throw new IllegalArgumentException("bridge protocol version must not be negative");
        }
        requireSingleLine(requestId, "request id");
        requireSingleLine(sentAt, "sent at");
        requireSingleLine(senderId, "sender id");
        if (kind == null) {
            throw new IllegalArgumentException("bridge envelope kind must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("bridge envelope payload must not be null");
        }
    }

    /**
     * The bytes the signature is computed over.
     *
     * <p>The form is fixed and documented so that another implementation can reproduce it:
     * the protocol name, the version, the request id, the instant, the sender id and the kind,
     * each followed by a line feed, and then the payload, encoded as UTF-8. The signature is
     * not part of the form — a signature over itself would be a fixed point nobody can
     * compute.</p>
     *
     * @return the canonical bytes of this message
     */
    public byte[] canonicalBytes() {
        String canonical = protocol + '\n'
                + version + '\n'
                + requestId + '\n'
                + sentAt + '\n'
                + senderId + '\n'
                + kind.name() + '\n'
                + payload;
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The signature of this message under the given signer (BRIDGE-3).
     *
     * @param signer the signer that holds the secret
     * @return the signature, in lower-case hex
     */
    public String sign(BridgeSigner signer) {
        if (signer == null) {
            throw new IllegalArgumentException("a signer is required to sign a bridge message");
        }
        return HmacBridgeSigner.toHex(signer.sign(canonicalBytes()));
    }

    /**
     * The signature of this message under the given secret (BRIDGE-3).
     *
     * @param secret the shared secret
     * @return the signature, in lower-case hex
     */
    public String sign(byte[] secret) {
        return new HmacBridgeSigner(secret).signHex(canonicalBytes());
    }
    /**
     * True when this message's signature verifies under the given signer (BRIDGE-3).
     *
     * @param signer the signer that holds the secret
     * @return true when the signature is good
     */
    public boolean verify(BridgeSigner signer) {
        if (signer == null || signature == null || signature.isBlank()) {
            return false;
        }
        return signer.verify(canonicalBytes(), signature);
    }

    /**
     * True when this message's signature verifies under the given secret (BRIDGE-3).
     *
     * @param secret the shared secret
     * @return true when the signature is good
     */
    public boolean verify(byte[] secret) {
        return verify(new HmacBridgeSigner(secret));
    }

    /**
     * This envelope with the given signature (BRIDGE-3).
     *
     * @param newSignature the signature over this envelope's canonical bytes
     * @return the signed envelope
     */
    public BridgeEnvelope withSignature(String newSignature) {
        return new BridgeEnvelope(protocol, version, requestId, sentAt, senderId, kind, payload,
                newSignature);
    }

    /**
     * The digest of the payload, which is what the audit record carries (BRIDGE-7).
     *
     * @return the digest of the payload, in lower-case hex
     */
    public String payloadDigest() {
        return BridgeAuditRecord.digestOf(payload);
    }

    /**
     * A field that takes part in the canonical form must not span lines.
     *
     * <p>The canonical form is line-delimited, so a field carrying a line feed would make two
     * different messages produce the same bytes. Refusing the envelope at construction is
     * better than discovering it at verification time, when the message has already travelled
     * and the reader can only refuse it.</p>
     */
    private static void requireSingleLine(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("bridge envelope " + what + " must not be blank");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "bridge envelope " + what + " must not contain a line break");
        }
    }
}
