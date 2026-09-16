package io.matrix.research;

import io.matrix.consciousness.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W219 — Integration test for LLM-inspired cognitive subsystems.
 *
 * <p>End-to-end pipeline:
 * 1. Generate 64 cognitive profiles (8 frozen + 8 edge + 8 chaotic)
 * 2. Embed via CognitiveEmbedding
 * 3. Compute self-attention
 * 4. Store in ProfileKVCache
 * 5. Apply CognitiveSlidingWindow
 * 6. Speculate next profile
 * 7. RAG retrieval
 * 8. Project to 2D
 */
class W219LLMInspiredIntegrationTest {

    @Test
    void endToEndLLMInspiredPipeline() {
        // Generate 24 profiles (3 regimes × 8)
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 8; i++) profiles.add(makeProfile(0.1 + i * 0.01));  // frozen
        for (int i = 0; i < 8; i++) profiles.add(makeProfile(0.5 + i * 0.01));  // edge
        for (int i = 0; i < 8; i++) profiles.add(makeProfile(0.9 + i * 0.01));  // chaotic

        // 1. Embed
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 42L);
        double[] first = emb.embed(profiles.get(0));
        assertThat(first.length).isEqualTo(64);

        // 2. Self-attention
        CognitiveAttention.AttentionResult att =
            CognitiveAttention.selfAttention(profiles, 16, 42L);
        assertThat(att.attentionWeights().length).isEqualTo(24);
        for (double[] row : att.attentionWeights()) {
            double sum = 0;
            for (double v : row) sum += v;
            assertThat(sum).isCloseTo(1.0, offset(1e-9));
        }

        // 3. KV cache
        ProfileKVCache cache = new ProfileKVCache(4, 8, 1L);
        for (CognitiveGenesisProfile p : profiles) cache.append(p);
        assertThat(cache.size()).isEqualTo(24);
        assertThat(cache.pageCount()).isLessThanOrEqualTo(8);

        // 4. Sliding window
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(4, 8);
        for (CognitiveGenesisProfile p : profiles) sw.add(p);
        assertThat(sw.size()).isLessThanOrEqualTo(12);

        // 5. Speculative prediction
        double rate = ProfileSpeculativePredictor.acceptanceRate(profiles, 2, 0.5);
        assertThat(rate).isBetween(0.0, 1.0 + 1e-9);

        // 6. RAG
        CognitiveGenesisProfile query = profiles.get(0);
        CognitiveRAG.RetrievalResult rag = CognitiveRAG.retrieveAndAugment(
            query, profiles, 3, 0.5);
        assertThat(rag.retrievedIndices().length).isEqualTo(3);

        // 7. 2D projection
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, 42L);
        assertThat(points.length).isEqualTo(24);
        double length = ProfileEmbedding2D.trajectoryLength(points);
        assertThat(length).isGreaterThan(0.0);

        // Sink detection
        int[] sinks = CognitiveAttention.findSinks(att.attentionWeights(), 3);
        assertThat(sinks.length).isEqualTo(3);
    }

    @Test
    void pipelineDeterministicSameSeed() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 16; i++) profiles.add(makeProfile(i / 16.0));

        CognitiveEmbedding e1 = new CognitiveEmbedding(64, 42L);
        CognitiveEmbedding e2 = new CognitiveEmbedding(64, 42L);
        for (int i = 0; i < profiles.size(); i++) {
            double[] v1 = e1.embed(profiles.get(i));
            double[] v2 = e2.embed(profiles.get(i));
            for (int d = 0; d < 64; d++) {
                assertThat(v1[d]).isCloseTo(v2[d], offset(1e-9));
            }
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
