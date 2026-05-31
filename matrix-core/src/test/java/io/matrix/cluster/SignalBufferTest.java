package io.matrix.cluster;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalBufferTest {

    @Test
    void shouldPushAndPoll() {
        SignalBuffer buffer = new SignalBuffer(100);
        NeuronId id = NeuronId.create();
        Signal signal = new Signal(id, id, true);

        assertThat(buffer.push(signal)).isTrue();
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.poll()).isEqualTo(signal);
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void shouldRejectWhenFull() {
        SignalBuffer buffer = new SignalBuffer(2);
        NeuronId id = NeuronId.create();

        assertThat(buffer.push(new Signal(id, id, true))).isTrue();
        assertThat(buffer.push(new Signal(id, id, false))).isTrue();
        assertThat(buffer.push(new Signal(id, id, true))).isFalse();
    }

    @Test
    void shouldDrainToCollection() {
        SignalBuffer buffer = new SignalBuffer(10);
        NeuronId id = NeuronId.create();
        buffer.push(new Signal(id, id, true));
        buffer.push(new Signal(id, id, false));

        java.util.List<Signal> drained = new java.util.ArrayList<>();
        int count = buffer.drainTo(drained);

        assertThat(count).isEqualTo(2);
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void shouldReportCapacity() {
        SignalBuffer buffer = new SignalBuffer(42);
        assertThat(buffer.capacity()).isEqualTo(42);
    }
}
