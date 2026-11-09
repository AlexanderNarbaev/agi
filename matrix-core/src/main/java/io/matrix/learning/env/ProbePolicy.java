package io.matrix.learning.env;

/**
 * W51 §7 — how an episode chooses the assignment it probes next.
 *
 * <p>The microworld asks the learner what to ask: {@code proposeExperiment()} returns the
 * assignment that separates the surviving hypotheses the most. That is the right default,
 * because it is the learner's own request and the environment's response to it is what the
 * episode is measuring.</p>
 *
 * <p>The second policy exists because an episode that can only be run one way cannot be
 * COMPARED. A learner whose probe order is fixed by its own certainty measure always takes
 * the same path, so the only thing two such runs can disagree about is the starting point.
 * The exploratory policy puts the order back under the caller's control, deterministically:
 * the same seed chooses the same order, and a different seed chooses a different one.</p>
 */
public enum ProbePolicy {

    /**
     * Probe exactly what the learner proposed.
     *
     * <p>The probe order is the learner's own uncertainty, and the episode measures how fast
     * that uncertainty collapses. The seed does not enter this policy: the order is already
     * determined, and a seed that did not change anything would be a decoration.</p>
     */
    GREEDY,

    /**
     * Probe a seeded choice among the assignments the learner has not observed yet.
     *
     * <p>The choice is made by {@link io.matrix.cognitive.ExploratoryActionSampler}, whose
     * seed is a pure function of the episode's identity, its seed and the step number. The
     * order is therefore reproducible without being greedy, and the same seed gives the same
     * episode on every run.</p>
     */
    EXPLORATORY
}
