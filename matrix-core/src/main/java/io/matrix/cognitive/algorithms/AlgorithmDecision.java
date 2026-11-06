package io.matrix.cognitive.algorithms;

import java.util.List;

/**
 * W44.2 — the recorded outcome of one algorithm-selection decision.
 *
 * <p>The W44 directive requires that the selected algorithm and the REASON for the selection are
 * both preserved, and that a rejected candidate is not silently dropped. This record keeps every
 * candidate that was considered, why each was accepted or rejected, what the budget was, and
 * what the computation actually returned.</p>
 *
 * <p>{@code status} is the field that keeps the system honest. A result that exhausted its
 * budget is {@code RESOURCE_LIMITED}, not {@code COMPLETED}: the directive states plainly that an
 * exhausted resource budget must never be reported as a successfully proven result.</p>
 */
public record AlgorithmDecision(
        String problemFamily,
        String selectedAlgorithmId,
        String selectedAlgorithmVersion,
        String reason,
        List<Candidate> candidates,
        ComputeBudget budget,
        Status status,
        String fallbackAlgorithmId,
        String outcome) {

    /** How the computation ended. */
    public enum Status {
        /** The method ran and returned a result within its budget. */
        COMPLETED,
        /** The method ran out of budget; the result is bounded, not proven. */
        RESOURCE_LIMITED,
        /** No candidate satisfied the preconditions; the task was refused. */
        REFUSED,
        /** The method failed; the fallback was attempted. */
        FAILED,
        /** A cached procedure was used after its applicability conditions were checked. */
        REUSED_PROCEDURE
    }

    /** One candidate that was considered. */
    public record Candidate(
            String algorithmId,
            String version,
            double score,
            boolean selected,
            String verdict) {
        public Candidate {
            if (algorithmId == null || algorithmId.isBlank()) {
                throw new IllegalArgumentException("candidate algorithm id must not be blank");
            }
            verdict = verdict == null ? "" : verdict;
        }
    }

    public AlgorithmDecision {
        if (problemFamily == null || problemFamily.isBlank()) {
            throw new IllegalArgumentException("problem family must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("decision status must not be null");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** The candidates that were rejected, with their reasons. */
    public List<Candidate> rejected() {
        return candidates.stream().filter(c -> !c.selected()).toList();
    }

    @Override
    public String toString() {
        return "AlgorithmDecision[" + problemFamily + " -> " + selectedAlgorithmId
                + " status=" + status + " reason=" + reason + "]";
    }
}
