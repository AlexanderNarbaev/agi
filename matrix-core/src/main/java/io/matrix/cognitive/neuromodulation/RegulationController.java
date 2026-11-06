package io.matrix.cognitive.neuromodulation;

import io.matrix.cognitive.DriveSignal;

import java.util.List;
import java.util.Objects;

/**
 * W44.3 — turns a regulatory state into the bounded influence it is allowed to have.
 *
 * <p>This is the only place where a drive becomes a decision input, and it is deliberately a very
 * narrow place. A drive does not reach into a subsystem; it produces a {@link RegulationBias}, the
 * subsystem asks for the bias it is allowed to use, and the bias is a number in a fixed range. The
 * W44 directive's rule about neuromodulation is that a modulator must not be able to weaken a
 * gate, and the way a rule like that is kept is by making the surface through which a modulator
 * could act small, closed, and one-sided.</p>
 *
 * <h2>Every bias is at least as conservative as the neutral one</h2>
 *
 * <p>Look at the four knobs from the neutral values and notice the direction of each:</p>
 *
 * <ul>
 *   <li>{@code costWeightMultiplier} starts at 1.0 and may only go UP. A drive can make the system
 *       prefer the cheaper method, and it can never make the expensive method look cheap.</li>
 *   <li>{@code evidenceRequestThreshold} starts at 1.0 — "never demand more evidence" — and may
 *       only go DOWN. A drive can make the system ask for a discriminating test sooner; it can
 *       never make it stop asking.</li>
 *   <li>{@code admitNonCriticalTask} starts at true and may only go false. A drive can stop the
 *       system taking on more work; it can never authorise more work than the default allows.</li>
 *   <li>{@code taskPriorityMultiplier} starts at 1.0 and may only go UP. A drive can make a task
 *       more important; it can never make an important task less important.</li>
 * </ul>
 *
 * <p>There is no knob in the other direction. That is the whole design, and it is checked
 * exhaustively by the test that sweeps every dimension against every effect.</p>
 *
 * <h2>The consolidation bound</h2>
 *
 * <p>{@code CONSOLIDATION_DEMAND} is the one drive a reward may relieve, and the relief is bounded
 * so that it cannot make the demand disappear. The reasoning is the one already written down in
 * {@code matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/graph/NeuroModulatorHub.java}
 * (the {@code sleepDemand()} method and the constants around it): a cap expressed as a fraction of
 * the quantity being cancelled is only a floor when the fraction cannot cross the threshold, and
 * here it can — so the bound is expressed as a FLOOR on the residual demand, placed above the
 * threshold at which the system would stop admitting work. A reward-rich environment may slow
 * consolidation down. It may not prevent it.</p>
 *
 * <p>The floor is applied RELATIVE to the demand — {@code max(relieved, min(demand, FLOOR))} —
 * rather than as an absolute number. The absolute form would create demand that was never there,
 * which is the one thing a floor must not do. The relative form cannot: it is never above the
 * demand it started from.</p>
 */
public final class RegulationController {

    // ── cost preference ────────────────────────────────────────────────────────────

    /**
     * CONVENTION — the sustained load above which the system starts preferring cheap methods.
     *
     * <p>Half the scale, so that a system under real pressure has crossed the line before the
     * preference starts to bite.</p>
     */
    public static final double LOAD_COST_THRESHOLD = 0.5;

    /**
     * CONVENTION — how much one unit of load above the threshold raises the cost weight.
     *
     * <p>At a load of 1.0 the multiplier is 1.5: the expensive method becomes one and a half
     * times as expensive, which changes the ordering of two comparable methods without ever
     * making an expensive method look cheap.</p>
     */
    public static final double LOAD_COST_GAIN = 1.0;

    // ── evidence request ───────────────────────────────────────────────────────────

    /**
     * CONVENTION — how much uncertainty lowers the threshold at which the system asks for
     * evidence.
     *
     * <p>At an uncertainty of 1.0 the threshold is 0.5, so a discriminating test is requested as
     * soon as the current evidence is half as good as a full answer would be.</p>
     */
    public static final double UNCERTAINTY_EVIDENCE_GAIN = 0.5;

    // ── task priority ──────────────────────────────────────────────────────────────

