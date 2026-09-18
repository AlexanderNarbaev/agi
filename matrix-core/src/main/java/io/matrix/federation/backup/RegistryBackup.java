package io.matrix.federation.backup;

import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorRegistry;
import io.matrix.federation.registry.ModulatorRegistryStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * W379 — Registry backup/restore utility.
 * 
 * Provides snapshot and restore functionality for ModulatorRegistry.
 * Backups stored as ProtoBuf binary files.
 */
public final class RegistryBackup {
    
    /** File extension for backup files. */
    public static final String BACKUP_EXTENSION = ".regpb";
    
    /** Result of a backup or restore operation. */
    public record BackupResult(
        String path,
        int modulatorCount,
        long timestampNs,
        long fileSizeBytes
    ) {}
    
    private RegistryBackup() {}
    
    /**
     * Create a backup of the registry at the given path.
     */
    public static BackupResult backup(ModulatorRegistryStore store, Path target) throws IOException {
        ModulatorRegistry proto = store.exportProto();
        byte[] bytes = proto.toByteArray();
        Files.write(target, bytes);
        
        return new BackupResult(
            target.toString(),
            proto.getModulatorsCount(),
            System.nanoTime(),
            bytes.length
        );
    }
    
    /**
     * Restore registry from a backup file.
     * 
     * Returns a new store. Caller is responsible for replacing any
     * existing runtime's store with the restored one.
     */
    public static ModulatorRegistryStore restore(Path source, long seed) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        ModulatorRegistry proto = ModulatorRegistry.parseFrom(bytes);
        
        ModulatorRegistryStore store = new ModulatorRegistryStore(seed);
        for (ModulatorDefinition def : proto.getModulatorsList()) {
            store.add(def);
        }
        
        return store;
    }
    
    /**
     * Verify backup file integrity (parseable, has data).
     */
    public static boolean verify(Path source) {
        try {
            byte[] bytes = Files.readAllBytes(source);
            ModulatorRegistry proto = ModulatorRegistry.parseFrom(bytes);
            return proto.getModulatorsCount() > 0 || bytes.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
