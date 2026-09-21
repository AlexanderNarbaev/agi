package io.matrix.consciousness;

import java.util.Random;

/**
 * W329 — Cognitive HyperNetwork.
 *
 * <p>Inspired by HyperNetworks (Ha et al. 2016). Network that generates
 * weights for another network based on input.
 *
 * <p>For cognitive profiles: generates a custom processing network
 * for each profile, allowing personalized cognitive processing.
 *
 * <p>CONSTITUTION VI compliance: hypernetwork cognitive substrate,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveHyperNetwork {

    private CognitiveHyperNetwork() {}

    /** Generated weights for the target network. */
    public record GeneratedWeights(double[][] weights) {}

    private int inputDim;
    private int hiddenDim;
    private int outputDim;
    private long seed;
    private double[][] hyperWeights; // [inputDim × outputDim × hiddenDim]

    public CognitiveHyperNetwork(int inputDim, int hiddenDim, int outputDim, long seed) {
        if (inputDim < 1 || hiddenDim < 1 || outputDim < 1) {
            throw new IllegalArgumentException("dims must be >= 1");
        }
        this.inputDim = inputDim;
        this.hiddenDim = hiddenDim;
        this.outputDim = outputDim;
        this.seed = seed;
        Random rng = new Random(seed);
        this.hyperWeights = new double[inputDim][outputDim * hiddenDim];
        double std = 1.0 / Math.sqrt((double) inputDim);
        for (int i = 0; i < inputDim; i++) {
            for (int j = 0; j < outputDim * hiddenDim; j++) {
                hyperWeights[i][j] = rng.nextGaussian() * std;
            }
        }
    }

    /**
     * Generate target network weights from input profile embedding.
     */
    public GeneratedWeights generate(double[] input) {
        if (input == null || input.length != inputDim) {
            return new GeneratedWeights(new double[0][]);
        }
        double[] flatWeights = new double[outputDim * hiddenDim];
        for (int j = 0; j < outputDim * hiddenDim; j++) {
            double sum = 0;
            for (int i = 0; i < inputDim; i++) {
                sum += input[i] * hyperWeights[i][j];
            }
            flatWeights[j] = sum;
        }
        double[][] weights = new double[outputDim][hiddenDim];
        for (int o = 0; o < outputDim; o++) {
            for (int h = 0; h < hiddenDim; h++) {
                weights[o][h] = flatWeights[o * hiddenDim + h];
            }
        }
        return new GeneratedWeights(weights);
    }

    /**
     * Apply generated weights to input.
     */
    public double[] apply(double[] input, GeneratedWeights gw) {
        if (input == null || gw == null || gw.weights().length != outputDim) return input;
        double[] result = new double[hiddenDim];
        for (int o = 0; o < outputDim; o++) {
            double sum = 0;
            for (int h = 0; h < hiddenDim; h++) {
                sum += gw.weights()[o][h];
            }
            if (o < input.length) result[o % hiddenDim] += input[o] * sum / outputDim;
        }
        return result;
    }

    public int inputDim() { return inputDim; }
    public int hiddenDim() { return hiddenDim; }
    public int outputDim() { return outputDim; }
    public long seed() { return seed; }

    /** Hypernetwork parameter count. */
    public long parameterCount() {
        return (long) inputDim * outputDim * hiddenDim;
    }
}
