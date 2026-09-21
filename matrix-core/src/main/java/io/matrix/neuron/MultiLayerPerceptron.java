package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 428 — Multi-Layer Perceptron trained by back-propagation.
 * <p>XOR-style small networks in pure function form. ReLU hidden,
 * sigmoid output, mean-squared-error loss. Stochastic gradient descent
 * with mini-batches (size {@code batchSize}). Pure function: caller
 * supplies {@link Random} for initialisation. CONSTITUTION I-safe.
 *
 * <p>The point is to capture the algorithm in code (Rosenblatt-style
 * neural learning); use this only as an embedded warm-start or
 * pedagogical baseline, not a substitute for {@code djlearn} or
 * {@code mkl-dnn}.
 */
public final class MultiLayerPerceptron {

    private final int inputDim;
    private final int hiddenDim;
    private final int outputDim;
    private final double[][] wHidden;
    private final double[] bHidden;
    private final double[][] wOutput;
    private final double[] bOutput;
    private final Random rng;

    public MultiLayerPerceptron(int inputDim, int hiddenDim, int outputDim, Random rng) {
        if (inputDim <= 0 || hiddenDim <= 0 || outputDim <= 0) {
            throw new IllegalArgumentException("dims must be > 0");
        }
        this.inputDim = inputDim;
        this.hiddenDim = hiddenDim;
        this.outputDim = outputDim;
        this.rng = rng;
        this.wHidden = new double[hiddenDim][inputDim];
        this.bHidden = new double[hiddenDim];
        this.wOutput = new double[outputDim][hiddenDim];
        this.bOutput = new double[outputDim];
        // He initialisation (sqrt(2/inputDim))
        double scaleHidden = Math.sqrt(2.0 / inputDim);
        for (int i = 0; i < hiddenDim; i++) {
            for (int j = 0; j < inputDim; j++) {
                wHidden[i][j] = rng.nextGaussian() * scaleHidden;
            }
            bHidden[i] = 0.0;
        }
        for (int i = 0; i < outputDim; i++) {
            for (int j = 0; j < hiddenDim; j++) {
                wOutput[i][j] = rng.nextGaussian() * scaleHidden;
            }
            bOutput[i] = 0.0;
        }
    }

    private static double relu(double x) { return Math.max(0, x); }
    private static double reluDeriv(double x) { return x > 0 ? 1.0 : 0.0; }
    private static double sigmoid(double z) { return 1.0 / (1.0 + Math.exp(-z)); }

    /**
     * Train using batched SGD. {@code X} is m × inputDim, {@code Y} is m × outputDim.
     */
    public void fit(double[][] X, double[][] Y,
                    int epochs, int batchSize, double learningRate) {
        int n = X.length;
        if (n == 0) return;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        for (int ep = 0; ep < epochs; ep++) {
            // Shuffle
            for (int i = n - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = order[i]; order[i] = order[j]; order[j] = t;
            }
            for (int bs = 0; bs < n; bs += batchSize) {
                int end = Math.min(bs + batchSize, n);
                int bsSize = end - bs;
                double lr = learningRate / bsSize;

                // Gradients
                double[][] gHidden = new double[hiddenDim][inputDim];
                double[] gBiasHidden = new double[hiddenDim];
                double[][] gOutput = new double[outputDim][hiddenDim];
                double[] gBiasOutput = new double[outputDim];

                for (int s = bs; s < end; s++) {
                    int idx = order[s];
                    double[] x = X[idx];
                    double[] y = Y[idx];

                    // Forward
                    double[] hIn = new double[hiddenDim];
                    double[] hOut = new double[hiddenDim];
                    for (int i = 0; i < hiddenDim; i++) {
                        double s2 = bHidden[i];
                        for (int j = 0; j < inputDim; j++) s2 += wHidden[i][j] * x[j];
                        hIn[i] = s2;
                        hOut[i] = relu(s2);
                    }
                    double[] oIn = new double[outputDim];
                    double[] oOut = new double[outputDim];
                    for (int i = 0; i < outputDim; i++) {
                        double s2 = bOutput[i];
                        for (int j = 0; j < hiddenDim; j++) s2 += wOutput[i][j] * hOut[j];
                        oIn[i] = s2;
                        oOut[i] = sigmoid(s2);
                    }

                    // Backward output layer
                    double[] dOut = new double[outputDim];
                    for (int i = 0; i < outputDim; i++) {
                        dOut[i] = (oOut[i] - y[i]) * oOut[i] * (1 - oOut[i]);
                        for (int j = 0; j < hiddenDim; j++) gOutput[i][j] += dOut[i] * hOut[j];
                        gBiasOutput[i] += dOut[i];
                    }
                    // Backward hidden layer
                    double[] dHidden = new double[hiddenDim];
                    for (int i = 0; i < hiddenDim; i++) {
                        double err = 0;
                        for (int j = 0; j < outputDim; j++) err += dOut[j] * wOutput[j][i];
                        dHidden[i] = err * reluDeriv(hIn[i]);
                        for (int j = 0; j < inputDim; j++) gHidden[i][j] += dHidden[i] * x[j];
                        gBiasHidden[i] += dHidden[i];
                    }
                }
                // Update weights (avg over mini-batch)
                for (int i = 0; i < hiddenDim; i++)
                    for (int j = 0; j < inputDim; j++)
                        wHidden[i][j] -= lr * gHidden[i][j];
                for (int i = 0; i < hiddenDim; i++) bHidden[i] -= lr * gBiasHidden[i];
                for (int i = 0; i < outputDim; i++)
                    for (int j = 0; j < hiddenDim; j++)
                        wOutput[i][j] -= lr * gOutput[i][j];
                for (int i = 0; i < outputDim; i++) bOutput[i] -= lr * gBiasOutput[i];
            }
        }
    }

    /** Run inference on a single input vector. */
    public double[] predict(double[] x) {
        double[] hOut = new double[hiddenDim];
        for (int i = 0; i < hiddenDim; i++) {
            double s = bHidden[i];
            for (int j = 0; j < inputDim; j++) s += wHidden[i][j] * x[j];
            hOut[i] = relu(s);
        }
        double[] oOut = new double[outputDim];
        for (int i = 0; i < outputDim; i++) {
            double s = bOutput[i];
            for (int j = 0; j < hiddenDim; j++) s += wOutput[i][j] * hOut[j];
            oOut[i] = sigmoid(s);
        }
        return oOut;
    }
}
