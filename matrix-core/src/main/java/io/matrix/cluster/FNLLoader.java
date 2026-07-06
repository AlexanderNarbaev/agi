package io.matrix.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.snapshot.ClusterSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic FNL (Functional Neural Layer) loader.
 *
 * <p>Supports loading FNLs from {@code .ldn} snapshot files, JIT loading by
 * name with caching, and tracking of currently loaded FNLs.
 *
 * <p>Ref: L3_Neurocluster_Arch.md §4.2, L6_Memory.md §5
 */
@ApplicationScoped
public class FNLLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<UUID, FNLMetadata> loaded = new ConcurrentHashMap<>();
    private final Map<String, FNLMetadata> jitCache = new ConcurrentHashMap<>();
    private final Path baseDir;

    public FNLLoader() {
        this(null);
    }

    public FNLLoader(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Loads an FNL from a {@code .ldn} snapshot file.
     *
     * <p>Parses the file as a {@link ClusterSnapshot} JSON and extracts
     * neuron count and creation timestamp. The FNL name is derived from
     * the filename (without extension).
     *
     * @param ldnPath path to the .ldn file
     * @return metadata of the loaded FNL
     * @throws IOException if the file cannot be read or parsed
     */
    public FNLMetadata load(Path ldnPath) throws IOException {
        String json = Files.readString(ldnPath);
        ClusterSnapshot snapshot = MAPPER.readValue(json, ClusterSnapshot.class);

        String name = ldnPath.getFileName().toString();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            name = name.substring(0, dotIdx);
        }

        FNLMetadata metadata = new FNLMetadata(
                UUID.randomUUID(),
                name,
                snapshot.neuronCount(),
                0,
                0.0,
                snapshot.createdAt());

        loaded.put(metadata.fnlId(), metadata);
        return metadata;
    }

    /**
     * Unloads an FNL and frees associated resources.
     *
     * @param fnlId the FNL identifier to unload
     */
    public void unload(UUID fnlId) {
        FNLMetadata removed = loaded.remove(fnlId);
        if (removed != null) {
            jitCache.remove(removed.name());
        }
    }

    /**
     * Lists all currently loaded FNLs.
     *
     * @return unmodifiable list of loaded FNL metadata
     */
    public List<FNLMetadata> listLoaded() {
        return List.copyOf(loaded.values());
    }

    /**
     * JIT-loads an FNL by name: loads on first access, caches for future calls.
     *
     * <p>If the FNL is already in the JIT cache, returns the cached metadata.
     * Otherwise, attempts to load from {@code {baseDir}/{name}.ldn} if a base
     * directory is configured. If no file is found, creates a synthetic
     * metadata entry and registers it.
     *
     * @param fnlName the functional name ("vision", "motor", "language")
     * @return metadata of the loaded FNL
     */
    public FNLMetadata jitLoad(String fnlName) {
        return jitCache.computeIfAbsent(fnlName, name -> {
            if (baseDir != null) {
                Path ldnPath = baseDir.resolve(name + ".ldn");
                if (Files.exists(ldnPath)) {
                    try {
                        return load(ldnPath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to JIT-load FNL: " + name, e);
                    }
                }
            }
            FNLMetadata synthetic = new FNLMetadata(
                    UUID.randomUUID(),
                    name,
                    0,
                    0,
                    0.0,
                    System.currentTimeMillis());
            loaded.put(synthetic.fnlId(), synthetic);
            return synthetic;
        });
    }

    /**
     * Checks whether an FNL with the given ID is currently loaded.
     *
     * @param fnlId the FNL identifier
     * @return true if loaded
     */
    public boolean isLoaded(UUID fnlId) {
        return loaded.containsKey(fnlId);
    }
}
