package io.matrix.embodied;

import java.util.*;

/**
 * W871 — Active Inference Engine.
 *
 * Implements the Free Energy Principle:
 * Actions are chosen to minimize surprise (prediction error).
 */
public final class ActiveInferenceEngine {

    public record Action(String name, double expectedFreeEnergy) {}
    public record Prediction(double[] predicted, double confidence) {}
    public record Observation(double[] actual) {}

    private final Random rng;
    private double[] priorBeliefs;

    public ActiveInferenceEngine(int stateDim, long seed) {
        this.rng = new Random(seed);
        this.priorBeliefs = new double[stateDim];
        // Initialize with random beliefs
        for (int i = 0; i < stateDim; i++) {
            priorBeliefs[i] = rng.nextGaussian() * 0.1;
        }
    }

    /**
     * Choose action that minimizes expected free energy.
     */
    public Action selectAction(List<Action> candidates, Observation observation) {
        // Compute prediction error
        double[] predictionError = new double[priorBeliefs.length];
        for (int i = 0; i < predictionError.length; i++) {
            if (i < observation.actual().length) {
                predictionError[i] = observation.actual()[i] - priorBeliefs[i];
            }
        }

        // Update beliefs
        for (int i = 0; i < priorBeliefs.length; i++) {
            priorBeliefs[i] += predictionError[i] * 0.1;
        }

        // Select action with lowest expected free energy
        return candidates.stream()
            .min(Comparator.comparingDouble(Action::expectedFreeEnergy))
            .orElse(new Action("noop", 0.0));
    }

    /**
     * Predict next observation based on current beliefs.
     */
    public Prediction predict() {
        double noise = 0.05;
        double[] predicted = new double[priorBeliefs.length];
        for (int i = 0; i < predicted.length; i++) {
            predicted[i] = priorBeliefs[i] + rng.nextGaussian() * noise;
        }
        return new Prediction(predicted, 0.9);
    }

    public double[] getBeliefs() {
        return priorBeliefs.clone();
    }
}
