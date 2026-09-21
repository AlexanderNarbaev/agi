package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MpdtHdcBridgeTest {

    @Test
    void constructorRejectsNullArgs() {
        HierarchicalBrain mpdt = new HierarchicalBrain(new Random(1));
        HdcBrain hdc = new HdcBrain(10, new Random(1));
        assertThatThrownBy(() -> new MpdtHdcBridge(null, hdc, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MpdtHdcBridge(mpdt, null, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MpdtHdcBridge(mpdt, hdc, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decideAndRememberReturnsValidAction() {
        HierarchicalBrain mpdt = new HierarchicalBrain(new Random(1));
        HdcBrain hdc = new HdcBrain(20, new Random(1));
        MpdtHdcBridge bridge = new MpdtHdcBridge(mpdt, hdc, new Random(1));
        MpdtHdcBridge.HybridDecision decision = bridge.decideAndRemember(42L);
        assertThat(decision.action).isBetween(0, 31);
        assertThat(decision.fromMemory()).isFalse();
        assertThat(decision.sensors).isEqualTo(42L);
    }

    @Test
    void decideAndRememberStoresInHdc() {
        HierarchicalBrain mpdt = new HierarchicalBrain(new Random(1));
        HdcBrain hdc = new HdcBrain(20, new Random(1));
        MpdtHdcBridge bridge = new MpdtHdcBridge(mpdt, hdc, new Random(1));
        bridge.decideAndRemember(42L);
        // Brain should now know about this sensor→action pair
        assertThat(hdc.size()).isEqualTo(1);
    }

    @Test
    void decideWithMemoryEventualMemoryHit() {
        HierarchicalBrain mpdt = new HierarchicalBrain(new Random(1));
        HdcBrain hdc = new HdcBrain(20, new Random(1));
        MpdtHdcBridge bridge = new MpdtHdcBridge(mpdt, hdc, new Random(1));
        long sensors = 12345L;
        bridge.decideAndRemember(sensors); // learn
        MpdtHdcBridge.HybridDecision decision = bridge.decideWithMemory(sensors, -1.0);
        // With threshold -1.0, even poor matches count
        // (similarity >= -1 always true)
        assertThat(decision.fromMemory()).isTrue();
        assertThat(decision.memoryHit).isNotNull();
    }

    @Test
    void decideWithMemoryFallbackOnLowSimilarity() {
        HierarchicalBrain mpdt = new HierarchicalBrain(new Random(1));
        HdcBrain hdc = new HdcBrain(20, new Random(1));
        MpdtHdcBridge bridge = new MpdtHdcBridge(mpdt, hdc, new Random(1));
        // No training — memory is empty
        MpdtHdcBridge.HybridDecision decision = bridge.decideWithMemory(42L, 0.99);
        assertThat(decision.fromMemory()).isFalse();
    }

    @Test
    void encodeSensorsProducesCorrectLength() {
        MpdtHdcBridge bridge = new MpdtHdcBridge(
                new HierarchicalBrain(new Random(1)),
                new HdcBrain(20, new Random(1)),
                new Random(1));
        float[] f = bridge.encodeSensors(0xDEADBEEFL);
        assertThat(f).hasSize(HdcEncoding.DIM);
        // Each sensor bit should map to 16 features
        for (int i = 0; i < 64; i++) {
            float bit = ((0xDEADBEEFL >>> i) & 1L) != 0L ? 1.0f : -1.0f;
            for (int j = 0; j < 16; j++) {
                assertThat(f[i * 16 + j]).isEqualTo(bit);
            }
        }
    }

    @Test
    void hybridDecisionToStringContainsAction() {
        MpdtHdcBridge.HybridDecision d = new MpdtHdcBridge.HybridDecision(42L, 5, null);
        assertThat(d.toString()).contains("action=5").contains("fromMpdt");
    }

    @Test
    void hybridDecisionFromMemoryTrueWhenHitSet() {
        // Construct Recall through a real HdcBrain forward
        HdcBrain hdc = new HdcBrain(20, new Random(1));
        hdc.learn(new float[HdcEncoding.DIM], "act-3", 0.5f, 0.01f);
        HdcBrain.Recall hit = hdc.forward(new float[HdcEncoding.DIM]);
        MpdtHdcBridge.HybridDecision d = new MpdtHdcBridge.HybridDecision(42L, 3, hit);
        assertThat(d.fromMemory()).isTrue();
        assertThat(d.toString()).contains("fromMemory");
    }

    @Test
    void benchmarkRuns() {
        HierarchicalBrain mpdt = new HierarchicalBrain(new Random(1));
        HdcBrain hdc = new HdcBrain(20, new Random(1));
        MpdtHdcBridge bridge = new MpdtHdcBridge(mpdt, hdc, new Random(42));
        MpdtHdcBridge.BridgeBenchmarkResult result = bridge.benchmark(20, 10, 0.5);
        assertThat(result.total).isEqualTo(10);
        // Both should be 100% (same sensor → same action for both MPDT and hybrid)
        assertThat(result.mpdtAccuracy()).isEqualTo(1.0);
        assertThat(result.hybridAccuracy()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void benchmarkRejectsBadArgs() {
        MpdtHdcBridge bridge = new MpdtHdcBridge(
                new HierarchicalBrain(new Random(1)),
                new HdcBrain(20, new Random(1)),
                new Random(1));
        assertThatThrownBy(() -> bridge.benchmark(-1, 10, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bridge.benchmark(10, 0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void benchmarkResultMethods() {
        MpdtHdcBridge.BridgeBenchmarkResult r = new MpdtHdcBridge.BridgeBenchmarkResult(
                9, 8, 10, 5);
        assertThat(r.mpdtAccuracy()).isEqualTo(0.9);
        assertThat(r.hybridAccuracy()).isEqualTo(0.8);
        assertThat(r.memoryHitRate()).isEqualTo(0.5);
        assertThat(r.toString()).contains("90.0%").contains("80.0%").contains("5/10");
    }
}
