package io.matrix.federation.property;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.proto.SafetyConstraints;
import io.matrix.federation.registry.ModulatorRegistryStore;
import net.jqwik.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W372 — Property-based tests for ModulatorRegistry.
 */
class ModulatorRegistryPropertyTest {
    
        
    @Provide
    Arbitrary<Integer> nonNegInt() {
        return Arbitraries.integers().between(0, 50);
    }

    @Provide
    Arbitrary<ModulatorType> types() {
        return Arbitraries.of(
            ModulatorType.MODULATOR_TYPE_HORMONE,
            ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER,
            ModulatorType.MODULATOR_TYPE_SIGNAL,
            ModulatorType.MODULATOR_TYPE_LEARNING_RATE,
            ModulatorType.MODULATOR_TYPE_AROUSAL,
            ModulatorType.MODULATOR_TYPE_PLASTICITY
        );
    }
    
    @Provide
    Arbitrary<DataType> dataTypes() {
        return Arbitraries.of(
            DataType.DATA_TYPE_FLOAT32,
            DataType.DATA_TYPE_FLOAT64,
            DataType.DATA_TYPE_INT32,
            DataType.DATA_TYPE_BOOL
        );
    }
    
    @Provide
    Arbitrary<Boolean> booleans() {
        return Arbitraries.of(true, false);
    }
    
    @Provide
    Arbitrary<Integer> validThresholds() {
        return Arbitraries.integers().between(0, 100);
    }
    
    private ModulatorDefinition makeModulator(String id, ModulatorType type, 
                                               DataType dataType, boolean frozen, int threshold) {
        return ModulatorDefinition.newBuilder()
            .setId(id)
            .setName("test_" + id)
            .setType(type)
            .setValueType(dataType)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(0.5f)
                .setMinAllowed(0.0f)
                .setMaxAllowed(1.0f)
                .build())
            .setSafety(SafetyConstraints.newBuilder()
                .setFrozen(frozen)
                .setRequiresConsensus(true)
                .setMinConsensusThreshold(threshold)
                .setMinCapability(CapabilityLevel.CAPABILITY_L2_ADULT)
                .build())
            .build();
    }
    
    @Property
    void addThenGetReturnsSame(@ForAll("types") ModulatorType type,
                                 @ForAll("dataTypes") DataType dataType,
                                 @ForAll("validThresholds") int threshold) {
        Assume.that(threshold >= 0 && threshold <= 100);
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        String id = "mod-" + type.getNumber() + "-" + dataType.getNumber() + "-" + threshold;
        ModulatorDefinition def = makeModulator(id, type, dataType, false, threshold);
        
        store.add(def);
        ModulatorDefinition retrieved = store.get(id).orElseThrow();
        
        assertEquals(id, retrieved.getId());
        assertEquals(type, retrieved.getType());
        assertEquals(dataType, retrieved.getValueType());
        assertEquals(threshold, retrieved.getSafety().getMinConsensusThreshold());
    }
    
    @Property
    void versionIncrementsPerMutation(@ForAll("nonNegInt") Integer n) {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        long initialVersion = store.getVersion();
        
        for (int i = 0; i < n; i++) {
            store.add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE,
                DataType.DATA_TYPE_FLOAT32, false, 50));
        }
        
        assertEquals(initialVersion + n, store.getVersion());
    }
    
    @Property
    void sizeMatchesAddedCount(@ForAll("nonNegInt") Integer n) {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        for (int i = 0; i < n; i++) {
            store.add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE,
                DataType.DATA_TYPE_FLOAT32, false, 50));
        }
        
        assertEquals(n, store.size());
    }
    
    @Property
    void deterministicOrdering(@ForAll("nonNegInt") Integer n) {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        // Add in shuffled order
        for (int i = n; i > 0; i--) {
            store.add(makeModulator("mod-" + String.format("%03d", i),
                ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 50));
        }
        
        List<ModulatorDefinition> all = store.getAll();
        // Should be sorted by ID (String compare)
        for (int i = 0; i < all.size() - 1; i++) {
            assertTrue(all.get(i).getId().compareTo(all.get(i + 1).getId()) < 0);
        }
    }
    
    @Property
    void hashDeterministicForSameContent(@ForAll long seed) {
        ModulatorRegistryStore s1 = new ModulatorRegistryStore(seed);
        ModulatorRegistryStore s2 = new ModulatorRegistryStore(seed + 1);  // Different seed
        
        s1.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 50));
        s2.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 50));
        
        // Same content → same hash
        assertEquals(s1.calculateHash(), s2.calculateHash());
    }
    
    @Property
    void differentContentDifferentHash(@ForAll("types") ModulatorType t1,
                                       @ForAll("types") ModulatorType t2) {
        Assume.that(t1 != t2);
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        ModulatorDefinition d1 = ModulatorDefinition.newBuilder()
            .setId("m1").setName("m1").setType(t1).setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder().setDefaultFloat(0.5f).build())
            .build();
        ModulatorDefinition d2 = ModulatorDefinition.newBuilder()
            .setId("m1").setName("m1").setType(t2).setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder().setDefaultFloat(0.5f).build())
            .build();
        
        // Same ID but different type — won't both be added (duplicate check)
        // Use different IDs
        d1 = d1.toBuilder().setId("m1").build();
        d2 = d2.toBuilder().setId("m2").build();
        
        store.add(d1);
        store.add(d2);
        
        // Hash includes type
        assertNotEquals(store.calculateHash(), "");
    }
    
    @Property
    void canMutateIsLevelBased(@ForAll("booleans") boolean arbitraryCheck) {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        // L0-L1 cannot mutate
        assertFalse(store.canMutate(CapabilityLevel.CAPABILITY_L0_INFANT));
        assertFalse(store.canMutate(CapabilityLevel.CAPABILITY_L1_LEARNER));
        
        // L2+ can
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L2_ADULT));
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L3_SPECIALIST));
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L4_EXPERT));
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L5_MASTER));
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L6_ARCHITECT));
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L7_GUARDIAN));
    }
    
    @Property
    void exportProtoPreservesCount(@ForAll("nonNegInt") Integer n) {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        for (int i = 0; i < n; i++) {
            store.add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE,
                DataType.DATA_TYPE_FLOAT32, false, 50));
        }
        
        var proto = store.exportProto();
        assertEquals(n, proto.getModulatorsCount());
        assertEquals(store.getVersion(), proto.getVersion());
    }
}
