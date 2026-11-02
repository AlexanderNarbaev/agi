package io.matrix.cognitive.neuromodulation;

import io.matrix.cognitive.DriveSignal;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * W44.3 — deterministic dynamics for the drives.
 *
 * <p>A drive is a number that says how much pressure the system is under along one axis. This
 * class is what makes the number move, and its whole job is to do that in a way that can be
 * replayed: given the same state, the same events and the same step count, the result is the same
 * state. CONSTITUTION Article I asks for determinism, and the W44 directive asks that a modulator
 * be a named, bounded, inspectable thing rather than a mood. Both are satisfied by refusing to
 * read a clock and by refusing to invent a random number: the caller supplies the step and the
 * events, and this class does arithmetic.</p>
 *
 * <h2>The model</h2>
 *
 * <p>Each dimension carries a profile: a <b>baseline</b> it relaxes toward, a <b>decay rate per
 * tick</b>, a <b>minimum</b> and a <b>maximum</b>, and a <b>production rate per event class</b>.
 * One update is:</p>
 *
 * <pre>
 *   level' = clamp( baseline + (level - baseline) * (1 - decay)^steps + SUM(rate * magnitude) )
 * </pre>
 *
 * <p>The relaxation is what makes a drive forget: pressure that no event maintains is not
 * pressure, and a drive that only ever rose would be a counter, not a signal. The decay rates are
 * not uniform and the differences are the point: an attention signal is spent almost at once, a
 * consolidation demand is spent almost never.</p>
 *
 * <h2>Why the numbers here are a CONVENTION and not a measurement</h2>
 *
 * <p>Every rate in this class is a <b>CONVENTION</b> unless the comment says otherwise. That is
 * the honest label, and it is not a weakness of the class: these numbers have never been fitted to
 * anything, because nothing in this repository has yet produced the measurements that would fit
 * them. They are stated so that they can be argued with, replaced when they are measured, and
 * compared against each other. A number that was labelled MEASURED and was not would be the
 * dangerous one — see the provenance note in
 * {@code docs-v2/operations/TUNING-PARAMETERS.md §6c} for what a measured number has to carry with
 * it (the corpus, the labels, the regeneration command) before it may be called measured.</p>
 *
 * <p>The one number here that is <b>DERIVED</b> rather than conventional is nothing: every
 * baseline is zero and every bound is the interval {@link DriveSignal} itself enforces, because
 * those two are not choices this class gets to make. They are recorded per dimension anyway, so
 * that tightening one dimension later does not require changing the others.</p>
 */
public final class DriveDynamics {

    /**
     * The level every drive relaxes toward when no event maintains it.
     *
     * <p>CONVENTION — and the only defensible one. A drive is pressure; a system at rest is under
     * none. A non-zero baseline would mean the system is permanently under something, which is a
     * claim about the world that nothing here measures.</p>
     */
    public static final double BASELINE_RESTING = 0.0;

    /** CONVENTION — the lowest level a drive may take. */
    public static final double MINIMUM_LEVEL = 0.0;

    /**
     * CONVENTION — the highest level a drive may take.
     *
     * <p>It is the level {@link DriveSignal} validates against, so the dynamics cannot produce a
     * drive the record would refuse.</p>
     */
    public static final double MAXIMUM_LEVEL = 1.0;

    // ── decay rates, one per dimension ────────────────────────────────────────────
    //
    // CONVENTION throughout. The ordering is the meaningful part: transient signals are spent
    // quickly, accumulating signals are spent slowly.

    /** CONVENTION — attention is spent almost at once. */
    public static final double DECAY_ATTENTION = 0.50;

    /** CONVENTION — vigilance fades faster than it is raised. */
    public static final double DECAY_VIGILANCE = 0.40;

    /** CONVENTION — novelty decays quickly; a new input is news only once. */
    public static final double DECAY_NOVELTY = 0.30;

    /** CONVENTION — an outstanding prediction error is corrected rather than forgotten. */
    public static final double DECAY_ERROR = 0.25;

    /** CONVENTION — uncertainty clears as the model discriminates again. */
    public static final double DECAY_UNCERTAINTY = 0.20;

    /** CONVENTION — curiosity outlives the event that provoked it. */
    public static final double DECAY_CURIOSITY = 0.10;

    /** CONVENTION — operational load accumulates slowly and is relieved slowly. */
    public static final double DECAY_LOAD = 0.05;

    /** CONVENTION — a need to consolidate is the slowest of all to fade. */
    public static final double DECAY_CONSOLIDATION = 0.02;

    // ── production rates, one per event class ─────────────────────────────────────
    //
    // CONVENTION throughout. The magnitudes are relative to one another, not to any measurement.

    /** CONVENTION — the strongest single production of any event into any drive. */
    public static final double RATE_DOMINANT = 0.20;

    /** CONVENTION — a strong production. */
    public static final double RATE_HIGH = 0.15;

    /** CONVENTION — a medium production. */
    public static final double RATE_MEDIUM = 0.10;

    /** CONVENTION — the production of completed work into the need to consolidate. */
    public static final double RATE_CONSOLIDATION = 0.08;

