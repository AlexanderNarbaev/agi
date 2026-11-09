package io.matrix.learning.env;

import io.matrix.cognitive.microworld.HypothesisPrediction;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;

/**
 * W51 §7 — one probe of an environment, and what the learner believed when it made it.
 *
 * <p><b>Why the prediction and the observation live in ONE record.</b> A trajectory that kept
 * them apart would let a reader see them side by side and still not be able to tell which
 * came first, and "which came first" is the only question this record exists to answer. A
 * prediction is a temporal expectation: it was made BEFORE the outcome was known, and the
 * evidence that it rested on is the set of cases the session already held. Keeping the two
 * together makes that checkable rather than a matter of trust.</p>
 *
 * <p><b>Why {@code applied} is here and not inferred.</b> An observation can be recorded and
 * still not be model data: the microworld holds a contradiction out of the hypothesis set
 * until a review adjudicates it. Whether the episode's probe actually taught the learner
 * anything is therefore a fact about the model, and it is recorded as it happened.</p>
 *
 * <p><b>Why the information gain is the microworld's own.</b> The gain of a probe is the
 * fraction of the surviving hypotheses that the outcome is expected to eliminate. It is
 * computed by the microworld's own arithmetic, so a step's gain and the microworld's
 * benchmark of the same probe cannot disagree.</p>
 *
 * @param index           the position of the step in the episode, starting at ONE — the same
 *                        numbering the world's own observation identifiers use, so that the
 *                        Nth step of an episode carries the Nth observation and a trajectory
 *                        never shows a "step 0" that reads like a bug
 * @param input           the assignment that was probed
 * @param prediction      what the learner believed about the assignment, before the outcome
 * @param observation     what the world answered
 * @param applied         whether the observation was allowed to constrain the model
 * @param informationGain the fraction of the surviving hypotheses the probe was expected to
 *                        eliminate, before the outcome was known
 */
public record EpisodeStep(int index,
                          WorldInput input,
                          HypothesisPrediction prediction,
                          Observation observation,
                          boolean applied,
                          double informationGain) {

    public EpisodeStep {
        if (index < 1) {
            throw new IllegalArgumentException("a step's index must be at least one");
        }
        if (input == null) {
            throw new IllegalArgumentException("a step must name the assignment it probed");
        }
        if (prediction == null) {
            throw new IllegalArgumentException("a step must carry the prediction it made");
        }
        if (observation == null) {
            throw new IllegalArgumentException("a step must carry the observation it received");
        }
        if (Double.isNaN(informationGain) || informationGain < 0.0 || informationGain > 1.0) {
            throw new IllegalArgumentException("the expected information gain must be within [0,1]");
        }
    }

    /**
     * The value the learner predicted for the outcome variable.
     *
     * @return the predicted value
     */
    public boolean predictedValue() {
        return prediction.value();
    }

    /**
     * The value the world actually produced.
     *
     * @return the observed value
     */
    public boolean observedValue() {
        return observation.lamp();
    }

    /**
     * Whether the learner was sure when it predicted.
     *
     * <p>A prediction that was not unanimous is an admission of ignorance, not a claim, and
     * a step that reads it as a claim would misreport how much the learner actually knew.</p>
     *
     * @return true when the surviving hypotheses agreed
     */
    public boolean settledPrediction() {
        return prediction.unanimous();
    }

    /**
     * Whether the prediction was contradicted by the outcome that followed it.
     *
     * @return true when the predicted value and the observed value differ
     */
    public boolean contradicted() {
        return predictedValue() != observedValue();
    }

    @Override
    public String toString() {
        return "EpisodeStep[" + index + " " + input.key()
                + " predicted=" + predictedValue() + " observed=" + observedValue()
                + " applied=" + applied + " gain=" + informationGain + "]";
    }
}