    /**
     * CONVENTION — how much a priority-bearing drive raises a task's priority.
     *
     * <p>At a drive level of 1.0 the multiplier is 1.5.</p>
     */
    public static final double TASK_PRIORITY_GAIN = 0.5;

    // ── consolidation ──────────────────────────────────────────────────────────────

    /**
     * CONVENTION — the demand at or above which the system stops admitting non-critical tasks.
     *
     * <p>The same number as the sleep-demand threshold in {@code NeuroModulatorHub}, which is
     * where the reasoning behind the bound below comes from.</p>
     */
    public static final double CONSOLIDATION_ADMISSION_THRESHOLD = 0.7;

    /**
     * DERIVED — the floor below which a reward may not reduce the consolidation demand.
     *
     * <p>It is placed strictly above {@link #CONSOLIDATION_ADMISSION_THRESHOLD}, so the guarantee
     * holds even if the threshold is configured lower, and it is above the threshold for the same
     * reason {@code NeuroModulatorHub.MIN_DEMAND_AT_SEVERE_FATIGUE} sits above its own threshold:
     * a floor that can be crossed by the threshold is not a floor.</p>
     */
    public static final double CONSOLIDATION_DEMAND_FLOOR = 0.85;

    /**
     * CONVENTION — the largest share of the demand a reward may cancel.
     *
     * <p>Half, matching {@code NeuroModulatorHub.MAX_REWARD_FATIGUE_CANCEL}. The half is what
     * makes the floor necessary rather than decorative: a cap of one would let a reward erase the
     * demand outright.</p>
     */
    public static final double MAX_REWARD_CONSOLIDATION_RELIEF = 0.5;

    /** The reward that counts as the most the reward signal can carry. */
    private static final double MAX_REWARD_SIGNAL = 1.0;

    /**
     * The number of decay steps a convenience {@link #evolve(RegulatoryState, List)} applies.
     *
     * <p>CONVENTION — one step is the smallest useful interval: the events happened, and the
     * pressure they created has not had a chance to fade. A caller that wants a different interval
     * says so by using the overload that takes the step count.</p>
     */
    public static final int DEFAULT_STEPS = 1;

    /**
     * The timestamp stamped on a drive when the caller has not said when the update happened and
     * the state carries nothing to remember.
     *
     * <p>CONVENTION — and deliberately not a clock reading. The point of the constant is that it
     * is visibly not a time: a caller who cares about the timestamp supplies it.</p>
     */
    public static final String UNSPECIFIED_OBSERVED_AT = "unspecified";

    /** The dynamics this controller folds events with. */
    private final DriveDynamics dynamics;

    /** A controller that folds events with the standard dynamics. */
    public RegulationController() {
        this(DriveDynamics.standard());
    }

    /**
     * A controller that folds events with the given dynamics.
     *
     * <p>Exposed so that the kernel can hold ONE dynamics and hand it to the controller, rather
     * than holding two that could drift apart. A controller and a dynamics that disagree about
     * how a drive moves would be a bug that only shows up in the numbers.</p>
     */
    public RegulationController(DriveDynamics dynamics) {
        this.dynamics = Objects.requireNonNull(dynamics, "dynamics");
    }

    /** The dynamics this controller folds events with. */
    public DriveDynamics dynamics() {
        return dynamics;
    }

    /**
     * Fold a batch of events into the state.
     *
     * <p>This is the companion of {@link #bias(RegulatoryState, List)}: the kernel evolves the
     * state, then asks for the bias of the state it just produced. The two agree in the sense
     * that matters — <b>the events are interpreted exactly once</b>. {@code evolve} folds them
     * into the levels; {@code bias} reads the levels and applies only the reward, which is the
     * one thing an event carries that is not a level. So the pattern</p>
     *
     * <pre>
     *   RegulatoryState next = controller.evolve(current, events);
     *   RegulationBias bias  = controller.bias(next, events);
     * </pre>
     *
     * <p>does not apply the events twice, and the reward relief is applied exactly once, to the
     * demand of the evolved state.</p>
     *
     * <p>One step is applied and the timestamp is the one the state already remembers. A caller
     * that wants a different interval or a different timestamp uses the overloads that take
     * them.</p>
     */
    public RegulatoryState evolve(RegulatoryState current, List<RegulatoryEvent> events) {
        return evolve(current, events, DEFAULT_STEPS, rememberedTimestamp(current));
    }

