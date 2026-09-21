package io.matrix.io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorBusTest {

    @Test
    void shouldReturnEmptyWhenNoSensorsRegistered() {
        SensorBus bus = new SensorBus();
        assertThat(bus.size()).isZero();
        SensorFrame f = bus.pollAll();
        assertThat(f.sourceId()).isEqualTo("none");
        assertThat(f.sensorBits()).isZero();
    }

    @Test
    void shouldRegisterAndLookupSensors() {
        SensorBus bus = new SensorBus()
                .register(new ChatSensor())
                .register(new IoTSensor());
        assertThat(bus.size()).isEqualTo(2);
        assertThat(bus.sourceIds()).containsExactly("chat", "iot");
        assertThat(bus.get("chat")).isPresent();
        assertThat(bus.get("missing")).isEmpty();
    }

    @Test
    void shouldRejectDuplicateIds() {
        SensorBus bus = new SensorBus();
        bus.register(new ChatSensor());
        assertThatThrownBy(() -> bus.register(new ChatSensor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void shouldCompositeAllSensors() {
        ChatSensor chat = new ChatSensor();
        chat.enqueue("hello world");
        IoTSensor iot = new IoTSensor();
        iot.enqueue(new IoTSensor.IotEvent("/sensors/temp", 1, 42, "{\"v\":22.5}"));

        SensorBus bus = new SensorBus().register(chat).register(iot);
        SensorFrame f = bus.pollAll();
        assertThat(f.sensorBits()).isNotZero();
        // Composite should reflect OR of both — assert the bit field is the union
        SensorFrame chatFrame = chat.read();
        SensorFrame iotFrame = iot.read();
        assertThat(f.sensorBits()).isEqualTo(chatFrame.sensorBits() | iotFrame.sensorBits());
    }

    @Test
    void shouldUnregisterSuccessfully() {
        SensorBus bus = new SensorBus().register(new ChatSensor());
        assertThat(bus.unregister("chat")).isPresent();
        assertThat(bus.size()).isZero();
    }
}
