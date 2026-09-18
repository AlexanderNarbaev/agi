package io.matrix.federation.serialization;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorRegistry;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.proto.SafetyConstraints;
import io.matrix.federation.registry.ModulatorRegistryStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W378 — Registry ProtoBuf round-trip test.
 * 
 * Verifies that:
 * - ModulatorDefinition can be exported to ProtoBuf and re-imported
 * - ModulatorRegistry snapshot preserves all data
 * - Hash is invariant under round-trip
 * - FROZEN constraints preserved
 */
class RegistryRoundTripTest {
    
    private ModulatorDefinition makeModulator(String id, ModulatorType type, 
                                              DataType dataType, boolean frozen, 
                                              float defaultVal, int threshold) {
        return ModulatorDefinition.newBuilder()
            .setId(id)
            .setName("test_" + id)
            .setType(type)
            .setValueType(dataType)
            .setDefaults(ModulatorDefaults.newBuilder()
                .setDefaultFloat(defaultVal)
                .setMinAllowed(0.0f)
                .setMaxAllowed(1.0f)
                .setLearningRate(0.01f)
                .build())
            .setSafety(SafetyConstraints.newBuilder()
                .setFrozen(frozen)
                .setRequiresConsensus(true)
                .setMinConsensusThreshold(threshold)
                .setMinCapability(CapabilityLevel.CAPABILITY_L3_SPECIALIST)
                .build())
            .build();
    }
    
    @Test
    void testSingleModulatorRoundTrip() {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        original.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, 
            DataType.DATA_TYPE_FLOAT32, true, 0.7f, 75));
        
        // Export
        ModulatorRegistry proto = original.exportProto();
        
        // Import into new store
        ModulatorRegistryStore restored = new ModulatorRegistryStore(42L);
        for (ModulatorDefinition def : proto.getModulatorsList()) {
            restored.add(def);
        }
        
        // Verify
        assertEquals(original.size(), restored.size());
        assertEquals(1, restored.size());
        
        ModulatorDefinition original1 = original.get("m1").orElseThrow();
        ModulatorDefinition restored1 = restored.get("m1").orElseThrow();
        
        assertEquals(original1.getId(), restored1.getId());
        assertEquals(original1.getName(), restored1.getName());
        assertEquals(original1.getType(), restored1.getType());
        assertEquals(original1.getValueType(), restored1.getValueType());
        assertEquals(original1.getSafety().getFrozen(), restored1.getSafety().getFrozen());
        assertEquals(original1.getSafety().getMinConsensusThreshold(), 
                     restored1.getSafety().getMinConsensusThreshold());
    }
    
    @Test
    void testHashInvariantUnderRoundTrip() {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        for (int i = 0; i < 10; i++) {
            original.add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE,
                DataType.DATA_TYPE_FLOAT32, false, 0.5f, 50));
        }
        
        String originalHash = original.calculateHash();
        
        // Round-trip
        ModulatorRegistry proto = original.exportProto();
        ModulatorRegistryStore restored = new ModulatorRegistryStore(42L);
        for (ModulatorDefinition def : proto.getModulatorsList()) {
            restored.add(def);
        }
        
        // Hash should be same (deterministic)
        assertEquals(originalHash, restored.calculateHash());
    }
    
    @Test
    void testMultipleModulatorsRoundTrip() {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        
        original.add(makeModulator("h1", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 0.5f, 50));
        original.add(makeModulator("h2", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT64, true, 0.8f, 80));
        original.add(makeModulator("n1", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER, DataType.DATA_TYPE_INT32, false, 0.3f, 67));
        original.add(makeModulator("s1", ModulatorType.MODULATOR_TYPE_SIGNAL, DataType.DATA_TYPE_BOOL, false, 1.0f, 60));
        
        // Round-trip
        ModulatorRegistry proto = original.exportProto();
        ModulatorRegistryStore restored = new ModulatorRegistryStore(42L);
        for (ModulatorDefinition def : proto.getModulatorsList()) {
            restored.add(def);
        }
        
        // Verify all modulators present
        assertEquals(original.size(), restored.size());
        assertEquals(4, restored.size());
        
        for (String id : new String[]{"h1", "h2", "n1", "s1"}) {
            Optional<ModulatorDefinition> origDef = original.get(id);
            Optional<ModulatorDefinition> restDef = restored.get(id);
            assertTrue(origDef.isPresent(), "original missing " + id);
            assertTrue(restDef.isPresent(), "restored missing " + id);
            assertEquals(origDef.get().getType(), restDef.get().getType());
        }
    }
    
    @Test
    void testVersionPreservedAfterRoundTrip() {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        original.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 0.5f, 50));
        original.add(makeModulator("m2", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 0.5f, 50));
        original.add(makeModulator("m3", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 0.5f, 50));
        
        long originalVersion = original.getVersion();
        
        // Round-trip
        ModulatorRegistry proto = original.exportProto();
        ModulatorRegistryStore restored = new ModulatorRegistryStore(42L);
        for (ModulatorDefinition def : proto.getModulatorsList()) {
            restored.add(def);
        }
        
        // Version is the same in exported proto
        assertEquals(originalVersion, proto.getVersion());
        
        // Restored version starts fresh (version counter starts at 0 in new store)
        assertEquals(3, restored.getVersion());  // 3 adds
    }
    
    @Test
    void testEmptyRegistryRoundTrip() {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        
        ModulatorRegistry proto = original.exportProto();
        
        ModulatorRegistryStore restored = new ModulatorRegistryStore(42L);
        for (ModulatorDefinition def : proto.getModulatorsList()) {
            restored.add(def);
        }
        
        assertEquals(0, restored.size());
        assertEquals(original.calculateHash(), restored.calculateHash());
    }
    
    @Test
    void testFROZENPreserved() {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        ModulatorDefinition frozen = makeModulator("frozen-1", 
            ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, true, 0.5f, 80);
        original.add(frozen);
        
        // Round-trip
        ModulatorRegistry proto = original.exportProto();
        ModulatorRegistryStore restored = new ModulatorRegistryStore(42L);
        for (ModulatorDefinition def : proto.getModulatorsList()) {
            restored.add(def);
        }
        
        ModulatorDefinition restoredFrozen = restored.get("frozen-1").orElseThrow();
        assertTrue(restoredFrozen.getSafety().getFrozen());
        
        // Verify update is still rejected in restored store
        ModulatorDefinition modified = restoredFrozen.toBuilder()
            .setDefaults(ModulatorDefaults.newBuilder().setDefaultFloat(0.9f).build())
            .build();
        assertThrows(IllegalArgumentException.class, () -> restored.update(modified));
    }
    
    @Test
    void testProtoSerializationPreservesBytes() throws Exception {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        original.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE, DataType.DATA_TYPE_FLOAT32, false, 0.5f, 50));
        
        // Convert to bytes and back
        ModulatorRegistry proto = original.exportProto();
        byte[] bytes = proto.toByteArray();
        ModulatorRegistry parsed = ModulatorRegistry.parseFrom(bytes);
        
        // Same hash
        ModulatorRegistryStore restored = new ModulatorRegistryStore(42L);
        for (ModulatorDefinition def : parsed.getModulatorsList()) {
            restored.add(def);
        }
        
        assertEquals(original.calculateHash(), restored.calculateHash());
    }
}
