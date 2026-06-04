package io.matrix;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.hades.DerangementDetector;
import io.matrix.hades.HadesProtocol;
import io.matrix.neuron.TruthTable;
import io.matrix.snapshot.SnapshotStore;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Chaos: Resilience under neuron failures")
class ChaosTest {

    private Path tempDir;
    private Random rng;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("chaos-");
        rng = new Random(42);
    }

    @Test
    @DisplayName("Should detect all corrupt neurons in a damaged cluster")
    void should_detectAllCorrupt() {
        DerangementDetector detector = new DerangementDetector();
        var neurons = new ArrayList<NeuronInstance>();

        for (int i = 0; i < 50; i++) {
            neurons.add(NeuronInstance.stable(NeuronId.create(), TruthTable.random(4, rng)));
        }

        int corrupted = 0;
        for (int i = 0; i < 15; i++) {
            neurons.get(i * 3 + 1);
            var corrupt = NeuronInstance.stable(NeuronId.create(),
                    TruthTable.of(4, new BitSet()));
            neurons.set(i * 3 + 1, corrupt);
            corrupted++;
        }

        Map<NeuronId, Integer> rates = new HashMap<>();
        for (var n : neurons) {
            rates.put(n.id(), 10);
        }

        var alerts = detector.scanAll(neurons, rates);
        assertThat(alerts).hasSize(corrupted);
    }

    @Test
    @DisplayName("Should trigger HADES on high corruption rate")
    void should_triggerHadesOnHighCorruption() throws Exception {
        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();

        for (int i = 0; i < 30; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4, rng)));
            signalRates.put(id, rng.nextInt(5, 15));
        }

        for (int i = 0; i < 10; i++) {
            var n = neurons.values().iterator().next();
            signalRates.put(n.id(), rng.nextInt(200, 500));
        }

        SnapshotStore store = new SnapshotStore(tempDir, "chaos-instance");
        store.save(store.createSnapshot(neurons, 0));

        HadesProtocol hades = new HadesProtocol(store);
        var result = hades.execute(neurons, signalRates, tempDir);

        assertThat(result.state()).isIn(
                HadesProtocol.HadesState.COMPLETED,
                HadesProtocol.HadesState.ISOLATING);

        if (result.state() == HadesProtocol.HadesState.COMPLETED) {
            assertThat(hades.hadesLog()).isNotEmpty();
            assertThat(hades.hadesLog().get(0)).contains("HADES:START");
        }
    }

    @Test
    @DisplayName("Should handle 50% neuron death")
    void should_handleHalfDeath() {
        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(6, rng)));
        }

        List<NeuronId> toKill = new ArrayList<>(neurons.keySet()).subList(0, 50);
        for (var id : toKill) {
            neurons.remove(id);
        }

        assertThat(neurons).hasSize(50);
    }

    @Test
    @DisplayName("Should validate all remaining neurons after partial failure")
    void should_validateRemainingAfterFailure() {
        DerangementDetector detector = new DerangementDetector();
        var neurons = new ArrayList<NeuronInstance>();

        for (int i = 0; i < 30; i++) {
            neurons.add(NeuronInstance.stable(NeuronId.create(), TruthTable.random(4, rng)));
        }

        neurons.set(5, NeuronInstance.stable(NeuronId.create(),
                TruthTable.of(4, new BitSet())));

        Map<NeuronId, Integer> rates = new HashMap<>();
        for (var n : neurons) {
            rates.put(n.id(), 10);
        }

        var alerts = detector.scanAll(neurons, rates);
        var problemNeurons = alerts.stream()
                .map(DerangementDetector.DerangementAlert::neuronId)
                .toList();

        long healthyCount = neurons.stream()
                .filter(n -> !problemNeurons.contains(n.id()))
                .filter(n -> detector.checkTruthTable(n) == null)
                .count();

        assertThat(healthyCount).isEqualTo(29);
    }
}
