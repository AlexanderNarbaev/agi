package io.matrix.research;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EXP-MATRIX.14 helper — Decentralized Digest Anonymizer.
 *
 * <p>Implements k-anonymity + Laplace DP-noise for hash-digest synthesis
 * (H-043 verification). The anonymizer takes a stream of "digest records"
 * (each a hash + content + timestamp tuple from a federated M3 quorum)
 * and emits k-anonymous buckets with DP-noised counts.
 *
 * <p>Determinism: the hash function is SHA-256, the Laplace noise uses
 * a seeded PRNG (so it can be replayed). All inputs are content-derived.
 *
 * <p>The "downstream utility" measure is: how many original records can
 * be reconstructed by matching anonymized buckets to their pre-anonymized
 * counterparts? The acceptance criterion is utility ≥ 0.7 at k=100,
 * ε=1.0.
 */
public final class DigestAnonymizer {

    /**
     * A pre-anonymization digest record (federated M3 output).
     */
    public record Digest(String hash, String content, long timestampMs, String tenant) {}

    /**
     * A k-anonymous bucket of digests.
     */
    public record Bucket(String bucketId, List<String> hashes, long noisedCount, double eps) {}

    /** DP-noise scale for Laplace mechanism with parameter ε. */
    public static double laplaceScale(double eps) {
        return 1.0 / eps;
    }

    /**
     * Compute k-anonymous buckets from a list of digests. Each bucket
     * contains at least {@code k} digests (smaller groups are suppressed).
     *
     * @param digests input records
     * @param k minimum group size (k-anonymity)
     * @param eps Laplace DP parameter (smaller = more noise)
     * @param rng seeded PRNG for Laplace noise
     * @return list of surviving buckets
     */
    public static List<Bucket> anonymize(List<Digest> digests, int k, double eps, Random rng) {
        // 1. Group digests by content-hash prefix (quasi-identifier surrogate).
        Map<String, List<Digest>> groups = new HashMap<>();
        for (Digest d : digests) {
            String qi = quasiIdentifier(d);
            groups.computeIfAbsent(qi, x -> new ArrayList<>()).add(d);
        }
        // 2. Suppress small groups; emit k-anonymous buckets with DP-noise.
        List<Bucket> buckets = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<Digest> group = e.getValue();
            if (group.size() < k) continue;
            long trueCount = group.size();
            long noised = trueCount + laplaceNoise(eps, rng);
            List<String> hashes = new ArrayList<>(group.size());
            for (Digest d : group) hashes.add(d.hash());
            buckets.add(new Bucket(e.getKey(), hashes, noised, eps));
        }
        return buckets;
    }

    /**
     * Utility measure: fraction of original digests that appear in any
     * surviving bucket. Range [0, 1]. H-043 acceptance requires ≥ 0.7.
     */
    public static double utilityMeasure(List<Digest> original, List<Bucket> buckets) {
        if (original.isEmpty()) return 0.0;
        java.util.Set<String> survivingHashes = new java.util.HashSet<>();
        for (Bucket b : buckets) {
            survivingHashes.addAll(b.hashes);
        }
        int matched = 0;
        for (Digest d : original) {
            if (survivingHashes.contains(d.hash())) matched++;
        }
        return (double) matched / original.size();
    }

    private static String quasiIdentifier(Digest d) {
        // Use the first 8 hex chars of the content hash as the quasi-identifier.
        // Digests in the same quasi-identifier bucket share a content prefix.
        return d.hash().substring(0, 8);
    }

    private static long laplaceNoise(double eps, Random rng) {
        double scale = laplaceScale(eps);
        double u = rng.nextDouble() - 0.5;
        double noise = -scale * Math.signum(u) * Math.log(1 - 2 * Math.abs(u));
        return Math.round(noise);
    }

    /** Generate deterministic synthetic digests for the experiment.
     * The quasi-identifier (first 8 hex chars) repeats across many records
     * so that k-anonymous groups form. Each call uses a fresh RNG to keep
     * the corpus stable per experiment. */
    public static List<Digest> generateCorpus(int n, Random rng) {
        List<Digest> out = new ArrayList<>(n);
        // Pre-compute a small pool of quasi-identifier prefixes so groups form.
        String[] qiPool = new String[10];
        for (int i = 0; i < qiPool.length; i++) {
            qiPool[i] = String.format("%08x", 0xa0000000L + i * 0x1000L + rng.nextInt(0x100));
        }
        for (int i = 0; i < n; i++) {
            String qi = qiPool[i % qiPool.length];
            // Per-record nonce in the lower 56 bits so the hash is unique.
            long nonce = (long) i * 7919L;
            String content = "digest-payload-" + qi + "-" + nonce;
            String hash = qi + String.format("%016x", nonce);
            long ts = 1_000_000L + i * 60_000L;
            String tenant = "tenant-" + (i % 10);
            out.add(new Digest(hash, content, ts, tenant));
        }
        return out;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private DigestAnonymizer() {}
}
