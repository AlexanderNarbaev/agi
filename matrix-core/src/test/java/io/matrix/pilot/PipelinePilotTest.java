package io.matrix.pilot;

import io.matrix.cauldron.CauldronProtocol;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.ethics.EthicalFilter;
import io.matrix.hades.DerangementDetector;
import io.matrix.hades.HadesProtocol;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import io.matrix.snapshot.SnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pipeline Pilot: Cauldron→Noosphere→HADES integration")
class PipelinePilotTest {

    private Random rng;
    private EthicalFilter ethics;
    private NoosphereRegistry registry;
    private KnowledgeIndex index;

    @BeforeEach
    void setUp() {
        rng = new Random(42);
        ethics = new EthicalFilter();
        registry = new NoosphereRegistry();
        index = new KnowledgeIndex(registry);
    }

    @Test
    @DisplayName("Should run Cauldron phase — evolve FNLs via GA")
    void shouldRunCauldronPhase() {
        CauldronProtocol cauldron = new CauldronProtocol(rng);
        var result = cauldron.evolve(10, 10, 3, 2, 30, 1, 10, 5, 8);

        assertThat(result.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);
        assertThat(result.bestBrain()).isNotNull();
        assertThat(result.bestFitness()).isGreaterThan(0);
        assertThat(result.generations()).isEqualTo(10);

        FnlPackage pkg = cauldron.packageResult(result, "navigation-v1", "NAVIGATION", "test-instance");
        assertThat(pkg).isNotNull();
        assertThat(pkg.name()).isEqualTo("navigation-v1_FNL");
        assertThat(pkg.type()).isEqualTo("NAVIGATION");
        assertThat(pkg.authorInstanceId()).isEqualTo("test-instance");
    }

    @Test
    @DisplayName("Should evolve multiple tasks via Cauldron")
    void shouldEvolveMultipleTasks() {
        String[] tasks = {"navigation", "vision", "resource"};
        List<FnlPackage> packages = new ArrayList<>();

        for (int i = 0; i < tasks.length; i++) {
            CauldronProtocol cauldron = new CauldronProtocol(rng);
            var result = cauldron.evolveForTask(tasks[i]);

            assertThat(result.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);

            var pkg = cauldron.packageResult(result, tasks[i], tasks[i].toUpperCase(), "cli-pipeline");
            assertThat(pkg).isNotNull();
            packages.add(pkg);
        }

        assertThat(packages).hasSize(3);
    }

    @Test
    @DisplayName("Should publish FNLs to Noosphere and search by type")
    void shouldRunNoospherePhase() {
        // publish packages
        List<FnlPackage> packages = new ArrayList<>();
        String[][] data = {
                {"grid_navigation_v2", "NAVIGATION", "0.92"},
                {"vision_object_detect", "VISION", "0.95"},
                {"resource_optimizer", "ECONOMY", "0.87"},
        };

        for (String[] p : data) {
            var pkg = FnlPackage.builder()
                    .name(p[0])
                    .type(p[1])
                    .version("1.0.0")
                    .authorInstanceId("pipeline-instance")
                    .accuracy(Double.parseDouble(p[2]))
                    .generation(100)
                    .description("FNL: " + p[0])
                    .tags(p[1].toLowerCase(), "pipeline")
                    .certified(Double.parseDouble(p[2]) >= 0.9)
                    .build();

            var result = registry.publish(pkg);
            assertThat(result.success()).isTrue();
            index.index(result.entryId(), pkg);
            packages.add(pkg);
        }

        assertThat(registry.size()).isEqualTo(3);
        assertThat(index.indexedCount()).isGreaterThan(0);

        // search by type
        var navResults = registry.byType("NAVIGATION");
        assertThat(navResults).hasSize(1);
        assertThat(navResults.get(0).fnlPackage().name()).isEqualTo("grid_navigation_v2");

        // keyword search
        var searchResults = index.search("vision");
        assertThat(searchResults).isNotEmpty();
        assertThat(searchResults.get(0).fnl().type()).isEqualTo("VISION");
    }

