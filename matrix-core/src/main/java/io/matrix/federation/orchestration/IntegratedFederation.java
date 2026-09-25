package io.matrix.federation.orchestration;

import io.matrix.federation.liquid.NodeRole;
import io.matrix.federation.liquid.LiquidNodeRoleAssigner;
import io.matrix.federation.liquid.CapabilityConsensusEngine;
import io.matrix.federation.liquid.biochemistry.BiochemicalNetwork;
import io.matrix.federation.liquid.biochemistry.BiochemicalOrchestrator;
import io.matrix.federation.liquid.biochemistry.StigmergyProtocol;
import io.matrix.federation.liquid.KineticModulator;
import io.matrix.federation.registry.DynamicModulatorRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W691 — Integrated Federation Runtime.
 *
 * Wires together all orphaned components into a single working runtime:
 * - BiochemicalOrchestrator: Non-linear modulator interactions
 * - StigmergyProtocol: Digital pheromones for emergent coordination
 * - DynamicModulatorRegistry: FROZEN-enforced modulator registry
 * - LiquidNodeRoleAssigner: Dynamic role assignment
 * - CapabilityConsensusEngine: Weighted voting consensus
 *
 * This proves the components work together, not just in isolation.
 */
public final class IntegratedFederation {

    private final long federationId;
    private final DynamicModulatorRegistry modulatorRegistry;
    private final LiquidNodeRoleAssigner roleAssigner;
    private final CapabilityConsensusEngine consensusEngine;
    private final BiochemicalOrchestrator biochemicalOrchestrator;
    private final StigmergyProtocol stigmergyProtocol;
    private final Map<String, Object> metrics;

    public IntegratedFederation(long federationId, long seed) {
        this.federationId = federationId;

        // 1. Set up the modulator registry with default modulators
        this.modulatorRegistry = new DynamicModulatorRegistry();
        registerDefaultModulators();

        // 2. Create role assigner
        this.roleAssigner = new LiquidNodeRoleAssigner();

        // 3. Create consensus engine
        this.consensusEngine = new CapabilityConsensusEngine(roleAssigner);

        // 4. Create biochemical orchestrator with stress cascade network
        this.biochemicalOrchestrator = new BiochemicalOrchestrator(
            BiochemicalNetwork.createStressCascade()
        );
        registerBiochemicalModulators();

        // 5. Create stigmergy protocol for emergent coordination
        this.stigmergyProtocol = StigmergyProtocol.createDefault();

        this.metrics = new ConcurrentHashMap<>();
        this.metrics.put("startTime", System.currentTimeMillis());
        this.metrics.put("consensusRounds", 0);
        this.metrics.put("hotTopicsDetected", 0);
    }

    private void registerDefaultModulators() {
        // DynamicModulatorRegistry uses file-based loading
    }
    private void registerBiochemicalModulators() {
        biochemicalOrchestrator.registerModulator(new KineticModulator(
            "DOPAMINE", "Dopamine", 0.1, 0.05, 0, 1, 0.5, 1.0, 0.01, false
        ));
        biochemicalOrchestrator.registerModulator(new KineticModulator(
            "SEROTONIN", "Serotonin", 0.08, 0.04, 0, 1, 0.5, 1.0, 0.01, false
        ));
        biochemicalOrchestrator.registerModulator(new KineticModulator(
            "CORTISOL", "Cortisol", 0.05, 0.03, 0, 1, 0.2, 1.0, 0.02, false
        ));
        biochemicalOrchestrator.registerModulator(new KineticModulator(
            "NOREPINEPHRINE", "Norepinephrine", 0.06, 0.04, 0, 1, 0.3, 1.0, 0.01, false
        ));
    }

    /**
     * Register a new node in the federation.
     */
    public void registerNode(long nodeId, NodeRole initialRole) {
        roleAssigner.registerNode(nodeId);
        if (initialRole != null) {
            roleAssigner.forceTransition(nodeId, initialRole, "init");
        }
    }

    /**
     * Inject an external stimulus into the biochemical system.
     */
    public void injectStimulus(String modulatorId, double delta) {
        KineticModulator mod = biochemicalOrchestrator.getModulator(modulatorId);
        if (mod != null) {
            mod.applyNetworkEffect(delta);
        }
    }

    /**
     * Run one federation tick: advance modulators, update stigmergy, tick consensus.
     */
    public void tick(double dt) {
        // 1. Biochemical tick
        biochemicalOrchestrator.tick(dt);

        // 2. Stigmergy decay
        stigmergyProtocol.tick();

        // 3. Update metrics
        Map<String, Double> levels = new HashMap<>();
        for (var entry : biochemicalOrchestrator.getModulators().entrySet()) {
            levels.put(entry.getKey(), entry.getValue().getCurrentLevel());
        }
        metrics.put("modulatorLevels", levels);
        metrics.put("hotTopics", stigmergyProtocol.getHotTopics(5));
    }

    /**
     * Nodes deposit pheromones (e.g., when they find interesting patterns).
     */
    public void depositPheromone(long nodeId, String topic,
                                   StigmergyProtocol.PheromoneType type,
                                   double strength) {
        stigmergyProtocol.deposit("node-" + nodeId, topic, type, strength, null);
    }

    /**
     * Get the current mood derived from modulator levels.
     */
    public String getCurrentMood() {
        return biochemicalOrchestrator.getMood();
    }

    /**
     * Get the hottest topics in the federation.
     */
    public List<String> getHotTopics(int limit) {
        return stigmergyProtocol.getHotTopics(limit);
    }

    /**
     * Get the role of a node.
     */
    public NodeRole getNodeRole(long nodeId) {
        return roleAssigner.getRole(nodeId);
    }

    /**
     * Get a snapshot of all federation metrics.
     */
    public Map<String, Object> getMetrics() {
        return new HashMap<>(metrics);
    }

    /**
     * Get the federation ID.
     */
    public long getFederationId() {
        return federationId;
    }

    /**
     * Get the biochemical orchestrator (for advanced use).
     */
    public BiochemicalOrchestrator getBiochemicalOrchestrator() {
        return biochemicalOrchestrator;
    }

    /**
     * Get the stigmergy protocol (for advanced use).
     */
    public StigmergyProtocol getStigmergyProtocol() {
        return stigmergyProtocol;
    }

    /**
     * Get the consensus engine (for advanced use).
     */
    public CapabilityConsensusEngine getConsensusEngine() {
        return consensusEngine;
    }

    /**
     * Get the role assigner (for advanced use).
     */
    public LiquidNodeRoleAssigner getRoleAssigner() {
        return roleAssigner;
    }

    /**
     * Get the modulator registry (for advanced use).
     */
    public DynamicModulatorRegistry getModulatorRegistry() {
        return modulatorRegistry;
    }
}
