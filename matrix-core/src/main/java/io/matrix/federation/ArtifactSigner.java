package io.matrix.federation;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Artifact signer for federation (DESIGN-08 §1).
 *
 * <p>Signs knowledge artifacts with Ed25519 (Edwards-curve Digital Signature
 * Algorithm). Each artifact gets: content hash + timestamp + signature.
 * Verification proves the artifact was signed by the claimed node.
 *
 * <p>Per CONSTITUTION V: genesis-check and lineage tracking are mandatory
 * for any artifact entering the federation.
 */
public final class ArtifactSigner {

    private final KeyPair keyPair;
    private final String nodeId;

    public ArtifactSigner(String nodeId) throws Exception {
        this.nodeId = nodeId;
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        kpg.initialize(new ECGenParameterSpec("Ed25519"));
        this.keyPair = kpg.generateKeyPair();
    }

    /** Sign an artifact. */
    public SignedArtifact sign(byte[] content, String contentType) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA3-256").digest(content);
            String hashB64 = Base64.getEncoder().encodeToString(hash);
            long timestamp = Instant.now().toEpochMilli();

            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(keyPair.getPrivate());
            sig.update(hash);
            sig.update(nodeId.getBytes());
            sig.update(longToBytes(timestamp));
            byte[] signature = sig.sign();

            return new SignedArtifact(
                    nodeId, hashB64, contentType,
                    Base64.getEncoder().encodeToString(signature),
                    timestamp,
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    /** Verify a signed artifact. */
    public boolean verify(SignedArtifact artifact, byte[] content) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA3-256").digest(content);
            String expectedHash = Base64.getEncoder().encodeToString(hash);
            if (!expectedHash.equals(artifact.contentHash())) return false;

            Signature sig = Signature.getInstance("Ed25519");
            var kf = java.security.KeyFactory.getInstance("Ed25519");
            var pubKey = kf.generatePublic(
                    new java.security.spec.X509EncodedKeySpec(
                            Base64.getDecoder().decode(artifact.publicKey())));
            sig.initVerify(pubKey);
            sig.update(hash);
            sig.update(artifact.nodeId().getBytes());
            sig.update(longToBytes(artifact.timestamp()));
            return sig.verify(Base64.getDecoder().decode(artifact.signature()));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] longToBytes(long v) {
        return java.nio.ByteBuffer.allocate(8).putLong(v).array();
    }

    public String nodeId() { return nodeId; }

    /** Signed artifact record. */
    public record SignedArtifact(
            String nodeId,
            String contentHash,
            String contentType,
            String signature,
            long timestamp,
            String publicKey) {}
}
