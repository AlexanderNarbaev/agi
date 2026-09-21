package io.matrix.simulation;

import io.matrix.cluster.ClusterConfig;
import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronClusterActor.*;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.neuron.TruthTable;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClusterBrainTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() { testKit = ActorTestKit.create(); }

    @AfterAll
    static void teardown() { testKit.shutdownTestKit(); }

    @Test
    void shouldLoad1000NeuronsIntoCluster() {
        ClusterTopology topo = ClusterTopology.with1000Neurons(new Random(42));
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(1000), journal, "test-instance"));
        var probe = testKit.<Response>createTestProbe();

        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
        }

        actor.tell(new GetNeuronCount(probe.ref()));
        var resp = (CountResult) probe.receiveMessage();
        assertThat(resp.count()).isEqualTo(1000);
    }

    @Test
    void shouldLoad40NeuronTopology() {
        ClusterTopology topo = ClusterTopology.small(new Random(42));
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(40), journal, "test-instance"));
        var probe = testKit.<Response>createTestProbe();

        int loaded = 0;
        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            var resp = probe.receiveMessage();
            assertThat(resp).isInstanceOf(NeuronLoaded.class);
            loaded++;
        }

        assertThat(loaded).isEqualTo(40);

        actor.tell(new GetMetrics(probe.ref()));
        var metrics = (MetricsResult) probe.receiveMessage();
        assertThat(metrics.activeNeurons()).isEqualTo(40);
    }

    @Test
    void shouldInjectSensorSignals() {
        ClusterTopology topo = ClusterTopology.small(new Random(42));
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(40), journal, "test-instance"));
        var probe = testKit.<Response>createTestProbe();

        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
        }

        long sensorBits = 0b101010101010101010L;
        for (int i = 0; i < 18; i++) {
            if (((sensorBits >> i) & 1L) != 0) {
                actor.tell(new InjectSignal(new io.matrix.cluster.Signal(
                        topo.sensorIds().get(i), topo.sensorIds().get(i), true)));
            }
        }

        actor.tell(new GetMetrics(probe.ref()));
        var metrics = (MetricsResult) probe.receiveMessage();
        assertThat(metrics.activeNeurons()).isEqualTo(40);
        assertThat(metrics.eventsLogged()).isGreaterThanOrEqualTo(40);
    }

    @Test
    void shouldEvaluateTickOnCluster() {
        ClusterTopology topo = ClusterTopology.small(new Random(42));
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(40), journal, "test-instance"));
        var probe = testKit.<Response>createTestProbe();

        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
        }

        for (int i = 0; i < 18; i++) {
            actor.tell(new InjectSignal(new io.matrix.cluster.Signal(
                    topo.sensorIds().get(i), topo.sensorIds().get(i), true)));
        }

        actor.tell(new EvaluateTick(probe.ref()));
        var result = (TickResult) probe.receiveMessage();
        assertThat(result.evaluated()).isGreaterThanOrEqualTo(0);
        assertThat(result.signalsEmitted()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldCreateAndRestoreSnapshotForFullCluster(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        ClusterTopology topo = ClusterTopology.small(new Random(42));
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(40), journal, "inst"));
        var probe = testKit.<Response>createTestProbe();

        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
        }

        actor.tell(new CreateSnapshot(tempDir, probe.ref()));
        var snapResp = (SnapshotCreated) probe.receiveMessage();
        assertThat(snapResp.filePath()).exists();

        actor.tell(new RestoreSnapshot(tempDir, probe.ref()));
        var restoreResp = (SnapshotRestored) probe.receiveMessage();
        assertThat(restoreResp.neuronCount()).isEqualTo(40);
    }
}
