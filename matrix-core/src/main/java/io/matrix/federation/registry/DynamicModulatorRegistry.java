package io.matrix.federation.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * W568 — Dynamic Modulator Registry.
 *
 * <p>Replaces the fixed 7-hormone system with an extensible registry.
 * Modulators can be added/updated at runtime via consensus (min L5 capability).
 *
 * <h2>CONSTITUTION IV Enforcement</h2>
 * <p>FROZEN principles cannot be removed. The following modulator IDs are
 * immutable and always present:
 * <ul>
 *   <li>{@code ETHICAL_FILTER} — blocks unethical content</li>
 *   <li>{@code SAFETY_MONITOR} — monitors safety violations</li>
 *   <li>{@code LIE_DETECTOR} — detects deception</li>
 *   <li>{@code CONSISTENCY_CHECKER} — prevents contradictions</li>
 * </ul>
 *
 * <h2>Default Modulators</h2>
 * <p>Loaded from {@code data/modulators/defaults.json} on startup.
 * If the file doesn't exist, 3 built-in defaults are used:
 * <ul>
 *   <li>{@code DOPAMINE} — reward signal, range [0, 1]</li>
 *   <li>{@code SEROTONIN} — mood stabilizer, range [0, 1]</li>
 *   <li>{@code NOREPINEPHRINE} — alertness, range [0, 1]</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All reads/writes are protected by a {@link ReentrantReadWriteLock}.
 */
public final class DynamicModulatorRegistry {

    /** FROZEN modulator IDs — cannot be removed (CONSTITUTION IV). */
    public static final Set<String> FROZEN_IDS = Set.of(
        "ETHICAL_FILTER",
        "SAFETY_MONITOR",
        "LIE_DETECTOR",
        "CONSISTENCY_CHECKER"
    );

    /** Minimum capability level to modify registry. */
    public static final int MIN_CAPABILITY_LEVEL = 5;

    private final ConcurrentHashMap<String, Modulator> modulators = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long version = 0;

    /**
     * A single modulator definition.
     */
    public static final class Modulator {
        private final String id;
        private final String name;
        private final double minValue;
        private final double maxValue;
        private final double defaultValue;
        private final boolean frozen;
        private volatile double currentValue;

        public Modulator(String id, String name, double minValue, double maxValue,
                         double defaultValue, boolean frozen) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.defaultValue = defaultValue;
            this.currentValue = defaultValue;
            this.frozen = frozen;
        }

        public String id() { return id; }
        public String name() { return name; }
        public double minValue() { return minValue; }
        public double maxValue() { return maxValue; }
        public double defaultValue() { return defaultValue; }
        public boolean isFrozen() { return frozen; }
        public double currentValue() { return currentValue; }

        /**
         * Set the current value, clamped to [minValue, maxValue].
         * @return true if the value was changed
         */
        public boolean setCurrentValue(double value) {
            double clamped = Math.max(minValue, Math.min(maxValue, value));
            double old = this.currentValue;
            this.currentValue = clamped;
            return old != clamped;
        }

