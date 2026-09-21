package io.matrix.research;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.14 — H-043 verification: decentralized digest synthesis
 * (k-anonymous + DP-noise) preserves utility ≥ 0.7.
 *
 * <p>Setup:
 * <ul>
 *   <li>Generate a synthetic corpus of {@code N=1000} digests with
 *       10 distinct quasi-identifiers (tenants).</li>
 *   <li>Apply k-anonymity (k=100) and Laplace DP-noise (ε=1.0).</li>
 *   <li>Measure utility = fraction of original digests appearing in
 *       surviving buckets.</li>
 * </ul>
 *
 * <p>Acceptance: utility ≥ 0.7.
 */
class Exp043DecentralizedDigestTest {

    @Test
    void utilityAtK100Epsilon1() {
        Random rng = new Random(42);
        List<DigestAnonymizer.Digest> corpus = DigestAnonymizer.generateCorpus(1000, rng);

        // k=100, ε=1.0 — H-043 target.
        List<DigestAnonymizer.Bucket> buckets = DigestAnonymizer.anonymize(corpus, 100, 1.0, rng);
        double utility = DigestAnonymizer.utilityMeasure(corpus, buckets);

        System.out.printf(
                "[EXP-MATRIX.14] H-043: N=%d buckets=%d utility=%.3f%n",
                corpus.size(), buckets.size(), utility);

        assertThat(utility)
                .as("H-043 acceptance: utility ≥ 0.7 at k=100, ε=1.0")
                .isGreaterThanOrEqualTo(0.7);
    }

    @Test
    void higherKReducesBucketCount() {
        // Sanity check: raising k should suppress more groups.
        Random rng = new Random(42);
        List<DigestAnonymizer.Digest> corpus = DigestAnonymizer.generateCorpus(1000, rng);

        List<DigestAnonymizer.Bucket> b50 = DigestAnonymizer.anonymize(corpus, 50, 1.0, rng);
        List<DigestAnonymizer.Bucket> b200 = DigestAnonymizer.anonymize(corpus, 200, 1.0, rng);
        List<DigestAnonymizer.Bucket> b500 = DigestAnonymizer.anonymize(corpus, 500, 1.0, rng);

        System.out.printf(
                "[EXP-MATRIX.14] buckets-vs-k: k=50 -> %d, k=200 -> %d, k=500 -> %d%n",
                b50.size(), b200.size(), b500.size());

        assertThat(b50.size()).isGreaterThanOrEqualTo(b200.size());
        assertThat(b200.size()).isGreaterThanOrEqualTo(b500.size());
    }

    @Test
    void smallerEpsilonIncreasesNoise() {
        // Compare noised counts at ε=1.0 vs ε=0.1 — smaller ε should produce
        // larger absolute deviations.
        Random rng1 = new Random(7);
        Random rng2 = new Random(7);
        List<DigestAnonymizer.Digest> corpus = DigestAnonymizer.generateCorpus(1000, rng1);
        List<DigestAnonymizer.Bucket> b1 = DigestAnonymizer.anonymize(corpus, 100, 1.0, rng1);
        List<DigestAnonymizer.Bucket> b2 = DigestAnonymizer.anonymize(corpus, 100, 0.1, rng2);

        // Mean absolute noise
        double noise1 = meanAbsNoise(b1);
        double noise2 = meanAbsNoise(b2);
        System.out.printf("[EXP-MATRIX.14] noise-eps1=%.3f noise-eps01=%.3f%n",
                noise1, noise2);
        assertThat(noise2).isGreaterThan(noise1);
    }

    @Test
    void utilityDropsAtLargerK() {
        // Higher k → fewer surviving buckets → lower utility. Sanity check.
        Random rng = new Random(99);
        List<DigestAnonymizer.Digest> corpus = DigestAnonymizer.generateCorpus(1000, rng);

        double u50 = DigestAnonymizer.utilityMeasure(corpus, DigestAnonymizer.anonymize(corpus, 50, 1.0, rng));
        double u200 = DigestAnonymizer.utilityMeasure(corpus, DigestAnonymizer.anonymize(corpus, 200, 1.0, rng));
        System.out.printf("[EXP-MATRIX.14] utility k=50: %.3f  k=200: %.3f%n", u50, u200);
        assertThat(u50).isGreaterThanOrEqualTo(u200);
    }

    @Test
    void noisedCountIsLaplaceAroundTrueCount() {
        // Sanity: with seeded PRNG the noised count should be close to true count.
        Random rng = new Random(123);
        List<DigestAnonymizer.Digest> corpus = DigestAnonymizer.generateCorpus(1000, rng);
        List<DigestAnonymizer.Bucket> buckets = DigestAnonymizer.anonymize(corpus, 100, 1.0, rng);
        assertThat(buckets).isNotEmpty();
        // Each bucket should have at least k digests.
        for (var b : buckets) {
            assertThat(b.hashes().size()).isGreaterThanOrEqualTo(100);
            // Noised count can be negative for small ε; with ε=1.0 and 100+ records
            // it should be positive.
            assertThat(b.noisedCount()).isGreaterThan(0);
        }
    }

    private static double meanAbsNoise(List<DigestAnonymizer.Bucket> buckets) {
        if (buckets.isEmpty()) return 0.0;
        double sum = 0;
        for (var b : buckets) {
            // true count ≈ hashes.size(); noised count = b.noisedCount()
            sum += Math.abs(b.noisedCount() - b.hashes().size());
        }
        return sum / buckets.size();
    }
}
