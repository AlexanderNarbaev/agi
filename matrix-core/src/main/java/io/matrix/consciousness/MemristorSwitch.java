package io.matrix.consciousness;

import java.util.Random;

/**
 * W109 — Memristor switch model (Chua 1971, Williams HP 2008).
 *
 * <p>A memristor (memory + resistor) is a passive two-terminal circuit
 * element whose resistance depends on the history of charge that has
 * flowed through it. HP Labs (Williams et al., 2008) built the first
 * physical TiO2 memristor, confirming Chua's 1971 theoretical prediction.
 *
 * <p>The HP model: a thin TiO2 layer between two platinum electrodes,
 * with oxygen vacancies (positive charges) that drift under an applied
 * voltage. The resistance R = R_on * (w/D) + R_off * (1 - w/D), where
 * w is the width of the doped region and D the total thickness.
 *
 * <p>In MATRIX cognitive architecture, memristors model synaptic-like
 * connections whose strength depends on activation history. This connects
 * to physical substrate research:
 * <ul>
 *   <li>Neuromorphic computing (Intel Loihi, IBM TrueNorth): physical
 *       memristor arrays emulate spiking neural networks.</li>
 *   <li>DNA computing (Adleman 1994): strand displacement cascades as
 *       programmable chemical logic.</li>
 *   <li>Reservoir computing (Memristive nanowire networks): physical
 *       substrates with intrinsic memory.</li>
 * </ul>
 *
 * <p>CONSTITUTION VI compliance: a model of physical substrate dynamics,
 * not a phenomenal consciousness claim.
 */
public final class MemristorSwitch {

    private MemristorSwitch() {}

    /** Minimum conductance (off state). */
    private static final double G_OFF = 1e-6;
    /** Maximum conductance (on state). */
    private static final double G_ON = 1.0;

    /**
     * Compute the conductance trajectory under a sequence of voltage pulses.
     * Returns the conductance at each timestep.
     *
     * @param voltages sequence of applied voltages
     * @param initialW initial doped-region fraction [0, 1]
     * @param mobility ion mobility parameter
     * @return conductance trajectory (length = voltages.length + 1)
     */
    public static double[] simulate(double[] voltages, double initialW, double mobility) {
        if (voltages == null || voltages.length == 0) return new double[] {conductance(initialW)};
        double[] g = new double[voltages.length + 1];
        double w = initialW;
        g[0] = conductance(w);
        double dt = 1e-3; // time step
        double D = 10e-9;  // 10nm thickness
        for (int t = 0; t < voltages.length; t++) {
            // dw/dt = (μ_v * R_on * i(t)) / D, where i = V / R(w)
            double r = resistance(w);
            double i = voltages[t] / r;
            double dwdt = mobility * r * i / D;
            w = w + dwdt * dt;
            // Clamp to [0, 1]
            if (w < 0) w = 0;
            if (w > 1) w = 1;
            g[t + 1] = conductance(w);
        }
        return g;
    }

    /** Conductance as a function of doped-region fraction. */
    public static double conductance(double w) {
        return G_OFF + (G_ON - G_OFF) * w;
    }

    /** Resistance as a function of doped-region fraction. */
    public static double resistance(double w) {
        if (w <= 0) return 1.0 / G_OFF;
        if (w >= 1) return 1.0 / G_ON;
        double g = conductance(w);
        return 1.0 / g;
    }

    /**
     * Memristor synaptic update: given current weight and a spike,
     * update according to the Spike-Timing-Dependent Plasticity (STDP)
     * rule mapped to memristor conductance.
     *
     * <p>If the synapse was recently active (high w), positive voltage
     * increases w further (potentiation). If low w, negative voltage
     * decreases w (depression).
     */
    public static double stdpUpdate(double currentW, double deltaTime, double voltage) {
        // Hebbian STDP: Δw ∝ sign(Δt) * exp(-|Δt|/τ)
        // Here we use voltage as the trigger; Δt maps to a window
        double tau = 20.0; // ms
        double sign = deltaTime > 0 ? 1.0 : -1.0;
        double magnitude = Math.exp(-Math.abs(deltaTime) / tau);
        double deltaW = sign * magnitude * voltage * 0.1;
        double newW = currentW + deltaW;
        if (newW < 0) newW = 0;
        if (newW > 1) newW = 1;
        return newW;
    }

    /**
     * Memristor crossbar: simulate an NxM array of memristors with random
     * initial states and a sequence of input voltages. Returns the final
     * weight matrix as conductance values.
     *
     * <p>This is a discrete approximation of the physical operation used
     * in neuromorphic hardware: cross-point arrays compute V_out = W * V_in.
     */
    public static double[][] simulateCrossbar(int N, int M, long seed, double[] inputPulses) {
        Random rng = new Random(seed);
        double[][] weights = new double[N][M];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                weights[i][j] = rng.nextDouble();
            }
        }
        if (inputPulses == null || inputPulses.length == 0) return weights;
        // Simple per-row integration
        for (int t = 0; t < inputPulses.length; t++) {
            double v = inputPulses[t];
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < M; j++) {
                    double dt = 0.1; // fixed Δt
                    weights[i][j] = stdpUpdate(weights[i][j], dt, v);
                }
            }
        }
        return weights;
    }
}
