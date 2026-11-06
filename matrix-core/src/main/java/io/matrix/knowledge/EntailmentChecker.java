package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides, exactly, whether a conclusion follows from a set of premises and
 * a set of rules (RECON-W41).
 *
 * <p>This is the checker behind the mission's §6.2 "simple deductive
 * inference" and "multi-hop relational inference". It is deliberately
 * boring: a bounded enumerator over ground propositional models.
 *
 * <p><b>Why an enumerator and not a heuristic.</b> The mission's §6.3 says
 * plainly: "A BIR execution is not automatically a correct interpretation
 * of the premises", and §5.4 says an LLM adapter "may generate candidate
 * claims, but those candidates must remain provisional". A reasoner that
 * answers "probably follows" cannot be audited. This one answers
 * {@link DerivationStatus#DERIVED_VALID},
 * {@link DerivationStatus#DERIVED_INVALID} or
 * {@link DerivationStatus#UNDECIDED}, and nothing else. There is no score,
 * no count of supporting rules, no timestamp and no network access.
 *
 * <p><b>The semantics.</b> The atoms are claim identifiers. A <i>fact</i> is
 * an atom asserted true. A <i>rule</i> is a recorded {@link Derivation}:
 * its premises joined by its connective imply its conclusion. A
 * <i>model</i> is an assignment of true or false to every atom that
 * satisfies every fact and every rule. The goal is entailed when every
 * such model satisfies it.
 *
 * <p>This is the classical definition, and the enumeration is exhaustive
 * over the atom set — so within the budget the answer is exact, not
 * approximate. Beyond the budget the checker returns
 * {@link DerivationStatus#UNDECIDED}, which is an honest answer rather
 * than a guess.
 *
 * <p><b>The budget.</b> The number of valuations is 2^n in the number of
 * atoms, so the checker refuses to work past {@link #DEFAULT_ATOM_BUDGET}
 * atoms. The bound is a property of the algorithm, not a tuning knob: past
 * it, the enumeration is not affordable and the checker says so.
 *
 * <p><b>What validity does not mean.</b> That the conclusion follows from
 * the premises. It says nothing about whether the premises are true. A
 * conclusion derived from false premises is valid and false; the store
 * keeps the premise's own warrant for exactly this reason.
 */
public final class EntailmentChecker {

    /** The largest number of atoms the checker will enumerate over. */
    public static final int DEFAULT_ATOM_BUDGET = 16;

    private final int atomBudget;

    public EntailmentChecker() {
        this(DEFAULT_ATOM_BUDGET);
    }

    public EntailmentChecker(int atomBudget) {
        if (atomBudget < 1) {
            throw new IllegalArgumentException("the atom budget must be at least 1");
        }
        this.atomBudget = atomBudget;
    }

    /**
     * Whether the goal follows from the facts and the rules.
     *
     * @param goal the claim identifier to test
     * @param facts the claim identifiers asserted true
     * @param rules the recorded derivations that make up the theory
     * @return DERIVED_VALID, DERIVED_INVALID, or UNDECIDED when the theory
     *     is too large for the budget
     */
    public DerivationStatus entails(String goal, Collection<String> facts, List<Derivation> rules) {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("the goal must not be blank");
        }
        Set<String> atoms = new LinkedHashSet<>();
        atoms.add(goal);
        if (facts != null) {
            atoms.addAll(facts);
        }
        List<Derivation> theRules = rules == null ? List.of() : rules;
        for (Derivation rule : theRules) {
            atoms.add(rule.conclusionClaimId());
            atoms.addAll(rule.premiseClaimIds());
        }
        if (atoms.size() > atomBudget) {
            return DerivationStatus.UNDECIDED;
        }

        List<String> ordered = new ArrayList<>(atoms);
        boolean[] model = new boolean[ordered.size()];
        for (String fact : facts == null ? List.<String>of() : facts) {
            model[ordered.indexOf(fact)] = true;
        }

        // Every valuation of the atoms, with the facts pinned.
        for (long bits = 0; bits < (1L << ordered.size()); bits++) {
            for (int i = 0; i < ordered.size(); i++) {
                model[i] = ((bits >> i) & 1L) != 0L;
            }
            boolean consistent = true;
            for (String fact : facts == null ? List.<String>of() : facts) {
                if (!model[ordered.indexOf(fact)]) {
                    consistent = false;
                    break;
                }
            }
            if (!consistent) {
                continue;
            }
            if (!satisfiesAllRules(model, ordered, theRules)) {
                continue;
            }
            if (!model[ordered.indexOf(goal)]) {
                return DerivationStatus.DERIVED_INVALID;
            }
        }
        return DerivationStatus.DERIVED_VALID;
    }

    /**
     * Checks one derivation against the facts and the whole theory.
     *
     * <p>The derivation's own rule is part of the theory, so a derivation is
     * valid when it follows from the facts together with the rules — and,
     * for a sound rule set, with itself.
     */
    public DerivationStatus check(Derivation derivation, Collection<String> facts,
                                  List<Derivation> rules) {
        List<Derivation> theory = new ArrayList<>(rules == null ? List.of() : rules);
        if (!theory.contains(derivation)) {
            theory.add(derivation);
        }
        return entails(derivation.conclusionClaimId(), facts, theory);
    }

    /** Whether a valuation satisfies every rule of the theory. */
    private boolean satisfiesAllRules(boolean[] model, List<String> atoms, List<Derivation> rules) {
        for (Derivation rule : rules) {
            if (!satisfies(model, atoms, rule)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a valuation satisfies one rule. */
    private boolean satisfies(boolean[] model, List<String> atoms, Derivation rule) {
        List<Boolean> premises = new ArrayList<>();
        for (String premise : rule.premiseClaimIds()) {
            int index = atoms.indexOf(premise);
            premises.add(index < 0 || model[index]);
        }
        int conclusion = atoms.indexOf(rule.conclusionClaimId());
        boolean holds = conclusion >= 0 && model[conclusion];
        return switch (rule.connective()) {
            case AND -> {
                boolean all = premises.stream().allMatch(Boolean::booleanValue);
                yield !all || holds;
            }
            case OR -> {
                boolean any = premises.stream().anyMatch(Boolean::booleanValue);
                yield !any || holds;
            }
            case IMPLIES -> {
                boolean premiseHolds = premises.isEmpty() || premises.get(0);
                yield !premiseHolds || holds;
            }
            case NOT -> {
                // The rule asserts that the conclusion holds exactly when
                // the premise does not: a biconditional, not a one-way
                // implication.
                boolean premiseHolds = !premises.isEmpty() && premises.get(0);
                yield premiseHolds == holds;
            }
        };
    }
}
