package io.matrix.cognitive.microworld;

import java.util.Set;

/**
 * W44.4 — the world the learner probes, seen from the outside.
 *
 * <p><b>The rule stays inside.</b> The environment holds a rule that governs what it answers, and
 * that rule is NOT part of this interface and NOT reachable through it. The only thing a learner
 * can do is ask a question and receive an answer. This is the whole point of the micro-world: a
 * system that is told the rule learns nothing, and a system that can ask the rule what the rule
 * is has learned nothing either.</p>
 *
 * <p>What the interface DOES expose is the world's VOCABULARY — which variables it accepts and
 * what its outcome variable is called. That is not the rule; it is the shape of the questions,
 * and a learner that cannot name the variables cannot ask a well-formed question.</p>
 */
public interface MicroWorldEnvironment {

    /**
     * Asks the world what the outcome variable is for this assignment.
     *
     * <p>This is a PROBE, not a rule. It performs the hidden rule once and reports the result.
     * The rule itself is not returned, not logged and not derivable from the answer.</p>
     *
     * @param input the assignment to try
     * @return the observation the world produced
     */
    Observation probe(WorldInput input);

    /** The input variables this world accepts. */
    Set<String> variables();

    /** The name of the outcome variable, for example {@code "lamp"}. */
    String outcomeVariable();
}
