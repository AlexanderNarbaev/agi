package io.matrix.federation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * RUN 165 — Federation digest (shared between nodes).
 *
 * <p>A digest summarizes state changes a node shares with peers.
 * Per R11.1, federation data is digests only — never raw payloads.
 *
 * <p>Digest structure:
 * <pre>
 *   digest = {
 *     nodeId,
 *     timestampMillis,
 *     entries: [{ key, hash, tier }]
 *   }
 * </pre>
 *
 * <p>Hashing is SHA-256 (deterministic). The digest itself is
 * deterministic: same state + same nodeId → same digest.
 */
public final class FederationDigest {

    public record Entry(String key, String hash, String tier) {}

    public record Digest(String nodeId,
                         long timestampMillis,
                         List<Entry> entries,
                         String bodyHash) {}

    public static Digest compute(String nodeId, long timestampMillis,
                                 List<Entry> entries) {
        StringBuilder body = new StringBuilder();
        body.append("node=").append(nodeId).append(';');
        body.append("ts=").append(timestampMillis).append(';');
        for (Entry e : entries) {
            body.append(e.key()).append('=').append(e.hash())
                    .append('|').append(e.tier()).append(';');
        }
        String bodyHash = sha256(body.toString());
        return new Digest(nodeId, timestampMillis, entries, bodyHash);
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(Digest d) {
        // Re-compute body hash from entries + nodeId + ts.
        StringBuilder body = new StringBuilder();
        body.append("node=").append(d.nodeId()).append(';');
        body.append("ts=").append(d.timestampMillis()).append(';');
        for (Entry e : d.entries()) {
            body.append(e.key()).append('=').append(e.hash())
                    .append('|').append(e.tier()).append(';');
        }
        return sha256(body.toString()).equals(d.bodyHash());
    }

    public static List<Entry> entriesForTier(Digest d, String tier) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : d.entries()) {
            if (e.tier().equals(tier)) out.add(e);
        }
        return out;
    }
}
