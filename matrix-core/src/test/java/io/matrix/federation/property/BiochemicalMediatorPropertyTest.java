package io.matrix.federation.property;

import io.matrix.federation.integration.BiochemicalMediator;
import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.runtime.FederationRuntime;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W373 — Property-based tests for BiochemicalMediator.
 */
class BiochemicalMediatorPropertyTest {
    
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
    Arbitrary<Float> validValues() {
        return Arbitraries.floats().between(0.0f, 1.0f);
    }
    
    @Provide
    Arbitrary<Integer> nonNegCount() {
        return Arbitraries.integers().between(0, 20);
    }
    
    private ModulatorDefinition makeModulator(String id, ModulatorType type, float value) {
        return ModulatorDefinition.newBuilder()
            .setId(id)
            .setName("test_" + id)
            .setType(type)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(value)
                .setMinAllowed(0.0f)
                .setMaxAllowed(1.0f)
                .build())
            .build();
    }
    
    @Property
    void totalCountEqualsAllValuesSize(@ForAll("nonNegCount") Integer n) {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        for (int i = 0; i < n; i++) {
            runtime.getRegistryStore().add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        }
        mediator.refreshFromRegistry();
        
        assertEquals(n, mediator.getAllValues().size());
        assertEquals(n, mediator.snapshot().totalModulators());
    }
    
    @Property
    void snapshotCategorizesCorrectly(@ForAll("types") ModulatorType type) {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        runtime.getRegistryStore().add(makeModulator("m1", type, 0.5f));
        mediator.refreshFromRegistry();
        var snap = mediator.snapshot();
        
        // Total modulators should always be 1
        assertEquals(1, snap.totalModulators());
        
        // Categorized types show in category
        switch (type) {
            case MODULATOR_TYPE_HORMONE:
                assertEquals(1, snap.hormoneLevels().size());
                assertEquals(0, snap.neurotransmitterLevels().size());
                assertEquals(0, snap.signalLevels().size());
                break;
            case MODULATOR_TYPE_NEUROTRANSMITTER:
                assertEquals(0, snap.hormoneLevels().size());
                assertEquals(1, snap.neurotransmitterLevels().size());
                assertEquals(0, snap.signalLevels().size());
                break;
            case MODULATOR_TYPE_SIGNAL:
                assertEquals(0, snap.hormoneLevels().size());
                assertEquals(0, snap.neurotransmitterLevels().size());
                assertEquals(1, snap.signalLevels().size());
                break;
            default:
                // LEARNING_RATE/AROUSAL/PLASTICITY not categorized
                assertEquals(0, snap.hormoneLevels().size());
                assertEquals(0, snap.neurotransmitterLevels().size());
                assertEquals(0, snap.signalLevels().size());
        }
    }
    
    @Property
    void setLocalValueOverwrites(@ForAll("validValues") Float v1, 
                                  @ForAll("validValues") Float v2) {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        // Use setLocalValue to bypass registry, then verify behavior
        mediator.setLocalValue("m1", v1);
        assertEquals(v1, mediator.getValue("m1").orElseThrow(), 0.001f);
        
        mediator.setLocalValue("m1", v2);
        assertEquals(v2, mediator.getValue("m1").orElseThrow(), 0.001f);
    }
    
    @Property
    void categoryCountsSumToTotal(@ForAll("nonNegCount") Integer n) {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        for (int i = 0; i < n; i++) {
            runtime.getRegistryStore().add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        }
        mediator.refreshFromRegistry();
        
        var counts = mediator.getCategoryCounts();
        int sum = counts.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(n, sum);
    }
    
    @Property
    void refreshIncrementsCount(@ForAll("nonNegCount") Integer n1, 
                                  @ForAll("nonNegCount") Integer n2) {
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        for (int i = 0; i < n1; i++) {
            runtime.getRegistryStore().add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE, 0.5f));
        }
        mediator.refreshFromRegistry();
        
        int countBefore = mediator.getUpdateCount();
        mediator.refreshFromRegistry();
        
        assertEquals(countBefore + 1, mediator.getUpdateCount());
    }
    
    @Property
    void nonExistentGetReturnsEmpty(@ForAll String id) {
        Assume.that(!id.isEmpty());
        FederationRuntime runtime = new FederationRuntime(1L, CapabilityLevel.CAPABILITY_L5_MASTER, 42L);
        BiochemicalMediator mediator = new BiochemicalMediator(runtime, 42L);
        
        assertTrue(mediator.getValue(id).isEmpty());
    }
}
