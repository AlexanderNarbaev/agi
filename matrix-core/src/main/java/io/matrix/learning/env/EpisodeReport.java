package io.matrix.learning.env;

import java.util.List;

/**
 * W51 §7 — the machine-readable account of one finished episode.
 *
 * <p>This is the record the W51 trace carries: what the learner probed, what it believed
 * before each probe, what it received, and what it had left at the end. It is a VALUE, so
 * two reports of the same episode can be compared field by field — which is what makes the
 * determinism of an episode a checkable claim rather than a promise.</p>
 *
 * <p><b>Why the report holds the trajectory and not a summary of it.</b> A summary says how
 * the episode ended; the trajectory says HOW it ended, and a reader who cannot see the
 * order of the probes cannot check the one property that makes the episode meaningful —
 * that every prediction preceded its observation. The trajectory is the evidence; the
 * numbers are its digest.</p>
 *
 * <p><b>Why the timestamp is a string and not a clock reading.</b> The record says WHEN an
 * episode happened; it does not decide anything, and a decision that depended on the time it
 * was taken would be a decision about the clock rather than about the world. The instant is
 * supplied by the caller, and the default is a constant, so a report that carries no
 * meaningful instant is still comparable with another report of the same episode.</p>
 *
 * @param episodeId         the identity of the episode
 * @param seed              the seed the episode's probe order was derived from
 * @param probePolicy       the policy that chose the probe order
 * @param terminationReason why the episode ended
 * @param steps             how many probes the episode made
 * @param finalSurvivors    how many hypotheses survived the episode's observations
 * @param survivorId        the identifier of the surviving hypothesis, when exactly one
 *                          survived, and empty otherwise
 * @param utility           the fraction of the hypothesis space the learner eliminated
 * @param trajectory        the steps of the episode, in order
 * @param timestamp         the instant the episode reports, supplied by the caller
 */
public record EpisodeReport(String episodeId,
                             long seed,
                             ProbePolicy probePolicy,
                             TerminationReason terminationReason,
                             int steps,
                             int finalSurvivors,
                             String survivorId,
                             double utility,
                             List<EpisodeStep> trajectory,
                             String timestamp) implements EnvironmentEpisode {

    /**
     * Why an episode ended.
     *
     * <p>The reason is part of the report rather than something a reader infers from the
     * numbers: "the model settled" and "the step bound was reached" produce the same shape
     * of report and completely different verdicts, and a run that was cut short must not be
     * readable as a run that finished.</p>
     */
    public enum TerminationReason {

        /**
         * The hypothesis space narrowed to exactly one survivor.
         *
         * <p>This is the success condition: the learner has identified the rule, because
         * every other rule in the space has been contradicted by an observation.</p>
         */
        MODEL_SETTLED,

        /**
         * Every assignment of the input domain was observed.
         *
         * <p>The world has nothing left to teach. The model may or may not have settled on
         * the way there — with a consistent world it does, and with an inconsistent one it
         * does not — and the report says which, through the survivor count.</p>
         */
        DOMAIN_EXHAUSTED,

        /**
         * The step bound was reached before the model settled.
         *
         * <p>The episode was CUT SHORT. It is not a failure of the learner; it is a failure
         * of the budget, and the two must not be read the same way.</p>
         */
        STEP_LIMIT_REACHED,

        /**
         * No hypothesis survived the episode's observations.
         *
         * <p>The observations contradict every rule in the space, so the model has no
         * account of its own data. The episode stops rather than asking a question the
         * learner cannot answer.</p>
         */
        NO_SURVIVING_HYPOTHESIS
    }

    public EpisodeReport {
        if (episodeId == null || episodeId.isBlank()) {
            throw new IllegalArgumentException("an episode needs an identity");
        }
        if (probePolicy == null) {
            throw new IllegalArgumentException("an episode must name the policy that ordered its probes");
        }
        if (terminationReason == null) {
            throw new IllegalArgumentException("an episode must say why it ended");
        }
        if (steps < 0) {
            throw new IllegalArgumentException("an episode cannot have a negative number of steps");
        }
        if (finalSurvivors < 0) {
            throw new IllegalArgumentException("an episode cannot have a negative number of survivors");
        }
        if (Double.isNaN(utility)) {
            throw new IllegalArgumentException("the utility of an episode must be a number");
        }
        trajectory = trajectory == null ? List.of() : List.copyOf(trajectory);
        if (trajectory.size() != steps) {
            throw new IllegalArgumentException("the trajectory holds " + trajectory.size()
                    + " step(s) but the episode reports " + steps);
        }
    }

    @Override
    public boolean done() {
        return true;
    }

    @Override
    public String successCriterion() {
        return EnvironmentEpisode.SUCCESS_CRITERION;
    }

    /**
     * Whether the learner identified the rule.
     *
     * <p>Exactly one survivor means that every other rule in the space was contradicted by
     * an observation, and the one that survived explains all of them.</p>
     *
     * @return true when the model settled
     */
    public boolean learned() {
        return finalSurvivors == MicroWorldEpisodeRunner.SETTLED_SURVIVOR_COUNT;
    }

    /**
     * Whether the episode probed the same assignment twice.
     *
     * <p>A repeated probe is not a failure — a world that contradicts itself may have to be
     * asked again — but it is the difference between an episode that learned and an episode
     * that kept looking, and a reader needs to be able to tell them apart.</p>
     *
     * @return true when some assignment was probed more than once
     */
    public boolean repeatedProbe() {
        return distinctAssignments() < trajectory.size();
    }

    /**
     * How many distinct assignments the episode probed.
     *
     * @return the number of distinct assignments
     */
    public int distinctAssignments() {
        return (int) trajectory.stream().map(step -> step.input().key()).distinct().count();
    }

    /**
     * The information the episode's probes were expected to produce, added up.
     *
     * <p>This is the microworld's own expected-information-gain measure, summed over the
     * steps. It is reported beside the utility and is not a substitute for it: the gain is
     * what was EXPECTED of each probe before it was made, and the utility is what the
     * episode actually achieved.</p>
     *
     * @return the total expected information gain
     */
    public double totalInformationGain() {
        double total = 0.0;
        for (EpisodeStep step : trajectory) {
            total += step.informationGain();
        }
        return total;
    }

    @Override
    public String toString() {
        return "EpisodeReport[" + episodeId + " steps=" + steps
                + " survivors=" + finalSurvivors
                + " utility=" + utility
                + " ended=" + terminationReason + "]";
    }
}
