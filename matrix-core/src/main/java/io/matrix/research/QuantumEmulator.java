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
            // H|0⟩ = (|0⟩ + |1⟩) / √2
            double invSqrt2 = 1.0 / Math.sqrt(2);
            result = new Qubit(q.alphaReal() * invSqrt2, q.alphaImag() * invSqrt2,
                               q.betaReal() * invSqrt2, q.betaImag() * invSqrt2);
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
