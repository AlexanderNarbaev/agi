package io.matrix.signals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 211 — DefaultSignals unit tests. */
class DefaultSignalsTest {

    @Test
    void textModuleEmits256Bits() {
        SignalRegistry.SignalModule m = DefaultSignals.textModule();
        assertThat(m.name()).isEqualTo(DefaultSignals.TEXT_NAME);
        assertThat(m.emit("hello")).hasSize(256);
    }

    @Test
    void audioModuleEmits256Bits() {
        SignalRegistry.SignalModule m = DefaultSignals.audioModule();
        assertThat(m.name()).isEqualTo(DefaultSignals.AUDIO_NAME);
        assertThat(m.emit("audio input")).hasSize(256);
    }

    @Test
    void videoModuleEmits256Bits() {
        SignalRegistry.SignalModule m = DefaultSignals.videoModule();
        assertThat(m.name()).isEqualTo(DefaultSignals.VIDEO_NAME);
        assertThat(m.emit("video frame")).hasSize(256);
    }

    @Test
    void defaultRegistryHasAllThree() {
        SignalRegistry r = DefaultSignals.defaultRegistry();
        assertThat(r.size()).isEqualTo(3);
        assertThat(r.has(DefaultSignals.TEXT_NAME)).isTrue();
        assertThat(r.has(DefaultSignals.AUDIO_NAME)).isTrue();
        assertThat(r.has(DefaultSignals.VIDEO_NAME)).isTrue();
    }

    @Test
    void modulesAreDeterministic() {
        var text1 = DefaultSignals.textModule().emit("hello");
        var text2 = DefaultSignals.textModule().emit("hello");
        assertThat(text1).isEqualTo(text2);
    }
}
