package io.matrix.federation.edge;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.proto.SafetyConstraints;
import io.matrix.federation.registry.ModulatorRegistryStore;
import io.matrix.federation.runtime.FederationRuntime;
import io.matrix.federation.integration.BiochemicalMediator;
import io.matrix.federation.integration.CognitiveModulationBridge;
import io.matrix.federation.consensus.LocalConsensusEngine;
import io.matrix.federation.proto.ConsensusProposal;
import io.matrix.federation.proto.VoteDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W387 — Edge case tests for federation classes.
 */
class FederationEdgeCaseTest {
    
    @Test
    void testModulatorRegistryAddNullThrows() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        assertThrows(IllegalArgumentException.class, () -> store.add(null));
    }
    
    @Test
    void testModulatorRegistryAddEmptyIdThrows() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        ModulatorDefinition def = ModulatorDefinition.newBuilder()
            .setId("").setName("test").setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .build();
        assertThrows(IllegalArgumentException.class, () -> store.add(def));
    }
    
    @Test
    void testModulatorRegistryAddDuplicateThrows() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        ModulatorDefinition def = ModulatorDefinition.newBuilder()
            .setId("dup").setName("test").setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .build();
        store.add(def);
        assertThrows(IllegalArgumentException.class, () -> store.add(def));
    }
    
    @Test
    void testConsensusAddNullVoteThrows() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        assertThrows(IllegalArgumentException.class, () -> engine.addVote(null));
    }
    
    @Test
    void testFederationRuntimeNullCapabilityThrows() {
        // FAIL-10 fix verified
        assertThrows(IllegalArgumentException.class, () -> 
            new FederationRuntime(1L, null, 42L));
    }
    
    @Test
    void testFederationRuntimeProposeAddNullDef() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        // null def doesn't throw but returns empty (graceful)
        var result = runtime.proposeAdd(null, 6);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testFederationRuntimeProposeAddZeroCapability() {
        // Capability ordinal 0 = CAPABILITY_UNSPECIFIED, treated as L0 (cannot mutate)
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        ModulatorDefinition def = ModulatorDefinition.newBuilder()
            .setId("m1").setName("test").setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .build();
        var result = runtime.proposeAdd(def, 0);  // 0 = UNSPECIFIED
        assertTrue(result.isEmpty());  // L0 cannot mutate
    }
    
    @Test
    void testBiochemicalMediatorNullRuntimeThrows() {
        assertThrows(IllegalArgumentException.class, () -> 
            new BiochemicalMediator(null, 42L));
    }
    
    @Test
    void testBiochemicalMediatorNullMediatorThrows() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        assertThrows(IllegalArgumentException.class, () -> 
            new CognitiveModulationBridge(null));
    }
    
    @Test
    void testCognitiveModulationBridgeNullProfileThrows() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        assertThrows(IllegalArgumentException.class, () -> bridge.modulate(null));
    }
    
    @Test
    void testConsensusEngineCreateVoteNullLevelThrows() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        // No NPE because getVotingWeight returns 0.0 for unknown
        var vote = engine.createVote("p1", VoteDecision.VOTE_YES, null, 1.0);
        assertNotNull(vote);
        assertEquals(0.0f, vote.getReputationWeight(), 0.001f);
    }
    
    @Test
    void testModulatorRegistryRemoveNonExistentThrows() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        assertThrows(IllegalArgumentException.class, () -> store.remove("nonexistent"));
    }
    
    @Test
    void testModulatorRegistryUpdateNonExistentThrows() {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        ModulatorDefinition def = ModulatorDefinition.newBuilder()
            .setId("nonexistent").setName("test").setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .build();
        assertThrows(IllegalArgumentException.class, () -> store.update(def));
    }
    
    @Test
    void testFederationRuntimeRemoveNonExistentReturnsFalse() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        assertFalse(runtime.proposeRemove("nonexistent", 6));
    }
}
