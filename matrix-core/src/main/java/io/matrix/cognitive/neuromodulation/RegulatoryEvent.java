package io.matrix.cognitive.neuromodulation;

/**
 * W44.3 — one explicit thing that happened, in the shape the regulatory dynamics can consume.
 *
 * <p>The W44 directive's rule about neuromodulation is that a name must not be a number and a
 * modulator must not be free to change anything it likes. This record is the input side of that
 * rule. An event says WHAT happened, HOW MUCH of it happened, and WHO reported it. Nothing else.
 * There is no field for "how the system felt about it", because a feeling is not an observation and
 * a drive built on one would be a drive built on a name.</p>
 *
 * <p><b>Why the record carries no timestamp and no step count.</b> Both are supplied by the
 * caller to {@link DriveDynamics#advance}, because a decision path that reads a clock is not
 * reproducible: two runs of the same scenario would produce two different regulatory states, and
 * the difference between them could not be attributed to anything. CONSTITUTION Article I asks for
 * determinism; the way to get it here is to make the caller state the step rather than have the
 * system read it.</p>
 *
 * <p><b>Why the magnitude is bounded to [0,1].</b> A magnitude is a share of a unit event, not a
 * count. An unbounded magnitude would let one event of "large magnitude" dominate every drive at
 * once, and a drive that dominates everything is a drive that has stopped being a signal and
 * become a mood.</p>
 */
public record RegulatoryEvent(Kind kind, double magnitude, String source) {

    /** The magnitude of an event whose size was not measured, and therefore is not claimed. */
    public static final double UNIT_MAGNITUDE = 1.0;

    public RegulatoryEvent {
        if (kind == null) {
            throw new IllegalArgumentException("event kind must not be null");
        }
        if (magnitude < 0.0 || magnitude > 1.0 || Double.isNaN(magnitude)) {
            throw new IllegalArgumentException("event magnitude must be within [0,1]");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("event source must not be blank");
        }
    }

    /** An event of unit magnitude. */
    public static RegulatoryEvent of(Kind kind, String source) {
        return new RegulatoryEvent(kind, UNIT_MAGNITUDE, source);
    }

    /** An event of a measured magnitude. */
    public static RegulatoryEvent of(Kind kind, double magnitude, String source) {
        return new RegulatoryEvent(kind, magnitude, source);
    }

    /**
     * The kinds of thing the regulatory dynamics knows how to react to.
     *
     * <p>The set is deliberately short. Every kind added here is a new way for a drive to move, and
     * a drive that moves is a drive that has to be justified against the W44 rule that a modulator
     * may not weaken a gate. Adding a kind therefore adds work, and the cost of adding it should
     * be paid deliberately.</p>
     */
    public enum Kind {

        /** A task finished as it was supposed to. Relieves load, relieves outstanding error. */
        TASK_COMPLETED,

        /** A task did not finish as it was supposed to. Adds load, adds error, raises vigilance. */
        TASK_FAILED,

        /** The system's prediction was compared with an outcome and the two disagreed. */
        PREDICTION_ERROR,

        /** Input arrived that the current model does not recognise. */
        NOVEL_INPUT,

        /**
         * A sample of operational pressure was taken.
         *
         * <p>The name says "sample" on purpose. Sustained load is the one drive that must not be
         * inferred from a single event, so the dynamics only ever raises it when the caller
         * explicitly reports that a sample was taken.</p>
         */
        LOAD_SAMPLE,

        /**
         * The interaction was rewarded.
         *
         * <p>This is the only event that may relieve the consolidation demand, and it is bounded
         * so that it cannot: see {@link RegulationController#effectiveConsolidationDemand}. The
         * reasoning behind that bound is the one already written down in
         * {@code NeuroModulatorHub.sleepDemand}.</p>
         */
        REWARD,

        /**
         * The model's surviving hypotheses disagree, so the system does not know what it would
         * answer.
         *
         * <p>This is NOT novelty, and it is NOT a task failure, and it is NOT a reward. The three
         * are different facts and the dynamics treats them differently:</p>
         *
         * <ul>
         *   <li>novelty says the input was unfamiliar; unsettled says the MODEL is undecided, which
         *       can happen on entirely familiar input;</li>
         *   <li>a failure says an attempt did not succeed; unsettled says no attempt was made
         *       because nothing could be decided, so there is nothing to fail;</li>
         *   <li>a reward says the outcome was better than expected; an unsettled model has no
         *       outcome to have been better or worse than, and recording one would be a claim the
         *       system cannot support.</li>
         * </ul>
         *
         * <p>For those reasons it raises the {@code UNCERTAINTY} drive and nothing else. The
         * consequence the caller can rely on is the one that matters: the system asks for a
         * discriminating test sooner, and it does not become more curious, more loaded, more
         * vigilant or more rewarded.</p>
         */
        MODEL_UNSETTLED
    }
}
