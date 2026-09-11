package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 112 — BeamSearchGenerator unit tests. */
class BeamSearchGeneratorTest {

    @Test
    void constructorStoresValues() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/tmp/none"));
        BeamSearchGenerator gen = new BeamSearchGenerator(bridge, 4);
        assertThat(gen.beamWidth()).isEqualTo(4);
    }

    @Test
    void invalidBeamRejected() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/tmp/none"));
        assertThatThrownBy(() -> new BeamSearchGenerator(bridge, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BeamSearchGenerator(bridge, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateThrowsWhenBridgeNotLoaded() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/tmp/none"));
        BeamSearchGenerator gen = new BeamSearchGenerator(bridge, 1);
        assertThatThrownBy(() -> gen.generate("hi", 4))
                .isInstanceOf(IllegalStateException.class);
    }
}
