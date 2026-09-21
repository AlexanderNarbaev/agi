package io.matrix.signals;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SignalModule registry (DESIGN-06 §2): central registration and discovery.
 *
 * <p>Modules are registered by modality + version. The registry supports
 * lookup by modality (latest version) or by modality+version (specific).
 */
public final class SignalModuleRegistry {

    private final Map<String, SignalModule> byModality = new ConcurrentHashMap<>();
    private final Map<String, SignalModule> byModalityVersion = new ConcurrentHashMap<>();

    /** Register a module. */
    public void register(SignalModule module) {
        String key = module.modality() + "@" + module.version();
        byModalityVersion.put(key, module);
        // Latest version wins for modality lookup
        byModality.merge(module.modality(), module,
                (existing, newMod) -> newMod.version().compareTo(existing.version()) > 0 ? newMod : existing);
    }

    /** Get module by modality (latest version). */
    public Optional<SignalModule> get(String modality) {
        return Optional.ofNullable(byModality.get(modality));
    }

    /** Get module by modality and version. */
    public Optional<SignalModule> get(String modality, String version) {
        return Optional.ofNullable(byModalityVersion.get(modality + "@" + version));
    }

    /** List all registered modalities. */
    public java.util.Set<String> modalities() {
        return byModality.keySet();
    }

    /** List all registered module infos. */
    public java.util.List<SignalModule.ModuleInfo> listModules() {
        return byModalityVersion.values().stream()
                .map(SignalModule::info)
                .toList();
    }

    /** Unregister a module. */
    public void unregister(String modality, String version) {
        SignalModule removed = byModalityVersion.remove(modality + "@" + version);
        if (removed != null && byModality.get(modality) == removed) {
            byModality.remove(modality);
        }
    }

    /** Clear all registrations. */
    public void clear() {
        byModality.clear();
        byModalityVersion.clear();
    }
}
