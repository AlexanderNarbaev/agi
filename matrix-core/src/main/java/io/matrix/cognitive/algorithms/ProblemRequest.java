package io.matrix.cognitive.algorithms;

import java.util.Set;

/**
 * W44.2 — one computational problem, with the facts known about its input.
 *
 * <p>The facts are the PRECONDITION FACTS: named statements about the input that a method may
 * require. {@code NON_NEGATIVE_WEIGHTS}, {@code SORTED_INPUT}, {@code SINGLE_PASS_ALLOWED} and
 * so on. The selector admits a method only when every precondition the method declares is
 * present here, so "the method was selected outside its declared preconditions" is a state the
 * code cannot represent.</p>
 *
 * <p>The payload is typed per family; this record carries it as {@link Object} because the
 * portfolio spans families, and each method casts its own payload to its own record type. The
 * cast is safe because the registry only offers a method to a request of its own family.</p>
 */
public record ProblemRequest(
        String family,
        Object payload,
        Set<String> preconditionFacts,
        ComputeBudget budget) {

    public ProblemRequest {
        if (family == null || family.isBlank()) {
            throw new IllegalArgumentException("problem family must not be blank");
        }
        preconditionFacts = preconditionFacts == null ? Set.of() : Set.copyOf(preconditionFacts);
        if (budget == null) {
            budget = ComputeBudget.unbounded();
        }
    }

    /** True when the named fact is known to hold for this input. */
    public boolean fact(String name) {
        return preconditionFacts.contains(name);
    }
}
