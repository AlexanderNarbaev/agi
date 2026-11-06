package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A learning objective that can be put down and picked up again
 * (RECON-W41).
 *
 * <p>The mission's §7.1: "A goal must have: stable identifier, description,
 * success criteria, prerequisites, subtasks, current state, progress
 * evidence, failed attempts, remaining work, resource budget, last
 * checkpoint, recovery instructions. The system must resume a partially
 * completed objective after application restart. <b>Do not assume that
 * restoring a knowledge database is equivalent to restoring an active
 * goal.</b>"
 *
 * <p>That last sentence is the reason this is a separate record. The
 * knowledge store holds what is known; it does not hold what was being
 * attempted, what failed, how much of the budget is spent, or where to pick
 * up. The historical {@code GoalTracker} on the branch
 * {@code feature/mind-w3-sleep-consolidation} had the shape of a goal but
 * declared itself in memory, and the wave that was supposed to persist it
 * never came.
 *
 * <p><b>Completion semantics.</b> The mission's §7.2 asks for these to be
 * explicit. They are:
 *
 * <ul>
 *   <li>a goal is COMPLETED when every <b>required</b> subtask is completed
 *       and every success criterion has evidence behind it;</li>
 *   <li>an <b>optional</b> subtask never blocks completion - it is recorded
 *       as remaining work;</li>
 *   <li>a subtask whose prerequisites are not complete is <b>BLOCKED</b>,
 *       and a blocked goal is not failed - it is waiting;</li>
 *   <li>a failed attempt is <b>recorded</b>, never silently dropped: an
 *       attempt that failed is evidence about what to do differently;</li>
 *   <li>the budget is a limit on the attempts spent, and exceeding it moves
 *       the goal to ABANDONED rather than leaving it running forever.</li>
 * </ul>
 */
