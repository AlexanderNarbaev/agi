package io.matrix.pilot;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.hades.DerangementDetector;
import io.matrix.hades.HadesProtocol;
import io.matrix.neuron.TruthTable;
import io.matrix.snapshot.SnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pilot #6: HADES — self-healing after damage")
class HadesPilotTest {

    private Random rng;

    @BeforeEach
    void setUp() {
        rng = new Random(42);
    }

    private NeuronInstance makeNeuron(int k) {
        return NeuronInstance.stable(NeuronId.create(), TruthTable.random(k, rng));
    }

    private NeuronInstance makeCorruptNeuron(int k) {
        return NeuronInstance.stable(NeuronId.create(), TruthTable.of(k, new java.util.BitSet()));
    }

    @Test
    @DisplayName("Should check neuron truth table for corruption")
    void should_detectCorruptTruthTable() {
        DerangementDetector detector = new DerangementDetector();

        NeuronInstance healthy = makeNeuron(4);
        NeuronInstance corrupt = makeCorruptNeuron(4);

        assertThat(detector.checkTruthTable(healthy)).isNull();
        assertThat(detector.checkTruthTable(corrupt)).isNotNull();
        assertThat(detector.checkTruthTable(corrupt).severity())
                .isEqualTo(DerangementDetector.Severity.MEDIUM);
    }

    @Test
    @DisplayName("Should detect excessive signal rate")
    void should_detectExcessiveSignalRate() {
        DerangementDetector detector = new DerangementDetector();
        NeuronInstance neuron = makeNeuron(4);

        var alert = detector.checkNeuron(neuron, 200);

        assertThat(alert).isNotNull();
        assertThat(alert.severity()).isEqualTo(DerangementDetector.Severity.HIGH);
        assertThat(alert.description()).contains("Excessive signal rate");
    }

    @Test
    @DisplayName("Should not alert for normal signal rate")
    void should_notAlertNormalRate() {
        DerangementDetector detector = new DerangementDetector();
        NeuronInstance neuron = makeNeuron(4);

        assertThat(detector.checkNeuron(neuron, 10)).isNull();
    }

    @Test
    @DisplayName("Should scan cluster and find derangement")
    void should_scanCluster() {
        DerangementDetector detector = new DerangementDetector();
        NeuronInstance corrupt = makeCorruptNeuron(4);

        Map<NeuronId, Integer> signalRates = Map.of(corrupt.id(), 5);
        var alerts = detector.scanAll(List.of(corrupt), signalRates);

        assertThat(alerts).isNotEmpty();
    }

    @Test
    @DisplayName("Should execute HADES and return clean for healthy cluster")
    void should_returnCleanForHealthyCluster(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "test-instance");
        HadesProtocol hp = new HadesProtocol(store);

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            var n = makeNeuron(4);
            neurons.put(n.id(), n);
        }
        Map<NeuronId, Integer> rates = new HashMap<>();
        neurons.keySet().forEach(id -> rates.put(id, 5));

        var result = hp.execute(neurons, rates, tempDir);

        assertThat(result.state()).isEqualTo(HadesProtocol.HadesState.COMPLETED);
    }
}
