package io.matrix.mediator.hierarchy;

import io.matrix.cluster.NeuronId;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.DriverState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNL-level mediator managing local neuron optimization.
 *
 * <p>Weight 0.2. Operates within a single FNL, optimizes local neurons,
 * executes commands from ClusterMediator. Delegates escalation requests
 * to parent ClusterMediator.
 *
 * <p>Ref: L4_Mediator.md §2.1
 */
public class LobeMediator {

    private final String id;
    private final MediatorLevel level = MediatorLevel.LOBE;
    private final String parentClusterId;
    private final List<String> actionLog = new ArrayList<>();
    private long tickCount;

    public LobeMediator(String id, String parentClusterId) {
        this.id = id;
        this.parentClusterId = parentClusterId;
    }

    public String id() { return id; }
    public MediatorLevel level() { return level; }
    public String parentClusterId() { return parentClusterId; }
    public long tickCount() { return tickCount; }
    public List<String> actionLog() { return List.copyOf(actionLog); }

    /**
     * Performs one LobeMediator tick: local optimization, metrics collection.
     */
    public List<String> tick() {
        List<String> actions = new ArrayList<>();
        tickCount++;
        actions.add("LOBE:" + id + ":tick=" + tickCount);
        actionLog.addAll(actions);
        return actions;
    }

    /**
     * Requests additional resources from parent ClusterMediator.
     */
    public MediatorMessage.Command requestResources(String reason) {
        return new MediatorMessage.Command(
                java.util.UUID.randomUUID(),
                level, id,
                MediatorLevel.CLUSTER, parentClusterId,
                "REQUEST_RESOURCES", reason);
    }

    /**
     * Reports a local neuron mutation to parent.
     */
    public MediatorMessage.Command reportMutation(NeuronId neuronId, String details) {
        return new MediatorMessage.Command(
                java.util.UUID.randomUUID(),
                level, id,
                MediatorLevel.CLUSTER, parentClusterId,
                "REPORT_MUTATION", neuronId.toString() + ":" + details);
    }
}
