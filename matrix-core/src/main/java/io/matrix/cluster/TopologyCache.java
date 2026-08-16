package io.matrix.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Caches neuron input/output topology for efficient signal routing.
 *
 * <p>Maintains two maps:
 * <ul>
 *   <li>{@code inputMap} — for each neuron, which neurons feed INTO it</li>
 *   <li>{@code outputMap} — for each neuron, which neurons it feeds INTO</li>
 * </ul>
 *
 * <p>Thread-safe via {@link ConcurrentHashMap} and {@link CopyOnWriteArrayList}.
 *
 * <p>Ref: L3_Neurocluster_Arch.md §2.2
 */
public class TopologyCache {

    private final Map<UUID, List<UUID>> inputMap = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> outputMap = new ConcurrentHashMap<>();

    /**
     * Registers a directional connection: {@code from} → {@code to}.
     *
     * @param from source neuron UUID
     * @param to   target neuron UUID
     */
    public void registerConnection(UUID from, UUID to) {
        inputMap.computeIfAbsent(to, k -> new CopyOnWriteArrayList<>()).add(from);
        outputMap.computeIfAbsent(from, k -> new CopyOnWriteArrayList<>()).add(to);
    }

    /**
     * Returns the list of neurons that feed into {@code neuronId}.
     *
     * @return unmodifiable list, empty if neuron has no inputs
     */
    public List<UUID> getInputs(UUID neuronId) {
        return List.copyOf(inputMap.getOrDefault(neuronId, List.of()));
    }

    /**
     * Returns the list of neurons that {@code neuronId} feeds into.
     *
     * @return unmodifiable list, empty if neuron has no outputs
     */
    public List<UUID> getOutputs(UUID neuronId) {
        return List.copyOf(outputMap.getOrDefault(neuronId, List.of()));
    }

    /**
     * Removes a neuron and all its connections from both maps.
     *
     * @param neuronId the neuron to remove
     */
    public void removeNeuron(UUID neuronId) {
        inputMap.remove(neuronId);
        outputMap.remove(neuronId);

        inputMap.values().forEach(list -> list.remove(neuronId));
        outputMap.values().forEach(list -> list.remove(neuronId));

        inputMap.values().removeIf(List::isEmpty);
        outputMap.values().removeIf(List::isEmpty);
    }

    /**
     * Returns the total number of directed connections in the cache.
     */
    public int connectionCount() {
        return outputMap.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Clears all topology data.
     */
    public void clear() {
        inputMap.clear();
        outputMap.clear();
    }

    /**
     * Builds a batch routing plan by grouping signals by their target neuron.
     *
     * <p>Each signal is placed in the list keyed by its target neuron's UUID.
     * Signals targeting the same neuron end up in the same batch.
     *
     * @param signals the signals to partition
     * @return map of target UUID → list of signals destined for that target
     */
    public Map<UUID, List<Signal>> groupByTarget(List<Signal> signals) {
        Map<UUID, List<Signal>> grouped = new ConcurrentHashMap<>();
        for (Signal s : signals) {
            UUID target = s.targetId().uuid();
            grouped.computeIfAbsent(target, k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }
}
