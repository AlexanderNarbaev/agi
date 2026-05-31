package io.matrix.hades;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.neuron.TruthTable;
import io.matrix.snapshot.SnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HadesProtocolTest {

    @Test
    void shouldExecuteCleanCheck(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");
        HadesProtocol hades = new HadesProtocol(store);

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        var id = NeuronId.create();
        neurons.put(id, NeuronInstance.stable(id, TruthTable.fromLong(2, 5)));

        var result = hades.execute(neurons, new HashMap<>(), tempDir);

        assertThat(result.state()).isEqualTo(HadesProtocol.HadesState.COMPLETED);
        assertThat(result.isolatedNeurons()).isEqualTo(0);
    }

    @Test
    void shouldDetectAndIsolateDerangedNeuron(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        var id = NeuronId.create();
        neurons.put(id, NeuronInstance.stable(id, TruthTable.fromLong(3, 0)));

        Map<NeuronId, Integer> rates = new HashMap<>();
        rates.put(id, 500);

        HadesProtocol hades = new HadesProtocol(store);
        var result = hades.execute(neurons, rates, tempDir);

        assertThat(result.isolatedNeurons()).isGreaterThan(0);
        assertThat(result.affectedNeuronIds()).contains(id);
    }

    @Test
    void shouldLogHadesActivity(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "instance-1");
        HadesProtocol hades = new HadesProtocol(store);

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        neurons.put(NeuronId.create(),
                NeuronInstance.stable(NeuronId.create(), TruthTable.random(2)));

        hades.execute(neurons, new HashMap<>(), tempDir);

        assertThat(hades.hadesLog()).isNotEmpty();
        assertThat(hades.hadesLog().get(0)).contains("HADES:START");
    }

    @Test
    void shouldAccessDetectorIndependently() {
        HadesProtocol hades = new HadesProtocol(
                new SnapshotStore(Path.of("/tmp"), "i1"));

        var detector = hades.detector();
        detector.checkNeuron(NeuronInstance.stable(NeuronId.create(),
                TruthTable.random(2)), 999);

        assertThat(detector.hasHighAlerts()).isTrue();
    }
}
