package io.matrix.federation;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ELSP v2 signature profile — post-quantum ML-DSA (FIPS 204, JEP 497) as the
 * drop-in upgrade of the Ed25519 channel (DESIGN-08 §ELSP «профиль v2»).
 *
 * <p>Same anti-replay semantics as {@link ElspChannel}: signatures cover
 * {@code seq || payload} and the receiver enforces strictly increasing
 * sequence numbers.
 *
 * <p>JDK 25 provides ML-DSA natively via JCA — no external dependency.
 */
public final class ElspChannelMlDsa {

    /** Post-quantum signed envelope. */
    public record Envelope(long seq, byte[] payload, byte[] signature) {
        public Envelope {
            if (payload == null || payload.length == 0) {
                throw new IllegalArgumentException("payload must be non-empty");
            }
            signature = signature.clone();
        }
    }

    private static final String KPG_ALG = "ML-DSA";
    private static final String SIG_ALG = "ML-DSA";

    private final KeyPair keyPair;
    private final AtomicLong lastAcceptedSeq = new AtomicLong(-1);

    public ElspChannelMlDsa() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KPG_ALG);
        this.keyPair = kpg.generateKeyPair();
    }

    public Envelope sign(long seq, byte[] payload) throws Exception {
        if (seq <= lastAcceptedSeq.get()) {
            throw new IllegalArgumentException("seq must exceed last accepted " + lastAcceptedSeq.get());
        }
        Signature sig = Signature.getInstance(SIG_ALG);
        sig.initSign(keyPair.getPrivate());
        sig.update(seqBytes(seq));
        sig.update(payload);
        return new Envelope(seq, payload.clone(), sig.sign());
    }

    public boolean verifyAndAccept(Envelope envelope) throws Exception {
        long prev;
        do {
            prev = lastAcceptedSeq.get();
            if (envelope.seq() <= prev) return false;
        } while (!lastAcceptedSeq.compareAndSet(prev, envelope.seq()));

        Signature sig = Signature.getInstance(SIG_ALG);
        sig.initVerify(keyPair.getPublic());
        sig.update(seqBytes(envelope.seq()));
        sig.update(envelope.payload());
        return sig.verify(envelope.signature());
    }

    private static byte[] seqBytes(long seq) {
        return ByteBuffer.allocate(8).putLong(seq).array();
    }
}
