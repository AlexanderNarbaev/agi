package io.matrix.knowledge;

import java.util.List;

/**
 * One step of a teaching plan (owner directive §9 Stage 6, §14).
 *
 * <p>A step is a piece of explanation, an exercise and a check. The check
 * matters: a plan that only contains explanations measures nothing.
 */
public record TeachingStep(
        int order,
        AbstractionLevel level,
        String conceptId,
        String statement,
        String exercise,
        String check,
        List<String> sourceIds) {

    public TeachingStep {
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("a teaching step needs a statement");
        }
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }
}
