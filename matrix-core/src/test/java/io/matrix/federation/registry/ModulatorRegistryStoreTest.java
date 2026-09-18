package io.matrix.federation.registry;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.proto.SafetyConstraints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W358 — Tests for ModulatorRegistryStore.
 */
class ModulatorRegistryStoreTest {
    
    private ModulatorDefinition sample(String id, boolean frozen) {
        return ModulatorDefinition.newBuilder()
            .setId(id)
            .setName("test_" + id)
            .setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(0.5f)
                .setMinAllowed(0.0f)
                .setMaxAllowed(1.0f)
                .build())
            .setSafety(SafetyConstraints.newBuilder()
                .setFrozen(frozen)
                .setRequiresConsensus(true)
                .setMinConsensusThreshold(67)
                .setMinCapability(CapabilityLevel.CAPABILITY_L2_ADULT)
                .build())
            .build();
    }
    
    @Test
    void testAddAndGet() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        assertEquals(0, store.size());
        
        store.add(sample("m1", false));
        assertEquals(1, store.size());
        
        ModulatorDefinition retrieved = store.get("m1").orElseThrow();
        assertEquals("m1", retrieved.getId());
        assertEquals("test_m1", retrieved.getName());
    }
    
    @Test
    void testVersionIncrement() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        assertEquals(0, store.getVersion());
        
        store.add(sample("m1", false));
        assertEquals(1, store.getVersion());
        
        store.add(sample("m2", false));
        assertEquals(2, store.getVersion());
        
        store.update(sample("m1", false));
        assertEquals(3, store.getVersion());
        
        store.remove("m1");
        assertEquals(4, store.getVersion());
    }
    
    @Test
    void testCannotAddDuplicate() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        store.add(sample("m1", false));
        
        assertThrows(IllegalArgumentException.class, () -> store.add(sample("m1", false)));
    }
    
    @Test
    void testCannotUpdateFrozen() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        store.add(sample("frozen-1", true));
        
        assertThrows(IllegalArgumentException.class, () -> 
            store.update(sample("frozen-1", true)));
    }
    
    @Test
    void testCannotRemoveFrozen() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        store.add(sample("frozen-1", true));
        
        assertThrows(IllegalArgumentException.class, () -> store.remove("frozen-1"));
    }
    
    @Test
    void testDeterministicOrdering() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        store.add(sample("z-third", false));
        store.add(sample("a-first", false));
        store.add(sample("m-second", false));
        
        // getAll() should return sorted by ID
        assertEquals("a-first", store.getAll().get(0).getId());
        assertEquals("m-second", store.getAll().get(1).getId());
        assertEquals("z-third", store.getAll().get(2).getId());
    }
    
    @Test
    void testConsensusHashDeterministic() {
        ModulatorRegistryStore store1 = new ModulatorRegistryStore(42L);
        ModulatorRegistryStore store2 = new ModulatorRegistryStore(99L);  // different seed
        
        store1.add(sample("m1", false));
        store2.add(sample("m1", false));
        
        // Hash should be same regardless of seed (only depends on contents)
        assertEquals(store1.calculateHash(), store2.calculateHash());
    }
    
    @Test
    void testExportProto() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        store.add(sample("m1", false));
        store.add(sample("m2", false));
        
        var proto = store.exportProto();
        assertEquals(2, proto.getVersion());
        assertEquals(2, proto.getModulatorsCount());
        assertFalse(proto.getConsensusHash().isEmpty());
    }
    
    @Test
    void testCapabilityCheck() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        // L0-L1 cannot mutate
        assertFalse(store.canMutate(CapabilityLevel.CAPABILITY_L0_INFANT));
        assertFalse(store.canMutate(CapabilityLevel.CAPABILITY_L1_LEARNER));
        
        // L2+ can mutate
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L2_ADULT));
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L3_SPECIALIST));
        assertTrue(store.canMutate(CapabilityLevel.CAPABILITY_L7_GUARDIAN));
    }
    
    @Test
    void testValidationConsensusThreshold() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        
        ModulatorDefinition invalid = ModulatorDefinition.newBuilder()
            .setId("bad")
            .setName("bad")
            .setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setSafety(SafetyConstraints.newBuilder()
                .setMinConsensusThreshold(150)  // > 100, invalid
                .build())
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> store.add(invalid));
    }
}
