package io.matrix.io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IoTSensorTest {

    @Test
    void shouldEncodeTypeIntoLowBits() {
        IoTSensor s = new IoTSensor();
        s.enqueue(new IoTSensor.IotEvent("/dev/temp", 2, 42, "{\"v\":22.5}"));
        SensorFrame f = s.read();
        assertThat(f.sourceId()).isEqualTo("iot");
        assertThat(f.payload()).isEqualTo(SensorFrame.Payload.IOT_JSON);
        assertThat(f.sensorBits() & 0xFFL).isEqualTo(42L);
    }

    @Test
    void shouldEncodeSeverityInBits8to11() {
        IoTSensor s = new IoTSensor();
        s.enqueue(new IoTSensor.IotEvent("/dev/alert", 3, 7, ""));
        SensorFrame f = s.read();
        assertThat((f.sensorBits() >> 8) & 0xFL).isEqualTo(3L);
    }

    @Test
    void shouldRejectOutOfRangeSeverity() {
        assertThatThrownBy(() ->
                new IoTSensor.IotEvent("/x", 9, 0, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void shouldRejectOutOfRangeTypeCode() {
        assertThatThrownBy(() ->
                new IoTSensor.IotEvent("/x", 0, 1000, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typeCode");
    }

    @Test
    void shouldReturnEmptyWhenNoEvents() {
        IoTSensor s = new IoTSensor();
        assertThat(s.read()).isEqualTo(SensorFrame.EMPTY);
    }
}
