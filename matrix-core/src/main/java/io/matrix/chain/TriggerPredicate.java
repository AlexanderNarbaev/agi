package io.matrix.chain;

import io.matrix.imports.ChainEnrichedOutput;

/**
 * DESIGN-21 — Pure-function predicate over a chain's enriched output.
 * Triggers downstream chains when the predicate evaluates true.
 *
 * <p>CONSTITUTION I: must be a pure function of the input — no Random,
 * no wall-clock, no LLM calls. Same input → same boolean.
 */
@FunctionalInterface
public interface TriggerPredicate {
    /**
     * @param output enriched forward-pass output from a chain
     * @return true if this predicate fires
     */
    boolean test(ChainEnrichedOutput output);
}
