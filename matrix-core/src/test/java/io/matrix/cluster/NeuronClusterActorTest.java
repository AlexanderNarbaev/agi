package io.matrix.cluster;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import io.matrix.cluster.NeuronClusterActor.*;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NeuronClusterActorTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    void shouldLoadNeuron() {
        var actor = testKit.spawn(NeuronClusterActor.create(ClusterConfig.defaults()));
        var probe = testKit.<Response>createTestProbe();
        NeuronId id = NeuronId.create();
        TruthTable table = TruthTable.random(3);

        actor.tell(new LoadNeuron(id, table, NeuronInstance.State.STABLE, probe.ref()));
        Response response = probe.receiveMessage();

        assertThat(response).isInstanceOf(NeuronLoaded.class);
        assertThat(((NeuronLoaded) response).id()).isEqualTo(id);
    }

    @Test
    void shouldRejectWhenFull() {
        var actor = testKit.spawn(NeuronClusterActor.create(
                new ClusterConfig(2, 100, 1)));
        var probe = testKit.<Response>createTestProbe();

        actor.tell(new LoadNeuron(NeuronId.create(), TruthTable.random(2),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new LoadNeuron(NeuronId.create(), TruthTable.random(2),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new LoadNeuron(NeuronId.create(), TruthTable.random(2),
                NeuronInstance.State.STABLE, probe.ref()));
        Response response = probe.receiveMessage();

        assertThat(response).isInstanceOf(ErrorResponse.class);
    }

    @Test
    void shouldFreezeNeuron() {
        var actor = testKit.spawn(NeuronClusterActor.create(ClusterConfig.defaults()));
        var probe = testKit.<Response>createTestProbe();
        NeuronId id = NeuronId.create();

        actor.tell(new LoadNeuron(id, TruthTable.random(3),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new FreezeNeuron(id, probe.ref()));
        Response response = probe.receiveMessage();

        assertThat(response).isInstanceOf(NeuronFrozen.class);
    }

    @Test
    void shouldRejectDoubleFreeze() {
        var actor = testKit.spawn(NeuronClusterActor.create(ClusterConfig.defaults()));
        var probe = testKit.<Response>createTestProbe();
        NeuronId id = NeuronId.create();

        actor.tell(new LoadNeuron(id, TruthTable.random(3),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new FreezeNeuron(id, probe.ref()));
        probe.receiveMessage();

        actor.tell(new FreezeNeuron(id, probe.ref()));
        Response response = probe.receiveMessage();

        assertThat(response).isInstanceOf(ErrorResponse.class);
    }

    @Test
    void shouldEvaluateTick() {
        var actor = testKit.spawn(NeuronClusterActor.create(ClusterConfig.defaults()));
        var probe = testKit.<Response>createTestProbe();
        NeuronId id = NeuronId.create();

        actor.tell(new LoadNeuron(id, TruthTable.random(3),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new EvaluateTick(probe.ref()));
        Response response = probe.receiveMessage();

        assertThat(response).isInstanceOf(TickResult.class);
    }

    @Test
    void shouldReturnMetrics() {
        var actor = testKit.spawn(NeuronClusterActor.create(ClusterConfig.defaults()));
        var probe = testKit.<Response>createTestProbe();

        actor.tell(new LoadNeuron(NeuronId.create(), TruthTable.random(2),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new GetMetrics(probe.ref()));
        Response response = probe.receiveMessage();

        assertThat(response).isInstanceOf(MetricsResult.class);
        MetricsResult metrics = (MetricsResult) response;
        assertThat(metrics.activeNeurons()).isEqualTo(1);
    }

    @Test
    void shouldCountNeurons() {
        var actor = testKit.spawn(NeuronClusterActor.create(ClusterConfig.defaults()));
        var probe = testKit.<Response>createTestProbe();

        actor.tell(new LoadNeuron(NeuronId.create(), TruthTable.random(2),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new LoadNeuron(NeuronId.create(), TruthTable.random(2),
                NeuronInstance.State.FROZEN, probe.ref()));
        probe.receiveMessage();

        actor.tell(new GetNeuronCount(probe.ref()));
        Response response = probe.receiveMessage();

        assertThat(response).isInstanceOf(CountResult.class);
        assertThat(((CountResult) response).count()).isEqualTo(2);
    }

    @Test
    void shouldCreateAndRestoreSnapshot(@TempDir Path tempDir) {
        var journal = new InMemoryEventJournal();
        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.defaults(), journal, "test-instance"));
        var probe = testKit.<Response>createTestProbe();
        NeuronId id = NeuronId.create();

        actor.tell(new LoadNeuron(id, TruthTable.random(3),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        actor.tell(new CreateSnapshot(tempDir, probe.ref()));
        Response snapResp = probe.receiveMessage();
        assertThat(snapResp).isInstanceOf(SnapshotCreated.class);
        SnapshotCreated created = (SnapshotCreated) snapResp;
        assertThat(created.snapshotId()).isNotEmpty();
        assertThat(created.filePath()).exists();

        actor.tell(new GetNeuronCount(probe.ref()));
        assertThat(((CountResult) probe.receiveMessage()).count()).isEqualTo(1);

        actor.tell(new UnloadNeuron(id, probe.ref()));
        probe.receiveMessage();

        actor.tell(new RestoreSnapshot(tempDir, probe.ref()));
        Response restoreResp = probe.receiveMessage();
        assertThat(restoreResp).isInstanceOf(SnapshotRestored.class);
        SnapshotRestored restored = (SnapshotRestored) restoreResp;
        assertThat(restored.neuronCount()).isEqualTo(1);

        actor.tell(new GetNeuronCount(probe.ref()));
        assertThat(((CountResult) probe.receiveMessage()).count()).isEqualTo(1);
    }

    @Test
    void shouldRecordEventsInJournal() {
        var journal = new InMemoryEventJournal();
        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.defaults(), journal, "test-instance"));
        var probe = testKit.<Response>createTestProbe();

        actor.tell(new LoadNeuron(NeuronId.create(), TruthTable.random(2),
                NeuronInstance.State.STABLE, probe.ref()));
        probe.receiveMessage();

        assertThat(journal.size()).isGreaterThanOrEqualTo(1);
        assertThat(journal.replayAll().get(0).instanceId()).isEqualTo("test-instance");
    }

    @Test
    void shouldRestoreNeuronStateCorrectly(@TempDir Path tempDir) {
        var journal = new InMemoryEventJournal();
        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.defaults(), journal, "inst"));
        var probe = testKit.<Response>createTestProbe();
        NeuronId id = NeuronId.create();

        actor.tell(new LoadNeuron(id, TruthTable.random(2),
                NeuronInstance.State.FROZEN, probe.ref()));
        probe.receiveMessage();

        actor.tell(new CreateSnapshot(tempDir, probe.ref()));
        probe.receiveMessage();

        actor.tell(new UnloadNeuron(id, probe.ref()));
        probe.receiveMessage();

        actor.tell(new RestoreSnapshot(tempDir, probe.ref()));
        Response response = probe.receiveMessage();
        assertThat(response).isInstanceOf(SnapshotRestored.class);

        actor.tell(new GetMetrics(probe.ref()));
        var metrics = (MetricsResult) probe.receiveMessage();
        assertThat(metrics.frozenNeurons()).isEqualTo(1);
    }
}
