package io.matrix.federation.runtime;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.proto.SafetyConstraints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FederationRuntimeTest {
    
    private ModulatorDefinition sample(String id, boolean frozen) {
        return ModulatorDefinition.newBuilder()
            .setId(id)
            .setName("test_" + id)
            .setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setSafety(SafetyConstraints.newBuilder()
                .setFrozen(frozen)
                .setRequiresConsensus(true)
                .setMinConsensusThreshold(67)
                .setMinCapability(CapabilityLevel.CAPABILITY_L2_ADULT)
                .build())
            .build();
    }
    
    @Test
    void testProposeAddAccepted() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        var result = runtime.proposeAdd(sample("m1", false), 6);
        
        assertTrue(result.isPresent());
        assertEquals("m1", result.get().getId());
        assertEquals(1, runtime.getRegistryStore().size());
    }
    
    @Test
    void testProposeAddRejectedByCapability() {
        // L0 cannot mutate
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L0_INFANT, 100L);
        var result = runtime.proposeAdd(sample("m1", false), 1);
        
        assertTrue(result.isEmpty());
        assertEquals(0, runtime.getRegistryStore().size());
    }
    
    @Test
    void testProposeUpdateFailsForFrozen() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L7_GUARDIAN, 100L);
        runtime.proposeAdd(sample("frozen-1", true), 8);
        
        var result = runtime.proposeUpdate(sample("frozen-1", true), 8);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testProposeRemove() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L4_EXPERT, 100L);
        runtime.proposeAdd(sample("m1", false), 5);
        assertEquals(1, runtime.getRegistryStore().size());
        
        boolean removed = runtime.proposeRemove("m1", 5);
        assertTrue(removed);
        assertEquals(0, runtime.getRegistryStore().size());
    }
    
    @Test
    void testProposeRemoveFrozenFails() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L4_EXPERT, 100L);
        runtime.proposeAdd(sample("frozen-1", true), 5);
        
        assertFalse(runtime.proposeRemove("frozen-1", 5));
        assertEquals(1, runtime.getRegistryStore().size());
    }
    
    @Test
    void testExportRegistry() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(sample("m1", false), 6);
        runtime.proposeAdd(sample("m2", false), 6);
        
        var proto = runtime.exportRegistry();
        assertEquals(2, proto.getModulatorsCount());
    }
    
    @Test
    void testGetNonExistent() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        assertTrue(runtime.get("nonexistent").isEmpty());
    }
    
    @Test
    void testMultipleNodesIndependentRuntimes() {
        FederationRuntime node1 = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L3_SPECIALIST, 100L);
        FederationRuntime node2 = new FederationRuntime(2L, CapabilityLevel.CAPABILITY_L3_SPECIALIST, 100L);
        
        node1.proposeAdd(sample("m1", false), 4);
        // node2 doesn't have m1
        assertTrue(node1.get("m1").isPresent());
        assertTrue(node2.get("m1").isEmpty());
    }

    
    // ===== FAIL-4, FAIL-5, FAIL-10 fixes =====
    
    @Test
    void testNullCapabilityThrows() {
        // FAIL-10 fix: null capability throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> 
            new FederationRuntime(1L, null, 42L));
    }
    
    @Test
    void testPerModulatorMinCapabilityEnforced() {
        // FAIL-5 fix: per-modulator min_capability requirement
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L2_ADULT, 100L);
        
        io.matrix.federation.proto.ModulatorDefinition def = io.matrix.federation.proto.ModulatorDefinition.newBuilder()
            .setId("requires-L7")
            .setName("test")
            .setType(io.matrix.federation.proto.ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(io.matrix.federation.proto.DataType.DATA_TYPE_FLOAT32)
            .setDefaults(io.matrix.federation.proto.ModulatorDefaults.newBuilder().setDefaultFloat(0.5f).build())
            .setSafety(io.matrix.federation.proto.SafetyConstraints.newBuilder()
                .setMinCapability(CapabilityLevel.CAPABILITY_L7_GUARDIAN)
                .setMinConsensusThreshold(67)
                .build())
            .build();
        
        // L2 should NOT be able to add (def requires L7)
        var result = runtime.proposeAdd(def, CapabilityLevel.CAPABILITY_L2_ADULT.getNumber());
        assertTrue(result.isEmpty());
        
        // L7 should be able to add
        var result2 = runtime.proposeAdd(def, CapabilityLevel.CAPABILITY_L7_GUARDIAN.getNumber());
        assertTrue(result2.isPresent());
    }
    
    @Test
    void testConsensusResetBetweenProposals() {
        // FAIL-4 fix: consensus should reset between proposals
        // If reset didn't happen, second proposal would see phantom votes
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        
        // Add once
        var def1 = sample("m1", false);
        var result1 = runtime.proposeAdd(def1, 5);
        assertTrue(result1.isPresent());
        assertEquals(1, runtime.getRegistryStore().size());
        
        // Update (would fail because consensus has phantom votes without reset)
        var def2 = sample("m2", false);
        var result2 = runtime.proposeAdd(def2, 5);
        assertTrue(result2.isPresent());
        assertEquals(2, runtime.getRegistryStore().size());
    }
}
