package io.matrix.cognitive.microworld;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * W44.4 — the assignment of the micro-world's input variables for one probe.
 *
 * <p>This is the ACT of asking the world a question, not the answer. The world has two input
 * variables, {@code power} and {@code switch}, and one outcome variable, {@code lamp}. An
 * assignment says what the inputs were set to; it says nothing about what the world will do.</p>
 *
 * <p><b>Why the assignment is a value and not a mutable object.</b> A probe that has been asked is
 * a historical fact: the learner must be able to say "I asked with power off and the switch on,
 * and this is what came back", and that sentence must not change afterwards. The map is copied
 * and wrapped, so a caller that mutates the map it passed in cannot rewrite history.</p>
 *
 * <p><b>Why the key format is fixed.</b> The key {@code "power=0,switch=1"} is the identity of
 * an assignment across the whole package: it is the truth-table key of a hypothesis, the key of a
 * prediction, the key of an observed assignment and the key of a proposed experiment. It is built
 * from the variable names in SORTED order, not from the iteration order of the map, so two
 * assignments with the same content always produce the same key regardless of how they were
 * built.</p>
 *
 * <p>Whether an assignment names only known variables is the CALLER's problem, and this record
 * deliberately does not police it: the environment is the component that knows which variables
 * exist, and refusing an unknown name here would put the world's vocabulary in the wrong place.
 * {@link #KNOWN_VARIABLES} documents the vocabulary the micro-world uses.</p>
 */
public record WorldInput(Map<String, Boolean> assignment) {

    /** The variables the micro-world exposes. An assignment may name others; the environment refuses them. */
    public static final Set<String> KNOWN_VARIABLES = Set.of("power", "switch");

    /** The separator between two variables of a canonical key. */
    private static final String KEY_SEPARATOR = ",";

    /** The separator between a variable and its value in a canonical key. */
    private static final String KEY_ASSIGNMENT_SEPARATOR = "=";

    public WorldInput {
        if (assignment == null) {
            throw new IllegalArgumentException("an assignment must not be null");
        }
        if (assignment.isEmpty()) {
            throw new IllegalArgumentException("an assignment must name at least one variable");
        }
        Map<String, Boolean> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : assignment.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("a variable name must not be blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("variable '" + entry.getKey()
                        + "' must have a value; null is not a state of the world");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        assignment = Collections.unmodifiableMap(copy);
    }

    /**
     * The micro-world's two-variable assignment.
     *
     * <p>A convenience for the world that actually exists: the lamp has exactly two inputs. It
     * is named rather than left to the caller so that a test cannot accidentally build an
     * assignment the environment will refuse.</p>
     *
     * @param power  the value of the power variable
     * @param toggle the value of the switch variable
     * @return the assignment
     */
    public static WorldInput of(boolean power, boolean toggle) {
        return new WorldInput(Map.of("power", power, "switch", toggle));
    }

    /**
     * The value of one variable.
     *
     * @param name the variable
     * @return the value
     * @throws IllegalArgumentException when the assignment does not name that variable; an
     *         absent variable is not a false variable, and conflating the two is how a probe
     *         silently becomes a probe of something else
     */
    public boolean get(String name) {
        Boolean value = assignment.get(name);
        if (value == null) {
            throw new IllegalArgumentException("the assignment does not name '" + name
                    + "'; it names " + variables());
        }
        return value;
    }

    /** Whether the assignment names this variable. */
    public boolean has(String name) {
        return assignment.containsKey(name);
    }

    /** The variables this assignment names, in sorted order. */
    public Set<String> variables() {
        return Collections.unmodifiableSet(new TreeSet<>(assignment.keySet()));
    }

    /**
     * The canonical key of this assignment, for example {@code "power=0,switch=1"}.
     *
     * <p>Built from the variable names in sorted order, so the key depends on the CONTENT of the
     * assignment and on nothing else.</p>
     *
     * @return the key
     */
    public String key() {
        StringBuilder sb = new StringBuilder();
        for (String name : variables()) {
            if (sb.length() > 0) {
                sb.append(KEY_SEPARATOR);
            }
            sb.append(name).append(KEY_ASSIGNMENT_SEPARATOR).append(assignment.get(name) ? 1 : 0);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "WorldInput[" + key() + "]";
    }
}
