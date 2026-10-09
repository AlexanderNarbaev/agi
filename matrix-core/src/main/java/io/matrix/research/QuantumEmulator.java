package io.matrix.research;

import java.util.*;

/**
 * W1401 — Quantum Emulator.
 *
 * Simulates Qubits/MPS (Matrix Product States) for specific sub-tasks.
 * Classical simulation of quantum-like operations.
 */
public final class QuantumEmulator {

    public record Qubit(double alphaReal, double alphaImag, double betaReal, double betaImag) {
        public double probabilityZero() {
            return alphaReal * alphaReal + alphaImag * alphaImag;
        }
        public double probabilityOne() {
            return betaReal * betaReal + betaImag * betaImag;
        }
    }

    public enum Gate { HADAMARD, PAULI_X, PAULI_Z, CNOT }

    private final Random rng;
    private final List<Qubit> qubits = new ArrayList<>();

    public QuantumEmulator(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Create a qubit in |0⟩ state.
     */
    public Qubit createQubit() {
        Qubit q = new Qubit(1, 0, 0, 0);
        qubits.add(q);
        return q;
    }

    /**
     * Apply a quantum gate.
     */
    public Qubit applyGate(int qubitIndex, Gate gate) {
        Qubit q = qubits.get(qubitIndex);
        Qubit result;
        if (gate == Gate.HADAMARD) {
            // RECON-W34.7. This was:
            //
            //   alpha' = alpha / sqrt(2)    beta' = beta / sqrt(2)
            //
            // which SCALES both amplitudes instead of MIXING them, and is not the Hadamard
            // gate. Measured on |0>:
            //
            //   before : p0 = 1.0000  p1 = 0.0000
            //   after  : p0 = 0.5000  p1 = 0.0000   TOTAL = 0.5000
            //
            // A quantum state must satisfy p0 + p1 == 1. This one totals 0.5, so the simulator
            // was silently discarding half the probability mass on every Hadamard. Since
            // measure() samples from p0 without renormalising, every collapse was biased
            // toward the wrong outcome -- and the results still looked plausible, which is the
            // most dangerous way for a simulator to fail.
            //
            // It also compounded: H(H(|0>)) gave p0 = 0.25 rather than 1.0, because each
            // application multiplied the mass by 0.5 again.
            //
            // Hadamard is the mixing gate. For |alpha, beta>:
            //   alpha' = (alpha + beta) / sqrt(2)
            //   beta'  = (alpha - beta) / sqrt(2)
            // which is unitary, so it preserves normalisation by construction.
            double invSqrt2 = 1.0 / Math.sqrt(2);
            double newAlphaReal = (q.alphaReal() + q.betaReal()) * invSqrt2;
            double newAlphaImag = (q.alphaImag() + q.betaImag()) * invSqrt2;
            double newBetaReal = (q.alphaReal() - q.betaReal()) * invSqrt2;
            double newBetaImag = (q.alphaImag() - q.betaImag()) * invSqrt2;
            result = new Qubit(newAlphaReal, newAlphaImag, newBetaReal, newBetaImag);
        } else if (gate == Gate.PAULI_X) {
            result = new Qubit(q.betaReal(), q.betaImag(), q.alphaReal(), q.alphaImag());
        } else if (gate == Gate.PAULI_Z) {
            result = new Qubit(q.alphaReal(), q.alphaImag(), -q.betaReal(), -q.betaImag());
        } else {
            result = q; // CNOT needs 2 qubits; simplified
        }
        qubits.set(qubitIndex, result);
        return result;
    }

    /**
     * Measure a qubit (collapses to |0⟩ or |1⟩).
     */
    public int measure(int qubitIndex) {
        Qubit q = qubits.get(qubitIndex);
        double p0 = q.probabilityZero();
        int result = rng.nextDouble() < p0 ? 0 : 1;
        // Collapse
        qubits.set(qubitIndex, result == 0
            ? new Qubit(1, 0, 0, 0)
            : new Qubit(0, 0, 1, 0));
        return result;
    }

    public int getQubitCount() { return qubits.size(); }
}
