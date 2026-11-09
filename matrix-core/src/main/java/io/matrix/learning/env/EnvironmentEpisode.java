package io.matrix.learning.env;

import java.util.List;

/**
 * W51 §7 — one episode of learning through an environment.
 *
 * <p>This is the seam W51 §7 needs and the micro-world deliberately does not have: the
 * micro-world is a WORLD (a rule the learner probes), and a learning loop needs an EPISODE
 * (a bounded run of that learner over that world, with a verdict). The two are different
 * things, and the world already exists in full — so the episode is built <em>above</em> it,
 * not inside it.</p>
 *
 * <p><b>Why the episode is a separate concept.</b> The microworld has no reward, no
 * termination condition and no notion of a run: a session accumulates observations until
 * somebody stops looking at it. An episode is what turns that accumulation into a measurable
 * unit of learning — a bounded sequence of probes, the trajectory they produced, and a single
 * number that says how much the learner got out of them.</p>
 *
 * <p><b>Why the success criterion is a constant and not a method.</b> A criterion that a
 * caller could vary per episode would not be a criterion; it would be a preference. One
 * definition, written down once, is what makes two episodes comparable at all — and the
 * comparison is the whole point of running an episode twice.</p>
 */
public interface EnvironmentEpisode {

    /**
     * The condition under which an episode counts as successful.
     *
     * <p>The microworld's model is a set of candidate rules, and it is settled when exactly
     * one of them survives every observation. There is no other success condition available
     * in this world: the outcome is Boolean, there is no reward, and the hypothesis set is the
     * only thing that changes as the learner learns. So the criterion is the size of that set,
     * and it is written as a machine-readable expression so that a report can be checked
     * without a human reading the prose.</p>
     */
    String SUCCESS_CRITERION = "survivingHypotheses == 1";

    /**
     * The identity of the episode.
     *
     * <p>The caller names the episode. Nothing in the learning depends on the name — it is
     * an identity, not a seed — but two episodes with the same name and the same seed are
     * the same episode, and that is worth being able to say.</p>
     *
     * @return the episode's identity
     */
    String episodeId();

    /**
     * The steps of the episode, in the order they happened.
     *
     * <p>Each step carries the prediction that was made BEFORE the outcome was known, the
     * outcome that followed it, and whether the outcome was allowed to constrain the model.
     * The order is the point: a trajectory that cannot be read in order cannot be checked
     * for the thing that matters, which is whether the prediction preceded the observation.</p>
     *
     * @return the steps, oldest first
     */
    List<EpisodeStep> trajectory();

    /**
     * How many probes the episode made.
     *
     * @return the number of steps
     */
    int steps();

    /**
     * Whether the episode has reached a terminal condition.
     *
     * <p>An episode produced by a runner is always finished — the runner returns only when
     * the model settled, the input domain was exhausted, the step bound was reached, or the
     * hypothesis space emptied. The method exists so that a caller can handle a live episode
     * and a finished one through ONE interface, and so that the reason for finishing is
     * asked for explicitly rather than inferred.</p>
     *
     * @return true when the episode is finished
     */
    boolean done();

    /**
     * How much the learner got out of the episode.
     *
     * <p>The scale is the microworld's own: the fraction of the hypothesis space the learner
     * eliminated. It is computed from the microworld's numbers and introduces no second
     * scale, so an episode of this world and a benchmark of this world are measured the same
     * way.</p>
     *
     * @return the utility of the episode
     */
    double utility();

    /**
     * The condition under which this episode counts as successful.
     *
     * @return the success criterion
     */
    String successCriterion();
}
