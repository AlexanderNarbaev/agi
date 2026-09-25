package io.matrix.research;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuantumEmulatorTest {

    @Test
    void testCreateEmulator() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        assertNotNull(qe);
        assertEquals(0, qe.getQubitCount());
    }

    @Test
    void testCreateQubit() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        QuantumEmulator.Qubit q = qe.createQubit();
        assertEquals(1.0, q.probabilityZero(), 0.001);
        assertEquals(0.0, q.probabilityOne(), 0.001);
    }

    @Test
    void testHadamardGate() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        qe.createQubit();
        QuantumEmulator.Qubit q = qe.applyGate(0, QuantumEmulator.Gate.HADAMARD);
        assertEquals(0.5, q.probabilityZero(), 0.01);
        assertEquals(0.5, q.probabilityOne(), 0.01);
    }

    @Test
    void testPauliXGate() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        qe.createQubit();
        QuantumEmulator.Qubit q = qe.applyGate(0, QuantumEmulator.Gate.PAULI_X);
        // |0⟩ → |1⟩: prob(0) = 0, prob(1) = 1
        assertEquals(0.0, q.probabilityZero(), 0.001);
        assertEquals(1.0, q.probabilityOne(), 0.001);
    }

    @Test
    void testPauliZGate() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        qe.createQubit();
        QuantumEmulator.Qubit q = qe.applyGate(0, QuantumEmulator.Gate.PAULI_Z);
        // Z|0⟩ = |0⟩: prob(0) = 1
        assertEquals(1.0, q.probabilityZero(), 0.001);
    }

    @Test
    void testMeasurement() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        qe.createQubit();
        int result = qe.measure(0);
        assertTrue(result == 0 || result == 1);
    }

    @Test
    void testGateEnum() {
        assertEquals(4, QuantumEmulator.Gate.values().length);
        assertNotNull(QuantumEmulator.Gate.valueOf("HADAMARD"));
    }
}
