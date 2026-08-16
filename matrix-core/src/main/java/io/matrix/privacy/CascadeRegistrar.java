package io.matrix.privacy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Pre-built cascade rules for typical MATRIX-AGI resource types.
 *
 * <p>These rules wire up the "natural" dependency graph between resource
 * types in MATRIX. When a resource is tombstoned, the dependent artefacts
 * are automatically erased too — preventing GDPR Art. 17 leakage.
 *
 * <p>Default rules:
 * <ul>
 *   <li><b>Agent → Neuron</b>: when an Agent is erased, all its neurons
 *       are erased (assuming naming convention {@code agentId-neuron-N}).</li>
 *   <li><b>Agent → Snapshot</b>: when an Agent is erased, all snapshots
 *       taken of it are erased (naming: {@code snap-of-{agentId}-*}).</li>
 *   <li><b>Neuron → TruthTable</b>: when a Neuron is erased, the underlying
 *       TruthTable (naming: {@code tt-of-{neuronId}}) is erased.</li>
 *   <li><b>FnlPackage → KnowledgeIndex</b>: when a FnlPackage is erased,
 *       the corresponding KnowledgeIndex entries are erased.</li>
 * </ul>
 *
 * <p>Resolvers are pluggable via {@link Resolvers} so production code can
 * wire real dependency lookups (e.g. against NoosphereRegistry or a DB).
 *
 * <p>Ref: L6 §6.7 (GDPR cascade), L12 §4 (legal framework).
 */
public final class CascadeRegistrar {

    private CascadeRegistrar() {}

    /** All standard source types we register cascades for. */
    public static final Set<String> STANDARD_SOURCE_TYPES = Set.of(
            "Agent", "Neuron", "FnlPackage", "Snapshot");

    /**
     * Register all standard cascade rules on the given service, using the
     * provided {@link Resolvers} to look up dependent resource ids.
     */
    public static CascadeTombstoneService registerAll(
            CascadeTombstoneService service, Resolvers resolvers) {
        // Agent → Neuron
        service.register(new CascadeTombstoneService.CascadeRule(
                "Agent", "Neuron", resolvers.neuronsForAgent, "cascade.from.agent"));

        // Agent → Snapshot
        service.register(new CascadeTombstoneService.CascadeRule(
                "Agent", "Snapshot", resolvers.snapshotsForAgent, "cascade.from.agent"));

        // Neuron → TruthTable
        service.register(new CascadeTombstoneService.CascadeRule(
                "Neuron", "TruthTable", resolvers.truthTableForNeuron, "cascade.from.neuron"));

        // FnlPackage → KnowledgeIndex
        service.register(new CascadeTombstoneService.CascadeRule(
                "FnlPackage", "KnowledgeIndex",
                resolvers.knowledgeIndexForPackage, "cascade.from.package"));

        return service;
    }

    /**
     * Build a no-op {@link Resolvers} that returns no dependents — useful
     * for tests and for deployments where dependency lookups aren't wired.
     */
    public static Resolvers noOpResolvers() {
        return new Resolvers(
                (id, d) -> List.of(),
                (id, d) -> List.of(),
                (id, d) -> List.of(),
                (id, d) -> List.of());
    }

    /**
     * Bundle of dependent-id resolvers for the four standard cascade rules.
     * Each function takes the source id and current cascade depth and returns
     * the list of dependent ids (or empty list if no dependents).
     */
    public record Resolvers(
            BiFunction<String, Integer, List<String>> neuronsForAgent,
            BiFunction<String, Integer, List<String>> snapshotsForAgent,
            BiFunction<String, Integer, List<String>> truthTableForNeuron,
            BiFunction<String, Integer, List<String>> knowledgeIndexForPackage) {
        public Resolvers {
            neuronsForAgent = neuronsForAgent != null ? neuronsForAgent : (id, d) -> List.of();
            snapshotsForAgent = snapshotsForAgent != null ? snapshotsForAgent : (id, d) -> List.of();
            truthTableForNeuron = truthTableForNeuron != null ? truthTableForNeuron : (id, d) -> List.of();
            knowledgeIndexForPackage = knowledgeIndexForPackage != null ? knowledgeIndexForPackage : (id, d) -> List.of();
        }
    }

    /**
     * Static convenience: register rules and return a service bound to the
     * given {@link TombstoneService}.
     */
    public static CascadeTombstoneService registerAll(
            TombstoneService base, Resolvers resolvers) {
        return registerAll(new CascadeTombstoneService(base), resolvers);
    }
}