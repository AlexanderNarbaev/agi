package io.matrix.privacy.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.privacy.Tombstone;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka tombstone storage — append-only audit topic.
 *
 * <p>Write path: tombstones are serialised to JSON and published to a
 * configurable Kafka topic (default {@code matrix.tombstones}). The topic
 * is treated as an immutable append-only log — tombstones are never
 * deleted from Kafka.
 *
 * <p>Read path: at startup the backend consumes the entire topic into an
 * in-memory map (keyed by {@code resourceType + ":" + resourceId}). This
 * map is kept up-to-date by a background consumer thread that polls every
 * {@link #consumerPollMs} for new records.
 *
 * <p>Caveats:
 * <ul>
 *   <li>Query latency is O(1) but bounded by consumer lag — typically
 *       near-realtime for small deployments.</li>
 *   <li>This backend assumes the Kafka cluster is already configured
 *       (Quarkus auto-injects {@code org.apache.kafka.kafka-clients}).</li>
 *   <li>For very large audit logs, switch to a partitioned topic with
 *       key = {@code resourceType}.</li>
 * </ul>
 *
 * <p>Pairs well with {@link PostgresTombstoneStorage} (Kafka for audit
 * stream, PG for queries) or {@link CompositeTombstoneStorage} for fan-out.
 */
public final class KafkaTombstoneStorage implements TombstoneStorage {

    private static final Logger log = LoggerFactory.getLogger(KafkaTombstoneStorage.class);

    private final String bootstrapServers;
    private final String topic;
    private final long consumerPollMs;
    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Tombstone> byKey = new ConcurrentHashMap<>();
    private final AtomicLong offsetPosition = new AtomicLong();
    private volatile boolean running = true;
    private final Thread consumerThread;

    public KafkaTombstoneStorage(String bootstrapServers, String topic) {
        this(bootstrapServers, topic, 1000L);
    }

    public KafkaTombstoneStorage(String bootstrapServers, String topic, long consumerPollMs) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.consumerPollMs = consumerPollMs;
        this.producer = new KafkaProducer<>(producerProps());
        this.consumer = new KafkaConsumer<>(consumerProps());
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "kafka-tombstone");
            t.setDaemon(true);
            return t;
        });
        this.consumerThread = new Thread(this::consumeLoop, "kafka-tombstone-consumer");
        this.consumerThread.setDaemon(true);
        this.consumerThread.start();
    }

    private Properties producerProps() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.RETRIES_CONFIG, 3);
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return p;
    }

    private Properties consumerProps() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "matrix-tombstone-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        return p;
    }

    private void consumeLoop() {
        try {
            consumer.subscribe(Collections.singletonList(topic));
            log.info("Kafka tombstone consumer subscribed to {}", topic);
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(consumerPollMs));
                if (records.isEmpty()) continue;
                records.forEach(r -> {
                    try {
                        Tombstone t = mapper.readValue(r.value(), Tombstone.class);
                        byKey.put(keyOf(t.resourceType(), t.resourceId()), t);
                        offsetPosition.set(r.offset());
                    } catch (Exception e) {
                        log.warn("Failed to deserialise tombstone at offset {}: {}",
                                r.offset(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            if (running) {
                log.error("Kafka tombstone consumer stopped unexpectedly", e);
            }
        }
    }

    @Override
    public CompletableFuture<Void> append(Tombstone tombstone) {
        return CompletableFuture.runAsync(() -> {
            try {
                String payload = mapper.writeValueAsString(tombstone);
                String key = keyOf(tombstone.resourceType(), tombstone.resourceId());
                producer.send(new ProducerRecord<>(topic, key, payload)).get();
                // Optimistically update the local map so reads are consistent
                // before the consumer thread catches up.
                byKey.put(key, tombstone);
            } catch (Exception e) {
                throw new StorageUnavailableException("Failed to publish tombstone to Kafka", e);
            }
        }, executor);
    }

    @Override
    public Optional<Tombstone> load(String resourceType, String resourceId) {
        return Optional.ofNullable(byKey.get(keyOf(resourceType, resourceId)));
    }

    @Override
    public List<Tombstone> all() {
        return byKey.values().stream()
                .sorted((a, b) -> b.deletedAt().compareTo(a.deletedAt()))
                .toList();
    }

    @Override
    public List<Tombstone> filterByReason(String reasonPrefix) {
        if (reasonPrefix == null || reasonPrefix.isEmpty()) return all();
        String prefix = reasonPrefix;
        return byKey.values().stream()
                .filter(t -> t.reason() != null && t.reason().startsWith(prefix))
                .sorted((a, b) -> b.deletedAt().compareTo(a.deletedAt()))
                .toList();
    }

    @Override
    public List<Tombstone> filterBySubject(String subjectId) {
        if (subjectId == null) return List.of();
        return byKey.values().stream()
                .filter(t -> subjectId.equals(t.subjectId()))
                .sorted((a, b) -> b.deletedAt().compareTo(a.deletedAt()))
                .toList();
    }

    @Override
    public boolean isHealthy() {
        try {
            producer.partitionsFor(topic);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String backendId() { return "kafka"; }

    public long lastConsumedOffset() { return offsetPosition.get(); }

    public int inMemorySize() { return byKey.size(); }

    /** Stops the consumer thread and closes producer/consumer. */
    public void close() {
        running = false;
        try { consumerThread.join(2000); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try { producer.close(); } catch (Exception ignored) {}
        try { consumer.close(); } catch (Exception ignored) {}
        executor.shutdownNow();
    }

    private static String keyOf(String type, String id) {
        return type + ":" + id;
    }
}
