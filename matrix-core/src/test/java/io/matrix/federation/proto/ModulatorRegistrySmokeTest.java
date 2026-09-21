package io.matrix.federation.proto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W357 — Smoke test for generated ProtoBuf classes.
 */
class ModulatorRegistrySmokeTest {

    @Test
    void testModulatorRegistryBuilder() {
        ModulatorDefinition def = ModulatorDefinition.newBuilder()
            .setId("test-modulator-001")
            .setName("test_cortisol")
            .setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(0.5f)
                .setMinAllowed(0.0f)
                .setMaxAllowed(1.0f)
                .setLearningRate(0.01f)
                .build())
            .setSafety(SafetyConstraints.newBuilder()
                .setFrozen(false)
                .setRequiresConsensus(true)
                .setMinConsensusThreshold(67)
                .setMinCapability(CapabilityLevel.CAPABILITY_L2_ADULT)
                .build())
            .build();
        
        ModulatorRegistry registry = ModulatorRegistry.newBuilder()
            .setVersion(1L)
            .setTimestampNs(System.nanoTime())
            .setConsensusHash("hash-abc-123")
            .addModulators(def)
            .build();
        
        assertEquals(1, registry.getVersion());
        assertEquals("hash-abc-123", registry.getConsensusHash());
        assertEquals(1, registry.getModulatorsCount());
        
        ModulatorDefinition retrieved = registry.getModulators(0);
        assertEquals("test-modulator-001", retrieved.getId());
        assertEquals("test_cortisol", retrieved.getName());
        assertEquals(ModulatorType.MODULATOR_TYPE_HORMONE, retrieved.getType());
        assertEquals(DataType.DATA_TYPE_FLOAT32, retrieved.getValueType());
        assertEquals(0.5f, retrieved.getDefaults().getDefaultFloat(), 0.001f);
        assertEquals(67, retrieved.getSafety().getMinConsensusThreshold());
    }
    
    @Test
    void testFederationNodeBasicFields() {
        FederationNode node = FederationNode.newBuilder()
            .setNodeId("node-42")
            .setRole(NodeType.NODE_TYPE_ADULT)
            .setStatus(NodeStatus.NODE_STATUS_ONLINE)
            .setCapability(CapabilityLevel.CAPABILITY_L2_ADULT)
            .setReputationScore(0.85f)
            .setLastSeenNs(System.currentTimeMillis())
            .setResources(NodeResources.newBuilder()
                .setCpuCores(8)
                .setMemoryGb(16)
                .setStorageGb(500)
                .build())
            .build();
        
        assertEquals("node-42", node.getNodeId());
        assertEquals(NodeType.NODE_TYPE_ADULT, node.getRole());
        assertEquals(CapabilityLevel.CAPABILITY_L2_ADULT, node.getCapability());
        assertEquals(0.85, node.getReputationScore(), 0.001);
        assertEquals(8, node.getResources().getCpuCores());
    }
    
    @Test
    void testCapabilityLevelEnum() {
        assertEquals(10, CapabilityLevel.values().length);  // includes UNSPECIFIED + 2 reserved
        assertEquals(CapabilityLevel.CAPABILITY_L0_INFANT, CapabilityLevel.valueOf("CAPABILITY_L0_INFANT"));
        assertEquals(CapabilityLevel.CAPABILITY_L7_GUARDIAN, CapabilityLevel.valueOf("CAPABILITY_L7_GUARDIAN"));
    }
    
    @Test
    void testFrozenFilterBasics() {
        FrozenFilter filter = FrozenFilter.newBuilder()
            .setFilterId("filter-001")
            .setMode(FilterMode.FILTER_HARD)
            .setActivatedAtNs(System.nanoTime())
            .setActivatedBy("owner-node")
            .build();
        
        assertEquals("filter-001", filter.getFilterId());
        assertEquals(FilterMode.FILTER_HARD, filter.getMode());
        assertEquals("owner-node", filter.getActivatedBy());
    }
    
    @Test
    void testWaveCheckpointBasics() {
        WaveCheckpoint checkpoint = WaveCheckpoint.newBuilder()
            .setWaveId("357")
            .setStartedAtNs(System.nanoTime())
            .setCompletedAtNs(System.nanoTime())
            .setStatus(WaveStatus.WAVE_COMPLETED)
            .build();
        
        assertEquals("357", checkpoint.getWaveId());
        assertEquals(WaveStatus.WAVE_COMPLETED, checkpoint.getStatus());
    }
    
    @Test
    void testEnumConsistency() {
        assertTrue(ModulatorType.values().length >= 6);
        assertTrue(DataType.values().length >= 7);
        assertEquals(9, EthicalPrinciple.values().length);  // includes UNSPECIFIED + reserved
        assertEquals(6, SeverityLevel.values().length);  // includes UNSPECIFIED + reserved
    }
}
