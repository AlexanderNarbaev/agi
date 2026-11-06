package io.matrix.cognitive.microworld;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * W44.4 — a candidate rule about the micro-world, stated as a truth table.
 *
 * <p>A hypothesis is a complete proposal about what the outcome variable does: for EVERY
 * assignment of the input variables it says what the outcome will be. There is no partial
 * hypothesis and no default value, because a rule that says nothing about an assignment cannot be
 * checked against an observation of it.</p>
 *
 * <p><b>Why a truth table and not a formula.</b> A formula ({@code "power AND switch"}) is
 * readable but not directly checkable; a truth table is a mechanical mapping from an assignment to
 * a value. The human-readable {@code statement} carries the readability and the table carries the
 * checkability, and the two are kept apart so that a wrong statement cannot make a wrong table
 * look right.</p>
 *
 * <p><b>Why the counts are part of the record.</b> A hypothesis that has been checked against
 * three observations and a hypothesis that has been checked against thirty are not the same
 * piece of knowledge, and a reader who cannot see the difference will read a guess as a
 * conclusion. The counts are the evidence the hypothesis has been measured against, and they are
 * recomputed every time the set of observations changes.</p>
 *
 * <p>Hypotheses are values. Two hypotheses with the same identifier are the same hypothesis, and
 * they are compared by identifier rather than by identity — the session produces a fresh record
 * each time it re-evaluates a hypothesis, because the counts changed.</p>
 */
public record RuleHypothesis(String id, String statement, Map<String, Boolean> truthTable,
                             int supportingCases, int contradictingCases) {

    public RuleHypothesis {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a hypothesis id must not be blank");
        }
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("a hypothesis must say what it claims");
        }
        if (truthTable == null || truthTable.isEmpty()) {
            throw new IllegalArgumentException("a hypothesis must have a truth table");
        }
        if (supportingCases < 0 || contradictingCases < 0) {
            throw new IllegalArgumentException("a hypothesis cannot have a negative case count");
        }
        Map<String, Boolean> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : truthTable.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("a truth-table key must not be blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("the truth table of '" + id
                        + "' has no value for '" + entry.getKey() + "'");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        truthTable = Map.copyOf(copy);
    }

    /**
     * The canonical key of an assignment, in the format this truth table is written in.
     *
     * <p><b>The format is a CONTRACT, not an implementation detail.</b> The key is the variable
     * names in sorted order, joined as {@code name=value} with the value {@code 1} or {@code 0},
     * comma-separated — for example {@code "power=0,switch=1"}. The kernel computes the same
     * key through {@code MicroWorldCodec.describe}, and the two must agree exactly: a truth
     * table that cannot be addressed by the key the kernel computes is a truth table nobody can
     * read.</p>
     *
     * @param input the assignment
     * @return the key
     */
    public String keyFor(WorldInput input) {
        if (input == null) {
            throw new IllegalArgumentException("an assignment is required");
        }
        return input.key();
    }

    /**
     * The variables this hypothesis is defined over.
     *
     * <p>Derived from the keys of its own truth table, so it can never disagree with the table
     * it describes.</p>
     *
     * @return the variable names, in sorted order
     */
    public Set<String> variables() {
        Set<String> names = new TreeSet<>();
        for (String key : truthTable.keySet()) {
            for (String part : key.split(",")) {
                int separator = part.indexOf('=');
                if (separator > 0) {
                    names.add(part.substring(0, separator).trim());
                }
            }
        }
        return java.util.Collections.unmodifiableSet(names);
    }

    /**
     * What this hypothesis says about an assignment.
     *
     * @param input the assignment
     * @return the value, or empty when the hypothesis has no entry for it — which means the
     *         hypothesis cannot be checked against that assignment, NOT that the value is false
     */
    public Optional<Boolean> valueAt(WorldInput input) {
        if (input == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(truthTable.get(keyFor(input)));
    }

    /**
     * What this hypothesis says about an assignment, without the tolerance.
     *
     * <p>This is the read the kernel's methods use. A hypothesis that has no entry for an
     * assignment cannot be evaluated against it, and returning {@code false} for that case would
     * turn "I do not know" into "I say no" — the confusion this package exists to avoid.</p>
     *
     * @param input the assignment
     * @return the value the hypothesis holds for the assignment
     * @throws IllegalStateException when the truth table has no entry for the assignment
     */
    public boolean evaluate(WorldInput input) {
        String key = keyFor(input);
        Boolean value = truthTable.get(key);
        if (value == null) {
            throw new IllegalStateException("the hypothesis '" + id + "' has no truth-table entry for '"
                    + key + "'; it is defined over " + variables());
        }
        return value;
    }

    /** Whether this hypothesis agrees with the observation. */
    public boolean agreesWith(Observation observation) {
        if (observation == null) {
            return false;
        }
        return valueAt(observation.input()).map(value -> value == observation.lamp()).orElse(false);
    }

    /** The same hypothesis with different evidence counts. */
    public RuleHypothesis withCounts(int supporting, int contradicting) {
        return new RuleHypothesis(id, statement, truthTable, supporting, contradicting);
    }

    @Override
    public String toString() {
        return "RuleHypothesis[" + id + " supporting=" + supportingCases
                + " contradicting=" + contradictingCases + "]";
    }
}
