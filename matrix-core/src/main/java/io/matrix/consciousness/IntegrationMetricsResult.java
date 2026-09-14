package io.matrix.consciousness;

/**
 * Bundle of integration metrics for one measurement.
 * Returned by {@link IntegrationMetrics} or computed by brain classes.
 */
public record IntegrationMetricsResult(
        Double publicPhiBinary,
        Double publicPhiR,
        Double publicPhiF,
        Double publicNeuralComplexity,
        Boolean publicTicklingFlag) {
    public Double phiBinary() { return publicPhiBinary; }
    public Double phiR() { return publicPhiR; }
    public Double phiF() { return publicPhiF; }
    public Double neuralComplexity() { return publicNeuralComplexity; }
    public Boolean ticklingFlag() { return publicTicklingFlag; }
}