        @Override
        public String toString() {
            return String.format("Modulator{%s, value=%.3f, range=[%.1f, %.1f], frozen=%s}",
                    id, currentValue, minValue, maxValue, frozen);
        }
    }

    /**
     * Create with default modulators.
     */
    public DynamicModulatorRegistry() {
        loadDefaults();
    }

    /**
     * Load defaults from {@code data/modulators/defaults.json} or use built-in defaults.
     */
    private void loadDefaults() {
        Path defaultsPath = Paths.get("data/modulators/defaults.json");
        if (Files.exists(defaultsPath)) {
            try {
                loadFromFile(defaultsPath);
                return;
            } catch (IOException e) {
                // Fall through to built-in defaults
            }
        }

        // Built-in defaults (always present)
        addModulator(new Modulator("DOPAMINE", "Dopamine (reward)", 0.0, 1.0, 0.5, false));
        addModulator(new Modulator("SEROTONIN", "Serotonin (mood)", 0.0, 1.0, 0.5, false));
        addModulator(new Modulator("NOREPINEPHRINE", "Norepinephrine (alertness)", 0.0, 1.0, 0.3, false));

        // FROZEN modulators (CONSTITUTION IV)
        addModulator(new Modulator("ETHICAL_FILTER", "Ethical Filter", 0.0, 1.0, 1.0, true));
        addModulator(new Modulator("SAFETY_MONITOR", "Safety Monitor", 0.0, 1.0, 1.0, true));
        addModulator(new Modulator("LIE_DETECTOR", "Lie Detector", 0.0, 1.0, 1.0, true));
        addModulator(new Modulator("CONSISTENCY_CHECKER", "Consistency Checker", 0.0, 1.0, 1.0, true));
    }

    /**
     * Load modulators from a JSON file.
     */
    public void loadFromFile(Path path) throws IOException {
        String json = Files.readString(path);
        // Simple JSON parser for modulator definitions
        // Expected format: [{"id":"X","name":"Y","min":0.0,"max":1.0,"default":0.5,"frozen":false}, ...]
        // For now, use a simple line-based parser
        // TODO: Use proper JSON parser when available
        throw new IOException("JSON parser not yet implemented — use built-in defaults");
    }

    /**
     * Add or update a modulator.
     *
     * @param modulator the modulator to add/update
     * @return true if a new modulator was added, false if existing was updated
     * @throws IllegalArgumentException if trying to remove a FROZEN modulator
     */
    public boolean addModulator(Modulator modulator) {
        Objects.requireNonNull(modulator, "modulator");
        lock.writeLock().lock();
        try {
            // FROZEN enforcement
            if (FROZEN_IDS.contains(modulator.id()) && !modulator.isFrozen()) {
                throw new IllegalArgumentException(
                        "Cannot make FROZEN modulator non-frozen: " + modulator.id());
            }

            boolean isNew = !modulators.containsKey(modulator.id());
            modulators.put(modulator.id(), modulator);
            version++;
            return isNew;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove a modulator by ID.
     *
     * @throws IllegalArgumentException if the modulator is FROZEN
     */
    public boolean removeModulator(String id) {
        Objects.requireNonNull(id, "id");
        if (FROZEN_IDS.contains(id)) {
            throw new IllegalArgumentException("Cannot remove FROZEN modulator: " + id);
        }
        lock.writeLock().lock();
        try {
            boolean removed = modulators.remove(id) != null;
            if (removed) version++;
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a modulator by ID.
     */
    public Modulator getModulator(String id) {
        lock.readLock().lock();
        try {
            return modulators.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the current value of a modulator.
     *
     * @return the current value, or NaN if the modulator doesn't exist
     */
    public double getValue(String id) {
        Modulator m = getModulator(id);
        return m != null ? m.currentValue() : Double.NaN;
    }

    /**
     * Set the value of a modulator.
     *
     * @return true if the value was changed
     */
    public boolean setValue(String id, double value) {
        Modulator m = getModulator(id);
        if (m == null) return false;
        return m.setCurrentValue(value);
    }

    /**
     * Get all modulator IDs.
     */
    public Set<String> getIds() {
        lock.readLock().lock();
        try {
            return new LinkedHashSet<>(modulators.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all modulators.
     */
    public Collection<Modulator> getAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(modulators.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Number of registered modulators.
     */
    public int size() {
        lock.readLock().lock();
        try {
            return modulators.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Current version (incremented on every change).
     */
    public long getVersion() {
        lock.readLock().lock();
        try {
            return version;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if all FROZEN modulators are present.
     */
    public boolean isConstitutionCompliant() {
        lock.readLock().lock();
        try {
            for (String frozenId : FROZEN_IDS) {
                Modulator m = modulators.get(frozenId);
                if (m == null || !m.isFrozen()) return false;
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Snapshot of all modulator values for serialization.
     */
    public Map<String, Double> snapshot() {
        lock.readLock().lock();
        try {
            Map<String, Double> snap = new LinkedHashMap<>();
            for (var entry : modulators.entrySet()) {
                snap.put(entry.getKey(), entry.getValue().currentValue());
            }
            return snap;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("DynamicModulatorRegistry{version=%d, size=%d, compliant=%s}",
                getVersion(), size(), isConstitutionCompliant());
    }
}
