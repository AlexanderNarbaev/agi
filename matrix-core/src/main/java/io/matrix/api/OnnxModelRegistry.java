package io.matrix.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RUN 82 — OnnxModelRegistry: multi-model bridge registry.
 *
 * <p>Manages multiple {@link QwenOnnxBridge} instances keyed by
 * model id. Supports different model variants (0.5B, 1.5B, etc.)
 * and different precision modes (BF16, FP16, INT8).
 *
 * <p>Each model is loaded lazily on first access and stays loaded
 * until close() is called on the registry or the JVM shuts down.
 */
public final class OnnxModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(OnnxModelRegistry.class);

    /** Identifies a registered model. */
    public record ModelId(String name, String size, String precision) {
        public static ModelId of(String name, String size, String precision) {
            return new ModelId(name, size, precision);
        }
        public String key() {
            return name + ":" + size + ":" + precision;
        }
    }

    /** Source for loading a model. */
    @FunctionalInterface
    public interface ModelLoader {
        QwenOnnxBridge load(boolean useGpu) throws IOException;
    }

    private final Map<String, ModelEntry> registry = new ConcurrentHashMap<>();
    private final boolean defaultUseGpu;

    public OnnxModelRegistry(boolean defaultUseGpu) {
        this.defaultUseGpu = defaultUseGpu;
    }

    /**
     * Register a model with a loader function. The loader is called
     * lazily on first access.
     */
    public void register(ModelId id, Path modelDir) {
        register(id, useGpu -> {
            QwenOnnxBridge bridge = new QwenOnnxBridge(modelDir);
            bridge.useGpu(useGpu);
            bridge.load();
            return bridge;
        });
    }

    /** Register with an explicit loader. */
    public void register(ModelId id, ModelLoader loader) {
        registry.put(id.key(), new ModelEntry(id, loader));
    }

    /**
     * Get (and lazily load) the bridge for this model.
     */
    public QwenOnnxBridge get(ModelId id) {
        return get(id, defaultUseGpu);
    }

    public QwenOnnxBridge get(ModelId id, boolean useGpu) {
        ModelEntry entry = registry.get(id.key());
        if (entry == null) return null;
        synchronized (entry) {
            if (!entry.loadAttempted) {
                entry.loadAttempted = true;
                try {
                    entry.bridge = entry.loader.load(useGpu);
                    if (entry.bridge != null) {
                        log.info("OnnxModelRegistry: loaded {} (gpu={})",
                                id.key(), useGpu);
                    }
                } catch (Exception e) {
                    log.warn("OnnxModelRegistry: failed to load {}: {}",
                            id.key(), e.getMessage());
                }
            }
            return entry.bridge;
        }
    }

    /** Returns true if a model is registered (regardless of load state). */
    public boolean contains(ModelId id) {
        return registry.containsKey(id.key());
    }

    /** Returns true if a model is registered AND loaded. */
    public boolean isLoaded(ModelId id) {
        ModelEntry entry = registry.get(id.key());
        return entry != null && entry.bridge != null && entry.bridge.isLoaded();
    }

    /** Number of registered models. */
    public int size() {
        return registry.size();
    }

    /** Number of currently loaded models (in memory). */
    public int loadedCount() {
        int n = 0;
        for (ModelEntry e : registry.values()) {
            if (e.bridge != null && e.bridge.isLoaded()) n++;
        }
        return n;
    }

    /** Unload all models and free memory. */
    public synchronized void closeAll() {
        for (ModelEntry e : registry.values()) {
            if (e.bridge != null) {
                try { e.bridge.close(); } catch (Exception ignored) {}
                e.bridge = null;
            }
            e.loadAttempted = false;  // allow re-load
        }
    }

    /** Snapshot of registered model ids. */
    public java.util.Set<String> registeredKeys() {
        return java.util.Set.copyOf(registry.keySet());
    }

    private static final class ModelEntry {
        final ModelId id;
        final ModelLoader loader;
        volatile QwenOnnxBridge bridge;
        volatile boolean loadAttempted;  // RUN 82: track if we tried loading
        ModelEntry(ModelId id, ModelLoader loader) {
            this.id = id;
            this.loader = loader;
        }
    }
}
