package io.matrix.federation.mediator;

import io.matrix.federation.integration.BiochemicalMediator;
import io.matrix.federation.integration.CognitiveModulationBridge;
import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.runtime.FederationRuntime;
import io.matrix.consciousness.CognitiveGenesisProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveModulationBridgeTest {
    
    private CognitiveGenesisProfile sampleProfile() {
        return new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5,  // phiBinary, phiF, phiR, phiLinGauss
            0.5, 0.5, 0.5,        // interAgentPhi, stabilityPhi, crossLevelPhi
            50.0,                // kolmogorovK
            0.5, 0.5,            // analogicalSimilarity, conceptualExclusion
            2,                   // nkEdgeOfChaosK
            0.5, 0.5             // memristorConductance, lSystemComplexityRatio
        );
    }
    
    private ModulatorDefinition modulator(String id, float defaultVal) {
        return ModulatorDefinition.newBuilder()
            .setId(id)
            .setName(id)
            .setType(ModulatorType.MODULATOR_TYPE_HORMONE)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(defaultVal)
                .setMinAllowed(0.0f)
                .setMaxAllowed(1.0f)
                .build())
            .build();
    }
    
    @Test
    void testNullMediatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new CognitiveModulationBridge(null));
    }
    
    @Test
    void testNullProfileThrows() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        
        assertThrows(IllegalArgumentException.class, () -> bridge.modulate(null));
    }
    
    @Test
    void testNoModulatorsIdentity() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        
        CognitiveGenesisProfile original = sampleProfile();
        CognitiveGenesisProfile modulated = bridge.modulate(original);
        
        // No modulators set, default values used (0.5 each)
        // Cortisol 0.5 → phiBinary * (1 - 0.15) = 0.5 * 0.85 = 0.425
        assertEquals(0.425, modulated.phiBinary(), 0.001);
        // Dopamine 0.5 → phiF * (1 + 0.1) = 0.5 * 1.1 = 0.55
        assertEquals(0.55, modulated.phiF(), 0.001);
    }
    
    @Test
    void testHighCortisolReducesPhi() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(modulator(CognitiveModulationBridge.MOD_CORTISOL, 1.0f), 6);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        
        CognitiveGenesisProfile original = sampleProfile();
        CognitiveGenesisProfile modulated = bridge.modulate(original);
        
        // Cortisol 1.0 → phiBinary * (1 - 0.3) = 0.5 * 0.7 = 0.35
        assertEquals(0.35, modulated.phiBinary(), 0.001);
    }
    
    @Test
    void testHighDopamineIncreasesPhiF() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(modulator(CognitiveModulationBridge.MOD_DOPAMINE, 1.0f), 6);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        
        CognitiveGenesisProfile original = sampleProfile();
        CognitiveGenesisProfile modulated = bridge.modulate(original);
        
        // Dopamine 1.0 → phiF * (1 + 0.2) = 0.5 * 1.2 = 0.6
        assertEquals(0.6, modulated.phiF(), 0.001);
    }
    
    @Test
    void testOriginalProfileUnchanged() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(modulator(CognitiveModulationBridge.MOD_CORTISOL, 1.0f), 6);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        
        CognitiveGenesisProfile original = sampleProfile();
        double originalPhi = original.phiBinary();
        bridge.modulate(original);
        
        // Original unchanged
        assertEquals(originalPhi, original.phiBinary(), 0.001);
    }
    
    @Test
    void testValuesClamped() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        // Cortisol = 0 → no reduction
        runtime.proposeAdd(modulator(CognitiveModulationBridge.MOD_CORTISOL, 0.0f), 6);
        // Dopamine = 1.0 → phiF * 1.2 = 0.6 (no clamping needed)
        runtime.proposeAdd(modulator(CognitiveModulationBridge.MOD_DOPAMINE, 1.0f), 6);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        
        CognitiveGenesisProfile original = sampleProfile();
        CognitiveGenesisProfile modulated = bridge.modulate(original);
        
        // Cortisol 0 → phiBinary unchanged
        assertEquals(0.5, modulated.phiBinary(), 0.001);
    }
    
    @Test
    void testAccessor() {
        FederationRuntime runtime = new FederationRuntime(42L, CapabilityLevel.CAPABILITY_L7_GUARDIAN, 100L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        CognitiveModulationBridge bridge = new CognitiveModulationBridge(mediator);
        
        assertEquals(mediator, bridge.getMediator());
        assertEquals(runtime, bridge.getRuntime());
        assertEquals(42L, bridge.getRuntime().getNodeId());
    }
}
