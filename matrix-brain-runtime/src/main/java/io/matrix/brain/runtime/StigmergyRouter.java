package io.matrix.brain.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * TRUE-W11 iteration #9 — Stigmergy routing.
 *
 * <p>Models ant-colony path reinforcement: each "step" between stages
 * leaves a pheromone trail. Pheromones evaporate by a factor per tick.
 * A new step reinforces by adding mass.</p>
 *
 * <p>The router can be queried with a stochastic policy: 80% pick the
 * strongest trail; 20% pick uniformly at random (exploration).</p>
 */
public final class StigmergyRouter {

    private final Map<String, Double> pheromones = new HashMap<>();
    private final double evaporation;
    private final double reinforcement;
    private final Random random;

    public StigmergyRouter(double evaporation, double reinforcement) {
        if (evaporation <= 0 || evaporation >= 1)
            throw new IllegalArgumentException("evaporation in (0, 1)");
        if (reinforcement <= 0)
            throw new IllegalArgumentException("reinforcement > 0");
        this.evaporation = evaporation;
        this.reinforcement = reinforcement;
        this.random = new Random(42L);    // deterministic
    }

    /** A mind-step takes the (from, to) edge; pheromone is reinforced. */
    public void step(String from, String to) {
        if (from == null || to == null) return;
        String edge = from + "->" + to;
        double current = pheromones.getOrDefault(edge, 0.0);
        pheromones.put(edge, current + reinforcement);
    }

    /** Evaporate all pheromones (one tick of time). */
    public void evaporate() {
        var entries = new HashMap<>(pheromones);
        for (var e : entries.entrySet()) {
            double v = e.getValue() * (1.0 - evaporation);
            if (v < 1e-9) pheromones.remove(e.getKey());
            else pheromones.put(e.getKey(), v);
        }
    }

    /** Strongest trail leaving `from`. */
    public String strongestFrom(String from) {
        String best = null;
        double bestMass = Double.NEGATIVE_INFINITY;
        for (var e : pheromones.entrySet()) {
            int arrow = e.getKey().indexOf("->");
            if (arrow < 0) continue;
            String keyFrom = e.getKey().substring(0, arrow);
            if (!keyFrom.equals(from)) continue;
            if (e.getValue() > bestMass) {
                bestMass = e.getValue();
                best = e.getKey().substring(arrow + 2);
            }
        }
        return best;
    }

    /** Stochastic policy: 80% strongest, 20% uniform random from candidates. */
    public String route(String from, String[] candidates) {
        if (candidates == null || candidates.length == 0) return null;
        if (candidates.length == 1) return candidates[0];
        if (random.nextDouble() < 0.8) {
            String best = strongestFrom(from);
            if (best != null) return best;
        }
        return candidates[random.nextInt(candidates.length)];
    }

    public Map<String, Double> pheromones() {
        return new HashMap<>(pheromones);
    }

    public int edgeCount() {
        return pheromones.size();
    }
}