public record LearningGoal(
        String id,
        String workspaceId,
        String description,
        List<String> successCriteria,
        List<String> prerequisiteConceptIds,
        List<Subtask> subtasks,
        Status status,
        List<String> progressEvidenceIds,
        List<FailedAttempt> failedAttempts,
        String remainingWork,
        int budgetUnits,
        int budgetUsed,
        String lastCheckpoint,
        String recoveryInstructions,
        String createdAt,
        String updatedAt) {

    /** Where a goal stands. */
    public enum Status {
        /** Not started. */
        PENDING,
        /** Being worked on. */
        IN_PROGRESS,
        /** Waiting on something that has not happened yet. */
        BLOCKED,
        /** Done: every required subtask is complete and every criterion has evidence. */
        COMPLETED,
        /** Given up: the budget is spent, or the goal was withdrawn. */
        ABANDONED
    }

    /** One piece of the goal. */
    public record Subtask(
            String id,
            String description,
            boolean required,
            List<String> prerequisiteSubtaskIds,
            Status status,
            List<String> evidenceIds,
            String note) {

        public Subtask {
            prerequisiteSubtaskIds = List.copyOf(
                    prerequisiteSubtaskIds == null ? List.of() : prerequisiteSubtaskIds);
            evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
            status = status == null ? Status.PENDING : status;
        }

        public Subtask withStatus(Status newStatus, List<String> evidence, String newNote) {
            return new Subtask(id, description, required, prerequisiteSubtaskIds, newStatus,
                    evidence == null ? this.evidenceIds : evidence,
                    newNote == null ? this.note : newNote);
        }
    }

    /** An attempt that did not work, and why. */
    public record FailedAttempt(String id, String attempt, String reason, String recordedAt) {
    }

    public LearningGoal {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a goal must have an id");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("a goal " + id + " must have a description");
        }
        successCriteria = List.copyOf(successCriteria == null ? List.of() : successCriteria);
        prerequisiteConceptIds = List.copyOf(
                prerequisiteConceptIds == null ? List.of() : prerequisiteConceptIds);
        subtasks = List.copyOf(subtasks == null ? List.of() : subtasks);
        progressEvidenceIds = List.copyOf(progressEvidenceIds == null ? List.of() : progressEvidenceIds);
        failedAttempts = List.copyOf(failedAttempts == null ? List.of() : failedAttempts);
        status = status == null ? Status.PENDING : status;
    }

    /** The subtasks that still have to be done. */
    public List<Subtask> remainingSubtasks() {
        return subtasks.stream()
                .filter(subtask -> subtask.status() != Status.COMPLETED)
                .toList();
    }

    /** The subtasks that must be done for the goal to be complete. */
    public List<Subtask> requiredSubtasks() {
        return subtasks.stream().filter(Subtask::required).toList();
    }

    /**
     * Whether the goal is complete under the semantics stated above.
     *
     * @param satisfiedCriteria the success criteria that have evidence
     * @return true when every required subtask is done and every criterion
     *     is satisfied
     */
    public boolean isComplete(Set<String> satisfiedCriteria) {
        boolean subtasksDone = requiredSubtasks().stream()
                .allMatch(subtask -> subtask.status() == Status.COMPLETED);
        boolean criteriaMet = successCriteria.stream().allMatch(satisfiedCriteria::contains);
        return subtasksDone && criteriaMet;
    }

    /**
     * The subtasks whose prerequisites are not complete.
     *
     * <p>A goal whose subtasks depend on each other is checked in order, and
     * a dependency cycle makes the goal blocked rather than complete: a
     * circular dependency must never yield a false "done".
     */
    public List<Subtask> blockedSubtasks() {
        List<Subtask> blocked = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (Subtask subtask : subtasks) {
            if (subtask.status() == Status.COMPLETED) {
                done.add(subtask.id());
            }
        }
        for (Subtask subtask : subtasks) {
            if (subtask.status() == Status.COMPLETED) {
                continue;
            }
            boolean waiting = subtask.prerequisiteSubtaskIds().stream()
                    .anyMatch(prerequisite -> !done.contains(prerequisite));
            if (waiting) {
                blocked.add(subtask);
            }
        }
        return blocked;
    }

    /**
     * Whether a subtask was completed while one of its prerequisites was not.
     *
     * <p>A goal whose recorded state contradicts its own dependency graph is
     * not making progress: it is waiting. This is distinct from a goal that
     * is simply unfinished, and it is distinct from a failure.
     */
    public boolean violatesDependencies() {
        Set<String> done = new LinkedHashSet<>();
        for (Subtask subtask : subtasks) {
            if (subtask.status() == Status.COMPLETED) {
                done.add(subtask.id());
            }
        }
        for (Subtask subtask : subtasks) {
            if (subtask.status() != Status.COMPLETED) {
                continue;
            }
            for (String prerequisite : subtask.prerequisiteSubtaskIds()) {
                if (!done.contains(prerequisite)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the goal's subtasks depend on each other in a circle. */
    public boolean hasDependencyCycle() {
        for (Subtask subtask : subtasks) {
            if (visit(subtask, new LinkedHashSet<>(), new LinkedHashSet<>())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A depth-first walk along the dependency edges.
     *
     * <p>The walk follows the EDGES, not the whole list. An earlier version
     * re-scanned every subtask at each level, which made any goal with a
     * dependency look like a cycle - a goal whose assessment depends on its
     * reading was reported as circular, and a circular dependency is never
     * allowed to yield a false completion.
     */
    private boolean visit(Subtask subtask, Set<String> visiting, Set<String> done) {
        if (done.contains(subtask.id())) {
            return false;
        }
        if (!visiting.add(subtask.id())) {
            return true;
        }
        for (String prerequisite : subtask.prerequisiteSubtaskIds()) {
            for (Subtask next : subtasks) {
                if (next.id().equals(prerequisite) && visit(next, visiting, done)) {
                    return true;
                }
            }
        }
        visiting.remove(subtask.id());
        done.add(subtask.id());
        return false;
    }
}
