package io.matrix.neuron;

import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;

import java.util.List;

/**
 * DESIGN-25 — Hebbian learning at chain level. Co-active neurons
 * strengthen together; Oja's rule keeps magnitudes bounded.
 * Pure function (CONSTITUTION I).
 */
public final class ChainHebbian {

    private ChainHebbian() {}

    /**
     * Hebbian update: Δm = η · m · Σm_j for co-active neighbors.
     * Returns a new EnrichedNeuron with updated magnitude.
     */
    public static EnrichedNeuron strengthenOnCoFire(
            EnrichedNeuron neuron, List<EnrichedNeuron> coActive, double learningRate) {
        if (neuron == null) throw new IllegalArgumentException("null neuron");
        if (learningRate < 0) throw new IllegalArgumentException("η must be ≥ 0");
        double sumCoActive = 0;
        if (coActive != null) {
            for (EnrichedNeuron other : coActive) {
                if (other == null || other == neuron) continue;
                if (other.table().k() != neuron.table().k()) continue;
                sumCoActive += other.magnitude();
            }
        }
        double delta = learningRate * neuron.magnitude() * sumCoActive;
        double newMag = Math.min(1.0, neuron.magnitude() + delta);
        // certainty also increases
        double[] chem = neuron.chemicalVector().clone();
        if (chem.length > EnrichedNeuron.CERTAINTY) {
            chem[EnrichedNeuron.CERTAINTY] = Math.min(1.0,
                    chem[EnrichedNeuron.CERTAINTY] + 0.1 * learningRate);
        }
        return new EnrichedNeuron(neuron.table(), newMag, chem,
                EnrichedNeuron.classify(chem));
    }

    /**
     * Oja's rule: Δm = η · m · (m_j - m · m_j²)
     * Keeps weights bounded even with many co-active inputs.
     */
    public static EnrichedNeuron ojaUpdate(
            EnrichedNeuron neuron, List<EnrichedNeuron> coActive, double learningRate) {
        if (neuron == null) throw new IllegalArgumentException("null");
        if (learningRate < 0) throw new IllegalArgumentException("η must be ≥ 0");
        double sumPre = 0, sumPost = 0;
        if (coActive != null) {
            for (EnrichedNeuron other : coActive) {
                if (other == null || other == neuron) continue;
                if (other.table().k() != neuron.table().k()) continue;
                double mj = other.magnitude();
                sumPre += mj;
                sumPost += mj * mj;
            }
        }
        double delta = learningRate * neuron.magnitude()
                * (sumPre - neuron.magnitude() * sumPost);
        double newMag = Math.max(0.0, Math.min(1.0, neuron.magnitude() + delta));
        return new EnrichedNeuron(neuron.table(), newMag,
                neuron.chemicalVector(), neuron.tag());
    }
}
