package io.matrix.neuron;

/**
 * DESIGN-20 — Neurotransmitter tag, derived from chemical vector
 * via threshold rules. Not trained, computed as a pure function.
 *
 * <p>Models a coarse biological-inspired categorization of neural
 * firing patterns. Does NOT claim real neuroscience — explicit
 * CONSTITUTION VI compliance.
 */
public enum Neurotransmitter {
    /** Excitation > 0.5 AND novelty > 0.8 — urgent novelty, high arousal */
    NOREPINEPHRINE,
    /** Novelty > 0.7 AND certainty > 0.5 — confident novelty, reward */
    DOPAMINE,
    /** Certainty > 0.7 AND excitation < 0.3 — calm, low arousal */
    SEROTONIN,
    /** Inhibition > 0.7 — suppressive */
    GABA,
    /** Excitation > 0.7 AND novelty > 0.3 — strong activation */
    GLUTAMATE,
    /** Certainty < 0.4 AND novelty > 0.5 — learning, attention */
    ACETYLCHOLINE;

    /** Short mnemonic for logs / UI. */
    public String shortName() {
        return switch (this) {
            case NOREPINEPHRINE -> "NE";
            case DOPAMINE -> "DA";
            case SEROTONIN -> "5HT";
            case GABA -> "GABA";
            case GLUTAMATE -> "Glu";
            case ACETYLCHOLINE -> "ACh";
        };
    }
}
