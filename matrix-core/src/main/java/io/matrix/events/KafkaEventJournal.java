package io.matrix.events;

import io.matrix.cluster.NeuronId;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Kafka-backed event journal with Avro serialization.
 *
 * <p>Publishes events to a Kafka topic via {@link KafkaProducer} and maintains
 * an in-memory bounded ring buffer (last 10_000 events) for fast replay
 * without a separate consumer group.
 *
 * <p>Ref: L6_Memory.md §3.2
 */
public final class KafkaEventJournal implements EventJournal, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaEventJournal.class);

    private static final String EVENT_SCHEMA_JSON = """
        {
          "type": "record",
          "name": "ClusterEvent",
          "namespace": "io.matrix.events",
          "fields": [
            {"name": "eventId", "type": "string"},
            {"name": "type", "type": "string"},
            {"name": "instanceId", "type": "string"},
            {"name": "neuronId", "type": "string"},
            {"name": "timestamp", "type": "long"},
            {"name": "payload", "type": "string"}
          ]
        }""";

    private static final Schema SCHEMA = new Schema.Parser().parse(EVENT_SCHEMA_JSON);

    private static final int MAX_REPLAY_CACHE = 10_000;

    private final String topic;
    private final KafkaProducer<String, byte[]> producer;
    private final LinkedList<byte[]> replayCache;
    private long offset;

    public KafkaEventJournal(String topic) {
        this(topic, resolveBootstrapServers());
    }

    public KafkaEventJournal(String topic, String bootstrapServers) {
        this.topic = topic;
        this.producer = createProducer(bootstrapServers);
        this.replayCache = new LinkedList<>();
        this.offset = 0;
        LOG.info("KafkaEventJournal initialized — topic={}, bootstrapServers={}", topic, bootstrapServers);
    }

    public KafkaEventJournal() {
        this("neuron-events");
    }

    /**
     * Creates a journal for testing — no Kafka producer, in-memory only.
     * Serialization/deserialization and replay logic are fully functional.
     */
    static KafkaEventJournal forTesting(String topic) {
        return new KafkaEventJournal(topic, null, false);
    }

    private KafkaEventJournal(String topic, String bootstrapServers, boolean ignored) {
        this.topic = topic;
        this.producer = (bootstrapServers != null) ? createProducer(bootstrapServers) : null;
        this.replayCache = new LinkedList<>();
        this.offset = 0;
        if (producer != null) {
            LOG.info("KafkaEventJournal initialized — topic={}, bootstrapServers={}", topic, bootstrapServers);
        } else {
            LOG.info("KafkaEventJournal initialized (no producer) — topic={}", topic);
        }
    }

    public String topic() {
        return topic;
    }

    @Override
    public long append(ClusterEvent event) {
        byte[] serialized = serialize(event);

        synchronized (replayCache) {
            replayCache.addLast(serialized);
            if (replayCache.size() > MAX_REPLAY_CACHE) {
                replayCache.removeFirst();
            }
        }

        if (producer != null) {
            var record = new ProducerRecord<>(topic, null, event.timestamp(),
                    event.eventId(), serialized);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    LOG.error("Failed to publish event {} to topic {}: {}",
                            event.eventId(), topic, exception.getMessage());
                } else {
                    LOG.trace("Published event {} to topic {} partition {} offset {}",
                            event.eventId(), metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        }

        long currentOffset = offset;
        offset++;
        return currentOffset;
    }

    @Override
    public List<ClusterEvent> replayFrom(long fromIndex) {
        if (fromIndex < 0) {
            return List.of();
        }
        synchronized (replayCache) {
            int size = replayCache.size();
            long cacheStart = offset - size;
            if (fromIndex >= offset) {
                return List.of();
            }
            long adjustedFrom = Math.max(fromIndex, cacheStart);
            int startIdx = (int) (adjustedFrom - cacheStart);
            if (startIdx >= size) {
                return List.of();
            }
            List<ClusterEvent> events = new ArrayList<>();
            for (int i = startIdx; i < size; i++) {
                events.add(deserialize(replayCache.get(i)));
            }
            return Collections.unmodifiableList(events);
        }
    }

    @Override
    public List<ClusterEvent> replayAll() {
        return replayFrom(0);
    }

    @Override
    public long size() {
        synchronized (replayCache) {
            return replayCache.size();
        }
    }

    /**
     * Drains all events as Avro-encoded byte arrays (for Kafka publishing).
     * With the real producer, events are already published on {@link #append},
     * so this is a no-op, kept for backward compatibility.
     */
    public List<byte[]> drainForPublish() {
        LOG.debug("drainForPublish called — events are already published via Kafka producer");
        return List.of();
    }

    @Override
    public void close() {
        if (producer != null) {
            LOG.info("Closing KafkaEventJournal for topic {}", topic);
            producer.flush();
            producer.close();
        }
    }

    // ─── private helpers ───

    private static String resolveBootstrapServers() {
        String servers = System.getProperty("kafka.bootstrap.servers");
        if (servers != null && !servers.isBlank()) {
            return servers;
        }
        servers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (servers != null && !servers.isBlank()) {
            return servers;
        }
        return "localhost:9092";
    }

    private static KafkaProducer<String, byte[]> createProducer(String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName(),
                ProducerConfig.ACKS_CONFIG, "1",
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy",
                ProducerConfig.BATCH_SIZE_CONFIG, 16384,
                ProducerConfig.LINGER_MS_CONFIG, 5
        );
        return new KafkaProducer<>(props);
    }

    // ─── serialization ───

    private byte[] serialize(ClusterEvent event) {
        try {
            GenericRecord record = new GenericData.Record(SCHEMA);
            record.put("eventId", event.eventId());
            record.put("type", event.type().name());
            record.put("instanceId", event.instanceId());
            record.put("neuronId", event.neuronId().toString());
            record.put("timestamp", event.timestamp());
            record.put("payload", event.payload());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
            new GenericDatumWriter<>(SCHEMA).write(record, encoder);
            encoder.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    private ClusterEvent deserialize(byte[] data) {
        try {
            BinaryDecoder decoder = DecoderFactory.get()
                    .binaryDecoder(new ByteArrayInputStream(data), null);
            GenericRecord record = new GenericDatumReader<GenericRecord>(SCHEMA)
                    .read(null, decoder);

            return new ClusterEvent(
                    record.get("eventId").toString(),
                    ClusterEventType.valueOf(record.get("type").toString()),
                    record.get("instanceId").toString(),
                    NeuronId.parse(record.get("neuronId").toString()),
                    (long) record.get("timestamp"),
                    record.get("payload").toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }
}
