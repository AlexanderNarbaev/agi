package io.matrix.federated;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Tracks federated model versions across training rounds.
 */
@ApplicationScoped
public class ModelVersioning {

    private final Map<Integer, boolean[]> versions = new ConcurrentHashMap<>();
    private int currentVersion = 0;

    /**
     * Store a new model version.
     */
    public int storeVersion(boolean[] model) {
        int version = ++currentVersion;
        versions.put(version, model.clone());
        return version;
    }

    /**
     * Get model at specific version.
     */
    public boolean[] getVersion(int version) {
        boolean[] model = versions.get(version);
        return model != null ? model.clone() : null;
    }

    /**
     * Get current version number.
     */
    public int getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Get total stored versions.
     */
    public int getVersionCount() {
        return versions.size();
    }

    /**
     * Check if model at version changed from previous.
     */
    public boolean hasChanged(int version) {
        boolean[] current = versions.get(version);
        boolean[] previous = versions.get(version - 1);
        if (current == null || previous == null) return false;
        if (current.length != previous.length) return true;
        for (int i = 0; i < current.length; i++) {
            if (current[i] != previous[i]) return true;
        }
        return false;
    }
}
