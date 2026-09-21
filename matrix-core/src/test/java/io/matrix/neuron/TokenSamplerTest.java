package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TokenSamplerTest {

    @Test
    void greedyReturnsArgmax() {
        float[] logits = {1.0f, 3.0f, 2.0f, 0.5f};
        assertThat(TokenSampler.greedy(logits)).isEqualTo(1);
    }

    @Test
    void greedyOnAllEqual() {
        float[] logits = {1.0f, 1.0f, 1.0f};
        // Returns first one (0) since comparison is strict >
        assertThat(TokenSampler.greedy(logits)).isEqualTo(0);
    }

    @Test
    void lowTemperatureApproximatesGreedy() {
        // At temperature=0.01, argmax dominates distribution
        TokenSampler.Config cfg = new TokenSampler.Config(0.01f, 0, 1.0f, 0);
        Random rng = new Random(42);
        int countArgmax = 0;
        int trials = 100;
        for (int i = 0; i < trials; i++) {
            float[] logits = {1.0f, 3.0f, 2.0f};
            if (TokenSampler.sample(logits, cfg, rng) == 1) countArgmax++;
        }
        // At T=0.01, exp(2/0.01) >> exp(0/0.01), so argmax wins nearly always
        assertThat(countArgmax).isGreaterThan(trials - 5);
    }

    @Test
    void highTemperatureFlattensDistribution() {
        TokenSampler.Config cfg = new TokenSampler.Config(10.0f, 0, 1.0f, 0);
        Random rng = new Random(42);
        // At T=10, distribution is nearly uniform → each token gets ~25%
        int[] counts = new int[4];
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            float[] logits = {1.0f, 1.0f, 1.0f, 1.0f};
            counts[TokenSampler.sample(logits, cfg, rng)]++;
        }
        // Each token should get ~250 trials
        for (int c : counts) {
            assertThat(c).isBetween(trials / 8, trials * 3 / 8);
        }
    }

    @Test
    void topKRestrictsToTopK() {
        TokenSampler.Config cfg = new TokenSampler.Config(1.0f, 2, 1.0f, 0);
        Random rng = new Random(42);
        // Only top-2 tokens (indices 1 and 2) should ever be sampled
        int[] counts = new int[4];
        for (int i = 0; i < 100; i++) {
            float[] logits = {0.1f, 5.0f, 3.0f, 0.5f}; // top-2 = indices 1, 2
            counts[TokenSampler.sample(logits, cfg, rng)]++;
        }
        assertThat(counts[0]).isEqualTo(0);
        assertThat(counts[3]).isEqualTo(0);
        assertThat(counts[1]).isGreaterThan(0);
        assertThat(counts[2]).isGreaterThan(0);
    }

    @Test
    void topPKeepsCumulativeSet() {
        // After softmax of {1.0, -0.5, -2.0, -3.0}:
        // probs ≈ {0.774, 0.173, 0.039, 0.014}
        // top-p=0.7 keeps index 0 only (0.774 ≥ 0.7)
        TokenSampler.Config cfg = new TokenSampler.Config(1.0f, 0, 0.7f, 0);
        Random rng = new Random(42);
        int[] counts = new int[4];
        for (int i = 0; i < 200; i++) {
            float[] logits = {1.0f, -0.5f, -2.0f, -3.0f};
            counts[TokenSampler.sample(logits, cfg, rng)]++;
        }
        // Only index 0 should appear (top-p kept set)
        assertThat(counts[0]).isEqualTo(200);
        assertThat(counts[1]).isEqualTo(0);
        assertThat(counts[2]).isEqualTo(0);
        assertThat(counts[3]).isEqualTo(0);
    }

    @Test
    void topPKeepsTwoTokens() {
        // After softmax of {0.5, 0.4, -2.0, -3.0}:
        // probs ≈ {0.466, 0.424, 0.063, 0.047}
        // top-p=0.85 keeps tokens until cumulative ≥ 0.85:
        // 0.466 + 0.424 = 0.890 ≥ 0.85 → both indices 0, 1 kept
        TokenSampler.Config cfg = new TokenSampler.Config(1.0f, 0, 0.85f, 0);
        Random rng = new Random(42);
        int[] counts = new int[4];
        for (int i = 0; i < 200; i++) {
            float[] logits = {0.5f, 0.4f, -2.0f, -3.0f};
            counts[TokenSampler.sample(logits, cfg, rng)]++;
        }
        // Both indices 0 and 1 should appear; 2 and 3 should not
        assertThat(counts[0] + counts[1]).isGreaterThan(150);
        assertThat(counts[2]).isEqualTo(0);
        assertThat(counts[3]).isEqualTo(0);
    }

    @Test
    void sampleReturnsValidToken() {
        TokenSampler.Config cfg = TokenSampler.Config.defaults();
        Random rng = new Random(42);
        float[] logits = new float[100];
        Random r = new Random(123);
        for (int i = 0; i < 100; i++) logits[i] = (float) r.nextGaussian();
        int sampled = TokenSampler.sample(logits, cfg, rng);
        assertThat(sampled).isBetween(0, 99);
    }

    @Test
    void defaultsMatchBitNetConfig() {
        TokenSampler.Config cfg = TokenSampler.Config.defaults();
        // Per BitNet b1.58 generation_config.json
        assertThat(cfg.temperature).isEqualTo(0.6f);
        assertThat(cfg.topP).isEqualTo(0.9f);
    }

    @Test
    void greedyConfig() {
        TokenSampler.Config cfg = TokenSampler.Config.greedy();
        assertThat(cfg.topK).isEqualTo(1);
        assertThat(cfg.topP).isEqualTo(1.0f);
    }

    @Test
    void sampleRejectsNullInputs() {
        TokenSampler.Config cfg = TokenSampler.Config.defaults();
        Random rng = new Random(1);
        assertThatThrownBy(() -> TokenSampler.sample(null, cfg, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TokenSampler.sample(new float[]{1.0f}, null, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TokenSampler.sample(new float[]{1.0f}, cfg, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sampleOnEmptyLogitsThrows() {
        TokenSampler.Config cfg = TokenSampler.Config.defaults();
        Random rng = new Random(1);
        assertThatThrownBy(() -> TokenSampler.sample(new float[0], cfg, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minPFiltersSmallProbs() {
        // After softmax of {5.0, -2.0, -3.0, -4.0}:
        // probs ≈ {0.998, 0.0009, 0.0003, 0.0001}
        // min_p=0.1 keeps tokens ≥ 0.1 * 0.998 = 0.0998. Only index 0.
        TokenSampler.Config cfg = new TokenSampler.Config(1.0f, 0, 1.0f, 0.1f);
        Random rng = new Random(42);
        int[] counts = new int[4];
        for (int i = 0; i < 100; i++) {
            float[] logits = {5.0f, -2.0f, -3.0f, -4.0f};
            counts[TokenSampler.sample(logits, cfg, rng)]++;
        }
        assertThat(counts[0]).isEqualTo(100);
        assertThat(counts[1]).isEqualTo(0);
        assertThat(counts[2]).isEqualTo(0);
        assertThat(counts[3]).isEqualTo(0);
    }

    @Test
    void sampleDistributionSumsToOne() {
        // Verify that over many samples, each token gets ~expected prob.
        // Use uniform logits + T=2 → close to uniform distribution.
        TokenSampler.Config cfg = new TokenSampler.Config(2.0f, 0, 1.0f, 0);
        Random rng = new Random(42);
        float[] logits = {1.0f, 1.0f, 1.0f, 1.0f};
        int[] counts = new int[4];
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            counts[TokenSampler.sample(logits, cfg, rng)]++;
        }
        // Each token should get ~25%
        for (int c : counts) {
            assertThat(c).isBetween(trials / 6, trials * 5 / 12);
        }
    }

}
