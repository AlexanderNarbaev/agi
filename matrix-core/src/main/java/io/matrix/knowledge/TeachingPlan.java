package io.matrix.knowledge;

import java.util.List;

/**
 * A structured sequence of explanations, exercises and assessments intended
 * for another learner (owner directive §14, §30).
 *
 * <p>The plan names the dataset version it was built against, so a later
 * measurement can say which version of which questions it answers.
 */
public record TeachingPlan(
        String conceptId,
        String conceptName,
        AbstractionLevel targetLevel,
        List<TeachingStep> steps,
        List<String> prerequisiteConceptIds,
        List<String> sourceIds,
        String datasetId,
        int datasetVersion) {

    public TeachingPlan {
        steps = List.copyOf(steps == null ? List.of() : steps);
        prerequisiteConceptIds = List.copyOf(prerequisiteConceptIds == null ? List.of() : prerequisiteConceptIds);
        sourceIds = List.copyOf(sourceIds == null ? List.of() : sourceIds);
    }

    /** The number of steps in the plan. */
    public int size() {
        return steps.size();
    }
}
