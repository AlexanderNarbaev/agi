package io.matrix.events;

import io.matrix.cluster.NeuronId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventJournalTest {

    @Test
    void shouldBeEmptyInitially() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");

        assertThat(journal.size()).isEqualTo(0);
        assertThat(journal.replayAll()).isEmpty();
        assertThat(journal.topic()).isEqualTo("test-topic");
    }

    @Test
    void shouldUseDefaultTopic() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("neuron-events");

        assertThat(journal.topic()).isEqualTo("neuron-events");
    }

    @Test
    void shouldAppendAndReplaySingleEvent() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");
        NeuronId id = NeuronId.create();

        long idx = journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED,
                "instance-1", id, "test-payload"));

        assertThat(idx).isEqualTo(0);
        assertThat(journal.size()).isEqualTo(1);

        List<ClusterEvent> events = journal.replayAll();
        assertThat(events).hasSize(1);
        ClusterEvent replayed = events.get(0);
        assertThat(replayed.type()).isEqualTo(ClusterEventType.NEURON_CREATED);
        assertThat(replayed.instanceId()).isEqualTo("instance-1");
        assertThat(replayed.neuronId()).isEqualTo(id);
    }

    @Test
    void shouldAppendAndReplayMultipleEvents() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");
        Random rng = new Random(42);

        for (int i = 0; i < 50; i++) {
            journal.append(ClusterEvent.of(ClusterEventType.SIGNAL_EMITTED,
                    "instance-" + (i % 3), NeuronId.create(), "payload-" + i));
        }

        assertThat(journal.size()).isEqualTo(50);
        assertThat(journal.replayAll()).hasSize(50);
    }

    @Test
    void shouldReplayFromIndex() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");
        NeuronId id = NeuronId.create();

        journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED, "i1", id, "e1"));
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_MUTATED, "i1", id, "e2"));
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_FROZEN, "i1", id, "e3"));
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_REMOVED, "i1", id, "e4"));

        var from2 = journal.replayFrom(2);
        assertThat(from2).hasSize(2);
        assertThat(from2.get(0).type()).isEqualTo(ClusterEventType.NEURON_FROZEN);
        assertThat(from2.get(1).type()).isEqualTo(ClusterEventType.NEURON_REMOVED);
    }

    @Test
    void shouldReplayFromOutOfBounds() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED,
                "i1", NeuronId.create(), "e1"));

        assertThat(journal.replayFrom(-1)).isEmpty();
        assertThat(journal.replayFrom(10)).isEmpty();
    }

    @Test
    void drainForPublishShouldReturnEmpty() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");

        for (int i = 0; i < 10; i++) {
            journal.append(ClusterEvent.of(ClusterEventType.SIGNAL_EMITTED,
                    "i1", NeuronId.create(), "s" + i));
        }

        List<byte[]> batch = journal.drainForPublish();
        assertThat(batch).isEmpty();
        assertThat(journal.size()).isEqualTo(10);
    }

    @Test
    void shouldHandleAllEventTypes() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");
        NeuronId id = NeuronId.create();

        for (ClusterEventType type : ClusterEventType.values()) {
            journal.append(ClusterEvent.of(type, "instance-1", id, "payload-" + type));
        }

        List<ClusterEvent> events = journal.replayAll();
        assertThat(events).hasSize(ClusterEventType.values().length);

        for (int i = 0; i < events.size(); i++) {
            assertThat(events.get(i).type()).isEqualTo(ClusterEventType.values()[i]);
            assertThat(events.get(i).neuronId()).isEqualTo(id);
            assertThat(events.get(i).instanceId()).isEqualTo("instance-1");
        }
    }

    @Test
    void shouldSerializeAndDeserializeCorrectly() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");
        NeuronId id = NeuronId.create();

        ClusterEvent original = ClusterEvent.of(ClusterEventType.SNAPSHOT_CREATED,
                "instance-42", id, "snapshot-001");
        journal.append(original);

        ClusterEvent replayed = journal.replayAll().get(0);
        assertThat(replayed.eventId()).isEqualTo(original.eventId());
        assertThat(replayed.type()).isEqualTo(original.type());
        assertThat(replayed.instanceId()).isEqualTo(original.instanceId());
        assertThat(replayed.neuronId()).isEqualTo(original.neuronId());
        assertThat(replayed.timestamp()).isEqualTo(original.timestamp());
        assertThat(replayed.payload()).isEqualTo(original.payload());
    }

    @Test
    void shouldSupportMultipleDrainCycles() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");

        journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED,
                "i1", NeuronId.create(), "batch1"));

        List<byte[]> batch1 = journal.drainForPublish();
        assertThat(batch1).isEmpty();

        journal.append(ClusterEvent.of(ClusterEventType.NEURON_MUTATED,
                "i1", NeuronId.create(), "batch2"));
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_FROZEN,
                "i1", NeuronId.create(), "batch2"));

        List<byte[]> batch2 = journal.drainForPublish();
        assertThat(batch2).isEmpty();
    }

    @Test
    void shouldMaintainEventOrder() {
        KafkaEventJournal journal = KafkaEventJournal.forTesting("test-topic");

        for (int i = 0; i < 100; i++) {
            journal.append(ClusterEvent.of(
                    i % 2 == 0 ? ClusterEventType.SIGNAL_EMITTED : ClusterEventType.BATCH_EVALUATED,
                    "i1", NeuronId.create(), "event-" + i));
        }

        List<ClusterEvent> events = journal.replayAll();
        assertThat(events).hasSize(100);

        for (int i = 0; i < 100; i++) {
            assertThat(events.get(i).payload()).isEqualTo("event-" + i);
        }
    }
}
