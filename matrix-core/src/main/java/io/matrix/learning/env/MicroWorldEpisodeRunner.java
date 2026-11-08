package io.matrix.learning.env;

import io.matrix.cognitive.ExploratoryActionSampler;
import io.matrix.cognitive.microworld.Experiment;
import io.matrix.cognitive.microworld.HypothesisPrediction;
import io.matrix.cognitive.microworld.HypothesisSpace;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.RuleHypothesis;
import io.matrix.cognitive.microworld.WorldInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * W51 §7 — one episode of learning through the micro-world.
 *
 * <p><b>Why this class adds no logic of its own.</b> The micro-world already knows how to
 * probe, how to predict, how to narrow a hypothesis space and how to refuse to rewrite its
 * model on a datum whose cause is unresolved. This runner only decides WHEN to stop, WHAT to
 * record, and HOW to score what happened — and every one of those three is derived from
 * numbers the microworld already produces. A second implementation of the learning would be
 * a second thing to be wrong; this is a wrapper that cannot disagree with the thing it
 * wraps.</p>
 *
 * <h2>The loop</h2>
 * <pre>
 *   propose   → the learner asks the world which assignment would separate its hypotheses
 *   select    → the policy decides which assignment is probed
 *   gain      → the microworld's own expected information gain of that probe
 *   predict   → the learner states what it believes, BEFORE the outcome is known
 *   probe     → the world answers
 *   apply     → the answer constrains the model, or is held out of it
 * </pre>
 *
 * <p>The order of {@code predict} and {@code probe} is the load-bearing part of the whole
 * class. A prediction made after the outcome would be a description of what happened, not an
 * expectation of what would happen, and the difference between the two is the entire subject
 * of the microworld.</p>
 *
 * <h2>Termination</h2>
 * <p>An episode ends when the model settles on one hypothesis, when every assignment of the
 * input domain has been observed, when the hypothesis space is emptied, or when the step
 * bound is reached. The last two are NOT the same as the first: an episode that was cut
 * short has not learned, and the report says which of the four happened.</p>
 *
 * <h2>Determinism</h2>
 * <p>Two runs with the same seed and the same episode identity produce byte-identical
 * reports. There is no clock in a decision path, no {@code Random} in this class, and the
 * microworld itself has neither; the instant is a string the caller supplies, and its
 * default is a constant.</p>
 */
public final class MicroWorldEpisodeRunner {

    /**
     * The number of hypotheses the unbiased hypothesis space holds.
     *
     * <p>The microworld starts from ALL the Boolean functions of its two input variables, so
     * this is the size of the space the learner has to narrow — and therefore the denominator
     * of the utility. It is read from the space itself rather than written down, so the
     * number cannot drift away from the thing it measures.</p>
     */
    public static final int UNBIASED_HYPOTHESIS_COUNT =
            HypothesisSpace.unbiased().candidates().size();

    /**
     * The number of assignments the micro-world's input domain holds.
     *
     * <p>Every Boolean function over two variables is determined only when all of its
     * assignments have been observed, so this is also the number of probes a consistent
     * episode needs before the model can settle. It is read from the space, for the same
     * reason.</p>
     */
    public static final int EXHAUSTED_INPUT_DOMAIN_SIZE =
            HypothesisSpace.unbiased().allAssignments().size();

    /**
     * The number of surviving hypotheses at which the model has settled.
     *
     * <p>One survivor means every other rule in the space has been contradicted by an
     * observation. This is the microworld's success condition, and it is the one the
     * episode reports as its criterion.</p>
     */
    public static final int SETTLED_SURVIVOR_COUNT = 1;

    /**
     * The default bound on the number of probes in one episode.
     *
     * <p>The bound is the size of the input domain, because a consistent world cannot teach
     * more than its domain holds. It is a BOUND and not a plan: it is what stops an episode
     * that is asking a world which keeps contradicting itself.</p>
     */
    public static final int DEFAULT_STEP_LIMIT = EXHAUSTED_INPUT_DOMAIN_SIZE;

