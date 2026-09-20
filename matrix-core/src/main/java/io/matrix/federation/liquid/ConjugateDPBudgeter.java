package io.matrix.federation.liquid;

import java.util.*;

/**
 * W572 — Conjugate DP Budgeter for distributed optimization.
 *
 * Implements Conjugate Gradient Descent for federated weight updates.
 * Used for HDC vector optimization across federation nodes.
 *
 * Key features:
 * - Backward value iteration with shadow prices ψ
 * - Budget allocation based on marginal utility
 * - Convergence detection
 */
public final class ConjugateDPBudgeter {

    private final int maxIterations;
    private final double convergenceThreshold;
    private final double learningRate;

    private double[] currentWeights;
    private double[] gradient;
    private double[] conjugateDirection;
    private double lastObjective;

    public ConjugateDPBudgeter(int dimensions, int maxIterations, double convergenceThreshold, double learningRate) {
        this.maxIterations = maxIterations;
        this.convergenceThreshold = convergenceThreshold;
        this.learningRate = learningRate;
        this.currentWeights = new double[dimensions];
        this.gradient = new double[dimensions];
        this.conjugateDirection = new double[dimensions];
        this.lastObjective = Double.MAX_VALUE;
    }

    /**
     * Optimize weights using conjugate gradient descent.
     *
     * @param objectiveFunction computes objective value for given weights
     * @param gradientFunction  computes gradient for given weights
     * @return optimization result
     */
    public OptimizationResult optimize(
            ObjectiveFunction objectiveFunction,
            GradientFunction gradientFunction) {

        // Initialize
        gradient = gradientFunction.compute(currentWeights);
        conjugateDirection = Arrays.copyOf(gradient, gradient.length);
        negate(conjugateDirection);

        int iteration = 0;
        double prevBeta = 0;

        for (iteration = 0; iteration < maxIterations; iteration++) {
            // Line search: find optimal step size
            double stepSize = lineSearch(objectiveFunction, gradientFunction);

            // Update weights
            for (int i = 0; i < currentWeights.length; i++) {
                currentWeights[i] += stepSize * conjugateDirection[i];
            }

            // Compute new gradient
            double[] newGradient = gradientFunction.compute(currentWeights);

            // Check convergence
            double objective = objectiveFunction.compute(currentWeights);
            double improvement = Math.abs(lastObjective - objective);
            lastObjective = objective;

            if (improvement < convergenceThreshold) {
                return new OptimizationResult(currentWeights, objective, iteration, true);
            }

            // Polak-Ribiere conjugate gradient
            double numerator = dot(newGradient, subtract(newGradient, gradient));
            double denominator = dot(gradient, gradient);
            double beta = Math.max(0, numerator / denominator);

            // Update conjugate direction
            for (int i = 0; i < conjugateDirection.length; i++) {
                conjugateDirection[i] = -newGradient[i] + beta * conjugateDirection[i];
            }

            gradient = newGradient;
        }

        return new OptimizationResult(currentWeights, lastObjective, iteration, false);
    }

    private double lineSearch(ObjectiveFunction obj, GradientFunction grad) {
        // Simple backtracking line search
        double step = learningRate;
        double[] direction = conjugateDirection;
        double directionalDeriv = dot(gradient, direction);

        for (int i = 0; i < 20; i++) {
            double[] trial = new double[currentWeights.length];
            for (int j = 0; j < trial.length; j++) {
                trial[j] = currentWeights[j] + step * direction[j];
            }

            double trialObj = obj.compute(trial);
            double currentObj = lastObjective;

            // Armijo condition
            if (trialObj <= currentObj + 0.01 * step * directionalDeriv) {
                return step;
            }
            step *= 0.5;
        }
        return step;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static double[] subtract(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] - b[i];
        }
        return result;
    }

    private static void negate(double[] v) {
        for (int i = 0; i < v.length; i++) {
            v[i] = -v[i];
        }
    }

    public double[] getCurrentWeights() {
        return currentWeights.clone();
    }

    public void setInitialWeights(double[] weights) {
        this.currentWeights = weights.clone();
    }

    public record OptimizationResult(
            double[] optimalWeights,
            double objectiveValue,
            int iterations,
            boolean converged
    ) {}

    @FunctionalInterface
    public interface ObjectiveFunction {
        double compute(double[] weights);
    }

    @FunctionalInterface
    public interface GradientFunction {
        double[] compute(double[] weights);
    }
}
