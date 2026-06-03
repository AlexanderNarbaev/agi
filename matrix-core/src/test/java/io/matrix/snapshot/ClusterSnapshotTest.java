package io.matrix.snapshot;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterSnapshotTest {

    @Test
    void shouldCreateEmptySnapshot() {
        ClusterSnapshot snapshot = new ClusterSnapshot();

        assertThat(snapshot.snapshotId()).isEmpty();
        assertThat(snapshot.instanceId()).isEmpty();
        assertThat(snapshot.createdAt()).isZero();
        assertThat(snapshot.eventIndex()).isZero();
        assertThat(snapshot.neuronCount()).isZero();
        assertThat(snapshot.neurons()).isEmpty();
    }

    @Test
    void shouldCreateSnapshotWithTimestamp() {
        long now = System.currentTimeMillis();
        ClusterSnapshot snapshot = new ClusterSnapshot(
                "snap-001", "instance-1", now, 42L, 0, List.of());

        assertThat(snapshot.snapshotId()).isEqualTo("snap-001");
        assertThat(snapshot.instanceId()).isEqualTo("instance-1");
        assertThat(snapshot.createdAt()).isEqualTo(now);
        assertThat(snapshot.eventIndex()).isEqualTo(42L);
    }

    @Test
    void shouldFormatCreatedAtIso() {
        long now = System.currentTimeMillis();
        ClusterSnapshot snapshot = new ClusterSnapshot(
                "snap-001", "i1", now, 0L, 0, List.of());

        String iso = snapshot.createdAtIso();
        assertThat(iso).isNotEmpty();
        assertThat(iso).contains("T");
        assertThat(iso).doesNotContain(":");
    }

    @Test
    void shouldCreateEmptyNeuronRecord() {
        ClusterSnapshot.NeuronRecord record = new ClusterSnapshot.NeuronRecord();

        assertThat(record.neuronId()).isEmpty();
        assertThat(record.generation()).isZero();
        assertThat(record.k()).isZero();
        assertThat(record.truthTableBits()).isEmpty();
        assertThat(record.state()).isEmpty();
    }

    @Test
    void shouldConvertNeuronInstanceToRecord() {
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3, new Random(42));
        NeuronInstance neuron = NeuronInstance.stable(id, table);

        ClusterSnapshot.NeuronRecord record = ClusterSnapshot.NeuronRecord.from(neuron);

        assertThat(record.neuronId()).isEqualTo(id.uuid().toString());
        assertThat(record.generation()).isEqualTo(id.generation());
        assertThat(record.k()).isEqualTo(3);
        assertThat(record.state()).isEqualTo("STABLE");
        assertThat(record.truthTableBits()).isNotEmpty();
    }

    @Test
    void shouldConvertFrozenNeuronToRecord() {
        NeuronId id = NeuronId.create().nextGeneration();
        TruthTable table = TruthTable.random(4, new Random(7));
        NeuronInstance neuron = NeuronInstance.frozen(id, table);

        ClusterSnapshot.NeuronRecord record = ClusterSnapshot.NeuronRecord.from(neuron);

        assertThat(record.state()).isEqualTo("FROZEN");
        assertThat(record.generation()).isEqualTo(1);
        assertThat(record.k()).isEqualTo(4);
    }

    @Test
    void shouldCreateSnapshotWithNeurons() {
        NeuronId id = NeuronId.create();
        NeuronInstance neuron = NeuronInstance.stable(id,
                TruthTable.random(2, new Random(42)));

        ClusterSnapshot.NeuronRecord record = ClusterSnapshot.NeuronRecord.from(neuron);

        ClusterSnapshot snapshot = new ClusterSnapshot(
                "snap-002", "instance-1",
                System.currentTimeMillis(), 100L,
                1, List.of(record));

        assertThat(snapshot.neuronCount()).isEqualTo(1);
        assertThat(snapshot.neurons()).hasSize(1);
        assertThat(snapshot.neurons().get(0).neuronId())
                .isEqualTo(id.uuid().toString());
    }

    @Test
    void shouldRoundtripNeuronThroughRecord() {
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(5, new Random(42));
        NeuronInstance neuron = NeuronInstance.stable(id, table);

        ClusterSnapshot.NeuronRecord record = ClusterSnapshot.NeuronRecord.from(neuron);

        assertThat(record.neuronId()).isEqualTo(id.uuid().toString());
        assertThat(record.generation()).isEqualTo(0);
        assertThat(record.k()).isEqualTo(5);
        assertThat(record.state()).isEqualTo("STABLE");
        assertThat(record.truthTableBits().length).isGreaterThan(0);
    }
}
