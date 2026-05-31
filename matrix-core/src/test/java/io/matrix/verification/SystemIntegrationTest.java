package io.matrix.verification;

import io.matrix.cluster.ClusterConfig;
import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronClusterActor.*;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.consensus.ConsensusEngine;
import io.matrix.consensus.ConsensusLevel;
import io.matrix.consensus.Proposal;
import io.matrix.consensus.Vote;
import io.matrix.dialog.ChatBot;
import io.matrix.dialog.ProactiveInterface;
import io.matrix.ethics.EthicalFilter;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.InstanceMediator;
import io.matrix.mediator.hierarchy.ClusterMediator;
import io.matrix.mediator.hierarchy.LobeMediator;
import io.matrix.mediator.hierarchy.MediatorLevel;
import io.matrix.mediator.scheduler.TaskScheduler;
import io.matrix.neuron.TruthTable;
import io.matrix.simulation.ClusterTopology;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2.5: End-to-end integration test exercising all major components.
 *
 * <p>Flow: Topology → Cluster loading → Mediator hierarchy → Consensus →
 * Snapshots → Chatbot → Ethical filter.
 */
class SystemIntegrationTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() { testKit = ActorTestKit.create(); }

    @AfterAll
    static void teardown() { testKit.shutdownTestKit(); }

    @Test
    void fullSystemIntegrationWithAllComponents(@TempDir Path tempDir) throws Exception {
        var rng = new Random(42);

        // Phase 0-1: Evolution + Cluster
        var topo = ClusterTopology.small(rng);
        var journal = new InMemoryEventJournal();
        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(40), journal, "sys-instance"));
        var probe = testKit.<Response>createTestProbe();

        int loaded = 0;
        for (var entry : topo.neuronTables().entrySet()) {
            actor.tell(new LoadNeuron(entry.getKey(), entry.getValue(),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
            loaded++;
        }
        assertThat(loaded).isEqualTo(40);

        // Phase 1.2: InstanceMediator
        var mediator = InstanceMediator.withDefaults(rng);
        mediator.energy().nudge(0.9);
        var actions = mediator.tick();
        assertThat(actions).isNotEmpty();

        // Phase 1.3: Snapshot
        var store = new SnapshotStore(tempDir, "sys-instance");
        actor.tell(new CreateSnapshot(tempDir, probe.ref()));
        var snapResp = (SnapshotCreated) probe.receiveMessage();
        assertThat(snapResp.snapshotId()).isNotEmpty();

        // Phase 2.1: Hierarchy
        LobeMediator lobe = new LobeMediator("lobe-1", "cluster-1");
        var clusterActor = testKit.spawn(ClusterMediator.create("cluster-1",
                "instance-1", rng));
        var clusterProbe = testKit.<ClusterMediator.Response>createTestProbe();
        clusterActor.tell(new ClusterMediator.Tick(clusterProbe.ref()));
        var tickResp = (ClusterMediator.TickResult) clusterProbe.receiveMessage();
        assertThat(tickResp.actionsPerformed()).isGreaterThanOrEqualTo(0);

        // Phase 2.1: Ethical filter
        EthicalFilter ethics = new EthicalFilter();
        var verdict = ethics.evaluate("help user learn", List.of());
        assertThat(verdict).isEqualTo(io.matrix.ethics.EthicalVerdict.APPROVED);

        // Phase 2.2: Scheduler
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        scheduler.enqueue(new io.matrix.mediator.Task(
                java.util.UUID.randomUUID(), DriverType.ENERGY, 0.5));
        var scheduled = scheduler.scheduleNext();
        assertThat(scheduled).isNotNull();

        // Phase 2.2: Chatbot
        ProactiveInterface pi = new ProactiveInterface();
        ChatBot chat = new ChatBot(ethics, pi);
        var userResponse = chat.respond("Hello, how are you?");
        assertThat(userResponse.content()).isNotEmpty();

        // Phase 2.3: Consensus
        ConsensusEngine engine = new ConsensusEngine();
        var propId = engine.propose(Proposal.create(ConsensusLevel.LEVEL_2,
                "node-1", "INTEGRATION_MUTATION", "test integration"));
        engine.castVote(Vote.approve(propId, "voter-1", 0.7));
        engine.castVote(Vote.approve(propId, "voter-2", 0.5));
        var decision = engine.evaluate(propId);
        assertThat(decision).isEqualTo(ConsensusEngine.Decision.APPROVED);
    }
}
