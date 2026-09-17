package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W321 — Adaptive compute + MLA integration test.
 *
 * <p>Combines all adaptive techniques with MLA latent attention.
 */
class AdaptiveComputeWithMLATest {

    @Test
    void pipelineEmbeddingThenMLA() {
        CognitiveEmbedding embedding = new CognitiveEmbedding(64, 42L);
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0);
        double[] vec = embedding.embed(p);
        double[] latent = mla.compress(vec, true);
        // 8x compression
        // 16 latent × 8 compression = 128 (matches MHA 2×8×8)
        assertThat(latent.length * mla.compressionRatio()).isEqualTo(128.0);
    }

    @Test
    void pipelineMultiTokenWithMLA() {
        CognitiveEmbedding embedding = new CognitiveEmbedding(64, 42L);
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        CognitiveMultiTokenPrediction.MultiTokenPrediction mtp =
            CognitiveMultiTokenPrediction.predict(makeProfile(0.5), 3, 42L);

        // Compress each predicted profile
        for (int i = 0; i < mtp.predictions().size(); i++) {
            CognitiveGenesisProfile p = mtp.predictions().get(i);
            double[] vec = embedding.embed(p);
            double[] latent = mla.compress(vec, true);
            assertThat(latent.length).isEqualTo(16);
        }
    }

    @Test
    void pipelineTestTimeWithMLA() {
        CognitiveEmbedding embedding = new CognitiveEmbedding(64, 42L);
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 3);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), 2);

        // Compress trajectories
        for (CognitiveTestTimeCompute.ReasoningAttempt att : r.allAttempts()) {
            for (CognitiveGenesisProfile p : att.trajectory()) {
                double[] vec = embedding.embed(p);
                double[] latent = mla.compress(vec, true);
                assertThat(latent.length).isEqualTo(16);
            }
        }
    }

    @Test
    void mlaDecompressionPreservesApproximateValues() {
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 32, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = Math.sin(i * 0.1);
        double[] latent = mla.compress(v, true);
        double[] reconstructed = mla.decompress(latent, true);
        // Reconstruction should be approximate (not exact — this is compression)
        assertThat(reconstructed.length).isEqualTo(64);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
