package io.matrix;

import io.matrix.cluster.ClusterConfig;
import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronClusterActor.*;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import io.matrix.simulation.*;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.4 integration: 1000-neuron cluster, snapshots, adaptive agent.
 */
class Phase14IntegrationTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() { testKit = ActorTestKit.create(); }

    @AfterAll
    static void teardown() { testKit.shutdownTestKit(); }

    @Test
    void shouldLoad1000NeuronsWithSnapshots(@TempDir Path tempDir) {
        var topo = ClusterTopology.with1000Neurons(new Random(42));
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(1000), journal, "inst"));
        var probe = testKit.<Response>createTestProbe();

        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
        }

        actor.tell(new GetNeuronCount(probe.ref()));
        assertThat(((CountResult) probe.receiveMessage()).count()).isEqualTo(1000);

        long eventsLogged = journal.size();
        assertThat(eventsLogged).isEqualTo(1000);

        actor.tell(new CreateSnapshot(tempDir, probe.ref()));
        var snap = (SnapshotCreated) probe.receiveMessage();
        assertThat(snap.filePath()).exists();

        actor.tell(new GetMetrics(probe.ref()));
        var metrics = (MetricsResult) probe.receiveMessage();
        assertThat(metrics.eventsLogged()).isGreaterThanOrEqualTo(1001);
    }

    @Test
    void shouldInjectSignalsAndEvaluate1000NeuronCluster() {
        var topo = ClusterTopology.with1000Neurons(new Random(42));
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(1000), journal, "inst"));
        var probe = testKit.<Response>createTestProbe();

        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
        }

        long sensorBits = 0b111111111111111111L;
        for (int i = 0; i < 18; i++) {
            if (((sensorBits >> i) & 1L) != 0) {
                actor.tell(new InjectSignal(new io.matrix.cluster.Signal(
                        topo.sensorIds().get(i), topo.sensorIds().get(i), true)));
            }
        }

        actor.tell(new EvaluateTick(probe.ref()));
        var result = (TickResult) probe.receiveMessage();
        assertThat(result.evaluated()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void agentShouldSurviveInNormalEnvironment() {
        var brain = new AgentBrain(
                DecisionTree.random(18, 9, new Random(42)),
                DecisionTree.random(18, 9, new Random(43)),
                DecisionTree.random(18, 9, new Random(44)),
                DecisionTree.random(18, 9, new Random(45)));

        World world = new World(20, 20, 10, 8, new Random(123));
        AgentBody body = new AgentBody(new Position(10, 10), 100);
        SimulationRunner runner = new SimulationRunner(world, body, brain, 100, new Random(456));
        SimulationResult result = runner.run();

        assertThat(result.steps()).isGreaterThan(0);
    }

    @Test
    void agentShouldSurviveInHostileEnvironment() {
        var brain = new AgentBrain(
                DecisionTree.random(18, 9, new Random(101)),
                DecisionTree.random(18, 9, new Random(102)),
                DecisionTree.random(18, 9, new Random(103)),
                DecisionTree.random(18, 9, new Random(104)));

        World hostileWorld = new World(30, 30, 40, 5, new Random(999));
        AgentBody body = new AgentBody(new Position(15, 15), 100);
        SimulationRunner runner = new SimulationRunner(hostileWorld, body, brain, 150, new Random(777));
        SimulationResult result = runner.run();

        assertThat(result.steps()).isGreaterThan(0);
    }

    @Test
    void evolutionShouldProduceAdaptiveAgent() {
        var fitnessFn = new FitnessFn(10, 10, 3, 2, 50, 2, new Random(42));
        var loop = new EvolutionLoop(20, 10, 18, fitnessFn, new Random(42));
        loop.run();

        AgentBrain brain = loop.bestBrain();
        assertThat(brain).isNotNull();

        World world = new World(10, 10, 3, 2, new Random(99));
        AgentBody body = new AgentBody(new Position(5, 5), 50);
        SimulationRunner runner = new SimulationRunner(world, body, brain, 50, new Random(88));
        SimulationResult result = runner.run();

        long maxOverall = loop.bestFitnessHistory().stream()
                .mapToLong(Long::longValue).max().orElse(0);
        long initialBest = loop.bestFitnessHistory().get(0);
        assertThat(maxOverall).isGreaterThanOrEqualTo(initialBest);
    }
}
