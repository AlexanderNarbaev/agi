package io.matrix.federation;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decentralized digest pipeline (H-K / DESIGN-08 §5): combines
 * k-anonymous bucketing (existing {@link Anonymizer}) with
 * differential-privacy Laplace noise before egress.
 *
 * <p>The pipeline:
 * <ol>
 *   <li>Counts contributions per content hash.</li>
 *   <li>Buckets hashes with {@code count < k} into a single
 *       "suppressed" bucket (k-anonymity).</li>
 *   <li>Adds Laplace(0, sensitivity/epsilon) noise to each count.</li>
 *   <li>Emits digests for buckets whose noisy count ≥ k.</li>
 * </ol>
 *
 * <p>DP parameters: epsilon = privacy budget (smaller = more noise,
 * stronger privacy); sensitivity = 1 per contribution. Laplace scale
 * b = sensitivity / epsilon.
 */
public final class DecentralizedDigestPipeline {

    /** A single digest record for egress. */
    public record Digest(String contentHash, long noisyCount, boolean shared) {
        public Digest {
            if (contentHash == null) throw new IllegalArgumentException("hash required");
            if (noisyCount < 0) throw new IllegalArgumentException("noisyCount must be ≥ 0");
        }
    }

    private final Anonymizer anonymizer;
    private final double epsilon;
    private final Random rng;

    public DecentralizedDigestPipeline(Anonymizer anonymizer, double epsilon) {
        this(anonymizer, epsilon, new SecureRandom());
    }

    public DecentralizedDigestPipeline(Anonymizer anonymizer, double epsilon, Random rng) {
        if (epsilon <= 0) throw new IllegalArgumentException("epsilon > 0");
        this.anonymizer = anonymizer;
        this.epsilon = epsilon;
        this.rng = rng;
    }

    /**
     * Run one pass over the known content hashes and emit digests.
     * Hashes whose true count meets the k-threshold are emitted
     * with noisy counts (Laplace noise); suppressed hashes get a
     * shared=false flag.
     */
    public List<Digest> emitDigests() {
        List<Digest> out = new ArrayList<>();
        for (var entry : anonymizer.snapshotEntries().entrySet()) {
            int trueCount = entry.getValue();
            long noisy = Math.max(0L, Math.round(trueCount + laplaceNoise()));
            boolean shared = noisy >= anonymizer.kThreshold();
            out.add(new Digest(entry.getKey(), noisy, shared));
        }
        return out;
    }

    /** Sample one Laplace(0, b) value, b = sensitivity/epsilon. */
    private double laplaceNoise() {
        double b = 1.0 / epsilon;
        double u = rng.nextDouble() - 0.5; // uniform in (-0.5, 0.5)
        return -b * Math.signum(u) * Math.log(1.0 - 2.0 * Math.abs(u));
    }
}