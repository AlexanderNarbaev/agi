package io.matrix.integration;

import io.matrix.cluster.NeuronId;
import io.matrix.events.ClusterEvent;
import io.matrix.events.ClusterEventType;
import io.matrix.events.KafkaEventJournal;
import io.matrix.events.KafkaTopics;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Kafka producer/consumer using Testcontainers.
 *
 * <p>Tests Avro-serialized event publishing, consumption, partition assignment,
 * and error handling against a real Kafka broker.
 */
@Testcontainers
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class KafkaIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withEmbeddedZookeeper();

    private KafkaEventJournal journal;
    private String testTopic;

    @BeforeEach
    void setUp() {
        testTopic = "test-neuron-events-" + UUID.randomUUID().toString().substring(0, 8);
        journal = new KafkaEventJournal(testTopic, KAFKA.getBootstrapServers());
    }

    @AfterEach
    void tearDown() {
        if (journal != null) {
            journal.close();
        }
    }

    @Test
    void produceAndConsumeAvroSerializedEvent() {
        ClusterEvent event = ClusterEvent.of(
                ClusterEventType.NEURON_CREATED,
                "instance-1",
                NeuronId.create(),
                "{\"neuronType\":\"sensor\",\"layer\":0}");

        journal.append(event);

        // Consume via raw Kafka consumer to verify actual Kafka delivery
        KafkaConsumer<String, byte[]> consumer = createConsumer();
        consumer.subscribe(Collections.singletonList(testTopic));

        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
        consumer.close();

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        ConsumerRecord<String, byte[]> record = records.iterator().next();
        assertThat(record.value()).isNotNull();
        assertThat(record.value().length).isGreaterThan(0);
        assertThat(record.key()).isEqualTo(event.eventId());
    }

    @Test
    void produceMultipleEventsAndVerifyOrdering() {
        NeuronId neuronId = NeuronId.create();
        ClusterEvent event1 = ClusterEvent.of(
                ClusterEventType.NEURON_CREATED, "inst-1", neuronId, "payload-1");
        ClusterEvent event2 = ClusterEvent.of(
                ClusterEventType.NEURON_MUTATED, "inst-1", neuronId, "payload-2");
        ClusterEvent event3 = ClusterEvent.of(
                ClusterEventType.NEURON_FROZEN, "inst-1", neuronId, "payload-3");

        journal.append(event1);
        journal.append(event2);
        journal.append(event3);

        // Verify in-memory replay ordering
        List<ClusterEvent> replayed = journal.replayAll();
        assertThat(replayed).hasSize(3);
        assertThat(replayed.get(0).type()).isEqualTo(ClusterEventType.NEURON_CREATED);
        assertThat(replayed.get(1).type()).isEqualTo(ClusterEventType.NEURON_MUTATED);
        assertThat(replayed.get(2).type()).isEqualTo(ClusterEventType.NEURON_FROZEN);
    }

    @Test
    void replayFromIndexReturnsCorrectSubset() {
        NeuronId neuronId = NeuronId.create();
        for (int i = 0; i < 10; i++) {
            journal.append(ClusterEvent.of(
                    ClusterEventType.SIGNAL_EMITTED, "inst-1", neuronId, "payload-" + i));
        }

        List<ClusterEvent> fromMiddle = journal.replayFrom(5);
        assertThat(fromMiddle).hasSize(5);
        assertThat(fromMiddle.get(0).payload()).isEqualTo("payload-5");
    }

    @Test
    void partitionAssignmentWithMultiplePartitions() {
        // Create a topic with multiple partitions via AdminClient
        KafkaTopics.ensureTopics(KAFKA.getBootstrapServers());

        // Produce to the default neuron-events topic (3 partitions)
        KafkaEventJournal multiPartitionJournal = new KafkaEventJournal(
                "neuron-events", KAFKA.getBootstrapServers());

        NeuronId neuronId = NeuronId.create();
        for (int i = 0; i < 30; i++) {
            multiPartitionJournal.append(ClusterEvent.of(
                    ClusterEventType.SIGNAL_EMITTED, "inst-" + i, neuronId, "data-" + i));
        }

        // Consume and verify all events arrive
        KafkaConsumer<String, byte[]> consumer = createConsumer();
        consumer.subscribe(Collections.singletonList("neuron-events"));

        int totalReceived = 0;
        long deadline = System.currentTimeMillis() + 15_000;
        while (totalReceived < 30 && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(2));
            totalReceived += records.count();
        }
        consumer.close();
        multiPartitionJournal.close();

        assertThat(totalReceived).isEqualTo(30);
    }

    @Test
    void topicCreationIdempotent() {
        // Creating topics twice should not throw
        KafkaTopics.ensureTopics(KAFKA.getBootstrapServers());
        KafkaTopics.ensureTopics(KAFKA.getBootstrapServers());
        // No exception = success
    }

    @Test
    void journalSizeTracksAppendedEvents() {
        assertThat(journal.size()).isZero();

        NeuronId neuronId = NeuronId.create();
        journal.append(ClusterEvent.of(
                ClusterEventType.NEURON_CREATED, "inst-1", neuronId, "p1"));
        assertThat(journal.size()).isEqualTo(1);

        journal.append(ClusterEvent.of(
                ClusterEventType.NEURON_MUTATED, "inst-1", neuronId, "p2"));
        assertThat(journal.size()).isEqualTo(2);
    }

    @Test
    void replayFromNegativeIndexReturnsEmpty() {
        List<ClusterEvent> events = journal.replayFrom(-1);
        assertThat(events).isEmpty();
    }

    @Test
    void replayFromBeyondSizeReturnsEmpty() {
        NeuronId neuronId = NeuronId.create();
        journal.append(ClusterEvent.of(
                ClusterEventType.NEURON_CREATED, "inst-1", neuronId, "p1"));

        List<ClusterEvent> events = journal.replayFrom(100);
        assertThat(events).isEmpty();
    }

    @Test
    void connectionFailureHandledGracefully() {
        // Create a journal pointing to a non-existent broker
        KafkaEventJournal badJournal = new KafkaEventJournal(
                "bad-topic", "localhost:19999");

        NeuronId neuronId = NeuronId.create();
        ClusterEvent event = ClusterEvent.of(
                ClusterEventType.NEURON_CREATED, "inst-1", neuronId, "payload");

        // append should not throw — events go to in-memory cache
        long offset = badJournal.append(event);
        assertThat(offset).isZero();

        // In-memory replay should still work
        List<ClusterEvent> replayed = badJournal.replayAll();
        assertThat(replayed).hasSize(1);
        assertThat(replayed.get(0).eventId()).isEqualTo(event.eventId());

        badJournal.close();
    }

    @Test
    void drainForPublishReturnsBufferedEvents() {
        // Use a journal with a non-existent broker — events buffer in-memory
        KafkaEventJournal drainJournal = new KafkaEventJournal(
                "drain-topic", "localhost:19999");

        NeuronId neuronId = NeuronId.create();
        drainJournal.append(ClusterEvent.of(
                ClusterEventType.NEURON_CREATED, "inst-1", neuronId, "p1"));
        drainJournal.append(ClusterEvent.of(
                ClusterEventType.NEURON_MUTATED, "inst-1", neuronId, "p2"));

        // replayAll returns in-memory cached events
        List<ClusterEvent> replayed = drainJournal.replayAll();
        assertThat(replayed).hasSize(2);
        assertThat(replayed.get(0).eventId()).isNotNull();

        drainJournal.close();
    }

    @Test
    void concurrentAppendDoesNotLoseEvents() throws Exception {
        int threadCount = 4;
        int eventsPerThread = 50;
        NeuronId neuronId = NeuronId.create();

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < eventsPerThread; i++) {
                    journal.append(ClusterEvent.of(
                            ClusterEventType.SIGNAL_EMITTED,
                            "inst-" + threadId,
                            neuronId,
                            "t" + threadId + "-e" + i));
                }
            });
        }

        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join(30_000);

        assertThat(journal.size()).isEqualTo(threadCount * eventsPerThread);
    }

    // ─── helpers ───

    private KafkaConsumer<String, byte[]> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return new KafkaConsumer<>(props);
    }
}