    /** Fold a batch of events into the state, stamping the result with the caller's timestamp. */
    public RegulatoryState evolve(RegulatoryState current, List<RegulatoryEvent> events, String observedAt) {
        return evolve(current, events, DEFAULT_STEPS, observedAt);
    }

    /** Fold a batch of events into the state over an explicit number of steps. */
    public RegulatoryState evolve(RegulatoryState current,
                                   List<RegulatoryEvent> events,
                                   int steps,
                                   String observedAt) {
        return dynamics.advance(current, events, steps, observedAt);
    }

    /**
     * The timestamp the state already carries.
     *
     * <p>The most recent of the drives' timestamps, which is the most recent thing the state
     * knows about itself. The timestamps are ISO-8601 UTC, which sorts chronologically as text.
     * A state that holds no drives has nothing to remember, so the unspecified constant is
     * used.</p>
     */
    private static String rememberedTimestamp(RegulatoryState state) {
        return state.drives().values().stream()
                .map(DriveSignal::updatedAt)
                .filter(timestamp -> timestamp != null && !timestamp.isBlank())
                .max(String::compareTo)
                .orElse(UNSPECIFIED_OBSERVED_AT);
    }

    /**
     * The bias for a state, given the events that produced it.
     *
     * <p>The events are needed for one thing only: the reward they carry, which is the one thing
     * that may relieve the consolidation demand. Every other knob is read from the state, so the
     * events are not folded in twice — which is what makes the kernel's pattern
     * {@code bias(evolve(current, events), events)} correct rather than double-counting.</p>
     *
     * <p>A caller that cannot supply the events should call {@link #bias(RegulatoryState)}
     * instead, which assumes no reward — the conservative assumption, since a reward is the only
     * thing that can relieve anything here.</p>
     *
     * @param state  the regulatory state; not mutated
     * @param events the events reported alongside the state; may be empty
     * @return the bias the state is allowed to exert
     */
    public RegulationBias bias(RegulatoryState state, List<RegulatoryEvent> events) {
        Objects.requireNonNull(state, "state");

        double load = driveLevel(state, DriveSignal.Dimension.SUSTAINED_LOAD,
                DriveSignal.Effect.ALGORITHM_COST_PREFERENCE);
        double excess = Math.max(0.0, load - LOAD_COST_THRESHOLD);
        double costWeightMultiplier = RegulationBias.NEUTRAL_MULTIPLIER + excess * LOAD_COST_GAIN;

        double uncertainty = driveLevel(state, DriveSignal.Dimension.UNCERTAINTY,
                DriveSignal.Effect.EVIDENCE_REQUEST);
        double evidenceRequestThreshold = Math.max(0.0,
                RegulationBias.NEVER_REQUEST_EVIDENCE - uncertainty * UNCERTAINTY_EVIDENCE_GAIN);

        double consolidationDemand = effectiveConsolidationDemand(state, events);
        boolean admitNonCriticalTask = consolidationDemand < CONSOLIDATION_ADMISSION_THRESHOLD;

        double priorityDrive = priorityDrive(state);
        double taskPriorityMultiplier = RegulationBias.NEUTRAL_MULTIPLIER + priorityDrive * TASK_PRIORITY_GAIN;

        return new RegulationBias(costWeightMultiplier, evidenceRequestThreshold,
                admitNonCriticalTask, taskPriorityMultiplier);
    }

    /**
     * The bias for a state with no accompanying events.
     *
     * <p>No reward is assumed, so the consolidation demand is not relieved. This is the
     * conservative reading and the one a caller should use when it does not know what the
     * interaction was rewarded with.</p>
     */
    public RegulationBias bias(RegulatoryState state) {
        return bias(state, List.of());
    }

