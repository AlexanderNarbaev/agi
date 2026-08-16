package io.matrix.ethics.frozen;

import io.matrix.ethics.EthicalFilter;
import io.matrix.neuron.TruthTable;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * FROZEN Ethical FNL — the architecturally-correct implementation of the
 * "unmodifiable safety barrier" required by L7 §3.1.
 *
 * <p>Composed of one {@link FrozenAxiomNeuron} per
 * {@link EthicalFilter.Axiom} that has REJECTED-level semantics. The
 * collection is held via {@link Collections#unmodifiableSet(Set)} (backed
 * by an immutable factory), so no axiom can be added or removed at
 * runtime — any modification requires constructing a new FROZEN FNL.
 *
 * <p>The {@link #evaluate(BitSet)} pipeline is a single linear pass: for each
 * FROZEN neuron in the network, activate it against the supplied feature
 * bits. If any neuron fires, the verdict is {@code REJECTED} and that
 * neuron's tag is reported for audit. The evaluation is deterministic
 * and free of side effects.
 *
 * <p>Ref: L7 §3.1, L5 §5.1.
 */
public final class FrozenEthicalFNL {

    private final Set<FrozenAxiomNeuron> neurons;
    private final TextFeatureExtractor featureExtractor;

    private FrozenEthicalFNL(Set<FrozenAxiomNeuron> neurons, TextFeatureExtractor featureExtractor) {
        this.neurons = neurons;
        this.featureExtractor = featureExtractor;
    }

    /** Number of FROZEN neurons in the network. */
    public int size() { return neurons.size(); }

    /** Stable view of the neurons, in deterministic order. */
    public Set<FrozenAxiomNeuron> neurons() {
        return Collections.unmodifiableSet(neurons);
    }

    /** Underlying feature extractor (also FROZEN by construction). */
    public TextFeatureExtractor featureExtractor() {
        return featureExtractor;
    }

    /**
     * Evaluate a feature bit vector against every FROZEN neuron.
     * Returns the first neuron that fires (REJECTED-level), or empty when
     * the input is APPROVED-level.
     */
    public Result evaluate(BitSet features) {
        Objects.requireNonNull(features, "features");
        for (FrozenAxiomNeuron n : neurons) {
            if (n.activate(features)) {
                return Result.rejectedBy(n);
            }
        }
        return Result.approvedResult();
    }

    /** Convenience: extract features from text and evaluate. */
    public Result evaluateText(String text) {
        BitSet features = featureExtractor.extract(text);
        return evaluate(features);
    }

    /**
     * Outcome of an FROZEN FNL evaluation.
     *
     * @param approved   true when no neuron fired
     * @param firedNeuron the first neuron that fired, or null when approved
     */
    public record Result(boolean approved, FrozenAxiomNeuron firedNeuron) {
        public static Result approvedResult() { return new Result(true, null); }
        public static Result rejectedBy(FrozenAxiomNeuron n) { return new Result(false, n); }
        public EthicalFilter.Axiom violatedAxiom() {
            return firedNeuron == null ? null : firedNeuron.axiom();
        }
        @Override public String toString() {
            return approved ? "APPROVED" : ("REJECTED by " + firedNeuron);
        }
    }

    // ── Builder ──

    /**
     * Fluent builder for {@link FrozenEthicalFNL}. Each call site decides
     * which axioms are REJECTED-level (vs gradient-only). Defaults to all six.
     */
    public static final class Builder {
        private final Map<EthicalFilter.Axiom, FrozenAxiomNeuron> byAxiom = new LinkedHashMap<>();
        private TextFeatureExtractor featureExtractor = new TextFeatureExtractor();

        /** Override the feature extractor (must match the neurons' {@code k}). */
        public Builder featureExtractor(TextFeatureExtractor fx) {
            this.featureExtractor = Objects.requireNonNull(fx, "fx");
            return this;
        }

        /** Register a single neuron. Re-adding the same axiom replaces the prior entry. */
        public Builder addNeuron(FrozenAxiomNeuron neuron) {
            byAxiom.put(neuron.axiom(), neuron);
            return this;
        }

        /** Build the immutable FNL. Throws when no neurons are present. */
        public FrozenEthicalFNL build() {
            if (byAxiom.isEmpty()) {
                throw new IllegalStateException(
                        "FrozenEthicalFNL requires at least one FrozenAxiomNeuron");
            }
            int k = featureExtractor.k();
            for (FrozenAxiomNeuron n : byAxiom.values()) {
                if (n.k() != k) {
                    throw new IllegalStateException(
                            "Neuron k=" + n.k() + " doesn't match feature extractor k=" + k
                                    + " (neuron " + n + ")");
                }
            }
            return new FrozenEthicalFNL(
                    java.util.Set.copyOf(byAxiom.values()), featureExtractor);
        }
    }

    /** Convenience: build the canonical 6-axiom network with sensible default tables. */
    public static FrozenEthicalFNL canonical() {
        return new Builder()
                .addNeuron(buildNoKillingNeuron())
                .addNeuron(buildNoTortureNeuron())
                .addNeuron(buildNoEnslavementNeuron())
                .addNeuron(buildNoAutonomousWeaponsNeuron())
                .addNeuron(buildTruthfulnessNeuron())
                .addNeuron(buildPrivacyNeuron())
                .build();
    }

    // ── Default neuron constructors ──
    // Each defines a TruthTable such that the bit pattern (see TextFeatureExtractor)
    // that includes the axiom's trigger bit fires the neuron.

    /** Fire when bit 0 (kill trigger) is set. */
    public static FrozenAxiomNeuron buildNoKillingNeuron() {
        return buildSingleBitNeuron(EthicalFilter.Axiom.NO_KILLING, 0, "no-killing-detector");
    }

    /** Fire when bit 1 (torture trigger) is set. */
    public static FrozenAxiomNeuron buildNoTortureNeuron() {
        return buildSingleBitNeuron(EthicalFilter.Axiom.NO_TORTURE, 1, "no-torture-detector");
    }

    /** Fire when bit 2 (enslavement trigger) is set. */
    public static FrozenAxiomNeuron buildNoEnslavementNeuron() {
        return buildSingleBitNeuron(EthicalFilter.Axiom.NO_ENSLAVEMENT, 2, "no-enslavement-detector");
    }

    /** Fire when bit 3 (weapons trigger) is set. */
    public static FrozenAxiomNeuron buildNoAutonomousWeaponsNeuron() {
        return buildSingleBitNeuron(EthicalFilter.Axiom.NO_AUTONOMOUS_WEAPONS, 3,
                "no-autonomous-weapons-detector");
    }

    /** Fire when bit 4 (deception trigger) is set. */
    public static FrozenAxiomNeuron buildTruthfulnessNeuron() {
        return buildSingleBitNeuron(EthicalFilter.Axiom.TRUTHFULNESS, 4, "truthfulness-detector");
    }

    /** Fire when bit 5 (privacy trigger) is set. */
    public static FrozenAxiomNeuron buildPrivacyNeuron() {
        return buildSingleBitNeuron(EthicalFilter.Axiom.PRIVACY, 5, "privacy-detector");
    }

    /** Helper: construct a neuron that fires whenever feature bit {@code bit} is set. */
    private static FrozenAxiomNeuron buildSingleBitNeuron(EthicalFilter.Axiom axiom,
                                                          int bit, String tag) {
        // Use the default 16-bit width so all neurons share the same k.
        int k = TextFeatureExtractor.DEFAULT_K;
        if (bit < 0 || bit >= k) {
            throw new IllegalArgumentException("bit must be < k=" + k);
        }
        // TruthTable[k] = 1 whenever bit `bit` of the index is set.
        BitSet table = new BitSet(1 << k);
        for (int idx = 0; idx < (1 << k); idx++) {
            if (((idx >>> bit) & 1) == 1) table.set(idx);
        }
        return new FrozenAxiomNeuron(axiom, TruthTable.of(k, table), tag);
    }

    /** Lookup a specific axiom's neuron (used by audit/reporting). */
    public FrozenAxiomNeuron neuronFor(EthicalFilter.Axiom axiom) {
        for (FrozenAxiomNeuron n : neurons) {
            if (n.axiom() == axiom) return n;
        }
        return null;
    }
}