    /**
     * The instant an episode reports when the caller supplies none.
     *
     * <p>The microworld's own constant, reused rather than invented: an episode that
     * carries no meaningful instant must still be comparable with another episode that
     * carries no meaningful instant.</p>
     */
    public static final String DEFAULT_EPISODE_TIMESTAMP = LampEnvironment.DEFAULT_OBSERVED_AT;

    /**
     * The multiplier of the episode-identity hash.
     *
     * <p>31 is the radix of the classic polynomial string hash. {@link String#hashCode()} is
     * specified by the Java language, so the hash is stable across runs and across JVMs —
     * which is what "the same seed gives the same episode" needs.</p>
     */
    private static final int IDENTITY_HASH_MULTIPLIER = 31;

    private final MicroWorldEnvironment environment;
    private final String episodeId;
    private final long seed;
    private final int stepLimit;
    private final ProbePolicy probePolicy;
    private final Supplier<String> timestamps;

    /**
     * A runner over the given environment.
     *
     * @param environment the world the learner probes
     * @param episodeId   the identity of the episode
     * @param seed        the seed the probe order is derived from
     */
    public MicroWorldEpisodeRunner(MicroWorldEnvironment environment, String episodeId, long seed) {
        this(environment, episodeId, seed, DEFAULT_STEP_LIMIT, ProbePolicy.GREEDY,
                () -> DEFAULT_EPISODE_TIMESTAMP);
    }

    /**
     * A runner with an explicit bound on the number of probes.
     *
     * @param environment the world the learner probes
     * @param episodeId   the identity of the episode
     * @param seed        the seed the probe order is derived from
     * @param stepLimit   the maximum number of probes the episode may make
     */
    public MicroWorldEpisodeRunner(MicroWorldEnvironment environment, String episodeId, long seed,
                                   int stepLimit) {
        this(environment, episodeId, seed, stepLimit, ProbePolicy.GREEDY,
                () -> DEFAULT_EPISODE_TIMESTAMP);
    }

