package io.matrix.brain.runtime.graph;

/**
 * The weight update applied to a synapse after a propagation step.
 *
 * <p>Implements the brief's rule:</p>
 *
 * <pre>w_new = w_old + eta * (pre_activity * post_activity - decay)</pre>
 *
 * <p>Pure and total: no RNG, no global state, no I/O. That is deliberate — a learning rule whose
 * result depends on anything outside its arguments cannot be tested for correctness, only for
 * agreement with itself. Any stochastic exploration belongs in the <em>choice</em> of which
 * synapses to update (see {@link SeededRandom}), not in the arithmetic of the update.</p>
 *
 * <p>The {@code decay} term is what makes unused connections fade: a synapse carrying no
 * correlation loses {@code eta * decay} per step regardless of activity. Combined with clamping,
 * weights cannot run away.</p>
 */
public final class PlasticityRule {

    private PlasticityRule() {
    }

    /**
     * Computes the next weight for one synapse.
     *
     * @param current the current weight (any value; clamped on return)
     * @param pre     pre-synaptic activation magnitude
     * @param post    post-synaptic activation magnitude
     * @param eta     learning rate; larger values adapt faster and overshoot more
     * @param decay   per-step decay applied in the absence of correlation; must be >= 0
     * @return the updated weight, clamped to [-1, 1]
     * @throws IllegalArgumentException if {@code decay} is negative
     */
    public static double update(double current, double pre, double post, double eta, double decay) {
        if (decay < 0.0) {
            throw new IllegalArgumentException("decay must be >= 0, got " + decay);
        }
        double next = current + eta * (pre * post - decay);
        return Synapse.clamp(next);
    }

    /**
     * Convenience form for the common case of a unit learning rate and zero decay, which makes
     * the update a pure Hebbian product with clamping.
     */
    public static double hebbian(double current, double pre, double post) {
        return update(current, pre, post, 1.0, 0.0);
    }

    /** Convenience form for decay-only: no correlation, weight shrinks by {@code eta * decay}. */
    public static double decay(double current, double decay) {
        return update(current, 0.0, 0.0, 1.0, decay);
    }
}
