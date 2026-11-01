package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W41 — the entailment checker is exact, and it says so when it is
 * not.
 *
 * <p>The mission's §6.3 is blunt: "A BIR execution is not automatically a
 * correct interpretation of the premises." A reasoner that answers
 * "probably follows" cannot be audited, so this one answers VALID, INVALID
 * or UNDECIDED and nothing else.
 *
 * <p>The decisive test here is the agreement with an INDEPENDENT reference
 * implementation over random theories. The reference is written differently
 * on purpose: it evaluates the rules as formulas over a valuation, rather
 * than by the checker's bitmask technique. If the two ever disagree, the
 * word "exact" is false and must be withdrawn.
 */
class EntailmentCheckerTest {

    private final EntailmentChecker checker = new EntailmentChecker();

    private static Derivation rule(String id, Derivation.Connective connective,
                                   List<String> premises, String conclusion) {
        return new Derivation(id, "ws", "rule-" + id, "rule " + id, connective,
                conclusion, premises, DerivationStatus.UNDECIDED, "2026-11-01T00:00:00Z");
    }

    @Test
    @DisplayName("a fact entails itself")
    void aFactEntailsItself() {
        DerivationStatus status = checker.entails("q", List.of("q"), List.of());

        assertThat(status).isEqualTo(DerivationStatus.DERIVED_VALID);
    }

    @Test
    @DisplayName("a fact does not entail what is not there")
    void aFactDoesNotEntailWhatIsNotThere() {
        DerivationStatus status = checker.entails("r", List.of("q"), List.of());

        assertThat(status).isEqualTo(DerivationStatus.DERIVED_INVALID);
    }

    @Test
    @DisplayName("modus ponens is valid")
    void modusPonensIsValid() {
        // p and (p -> q) entail q.
        Derivation modusPonens = rule("mp", Derivation.Connective.AND, List.of("p", "p->q"), "q");

        DerivationStatus status = checker.entails("q", List.of("p", "p->q"), List.of(modusPonens));

        assertThat(status).isEqualTo(DerivationStatus.DERIVED_VALID);
    }

    @Test
    @DisplayName("a rule whose antecedent is not met does not give its conclusion")
    void aRuleWhoseAntecedentIsNotMetDoesNotGiveItsConclusion() {
        // The rule is "p AND q -> r". Only p is a fact, so the antecedent is
        // not met and r does not follow.
        Derivation rule = rule("needs-two", Derivation.Connective.AND, List.of("p", "q"), "r");

        DerivationStatus status = checker.entails("r", List.of("p"), List.of(rule));

        assertThat(status).isEqualTo(DerivationStatus.DERIVED_INVALID);
    }

    @Test
    @DisplayName("a rule that is part of the theory does give its conclusion")
    void aRuleThatIsPartOfTheTheoryDoesGiveItsConclusion() {
        // "p -> q" together with the fact p does entail q: the rule is part
        // of the theory being checked, which is the whole point of
        // recording it.
        Derivation rule = rule("implies", Derivation.Connective.IMPLIES, List.of("p"), "q");

        DerivationStatus status = checker.entails("q", List.of("p"), List.of(rule));

        assertThat(status).isEqualTo(DerivationStatus.DERIVED_VALID);
    }

    @Test
    @DisplayName("a multi-hop chain is valid")
    void aMultiHopChainIsValid() {
        // p, (p->q), (q->r) entail r.
        Derivation first = rule("hop1", Derivation.Connective.AND, List.of("p", "p->q"), "q");
        Derivation second = rule("hop2", Derivation.Connective.AND, List.of("q", "q->r"), "r");

        DerivationStatus status = checker.entails("r", List.of("p", "p->q", "q->r"),
                List.of(first, second));

        assertThat(status).isEqualTo(DerivationStatus.DERIVED_VALID);
    }

    @Test
    @DisplayName("a broken chain does not entail")
    void aBrokenChainDoesNotEntail() {
        // The second hop is missing, so r does not follow.
        Derivation first = rule("hop1", Derivation.Connective.AND, List.of("p", "p->q"), "q");

        DerivationStatus status = checker.entails("r", List.of("p", "p->q"), List.of(first));

        assertThat(status).isEqualTo(DerivationStatus.DERIVED_INVALID);
    }

    @Test
    @DisplayName("a theory too large for the budget is undecided, not guessed")
    void aTheoryTooLargeForTheBudgetIsUndecided() {
        EntailmentChecker small = new EntailmentChecker(3);
        List<String> facts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            facts.add("a" + i);
        }

        DerivationStatus status = small.entails("a0", facts, List.of());

