package io.matrix.neuron;

/**
 * DESIGN-29 — Spike-Timing-Dependent Plasticity (STDP) for boolean
 * neurons. Pure function (CONSTITUTION I).
 */
public final class StdpUpdate {

    public static final double DEFAULT_TAU_PLUS_MS = 20.0;
    public static final double DEFAULT_TAU_MINUS_MS = 20.0;
    public static final double DEFAULT_A_PLUS = 0.01;
    public static final double DEFAULT_A_MINUS = 0.012;

    private StdpUpdate() {}

    /**
     * Δmagnitude based on pre/post firing times. Positive Δt
     * (pre before post) → LTP (+), negative → LTD (-).
     */
    public static double deltaMagnitude(long preTimeMs, long postTimeMs) {
        return deltaMagnitude(preTimeMs, postTimeMs,
                DEFAULT_A_PLUS, DEFAULT_A_MINUS,
                DEFAULT_TAU_PLUS_MS, DEFAULT_TAU_MINUS_MS);
    }

    public static double deltaMagnitude(long preTimeMs, long postTimeMs,
                                        double aPlus, double aMinus,
                                        double tauPlus, double tauMinus) {
        double dt = (double) (postTimeMs - preTimeMs);
        if (dt > 0) {
            return aPlus * Math.exp(-dt / tauPlus);
        } else if (dt < 0) {
            return -aMinus * Math.exp(dt / tauMinus);
        }
        return 0.0;
    }

    /** Apply STDP to a neuron. */
    public static EnrichedNeuron apply(EnrichedNeuron neuron,
                                       long preTimeMs, long postTimeMs) {
        double delta = deltaMagnitude(preTimeMs, postTimeMs);
        double newMag = Math.max(0.0, Math.min(1.0, neuron.magnitude() + delta));
        return new EnrichedNeuron(neuron.table(), newMag,
                neuron.chemicalVector(), neuron.tag());
    }
}
