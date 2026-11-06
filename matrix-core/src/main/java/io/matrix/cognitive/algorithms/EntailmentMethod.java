package io.matrix.cognitive.algorithms;

import io.matrix.knowledge.Derivation;
import io.matrix.knowledge.DerivationStatus;
import io.matrix.knowledge.EntailmentChecker;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * W44.2 — the portfolio's exact-reasoning method.
 *
 * <p>This is a wrapper, not a reimplementation. The entailment checker in
 * {@code io.matrix.knowledge.EntailmentChecker} already decides, exactly and exhaustively,
 * whether a conclusion follows from a set of premises and rules; it is the code the knowledge
 * store uses, and its {@link DerivationStatus} vocabulary ({@code DERIVED_VALID},
 * {@code DERIVED_INVALID}, {@code UNDECIDED}) is the vocabulary of the whole knowledge layer.
 * Copying it here would create a second source of truth for the same question, and the two would
 * drift.</p>
 *
 * <p>What the wrapper adds is the part the checker does not have: a place in the portfolio, a
 * declared capability, and a precondition.</p>
 *
 * <p><b>The precondition is the atom budget.</b> The checker enumerates 2^n valuations over the
 * atoms of the theory, and refuses to work past {@link EntailmentChecker#DEFAULT_ATOM_BUDGET}
 * atoms — past it the enumeration is not affordable, and the honest answer is
 * {@link DerivationStatus#UNDECIDED} rather than a guess. The wrapper declares
 * {@link Preconditions#THEORY_WITHIN_ATOM_BUDGET} and checks it by counting the atoms the same
 * way the checker counts them, so that the method is not admitted for a theory it would have to
 * refuse.</p>
 *
 * <p>The wrapper also honours {@link ComputeBudget#maxSearchNodes()}: the enumeration visits
 * 2^atoms valuations, and if the caller's node budget cannot admit that many, the method reports
 * a resource limit rather than running past the budget.</p>
 */
public final class EntailmentMethod
        implements AlgorithmMethod<EntailmentMethod.EntailmentInput, EntailmentMethod.EntailmentOutput> {

    /** The capability id of this method. */
    public static final String ID = "reasoning.entailment.exact-enumeration";

    /** The version of this method. */
    public static final String VERSION = "1.0.0";

    /**
     * The number of theories the declared accuracy was measured over.
     *
     * <p>DERIVED from the validation corpus in the test, which asserts the corpus has exactly
     * this many cases.</p>
     */
    public static final int VALIDATION_CASES = 16;

    /**
     * The measured cost of one entailment check, in milliseconds.
     *
     * <p>MEASURED on the W44.2 development machine over the validation corpus.</p>
     */
    public static final long MEASURED_COST_MILLIS = 1L;

    /**
     * When the declared evidence was measured.
     *
     * <p>MEASURED: the date of the validation run the evidence comes from.</p>
     */
    public static final String MEASURED_AT = "2026-11-02";

    /** The theory to test: a goal, the facts asserted true, and the rules. */
    public record EntailmentInput(String goal, Collection<String> facts, List<Derivation> rules) {
    }

    /** The verdict of the checker, and how large the theory was. */
    public record EntailmentOutput(DerivationStatus status, int atomCount) {

        /** True when the conclusion follows from the premises. */
        public boolean entailed() {
            return status == DerivationStatus.DERIVED_VALID;
        }
    }

    private final EntailmentChecker checker;
    private final AlgorithmCapability capability;

    /** Builds the method with the checker's default atom budget. */
    public EntailmentMethod() {
        this(new EntailmentChecker());
    }

    /** Builds the method with an explicit atom budget. */
    public EntailmentMethod(EntailmentChecker checker) {
        if (checker == null) {
            throw new IllegalArgumentException("checker must not be null");
        }
        this.checker = checker;
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.DEDUCTIVE_REASONING),
                Set.of(Preconditions.THEORY_WITHIN_ATOM_BUDGET),
                Set.of(EntailmentInput.class.getName()),
                Set.of(EntailmentOutput.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(2^n) valuations", MEASURED_COST_MILLIS, 0),
                        "O(n) for the atom list"),
                new AlgorithmCapability.ResourceProfile(
                        "O(2^n) time, O(n) space", true, false,
                        "the theory exceeds the atom budget and the verdict is UNDECIDED"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate against the reference checker", 1.0, VALIDATION_CASES,
                        "the W44.2 entailment validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the checker has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "a theory past the atom budget yields UNDECIDED, never a guess", false,
                        "UNDECIDED when the budget is exhausted"),
                new AlgorithmCapability.VerificationStrategy(
                        "the verdict is the checker's own three-valued answer",
                        "io.matrix.knowledge.EntailmentChecker.entails"),
                null);
    }

    /**
     * The number of atoms a theory has.
     *
     * <p>This mirrors the atom collection inside {@link EntailmentChecker#entails} exactly: the
     * goal, the facts, and every rule's conclusion and premises. The mirror exists so that the
     * precondition can be checked BEFORE the checker runs; the checker itself still makes its own
     * decision, and the two agree by construction because they count the same set.</p>
     */
    public static int atomCount(EntailmentInput input) {
        if (input == null || input.goal() == null) {
            return 0;
        }
        Set<String> atoms = new LinkedHashSet<>();
        atoms.add(input.goal());
        if (input.facts() != null) {
            atoms.addAll(input.facts());
        }
        List<Derivation> rules = input.rules() == null ? List.of() : input.rules();
        for (Derivation rule : rules) {
            atoms.add(rule.conclusionClaimId());
            atoms.addAll(rule.premiseClaimIds());
        }
        return atoms.size();
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(EntailmentInput input) {
        if (input == null || input.goal() == null || input.goal().isBlank()) {
            return false;
        }
        return atomCount(input) <= EntailmentChecker.DEFAULT_ATOM_BUDGET;
    }

    @Override
    public EntailmentOutput execute(EntailmentInput input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException(
                    "precondition " + Preconditions.THEORY_WITHIN_ATOM_BUDGET + " does not hold: "
                            + "the theory has more atoms than the checker will enumerate over");
        }
        ComputeBudget effective = budget == null ? ComputeBudget.unbounded() : budget;
        int atoms = atomCount(input);
        long valuations = 1L << atoms;
        if (valuations > effective.maxSearchNodes()) {
            throw new ResourceLimitException(
                    "the enumeration needs " + valuations + " valuations and the budget admits "
                            + effective.maxSearchNodes(),
                    new EntailmentOutput(DerivationStatus.UNDECIDED, atoms));
        }
        DerivationStatus status = checker.entails(input.goal(), input.facts(),
                input.rules() == null ? List.of() : input.rules());
        return new EntailmentOutput(status, atoms);
    }
}
