package io.matrix.federation;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ElspChannel — anti-replay signing channel of the ELSP protocol
 * (DESIGN-08 §ELSP v1): Ed25519 signatures bound to a strictly monotonic
 * per-peer sequence number. A receiver accepting envelopes through
 * {@link #verify(ElspEnvelope)} rejects any envelope whose {@code seq} is
 * not greater than the highest accepted one.
 *
 * <p>Signature covers {@code seq || payload} so the sequence cannot be
 * stripped or reordered without breaking verification.
 */
public final class ElspChannel {

    /** Signed envelope. */
    public record Envelope(long seq, byte[] payload, byte[] signature) {
        public Envelope {
            if (payload == null || payload.length == 0) {
                throw new IllegalArgumentException("payload must be non-empty");
            }
            signature = signature.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Envelope e
                    && e.seq == seq && java.util.Arrays.equals(payload, e.payload)
                    && java.util.Arrays.equals(signature, e.signature);
        }

        @Override
        public int hashCode() {
            int h = Long.hashCode(seq);
            h = 31 * h + java.util.Arrays.hashCode(payload);
            return 31 * h + java.util.Arrays.hashCode(signature);
        }
    }

    private final KeyPair keyPair;
    private final AtomicLong lastAcceptedSeq = new AtomicLong(-1);

    /** Creates a channel with a fresh Ed25519 key pair. */
    public ElspChannel() throws Exception {
        this.keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    /** Signs {@code seq || payload}; seq must be strictly increasing per channel. */
    public Envelope sign(long seq, byte[] payload) throws Exception {
        if (seq <= lastAcceptedSeq.get()) {
            throw new IllegalArgumentException("seq must exceed last accepted " + lastAcceptedSeq.get());
        }
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(ByteBuffer.allocate(8).putLong(seq).array());
        sig.update(payload);
        return new Envelope(seq, payload.clone(), sig.sign());
    }

    /**
     * Verifies signature AND replay window; on success records the envelope
     * as the new high-water mark.
     *
     * @return true when authentic and not a replay
     */
    public boolean verifyAndAccept(Envelope envelope) throws Exception {
        long prev;
        do {
            prev = lastAcceptedSeq.get();
            if (envelope.seq() <= prev) {
                return false; // replay or stale
            }
        } while (!lastAcceptedSeq.compareAndSet(prev, envelope.seq()));

        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(keyPair.getPublic());
        sig.update(ByteBuffer.allocate(8).putLong(envelope.seq()).array());
        sig.update(envelope.payload());
        return sig.verify(envelope.signature());
    }

    /** Base64-encoded public key for peer distribution. */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
