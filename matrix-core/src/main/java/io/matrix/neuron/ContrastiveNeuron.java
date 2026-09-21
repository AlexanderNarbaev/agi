package io.matrix.neuron;

/**
 * DESIGN-30 — Contrastive Hebbian Learning.
 * Strengthens on positive examples, weakens on negative.
 * Pure function (CONSTITUTION I).
 */
public final class ContrastiveNeuron {

    private ContrastiveNeuron() {}

    public static EnrichedNeuron contrastive(EnrichedNeuron neuron,
                                              boolean fired, boolean expected,
                                              double learningRate) {
        if (learningRate < 0) {
            throw new IllegalArgumentException("η ≥ 0");
        }
        // Positive: fired AND expected → strengthen
        // Negative: fired AND !expected, OR !fired AND expected → weaken
        boolean correct = (fired == expected);
        double delta = correct ? +learningRate : -learningRate;
        double newMag = Math.max(0.0, Math.min(1.0, neuron.magnitude() + delta));
        return new EnrichedNeuron(neuron.table(), newMag,
                neuron.chemicalVector(), neuron.tag());
    }
}
