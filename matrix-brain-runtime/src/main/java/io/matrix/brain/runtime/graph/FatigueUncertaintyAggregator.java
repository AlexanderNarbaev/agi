package io.matrix.brain.runtime.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * RECON-W38.2 (M4) — candidate drivers for feeding measured reactions into
 * {@link NeuroModulatorHub}.
 *
 * <p><b>Not yet wired.</b> This class has no production caller; it is referenced only by the
 * test classes {@code FatigueUncertaintyTest}, {@code AttentionSafetyFloorTest} and
 * {@code NeuromodulationStreamSimulationTest}. It runs in shadow mode by design until the milestone
 * that connects it to a serving path, so nothing it computes currently influences a decision.</p>
 *
 * <p>The hub is immutable, so aggregation means: look at a serving observation, return the NEXT
 * hub. Two measured drivers, both deterministic (no random, no wall-clock):</p>
 *
 * <ul>
 * <li><b>Fatigue — query volume.</b> Every served query advances
 * {@link #FATIGUE_PER_SERVED_QUERY} until fatigue saturates at 1.0. The hub's existing
 * sleepDemand()/shouldSleep() machinery (threshold 0.7, capped reward cancellation, severe-fatigue
 * floor) turns that fatigue into the sleep-demand signal the sleep scheduler is expected to consume
 * in RECON-W38.4. That consumer does not exist yet, so today the signal is computed and discarded.</li>
 * <li><b>Uncertainty — similarity spread.</b> The class computes a <i>proposed</i> lower
 * uncertaintyTolerance from the spread of one query's retrieval candidate scores. It is NOT
 * established that a lower tolerance makes the system more conservative: the tag
 * {@code TAG_UNCERTAINTY_TOLERANCE} is written by the hub but read by no code in the repository,
 * and the polarity of a future consumer is not committed. Treat this as plumbing awaiting a
 * consumer, not as demonstrated behaviour.</li>
 * </ul>
 *
 * <p><b>Safety floor (binding operator rule):</b> this aggregator never changes attentionLevel —
 * attention is carried through bit-exact, so the effective retrieval floor and serving behaviour
 * are whatever the caller's hub already had; attention stays at
 * NeuroModulatorHub.NEUTRAL_ATTENTION in the live path and dynamic modulation remains shadow-mode.
 * The proposed tolerance is non-increasing over every legal input, so a consumer that treats a
 * lower tolerance as stricter cannot be made laxer here; whether a lower tolerance <i>is</i>
 * stricter is not established. Fatigue and tolerance are clamped locally; the hub clamps again on
 * construction.</p>
 *
 * <p><b>Fatigue is a one-way ratchet here.</b> {@link #observe} only ever adds fatigue, and
 * nothing in the hub lowers it (the hub's decayRate applies to weights, not to fatigue). At the
 * default sleep-demand threshold of 0.7, shouldSleep() therefore latches true after fourteen
 * served queries and stays latched. This class deliberately provides no reset: the reset contract
 * belongs to RealSleepScheduler in a later milestone.</p>
 */
public final class FatigueUncertaintyAggregator {

    /** Fatigue added by one served query. Pure query volume; a hit or a miss both cost effort. */
    public static final double FATIGUE_PER_SERVED_QUERY = 0.05;

    /**
     * How strongly a fully spread query (variance 1) lowers the proposed uncertainty tolerance,
     * per observation. Linear in the measured variance. Starting at or above
     * {@link #MIN_UNCERTAINTY_TOLERANCE} the tolerance never drops below it; starting below it the
     * value is preserved unchanged and never raised, because refusal-only outranks the floor.
     */
    public static final double UNCERTAINTY_STEP_ON_MAX_VARIANCE = 0.05;

    /** Uncertainty tolerance may drop only this far — conservatism is additive, never absolute. */
    public static final double MIN_UNCERTAINTY_TOLERANCE = 0.1;

    private static final int MIN_CANDIDATES_FOR_VARIANCE = 2;

    private FatigueUncertaintyAggregator() {
    }

    /**
     * Feed one serving observation into the hub state.
     *
     * <p>Degenerate candidate evidence carries no spread signal: a null list, a list with fewer
     * than two usable elements, and lists whose elements are all null, NaN or infinite all yield
     * variance 0. Null, NaN and infinite elements are dropped rather than allowed to poison the
     * mean, so a single bad score cannot mask the rest of the evidence.</p>
     *
     * @param current                the hub state the observation occurred in; must not be null
     * @param candidateSimilarities  the retrieval candidate scores of the query; may be null
     * @return the next hub state; attention, reward and sleep-demand threshold are carried through
     *         unchanged, and the returned tolerance is never greater than
     *         {@code current.uncertaintyTolerance()}
     * @throws NullPointerException if {@code current} is null
     */
    public static NeuroModulatorHub observe(NeuroModulatorHub current, List<Double> candidateSimilarities) {
        double fatigue = clamp01(current.fatigueIndex() + FATIGUE_PER_SERVED_QUERY);
        double variance = varianceOf(candidateSimilarities);
        double proposed = Math.max(MIN_UNCERTAINTY_TOLERANCE,
                clamp01(current.uncertaintyTolerance() - variance * UNCERTAINTY_STEP_ON_MAX_VARIANCE));
        // Bounding by the current value is what makes the refusal-only rule total: the hub
        // constructor clamps tolerance to [0,1] only, so a caller may legitimately supply a value
        // below MIN_UNCERTAINTY_TOLERANCE, and max(...) alone would then RAISE it.
        double tolerance = Math.min(current.uncertaintyTolerance(), proposed);
        return new NeuroModulatorHub(current.attentionLevel(), tolerance, current.rewardSignal(),
                fatigue, current.sleepDemandThreshold());
    }

    /**
     * Empirical variance of the candidate scores over their usable (non-null, finite) elements;
     * 0 when fewer than two survive.
     */
    static double varianceOf(List<Double> values) {
        if (values == null) return 0.0;
        List<Double> usable = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value != null && !value.isNaN() && !value.isInfinite()) {
                usable.add(value);
            }
        }
        if (usable.size() < MIN_CANDIDATES_FOR_VARIANCE) return 0.0;
        double mean = 0.0;
        for (double v : usable) mean += v;
        mean /= usable.size();
        double sq = 0.0;
        for (double v : usable) {
            double d = v - mean;
            sq += d * d;
        }
        return clamp01(sq / usable.size());
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}