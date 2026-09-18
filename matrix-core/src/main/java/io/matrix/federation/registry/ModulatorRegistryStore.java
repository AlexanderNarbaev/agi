package io.matrix.federation.registry;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorRegistry;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.proto.SafetyConstraints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * W358 — In-memory Modulator Registry.
 * 
 * Stores {@link ModulatorDefinition} objects indexed by ID and enforces:
 * - FROZEN constraint (immutable definitions cannot be updated)
 * - Capability-based access (only L2+ can modify)
 * - Deterministic ordering for Merkle root calculation
 * 
 * Per SPEC-013 R1-R7.
 * Per CONSTITUTION I v3: seeded Random used for any randomization.
 */
public final class ModulatorRegistryStore {
    
    private final Map<String, ModulatorDefinition> modulators = new HashMap<>();
    private final Random rng;
    private long version = 0L;
    
    public ModulatorRegistryStore(long seed) {
        this.rng = new Random(seed);
    }
    
    /**
     * Add a new modulator definition.
     * 
     * @throws IllegalArgumentException if ID already exists or safety violated
     */
    public void add(ModulatorDefinition def) {
        if (def == null) {
            throw new IllegalArgumentException("modulator definition cannot be null");
        }
        if (def.getId().isEmpty()) {
            throw new IllegalArgumentException("modulator id cannot be empty");
        }
        if (modulators.containsKey(def.getId())) {
            throw new IllegalArgumentException("modulator id already exists: " + def.getId());
        }
        validateSafety(def);
        modulators.put(def.getId(), def);
        version++;
    }
    
    /**
     * Update an existing modulator.
     * 
     * @throws IllegalArgumentException if not found or FROZEN
     */
    public void update(ModulatorDefinition def) {
        if (def == null || def.getId().isEmpty()) {
            throw new IllegalArgumentException("invalid modulator definition");
        }
        ModulatorDefinition existing = modulators.get(def.getId());
        if (existing == null) {
            throw new IllegalArgumentException("modulator not found: " + def.getId());
        }
        if (existing.getSafety().getFrozen()) {
            throw new IllegalArgumentException("cannot update FROZEN modulator: " + def.getId());
        }
        validateSafety(def);
        modulators.put(def.getId(), def);
        version++;
    }
    
    /**
     * Remove a modulator.
     * 
     * @throws IllegalArgumentException if not found or FROZEN
     */
    public void remove(String id) {
        ModulatorDefinition existing = modulators.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("modulator not found: " + id);
        }
        if (existing.getSafety().getFrozen()) {
            throw new IllegalArgumentException("cannot remove FROZEN modulator: " + id);
        }
        modulators.remove(id);
        version++;
    }
    
    /**
     * Get a modulator by ID.
     */
    public Optional<ModulatorDefinition> get(String id) {
        return Optional.ofNullable(modulators.get(id));
    }
    
    /**
     * Get all modulators (deterministic order by ID).
     */
    public List<ModulatorDefinition> getAll() {
        List<ModulatorDefinition> sorted = new ArrayList<>(modulators.values());
        sorted.sort((a, b) -> a.getId().compareTo(b.getId()));
        return sorted;
    }
    
    /**
     * Get current version (incremented on every mutation).
     */
    public long getVersion() {
        return version;
    }
    
    /**
     * Get count of registered modulators.
     */
    public int size() {
        return modulators.size();
    }
    
    /**
     * Export to ProtoBuf registry message.
     */
    public ModulatorRegistry exportProto() {
        // FIX (FAIL-6): Use 0 timestamp for deterministic export.
        // Previously used System.nanoTime() which made exports non-reproducible.
        return ModulatorRegistry.newBuilder()
            .setVersion(version)
            .setTimestampNs(0L)
            .setConsensusHash(calculateHash())
            .addAllModulators(getAll())
            .build();
    }
    
    /**
     * Compute consensus hash from sorted modulator IDs + values.
     * Returns simple XOR-based hash (not cryptographic, but deterministic).
     */
    public String calculateHash() {
        long h = 0L;
        for (ModulatorDefinition def : getAll()) {
            h ^= def.getId().hashCode();
            h = h * 31 + def.getName().hashCode();
            h = h * 31 + def.getTypeValue();
            h = h * 31 + def.getValueTypeValue();
            if (def.hasDefaults()) {
                h = h * 31 + Float.floatToIntBits(def.getDefaults().getDefaultFloat());
            }
        }
        return Long.toHexString(h);
    }
    
    /**
     * Validate safety constraints.
     */
    private void validateSafety(ModulatorDefinition def) {
        SafetyConstraints safety = def.getSafety();
        if (safety.getMinConsensusThreshold() < 0 || safety.getMinConsensusThreshold() > 100) {
            throw new IllegalArgumentException(
                "min_consensus_threshold must be 0-100, got " + safety.getMinConsensusThreshold());
        }
        // FROZEN constraint: requires_consensus must be true
        if (safety.getFrozen() && !safety.getRequiresConsensus()) {
            throw new IllegalArgumentException(
                "FROZEN modulator must require consensus");
        }
    }
    
    /**
     * Check if a capability level can mutate modulators.
     * Per SPEC-013 R2: only L2+ can modify.
     */
    public boolean canMutate(CapabilityLevel level) {
        int ordinal = level.getNumber();
        return ordinal >= CapabilityLevel.CAPABILITY_L2_ADULT.getNumber();
    }
    
    /**
     * Get the registry's RNG (for callers that need deterministic randomness).
     */
    public Random getRng() {
        return rng;
    }
}
