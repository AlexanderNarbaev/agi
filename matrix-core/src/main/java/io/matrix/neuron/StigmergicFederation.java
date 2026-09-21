package io.matrix.neuron;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * RUN 473 — StigmergicFederation: pheromone-based multi-agent coordination (DESIGN-60).
 *
 * <p>Implements stigmergy (Grassé 1959) for federated ensemble coordination.
 * Agents in a multi-brain ensemble deposit traces ("pheromones") in a
 * shared environment; other agents respond to those traces without
 * direct communication.
 *
 * <h2>ACO analogy (Ant Colony Optimization)</h2>
 * <ul>
 *   <li>Pheromone trail = environmental trace left by an agent</li>
 *   <li>Evaporation = decay over time (forgetting)</li>
 *   <li>Deposition = strengthening successful paths</li>
 *   <li>Ant path selection = action sampling biased by pheromone strength</li>
 * </ul>
 *
 * <h2>MATRIX application</h2>
 * <p>In MultiBrainEnsemble, each brain (Qwen, SmolLM, etc.) is an agent.
 * They deposit traces on shared M3 channels (DESIGN-08). Other brains
 * sense the traces and bias their retrieval towards high-pheromone
 * regions. This creates emergent coordination without central control.
 *
 * <h2>CONSTITUTION I</h2>
 * No wall-clock. Caller supplies RNG and time step.
 */
public final class StigmergicFederation {

    private final Map<String, Double> pheromones = new HashMap<>();
    private final double evaporationRate;
    private final double depositStrength;
    private final Random rng;

    /**
     * @param evaporationRate  fraction of pheromone that evaporates per time step (0..1)
     * @param depositStrength   amount of pheromone deposited per action
     * @param rng               random source
     */
    public StigmergicFederation(double evaporationRate, double depositStrength, Random rng) {
        if (evaporationRate < 0 || evaporationRate > 1) {
            throw new IllegalArgumentException("evaporationRate in [0, 1]");
        }
        if (depositStrength <= 0) {
            throw new IllegalArgumentException("depositStrength > 0");
        }
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.evaporationRate = evaporationRate;
        this.depositStrength = depositStrength;
        this.rng = rng;
    }

    /**
     * Deposit pheromone at a location (e.g., a model identifier + query hash).
     */
    public void deposit(String location, double amount) {
        if (location == null) throw new IllegalArgumentException("null location");
        if (amount <= 0) throw new IllegalArgumentException("amount > 0");
        double current = pheromones.getOrDefault(location, 0.0);
        pheromones.put(location, current + amount * depositStrength);
    }

    /**
     * Evaporate all pheromones by the evaporation rate.
     */
    public void evaporate() {
        for (Map.Entry<String, Double> e : pheromones.entrySet()) {
            double newVal = e.getValue() * (1.0 - evaporationRate);
            if (newVal < 0.001) {
                pheromones.remove(e.getKey());
            } else {
                pheromones.put(e.getKey(), newVal);
            }
        }
    }

    /**
     * Sample a location biased by pheromone strength.
     * Higher pheromone → more likely to be chosen.
     */
    public String sampleLocation(String[] candidates) {
        if (candidates == null || candidates.length == 0) return null;
        double[] weights = new double[candidates.length];
        double total = 0;
        for (int i = 0; i < candidates.length; i++) {
            weights[i] = pheromones.getOrDefault(candidates[i], 0.01); // min 0.01
            total += weights[i];
        }
        double r = rng.nextDouble() * total;
        double cumsum = 0;
        for (int i = 0; i < candidates.length; i++) {
            cumsum += weights[i];
            if (cumsum >= r) return candidates[i];
        }
        return candidates[candidates.length - 1];
    }

    /**
     * Get pheromone strength at a location.
     */
    public double strength(String location) {
        return pheromones.getOrDefault(location, 0.0);
    }

    /**
     * Number of pheromone-marked locations.
     */
    public int size() {
        return pheromones.size();
    }

    /**
     * Clear all pheromones.
     */
    public void clear() {
        pheromones.clear();
    }
}
