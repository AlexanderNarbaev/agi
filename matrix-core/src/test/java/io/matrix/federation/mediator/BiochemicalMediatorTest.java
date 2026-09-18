package io.matrix.federation.mediator;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.integration.BiochemicalMediator;
import io.matrix.federation.runtime.FederationRuntime;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BiochemicalMediatorTest {
    
    private ModulatorDefinition sample(String id, ModulatorType type, float defaultValue) {
        return ModulatorDefinition.newBuilder()
            .setId(id)
            .setName("test_" + id)
            .setType(type)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(defaultValue)
                .setMinAllowed(0.0f)
                .setMaxAllowed(1.0f)
                .build())
            .build();
    }
    
    @Test
    void testConstruction() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        assertEquals(0, mediator.snapshot().totalModulators());
        assertEquals(1, mediator.getUpdateCount());  // constructor calls refresh
    }
    
    @Test
    void testConstructionNullRuntime() {
        assertThrows(IllegalArgumentException.class, () -> new BiochemicalMediator(null, 42L));
    }
    
    @Test
    void testRefreshFromRegistry() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(sample("cortisol", ModulatorType.MODULATOR_TYPE_HORMONE, 0.7f), 6);
        runtime.proposeAdd(sample("dopamine", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER, 0.4f), 6);
        
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        // Constructor calls refresh
        assertEquals(2, mediator.snapshot().totalModulators());
        assertEquals(Optional.of(0.7f), mediator.getValue("cortisol"));
        assertEquals(Optional.of(0.4f), mediator.getValue("dopamine"));
    }
    
    @Test
    void testSnapshotCategories() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(sample("cortisol", ModulatorType.MODULATOR_TYPE_HORMONE, 0.7f), 6);
        runtime.proposeAdd(sample("adrenaline", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f), 6);
        runtime.proposeAdd(sample("dopamine", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER, 0.4f), 6);
        runtime.proposeAdd(sample("phasic_signal", ModulatorType.MODULATOR_TYPE_SIGNAL, 0.3f), 6);
        
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        var snap = mediator.snapshot();
        
        assertEquals(2, snap.hormoneLevels().size());
        assertEquals(1, snap.neurotransmitterLevels().size());
        assertEquals(1, snap.signalLevels().size());
    }
    
    @Test
    void testSetLocalValue() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(sample("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f), 6);
        
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        mediator.setLocalValue("m1", 0.9f);
        
        assertEquals(Optional.of(0.9f), mediator.getValue("m1"));
    }
    
    @Test
    void testSetLocalValueNotInRegistry() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        mediator.setLocalValue("ghost", 0.5f);
        assertEquals(Optional.of(0.5f), mediator.getValue("ghost"));
    }
    
    @Test
    void testCategoryCounts() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(sample("c1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f), 6);
        runtime.proposeAdd(sample("c2", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f), 6);
        runtime.proposeAdd(sample("d1", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER, 0.5f), 6);
        
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        var counts = mediator.getCategoryCounts();
        
        assertEquals(2, counts.get(ModulatorType.MODULATOR_TYPE_HORMONE));
        assertEquals(1, counts.get(ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER));
    }
    
    @Test
    void testMultipleRefreshes() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        assertEquals(1, mediator.getUpdateCount());  // constructor refresh
        
        runtime.proposeAdd(sample("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f), 6);
        mediator.refreshFromRegistry();
        
        assertEquals(2, mediator.getUpdateCount());
        assertEquals(1, mediator.snapshot().totalModulators());
    }
    
    @Test
    void testGetAllValues() {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 100L);
        runtime.proposeAdd(sample("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f), 6);
        runtime.proposeAdd(sample("m2", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER, 0.4f), 6);
        
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        var all = mediator.getAllValues();
        
        assertEquals(2, all.size());
        assertEquals(0.5f, all.get("m1"), 0.001f);
        assertEquals(0.4f, all.get("m2"), 0.001f);
    }
    
    @Test
    void testRuntimeAccessor() {
        FederationRuntime runtime = new FederationRuntime(99L, CapabilityLevel.CAPABILITY_L4_EXPERT, 100L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        assertEquals(99L, mediator.getRuntime().getNodeId());
        assertEquals(CapabilityLevel.CAPABILITY_L4_EXPERT, mediator.getRuntime().getNodeCapability());
    }
}
