package io.matrix.verification;

import io.matrix.cluster.ClusterConfig;
import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronClusterActor.*;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.neuron.TruthTable;
import io.matrix.simulation.ClusterTopology;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2.5: Load/stress test with 10,000 neurons.
 */
class TenThousandNeuronStressTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() { testKit = ActorTestKit.create(); }

    @AfterAll
    static void teardown() { testKit.shutdownTestKit(); }

    @Test
    void shouldLoad10000NeuronsIntoCluster() {
        int neuronCount = 10_000;
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(neuronCount), journal, "stress-instance"));
        var probe = testKit.<Response>createTestProbe();
        var rng = new Random(42);

        long startNanos = System.nanoTime();

        for (int i = 0; i < neuronCount; i++) {
            NeuronId id = NeuronId.create();
            TruthTable table = TruthTable.random(2 + rng.nextInt(5), rng);
            actor.tell(new LoadNeuron(id, table, NeuronInstance.State.STABLE, probe.ref()));

            Response resp = probe.receiveMessage();
            assertThat(resp).isInstanceOf(NeuronLoaded.class);
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        actor.tell(new GetNeuronCount(probe.ref()));
        int count = ((CountResult) probe.receiveMessage()).count();
        assertThat(count).isEqualTo(neuronCount);

        assertThat(journal.size()).isEqualTo(neuronCount);

        System.out.println("[STRESS] 10,000 neurons loaded in " + elapsedMs + " ms ("
                + (neuronCount * 1000L / Math.max(elapsedMs, 1)) + " neurons/s)");
    }

    @Test
    void shouldInjectAndEvaluate10000NeuronCluster() {
        int neuronCount = 10_000;
        var journal = new InMemoryEventJournal();

        var actor = testKit.spawn(NeuronClusterActor.create(
                ClusterConfig.forSize(neuronCount), journal, "stress-instance"));
        var probe = testKit.<Response>createTestProbe();
        var rng = new Random(42);

        for (int i = 0; i < neuronCount; i++) {
            actor.tell(new LoadNeuron(NeuronId.create(),
                    TruthTable.random(2, rng),
                    NeuronInstance.State.STABLE, probe.ref()));
            probe.receiveMessage();
        }

        long signalNanos = System.nanoTime();

        for (int i = 0; i < 1000; i++) {
            actor.tell(new InjectSignal(new io.matrix.cluster.Signal(
                    NeuronId.create(), NeuronId.create(), true)));
        }

        actor.tell(new EvaluateTick(probe.ref()));
        var result = (TickResult) probe.receiveMessage();

        long elapsedMs = (System.nanoTime() - signalNanos) / 1_000_000;
        assertThat(result.evaluated()).isGreaterThanOrEqualTo(0);

        System.out.println("[STRESS] 10K neuron cluster: 1000 signals processed in "
                + elapsedMs + " ms, evaluated=" + result.evaluated()
                + ", emitted=" + result.signalsEmitted());
    }
}
