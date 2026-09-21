package io.matrix.research;

import io.matrix.consciousness.*;
import io.matrix.neuron.ConsciousBrain;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W269 — End-to-end integration test for all LLM-architecture
 * techniques applied to cognitive processing.
 *
 * <p>Pipeline:
 * 1. Generate 8 cognitive profiles
 * 2. Embed via CognitiveEmbedding
 * 3. Apply RoPE positional encoding
 * 4. Apply LayerNorm + Residual + SwiGLU (Transformer block)
 * 5. Apply GQA attention
 * 6. Speculate next profile (EAGLE-style)
 * 7. Verify with Constitutional AI
 * 8. Apply RLHF training
 */
class W269LLMArchitectureIntegrationTest {

    @Test
    void endToEndLLMArchitecturePipeline() {
        // 1. Generate profiles
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            profiles.add(makeProfile(0.3 + i * 0.05));
        }

        // 2. Embed each profile
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 42L);
        double[][] vectors = new double[profiles.size()][64];
        for (int i = 0; i < profiles.size(); i++) {
            vectors[i] = emb.embed(profiles.get(i));
        }
        for (double[] v : vectors) {
            assertThat(v.length).isEqualTo(64);
        }

        // 3. Apply RoPE positional encoding
        for (int i = 0; i < profiles.size(); i++) {
            vectors[i] = CognitiveRotaryEmbedding.apply(vectors[i], i);
        }

        // 4. Apply LayerNorm + Residual + SwiGLU (Transformer block)
        CognitiveSwiGLU glu = new CognitiveSwiGLU(64, 128, 42L);
        for (int i = 0; i < profiles.size(); i++) {
            double[] normed = CognitiveLayerNormalization.apply(vectors[i]);
            double[] mlpOut = glu.apply(normed);
            vectors[i] = CognitiveResidualConnection.residual(normed, mlpOut);
        }

        // 5. Apply GQA attention
        CognitiveGroupedQueryAttention gqa = new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        for (int i = 0; i < profiles.size(); i++) {
            vectors[i] = gqa.attend(vectors[i]);
        }

        // 6. Speculate next profile (EAGLE-style)
        double speculationRate = ProfileSpeculativePredictor.acceptanceRate(profiles, 2, 0.5);
        assertThat(speculationRate).isBetween(0.0, 1.0 + 1e-9);

        // 7. Constitutional AI verification
        CognitiveConstitutionalAI.ConstitutionalResult caiResult =
            CognitiveConstitutionalAI.evaluate(profiles.get(profiles.size() - 1),
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        assertThat(caiResult.critiques().size()).isEqualTo(4);

        // 8. RLHF training
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        List<CognitiveRLHF.Preference> prefs = new ArrayList<>();
        prefs.add(new CognitiveRLHF.Preference(profiles.get(7), profiles.get(0)));
        CognitiveRLHF.TrainingResult trainResult = rlhf.train(prefs, 2);
        assertThat(trainResult.history().size()).isEqualTo(2);

        // All vectors should still be 64-dim
        for (double[] v : vectors) {
            assertThat(v.length).isEqualTo(64);
        }
    }

    @Test
    void consciousBrainAppliesAllTechniques() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveGenesisProfile p = makeProfile(0.5);
        // Apply RoPE
        double[] roped = brain.applyRoPEToProfile(p, 0);
        assertThat(roped).isNotNull();
        // Apply SwiGLU
        double[] glud = brain.applySwiGLUToProfile(p);
        assertThat(glud).isNotNull();
        // LLM pipeline
        CognitiveProcessor.ProcessingResult result = brain.processProfileWithLLMPipeline(p);
        assertThat(result).isNotNull();
        assertThat(result.embedding()).isNotNull();
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