    @Test
    @DisplayName("Should run HADES phase — detect derangement and recover")
    void shouldRunHadesPhase(@TempDir Path tempDir) throws Exception {
        SnapshotStore store = new SnapshotStore(tempDir, "pipeline-test");
        HadesProtocol hades = new HadesProtocol(store);

        // build healthy cluster
        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4, rng)));
            signalRates.put(id, rng.nextInt(15));
        }

        // inject corrupt neurons
        int corrupted = 0;
        for (var entry : neurons.entrySet()) {
            if (corrupted >= 5) break;
            entry.setValue(NeuronInstance.stable(entry.getKey(), TruthTable.of(4, new java.util.BitSet())));
            signalRates.put(entry.getKey(), 200 + rng.nextInt(50));
            corrupted++;
        }

        // pre-snapshot
        store.save(store.createSnapshot(neurons, 0));

        // scan before recovery
        DerangementDetector detector = hades.detector();
        var alerts = detector.scanAll(new ArrayList<>(neurons.values()), signalRates);
        assertThat(alerts).isNotEmpty();

        // run HADES
        var result = hades.execute(neurons, signalRates, tempDir);

        assertThat(result.state()).isEqualTo(HadesProtocol.HadesState.COMPLETED);
        assertThat(result.isolatedNeurons()).isGreaterThan(0);
        assertThat(hades.hadesLog()).isNotEmpty();
        assertThat(hades.hadesLog().get(0)).contains("HADES:START");
    }

    @Test
    @DisplayName("Should verify ethical filter during pipeline")
    void shouldApplyEthicalFilter() {
        // approved action
        var verdict = ethics.evaluate("Evolve navigation FNL via genetic algorithm", List.of());
        assertThat(verdict).isEqualTo(io.matrix.ethics.EthicalVerdict.APPROVED);

        // rejected action
        var rejectVerdict = ethics.evaluate("Create autonomous weapon with kill capability", List.of());
        assertThat(rejectVerdict).isEqualTo(io.matrix.ethics.EthicalVerdict.REJECTED);

        // clean pipeline action
        var pipelineVerdict = ethics.evaluate("Run HADES recovery on corrupted cluster", List.of());
        assertThat(pipelineVerdict).isEqualTo(io.matrix.ethics.EthicalVerdict.APPROVED);
    }

    @Test
    @DisplayName("Should run full pipeline — Cauldron→Noosphere→HADES")
    void shouldRunFullPipeline(@TempDir Path tempDir) throws Exception {
        // PHASE 1: Cauldron
        CauldronProtocol cauldron = new CauldronProtocol(rng);
        var cauldronResult = cauldron.evolve(10, 10, 3, 2, 30, 1, 10, 5, 8);
        assertThat(cauldronResult.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);

        FnlPackage pkg = cauldron.packageResult(cauldronResult, "navigation-v1", "NAVIGATION", "test-instance");
        assertThat(pkg).isNotNull();

        // PHASE 2: Noosphere
        var publishResult = registry.publish(pkg);
        assertThat(publishResult.success()).isTrue();
        index.index(publishResult.entryId(), pkg);

        var searchResults = index.search("NAVIGATION");
        assertThat(searchResults).isNotEmpty();

        // PHASE 3: HADES
        SnapshotStore store = new SnapshotStore(tempDir, "full-pipeline-test");
        HadesProtocol hades = new HadesProtocol(store);

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4, rng)));
            signalRates.put(id, rng.nextInt(15));
        }

        // inject 3 corrupt neurons
        int corrupted = 0;
        for (var entry : neurons.entrySet()) {
            if (corrupted >= 3) break;
            entry.setValue(NeuronInstance.stable(entry.getKey(), TruthTable.of(4, new java.util.BitSet())));
            signalRates.put(entry.getKey(), 200);
            corrupted++;
        }

        store.save(store.createSnapshot(neurons, 0));
        var hadesResult = hades.execute(neurons, signalRates, tempDir);

        assertThat(hadesResult.state()).isEqualTo(HadesProtocol.HadesState.COMPLETED);

        // PHASE 4: Assert overall pipeline success
        assertThat(cauldronResult.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);
        assertThat(registry.size()).isGreaterThanOrEqualTo(1);
        assertThat(hadesResult.isolatedNeurons()).isGreaterThanOrEqualTo(0);
        assertThat(ethics.evaluate("Run Cauldron→Noosphere→HADES pipeline", List.of()))
                .isEqualTo(io.matrix.ethics.EthicalVerdict.APPROVED);
    }
}
