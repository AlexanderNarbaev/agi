package io.matrix.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for KafkaTopics — topic creation utility.
 *
 * <p>Tests that require a running Kafka broker are disabled by default.
 * Enable them when Kafka (Redpanda) is running on localhost:9092.
 */
class KafkaTopicsTest {

    @Test
    @Disabled("Requires running Kafka broker — use: docker compose up -d kafka")
    void ensureTopicsShouldNotThrowWithValidBootstrapServer() {
        assertThatCode(() -> KafkaTopics.ensureTopics("localhost:9092"))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureTopicsShouldHandleInvalidServerGracefully() {
        assertThatCode(() -> KafkaTopics.ensureTopics("invalid-host:99999"))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureTopicsShouldHandleNullServerGracefully() {
        assertThatCode(() -> KafkaTopics.ensureTopics(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ensureTopicsShouldNotThrowWithEmptyServer() {
        assertThatCode(() -> KafkaTopics.ensureTopics(""))
                .doesNotThrowAnyException();
    }
}
