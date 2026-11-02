package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The error-driven learning integration (RECON-W42.6).
 *
 * <p>The mission's §9: *"error-driven learning integration only after
 * verifying the math, 8 required error fields"*, and *"do not
 * indiscriminately weaken neural associations after every unexpected
 * event"*.
 *
 * <p><b>The verification comes first.</b> An error is integrated only when
 * the learner has answered the same item wrongly in <b>two different
 * sessions</b>. One wrong answer may be a slip, a misread question or a
 * mistyped answer; integrating it would train the system on a fluke. The
 * unverified errors are not recorded at all — they stay in the outcomes,
 * which is where a fluke belongs.
 *
 * <p><b>What the integration produces.</b> A persisted record of the
 * verified errors, naming the concept the learner is wrong about. The
 * exercise selector acts on it: a concept with a verified error is a
 * concept the learner needs to practise, and the selector prefers its
 * items.
 *
 * <p>This is the phase that makes the consolidation affect the learner's
 * state rather than only the dialogue's memory.
 */
public final class ErrorIntegrator {

    /** What the integration did. */
    public record Result(
            int candidates,
            int verified,
            int recorded,
            List<LearningError> errors,
            List<String> unverified) {

        public Result {
            errors = List.copyOf(errors == null ? List.of() : errors);
            unverified = List.copyOf(unverified == null ? List.of() : unverified);
        }

        /** Whether the integration changed anything. */
        public boolean changedAnything() {
            return recorded > 0;
        }
    }

    private ErrorIntegrator() {
    }

    /**
     * Integrates the verified errors of a workspace.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @return what the integration did
     */
    public static Result integrate(KnowledgeWorkbench workbench, String workspaceId) {
        List<AssessmentOutcome> outcomes = workbench.outcomes(workspaceId);
        AssessmentDataset dataset = workbench.dataset(workspaceId);

        // The sessions in which each item was answered wrongly, and the
        // LAST outcome of each item.
        //
        // The last outcome matters: an error the learner has overcome is
        // not the learner's current state, and integrating it would train
        // the system on something the learner has already got right.
        Map<String, Set<String>> wrongSessions = new LinkedHashMap<>();
        Map<String, AssessmentOutcome> lastOutcome = new LinkedHashMap<>();
        for (AssessmentOutcome outcome : outcomes) {
            if (!outcome.correct()) {
                wrongSessions.computeIfAbsent(outcome.itemId(), key -> new LinkedHashSet<>())
                        .add(outcome.sessionId());
            }
            lastOutcome.put(outcome.itemId(), outcome);
        }

        List<LearningError> verifiedErrors = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : wrongSessions.entrySet()) {
            String itemId = entry.getKey();
            AssessmentItem item = dataset.items().stream()
                    .filter(candidate -> candidate.id().equals(itemId))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                // The dataset holds no such item: the error cannot be
                // described, so it cannot be integrated. It stays in the
                // outcomes.
                unverified.add(itemId);
                continue;
            }
            AssessmentOutcome outcome = lastOutcome.get(itemId);
            if (outcome == null || outcome.correct()
                    || !LearningError.verified(entry.getValue())) {
                // The learner has it right now, or the error has not been
                // repeated: neither is a gap.
                unverified.add(itemId);
                continue;
            }
            verifiedErrors.add(new LearningError(
                    Ids.digest(workspaceId + "|error|" + itemId),
                    workspaceId, itemId, item.conceptId(), item.dimension(),
                    outcome.learnerAnswer(), String.join(" ", item.keyTerms()), true));
        }

        workbench.store().recordLearningErrors(workspaceId, verifiedErrors);
        return new Result(wrongSessions.size(), verifiedErrors.size(), verifiedErrors.size(),
                verifiedErrors, unverified);
    }

    /**
     * The concepts the learner has a verified error about.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @return the concept ids
     */
    public static Set<String> conceptsWithVerifiedErrors(KnowledgeWorkbench workbench,
                                                          String workspaceId) {
        Set<String> concepts = new LinkedHashSet<>();
        for (LearningError error : workbench.store().learningErrors(workspaceId)) {
            if (error.verified()) {
                concepts.add(error.conceptId());
            }
        }
        return concepts;
    }
}
