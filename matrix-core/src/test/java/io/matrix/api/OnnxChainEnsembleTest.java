package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 81 — OnnxChainEnsemble unit tests. */
class OnnxChainEnsembleTest {

    @Test
    void pureOnnxAlphaReturnsArgmaxOfLogits() {
        QwenOnnxBridge stub = stub();
        OnnxChainEnsemble ensemble = new OnnxChainEnsemble(stub, 1.0, 5);
        float[] logits = {0.1f, 0.5f, 0.2f, 0.9f, 0.3f};
        int idx = ensemble.combineAndArgmax(logits);
        assertThat(idx).isEqualTo(3);  // argmax
        assertThat(ensemble.onnxCalls()).isEqualTo(1);
        assertThat(ensemble.chainCalls()).isZero();
    }

    @Test
    void pureChainAlphaReturnsZero() {
        QwenOnnxBridge stub = stub();
        OnnxChainEnsemble ensemble = new OnnxChainEnsemble(stub, 0.0, 5);
        float[] logits = {0.1f, 0.5f, 0.2f, 0.9f, 0.3f};
        int idx = ensemble.combineAndArgmax(logits);
        assertThat(idx).isEqualTo(0);  // pure chain returns 0 placeholder
        assertThat(ensemble.chainCalls()).isEqualTo(1);
    }

    @Test
    void mixedAlphaCombinesLogits() {
        QwenOnnxBridge stub = stub();
        OnnxChainEnsemble ensemble = new OnnxChainEnsemble(stub, 0.5, 5);
        float[] logits = {0.1f, 0.5f, 0.2f, 0.9f, 0.3f};
        int idx = ensemble.combineAndArgmax(logits);
        // 0.5 * 0.1 + 0.5 * 0 = 0.05
        // 0.5 * 0.5 + 0.5 * 0 = 0.25
        // 0.5 * 0.2 + 0.5 * 0 = 0.10
        // 0.5 * 0.9 + 0.5 * 0 = 0.45
        // 0.5 * 0.3 + 0.5 * 0 = 0.15
        // argmax = index 3
        assertThat(idx).isEqualTo(3);
    }

    @Test
    void invalidAlphaRejected() {
        QwenOnnxBridge stub = stub();
        assertThatThrownBy(() -> new OnnxChainEnsemble(stub, -0.5, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OnnxChainEnsemble(stub, 1.5, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logitsLengthMismatchThrows() {
        QwenOnnxBridge stub = stub();
        OnnxChainEnsemble ensemble = new OnnxChainEnsemble(stub, 1.0, 5);
        float[] logits = {0.1f, 0.5f};  // only 2 elements
        assertThatThrownBy(() -> ensemble.combineAndArgmax(logits))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countersIncrementIndependently() {
        QwenOnnxBridge stub = stub();
        OnnxChainEnsemble ensemble = new OnnxChainEnsemble(stub, 0.7, 5);
        float[] logits = {0.1f, 0.5f, 0.2f, 0.9f, 0.3f};
        for (int i = 0; i < 3; i++) ensemble.combineAndArgmax(logits);
        assertThat(ensemble.onnxCalls()).isEqualTo(3);
        assertThat(ensemble.chainCalls()).isEqualTo(3);
    }

    @Test
    void resetCountersClears() {
        QwenOnnxBridge stub = stub();
        OnnxChainEnsemble ensemble = new OnnxChainEnsemble(stub, 0.7, 5);
        float[] logits = {0.1f, 0.5f, 0.2f, 0.9f, 0.3f};
        ensemble.combineAndArgmax(logits);
        ensemble.resetCounters();
        assertThat(ensemble.onnxCalls()).isZero();
        assertThat(ensemble.chainCalls()).isZero();
    }

    @Test
    void alphaAccessor() {
        QwenOnnxBridge stub = stub();
        OnnxChainEnsemble e1 = new OnnxChainEnsemble(stub, 0.0, 5);
        OnnxChainEnsemble e2 = new OnnxChainEnsemble(stub, 0.5, 5);
        OnnxChainEnsemble e3 = new OnnxChainEnsemble(stub, 1.0, 5);
        assertThat(e1.alpha()).isEqualTo(0.0);
        assertThat(e2.alpha()).isEqualTo(0.5);
        assertThat(e3.alpha()).isEqualTo(1.0);
    }

    private static QwenOnnxBridge stub() {
        // No need to actually load — we use logits arrays directly.
        return new QwenOnnxBridge(java.nio.file.Path.of("/tmp/none"));
    }
}
