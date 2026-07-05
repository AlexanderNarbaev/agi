package io.matrix.events;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit + integration tests for KafkaTopics.
 *
 * <p>Uses Testcontainers Redpanda (Kafka-compatible) for integration tests.
 */
@Testcontainers
class KafkaTopicsTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    @Test
    void shouldCreateTopicsAgainstRealKafka() {
        String bootstrapServers = KAFKA.getBootstrapServers();
        assertThatCode(() -> KafkaTopics.ensureTopics(bootstrapServers))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleInvalidServerGracefully() {
        assertThatCode(() -> KafkaTopics.ensureTopics("invalid-host:99999"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleEmptyServerGracefully() {
        assertThatCode(() -> KafkaTopics.ensureTopics(""))
                .doesNotThrowAnyException();
    }
}
