package io.matrix.learning;

import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Continuous Learning Pipeline — wires agent feedback into neural retraining.
 *
 * <p>After each epoch (batch of N ticks), collected feedback triggers
 * {@link AgentBrainService#onlineTrain(int)} to improve the agent's neuron weights.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #tick()} — executes one agent tick, records sensor→success feedback</li>
 *   <li>At batch boundaries, computes pre-train fitness from success rate of recent feedback</li>
 *   <li>Calls {@code brain.onlineTrain(onlineTrainIterations)} to hill-climb the action layer</li>
 *   <li>Stores the pre-train fitness into {@link #fitnessHistory()} for trend tracking</li>
 * </ol>
 *
 * <p>Thread-safety: tick/retrain counters use {@link AtomicInteger};
 * fitness history is guarded by {@code synchronized} blocks.
 * The {@link #run(int)} method is synchronous and single-threaded per invocation.
 */
public final class ContinuousLearningLoop {

    private final AgentLoop agentLoop;
    private final AgentBrainService brain;
    private final int feedbackBatchSize;
    private final int onlineTrainIterations;

    private final AtomicInteger tickCount = new AtomicInteger(0);
    private final AtomicInteger retrainCount = new AtomicInteger(0);
    private final List<Double> fitnessHistory = Collections.synchronizedList(new ArrayList<>());

    /** Accumulated success rates for the current batch (cleared after each retrain). */
    private final List<Double> batchSuccessRates = new ArrayList<>();

    /**
     * Creates a continuous learning loop.
     *
     * @param loop                 the agent loop to drive
     * @param brain                brain service for feedback recording and online training
     * @param feedbackBatchSize    number of ticks per epoch before retraining triggers
     * @param onlineTrainIterations number of hill-climbing iterations per retrain call
     */
    public ContinuousLearningLoop(AgentLoop loop, AgentBrainService brain,
                                   int feedbackBatchSize, int onlineTrainIterations) {
        this.agentLoop = Objects.requireNonNull(loop, "loop must not be null");
        this.brain = Objects.requireNonNull(brain, "brain must not be null");
        this.feedbackBatchSize = Math.max(1, feedbackBatchSize);
        this.onlineTrainIterations = Math.max(1, onlineTrainIterations);
    }

    /**
     * Runs one tick with feedback collection.
     *
     * <p>Feedback is extracted from the resulting {@link AgentState}:
     * the observation becomes the sensor bits, and
     * {@link io.matrix.agent.AgentAction.ActionResult#success()} determines
     * whether the chosen action was successful.
     *
     * <p>When {@code tickCount} reaches {@code feedbackBatchSize},
     * the accumulated success rate is computed and recorded as the pre-train fitness,
     * then {@link AgentBrainService#onlineTrain(int)} is called.
     *
     * @return the agent state produced by this tick, never {@code null}
     */
    public AgentState tick() {
        AgentState state = agentLoop.tick();

        // ── Extract feedback ──
        long sensorBits = state.observation();
        boolean success = isActionSuccessful(state);
        brain.recordFeedback(sensorBits, success);
        batchSuccessRates.add(success ? 1.0 : 0.0);

        int currentTick = tickCount.incrementAndGet();

        // ── Retrain at batch boundaries ──
        if (currentTick % feedbackBatchSize == 0) {
            double preTrainFitness = batchSuccessRates.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            brain.onlineTrain(onlineTrainIterations);
            retrainCount.incrementAndGet();

            fitnessHistory.add(preTrainFitness);
            batchSuccessRates.clear();
        }

        return state;
    }

    /**
     * Runs the full continuous learning loop for up to {@code maxTicks} ticks.
     *
     * <p>Retraining happens automatically at batch boundaries.
     * Returns all agent states produced during the run.
     *
     * @param maxTicks maximum number of ticks to execute
     * @return immutable list of agent states, chronological order
     */
    public List<AgentState> run(int maxTicks) {
        if (maxTicks <= 0) {
            return List.of();
        }

        List<AgentState> states = new ArrayList<>(maxTicks);
        for (int i = 0; i < maxTicks; i++) {
            states.add(tick());
        }
        return Collections.unmodifiableList(states);
    }

    /**
     * Returns the number of retraining epochs completed so far.
     */
    public int retrainCount() {
        return retrainCount.get();
    }

    /**
     * Returns an immutable copy of the fitness history.
     *
     * <p>Each entry is the pre-train success rate (0.0–1.0) recorded just before
     * calling {@code onlineTrain}. A rising trend indicates the agent is improving.
     *
     * @return immutable list of fitness values
     */
    public List<Double> fitnessHistory() {
        synchronized (fitnessHistory) {
            return List.copyOf(fitnessHistory);
        }
    }

    /** Returns the current tick count. */
    public int tickCount() {
        return tickCount.get();
    }

    /** Returns the configured feedback batch size. */
    public int feedbackBatchSize() {
        return feedbackBatchSize;
    }

    /** Returns the configured online training iterations. */
    public int onlineTrainIterations() {
        return onlineTrainIterations;
    }

    // ─── Helpers ───

    /**
     * Determines if the action in the given state was successful.
     *
     * <p>A state with no action or no result is treated as unsuccessful.
     */
    private static boolean isActionSuccessful(AgentState state) {
        if (state.action() == null) {
            return false;
        }
        if (!state.action().hasResult()) {
            return false;
        }
        return state.action().result().success();
    }
}
