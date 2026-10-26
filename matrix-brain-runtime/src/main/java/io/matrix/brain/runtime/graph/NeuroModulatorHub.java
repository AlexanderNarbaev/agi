package io.matrix.brain.runtime.graph;

/**
 * Global cognitive state, broadcast to every node through the {@link Activation} tag map.
 *
 * <p>The brief calls these "hormones": a small set of scalars that bias local processing without
 * being consulted by it. Each node reads the tags it cares about; none of them queries the hub
 * directly, so a node cannot reach back and change the state it is being driven by.</p>
 *
 * <h2>What is measured and what is not</h2>
 *
 * <p>Only {@link #retrievalFloor()} is derived from measurement. {@code ContentSimilarity} has a
 * published ROC (AUC 0.716, recall ~0.29 at the 0.20 floor), so raising the floor trades recall
 * for precision along a curve that was actually measured rather than assumed. The other parameters
 * are <b>plumbing, not claims</b>: nothing here has yet been shown to improve an outcome, and
 * {@code AttentionLevel} in particular must not be described as improving retrieval until
 * {@code NeuromodulationEffectTest} measures it.</p>
 *
 * <h2>Safety constraint</h2>
 *
 * <p>No modulator may loosen a veto threshold. DESIGN-68 §4.2 proposes that
 * {@code Attention} may raise retrieval precision in sensory and association nodes only. This class
 * enforces the direction: {@link #retrievalFloor()} moves <em>up</em> with attention and never
 * below {@link ContentSimilarity#RETRIEVAL_FLOOR}, so the worst case is refusing more, never
 * serving more. A neuromodulator that could lower a safety-relevant threshold would be a way for
 * the system to talk itself out of a gate.</p>
 *
 * <p>All values are clamped to their documented ranges on construction, so no caller can construct
 * an out-of-contract state and have it propagate.</p>
 */
public final class NeuroModulatorHub {

    /** The measured production retrieval floor. A modulator may raise it, never lower it. */
    public static final double BASE_RETRIEVAL_FLOOR = 0.20;

    /**
     * The floor reached at full attention.
     *
     * <p>0.35 sits just under the best zero-FPR point of the published ROC (0.333 at TPR 0.25),
     * where recall is roughly 0.25-0.29. Chosen from the measured curve, not tuned to taste.</p>
     */
    public static final double ATTENTIVE_RETRIEVAL_FLOOR = 0.35;

    private final double attentionLevel;
    private final double uncertaintyTolerance;
    private final double rewardSignal;
    private final double fatigueIndex;
    private final double sleepDemandThreshold;

    /**
     * Fatigue above which sleep is requested, expressed in the same [0,1] units.
     *
     * <p>Deliberately not 1.0: a system that only sleeps when fully exhausted never consolidates
     * under partial load, which is the normal operating regime.</p>
     */
    public static final double DEFAULT_SLEEP_DEMAND_THRESHOLD = 0.7;

    /**
     * How much reward may cancel fatigue, at most.
     *
     * <p>DESIGN-68 §4.3: if Reward could cancel Fatigue without bound, an environment that rewards
     * fast answers could prevent consolidation indefinitely. The cancellation is capped at half of
     * the fatigue, so fatigue can be relieved but never erased by reward alone.</p>
     */
    public static final double MAX_REWARD_FATIGUE_CANCEL = 0.5;

    /**
     * Attention that leaves retrieval exactly at the measured production floor.
     *
     * <p>This is the value {@link TrueMindCycle} must use until someone deliberately chooses
     * otherwise. At mid attention the floor rises to 0.275, which changes serving behaviour — more
     * refusals — and that is a user-visible change that belongs to a decision, not to the act of
     * wiring the class in.</p>
     */
    public static final double NEUTRAL_ATTENTION = 0.0;

    /** A hub that reproduces current production behaviour exactly. */
    public static NeuroModulatorHub productionDefault() {
        return new NeuroModulatorHub(NEUTRAL_ATTENTION, 0.5, 0.0, 0.0,
                DEFAULT_SLEEP_DEMAND_THRESHOLD);
    }

    /** A hub at rest: mid attention, mid uncertainty, no reward, no fatigue. */
    public NeuroModulatorHub() {
        this(0.5, 0.5, 0.0, 0.0, DEFAULT_SLEEP_DEMAND_THRESHOLD);
    }

