package io.matrix.events;

import io.matrix.cluster.NeuronId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventJournalTest {

    @Test
    void shouldBeEmptyInitially() {
        EventJournal journal = new InMemoryEventJournal();

        assertThat(journal.size()).isEqualTo(0);
        assertThat(journal.replayAll()).isEmpty();
    }

    @Test
    void shouldAppendAndReplay() {
        EventJournal journal = new InMemoryEventJournal();
        NeuronId id = NeuronId.create();

        journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED,
                "instance-1", id, "test-payload"));

        assertThat(journal.size()).isEqualTo(1);
        assertThat(journal.replayAll()).hasSize(1);
        assertThat(journal.replayAll().get(0).type())
                .isEqualTo(ClusterEventType.NEURON_CREATED);
    }

    @Test
    void shouldReplayFromIndex() {
        EventJournal journal = new InMemoryEventJournal();
        NeuronId id = NeuronId.create();

        journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED,
                "i1", id, "e1"));
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_FROZEN,
                "i1", id, "e2"));
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_REMOVED,
                "i1", id, "e3"));

        var from1 = journal.replayFrom(1);
        assertThat(from1).hasSize(2);
        assertThat(from1.get(0).type()).isEqualTo(ClusterEventType.NEURON_FROZEN);
        assertThat(from1.get(1).type()).isEqualTo(ClusterEventType.NEURON_REMOVED);
    }

    @Test
    void shouldReplayFromOutOfBounds() {
        EventJournal journal = new InMemoryEventJournal();
        journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED,
                "i1", NeuronId.create(), "e1"));

        assertThat(journal.replayFrom(-1)).isEmpty();
        assertThat(journal.replayFrom(10)).isEmpty();
    }

    @Test
    void shouldHandleMultipleEvents() {
        EventJournal journal = new InMemoryEventJournal();

        for (int i = 0; i < 100; i++) {
            journal.append(ClusterEvent.of(ClusterEventType.SIGNAL_EMITTED,
                    "i1", NeuronId.create(), "s" + i));
        }

        assertThat(journal.size()).isEqualTo(100);
        assertThat(journal.replayAll()).hasSize(100);
    }

    @Test
    void shouldRecordInstanceId() {
        EventJournal journal = new InMemoryEventJournal();
        NeuronId id = NeuronId.create();

        journal.append(ClusterEvent.of(ClusterEventType.NEURON_CREATED,
                "instance-42", id, "p"));

        var event = journal.replayAll().get(0);
        assertThat(event.instanceId()).isEqualTo("instance-42");
        assertThat(event.neuronId()).isEqualTo(id);
    }
}
