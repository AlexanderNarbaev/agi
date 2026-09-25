package io.matrix.federation.liquid.biochemistry;

import io.matrix.federation.liquid.KineticModulator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W652 — Biochemical Orchestrator.
 *
 * Coordinates all modulators through the BiochemicalNetwork,
 * replacing the old linear cross-talk with non-linear interactions.
 *
 * Usage:
 *   BiochemicalOrchestrator orchestrator = new BiochemicalOrchestrator(network);
 *   orchestrator.registerModulator(dopamine);
 *   orchestrator.registerModulator(cortisol);
 *   orchestrator.tick(1.0); // Simulate one time step
 */
public final class BiochemicalOrchestrator {

    private final BiochemicalNetwork network;
    private final ConcurrentHashMap<String, KineticModulator> modulators = new ConcurrentHashMap<>();

    public BiochemicalOrchestrator(BiochemicalNetwork network) {
        this.network = network;
    }

    /**
     * Register a modulator with the orchestrator.
     */
    public void registerModulator(KineticModulator modulator) {
        modulators.put(modulator.getId(), modulator);
    }

    /**
     * Unregister a modulator.
     */
    public void unregisterModulator(String id) {
        modulators.remove(id);
    }

    /**
     * Simulate one time step for all modulators.
     *
     * Steps:
     * 1. Collect current levels into a snapshot
     * 2. Compute network interaction effects
     * 3. Apply effects to each modulator
     * 4. Run each modulator's internal tick
     *
     * @param dt time step (seconds)
     */
    public void tick(double dt) {
        // 1. Snapshot current levels
        Map<String, Double> levels = new HashMap<>();
        for (var entry : modulators.entrySet()) {
            levels.put(entry.getKey(), entry.getValue().getCurrentLevel());
        }
        BiochemicalNetwork.ModulatorSnapshot snapshot = new BiochemicalNetwork.ModulatorSnapshot(levels);

        // 2. Compute network effects
        Map<String, Double> effects = network.computeInteractionEffects(snapshot);

        // 3. Apply network effects
        for (var entry : modulators.entrySet()) {
            String id = entry.getKey();
            KineticModulator modulator = entry.getValue();
            double effect = effects.getOrDefault(id, 0.0);
            modulator.applyNetworkEffect(effect * dt);
        }

        // 4. Internal tick
        for (var entry : modulators.entrySet()) {
            entry.getValue().tick(dt);
        }
    }

    /**
     * Get all registered modulators.
     */
    public Map<String, KineticModulator> getModulators() {
        return Collections.unmodifiableMap(modulators);
    }

    /**
     * Get a specific modulator.
     */
    public KineticModulator getModulator(String id) {
        return modulators.get(id);
    }

    /**
     * Get the current mood derived from modulator levels.
     */
    public String getMood() {
        return KineticModulator.deriveMood(modulators);
    }

    /**
     * Get the current sleep need.
     */
    public double getSleepNeed() {
        return KineticModulator.getSleepNeed(modulators);
    }

    /**
     * Get the biochemical network.
     */
    public BiochemicalNetwork getNetwork() {
        return network;
    }

    /**
     * Create a standard orchestrator with stress cascade.
     */
    public static BiochemicalOrchestrator createDefault() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        BiochemicalOrchestrator orchestrator = new BiochemicalOrchestrator(network);

        // Register standard modulators
        orchestrator.registerModulator(new KineticModulator(
                "DOPAMINE", "Dopamine", 0.1, 0.05, 0, 1, 0.5, 1.0, 0.01, false));
        orchestrator.registerModulator(new KineticModulator(
                "SEROTONIN", "Serotonin", 0.08, 0.04, 0, 1, 0.5, 1.0, 0.01, false));
        orchestrator.registerModulator(new KineticModulator(
                "CORTISOL", "Cortisol", 0.05, 0.03, 0, 1, 0.2, 1.0, 0.02, false));
        orchestrator.registerModulator(new KineticModulator(
                "NOREPINEPHRINE", "Norepinephrine", 0.06, 0.04, 0, 1, 0.3, 1.0, 0.01, false));

        return orchestrator;
    }
}
