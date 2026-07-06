package io.matrix.events;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Creates required Kafka topics on startup.
 *
 * <p>Uses {@link AdminClient} to ensure topics exist before the journal starts
 * producing. If a topic already exists, the exception is swallowed.
 */
public final class KafkaTopics {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaTopics.class);

    private static final List<NewTopic> TOPICS = List.of(
            new NewTopic("neuron-events", 3, (short) 1),
            new NewTopic("cluster-snapshots", 1, (short) 1),
            new NewTopic("ethics-log", 1, (short) 1),
            new NewTopic("neuron-batch", 6, (short) 1)
    );

    private KafkaTopics() { /* utility */ }

    /**
     * Ensures all required topics exist on the broker.
     *
     * @param bootstrapServers Kafka bootstrap servers (e.g. "localhost:9092")
     */
    public static void ensureTopics(String bootstrapServers) {
        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000
        );

        try (var admin = AdminClient.create(config)) {
            var result = admin.createTopics(TOPICS);
            for (var entry : result.values().entrySet()) {
                try {
                    entry.getValue().get();
                    LOG.info("Created topic: {}", entry.getKey());
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TopicExistsException) {
                        LOG.debug("Topic already exists: {}", entry.getKey());
                    } else {
                        LOG.warn("Failed to create topic {}: {}", entry.getKey(), e.getCause().getMessage());
                    }
                }
            }
            LOG.info("Kafka topic initialization complete");
        } catch (Exception e) {
            LOG.warn("Could not connect to Kafka for topic creation: {}", e.getMessage());
        }
    }
}
