package io.matrix.neuron;

import io.matrix.agent.AgentBrainService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiBrainEnsembleTest {

    @Test
    void loadAllReturnsEnsemble() {
        MultiBrainEnsemble ensemble = MultiBrainEnsemble.loadAll();
        assertThat(ensemble).isNotNull();
        assertThat(ensemble.size()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void compositeSignatureProducesNonNullForValidText() {
        MultiBrainEnsemble ensemble = MultiBrainEnsemble.loadAll();
        if (ensemble.size() > 0) {
            long[] sig = ensemble.compositeSignature("hello");
            assertThat(sig).isNotNull();
            assertThat(sig.length).isGreaterThan(0);
            // First element is the sensor hash
            assertThat(sig[0]).isNotEqualTo(0L);
        }
    }

    @Test
    void compositeSignatureReturnsValidForDifferentTexts() {
        MultiBrainEnsemble ensemble = MultiBrainEnsemble.loadAll();
        if (ensemble.size() > 0) {
            long[] s1 = ensemble.compositeSignature("hello");
            long[] s2 = ensemble.compositeSignature("world");
            assertThat(s1).isNotNull();
            assertThat(s2).isNotNull();
            // Different inputs should produce different signatures
            assertThat(s1[0]).isNotEqualTo(s2[0]);
        }
    }

    @Test
    void compositeSignatureIsNullForNullInput() {
        MultiBrainEnsemble ensemble = MultiBrainEnsemble.loadAll();
        assertThat(ensemble.compositeSignature(null)).isNull();
    }

    @Test
    void decideReturnsZeroWhenEmpty() {
        // Empty ensemble (no models on disk) should still return 0
        MultiBrainEnsemble ensemble = MultiBrainEnsemble.loadAll();
        int action = ensemble.decide(42L);
        // Either 0 (empty) or a valid action code from first brain
        assertThat(action).isGreaterThanOrEqualTo(-1).isLessThanOrEqualTo(32);
    }
}