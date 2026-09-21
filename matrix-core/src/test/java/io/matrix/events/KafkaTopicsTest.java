package io.matrix.events;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit + integration tests for KafkaTopics.
 *
 * <p>Uses Testcontainers Redpanda (Kafka-compatible) for integration tests.
 */
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class KafkaTopicsTest {

    @BeforeAll
    static void verifyDockerAvailable() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker daemon not available — skipping Kafka integration tests");
    }

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
