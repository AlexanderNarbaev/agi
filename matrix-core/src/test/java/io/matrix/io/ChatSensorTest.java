package io.matrix.io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSensorTest {

    @Test
    void shouldEncodeFirstCharIntoLowBits() {
        ChatSensor s = new ChatSensor();
        s.enqueue("Hello");
        SensorFrame f = s.read();
        assertThat(f.sourceId()).isEqualTo("chat");
        assertThat(f.payload()).isEqualTo(SensorFrame.Payload.CHAT_TEXT);
        // First char of "Hello" is 'H' = 0x48
        assertThat(f.sensorBits() & 0xFFL).isEqualTo((long) 'h');
    }

    @Test
    void shouldDetectQuestionMark() {
        ChatSensor s = new ChatSensor();
        s.enqueue("where is the library?");
        SensorFrame f = s.read();
        assertThat((f.sensorBits() >> 18) & 1L).isEqualTo(1L);
    }

    @Test
    void shouldDetectExclamation() {
        ChatSensor s = new ChatSensor();
        s.enqueue("stop!");
        SensorFrame f = s.read();
        assertThat((f.sensorBits() >> 19) & 1L).isEqualTo(1L);
    }

    @Test
    void shouldBucketLength() {
        assertThat(ChatSensor.lengthBucket(2)).isZero();
        assertThat(ChatSensor.lengthBucket(15)).isEqualTo(1);
        assertThat(ChatSensor.lengthBucket(60)).isEqualTo(2);
        assertThat(ChatSensor.lengthBucket(200)).isEqualTo(3);
        assertThat(ChatSensor.lengthBucket(1000)).isEqualTo(4);
    }

    @Test
    void shouldIgnoreNullOrBlank() {
        ChatSensor s = new ChatSensor();
        s.enqueue(null);
        s.enqueue("");
        s.enqueue("   ");
        assertThat(s.read()).isEqualTo(SensorFrame.EMPTY);
    }

    @Test
    void shouldClearInbox() {
        ChatSensor s = new ChatSensor();
        s.enqueue("foo");
        s.enqueue("bar");
        s.clear();
        assertThat(s.read()).isEqualTo(SensorFrame.EMPTY);
    }
}
