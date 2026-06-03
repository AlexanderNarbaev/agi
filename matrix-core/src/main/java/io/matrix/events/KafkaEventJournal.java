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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Kafka-ready event journal with Avro serialization.
 *
 * <p>Phase 2 implementation: uses in-memory queue, but serializes
 * events in Avro binary format — ready for Kafka topic publishing.
 * Swap the queue for a Kafka producer/consumer pair when Kafka is deployed.
 *
 * <p>Ref: L6_Memory.md §3.2
 */
public final class KafkaEventJournal implements EventJournal {

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

    private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
    private final String topic;
    private long offset;

    public KafkaEventJournal(String topic) {
        this.topic = topic;
    }

    public KafkaEventJournal() {
        this("neuron-events");
    }

    public String topic() { return topic; }

    @Override
    public long append(ClusterEvent event) {
        byte[] serialized = serialize(event);
        queue.add(serialized);
        return offset++;
    }

    @Override
    public List<ClusterEvent> replayFrom(long fromIndex) {
        if (fromIndex < 0) {
            return List.of();
        }
        List<ClusterEvent> events = new ArrayList<>();
        long idx = 0;
        for (byte[] data : queue) {
            if (idx >= fromIndex) {
                events.add(deserialize(data));
            }
            idx++;
        }
        return Collections.unmodifiableList(events);
    }

    @Override
    public List<ClusterEvent> replayAll() {
        return replayFrom(0);
    }

    @Override
    public long size() {
        return queue.size();
    }

    /**
     * Drains all events as Avro-encoded byte arrays (for Kafka publishing).
     */
    public List<byte[]> drainForPublish() {
        List<byte[]> batch = new ArrayList<>();
        byte[] data;
        while ((data = queue.poll()) != null) {
            batch.add(data);
        }
        return batch;
    }

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
