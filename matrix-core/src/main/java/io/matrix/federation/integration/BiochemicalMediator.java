package io.matrix.federation.integration;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.registry.ModulatorRegistryStore;
import io.matrix.federation.runtime.FederationRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * W361 — Biochemical mediator bridging FederationRuntime to cognitive layers.
 * 
 * Reads modulator definitions from the federation registry and exposes them
 * as typed value maps for downstream consumption (cognitive loops, attention,
 * arousal dynamics, etc).
 * 
 * Per SPEC-013 Phase 3: Integration with BiochemicalMediator.
 * Per CONSTITUTION I v3: seeded Random for any tie-breaking.
 * Per CONSTITUTION VI: this is a measurement substrate, not subjective state.
 */
public final class BiochemicalMediator {
    
    /** Snapshot of modulator values keyed by modulator ID. */
    public record MediatorSnapshot(
        Map<String, Float> hormoneLevels,
        Map<String, Float> neurotransmitterLevels,
        Map<String, Float> signalLevels,
        long timestampNs,
        int totalModulators
    ) {}
    
    private final FederationRuntime runtime;
    private final Map<String, Float> activeValues = new HashMap<>();
    /** Deterministic RNG (seeded). Reserved for tie-breaking and downstream
     *  stochastic sampling. Exposed via getRng() for callers that need it. */
    private final Random rng;
    private long lastUpdateNs = 0L;
    private int updateCount = 0;
    
    public BiochemicalMediator(FederationRuntime runtime, long seed) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime cannot be null");
        }
        this.runtime = runtime;
        this.rng = new Random(seed);
        refreshFromRegistry();
    }
    
    /**
     * Re-read all modulators from the federation registry and rebuild local caches.
     */
    public synchronized void refreshFromRegistry() {
        activeValues.clear();
        for (ModulatorDefinition def : runtime.getRegistryStore().getAll()) {
            float value = def.getDefaults().getDefaultFloat();
            activeValues.put(def.getId(), value);
        }
        lastUpdateNs = System.nanoTime();
        updateCount++;
    }
    
    /**
     * Get the current value of a modulator by ID.
     */
    public synchronized Optional<Float> getValue(String id) {
        return Optional.ofNullable(activeValues.get(id));
    }
    
    /**
     * Set a new value for a modulator. Does NOT propose to federation —
     * caller is responsible for proposing via FederationRuntime.proposeUpdate.
     */
    public synchronized void setLocalValue(String id, float value) {
        activeValues.put(id, value);
        lastUpdateNs = System.nanoTime();
    }
    
    /**
     * Build a snapshot of current mediator state.
     */
    public synchronized MediatorSnapshot snapshot() {
        Map<String, Float> hormones = new HashMap<>();
        Map<String, Float> neurotransmitters = new HashMap<>();
        Map<String, Float> signals = new HashMap<>();
        
        for (ModulatorDefinition def : runtime.getRegistryStore().getAll()) {
            Float val = activeValues.get(def.getId());
            if (val == null) continue;
            
            switch (def.getType()) {
                case MODULATOR_TYPE_HORMONE:
                    hormones.put(def.getId(), val);
                    break;
                case MODULATOR_TYPE_NEUROTRANSMITTER:
                    neurotransmitters.put(def.getId(), val);
                    break;
                case MODULATOR_TYPE_SIGNAL:
                    signals.put(def.getId(), val);
                    break;
                default:
                    // Other types (LEARNING_RATE, AROUSAL, PLASTICITY) — skip for now
                    break;
            }
        }
        
        // FIX (FAIL-8): totalModulators = registry.size() (not activeValues.size()
        // which includes local-only overrides from setLocalValue).
        int registrySize = runtime.getRegistryStore().size();
        return new MediatorSnapshot(
            hormones,
            neurotransmitters,
            signals,
            lastUpdateNs,
            registrySize
        );
    }
    
    /**
     * Get all values as a single map (id → value).
     */
    public synchronized Map<String, Float> getAllValues() {
        return new HashMap<>(activeValues);
    }
    
    /**
     * Get the count of modulators in each category.
     */
    public synchronized Map<ModulatorType, Integer> getCategoryCounts() {
        Map<ModulatorType, Integer> counts = new HashMap<>();
        for (ModulatorDefinition def : runtime.getRegistryStore().getAll()) {
            counts.merge(def.getType(), 1, Integer::sum);
        }
        return counts;
    }
    
    /**
     * Number of refresh calls since creation.
     */
    public int getUpdateCount() {
        return updateCount;
    }
    
    /**
     * Get the underlying federation runtime.
     */
    public FederationRuntime getRuntime() {
        return runtime;
    }
    
    /**
     * Get the deterministic random for any downstream tie-breaking.
     */
    public Random getRng() {
        return rng;
    }
}
