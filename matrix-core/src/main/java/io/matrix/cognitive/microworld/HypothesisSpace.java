package io.matrix.cognitive.microworld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * W44.4 — the space of candidate rules the learner starts from.
 *
 * <h2>Why the space starts UNBIASED</h2>
 *
 * <p>{@link #allBooleanFunctionsOfTwoVariables()} is the COMPLETE set of Boolean functions over
 * the micro-world's two input variables — all sixteen of them, including the conjunction that
 * the world actually obeys. The learner is handed none of them in particular; it is handed the
 * whole space and has to narrow it with observations.</p>
 *
 * <p>This is what makes the ambiguity in the scenario real rather than staged. If the space were
 * seeded with the conjunction and two near misses, the "several hypotheses survive" state would
 * be an artefact of the fixture. With all sixteen present, the survivors after two observations
 * are a mathematical consequence of the observations, and any test that claims the survivors
 * disagree can be checked by anyone against the sixteen functions.</p>
 *
 * <p><b>The consequence is that the true rule is reachable by enumeration.</b> The learner can
 * eventually arrive at the conjunction — but by ELIMINATION, with evidence, and the elimination
 * history is what the session keeps.</p>
 *
 * <h2>The computations are exact</h2>
 *
 * <p>{@link #consistentWith(List)} evaluates the truth table of every candidate against every
 * observation. There is no sampling, no heuristic, no "close enough": a candidate is either
 * compatible with an observation or it is not.</p>
 */
public final class HypothesisSpace {

    /** The number of input variables of the micro-world. */
    public static final int VARIABLE_COUNT = 2;

    /** The separator between two variables of a canonical assignment key. */
    private static final String KEY_SEPARATOR = ",";

    /** The separator between a variable and its value in a canonical assignment key. */
    private static final String KEY_ASSIGNMENT_SEPARATOR = "=";

    /** The separator between two keys in a human-readable statement. */
    private static final String STATEMENT_SEPARATOR = " or ";

    /** The name of the outcome variable the statements talk about. */
    private static final String OUTCOME_NAME = LampEnvironment.LAMP_VARIABLE;

    private final List<String> variables;
    private final List<RuleHypothesis> candidates;

    /**
     * A space over the given variables.
     *
     * @param variables  the input variables, in sorted order
     * @param candidates the candidate hypotheses; they are kept in identifier order, so the space
     *                   has one canonical arrangement and every iteration over it is reproducible
     */
    public HypothesisSpace(List<String> variables, List<RuleHypothesis> candidates) {
        if (variables == null || variables.isEmpty()) {
            throw new IllegalArgumentException("a hypothesis space needs at least one variable");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("a hypothesis space needs at least one candidate");
        }
        List<String> orderedVariables = new ArrayList<>(new TreeSet<>(variables));
        List<RuleHypothesis> ordered = new ArrayList<>(candidates);
        ordered.sort((left, right) -> left.id().compareTo(right.id()));
        this.variables = List.copyOf(orderedVariables);
        this.candidates = List.copyOf(ordered);
    }

    /** The space of all sixteen Boolean functions over the micro-world's two variables. */
    public static HypothesisSpace unbiased() {
        return new HypothesisSpace(orderedVariables(), allBooleanFunctionsOfTwoVariables());
    }

    /** The input variables of this space, in sorted order. */
    public List<String> variables() {
        return variables;
    }

    /** The candidates of this space, in identifier order. */
    public List<RuleHypothesis> candidates() {
        return candidates;
    }

    /**
     * A candidate that has an opinion about this assignment.
     *
     * <p><b>The space does not resolve ambiguity.</b> When several candidates disagree about an
     * assignment, this method returns the first of them in the space's deterministic order and
     * the caller must NOT read that as the space's answer. For the question "what do the
     * candidates that SURVIVE say", use {@link MicroWorldSession#predict(WorldInput)}, which
     * reports the disagreement instead of hiding it.</p>
     *
     * @param input the assignment
     * @return the first candidate with a truth-table entry for the assignment, if any
     */
    public Optional<RuleHypothesis> forAssignment(WorldInput input) {
        for (RuleHypothesis candidate : candidates) {
            if (candidate.valueAt(input).isPresent()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * The candidates that fit EVERY observation.
     *
     * <p>Exact: each candidate's truth table is evaluated against each observation, and a
     * candidate is returned only if it agrees with all of them. A candidate with no truth-table
     * entry for an observation's assignment is NOT returned: it cannot be shown to fit, and
     * keeping it would keep a candidate that cannot be checked.</p>
     *
     * @param observations the observations to check against
     * @return the compatible candidates, with their evidence counts
     */
    public List<RuleHypothesis> consistentWith(List<Observation> observations) {
        List<Observation> observed = observations == null ? List.of() : observations;
        List<RuleHypothesis> fitting = new ArrayList<>();
        for (RuleHypothesis candidate : candidates) {
            boolean fits = true;
            int supporting = 0;
            for (Observation observation : observed) {
                Optional<Boolean> value = candidate.valueAt(observation.input());
                if (value.isEmpty() || value.get() != observation.lamp()) {
                    fits = false;
                    break;
                }
                supporting++;
            }
            if (fits) {
                fitting.add(candidate.withCounts(supporting, 0));
            }
        }
        return List.copyOf(fitting);
    }

    /**
     * All sixteen Boolean functions over the micro-world's two input variables.
     *
     * <p>Every function is a complete truth table over the four assignments, keyed by the
     * canonical assignment key. The identifier of a function is its four bits in the order of the
     * sorted keys, so {@code "h-0001"} is the function that is true only at
     * {@code "power=1,switch=1"} — the conjunction the lamp world obeys.</p>
     *
     * @return the sixteen functions, in identifier order
     */
    public static List<RuleHypothesis> allBooleanFunctionsOfTwoVariables() {
        List<String> keys = assignmentKeys(orderedVariables());
        int functionCount = 1 << keys.size();
        List<RuleHypothesis> functions = new ArrayList<>(functionCount);
        for (int pattern = 0; pattern < functionCount; pattern++) {
            Map<String, Boolean> table = new LinkedHashMap<>();
            StringBuilder bits = new StringBuilder();
            for (int index = 0; index < keys.size(); index++) {
                boolean value = ((pattern >> (keys.size() - 1 - index)) & 1) == 1;
                table.put(keys.get(index), value);
                bits.append(value ? '1' : '0');
            }
            functions.add(new RuleHypothesis("h-" + bits, statementOf(table, keys),
                    table, 0, 0));
        }
        return List.copyOf(functions);
    }

    /**
     * Every assignment of the micro-world's variables, in canonical key order.
     *
     * @return the assignments
     */
    public List<WorldInput> allAssignments() {
        List<WorldInput> assignments = new ArrayList<>();
        for (String key : assignmentKeys(variables)) {
            assignments.add(inputOf(key));
        }
        return List.copyOf(assignments);
    }

    /**
     * The expected reduction in the number of surviving hypotheses, as a fraction, for one probe
     * of this assignment.
     *
     * <p>The formula is written out in {@link Experiment}: the expected surviving fraction under
     * the hypothesis-implied prior, subtracted from one. The weighting is the hypothesis-implied
     * prior rather than a uniform one over the two outcomes, because a uniform weighting makes
     * the metric constant and a constant metric cannot rank the assignments.</p>
     *
     * @param input        the assignment to probe
     * @param hypotheses   the hypotheses that are alive now
     * @return the expected fraction of hypotheses eliminated, in [0, 0.5]
     */
    public static double informationGain(WorldInput input, List<RuleHypothesis> hypotheses) {
        if (input == null || hypotheses == null || hypotheses.isEmpty()) {
            return 0.0;
        }
        int trueCount = 0;
        int evaluable = 0;
        for (RuleHypothesis hypothesis : hypotheses) {
            Optional<Boolean> value = hypothesis.valueAt(input);
            if (value.isPresent()) {
                evaluable++;
                if (value.get()) {
                    trueCount++;
                }
            }
        }
        if (evaluable == 0) {
            return 0.0;
        }
        double falseCount = evaluable - trueCount;
        double probabilityTrue = (double) trueCount / evaluable;
        double probabilityFalse = falseCount / evaluable;
        double expectedSurvivingFraction =
                probabilityTrue * probabilityTrue + probabilityFalse * probabilityFalse;
        return 1.0 - expectedSurvivingFraction;
    }

    /**
     * The value every one of these hypotheses predicts for this assignment.
     *
     * @param input      the assignment
     * @param hypotheses the hypotheses to ask
     * @return the agreed value, or empty when they disagree or when none of them can speak about
     *         the assignment
     */
    public static Optional<Boolean> unanimousValueAt(WorldInput input,
                                                     List<RuleHypothesis> hypotheses) {
        if (input == null || hypotheses == null || hypotheses.isEmpty()) {
            return Optional.empty();
        }
        Boolean agreed = null;
        for (RuleHypothesis hypothesis : hypotheses) {
            Optional<Boolean> value = hypothesis.valueAt(input);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            if (agreed == null) {
                agreed = value.get();
            } else if (agreed != value.get()) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(agreed);
    }

    /** The number of observations this hypothesis agrees with. */
    public static int supportingCount(RuleHypothesis hypothesis, List<Observation> observations) {
        int count = 0;
        for (Observation observation : safe(observations)) {
            if (hypothesis.agreesWith(observation)) {
                count++;
            }
        }
        return count;
    }

    /** The number of observations this hypothesis contradicts. */
    public static int contradictingCount(RuleHypothesis hypothesis, List<Observation> observations) {
        int count = 0;
        for (Observation observation : safe(observations)) {
            Optional<Boolean> value = hypothesis.valueAt(observation.input());
            if (value.isEmpty() || value.get() != observation.lamp()) {
                count++;
            }
        }
        return count;
    }

    /** The first observation this hypothesis contradicts. */
    public static Optional<Observation> firstContradiction(RuleHypothesis hypothesis,
                                                            List<Observation> observations) {
        for (Observation observation : safe(observations)) {
            Optional<Boolean> value = hypothesis.valueAt(observation.input());
            if (value.isEmpty() || value.get() != observation.lamp()) {
                return Optional.of(observation);
            }
        }
        return Optional.empty();
    }

    /** The variable names of the micro-world, in sorted order. */
    private static List<String> orderedVariables() {
        return new ArrayList<>(new TreeSet<>(WorldInput.KNOWN_VARIABLES));
    }

    /** Every assignment key of these variables, in sorted order. */
    private static List<String> assignmentKeys(List<String> names) {
        int assignmentCount = 1 << names.size();
        List<String> keys = new ArrayList<>(assignmentCount);
        for (int mask = 0; mask < assignmentCount; mask++) {
            StringBuilder sb = new StringBuilder();
            for (int index = 0; index < names.size(); index++) {
                if (index > 0) {
                    sb.append(KEY_SEPARATOR);
                }
                sb.append(names.get(index)).append(KEY_ASSIGNMENT_SEPARATOR)
                        .append(((mask >> (names.size() - 1 - index)) & 1));
            }
            keys.add(sb.toString());
        }
        Collections.sort(keys);
        return keys;
    }

    /** The assignment a canonical key names. */
    private static WorldInput inputOf(String key) {
        Map<String, Boolean> assignment = new LinkedHashMap<>();
        for (String part : key.split(KEY_SEPARATOR)) {
            String[] pair = part.split(KEY_ASSIGNMENT_SEPARATOR);
            assignment.put(pair[0], "1".equals(pair[1]));
        }
        return new WorldInput(assignment);
    }

    /** The human-readable statement of a truth table. */
    private static String statementOf(Map<String, Boolean> table, List<String> keys) {
        List<String> trueKeys = new ArrayList<>();
        for (String key : keys) {
            if (Boolean.TRUE.equals(table.get(key))) {
                trueKeys.add(key);
            }
        }
        if (trueKeys.isEmpty()) {
            return OUTCOME_NAME + " is never true";
        }
        if (trueKeys.size() == keys.size()) {
            return OUTCOME_NAME + " is always true";
        }
        return OUTCOME_NAME + " is true exactly when " + String.join(STATEMENT_SEPARATOR, trueKeys);
    }

    private static List<Observation> safe(List<Observation> observations) {
        return observations == null ? List.of() : observations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HypothesisSpace other)) {
            return false;
        }
        return variables.equals(other.variables) && candidates.equals(other.candidates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variables, candidates);
    }

    @Override
    public String toString() {
        return "HypothesisSpace[variables=" + variables + " candidates=" + candidates.size() + "]";
    }
}
