package io.matrix.federation.liquid;

import java.util.*;

/**
 * W576 — Kinetic Modulator Simulation.
 *
 * Adds production/decay rates, receptor sensitivity, and cross-talk
 * to the modulator system. Simulates "Mood", "Stress", "Flow State",
 * and "Sleep Need" emergently.
 *
 * <h2>Physics</h2>
 * <pre>
 *   dM/dt = productionRate * receptorSensitivity - decayRate * M + crossTalk
 *   receptorSensitivity(t+1) = receptorSensitivity(t) * (1 + adaptationRate * (target - M))
 * </pre>
 */
public final class KineticModulator {

    private final String id;
    private final String name;
    private final double productionRate;    // Base production rate
    private final double decayRate;         // Decay rate
    private final double minLevel;
    private final double maxLevel;
    private final boolean frozen;           // CONSTITUTION IV: cannot be disabled

    private volatile double currentLevel;
    private volatile double receptorSensitivity; // [0, 2], 1.0 = normal
    private final double adaptationRate;

    // Cross-talk: positive = excitation, negative = inhibition
    private final Map<String, Double> crossTalkMap = new HashMap<>();

    public KineticModulator(String id, String name, double productionRate, double decayRate,
                            double minLevel, double maxLevel, double initialLevel,
                            double receptorSensitivity, double adaptationRate, boolean frozen) {
        this.id = id;
        this.name = name;
        this.productionRate = productionRate;
        this.decayRate = decayRate;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.currentLevel = initialLevel;
        this.receptorSensitivity = receptorSensitivity;
        this.adaptationRate = adaptationRate;
        this.frozen = frozen;
    }

    /**
     * Simulate one time step: dM/dt = production * sensitivity - decay * M
     *
     * @param dt time step (seconds)
     */
    public void tick(double dt) {
        // Production (modulated by receptor sensitivity)
        double production = productionRate * receptorSensitivity * dt;

        // Decay
        double decay = decayRate * currentLevel * dt;

        // Update level
        double newLevel = currentLevel + production - decay;

        // Clamp
        newLevel = Math.max(minLevel, Math.min(maxLevel, newLevel));
        currentLevel = newLevel;

        // Receptor adaptation (desensitization/sensitization)
        double target = (minLevel + maxLevel) / 2;
        double error = target - currentLevel;
        receptorSensitivity *= (1 + adaptationRate * error * dt);
        receptorSensitivity = Math.max(0.1, Math.min(2.0, receptorSensitivity));
    }

    /**
     * Apply cross-talk from another modulator.
     *
     * @param sourceId   source modulator ID
     * @param sourceLevel source modulator level
     */
    public void applyCrossTalk(String sourceId, double sourceLevel) {
        Double weight = crossTalkMap.get(sourceId);
        if (weight == null) return;

        // Cross-talk effect: weight * sourceLevel * dt
        double effect = weight * sourceLevel;
        currentLevel = Math.max(minLevel, Math.min(maxLevel, currentLevel + effect));
    }

    /**
     * Add cross-talk connection.
     *
     * @param targetId target modulator ID
     * @param weight   positive = excitation, negative = inhibition
     */
    public void addCrossTalk(String targetId, double weight) {
        crossTalkMap.put(targetId, weight);
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public double getCurrentLevel() { return currentLevel; }
    public double getReceptorSensitivity() { return receptorSensitivity; }
    public double getProductionRate() { return productionRate; }
    public double getDecayRate() { return decayRate; }
    public boolean isFrozen() { return frozen; }
    public Map<String, Double> getCrossTalkMap() { return Collections.unmodifiableMap(crossTalkMap); }

    /**
     * Get the "mood" emergent from modulator levels.
     * High dopamine + serotonin = positive mood
     * High cortisol + low serotonin = stress
     */
    public static String deriveMood(Map<String, KineticModulator> modulators) {
        double dopamine = getLevel(modulators, "DOPAMINE");
        double serotonin = getLevel(modulators, "SEROTONIN");
        double cortisol = getLevel(modulators, "CORTISOL");
        double norepinephrine = getLevel(modulators, "NOREPINEPHRINE");

        if (cortisol > 0.7 && serotonin < 0.3) return "STRESSED";
        if (dopamine > 0.7 && serotonin > 0.6) return "HAPPY";
        if (dopamine < 0.3 && serotonin < 0.3) return "LOW";
        if (norepinephrine > 0.7) return "ALERT";
        if (dopamine > 0.5 && norepinephrine > 0.5) return "FLOW";
        return "NEUTRAL";
    }

    /**
     * Get "sleep need" based on accumulated levels.
     */
    public static double getSleepNeed(Map<String, KineticModulator> modulators) {
        double adenosine = getLevel(modulators, "ADENOSINE");
        double cortisol = getLevel(modulators, "CORTISOL");
        return Math.min(1.0, adenosine * 0.7 + cortisol * 0.3);
    }

    private static double getLevel(Map<String, KineticModulator> modulators, String id) {
        KineticModulator m = modulators.get(id);
        return m != null ? m.getCurrentLevel() : 0.0;
    }
}
