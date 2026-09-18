package io.matrix.federation.e2e;

import io.matrix.federation.consensus.LocalConsensusEngine;
import io.matrix.federation.integration.BiochemicalMediator;
import io.matrix.federation.integration.CognitiveModulationBridge;
import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.ConsensusProposal;
import io.matrix.federation.proto.ConsensusStatus;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.proto.VoteDecision;
import io.matrix.federation.registry.ModulatorRegistryStore;
import io.matrix.federation.runtime.FederationRuntime;
import io.matrix.federation.telemetry.FederationTelemetry;
import io.matrix.consciousness.CognitiveGenesisProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W374 — End-to-end integration tests for federation stack.
 * 
 * Tests:
 * - Registry + Consensus + Runtime + Mediator + Bridge flow
 * - Telemetry integration
 * - Cognitive modulation end-to-end
 */
class FederationEndToEndTest {
    
    private ModulatorDefinition makeModulator(String id, ModulatorType type, float value) {
        return ModulatorDefinition.newBuilder()
            .setId(id).setName("test_" + id).setType(type)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(value).setMinAllowed(0.0f).setMaxAllowed(1.0f).build())
            .build();
    }
    
    @Test
    void testFullPipeline() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        // Add modulators directly (bypass consensus for setup)
        runtime.getRegistryStore().add(makeModulator(
            CognitiveModulationBridge.MOD_CORTISOL, ModulatorType.MODULATOR_TYPE_HORMONE, 0.8f));
        runtime.getRegistryStore().add(makeModulator(
            CognitiveModulationBridge.MOD_DOPAMINE, ModulatorType.MODULATOR_TYPE_HORMONE, 0.7f));
        
        // Sync mediator
        mediator.refreshFromRegistry();
        telemetry.recordMutation();
        telemetry.recordMutation();
        
        // Create a profile and modulate
        CognitiveGenesisProfile profile = new CognitiveGenesisProfile(
            0.6, 0.6, 0.5, 0.5,  // phiBinary, phiF, phiR, phiLinGauss
            0.5, 0.5, 0.5,        // interAgentPhi, stabilityPhi, crossLevelPhi
            50.0, 0.5, 0.5,       // kolmogorovK, analogicalSimilarity, conceptualExclusion
            2, 0.5, 0.5            // nkEdgeOfChaosK, memristorConductance, lSystemComplexityRatio
        );
        
        CognitiveGenesisProfile modulated = bridge.modulate(profile);
        
        // High cortisol → reduced phi
        assertTrue(modulated.phiBinary() < profile.phiBinary(),
            "Cortisol should reduce phiBinary");
        
        // High dopamine → increased phiF
        assertTrue(modulated.phiF() > profile.phiF(),
            "Dopamine should increase phiF");
        
        // Verify telemetry
        var snap = telemetry.snapshot();
        assertEquals(2, snap.registryMutations());
        assertEquals(2, snap.eventCountsByType().get("mutation"));
    }
    
    @Test
    void testConsensusWithMultipleVoters() {
        // Real consensus flow with multiple voters
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        // 3 L2 ADULT vote YES (weight 1.0 each = 3.0)
        for (int i = 0; i < 3; i++) {
            engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, 
                CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        }
        
        // 1 L5 MASTER vote NO (weight 3.0)
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, 
            CapabilityLevel.CAPABILITY_L5_MASTER, 1.0));
        
        var result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        // 3.0 / 6.0 = 0.5 < 0.67, REJECTED
        assertEquals(ConsensusStatus.CONSENSUS_REJECTED, result.status());
    }
    
    @Test
    void testFROZENPropagation() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        
        // FROZEN modulator
        ModulatorDefinition frozen = ModulatorDefinition.newBuilder()
            .setId("frozen-1").setName("test_frozen").setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder().setDefaultFloat(0.5f).build())
            .setSafety(io.matrix.federation.proto.SafetyConstraints.newBuilder()
                .setFrozen(true).setRequiresConsensus(true)
                .setMinConsensusThreshold(67)
                .setMinCapability(CapabilityLevel.CAPABILITY_L2_ADULT)
                .build())
            .build();
        
        runtime.getRegistryStore().add(frozen);
        
        // Verify cannot update
        ModulatorDefinition modified = frozen.toBuilder()
            .setDefaults(ModulatorDefaults.newBuilder().setDefaultFloat(0.9f).build())
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> 
            runtime.getRegistryStore().update(modified));
        
        // Verify cannot remove
        assertThrows(IllegalArgumentException.class, () -> 
            runtime.getRegistryStore().remove("frozen-1"));
    }
    
    @Test
    void testMediatorSnapshotConsistency() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        for (int i = 0; i < 5; i++) {
            runtime.getRegistryStore().add(makeModulator(
                "m" + i, ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        }
        mediator.refreshFromRegistry();
        
        var snap = mediator.snapshot();
        assertEquals(5, snap.totalModulators());
        assertEquals(5, snap.hormoneLevels().size());
        assertEquals(0, snap.neurotransmitterLevels().size());
        assertEquals(0, snap.signalLevels().size());
    }
    
    @Test
    void testRegistryStoreAndRuntimeCoherence() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        ModulatorRegistryStore store = runtime.getRegistryStore();
        
        // Add 3 modulators
        store.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        store.add(makeModulator("m2", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER, 0.5f));
        store.add(makeModulator("m3", ModulatorType.MODULATOR_TYPE_SIGNAL, 0.5f));
        
        // Both store and runtime should reflect
        assertEquals(3, store.size());
        
        var exported = runtime.exportRegistry();
        assertEquals(3, exported.getModulatorsCount());
    }
    
    @Test
    void testTelemetryOnConsensusFlow() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        // Simulate 5 proposals and votes
        for (int i = 0; i < 5; i++) {
            telemetry.recordProposal("APPROVED");
        }
        for (int i = 0; i < 10; i++) {
            telemetry.recordVote("YES");
        }
        
        var snap = telemetry.snapshot();
        assertEquals(15, snap.totalEvents());
        assertEquals(5, snap.proposalCountsByStatus().get("APPROVED"));
        assertEquals(10, snap.voteCountsByDecision().get("YES"));
    }
    
    @Test
    void testMediatorUpdateFlow() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        // Initial: no modulators
        assertEquals(0, mediator.snapshot().totalModulators());
        
        // Add via direct add + refresh
        runtime.getRegistryStore().add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        mediator.refreshFromRegistry();
        assertEquals(1, mediator.snapshot().totalModulators());
        
        // Set local value (does not propagate to registry)
        mediator.setLocalValue("m1", 0.9f);
        assertEquals(0.9f, mediator.getValue("m1").orElseThrow(), 0.001f);
        
        // Registry still has 0.5
        var stored = runtime.getRegistryStore().get("m1").orElseThrow();
        assertEquals(0.5f, stored.getDefaults().getDefaultFloat(), 0.001f);
    }
    
    @Test
    void testDeterminismAcrossComponents() {
        // Same seed should produce same modulator hash
        FederationRuntime runtime1 = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationRuntime runtime2 = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 99L);  // different seed
        
        // Same data
        runtime1.getRegistryStore().add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        runtime2.getRegistryStore().add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        
        // Hash should be same
        assertEquals(runtime1.getRegistryStore().calculateHash(), 
                     runtime2.getRegistryStore().calculateHash());
    }
}
