package io.matrix.io;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinecraftBotSensorTest {

    @Test
    void shouldEncodeCoordinatesIntoMidBits() {
        MinecraftBotSensor s = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
        s.enqueue(new MinecraftBotSensor.BotEvent(
                MinecraftBotSensor.BotEvent.Kind.MOVED,
                10, 64, 100, 1.0, "walk"));
        SensorFrame f = s.read();
        assertThat(f.sourceId()).isEqualTo("minecraft-bot");
        assertThat((f.sensorBits() >> 8) & 0xFFL).isEqualTo(10L);
        assertThat((f.sensorBits() >> 16) & 0xFFL).isEqualTo(64L);
        assertThat((f.sensorBits() >> 24) & 0xFFL).isEqualTo(100L);
    }

    @Test
    void shouldEncodeKindIntoLowBits() {
        MinecraftBotSensor s = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
        s.enqueue(new MinecraftBotSensor.BotEvent(
                MinecraftBotSensor.BotEvent.Kind.DAMAGE_TAKEN,
                0, 0, 0, 0.3, "zombie hit"));
        SensorFrame f = s.read();
        assertThat(f.sensorBits() & 0xFL).isEqualTo(2L);
    }

    @Test
    void shouldBucketHealthCorrectly() {
        assertThat(MinecraftBotSensor.healthBucket(1.0)).isEqualTo(0);
        assertThat(MinecraftBotSensor.healthBucket(0.95)).isEqualTo(0);
        assertThat(MinecraftBotSensor.healthBucket(0.50)).isEqualTo(2);
        assertThat(MinecraftBotSensor.healthBucket(0.30)).isEqualTo(3);
        assertThat(MinecraftBotSensor.healthBucket(0.10)).isEqualTo(4);
    }

    @Test
    void shouldEmitDisconnectedFrameWhenClientOffline() {
        MinecraftBotSensor s = new MinecraftBotSensor(MinecraftBotSensor.BotClient.neverConnected());
        SensorFrame f = s.read();
        assertThat(f).isNotEqualTo(SensorFrame.EMPTY);
        assertThat(s.isConnected()).isFalse();
    }

    @Test
    void shouldBeConnectedWhenClientReportsTrue() {
        MinecraftBotSensor s = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
        assertThat(s.isConnected()).isTrue();
    }

    @Test
    void shouldRejectNegativeHealth() {
        assertThatThrownBy(() ->
                new MinecraftBotSensor.BotEvent(
                        MinecraftBotSensor.BotEvent.Kind.MOVED,
                        0, 0, 0, -0.1, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
