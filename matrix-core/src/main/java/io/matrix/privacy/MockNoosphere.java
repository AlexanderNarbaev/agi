package io.matrix.privacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock of the MATRIX Noosphere — simulates {@code NoosphereRegistry}
 * for cascade-erasure integration tests.
 *
 * <p>Models the dependency graph that {@link CascadeRegistrar} expects:
 * <ul>
 *   <li>{@code Agent} → neurons (List&lt;String&gt;)</li>
 *   <li>{@code Agent} → snapshots</li>
 *   <li>{@code Neuron} → truth table</li>
 *   <li>{@code FnlPackage} → knowledge-index entries</li>
 * </ul>
 *
 * <p>Provide this mock to {@link CascadeRegistrar#registerAll} in lieu
 * of a real {@code NoosphereRegistry} for unit tests, integration tests,
 * and local dev. The mock is thread-safe and supports concurrent reads/writes.
 *
 * <p>Ref: L6 §6.7 (GDPR cascade), L12 §4 (legal framework).
 */
public final class MockNoosphere {

    private final Map<String, List<String>> neuronsByAgent = new ConcurrentHashMap<>();
    private final Map<String, List<String>> snapshotsByAgent = new ConcurrentHashMap<>();
    private final Map<String, String> truthTableByNeuron = new ConcurrentHashMap<>();
    private final Map<String, List<String>> knowledgeIndexByPackage = new ConcurrentHashMap<>();

    // ── Registration API (build the dependency graph) ──

    /** Add a neuron for the given agent. */
    public MockNoosphere addNeuron(String agentId, String neuronId) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(neuronId, "neuronId");
        neuronsByAgent.computeIfAbsent(agentId, k -> new ArrayList<>()).add(neuronId);
        return this;
    }

    /** Add a snapshot for the given agent. */
    public MockNoosphere addSnapshot(String agentId, String snapshotId) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(snapshotId, "snapshotId");
        snapshotsByAgent.computeIfAbsent(agentId, k -> new ArrayList<>()).add(snapshotId);
        return this;
    }

    /** Set the truth table for the given neuron. */
    public MockNoosphere setTruthTable(String neuronId, String truthTableId) {
        Objects.requireNonNull(neuronId, "neuronId");
        Objects.requireNonNull(truthTableId, "truthTableId");
        truthTableByNeuron.put(neuronId, truthTableId);
        return this;
    }

    /** Add a knowledge-index entry for the given package. */
    public MockNoosphere addKnowledgeIndex(String packageId, String entryId) {
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(entryId, "entryId");
        knowledgeIndexByPackage.computeIfAbsent(packageId, k -> new ArrayList<>()).add(entryId);
        return this;
    }

    // ── Query API (used by CascadeRegistrar) ──

    /** All neurons for an agent. */
    public List<String> neuronsForAgent(String agentId) {
        return List.copyOf(neuronsByAgent.getOrDefault(agentId, List.of()));
    }

    /** All snapshots for an agent. */
    public List<String> snapshotsForAgent(String agentId) {
        return List.copyOf(snapshotsByAgent.getOrDefault(agentId, List.of()));
    }

    /** Truth table for a neuron (empty if none). */
    public List<String> truthTableForNeuron(String neuronId) {
        return Optional.ofNullable(truthTableByNeuron.get(neuronId))
                .map(List::of)
                .orElse(List.of());
    }

    /** All knowledge-index entries for a package. */
    public List<String> knowledgeIndexForPackage(String packageId) {
        return List.copyOf(knowledgeIndexByPackage.getOrDefault(packageId, List.of()));
    }

    // ── Stats / debugging ──

    public int totalEntities() {
        return neuronsByAgent.values().stream().mapToInt(List::size).sum()
                + snapshotsByAgent.values().stream().mapToInt(List::size).sum()
                + truthTableByNeuron.size()
                + knowledgeIndexByPackage.values().stream().mapToInt(List::size).sum();
    }

    /** Snapshot of all entity counts per type — useful for assertions. */
    public Map<String, Integer> countByType() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("neurons", neuronsByAgent.values().stream().mapToInt(List::size).sum());
        counts.put("snapshots", snapshotsByAgent.values().stream().mapToInt(List::size).sum());
        counts.put("truthTables", truthTableByNeuron.size());
        counts.put("knowledgeIndex", knowledgeIndexByPackage.values().stream().mapToInt(List::size).sum());
        return counts;
    }
}