    /** CONVENTION — a weak production. */
    public static final double RATE_LOW = 0.05;

    /** CONVENTION — the weakest production in the table. */
    public static final double RATE_MINIMAL = 0.02;

    /** CONVENTION — the strongest relief any event produces. */
    public static final double RATE_STRONG_RELIEF = -0.10;

    /** CONVENTION — a mild relief. */
    public static final double RATE_RELIEF = -0.05;

    /**
     * The parameters of one dimension.
     *
     * <p>Public because the profile is part of what the system is: a reader who wants to know
     * what the drives actually do must be able to read the numbers rather than infer them from
     * the behaviour of the update.</p>
     */
    public record DriveProfile(
            double baseline,
            double decayPerTick,
            double minimum,
            double maximum,
            Map<RegulatoryEvent.Kind, Double> productionByEvent) {

        public DriveProfile {
            if (baseline < 0.0 || baseline > 1.0 || Double.isNaN(baseline)) {
                throw new IllegalArgumentException("baseline must be within [0,1]");
            }
            if (decayPerTick < 0.0 || decayPerTick > 1.0 || Double.isNaN(decayPerTick)) {
                throw new IllegalArgumentException("decay rate must be within [0,1]");
            }
            if (minimum < 0.0 || maximum > 1.0 || minimum > maximum || Double.isNaN(minimum)
                    || Double.isNaN(maximum)) {
                throw new IllegalArgumentException("bounds must satisfy 0 <= minimum <= maximum <= 1");
            }
            productionByEvent = productionByEvent == null
                    ? Map.of()
                    : Map.copyOf(productionByEvent);
        }
    }

    private final Map<DriveSignal.Dimension, DriveProfile> profiles;

    private DriveDynamics(Map<DriveSignal.Dimension, DriveProfile> profiles) {
        this.profiles = Map.copyOf(profiles);
    }

