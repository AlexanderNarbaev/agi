package io.matrix.snapshot;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotStoreTest {

    @Test
    void shouldCreateSnapshotFromNeurons() {
        SnapshotStore store = new SnapshotStore(Path.of("/tmp"), "instance-1");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3);
        neurons.put(id, NeuronInstance.stable(id, table));

        ClusterSnapshot snapshot = store.createSnapshot(neurons, 42);

        assertThat(snapshot.instanceId()).isEqualTo("instance-1");
        assertThat(snapshot.neuronCount()).isEqualTo(1);
        assertThat(snapshot.eventIndex()).isEqualTo(42);
        assertThat(snapshot.neurons()).hasSize(1);
    }

    @Test
    void shouldSaveAndLoadSnapshot(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3);
        neurons.put(id, NeuronInstance.stable(id, table));

        ClusterSnapshot original = store.createSnapshot(neurons, 0);
        Path filePath = store.save(original);

        assertThat(filePath).exists();
        assertThat(filePath.toString()).endsWith(".ldn");

        ClusterSnapshot loaded = store.load(filePath);
        assertThat(loaded.snapshotId()).isEqualTo(original.snapshotId());
        assertThat(loaded.neuronCount()).isEqualTo(1);
        assertThat(loaded.neurons().get(0).neuronId())
                .isEqualTo(id.uuid().toString());
    }

    @Test
    void shouldLoadLatestSnapshot(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");

        Map<NeuronId, NeuronInstance> neurons1 = new HashMap<>();
        neurons1.put(NeuronId.create(), NeuronInstance.stable(
                NeuronId.create(), TruthTable.random(2)));
        store.save(store.createSnapshot(neurons1, 0));
        Thread.sleep(5);

        Map<NeuronId, NeuronInstance> neurons2 = new HashMap<>();
        neurons2.put(NeuronId.create(), NeuronInstance.stable(
                NeuronId.create(), TruthTable.random(3)));
        ClusterSnapshot second = store.createSnapshot(neurons2, 10);
        store.save(second);

        ClusterSnapshot latest = store.loadLatest();

        assertThat(latest).isNotNull();
        assertThat(latest.snapshotId()).isEqualTo(second.snapshotId());
        assertThat(latest.eventIndex()).isEqualTo(10);
    }

    @Test
    void shouldReturnNullWhenNoSnapshots(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");
        assertThat(store.loadLatest()).isNull();
    }

    @Test
    void shouldRestoreNeurons(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3);
        neurons.put(id, NeuronInstance.stable(id, table));

        ClusterSnapshot snapshot = store.createSnapshot(neurons, 0);
        List<NeuronInstance> restored = store.restoreNeurons(snapshot);

        assertThat(restored).hasSize(1);
        NeuronInstance restoredNeuron = restored.get(0);
        assertThat(restoredNeuron.id().uuid()).isEqualTo(id.uuid());
        assertThat(restoredNeuron.id().generation()).isEqualTo(id.generation());
        assertThat(restoredNeuron.k()).isEqualTo(3);
        assertThat(restoredNeuron.state()).isEqualTo(NeuronInstance.State.STABLE);
    }

    @Test
    void shouldRestoreFrozenNeuron(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(2);
        neurons.put(id, NeuronInstance.frozen(id, table));

        ClusterSnapshot snapshot = store.createSnapshot(neurons, 0);
        List<NeuronInstance> restored = store.restoreNeurons(snapshot);

        assertThat(restored).hasSize(1);
        assertThat(restored.get(0).isFrozen()).isTrue();
    }

    @Test
    void shouldListSnapshots(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        neurons.put(NeuronId.create(), NeuronInstance.stable(
                NeuronId.create(), TruthTable.random(2)));

        store.save(store.createSnapshot(neurons, 0));
        store.save(store.createSnapshot(neurons, 0));

        List<Path> snapshots = store.listSnapshots();
        assertThat(snapshots).hasSize(2);
    }

    @Test
    void neuronRecordShouldPreserveTruthTableBits(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "i1");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3);
        neurons.put(id, NeuronInstance.stable(id, table));

        ClusterSnapshot snapshot = store.createSnapshot(neurons, 0);
        List<NeuronInstance> restored = store.restoreNeurons(snapshot);

        for (int i = 0; i < (1 << 3); i++) {
            assertThat(restored.get(0).truthTable().evaluate(i))
                    .isEqualTo(table.evaluate(i));
        }
    }
}
