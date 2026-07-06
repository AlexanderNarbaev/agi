package io.matrix.security;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;

/**
 * Ed25519 cryptographic signing for {@code .ldn} snapshots.
 *
 * <p>Uses Java's built-in EdDSA (JEP 1440, Java 15+). No external crypto
 * libraries required.
 *
 * <p>Ref: L6_Memory.md §7
 */
@ApplicationScoped
public class SnapshotSigner {

    private final KeyPair keyPair;

    public SnapshotSigner() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();
    }

    SnapshotSigner(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * Signs the given data with the Ed25519 private key.
     *
     * @param data the data to sign
     * @return the Ed25519 signature
     * @throws Exception if signing fails
     */
    public byte[] sign(byte[] data) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(data);
        return sig.sign();
    }

    /**
     * Verifies a signature against the data using the public key.
     *
     * @param data      the original data
     * @param signature the signature to verify
     * @return true if the signature is valid
     * @throws Exception if verification fails
     */
    public boolean verify(byte[] data, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(keyPair.getPublic());
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * Returns the public key used for verification.
     */
    public PublicKey publicKey() {
        return keyPair.getPublic();
    }
}
