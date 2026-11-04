package io.matrix.cognitive.microworld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * W44.4 — one learner's session in the micro-world.
 *
 * <p>The session is the learner-facing half of the scenario: it holds what has been observed,
 * what it still believes, what it has ruled out and why, what it predicted, and where the world
 * contradicted it. Everything it does is deterministic and everything it does is recorded.</p>
 *
 * <h2>Nothing is deleted</h2>
 *
 * <p>Observations are appended and never removed. Rejected hypotheses are kept with the
 * observation that contradicted them, so a reader can reconstruct why the survivors are the
 * survivors. A learner that silently drops what it has ruled out cannot explain itself, and
 * cannot notice a rule it once held coming back into play.</p>
 *
 * <h2>A prediction the world contradicts does NOT rewrite the model</h2>
 *
 * <p>This is the property the scenario exists to demonstrate. When an outcome contradicts a
 * prediction the session made, the session records a {@link PredictionError}, flags the
 * hypotheses that made the prediction for review, and <b>leaves the hypothesis set standing</b>.
 * It applies that observation to nothing.</p>
 *
 * <p>The reason is that the session cannot tell which of the two is wrong: the model may be
 * wrong, the instrument may be wrong, or a condition may be missing. Rewriting the model on a
 * datum whose cause is unresolved would destroy the evidence of the disagreement — and a learner
 * that quietly adopts every contradicting datum is a learner that cannot be wrong loudly. The
 * conflict stays open and is named, which is what the W44 kernel's
 * {@link io.matrix.cognitive.ErrorSignal} calls an unresolved disposition.</p>
 *
 * <h2>Ambiguity is visible</h2>
 *
 * <p>{@link #predict(WorldInput)} reports whether the surviving hypotheses agree and names the
 * ones that disagree. It never resolves an ambiguity by picking a side and calling it a fact.</p>
 */
public final class MicroWorldSession {

    /** The prefix of the identifier of a proposed experiment. */
    private static final String EXPERIMENT_ID_PREFIX = "exp-";

    /** The prefix of the identifier of a recorded prediction error. */
    private static final String ERROR_ID_PREFIX = "err-";

    /** The separator between the parts of a hypothesis identifier in a message. */
    private static final String MESSAGE_SEPARATOR = ", ";

    private final String episodeId;
    private final MicroWorldEnvironment environment;
    private final HypothesisSpace space;

    private final List<Observation> observations = new ArrayList<>();
    private final List<Observation> applied = new ArrayList<>();
    private final List<Observation> quarantined = new ArrayList<>();
    private final List<RuleHypothesis> surviving = new ArrayList<>();
    private final List<RejectedHypothesis> rejected = new ArrayList<>();
    private final Set<String> rejectedIds = new LinkedHashSet<>();
    private final List<HypothesisPrediction> predictionHistory = new ArrayList<>();
    private final Map<String, HypothesisPrediction> latestPredictionByAssignment =
            new LinkedHashMap<>();
    private final Map<String, List<String>> supportingHypothesesByAssignment = new LinkedHashMap<>();
    private final List<PredictionError> errors = new ArrayList<>();
    private final Set<String> reviewIds = new LinkedHashSet<>();
    private final List<Experiment> proposals = new ArrayList<>();
    private final List<Experiment> answered = new ArrayList<>();
    private final Set<String> answeredAssignments = new LinkedHashSet<>();

    private int proposalCounter;
    private int errorCounter;

    /**
     * A session over the unbiased hypothesis space of the micro-world.
     *
     * @param episodeId   the episode this session belongs to
     * @param environment the world to probe
     */
    public MicroWorldSession(String episodeId, MicroWorldEnvironment environment) {
        this(episodeId, environment, HypothesisSpace.unbiased());
    }

    /**
     * A session over an explicit hypothesis space.
     *
     * @param episodeId   the episode this session belongs to
     * @param environment the world to probe
     * @param space       the candidate rules the session starts from
     */
    public MicroWorldSession(String episodeId, MicroWorldEnvironment environment,
                             HypothesisSpace space) {
        if (episodeId == null || episodeId.isBlank()) {
            throw new IllegalArgumentException("a session needs an episode id");
        }
        this.episodeId = episodeId;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.space = Objects.requireNonNull(space, "space");
        if (!environment.variables().equals(space.variables().stream()
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            throw new IllegalArgumentException("the environment's variables " + environment.variables()
                    + " do not match the hypothesis space's " + space.variables());
        }
        this.surviving.addAll(space.candidates());
    }

    /**
     * Builds a session that already holds a set of observations.
     *
     * <p>An interface rather than a constructor call, because a session that is rebuilt from
     * durable state must be able to carry the observations it already has: the kernel restores
     * the micro-world's episodes from the recorded CASES, and the session it gets must already
     * know what it saw. A test can substitute a session over a different environment.</p>
     */
    public interface Factory {

        /**
         * A session for the given episode, already holding the given observations.
         *
         * @param episodeId    the episode
         * @param observations the observations the session already holds
         * @return the session
         */
        MicroWorldSession create(String episodeId, List<Observation> observations);
    }

    /**
     * The factory the kernel uses: a session over the unbiased hypothesis space, holding the
     * observations it is given.
     */
    public static final class DefaultFactory implements Factory {

        private final MicroWorldEnvironment environment;

        /**
         * @param environment the world the sessions probe
         */
        public DefaultFactory(MicroWorldEnvironment environment) {
            this.environment = Objects.requireNonNull(environment, "environment");
        }

        @Override
        public MicroWorldSession create(String episodeId, List<Observation> observations) {
            MicroWorldSession session = new MicroWorldSession(episodeId, environment);
            if (observations != null) {
                for (Observation observation : observations) {
                    session.observe(observation);
                }
            }
            return session;
        }
    }

    /**
     * Records an observation and updates the surviving hypothesis set.
     *
     * <p>An observation that contradicts a prediction the session already made does NOT change
     * the hypothesis set: it is recorded as a {@link PredictionError} and the hypotheses that
     * made the prediction are flagged for review. Every other observation narrows the set: the
     * candidates it contradicts are moved to the rejected list with the observation that
     * contradicted them, and they are never deleted.</p>
     *
     * <p>Recording the same observation twice is a no-op; recording a DIFFERENT observation under
     * an identifier that is already taken is refused, because the identifier is what the error
     * record points at.</p>
     *
     * @param observation the observation to record
     */
    public void observe(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        Observation existing = observationById(observation.id());
        if (existing != null) {
            if (existing.equals(observation)) {
                return;
            }
            throw new IllegalArgumentException("observation '" + observation.id()
                    + "' is already recorded with different content");
        }
        observations.add(observation);

        // W45.0. An observation that arrives already HELD — a contradiction whose cause has
        // not been established, or one a review rejected — is recorded and kept, and it does
        // NOT constrain the model. This is the rule that makes a restart safe: the durable
        // record says what the previous session decided, and the restored session honours it
        // instead of re-deciding from a prediction history it does not have.
        if (observation.disposition().isHeld()) {
            quarantined.add(observation);
            return;
        }

        HypothesisPrediction prediction = latestPredictionByAssignment.get(observation.assignmentKey());
        // W45.0. A contradiction is a disagreement with a BELIEF. The session holds an
        // observation only when the model was SETTLED — the prediction was unanimous — and the
        // world disagreed with it. A prediction the session explicitly marked as unsettled is
        // an admission of ignorance, not a belief: the record says the value "is explicitly
        // not a decision", so an observation that differs from it is ordinary learning.
        //
        // The W44 rule held the observation in both cases, and that rule only LOOKED harmless
        // because the restoration path applied the held observations anyway — the very defect
        // this wave fixes. With the defect fixed, the W44 rule is a learning deadlock: the
        // micro-world's learning observations are held as conflicts, the model never settles,
        // and every later prediction stays unsettled, so nothing ever becomes a belief again.
        // The W44 benchmark's own adversarial case (§4.4) predicts a SETTLED assignment — the
        // two survivors agree there — and it is still held under this rule.
        if (prediction != null && prediction.unanimous() && prediction.value() != observation.lamp()) {
            recordPredictionError(prediction, observation);
            // The observation is HELD: recorded, durable, and outside the model. It goes into
            // the same list a restored held observation goes into, so that "the observations
            // the model was NOT built from" means one thing whether the session is live or
            // rebuilt.
            quarantined.add(observation);
            return;
        }
        applied.add(observation);
        reclassify();
    }

    /**
     * Records the outcome of an experiment and checks it against what was predicted.
     *
     * <p>This is the method an experiment-driven scenario calls. It does what
     * {@link #observe(Observation)} does and additionally marks the proposal for this assignment
     * as answered, so a reader can tell which proposed experiment the outcome belongs to.</p>
     *
     * @param observation the outcome to record
     */
    public void applyOutcome(Observation observation) {
        observe(observation);
        String key = observation.assignmentKey();
        for (Experiment proposal : proposals) {
            if (proposal.assignmentKey().equals(key) && answeredAssignments.add(key)) {
                answered.add(proposal);
            }
        }
    }

    /** Every observation the session has recorded, in the order it recorded them. */
    public List<Observation> observations() {
        return List.copyOf(observations);
    }

    /**
     * The observations the hypothesis set was actually built from.
     *
     * <p>Every recorded observation except those that contradicted a prediction: those are
     * recorded, reported and deliberately NOT applied to the model.</p>
     */
    public List<Observation> appliedObservations() {
        return List.copyOf(applied);
    }

    /**
     * The observations the model was NOT built from.
     *
     * <p>W45.0. These are the observations the session recorded and deliberately kept out of
     * the active hypothesis set: a contradiction whose cause is unresolved, and an observation
     * a review rejected. They are durable, visible and inspectable, and they never constrain
     * the model. A reader can always ask the session what it knows and what it is holding.</p>
     *
     * @return the held observations, in the order they were recorded
     */
    public List<Observation> quarantinedObservations() {
        return List.copyOf(quarantined);
    }

    /**
     * Whether the model was actually built from this observation.
     *
     * <p>This is the session's own decision about one observation, and it is what the caller
     * persists as the observation's disposition. The answer is about the MODEL, not about the
     * record: an observation can be recorded, visible and still not be model data.</p>
     *
     * @param observation the observation
     * @return true when the observation constrains the active model
     */
    public boolean isApplied(Observation observation) {
        if (observation == null) {
            return false;
        }
        for (Observation candidate : applied) {
            if (candidate.id().equals(observation.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The disposition the session has decided for one observation.
     *
     * <p>This is the session's own decision, and it is the decision the caller persists. A
     * live contradiction is {@link ObservationDisposition#UNRESOLVED} — its cause has not been
     * established — and an observation the model was built from is
     * {@link ObservationDisposition#ELIGIBLE}. The rule is one rule: an observation that the
     * model was not built from is held.</p>
     *
     * <p><b>W45.1.</b> A RESTORED observation carries the disposition a REVIEW gave it, and
     * the session must not collapse that decision: an observation a review ACCEPTED is
     * {@link ObservationDisposition#ACCEPTED}, and one a review REJECTED is
     * {@link ObservationDisposition#REJECTED}. Collapsing them into "eligible" and "held"
     * would lose the very distinction the adjudication history exists to keep, and the
     * occurrence the session writes would then contradict the projection.</p>
     *
     * @param observation the observation
     * @return the disposition
     */
    public ObservationDisposition dispositionOf(Observation observation) {
        if (observation == null) {
            return ObservationDisposition.conservativeDefault();
        }
        ObservationDisposition own = observation.disposition();
        if (own == ObservationDisposition.ACCEPTED || own == ObservationDisposition.REJECTED) {
            // A reviewed decision is the decision. The session honours it.
            return own;
        }
        return isApplied(observation)
                ? ObservationDisposition.ELIGIBLE : ObservationDisposition.UNRESOLVED;
    }

    /** The hypotheses that still fit every applied observation, in identifier order. */
    public List<RuleHypothesis> survivingHypotheses() {
        return List.copyOf(surviving);
    }

    /** The hypotheses the session has ruled out, with the reason, in the order it ruled them out. */
    public List<RejectedHypothesis> rejectedHypotheses() {
        return List.copyOf(rejected);
    }

    /**
     * The surviving hypotheses that made a prediction the world then contradicted.
     *
     * <p>These are the hypotheses a reviewer has to look at: they are not wrong yet — the cause
     * of the contradiction is unresolved — but they are not innocent either.</p>
     */
    public List<RuleHypothesis> hypothesesNeedingReview() {
        List<RuleHypothesis> needing = new ArrayList<>();
        for (RuleHypothesis hypothesis : surviving) {
            if (reviewIds.contains(hypothesis.id())) {
                needing.add(hypothesis);
            }
        }
        return List.copyOf(needing);
    }

    /**
     * What the session believes about an assignment it has NOT observed.
     *
     * <p>The prediction is recorded, so that a later outcome can be compared with it. The
     * prediction carries whether the surviving hypotheses AGREE; when they do not, the
     * disagreeing ones are named and the value field is explicitly not a decision.</p>
     *
     * @param input the assignment to predict
     * @return the prediction
     */
    public HypothesisPrediction predict(WorldInput input) {
        Objects.requireNonNull(input, "input");
        if (surviving.isEmpty()) {
            throw new IllegalStateException("no hypothesis survives; there is nothing to predict from");
        }
        int trueCount = 0;
        int evaluable = 0;
        for (RuleHypothesis hypothesis : surviving) {
            Optional<Boolean> value = hypothesis.valueAt(input);
            if (value.isPresent()) {
                evaluable++;
                if (value.get()) {
                    trueCount++;
                }
            }
        }
        if (evaluable == 0) {
            throw new IllegalStateException("no surviving hypothesis has an opinion about " + input.key());
        }
        int falseCount = evaluable - trueCount;
        boolean value;
        if (trueCount > falseCount) {
            value = true;
        } else if (falseCount > trueCount) {
            value = false;
        } else {
            value = valueOfFirstSurvivor(input);
        }

        List<String> disagreeing = new ArrayList<>();
        List<String> supportingHypotheses = new ArrayList<>();
        for (RuleHypothesis hypothesis : surviving) {
            Optional<Boolean> held = hypothesis.valueAt(input);
            if (held.isEmpty()) {
                continue;
            }
            if (held.get() == value) {
                supportingHypotheses.add(hypothesis.id());
            } else {
                disagreeing.add(hypothesis.id());
            }
        }

        List<String> supportingCases = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation.lamp() == value) {
                supportingCases.add(observation.id());
            }
        }

        HypothesisPrediction prediction = new HypothesisPrediction(
                environment.outcomeVariable() + "@" + input.key(), value, disagreeing.isEmpty(),
                disagreeing, supportingCases);
        predictionHistory.add(prediction);
        latestPredictionByAssignment.put(input.key(), prediction);
        supportingHypothesesByAssignment.put(input.key(), supportingHypotheses);
        return prediction;
    }

    /**
     * The assignment that best separates the surviving hypotheses.
     *
     * <p>The proposals are the assignments the session has NOT observed, because an assignment
     * it has already seen separates nothing: every survivor agrees about it by construction. The
     * expected information gain is computed by {@link HypothesisSpace#informationGain} and the
     * rationale names the hypotheses on both sides.</p>
     *
     * <p><b>The tie-break is the assignment key.</b> The assignments are considered in canonical
     * key order and an equal gain keeps the earlier one, so the proposal of a given state is
     * always the same.</p>
     *
     * @return the proposal
     */
    public Experiment proposeExperiment() {
        List<WorldInput> pool = space.allAssignments();
        Set<String> observed = observedAssignments();
        List<WorldInput> unobserved = new ArrayList<>();
        for (WorldInput assignment : pool) {
            if (!observed.contains(assignment.key())) {
                unobserved.add(assignment);
            }
        }
        List<WorldInput> candidates = unobserved.isEmpty() ? pool : unobserved;

        WorldInput best = null;
        double bestGain = Double.NEGATIVE_INFINITY;
        for (WorldInput assignment : candidates) {
            double gain = HypothesisSpace.informationGain(assignment, surviving);
            if (gain > bestGain) {
                bestGain = gain;
                best = assignment;
            }
        }
        if (best == null) {
            throw new IllegalStateException("there is no assignment to propose");
        }
        Experiment experiment = new Experiment(
                EXPERIMENT_ID_PREFIX + episodeId + "-" + (++proposalCounter), best, bestGain,
                rationaleFor(best, bestGain));
        proposals.add(experiment);
        return experiment;
    }

    /** Every proposal the session has made, in order. */
    public List<Experiment> proposedExperiments() {
        return List.copyOf(proposals);
    }

    /** The proposals that an outcome has answered, in order. */
    public List<Experiment> answeredExperiments() {
        return List.copyOf(answered);
    }

    /** The prediction errors the session has recorded, in order. */
    public List<PredictionError> predictionErrors() {
        return List.copyOf(errors);
    }

    /** Every prediction the session has made, in order. A prediction is never rewritten. */
    public List<HypothesisPrediction> predictions() {
        return List.copyOf(predictionHistory);
    }

    /** The assignments the session has observed. */
    public Set<String> observedAssignments() {
        Set<String> keys = new LinkedHashSet<>();
        for (Observation observation : observations) {
            keys.add(observation.assignmentKey());
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * Whether this assignment has already been observed.
     *
     * <p>The kernel needs this to decide the precondition fact
     * {@code ASSIGNMENT_ALREADY_OBSERVED}: a method that needs to know whether the assignment is
     * new declares that fact, and a method that does not care does not. The answer is about the
     * ASSIGNMENT, not about the answer — an assignment that was observed and whose observation
     * was contradicted is still an assignment that was observed.</p>
     *
     * @param input the assignment
     * @return true when the session holds an observation of that assignment
     */
    public boolean hasObserved(WorldInput input) {
        if (input == null) {
            return false;
        }
        for (Observation observation : observations) {
            if (observation.assignmentKey().equals(input.key())) {
                return true;
            }
        }
        return false;
    }

    /** The assignments the session has predicted for and has NOT observed. */
    public Set<String> predictedButUnobservedAssignments() {
        Set<String> observed = observedAssignments();
        Set<String> keys = new LinkedHashSet<>();
        for (String key : latestPredictionByAssignment.keySet()) {
            if (!observed.contains(key)) {
                keys.add(key);
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * One line about the state of the session.
     *
     * @return the summary
     */
    public String statusLine() {
        return "episode " + episodeId + ": " + observations.size() + " observations, "
                + surviving.size() + " surviving hypotheses, " + errors.size() + " prediction errors, "
                + openErrorCount() + " open";
    }

    /** The episode this session belongs to. */
    public String episodeId() {
        return episodeId;
    }

    /** The number of prediction errors that are still open. */
    public long openErrorCount() {
        long open = 0;
        for (PredictionError error : errors) {
            boolean stillStanding = false;
            for (String id : supportingHypothesesByAssignment
                    .getOrDefault(error.assignmentKey(), List.of())) {
                if (survivingIds().contains(id)) {
                    stillStanding = true;
                    break;
                }
            }
            if (stillStanding) {
                open++;
            }
        }
        return open;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void reclassify() {
        List<RuleHypothesis> still = new ArrayList<>();
        for (RuleHypothesis candidate : space.candidates()) {
            int supporting = HypothesisSpace.supportingCount(candidate, applied);
            int contradicting = HypothesisSpace.contradictingCount(candidate, applied);
            if (contradicting == 0) {
                still.add(candidate.withCounts(supporting, 0));
            } else if (rejectedIds.add(candidate.id())) {
                Optional<Observation> first = HypothesisSpace.firstContradiction(candidate, applied);
                String reason = "contradicted by observation " + first.map(Observation::id).orElse("?")
                        + " at " + first.map(Observation::assignmentKey).orElse("?");
                rejected.add(new RejectedHypothesis(candidate.withCounts(supporting, contradicting),
                        reason));
            }
        }
        surviving.clear();
        surviving.addAll(still);
    }

    private void recordPredictionError(HypothesisPrediction prediction, Observation observation) {
        List<String> supporting = supportingHypothesesByAssignment
                .getOrDefault(observation.assignmentKey(), List.of());
        String responsible = supporting.isEmpty() ? survivingFirstId() : supporting.get(0);
        // W45.0: only a SETTLED prediction can be contradicted, so the record is always about
        // a belief the session actually held.
        String note = "the session predicted this value unanimously and the outcome contradicts "
                + "it; the cause is unresolved, so the hypotheses are left standing and flagged "
                + "for review";
        errors.add(new PredictionError(ERROR_ID_PREFIX + episodeId + "-" + (++errorCounter),
                observation.input(), prediction.value(), observation.lamp(), responsible, note));
        reviewIds.addAll(supporting);
    }

    private boolean valueOfFirstSurvivor(WorldInput input) {
        for (RuleHypothesis hypothesis : surviving) {
            Optional<Boolean> value = hypothesis.valueAt(input);
            if (value.isPresent()) {
                return value.get();
            }
        }
        throw new IllegalStateException("no surviving hypothesis has an opinion about " + input.key());
    }

    private String rationaleFor(WorldInput assignment, double gain) {
        List<String> trueIds = new ArrayList<>();
        List<String> falseIds = new ArrayList<>();
        for (RuleHypothesis hypothesis : surviving) {
            Optional<Boolean> value = hypothesis.valueAt(assignment);
            if (value.isEmpty()) {
                continue;
            }
            if (value.get()) {
                trueIds.add(hypothesis.id());
            } else {
                falseIds.add(hypothesis.id());
            }
        }
        String outcome = environment.outcomeVariable();
        return assignment.key() + " separates " + falseIds.size() + " of " + surviving.size()
                + " surviving hypotheses that predict " + outcome + "=false ("
                + String.join(MESSAGE_SEPARATOR, falseIds) + ") from " + trueIds.size()
                + " that predict " + outcome + "=true (" + String.join(MESSAGE_SEPARATOR, trueIds)
                + "); the expected surviving fraction after the outcome is "
                + format(1.0 - gain) + ", so the expected information gain is " + format(gain) + ".";
    }

    private String survivingFirstId() {
        return surviving.isEmpty() ? "unknown" : surviving.get(0).id();
    }

    private Set<String> survivingIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (RuleHypothesis hypothesis : surviving) {
            ids.add(hypothesis.id());
        }
        return ids;
    }

    private Observation observationById(String id) {
        for (Observation observation : observations) {
            if (observation.id().equals(id)) {
                return observation;
            }
        }
        return null;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
