package io.matrix.federation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H-K tests for {@link DecentralizedDigestPipeline}: k-anonymity
 * bucketing + Laplace DP noise on the noisy counts.
 */
class DecentralizedDigestPipelineTest {

    @Test
    void digestsAreSuppressedBelowThreshold() {
        Anonymizer anon = new Anonymizer(5);
        anon.recordContribution("h1", "n1");
        DecentralizedDigestPipeline pipe = new DecentralizedDigestPipeline(anon, 1.0);

        List<DecentralizedDigestPipeline.Digest> digests = pipe.emitDigests();
        assertThat(digests).hasSize(1);
        DecentralizedDigestPipeline.Digest d = digests.get(0);
        assertThat(d.contentHash()).isEqualTo("h1");
        // noisy count is true count (1) + Laplace noise; for high epsilon
        // and a single sample, it's likely still below 5
        assertThat(d.shared()).isFalse();
    }

    @Test
    void digestsAreSharedAtAndAboveThreshold() {
        Anonymizer anon = new Anonymizer(2);
        DecentralizedDigestPipeline pipe = new DecentralizedDigestPipeline(anon, 1.0);

        // push count past threshold
        for (int i = 0; i < 5; i++) {
            anon.recordContribution("h-full", "n" + i);
        }
        List<DecentralizedDigestPipeline.Digest> digests = pipe.emitDigests();
        DecentralizedDigestPipeline.Digest d = digests.stream()
                .filter(x -> x.contentHash().equals("h-full"))
                .findFirst().orElseThrow();
        assertThat(d.noisyCount()).isGreaterThanOrEqualTo(2);
        assertThat(d.shared()).isTrue();
    }

    @Test
    void noisyCountIsAlwaysNonNegative() {
        Anonymizer anon = new Anonymizer(2);
        // small true count — noise could in theory push it negative
        anon.recordContribution("h-low", "n1");
        DecentralizedDigestPipeline pipe =
                new DecentralizedDigestPipeline(anon, 0.001, // high noise
                        new Random(42));
        DecentralizedDigestPipeline.Digest d = pipe.emitDigests().get(0);
        assertThat(d.noisyCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void epsilonMustBePositive() {
        Anonymizer anon = new Anonymizer(2);
        assertThatThrownBy(() -> new DecentralizedDigestPipeline(anon, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecentralizedDigestPipeline(anon, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyAnonymizerEmitsNoDigests() {
        Anonymizer anon = new Anonymizer(2);
        DecentralizedDigestPipeline pipe = new DecentralizedDigestPipeline(anon, 1.0);
        assertThat(pipe.emitDigests()).isEmpty();
    }

    @Test
    void higherEpsilonMeansLessNoise() {
        // Two pipelines over the same data; the lower-epsilon one must
        // produce a wider noisy-count distribution.
        Anonymizer anonLow = new Anonymizer(2);
        Anonymizer anonHigh = new Anonymizer(2);
        for (int i = 0; i < 10; i++) {
            anonLow.recordContribution("h", "n" + i);
            anonHigh.recordContribution("h", "n" + i);
        }
        Random rng = new Random(1234);
        DecentralizedDigestPipeline lowE =
                new DecentralizedDigestPipeline(anonLow, 0.1, new Random(1234));
        DecentralizedDigestPipeline highE =
                new DecentralizedDigestPipeline(anonHigh, 10.0, new Random(1234));
        long lowCount = lowE.emitDigests().get(0).noisyCount();
        long highCount = highE.emitDigests().get(0).noisyCount();
        // high-epsilon is closer to the true count (10) than low-epsilon
        assertThat(Math.abs(highCount - 10)).isLessThanOrEqualTo(Math.abs(lowCount - 10));
    }
}