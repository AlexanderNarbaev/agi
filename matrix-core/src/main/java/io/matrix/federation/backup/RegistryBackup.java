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
    /**
     * Create a backup of the registry at the given path.
     *
     * @param store the registry to back up (read-only access)
     * @param target the file path to write the backup to (overwrites existing)
     * @return BackupResult describing the written file
     * @throws IOException if the file cannot be written
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
     * @param source the backup file to read
     * @param seed deterministic seed for the restored store's RNG
     * @return a new ModulatorRegistryStore populated with the backup's modulators
     * @throws IOException if the file cannot be read or parsed
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
     * Verify backup file integrity.
     *
     * A file is considered valid if:
     * - It is non-empty (size > 0)
     * - It parses as a ModulatorRegistry ProtoBuf message
     * - It contains modulators OR has version > 0
     *
     * @param source the file to verify
     * @return true if the file is a valid backup, false otherwise
     */
    public static boolean verify(Path source) {
        try {
            byte[] bytes = Files.readAllBytes(source);
            if (bytes.length == 0) {
                return false;  // Empty file is not a valid backup
            }
            ModulatorRegistry proto = ModulatorRegistry.parseFrom(bytes);
            // Valid if: non-empty file AND parses AND has either modulators or version > 0
            return proto.getModulatorsCount() > 0 || proto.getVersion() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
