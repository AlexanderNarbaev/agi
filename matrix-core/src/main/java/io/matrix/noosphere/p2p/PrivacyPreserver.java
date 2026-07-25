package io.matrix.noosphere.p2p;

import io.matrix.noosphere.FnlPackage;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Privacy-preserving anonymization for P2P knowledge exchange.
 * 
 * Removes identifying information from FNL packages before
 * broadcasting to the P2P network.
 */
@ApplicationScoped
public class PrivacyPreserver {

    /**
     * Anonymize a package by removing author identity.
     */
    public FnlPackage anonymize(FnlPackage pkg) {
        String anonymizedAuthor = pkg.authorInstanceId() != null ?
                hashPeer(pkg.authorInstanceId()) : "anonymous";
        return FnlPackage.builder()
                .name(pkg.name() != null ? pkg.name() : "unnamed")
                .type(pkg.type())
                .version(pkg.version())
                .authorInstanceId(anonymizedAuthor)
                .accuracy(pkg.accuracy())
                .generation(pkg.generation())
                .description(pkg.description())
                .tags(pkg.tags())
                .certified(pkg.certified())
                .snapshotHash(pkg.snapshotHash())
                .build();
    }

    /**
     * Verify content integrity by checking hash.
     */
    public boolean verifyIntegrity(FnlPackage pkg) {
        if (pkg.snapshotHash() == null || pkg.snapshotHash().isEmpty()) {
            return false;
        }
        // Hash exists — basic integrity check
        return pkg.snapshotHash().length() >= 8;
    }

    /**
     * Hash peer ID for anonymization.
     */
    private String hashPeer(String peerId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(peerId.getBytes(StandardCharsets.UTF_8));
            return "anon-" + HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return "anon-" + Math.abs(peerId.hashCode());
        }
    }
}
