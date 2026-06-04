package io.matrix;

import io.matrix.cauldron.CauldronProtocol;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.consensus.ConsensusEngine;
import io.matrix.consensus.ConsensusLevel;
import io.matrix.consensus.Proposal;
import io.matrix.consensus.Vote;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.hades.HadesProtocol;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("E2E: Full MATRIX pipeline")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTest {

    private static final Random RNG = new Random(42);
    private static Path snapshotDir;
    private static String snapshotId;

    @BeforeAll
    static void setUp() throws Exception {
        snapshotDir = Files.createTempDirectory("matrix-e2e-");
    }

    @Test
    @Order(1)
    @DisplayName("Phase 1: Evolution — train neurons via GA")
    void phase1_evolution() {
        FitnessFn fitness = new FitnessFn(20, 20, 5, 10, 50, 3, new Random(42));
        EvolutionLoop loop = new EvolutionLoop(30, 20, 8, fitness, RNG);
        loop.run();

        var history = loop.bestFitnessHistory();
        assertThat(history).isNotEmpty();
        assertThat(history.get(history.size() - 1)).isGreaterThan(0);

        var brain = loop.bestBrain();
        assertThat(brain.nNeuron()).isNotNull();
        assertThat(brain.sNeuron()).isNotNull();
        assertThat(brain.wNeuron()).isNotNull();
        assertThat(brain.eNeuron()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Phase 2: Consensus — multi-node agreement")
    void phase2_consensus() {
        ConsensusEngine engine = new ConsensusEngine();
        UUID propId = engine.propose(Proposal.create(ConsensusLevel.LEVEL_2,
                "node-1", "INTEGRATION_MUTATION", "e2e-test"));

        engine.castVote(Vote.approve(propId, "voter-1", 0.9));
        engine.castVote(Vote.approve(propId, "voter-2", 0.8));

        var decision = engine.evaluate(propId);
        assertThat(decision).isEqualTo(ConsensusEngine.Decision.APPROVED);
    }

    @Test
    @Order(3)
    @DisplayName("Phase 3: EthicalFilter — block harmful actions")
    void phase3_ethics() {
        EthicalFilter ethics = new EthicalFilter();
        assertThat(ethics.evaluate("help users learn", List.of("education")))
                .isEqualTo(EthicalVerdict.APPROVED);
        assertThat(ethics.evaluate("how to kill someone", List.of()))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    @Order(4)
    @DisplayName("Phase 4: Cauldron — autonomous FNL generation")
    void phase4_cauldron() {
        CauldronProtocol cauldron = new CauldronProtocol(RNG);
        var result = cauldron.evolveForTask("navigation");
        assertThat(result.state()).isEqualTo(CauldronProtocol.CauldronState.COMPLETED);
        assertThat(result.bestFitness()).isGreaterThan(0);

        FnlPackage pkg = cauldron.packageResult(result, "navigation",
                "NAVIGATION", "e2e-instance");
        assertThat(pkg.name()).contains("navigation");
        assertThat(pkg.type()).isEqualTo("NAVIGATION");
        assertThat(pkg.accuracy()).isGreaterThan(0);
    }

    @Test
    @Order(5)
    @DisplayName("Phase 5: Noosphere — FNL exchange")
    void phase5_noosphere() {
        NoosphereRegistry registry = new NoosphereRegistry();
        KnowledgeIndex index = new KnowledgeIndex(registry);

        FnlPackage pkg = FnlPackage.builder()
                .name("e2e_vision")
                .type("VISION")
                .version("1.0.0")
                .authorInstanceId("e2e-instance")
                .accuracy(0.93)
                .generation(50)
                .description("E2E vision FNL")
                .tags("vision", "e2e")
                .certified(true)
                .build();

        var published = registry.publish(pkg);
        assertThat(published.success()).isTrue();
        index.index(published.entryId(), pkg);

        var results = index.search("vision");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).fnl().name()).isEqualTo("e2e_vision");
    }

    @Test
    @Order(6)
    @DisplayName("Phase 6: Snapshot — save and restore")
    void phase6_snapshot() throws Exception {
        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4, RNG)));
        }

        SnapshotStore store = new SnapshotStore(snapshotDir, "e2e-instance");
        var snapshot = store.createSnapshot(neurons, 42);
        Path saved = store.save(snapshot);
        snapshotId = snapshot.snapshotId();

        assertThat(saved).exists();
        assertThat(snapshot.neuronCount()).isEqualTo(10);

        var loaded = store.loadLatest();
        assertThat(loaded).isNotNull();
        assertThat(loaded.snapshotId()).isEqualTo(snapshotId);
        assertThat(loaded.neuronCount()).isEqualTo(10);

        var restored = store.restoreNeurons(loaded);
        assertThat(restored.size()).isEqualTo(10);
    }

    @Test
    @Order(7)
    @DisplayName("Phase 7: HADES — damage detection and recovery")
    void phase7_hades() throws Exception {
        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4, RNG)));
            signalRates.put(id, 10);
        }

        var corruptId = NeuronId.create();
        var corrupt = NeuronInstance.stable(corruptId,
                TruthTable.of(4, new BitSet()));
        neurons.put(corruptId, corrupt);
        signalRates.put(corruptId, 500);

        SnapshotStore store = new SnapshotStore(snapshotDir, "e2e-hades");
        store.save(store.createSnapshot(neurons, 0));

        HadesProtocol hades = new HadesProtocol(store);
        var result = hades.execute(neurons, signalRates, snapshotDir);

        assertThat(result.state()).isEqualTo(HadesProtocol.HadesState.COMPLETED);
        assertThat(result.isolatedNeurons()).isEqualTo(1);
        assertThat(hades.hadesLog()).isNotEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("Phase 8: MPDT — neuron inference correctness")
    void phase8_mpdt_inference() {
        TruthTable xor = TruthTable.of(2, BitSet.valueOf(new long[]{6}));
        assertThat(xor.evaluate(0b00)).isFalse();
        assertThat(xor.evaluate(0b01)).isTrue();
        assertThat(xor.evaluate(0b10)).isTrue();
        assertThat(xor.evaluate(0b11)).isFalse();

        TruthTable and = TruthTable.of(2, BitSet.valueOf(new long[]{8}));
        assertThat(and.evaluate(0b00)).isFalse();
        assertThat(and.evaluate(0b11)).isTrue();

        TruthTable large = TruthTable.random(12, RNG);
        assertThat(large.k()).isEqualTo(12);
        assertThat(large.evaluate(0)).isIn(true, false);
        assertThat(large.evaluate(4095)).isIn(true, false);
    }
}
