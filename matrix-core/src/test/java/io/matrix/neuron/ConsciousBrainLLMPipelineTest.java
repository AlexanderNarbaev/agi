package io.matrix.neuron;

import io.matrix.consciousness.CognitiveGenesisProfile;
import io.matrix.consciousness.CognitiveProcessor;
import io.matrix.consciousness.ExtendedIntegrationMetrics;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConsciousBrainLLMPipelineTest {

    @Test
    void brainHasLLMProcessorHook() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveProcessor proc = brain.getLLMProcessor();
        assertThat(proc).isNotNull();
    }

    @Test
    void processProfileReturnsEmbedding() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveGenesisProfile profile = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
        CognitiveProcessor.ProcessingResult r = brain.processProfileWithLLMPipeline(profile);
        assertThat(r).isNotNull();
        assertThat(r.embedding()).isNotNull();
        assertThat(r.embedding().length).isEqualTo(64);
    }

    @Test
    void multipleProfilesBuildHistory() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        for (int i = 0; i < 5; i++) {
            CognitiveGenesisProfile p = new CognitiveGenesisProfile(
                i / 5.0, i / 5.0, i / 5.0, i / 5.0, i / 5.0, 0.5, i / 5.0,
                50.0, 0.5, 0.5, 2, 0.5, 2.0
            );
            brain.processProfileWithLLMPipeline(p);
        }
        assertThat(brain.getLLMProcessor().historySize()).isEqualTo(5);
    }

    @Test
    void nullProfileReturnsNull() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveProcessor.ProcessingResult r = brain.processProfileWithLLMPipeline(null);
        assertThat(r).isNull();
    }

    @Test
    void llmProcessorIsSingleton() {
        ConsciousBrain brain = new ConsciousBrain(64, 42L);
        CognitiveProcessor p1 = brain.getLLMProcessor();
        CognitiveProcessor p2 = brain.getLLMProcessor();
        assertThat(p1).isSameAs(p2);
    }
}
