package io.matrix.simulation;

import io.matrix.cluster.NeuronId;
import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds a layered neuron topology for the cluster-based agent brain.
 *
 * <p>Architecture:
 * <pre>
 * Sensor layer (18 neurons, one per sensor bit)
 *   → Hidden layer 1
 *   → Hidden layer 2
 *   → Motor layer (4 neurons: N, S, W, E)
 * </pre>
 *
 * <p>Total neurons: 18 + hidden1 + hidden2 + 4 = up to 1000.
 */
public final class ClusterTopology {

    public static final int SENSOR_BITS = 18;
    public static final int MOTOR_NEURONS = 4;

    private final int hidden1Size;
    private final int hidden2Size;

    private final List<NeuronId> sensorIds;
    private final List<NeuronId> hidden1Ids;
    private final List<NeuronId> hidden2Ids;
    private final Map<Direction, NeuronId> motorIds;

    private final Map<NeuronId, TruthTable> neuronTables;
    private final Map<NeuronId, List<NeuronId>> connections;

    public ClusterTopology(int hidden1Size, int hidden2Size, Random rng) {
        this.hidden1Size = hidden1Size;
        this.hidden2Size = hidden2Size;

        this.sensorIds = new ArrayList<>();
        this.hidden1Ids = new ArrayList<>();
        this.hidden2Ids = new ArrayList<>();
        this.motorIds = new HashMap<>();
        this.neuronTables = new HashMap<>();
        this.connections = new HashMap<>();

        build(rng);
    }

    /**
     * Creates the default 1000-neuron topology:
     * 18 sensory + 490 hidden1 + 488 hidden2 + 4 motor = 1000.
     */
    public static ClusterTopology with1000Neurons(Random rng) {
        return new ClusterTopology(490, 488, rng);
    }

    /**
     * Creates a small topology for testing: 18 + 10 + 8 + 4 = 40.
     */
    public static ClusterTopology small(Random rng) {
        return new ClusterTopology(10, 8, rng);
    }

    public int totalNeurons() {
        return SENSOR_BITS + hidden1Size + hidden2Size + MOTOR_NEURONS;
    }

    public List<NeuronId> sensorIds() { return List.copyOf(sensorIds); }
    public Map<Direction, NeuronId> motorIds() { return Map.copyOf(motorIds); }
    public Map<NeuronId, TruthTable> neuronTables() { return Map.copyOf(neuronTables); }
    public Map<NeuronId, List<NeuronId>> connections() { return Map.copyOf(connections); }

    public List<NeuronId> allNeuronIds() {
        List<NeuronId> all = new ArrayList<>();
        all.addAll(sensorIds);
        all.addAll(hidden1Ids);
        all.addAll(hidden2Ids);
        all.addAll(motorIds.values());
        return all;
    }

    private void build(Random rng) {
        createSensorNeurons(rng);
        createHiddenNeurons(hidden1Ids, hidden1Size, rng);
        createHiddenNeurons(hidden2Ids, hidden2Size, rng);
        createMotorNeurons(rng);
        wireLayers(rng);
    }

    private void createSensorNeurons(Random rng) {
        for (int i = 0; i < SENSOR_BITS; i++) {
            NeuronId id = NeuronId.create();
            sensorIds.add(id);
            neuronTables.put(id, TruthTable.random(1, rng));
            connections.put(id, new ArrayList<>());
        }
    }

    private void createHiddenNeurons(List<NeuronId> target, int count, Random rng) {
        for (int i = 0; i < count; i++) {
            NeuronId id = NeuronId.create();
            target.add(id);
            int k = 3 + rng.nextInt(5);
            neuronTables.put(id, TruthTable.random(k, rng));
            connections.put(id, new ArrayList<>());
        }
    }

    private void createMotorNeurons(Random rng) {
        for (Direction dir : List.of(Direction.N, Direction.S, Direction.W, Direction.E)) {
            NeuronId id = NeuronId.create();
            motorIds.put(dir, id);
            int k = 2 + rng.nextInt(4);
            neuronTables.put(id, TruthTable.random(k, rng));
            connections.put(id, new ArrayList<>());
        }
    }

    private void wireLayers(Random rng) {
        wireLayerToLayer(sensorIds, hidden1Ids, 3, rng);

        wireLayerToLayer(hidden1Ids, hidden2Ids, 4, rng);

        for (NeuronId motorId : motorIds.values()) {
            int fanIn = 3 + rng.nextInt(5);
            for (int i = 0; i < fanIn && i < hidden2Ids.size(); i++) {
                int src = rng.nextInt(hidden2Ids.size());
                connections.get(hidden2Ids.get(src)).add(motorId);
            }
        }
    }

    private void wireLayerToLayer(List<NeuronId> source, List<NeuronId> target,
                                   int fanInPerTarget, Random rng) {
        for (NeuronId tgt : target) {
            int actualFanIn = Math.min(fanInPerTarget + rng.nextInt(3), source.size());
            BitSet used = new BitSet(source.size());
            for (int f = 0; f < actualFanIn; f++) {
                int src;
                int attempts = 0;
                do {
                    src = rng.nextInt(source.size());
                    attempts++;
                } while (used.get(src) && attempts < 20);
                used.set(src);
                connections.get(source.get(src)).add(tgt);
            }
        }
    }
}