    /**
     * A runner with an explicit bound, an explicit probe policy and an explicit clock.
     *
     * @param environment  the world the learner probes
     * @param episodeId    the identity of the episode
     * @param seed         the seed the probe order is derived from
     * @param stepLimit    the maximum number of probes the episode may make
     * @param probePolicy  the policy that chooses the probe order
     * @param timestamps   supplies the instant the episode reports
     */
    public MicroWorldEpisodeRunner(MicroWorldEnvironment environment, String episodeId, long seed,
                                   int stepLimit, ProbePolicy probePolicy,
                                   Supplier<String> timestamps) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.episodeId = Objects.requireNonNull(episodeId, "episodeId");
        this.seed = seed;
        if (stepLimit < 1) {
            throw new IllegalArgumentException("an episode must be allowed at least one probe");
        }
        this.stepLimit = stepLimit;
        this.probePolicy = Objects.requireNonNull(probePolicy, "probePolicy");
        this.timestamps = Objects.requireNonNull(timestamps, "timestamps");
    }

    /**
     * Runs one episode.
     *
     * <p>The session is built here, over the unbiased hypothesis space, and discarded when
     * the episode ends: an episode owns its learner. A runner may be run more than once, and
     * two runs of the same runner are two episodes.</p>
     *
     * @return the report of the episode
     */
    public EpisodeReport run() {
        HypothesisSpace space = HypothesisSpace.unbiased();
        MicroWorldSession session = new MicroWorldSession(episodeId, environment, space);
        String timestamp = timestamps.get();
        long identityHash = identityHash();
        List<EpisodeStep> trajectory = new ArrayList<>();
        EpisodeReport.TerminationReason reason = null;

        // The step number is ONE-based, because the world's own observation identifiers are
        // one-based ("obs-1", "obs-2"). A trajectory whose steps and observations are
        // numbered differently is a trajectory that reads wrong at a glance, and a "step 0"
        // in a report reads like a bug rather than like a first step.
        int index = 1;
        while (reason == null) {
            if (session.survivingHypotheses().isEmpty()) {
                // The observations contradict every rule in the space. There is nothing left
                // to predict from, and a prediction from an empty space would be a fiction.
                reason = EpisodeReport.TerminationReason.NO_SURVIVING_HYPOTHESIS;
                break;
            }
            if (index > stepLimit) {
                reason = EpisodeReport.TerminationReason.STEP_LIMIT_REACHED;
                break;
            }

            Experiment proposal = session.proposeExperiment();
            // The sampler's cycle count is the number of probes ALREADY made — zero before
            // the first one — while the step number in the report is one-based. The two
            // numbering schemes answer different questions, and keeping them apart is what
            // makes a change to how a step is REPORTED unable to change what it LEARNED.
            WorldInput input = selectInput(proposal, session, space, identityHash, index - 1);
            // The gain is computed BEFORE the outcome, because it is a statement about what
            // the probe was expected to eliminate, not about what it did eliminate.
            double informationGain = HypothesisSpace.informationGain(input,
                    session.survivingHypotheses());
            HypothesisPrediction prediction = session.predict(input);
            Observation observation = environment.probe(input);
            session.applyOutcome(observation);
            boolean applied = session.isApplied(observation);

            trajectory.add(new EpisodeStep(index, input, prediction, observation, applied,
                    informationGain));
            index++;

            if (session.survivingHypotheses().size() == SETTLED_SURVIVOR_COUNT) {
                reason = EpisodeReport.TerminationReason.MODEL_SETTLED;
            } else if (session.observations().size() >= EXHAUSTED_INPUT_DOMAIN_SIZE) {
                reason = EpisodeReport.TerminationReason.DOMAIN_EXHAUSTED;
            }
        }

        List<RuleHypothesis> survivors = session.survivingHypotheses();
        int finalSurvivors = survivors.size();
        String survivorId = finalSurvivors == SETTLED_SURVIVOR_COUNT
                ? survivors.get(0).id()
                : "";
        return new EpisodeReport(episodeId, seed, probePolicy, reason, trajectory.size(),
                finalSurvivors, survivorId, utilityOf(finalSurvivors), trajectory, timestamp);
    }

    /**
     * The utility of an episode that ended with the given number of survivors.
     *
     * <p>The scale is the microworld's own: the fraction of the hypothesis space the
     * learner eliminated. It introduces no second scale, so an episode and the microworld's
     * published benchmark of the same world are measured the same way. An episode that
     * learned nothing scores zero, and a perfect one scores the fraction of the space it
     * removed.</p>
     *
     * @param finalSurvivors how many hypotheses survived the episode
     * @return the utility of the episode
     */
    public static double utilityOf(int finalSurvivors) {
        return 1.0 - (double) finalSurvivors / UNBIASED_HYPOTHESIS_COUNT;
    }

    /**
     * The hash of the episode's identity, from which the sampler's seed is derived.
     *
     * <p>The episode's identity is part of the hash so that two episodes with the same seed
     * but different names do not probe in the same order — otherwise the seed would be a
     * property of the runner rather than of the episode.</p>
     *
     * @return the identity hash
     */
    private long identityHash() {
        long hash = episodeId.hashCode();
        return hash * IDENTITY_HASH_MULTIPLIER + seed;
    }

    /**
     * The assignment this step probes.
     *
     * <p>The greedy policy probes what the learner asked for. The exploratory policy probes
     * a seeded choice among the assignments the learner has not observed yet — so the
     * episode still never asks the same question twice while there is anything left to
     * ask, and the order is the caller's rather than the learner's.</p>
     *
     * <p>The sampler's cycle count is the number of probes already made, so it is zero
     * before the first probe. It is what tells two steps of the same episode apart when
     * they are drawn from the same candidate set, and it is part of the seed — so a
     * different step is a different draw, deterministically.</p>
     */
    private WorldInput selectInput(Experiment proposal, MicroWorldSession session,
                                    HypothesisSpace space, long identityHash, int cycleCount) {
        if (probePolicy == ProbePolicy.GREEDY) {
            return proposal.input();
        }
        List<WorldInput> candidates = new ArrayList<>();
        for (WorldInput candidate : space.allAssignments()) {
            if (!session.hasObserved(candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            // The domain is exhausted; the learner's own proposal is all that is left, and
            // it may well be an assignment that was already observed.
            return proposal.input();
        }
        return ExploratoryActionSampler.sample(identityHash, candidates, cycleCount);
    }
}