    /**
     * The standard profile set: one profile for every dimension the contract declares.
     *
     * <p>Every dimension is present. A dimension with no profile would be a dimension whose
     * behaviour is defined by absence, and absence is not a specification.</p>
     */
    public static DriveDynamics standard() {
        Map<DriveSignal.Dimension, DriveProfile> byDimension = new LinkedHashMap<>();
        byDimension.put(DriveSignal.Dimension.REWARD_PREDICTION_ERROR, new DriveProfile(
                BASELINE_RESTING, DECAY_ERROR, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.TASK_FAILED, RATE_DOMINANT,
                        RegulatoryEvent.Kind.PREDICTION_ERROR, RATE_HIGH,
                        RegulatoryEvent.Kind.TASK_COMPLETED, RATE_STRONG_RELIEF,
                        RegulatoryEvent.Kind.REWARD, RATE_RELIEF)));
        // The uncertainty drive is fed by a wrong prediction, by an undecided model and by an
        // unfamiliar input. The undecided model is listed here and NOWHERE ELSE on purpose: it
        // is not novelty (the input may be perfectly familiar), it is not a failure (nothing was
        // attempted) and it is not a reward (there was no outcome), so it raises uncertainty and
        // changes nothing else.
        byDimension.put(DriveSignal.Dimension.UNCERTAINTY, new DriveProfile(
                BASELINE_RESTING, DECAY_UNCERTAINTY, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.PREDICTION_ERROR, RATE_MEDIUM,
                        RegulatoryEvent.Kind.MODEL_UNSETTLED, RATE_MEDIUM,
                        RegulatoryEvent.Kind.NOVEL_INPUT, RATE_LOW,
                        RegulatoryEvent.Kind.TASK_FAILED, RATE_LOW)));
        byDimension.put(DriveSignal.Dimension.NOVELTY, new DriveProfile(
                BASELINE_RESTING, DECAY_NOVELTY, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.NOVEL_INPUT, RATE_DOMINANT)));
        byDimension.put(DriveSignal.Dimension.SUSTAINED_LOAD, new DriveProfile(
                BASELINE_RESTING, DECAY_LOAD, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.LOAD_SAMPLE, RATE_MEDIUM,
                        RegulatoryEvent.Kind.TASK_FAILED, RATE_LOW,
                        RegulatoryEvent.Kind.TASK_COMPLETED, RATE_RELIEF)));
        byDimension.put(DriveSignal.Dimension.VIGILANCE, new DriveProfile(
                BASELINE_RESTING, DECAY_VIGILANCE, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.NOVEL_INPUT, RATE_LOW,
                        RegulatoryEvent.Kind.LOAD_SAMPLE, RATE_LOW,
                        RegulatoryEvent.Kind.TASK_FAILED, RATE_MEDIUM)));
        byDimension.put(DriveSignal.Dimension.CURIOSITY, new DriveProfile(
                BASELINE_RESTING, DECAY_CURIOSITY, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.NOVEL_INPUT, RATE_MEDIUM,
                        RegulatoryEvent.Kind.PREDICTION_ERROR, RATE_MEDIUM)));
        byDimension.put(DriveSignal.Dimension.CONSOLIDATION_DEMAND, new DriveProfile(
                BASELINE_RESTING, DECAY_CONSOLIDATION, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.TASK_COMPLETED, RATE_CONSOLIDATION,
                        RegulatoryEvent.Kind.PREDICTION_ERROR, RATE_LOW)));
        byDimension.put(DriveSignal.Dimension.ATTENTION, new DriveProfile(
                BASELINE_RESTING, DECAY_ATTENTION, MINIMUM_LEVEL, MAXIMUM_LEVEL,
                Map.of(
                        RegulatoryEvent.Kind.NOVEL_INPUT, RATE_LOW,
                        RegulatoryEvent.Kind.LOAD_SAMPLE, RATE_MINIMAL)));
        return new DriveDynamics(byDimension);
    }

    /** The profiles, by dimension. */
    public Map<DriveSignal.Dimension, DriveProfile> profiles() {
        return profiles;
    }

    /**
     * Advance the state by an explicit number of steps.
     *
     * <p>The step count is the caller's, because nothing here reads a clock. A caller that wants
     * the state to reflect one minute of work says so; a caller that wants it to reflect one
     * event says so. The two produce different states, which is the point: an interval of time and
     * an interval of work are not the same thing and the system should not pretend otherwise.</p>
     *
     * <p><b>The order of the two operations matters, and it is stated here because a caller
     * integrating this needs to know it.</b> The state that is ALREADY there is aged by the step
     * count first, and the events are then added on top of the aged state. So the step count
     * decays the pressure the system was carrying; it does not decay the pressure the new events
     * create. A caller that wants a different order — events first, then decay — gets a different
     * model, and the model is named here so that the difference is a decision rather than an
     * accident.</p>
     *
     * @param current    the state before the update; not mutated
     * @param events     the events that occurred; an empty list is a legal update
     * @param steps      how many decay steps to apply; must not be negative
     * @param observedAt the caller's timestamp for this update, carried onto the produced drives
     * @return the state after the update
     */
    public RegulatoryState advance(RegulatoryState current,
                                   List<RegulatoryEvent> events,
                                   int steps,
                                   String observedAt) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(events, "events");
        if (steps < 0) {
            throw new IllegalArgumentException("steps must not be negative");
        }
        if (observedAt == null || observedAt.isBlank()) {
            throw new IllegalArgumentException("observedAt must not be blank");
        }

        Map<DriveSignal.Dimension, Double> injected = new EnumMap<>(DriveSignal.Dimension.class);
        Map<DriveSignal.Dimension, String> injectedBy = new EnumMap<>(DriveSignal.Dimension.class);
        for (RegulatoryEvent event : events) {
            for (Map.Entry<DriveSignal.Dimension, Double> entry : productionFor(event).entrySet()) {
                injected.merge(entry.getKey(), entry.getValue(), Double::sum);
                injectedBy.put(entry.getKey(), event.source());
            }
        }

        Map<DriveSignal.Dimension, DriveSignal> produced = new EnumMap<>(DriveSignal.Dimension.class);
        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            DriveProfile profile = profiles.get(dimension);
            if (profile == null) {
                // A dimension with no profile has no defined behaviour, so it is left alone
                // rather than given one. standard() defines a profile for every dimension, so
                // this is a guard against a caller building a partial dynamics.
                continue;
            }
            DriveSignal previous = current.drive(dimension).orElse(null);
            Double delta = injected.get(dimension);
            if (previous == null && delta == null) {
                // Nothing is known about this dimension and nothing happened to it. A drive that
                // says "0" would be indistinguishable from a drive that was measured to be zero.
                continue;
            }

            double level = current.level(dimension);
            for (int step = 0; step < steps; step++) {
                level = profile.baseline() + (level - profile.baseline()) * (1.0 - profile.decayPerTick());
            }
            if (delta != null) {
                level += delta;
            }
            level = Math.max(profile.minimum(), Math.min(profile.maximum(), level));

            String source = injectedBy.getOrDefault(dimension,
                    previous == null ? "drive-dynamics" : previous.source());
            produced.put(dimension, new DriveSignal(dimension, level, source,
                    RegulationController.effectFor(dimension), observedAt));
        }
        return new RegulatoryState(produced);
    }

    /**
     * Advance the state by one step.
     *
     * <p>One step is the smallest useful interval: the events happened, and the pressure they
     * created has not had a chance to fade.</p>
     */
    public RegulatoryState advance(RegulatoryState current,
                                   List<RegulatoryEvent> events,
                                   String observedAt) {
        return advance(current, events, 1, observedAt);
    }

    /** What one event produces into each dimension, before the magnitude is applied. */
    private Map<DriveSignal.Dimension, Double> productionFor(RegulatoryEvent event) {
        Map<DriveSignal.Dimension, Double> production = new EnumMap<>(DriveSignal.Dimension.class);
        for (Map.Entry<DriveSignal.Dimension, DriveProfile> entry : profiles.entrySet()) {
            Double rate = entry.getValue().productionByEvent().get(event.kind());
            if (rate != null && rate != 0.0) {
                production.put(entry.getKey(), rate * event.magnitude());
            }
        }
        return production;
    }
}
