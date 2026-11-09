package io.matrix.contract.bridge;

/**
 * W51 — how a bridge message is authenticated (BRIDGE-3).
 *
 * <p><b>Why this is an interface at all.</b> BRIDGE-3 requires every call to be authenticated
 * and requires the chain A side to verify it. The mechanism it names — "mutual TLS or a signed
 * token" — is a choice, and the choice belongs to the deployment, not to the protocol. The
 * protocol therefore says what it needs: something that produces a signature over a byte
 * sequence, and something that can check one. {@link HmacBridgeSigner} is the implementation
 * this build ships; a deployment that terminates mutual TLS at the transport writes another
 * one and changes nothing else.</p>
 *
 * <p><b>Why verification is part of the interface and not a detail of the implementation.</b>
 * BRIDGE-3 says an unauthenticated call "is refused, not served in a degraded mode". A
 * verifier that could be asked "is this good enough?" is a verifier that will eventually be
 * asked it. The interface has one boolean answer and it is the only answer.</p>
 *
 * <p><b>There is no default.</b> An implementation that could be constructed without a secret
 * would be a bridge that accepts messages it cannot authenticate, which is the failure mode
 * BRIDGE-3 exists to prevent.</p>
 */
public interface BridgeSigner {

    /**
     * The algorithm this signer uses, named in the message (BRIDGE-3).
     *
     * @return the algorithm name
     */
    String algorithm();

    /**
     * The signature of a message.
     *
     * @param message the bytes to sign
     * @return the raw signature bytes
     */
    byte[] sign(byte[] message);

    /**
     * True when a signature is good for a message.
     *
     * @param message      the bytes that were signed
     * @param signatureHex the signature as it arrived, in hex
     * @return true when the signature verifies
     */
    boolean verify(byte[] message, String signatureHex);
}