    /**
     * @param attention          0..1, scales signal-to-noise in sensory nodes
     * @param uncertaintyTolerance 0..1, loosens or tightens acceptance of new facts
     * @param rewardSignal       any finite value; positive is success
     * @param fatigueIndex       0..1, drives sleep demand and weight decay
     * @param sleepDemandThreshold fatigue at which sleep is requested, 0..1
     */
    public NeuroModulatorHub(double attention, double uncertaintyTolerance, double rewardSignal,
                             double fatigueIndex, double sleepDemandThreshold) {
        this.attentionLevel = clamp01(attention);
        this.uncertaintyTolerance = clamp01(uncertaintyTolerance);
        this.rewardSignal = Double.isNaN(rewardSignal) ? 0.0 : rewardSignal;
        this.fatigueIndex = clamp01(fatigueIndex);
        this.sleepDemandThreshold = clamp01(sleepDemandThreshold);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    public double attentionLevel() {
        return attentionLevel;
    }

    public double uncertaintyTolerance() {
        return uncertaintyTolerance;
    }

    public double rewardSignal() {
        return rewardSignal;
    }

    public double fatigueIndex() {
        return fatigueIndex;
    }

    /** The configured fatigue level above which sleep is requested. */
    public double sleepDemandThreshold() {
        return sleepDemandThreshold;
    }

    /**
     * The effective retrieval floor for this cognitive state.
     *
     * <p>Interpolates from {@link #BASE_RETRIEVAL_FLOOR} at zero attention to
     * {@link #ATTENTIVE_RETRIEVAL_FLOOR} at full attention. Monotonic and bounded below by the
     * measured base: <b>this can only ever refuse more, never serve more</b>.</p>
     */
    public double retrievalFloor() {
        return BASE_RETRIEVAL_FLOOR
                + attentionLevel * (ATTENTIVE_RETRIEVAL_FLOOR - BASE_RETRIEVAL_FLOOR);
    }

    /**
     * Sleep demand in [0,1], after the capped reward cancellation.
     *
     * <p>Reward relieves fatigue but cannot erase it: the cancellation is at most
     * {@link #MAX_REWARD_FATIGUE_CANCEL} of the fatigue, so a reward-rich environment slows
     * consolidation without being able to prevent it.</p>
     */
    public double sleepDemand() {
        if (fatigueIndex <= 0.0) return 0.0;
        // DESIGN-68 §4.3: reward may relieve fatigue but never erase it. The first version of this
        // capped the cancellation at a FRACTION of fatigue (0.5), which looks safe but is not:
        // at fatigue=1.0 the demand fell to 0.5, below the 0.7 sleep threshold, so a permanently
        // reward-rich environment could suppress consolidation forever. A cap expressed as a
        // fraction of the quantity being cancelled is only a floor when the fraction cannot cross
        // the threshold; here it can. The bound is therefore expressed as a FLOOR on the residual
        // demand once fatigue is severe, which is the property that actually matters.
        double relieved = fatigueIndex;
        if (rewardSignal > 0.0) {
            double cancellation = Math.min(fatigueIndex * MAX_REWARD_FATIGUE_CANCEL,
                    Math.min(1.0, rewardSignal) * MAX_REWARD_FATIGUE_CANCEL);
            relieved = fatigueIndex - cancellation;
        }
        // Severe fatigue guarantees a sleep request regardless of reward.
        if (fatigueIndex >= SEVERE_FATIGUE) {
            relieved = Math.max(relieved, MIN_DEMAND_AT_SEVERE_FATIGUE);
        }
        return clamp01(relieved);
    }

    /**
     * Fatigue at or above this level guarantees a sleep request.
     *
     * <p>0.8 rather than the 0.7 threshold so the floor sits strictly above it and the guarantee
     * holds even if the threshold is configured lower.</p>
     */
    public static final double SEVERE_FATIGUE = 0.8;

    /**
     * Demand floor once {@link #SEVERE_FATIGUE} is reached. Set above the default threshold so a
     * reward-rich environment still consolidates.
     */
    public static final double MIN_DEMAND_AT_SEVERE_FATIGUE = 0.85;

    /** True when sleep should be requested. */
    public boolean shouldSleep() {
        return sleepDemand() >= sleepDemandThreshold;
    }

    /**
     * Per-step decay applied by plasticity while fatigued.
     *
     * <p>Fatigue accelerates forgetting of weak connections, which is what makes consolidation
     * necessary rather than merely available.</p>
     */
    public double decayRate() {
        return 0.001 * (1.0 + fatigueIndex);
    }

    /**
     * Writes this state onto an activation so downstream nodes see it.
     *
     * <p>Nodes read tags; they never hold a hub reference. That keeps the direction of influence
     * one-way and makes a node impossible to accidentally reconfigure from inside a propagate.</p>
     */
    public Activation applyTo(Activation activation) {
        return activation
                .withTag(TAG_ATTENTION, attentionLevel)
                .withTag(TAG_UNCERTAINTY_TOLERANCE, uncertaintyTolerance)
                .withTag(TAG_REWARD, rewardSignal)
                .withTag(TAG_FATIGUE, fatigueIndex)
                .withTag(TAG_RETRIEVAL_FLOOR, retrievalFloor())
                .withTag(TAG_SLEEP_DEMAND, sleepDemand());
    }

    public static final String TAG_ATTENTION = "neuromodulator.attention";
    public static final String TAG_UNCERTAINTY_TOLERANCE = "neuromodulator.uncertaintyTolerance";
    public static final String TAG_REWARD = "neuromodulator.reward";
    public static final String TAG_FATIGUE = "neuromodulator.fatigue";
    public static final String TAG_RETRIEVAL_FLOOR = "neuromodulator.retrievalFloor";
    public static final String TAG_SLEEP_DEMAND = "neuromodulator.sleepDemand";

    @Override
    public String toString() {
        return String.format(
                "NeuroModulatorHub[attention=%.2f uncertainty=%.2f reward=%.2f fatigue=%.2f "
                        + "floor=%.3f sleepDemand=%.2f]",
                attentionLevel, uncertaintyTolerance, rewardSignal, fatigueIndex,
                retrievalFloor(), sleepDemand());
    }
}