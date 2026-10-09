package io.matrix.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W34.7 — a Hadamard gate that was not a Hadamard gate, and did not preserve
 * normalisation.
 *
 * <p>The existing {@code QuantumEmulatorTest.testHadamardGate} caught this, but only halfway:
 * it asserts {@code probabilityZero == 0.5} first, which the broken implementation DOES
 * satisfy, and then {@code probabilityOne == 0.5}, which it does not. The failure therefore
 * presented as "expected 0.5 but was 0.0" with no hint that a fundamental invariant had been
 * violated underneath.</p>
 *
 * <h2>What the implementation did</h2>
 * <pre>
 *   alpha' = alpha / sqrt(2)      beta' = beta / sqrt(2)     <- scaling, not mixing
 * </pre>
 *
 * <p>Hadamard is the mixing gate:</p>
 * <pre>
 *   alpha' = (alpha + beta) / sqrt(2)
 *   beta'  = (alpha - beta) / sqrt(2)
 * </pre>
 *
 * <h2>Why scaling is not merely wrong but dangerous</h2>
 * <p>For {@code |0> = (1, 0)} the two agree on {@code alpha' = 1/sqrt(2)}, so the first
 * assertion passes and the bug hides. They disagree on {@code beta'}, and the discrepancy is
 * not a small error:</p>
 *
 * <pre>
 *   measured, after H on |0>:   p0 = 0.5000   p1 = 0.0000   TOTAL = 0.5000
 * </pre>
 *
 * <p>A quantum state must satisfy {@code p0 + p1 == 1}. This one totals {@code 0.5}. Every
 * probability downstream is therefore scaled by half, and {@code measure()} samples from
 * {@code p0} without renormalising — so every collapse is biased toward the wrong outcome.
 * A simulator that quietly loses half the probability mass will still produce plausible
 * numbers, which is the worst way for it to fail.</p>
 */
class QuantumGateNormalisationTest {

    /**
     * Apply a gate to a fresh {@code |0>} qubit and return the resulting state.
     *
     * @param gate gate to apply
     * @return the post-gate qubit
     */
    private static QuantumEmulator.Qubit applyToZero(QuantumEmulator.Gate gate) {
        QuantumEmulator qe = new QuantumEmulator(42L);
        qe.createQubit();
        return qe.applyGate(0, gate);
    }

    @Test
    @DisplayName("Hadamard on |0> produces an equal superposition")
    void hadamardProducesEqualSuperposition() {
        QuantumEmulator.Qubit q = applyToZero(QuantumEmulator.Gate.HADAMARD);
        assertThat(q.probabilityZero()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(q.probabilityOne()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    @DisplayName("Hadamard on |1> produces an equal superposition")
    void hadamardOnOneProducesEqualSuperposition() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        qe.createQubit();
        qe.applyGate(0, QuantumEmulator.Gate.PAULI_X);   // |0> -> |1>
        QuantumEmulator.Qubit q = qe.applyGate(0, QuantumEmulator.Gate.HADAMARD);
        assertThat(q.probabilityZero()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(q.probabilityOne()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    @DisplayName("every gate preserves total probability -- the invariant that was broken")
    void everyGatePreservesNormalisation() {
        for (QuantumEmulator.Gate gate : new QuantumEmulator.Gate[]{
                QuantumEmulator.Gate.HADAMARD,
                QuantumEmulator.Gate.PAULI_X,
                QuantumEmulator.Gate.PAULI_Z}) {
            QuantumEmulator.Qubit q = applyToZero(gate);
            double total = q.probabilityZero() + q.probabilityOne();
            assertThat(total)
                    .as("%s must preserve p0 + p1 == 1; measured %.4f", gate, total)
                    .isCloseTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
        }
    }

    @Test
    @DisplayName("Hadamard is its own inverse: H(H(|0>)) == |0>")
    void hadamardIsInvolutory() {
        QuantumEmulator qe = new QuantumEmulator(42L);
        qe.createQubit();
        qe.applyGate(0, QuantumEmulator.Gate.HADAMARD);
        QuantumEmulator.Qubit twice = qe.applyGate(0, QuantumEmulator.Gate.HADAMARD);
        // The universal NOT. A scaling implementation cannot produce this.
        assertThat(twice.probabilityZero()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(twice.probabilityOne()).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-9));
    }
}