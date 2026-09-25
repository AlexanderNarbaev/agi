package io.matrix.federation.liquid;

import java.util.*;

/**
 * W573 — Corridor Homeostat for system stability.
 *
 * Implements negative feedback with versioned corridors to keep
 * system metrics (load, temperature, error rate) within safe bounds.
 * Auto-throttles if bounds exceeded.
 */
public final class CorridorHomeostat {

    /** A corridor with min/max bounds and current value. */
    public static final class Corridor {
        private final String name;
        private final double minValue;
        private final double maxValue;
        private final double warningMin;
        private final double warningMax;
        private volatile double currentValue;
        private volatile int version;
        private volatile CorridorState state;

        public Corridor(String name, double min, double max, double warningMin, double warningMax) {
            this.name = name;
            this.minValue = min;
            this.maxValue = max;
            this.warningMin = warningMin;
            this.warningMax = warningMax;
            this.currentValue = (min + max) / 2;
            this.version = 0;
            this.state = CorridorState.NORMAL;
        }

        /**
         * Update the current value and check bounds.
         *
         * @return the new state after update
         */
        public CorridorState update(double value) {
            this.currentValue = value;
            this.version++;

            if (value < minValue || value > maxValue) {
                this.state = CorridorState.VIOLATED;
            } else if (value < warningMin || value > warningMax) {
                this.state = CorridorState.WARNING;
            } else {
                this.state = CorridorState.NORMAL;
            }
            return this.state;
        }

        /**
         * Get the throttle factor [0, 1] based on how close to bounds.
         */
        public double getThrottleFactor() {
            double range = maxValue - minValue;
            if (range <= 0) return 1.0;

            double distFromCenter = Math.abs(currentValue - (minValue + maxValue) / 2);
            double halfRange = range / 2;
            return Math.max(0.1, 1.0 - (distFromCenter / halfRange) * 0.5);
        }

        public String getName() { return name; }
        public double getMinValue() { return minValue; }
        public double getMaxValue() { return maxValue; }
        public double getCurrentValue() { return currentValue; }
        public int getVersion() { return version; }
        public CorridorState getState() { return state; }
    }

    public enum CorridorState {
        NORMAL,     // Within safe bounds
        WARNING,    // Approaching bounds
        VIOLATED    // Exceeded bounds
    }

    private final Map<String, Corridor> corridors = new HashMap<>();
    private final List<ViolationEvent> violations = new ArrayList<>();

    public record ViolationEvent(String corridorName, double value, CorridorState state, long timestampNs) {}

    /**
     * Add a corridor.
     */
    public void addCorridor(Corridor corridor) {
        corridors.put(corridor.getName(), corridor);
    }

    /**
     * Update a corridor's value.
     *
     * @return the new state
     */
    public CorridorState update(String corridorName, double value) {
        Corridor corridor = corridors.get(corridorName);
        if (corridor == null) return CorridorState.NORMAL;

        CorridorState state = corridor.update(value);
        if (state == CorridorState.VIOLATED) {
            violations.add(new ViolationEvent(corridorName, value, state, System.nanoTime()));
        }
        return state;
    }

    /**
     * Get the overall throttle factor (minimum across all corridors).
     */
    public double getThrottleFactor() {
        double minThrottle = 1.0;
        for (Corridor corridor : corridors.values()) {
            minThrottle = Math.min(minThrottle, corridor.getThrottleFactor());
        }
        return minThrottle;
    }

    /**
     * Check if any corridor is violated.
     */
    public boolean hasViolations() {
        return corridors.values().stream()
                .anyMatch(c -> c.getState() == CorridorState.VIOLATED);
    }

    /**
     * Check if any corridor is in warning state.
     */
    public boolean hasWarnings() {
        return corridors.values().stream()
                .anyMatch(c -> c.getState() == CorridorState.WARNING);
    }

    /**
     * Get a corridor by name.
     */
    public Corridor getCorridor(String name) {
        return corridors.get(name);
    }

    /**
     * Get all corridors.
     */
    public Collection<Corridor> getAllCorridors() {
        return Collections.unmodifiableCollection(corridors.values());
    }

    /**
     * Get violation history.
     */
    public List<ViolationEvent> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    /**
     * Create default system corridors.
     */
    public static CorridorHomeostat createDefault() {
        CorridorHomeostat homeostat = new CorridorHomeostat();
        homeostat.addCorridor(new Corridor("cpu_load", 0.0, 1.0, 0.0, 0.8));
        homeostat.addCorridor(new Corridor("memory_usage", 0.0, 1.0, 0.0, 0.85));
        homeostat.addCorridor(new Corridor("error_rate", 0.0, 0.1, 0.0, 0.05));
        homeostat.addCorridor(new Corridor("latency_ms", 0.0, 1000.0, 0.0, 500.0));
        return homeostat;
    }
}
