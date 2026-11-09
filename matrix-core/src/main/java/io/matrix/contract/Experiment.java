package io.matrix.contract;

import java.util.List;

/**
 * W51 — a deliberate attempt to find out something.
 *
 * <p><b>An experiment is not an action.</b> An action is something the system does to the world.
 * An experiment is an action chosen because it will DISCRIMINATE between the hypotheses the
 * system holds, and it declares what it expects to learn. The difference is the point: an
 * experiment that cannot change what the system believes was not an experiment.</p>
 *
 * <p><b>The expected information gain is declared before the run.</b> It is the reason the
 * experiment was chosen, and it is what a later measurement is compared against. When the
 * experiment turns out to have taught nothing, the system can say so — and that is a fact
 * worth recording, not a detail to smooth over.</p>
 *
 * <p><b>A failed experiment is a result.</b> {@link Status#FAILED} keeps the record with the
 * reason, because "we tried it and it did not work" is what stops the system from trying the
 * same thing again.</p>
 *
 * @param id                 the identity of the experiment
 * @param action             the action the experiment performs
 * @param hypothesisIds      the hypotheses the experiment discriminates between
 * @param expectedOutcome    what the system expects to observe
 * @param expectedInformationGain the information the experiment is expected to yield
 * @param status             the state of the experiment
 * @param outcomeId          the outcome the experiment produced, or {@code null}
 * @param reason             why the experiment ended as it did
 * @param provenance         where the experiment came from
 */
public record Experiment(
        String id,
        Action action,
        List<String> hypothesisIds,
        String expectedOutcome,
        double expectedInformationGain,
        Status status,
        String outcomeId,
        String reason,
        Provenance provenance) {

    /** The state of an experiment. */
    public enum Status {
        /** Proposed and not yet run. */
        PROPOSED,
        /** Running. */
        RUNNING,
        /** Run; the outcome is recorded. */
        COMPLETED,
        /** Run and failed; the reason is recorded. */
        FAILED,
        /** Abandoned before it could run. */
        ABANDONED
    }

    public Experiment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("experiment id must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("experiment action must not be null");
        }
        hypothesisIds = hypothesisIds == null ? List.of() : List.copyOf(hypothesisIds);
        if (Double.isNaN(expectedInformationGain) || expectedInformationGain < 0.0) {
            throw new IllegalArgumentException("expected information gain must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("experiment status must not be null");
        }
    }

    /** An experiment that discriminates between the named hypotheses. */
    public static Experiment proposed(String id, Action action, List<String> hypothesisIds,
                                      String expectedOutcome, double expectedInformationGain) {
        return new Experiment(id, action, hypothesisIds, expectedOutcome, expectedInformationGain,
                Status.PROPOSED, null, null, null);
    }

    /**
     * The completed form of this experiment.
     *
     * @param outcomeId the identity of the outcome the experiment produced
     * @param at        when the experiment ended
     * @return the completed experiment
     */
    public Experiment completed(String outcomeId, String at) {
        return new Experiment(id, action, hypothesisIds, expectedOutcome, expectedInformationGain,
                Status.COMPLETED, outcomeId, "the experiment produced an outcome at " + at, provenance);
    }

    /**
     * The failed form of this experiment.
     *
     * @param reason why the experiment failed
     * @return the failed experiment
     */
    public Experiment failed(String reason) {
        return new Experiment(id, action, hypothesisIds, expectedOutcome, expectedInformationGain,
                Status.FAILED, null, reason, provenance);
    }

    /** True when the experiment was worth running, in the system's own judgement. */
    public boolean informative() {
        return expectedInformationGain > 0.0;
    }

    @Override
    public String toString() {
        return "Experiment[" + id + " " + status + " gain=" + expectedInformationGain + "]";
    }
}
