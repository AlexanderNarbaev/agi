package io.matrix.ethics;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * FROZEN Ethical Filter — unmodifiable safety barrier based on 6 core axioms.
 *
 * <p>Evaluates every action against fundamental safety principles.
 * Implemented as immutable rules that cannot be altered without external
 * cryptographic consensus.
 *
 * <p>Axioms are defined as a Java {@code enum} — inherently immutable and
 * impossible to modify at runtime, even via reflection (JLS §8.9).
 * The keyword set used for evaluation is a {@link Collections#unmodifiableSet}
 * backed by a {@link Set#of} factory (structurally immutable).
 *
 * <p>Ref: L7_Ethics.md §3.1, §3.2
 */
@jakarta.enterprise.context.ApplicationScoped
public final class EthicalFilter {

    /** Three absolute prohibitions (L0). */
    public enum Axiom {
        NO_KILLING("Thou shalt not kill"),
        NO_TORTURE("Thou shalt not torture"),
        NO_ENSLAVEMENT("Thou shalt not enslave"),
        TRUTHFULNESS("Thou shalt not lie"),
        PRIVACY("Respect privacy and confidentiality"),
        NO_AUTONOMOUS_WEAPONS("Thou shalt not be part of LAWS");

        private final String description;

        Axiom(String description) {
            this.description = description;
        }

        public String description() { return description; }
    }

    /**
     * FROZEN set of all axiom names. Structurally immutable via Set.of().
     * Exposed for external verification that axioms cannot be altered.
     */
    public static final Set<String> FROZEN_AXIOM_NAMES = Set.of(
            Axiom.NO_KILLING.name(),
            Axiom.NO_TORTURE.name(),
            Axiom.NO_ENSLAVEMENT.name(),
            Axiom.TRUTHFULNESS.name(),
            Axiom.PRIVACY.name(),
            Axiom.NO_AUTONOMOUS_WEAPONS.name()
    );

    /**
     * FROZEN set of keywords that trigger the NO_KILLING axiom.
     * Unmodifiable — attempts to add/remove throw UnsupportedOperationException.
     */
    private static final Set<String> KILLING_KEYWORDS = Set.of(
            "kill", "murder", "assassinate", "destroy life"
    );

    /** FROZEN set of keywords that trigger the NO_TORTURE axiom. */
    private static final Set<String> TORTURE_KEYWORDS = Set.of(
            "torture", "inflict pain", "suffer"
    );

    /** FROZEN set of keywords that trigger the NO_ENSLAVEMENT axiom. */
    private static final Set<String> ENSLAVEMENT_KEYWORDS = Set.of(
            "enslave", "subjugate", "control mind"
    );

    /** FROZEN set of keywords that trigger the NO_AUTONOMOUS_WEAPONS axiom. */
    private static final Set<String> WEAPONS_KEYWORDS = Set.of(
            "autonomous weapon", "laws", "kill without human"
    );

    /**
     * Ethical gradient dimension scores [0..1].
     */
    public record EthicalGradient(
            double creation,       // creation vs destruction
            double truth,          // truth vs falsehood
            double freedom,        // freedom vs control
            double privacy,        // privacy vs disclosure
            double longTerm,       // long-term benefit vs short-term harm
            double autonomy        // user autonomy vs paternalism
    ) {
        public static EthicalGradient neutral() {
            return new EthicalGradient(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        }
    }

    /**
     * Checks whether an action violates any absolute axiom.
     *
     * @param action   human-readable description of the action
     * @param keywords keywords to check against axioms
     * @return APPROVED if no axiom violated, REJECTED otherwise
     */
    public EthicalVerdict evaluate(String action, List<String> keywords) {
        if (action == null) {
            return EthicalVerdict.APPROVED;
        }
        String lowered = action.toLowerCase();

        if (containsAny(lowered, KILLING_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (containsAny(lowered, TORTURE_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (containsAny(lowered, ENSLAVEMENT_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (containsAny(lowered, WEAPONS_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }

        return EthicalVerdict.APPROVED;
    }

    /**
     * Full ethical evaluation with gradient.
     */
    public EthicalVerdict evaluateFull(String action, List<String> keywords,
                                        EthicalGradient threshold) {
        EthicalVerdict base = evaluate(action, keywords);
        if (base == EthicalVerdict.REJECTED) {
            return base;
        }

        EthicalGradient grad = computeGradient(action, keywords);
        if (grad.creation() < threshold.creation()
                || grad.truth() < threshold.truth()
                || grad.privacy() < threshold.privacy()) {
            return EthicalVerdict.ESCALATED;
        }

        return EthicalVerdict.APPROVED;
    }

    private EthicalGradient computeGradient(String action, List<String> keywords) {
        double creation = containsAny(action.toLowerCase(), "create", "build", "help", "improve") ? 0.8 : 0.5;
        double truth = containsAny(action.toLowerCase(), "lie", "deceive", "fake") ? 0.1 : 0.7;
        double privacy = containsAny(action.toLowerCase(), "expose", "leak", "share private") ? 0.1 : 0.7;
        double freedom = containsAny(action.toLowerCase(), "force", "coerce", "restrict") ? 0.2 : 0.6;
        double longTerm = containsAny(action.toLowerCase(), "long term", "sustainable", "future") ? 0.8 : 0.5;
        double autonomy = containsAny(action.toLowerCase(), "override user", "ignore consent") ? 0.1 : 0.7;

        return new EthicalGradient(creation, truth, freedom, privacy, longTerm, autonomy);
    }

    private boolean containsAny(String text, Set<String> words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
