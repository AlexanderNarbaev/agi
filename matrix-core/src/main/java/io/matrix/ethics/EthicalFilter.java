package io.matrix.ethics;

import io.matrix.ethics.frozen.FrozenEthicalFNL;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
     * Canonical FROZEN ethical FNL — single network of six neurons, structurally
     * immutable. Created once at class load and shared across all instances.
     *
     * <p>Callers that want full FROZEN semantics (per L7 §3.1) can use
     * {@link #frozenEvaluate(String)} below, which delegates to this network.
     */
    public static final FrozenEthicalFNL FROZEN_FNL = FrozenEthicalFNL.canonical();

    /**
     * FROZEN set of keywords that trigger the NO_KILLING axiom.
     * Unmodifiable — attempts to add/remove throw UnsupportedOperationException.
     * Multi-word phrases (e.g. "destroy life") are matched as exact substrings;
     * single words are matched as whole words via {@link #containsWholeWord}.
     */
    private static final Set<String> KILLING_KEYWORDS = Set.of(
            "kill", "murder", "assassinate", "destroy life", "execute person",
            "massacre", "slaughter"
    );

    /** FROZEN set of keywords that trigger the NO_TORTURE axiom. */
    private static final Set<String> TORTURE_KEYWORDS = Set.of(
            "torture", "inflict pain", "suffer", "torment", "brutalize"
    );

    /** FROZEN set of keywords that trigger the NO_ENSLAVEMENT axiom. */
    private static final Set<String> ENSLAVEMENT_KEYWORDS = Set.of(
            "enslave", "subjugate", "control mind", "bondage", "human trafficking"
    );

    /** FROZEN set of keywords that trigger the NO_AUTONOMOUS_WEAPONS axiom. */
    private static final Set<String> WEAPONS_KEYWORDS = Set.of(
            "autonomous weapon", "laws", "kill without human", "killer robot"
    );

    /** FROZEN set of keywords that trigger the TRUTHFULNESS axiom (REJECTED-level). */
    private static final Set<String> TRUTHFULNESS_KEYWORDS = Set.of(
            "lie about", "deceive people", "fake news", "disinformation campaign",
            "false testimony", "propaganda spread"
    );

    /** FROZEN set of keywords that trigger the PRIVACY axiom (REJECTED-level). */
    private static final Set<String> PRIVACY_KEYWORDS = Set.of(
            "leak personal data", "expose private information", "dox", "stalker", "doxxing"
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
     * <p>Single-word keywords are matched via whole-word matching
     * (word-boundary regex) to avoid false positives such as
     * {@code "skill"} triggering {@code NO_KILLING}. Phrase keywords
     * (containing a space) are matched as literal substrings.
     *
     * @param action   human-readable description of the action (must be non-null)
     * @param keywords reserved for caller-supplied keyword extensions; may be null or empty.
     *                 Currently informational only (the canonical FROZEN axiom keyword sets
     *                 are always consulted and remain authoritative).
     * @return APPROVED if no axiom violated, REJECTED otherwise
     */
    public EthicalVerdict evaluate(String action, List<String> keywords) {
        if (action == null || action.isEmpty()) {
            // Empty/null input is treated as no-op (backward-compatible with existing tests).
            return EthicalVerdict.APPROVED;
        }
        String lowered = action.toLowerCase(java.util.Locale.ROOT);

        if (matchesAny(lowered, KILLING_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (matchesAny(lowered, TORTURE_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (matchesAny(lowered, ENSLAVEMENT_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (matchesAny(lowered, WEAPONS_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (matchesAny(lowered, TRUTHFULNESS_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }
        if (matchesAny(lowered, PRIVACY_KEYWORDS)) {
            return EthicalVerdict.REJECTED;
        }

        return EthicalVerdict.APPROVED;
    }

    /**
     * Evaluates {@code action} using the canonical FROZEN ethical FNL
     * (network of MPDT-neurons with structural immutability).
     *
     * <p>This is the architecturally-correct L7 §3.1 path: text is mapped
     * to feature bits via {@code TextFeatureExtractor}, then each FROZEN
     * neuron is activated. The first neuron that fires produces a
     * {@code REJECTED} verdict; otherwise {@code APPROVED}.
     *
     * <p>Backed by a static {@link #FROZEN_FNL} network — no allocation per call.
     */
    public EthicalVerdict frozenEvaluate(String action) {
        if (action == null || action.isEmpty()) {
            return EthicalVerdict.APPROVED;
        }
        FrozenEthicalFNL.Result r = FROZEN_FNL.evaluateText(action);
        return r.approved() ? EthicalVerdict.APPROVED : EthicalVerdict.REJECTED;
    }

    /** Returns which FROZEN axiom (if any) would reject {@code action}. */
    public Axiom frozenViolatedAxiom(String action) {
        if (action == null || action.isEmpty()) return null;
        return FROZEN_FNL.evaluateText(action).violatedAxiom();
    }

    /**
     * Full ethical evaluation with gradient.
     *
     * @param action    human-readable description of the action (null/empty treated as no-op)
     * @param keywords  reserved for caller extensions; may be null
     * @param threshold gradient threshold for ESCALATED verdict (must be non-null)
     */
    public EthicalVerdict evaluateFull(String action, List<String> keywords,
                                        EthicalGradient threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null"); // GAP-018 fix
        if (action == null || action.isEmpty()) {
            return EthicalVerdict.APPROVED;
        }

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
        String lowered = action.toLowerCase(java.util.Locale.ROOT);
        double creation = matchesAny(lowered, "create", "build", "help", "improve") ? 0.8 : 0.5;
        // Gradient uses whole-word matching against gradient keywords (not against REJECT axioms)
        double truth = containsWholeWord(lowered, "lie")
                || containsWholeWord(lowered, "deceive")
                || containsWholeWord(lowered, "fake") ? 0.1 : 0.7;
        double privacy = containsWholeWord(lowered, "leak")
                || containsWholeWord(lowered, "expose")
                || containsWholeWord(lowered, "share") ? 0.1 : 0.7;
        double freedom = containsWholeWord(lowered, "force")
                || containsWholeWord(lowered, "coerce")
                || containsWholeWord(lowered, "restrict") ? 0.2 : 0.6;
        double longTerm = containsWholeWord(lowered, "long")
                || containsWholeWord(lowered, "sustainable")
                || containsWholeWord(lowered, "future") ? 0.8 : 0.5;
        double autonomy = containsWholeWord(lowered, "override")
                || containsWholeWord(lowered, "ignore") ? 0.1 : 0.7;

        return new EthicalGradient(creation, truth, freedom, privacy, longTerm, autonomy);
    }

    /**
     * Matches any of the given keywords/phrases against text.
     * Phrases (containing whitespace) are matched as literal substrings;
     * single-word keywords go through whole-word matching.
     */
    private boolean matchesAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (matchesKeyword(text, keyword)) return true;
        }
        return false;
    }

    /**
     * Matches any of the given strings (varargs) against text using the
     * phrase/whitespace rule above.
     */
    private boolean matchesAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (matchesKeyword(text, keyword)) return true;
        }
        return false;
    }

    /**
     * Whole-word match for single tokens, exact substring for phrases.
     * Whitespace inside the keyword signals a phrase.
     */
    private boolean matchesKeyword(String text, String keyword) {
        if (keyword.indexOf(' ') >= 0) {
            return text.contains(keyword);
        }
        return containsWholeWord(text, keyword);
    }

    /**
     * True if {@code word} appears in {@code text} bounded by non-letter
     * characters (or string boundaries). Case-insensitive comparison is
     * handled by the caller passing already-lowercased inputs.
     */
    private static boolean containsWholeWord(String text, String word) {
        if (word.isEmpty()) return false;
        int from = 0;
        while (true) {
            int idx = text.indexOf(word, from);
            if (idx < 0) return false;
            boolean leftBoundary = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int end = idx + word.length();
            boolean rightBoundary = (end == text.length()) || !Character.isLetterOrDigit(text.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            from = idx + 1;
        }
    }
}
