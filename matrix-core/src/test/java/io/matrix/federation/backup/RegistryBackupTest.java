package io.matrix.federation.backup;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.DataType;
import io.matrix.federation.proto.ModulatorDefaults;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorType;
import io.matrix.federation.registry.ModulatorRegistryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RegistryBackupTest {
    
    private ModulatorDefinition makeModulator(String id, ModulatorType type) {
        return ModulatorDefinition.newBuilder()
            .setId(id).setName("test_" + id).setType(type)
            .setValueType(DataType.DATA_TYPE_FLOAT32)
            .setDefaults(ModulatorDefaults.newBuilder().setDefaultFloat(0.5f).build())
            .build();
    }
    
    @Test
    void testBackupCreatesFile(@TempDir Path tempDir) throws Exception {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        store.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE));
        
        Path backup = tempDir.resolve("test.regpb");
        RegistryBackup.BackupResult result = RegistryBackup.backup(store, backup);
        
        assertTrue(java.nio.file.Files.exists(backup));
        assertEquals(1, result.modulatorCount());
        assertEquals(backup.toString(), result.path());
    }
    
    @Test
    void testRestoreReadsBack(@TempDir Path tempDir) throws Exception {
        ModulatorRegistryStore original = new ModulatorRegistryStore(42L);
        original.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE));
        original.add(makeModulator("m2", ModulatorType.MODULATOR_TYPE_NEUROTRANSMITTER));
        original.add(makeModulator("m3", ModulatorType.MODULATOR_TYPE_SIGNAL));
        
        Path backup = tempDir.resolve("backup.regpb");
        RegistryBackup.backup(original, backup);
        
        // Restore to new store
        ModulatorRegistryStore restored = RegistryBackup.restore(backup, 42L);
        
        assertEquals(3, restored.size());
        assertEquals(original.calculateHash(), restored.calculateHash());
    }
    
    @Test
    void testEmptyRegistryBackup(@TempDir Path tempDir) throws Exception {
        ModulatorRegistryStore empty = new ModulatorRegistryStore(42L);
        
        Path backup = tempDir.resolve("empty.regpb");
        RegistryBackup.BackupResult result = RegistryBackup.backup(empty, backup);
        
        assertEquals(0, result.modulatorCount());
        assertTrue(java.nio.file.Files.exists(backup));
        
        // Restore still works
        ModulatorRegistryStore restored = RegistryBackup.restore(backup, 42L);
        assertEquals(0, restored.size());
    }
    
    @Test
    void testVerify(@TempDir Path tempDir) throws Exception {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        store.add(makeModulator("m1", ModulatorType.MODULATOR_TYPE_HORMONE));
        
        Path backup = tempDir.resolve("backup.regpb");
        RegistryBackup.backup(store, backup);
        
        assertTrue(RegistryBackup.verify(backup));
    }
    
    @Test
    void testVerifyNonexistentFile() {
        Path bogus = Path.of("/nonexistent/path/file.regpb");
        assertFalse(RegistryBackup.verify(bogus));
    }
    
    @Test
    void testBackupWithFROZEN(@TempDir Path tempDir) throws Exception {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
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
        store.add(frozen);
        
        Path backup = tempDir.resolve("backup.regpb");
        RegistryBackup.backup(store, backup);
        
        ModulatorRegistryStore restored = RegistryBackup.restore(backup, 42L);
        ModulatorDefinition restoredFrozen = restored.get("frozen-1").orElseThrow();
        assertTrue(restoredFrozen.getSafety().getFrozen());
    }
    
    @Test
    void testBackupRoundTripPreservesHash(@TempDir Path tempDir) throws Exception {
        ModulatorRegistryStore store = new ModulatorRegistryStore(42L);
        for (int i = 0; i < 20; i++) {
            store.add(makeModulator("m" + i, ModulatorType.MODULATOR_TYPE_HORMONE));
        }
        String originalHash = store.calculateHash();
        
        Path backup = tempDir.resolve("backup.regpb");
        RegistryBackup.backup(store, backup);
        ModulatorRegistryStore restored = RegistryBackup.restore(backup, 42L);
        
        assertEquals(originalHash, restored.calculateHash());
    }
}
