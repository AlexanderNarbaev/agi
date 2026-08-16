package io.matrix.events;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Batched Kafka event journal for high-throughput event publishing.
 * 
 * Collects events in a buffer and flushes in batches to reduce
 * Kafka producer overhead. Thread-safe via ReentrantLock.
 * 
 * @see <a href="docs/improvements/PERFORMANCE_OPTIMIZATION.md">Performance Plan</a>
 */
@ApplicationScoped
public class BatchKafkaJournal {

    private static final Logger log = LoggerFactory.getLogger(BatchKafkaJournal.class);

    @ConfigProperty(name = "matrix.kafka.batch.size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "matrix.kafka.batch.flush.ms", defaultValue = "1000")
    long flushIntervalMs;

    @ConfigProperty(name = "matrix.kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @ConfigProperty(name = "matrix.kafka.topic", defaultValue = "matrix-events")
    String topic;

    private final List<ClusterEvent> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private KafkaProducer<String, String> producer;
    private volatile boolean running = false;

    void onStart(@Observes StartupEvent ev) {
        running = true;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "1");
        props.put("linger.ms", "50");
        props.put("batch.size", "16384");
        producer = new KafkaProducer<>(props);
        log.info("BatchKafkaJournal started: topic={}, batchSize={}, flushMs={}", topic, batchSize, flushIntervalMs);
    }

    /**
     * Add event to buffer. Flushes if batch size reached.
     */
    public void publish(ClusterEvent event) {
        lock.lock();
        try {
            buffer.add(event);
            if (buffer.size() >= batchSize) {
                flushInternal();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Force flush all buffered events.
     */
    public void flush() {
        lock.lock();
        try {
            flushInternal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get buffer size.
     */
    public int bufferSize() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    private void flushInternal() {
        if (buffer.isEmpty() || producer == null) return;

        List<ClusterEvent> batch = new ArrayList<>(buffer);
        buffer.clear();

        for (ClusterEvent event : batch) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, event.instanceId(), event.toString());
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.warn("Kafka send failed: {}", exception.getMessage());
                    }
                });
            } catch (Exception e) {
                log.warn("Event publish failed: {}", e.getMessage());
            }
        }
        log.debug("Flushed {} events to Kafka", batch.size());
    }
}
