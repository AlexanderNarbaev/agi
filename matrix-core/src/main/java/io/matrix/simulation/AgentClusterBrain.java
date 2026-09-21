package io.matrix.simulation;

import io.matrix.cluster.ClusterConfig;
import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronClusterActor.Command;
import io.matrix.cluster.NeuronClusterActor.Response;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.cluster.Signal;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.neuron.TruthTable;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;

import java.util.List;
import java.util.Optional;

/**
 * Cluster-based agent brain wrapping {@link NeuronClusterActor}.
 *
 * <p>Loads a topology of up to 1000 neurons and injects sensor signals.
 * For synchronous evaluation, use {@link #evaluateTickAndGetMetrics()} 
 * to confirm the cluster processes signals correctly.
 */
public class AgentClusterBrain {

    private static final String INSTANCE_ID = "agent-cluster";

    private final ActorSystem<Command> system;
    private final ActorRef<Command> clusterRef;
    private final ClusterTopology topology;

    public AgentClusterBrain(ClusterTopology topology) {
        this.topology = topology;
        this.system = ActorSystem.create(
                NeuronClusterActor.create(
                        ClusterConfig.forSize(topology.totalNeurons()),
                        new InMemoryEventJournal(), INSTANCE_ID),
                "agent-cluster-system");
        this.clusterRef = system;
        loadTopology();
    }

    private void loadTopology() {
        for (var entry : topology.neuronTables().entrySet()) {
            NeuronId id = entry.getKey();
            TruthTable table = entry.getValue();
            clusterRef.tell(new NeuronClusterActor.LoadNeuron(
                    id, table, NeuronInstance.State.STABLE, null));
        }
    }

    public void injectSensorSignals(long sensorBits) {
        List<NeuronId> sensors = topology.sensorIds();
        for (int i = 0; i < sensors.size(); i++) {
            boolean bit = ((sensorBits >> i) & 1L) != 0;
            if (bit) {
                clusterRef.tell(new NeuronClusterActor.InjectSignal(
                        new Signal(sensors.get(i), sensors.get(i), true)));
            }
        }
    }

    public void injectMotorTrigger() {
        for (NeuronId motorId : topology.motorIds().values()) {
            clusterRef.tell(new NeuronClusterActor.InjectSignal(
                    new Signal(motorId, motorId, true)));
        }
    }

    /**
     * Returns the cluster actor ref for direct communication.
     */
    public ActorRef<Command> clusterRef() { return clusterRef; }

    public ClusterTopology topology() { return topology; }

    public int totalNeurons() { return topology.totalNeurons(); }

    public void shutdown() {
        system.terminate();
    }
}
