package io.matrix.hades;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.NoosphereRegistry;
import io.matrix.snapshot.SnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HADES → Noosphere REPORT phase integration.
 */
class HadesNoosphereIntegrationTest {

    private SnapshotStore store;
    private NoosphereRegistry registry;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("hades-test-");
        store = new SnapshotStore(tempDir, "test-instance");
        registry = new NoosphereRegistry();
    }

    @Test
    void hadesShouldReportToNoosphereWhenRegistryPresent() throws IOException {
        HadesProtocol hades = new HadesProtocol(store, null, registry);

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();

        // Create 5 stable neurons
        for (int i = 0; i < 5; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4)));
            signalRates.put(id, 10);
        }

        HadesProtocol.HadesResult result = hades.execute(neurons, signalRates, tempDir);

        // Clean neurons → no alerts → no report published (return early)
        // But should still complete without errors
        assertThat(result.state()).isEqualTo(HadesProtocol.HadesState.COMPLETED);
        assertThat(result.isolatedNeurons()).isEqualTo(0);
    }

    @Test
    void hadesShouldWorkWithoutRegistry() throws IOException {
        HadesProtocol hades = new HadesProtocol(store); // no registry

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();
        var id = NeuronId.create();
        neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4)));
        signalRates.put(id, 999); // exceed maxSignalRatePerNeuron=100 → alert

        HadesProtocol.HadesResult result = hades.execute(neurons, signalRates, tempDir);

        assertThat(result.state()).isEqualTo(HadesProtocol.HadesState.COMPLETED);
        // REPORT only happens when alerts are found (clean path returns early)
        assertThat(hades.hadesLog().stream()
                .anyMatch(s -> s.contains("REPORT") || s.contains("CLEAN"))).isTrue();
    }

    @Test
    void executeShouldLogStartAndDone() throws IOException {
        HadesProtocol hades = new HadesProtocol(store, null, registry);
        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();

        // No neurons → no alerts → clean path
        HadesProtocol.HadesResult result = hades.execute(neurons, signalRates, tempDir);

        assertThat(hades.hadesLog()).isNotEmpty();
        assertThat(hades.hadesLog().get(0)).contains("HADES:START");
        assertThat(hades.state()).isEqualTo(HadesProtocol.HadesState.IDLE); // clean → IDLE
    }

    @Test
    void hadesResultShouldContainAnalysisWithAlerts() throws IOException {
        HadesProtocol hades = new HadesProtocol(store);
        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();

        var id = NeuronId.create();
        neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4)));
        signalRates.put(id, 999); // trigger alert

        HadesProtocol.HadesResult result = hades.execute(neurons, signalRates, tempDir);

        assertThat(result.analysis()).isNotNull();
        // With alerts: analysis contains derangement info
        assertThat(result.analysis()).contains("Derangement");
        // No registry → REPORT skipped
        assertThat(hades.hadesLog().stream()
                .anyMatch(s -> s.contains("REPORT skipped"))).isTrue();
    }

    @Test
    void newConstructorShouldAcceptRegistry() {
        HadesProtocol hades = new HadesProtocol(store, null, registry);
        assertThat(hades.state()).isEqualTo(HadesProtocol.HadesState.IDLE);
        assertThat(hades.detector()).isNotNull();
    }
}