    /**
     * The consolidation demand after the reward has been applied and the floor enforced.
     *
     * <p>Public because it is the number a reader needs in order to check the bound. The bias
     * carries only the boolean consequence of it; the arithmetic behind the bound has to be
     * inspectable or the bound cannot be trusted.</p>
     *
     * <p>The result is never above the demand the state holds and never below
     * {@code min(demand, CONSOLIDATION_DEMAND_FLOOR)}. So a reward can relieve a demand that is
     * above the floor, and can relieve it no further than the floor — and it can do nothing at
     * all to a demand that is already below the floor.</p>
     */
    public double effectiveConsolidationDemand(RegulatoryState state, List<RegulatoryEvent> events) {
        Objects.requireNonNull(state, "state");
        double demand = driveLevel(state, DriveSignal.Dimension.CONSOLIDATION_DEMAND,
                DriveSignal.Effect.TASK_ADMISSION);
        if (demand <= 0.0) {
            return 0.0;
        }
        double reward = rewardSignal(events);
        double cancellation = Math.min(demand * MAX_REWARD_CONSOLIDATION_RELIEF,
                reward * MAX_REWARD_CONSOLIDATION_RELIEF);
        double relieved = demand - cancellation;
        return Math.max(relieved, Math.min(demand, CONSOLIDATION_DEMAND_FLOOR));
    }

    /**
     * The reward carried by a batch of events.
     *
     * <p>The rewards are summed and capped at one. A cap is needed because the relief is a share
     * of the demand, and an unbounded reward would make the share unbounded in the only place
     * where a reward is allowed to act at all.</p>
     */
    public static double rewardSignal(List<RegulatoryEvent> events) {
        if (events == null) {
            return 0.0;
        }
        double total = 0.0;
        for (RegulatoryEvent event : events) {
            if (event.kind() == RegulatoryEvent.Kind.REWARD) {
                total += event.magnitude();
            }
        }
        return Math.min(MAX_REWARD_SIGNAL, total);
    }

    /**
     * The effect a dimension carries.
     *
     * <p>The effect is the PERMISSION: it says which knob the dimension is allowed to move. A
     * drive whose effect is {@link DriveSignal.Effect#NONE} moves nothing, whatever its level.</p>
     *
     * <p>Three dimensions carry no effect at all, and the reason is the point of the whole
     * design. {@code ATTENTION} is the dimension that the previous design used to move a
     * retrieval floor with — see {@code NeuroModulatorHub.retrievalFloor()} — and W44 forbids a
     * drive from touching a retrieval floor. There is no attention knob in {@link RegulationBias},
     * so attention is recorded and nothing else. {@code VIGILANCE} and
     * {@code REWARD_PREDICTION_ERROR} are recorded for the same reason: there is no knob they may
     * move, and inventing one because a dimension exists would be exactly the "a name must not be
     * a number" failure the directive names.</p>
     */
    public static DriveSignal.Effect effectFor(DriveSignal.Dimension dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return switch (dimension) {
            case SUSTAINED_LOAD -> DriveSignal.Effect.ALGORITHM_COST_PREFERENCE;
            case UNCERTAINTY -> DriveSignal.Effect.EVIDENCE_REQUEST;
            case CONSOLIDATION_DEMAND -> DriveSignal.Effect.TASK_ADMISSION;
            case CURIOSITY, NOVELTY -> DriveSignal.Effect.TASK_PRIORITY;
            case ATTENTION, VIGILANCE, REWARD_PREDICTION_ERROR -> DriveSignal.Effect.NONE;
        };
    }

    /**
     * The level of one drive, but only if it carries the effect that permits it to act.
     *
     * <p>Both conditions have to hold. A drive whose dimension is the one a rule is about but
     * whose effect is {@code NONE} does not move that knob, and a drive whose effect would permit
     * the knob but whose dimension is not the one the rule is about does not move it either. A
     * drive therefore cannot move a knob by claiming an effect it was not given.</p>
     */
    private static double driveLevel(RegulatoryState state,
                                     DriveSignal.Dimension dimension,
                                     DriveSignal.Effect effect) {
        return state.drive(dimension)
                .filter(drive -> drive.effect() == effect)
                .map(DriveSignal::level)
                .orElse(0.0);
    }

    /**
     * The strongest priority-bearing drive the state holds.
     *
     * <p>The maximum, not the sum: two drives of 0.5 are not one drive of 1.0, and the
     * multiplier has to stay inside the range the contract allows.</p>
     */
    private static double priorityDrive(RegulatoryState state) {
        double strongest = 0.0;
        for (DriveSignal.Dimension dimension : List.of(
                DriveSignal.Dimension.CURIOSITY, DriveSignal.Dimension.NOVELTY)) {
            strongest = Math.max(strongest, driveLevel(state, dimension,
                    DriveSignal.Effect.TASK_PRIORITY));
        }
        return strongest;
    }
}
