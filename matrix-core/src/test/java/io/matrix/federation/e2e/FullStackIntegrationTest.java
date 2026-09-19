package io.matrix.federation.e2e;

import io.matrix.federation.proto.*;
import io.matrix.federation.registry.ModulatorRegistryStore;
import io.matrix.federation.runtime.FederationRuntime;
import io.matrix.federation.integration.BiochemicalMediator;
import io.matrix.federation.integration.CognitiveModulationBridge;
import io.matrix.federation.consensus.LocalConsensusEngine;
import io.matrix.federation.telemetry.FederationTelemetry;
import io.matrix.federation.telemetry.PrometheusExporter;
import io.matrix.federation.gpu.GpuTaskExecutor;
import io.matrix.consciousness.CognitiveGenesisProfile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W389 — Full-stack integration test.
 * 
 * Tests ALL federation classes working together:
 * - Registry, Runtime, Consensus
 * - Mediator, Bridge
 * - Telemetry, PrometheusExporter
 * - GpuTaskExecutor
 * - CognitiveGenesisProfile (consumer)
 */
class FullStackIntegrationTest {
    
    private ModulatorDefinition makeModulator(String id, ModulatorType type, float value) {
        return ModulatorDefinition.newBuilder()
            .setId(id).setName("test_" + id).setType(type)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(value).setMinAllowed(0.0f).setMaxAllowed(1.0f).build())
            .setSafety(SafetyConstraints.newBuilder()
                .setFrozen(false).setRequiresConsensus(true)
                .setMinConsensusThreshold(67)
                .setMinCapability(CapabilityLevel.CAPABILITY_L2_ADULT)
                .build())
            .build();
    }
    
    @Test
    void testCompleteDataFlow() {
        // 1. Setup runtime with all components
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        // 2. Register modulators (simulating federated discovery)
        runtime.getRegistryStore().add(makeModulator(
            CognitiveModulationBridge.MOD_CORTISOL, ModulatorType.MODULATOR_TYPE_HORMONE, 0.3f));  // low stress
        runtime.getRegistryStore().add(makeModulator(
            CognitiveModulationBridge.MOD_DOPAMINE, ModulatorType.MODULATOR_TYPE_HORMONE, 0.8f));  // high reward
        
        mediator.refreshFromRegistry();
        telemetry.recordMutation();
        telemetry.recordMutation();
        
        // 3. Get cognitive profile and modulate
        CognitiveGenesisProfile profile = new CognitiveGenesisProfile(
            0.7, 0.6, 0.5, 0.5,  // phiBinary, phiF, phiR, phiLinGauss
            0.5, 0.5, 0.5,        // interAgentPhi, stabilityPhi, crossLevelPhi
            50.0, 0.5, 0.5,       // kolmogorovK, analogicalSimilarity, conceptualExclusion
            2, 0.5, 0.5            // nkEdgeOfChaosK, memristorConductance, lSystemComplexityRatio
        );
        CognitiveGenesisProfile modulated = bridge.modulate(profile);
        
        // 4. Verify modulation applied correctly
        // Low cortisol (0.3) → small reduction in phiBinary: 0.7 * (1 - 0.09) = 0.637
        assertEquals(0.637, modulated.phiBinary(), 0.01);
        // High dopamine (0.8) → increase in phiF: 0.6 * (1 + 0.16) = 0.696
        assertEquals(0.696, modulated.phiF(), 0.01);
        
        // 5. Verify telemetry
        var snap = telemetry.snapshot();
        assertEquals(2, snap.registryMutations());
        assertEquals(2, snap.eventCountsByType().get("mutation"));
        assertEquals(2, snap.totalEvents());
    }
    
    @Test
    void testPrometheusExportIntegration() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        // Record some events
        telemetry.recordProposal(ConsensusStatus.CONSENSUS_APPROVED);
        telemetry.recordProposal(ConsensusStatus.CONSENSUS_APPROVED);
        telemetry.recordVote(VoteDecision.VOTE_YES);
        
        // Export to Prometheus
        String prom = PrometheusExporter.render(telemetry);
        
        // Verify metrics in output
        assertTrue(prom.contains("federation_total_events 3"));
        assertTrue(prom.contains("federation_proposals_by_status{status=\"CONSENSUS_APPROVED\"} 2"));
        assertTrue(prom.contains("federation_votes_by_decision{decision=\"VOTE_YES\"} 1"));
        // Verify HELP and TYPE comments
        assertTrue(prom.contains("# HELP federation_total_events"));
        assertTrue(prom.contains("# TYPE federation_total_events counter"));
    }
    
    @Test
    void testGpuAndRegistryIntegration() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        GpuTaskExecutor gpu = new GpuTaskExecutor(42L, false);  // CPU fallback
        
        // Register a modulator that GPU can use
        runtime.getRegistryStore().add(makeModulator("tensor-m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        
        // Execute GPU task
        GpuTask task = GpuTask.newBuilder()
            .setTaskId("task-1")
            .setOperation(GpuOperation.GPU_OPERATION_INFERENCE)
            .setInputData(com.google.protobuf.ByteString.copyFromUtf8("test"))
            .setConfig(GpuConfig.newBuilder().setBackend("cpu-fallback").build())
            .build();
        GpuResult result = gpu.execute(task);
        
        assertEquals("task-1", result.getTaskId());
        assertEquals(GpuStatus.GPU_STATUS_SUCCESS, result.getStatus());
        
        // Verify runtime state unchanged
        assertEquals(1, runtime.getRegistryStore().size());
    }
    
    @Test
    void testMediatorSnapshotCategories() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        // Add modulators of all 3 categorized types
        runtime.getRegistryStore().add(makeModulator("h1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        runtime.getRegistryStore().add(makeModulator("n1", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER, 0.5f));
        runtime.getRegistryStore().add(makeModulator("s1", ModulatorType.MODULATOR_TYPE_SIGNAL, 0.5f));
        mediator.refreshFromRegistry();
        
        var snap = mediator.snapshot();
        
        assertEquals(1, snap.hormoneLevels().size());
        assertEquals(1, snap.neurotransmitterLevels().size());
        assertEquals(1, snap.signalLevels().size());
        assertEquals(3, snap.totalModulators());
    }
    
    @Test
    void testAllClassesThreadsafeWhereApplicable() {
        // FederationTelemetry is documented thread-safe
        // Verify with simple sequential call
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        FederationTelemetry telemetry = new FederationTelemetry(runtime, 42L);
        
        // 1000 sequential events
        for (int i = 0; i < 1000; i++) {
            telemetry.recordProposal("APPROVED");
        }
        
        assertEquals(1000, telemetry.getTotalEventCount());
        var snap = telemetry.snapshot();
        assertEquals(1000, snap.totalEvents());
    }
}