        assertThat(status).isEqualTo(DerivationStatus.UNDECIDED);
    }

    @Test
    @DisplayName("a budget of zero is refused")
    void aBudgetOfZeroIsRefused() {
        assertThatThrownBy(() -> new EntailmentChecker(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the checker agrees with an independent reference over 1000 random theories")
    void theCheckerAgreesWithAnIndependentReference() {
        Random random = new Random(20261101L);
        int checked = 0;
        int disagreements = 0;

        for (int trial = 0; trial < 1000; trial++) {
            int atomCount = 2 + random.nextInt(4);
            List<String> atoms = new ArrayList<>();
            for (int i = 0; i < atomCount; i++) {
                atoms.add("a" + i);
            }
            List<String> facts = new ArrayList<>();
            for (String atom : atoms) {
                if (random.nextBoolean()) {
                    facts.add(atom);
                }
            }
            List<Derivation> rules = new ArrayList<>();
            int ruleCount = random.nextInt(3);
            for (int r = 0; r < ruleCount; r++) {
                int premiseCount = 1 + random.nextInt(2);
                List<String> premises = new ArrayList<>();
                for (int p = 0; p < premiseCount; p++) {
                    premises.add(atoms.get(random.nextInt(atoms.size())));
                }
                Derivation.Connective connective = Derivation.Connective.values()[
                        random.nextInt(Derivation.Connective.values().length)];
                rules.add(rule("r" + r, connective, premises,
                        atoms.get(random.nextInt(atoms.size()))));
            }
            String goal = atoms.get(random.nextInt(atoms.size()));

            DerivationStatus mine = checker.entails(goal, facts, rules);
            DerivationStatus reference = referenceEntails(goal, facts, rules);

            checked++;
            if (mine != reference) {
                disagreements++;
            }
        }

        assertThat(checked).isEqualTo(1000);
        assertThat(disagreements)
                .as("the checker's claim to be exact is false if it ever disagrees "
                        + "with an independent implementation")
                .isZero();
    }

    /**
     * An independent implementation of the same semantics.
     *
     * <p>Written differently on purpose: this one evaluates each rule as a
     * formula over an explicit valuation, using sets of satisfied atoms,
     * rather than the checker's bitmask technique. If the two agree on a
     * thousand random theories, the agreement is evidence and not a
     * tautology.
     */
    private DerivationStatus referenceEntails(String goal, List<String> facts,
                                              List<Derivation> rules) {
        Set<String> all = new LinkedHashSet<>(facts);
        all.add(goal);
        for (Derivation rule : rules) {
            all.add(rule.conclusionClaimId());
            all.addAll(rule.premiseClaimIds());
        }
        List<String> atoms = new ArrayList<>(all);
        if (atoms.size() > 16) {
            return DerivationStatus.UNDECIDED;
        }

        for (int bits = 0; bits < (1 << atoms.size()); bits++) {
            Set<String> trueAtoms = new LinkedHashSet<>();
            for (int i = 0; i < atoms.size(); i++) {
                if ((bits >> i & 1) == 1) {
                    trueAtoms.add(atoms.get(i));
                }
            }
            boolean factsHold = trueAtoms.containsAll(facts);
            boolean rulesHold = true;
            for (Derivation rule : rules) {
                if (!ruleHolds(rule, trueAtoms)) {
                    rulesHold = false;
                    break;
                }
            }
            if (factsHold && rulesHold && !trueAtoms.contains(goal)) {
                return DerivationStatus.DERIVED_INVALID;
            }
        }
        return DerivationStatus.DERIVED_VALID;
    }

    private boolean ruleHolds(Derivation rule, Set<String> trueAtoms) {
        // A LinkedHashMap, not a HashMap: the premise order decides which
        // premise the one-premise connectives use, and a HashMap does not
        // keep the insertion order. That bug made this reference disagree
        // with the checker on 12 of 1000 random theories.
        Map<String, Boolean> premiseValues = new java.util.LinkedHashMap<>();
        for (String premise : rule.premiseClaimIds()) {
            premiseValues.put(premise, trueAtoms.contains(premise));
        }
        boolean conclusion = trueAtoms.contains(rule.conclusionClaimId());
        return switch (rule.connective()) {
            case AND -> {
                boolean all = premiseValues.values().stream().allMatch(Boolean::booleanValue);
                yield !all || conclusion;
            }
            case OR -> {
                boolean any = premiseValues.values().stream().anyMatch(Boolean::booleanValue);
                yield !any || conclusion;
            }
            case IMPLIES -> {
                Boolean first = premiseValues.isEmpty() ? Boolean.TRUE
                        : premiseValues.values().iterator().next();
                yield !first || conclusion;
            }
            case NOT -> {
                Boolean first = premiseValues.isEmpty() ? Boolean.FALSE
                        : premiseValues.values().iterator().next();
                yield first == conclusion;
            }
        };
    }
}
