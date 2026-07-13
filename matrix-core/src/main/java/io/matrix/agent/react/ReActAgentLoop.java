package io.matrix.agent.react;

import io.matrix.agent.*;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.Task;
import io.matrix.mediator.scheduler.TaskScheduler;
import io.matrix.memory.HierarchicalMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ReAct Agent Loop — interleaved Reasoning + Acting cycle with Reflexion.
 *
 * <p>Extends the classic Observe → Think → Act cycle with explicit reasoning
 * and self-reflection steps:
 *
 * <pre>
 *   Observe → Reason → Act → Observe Result → Reflect
 *     ↑                                           |
 *     └───────────── (next tick) ─────────────────┘
 * </pre>
 *
 * <p>Each tick produces a {@link ReasoningTrace} that is stored in episodic
 * memory ({@link ReflexionMemory}). The {@link SelfEvaluator} generates
 * verbal reflections on failures, which inform future decisions.
 *
 * <p>Key differences from classic {@link AgentLoop}:
 * <ul>
 *   <li>Explicit reasoning step produces textual thought before action selection</li>
 *   <li>Post-action reflection evaluates outcome and stores lessons learned</li>
 *   <li>Episodic memory provides context for improved decision-making</li>
 *   <li>Failure pattern detection enables strategic course correction</li>
 * </ul>
 *
 * <p>Ref: ReAct (Yao et al., 2022) + Reflexion (Shinn et al., 2023)
 */
public final class ReActAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentLoop.class);

    /** Default max consecutive identical actions before declaring convergence. */
    public static final int DEFAULT_CONVERGENCE_THRESHOLD = 5;

    /** Default number of past episodes to consider for reasoning context. */
    public static final int DEFAULT_CONTEXT_WINDOW = 10;

    // ── Dependencies ──

    private final AgentBrainService brain;
    private final AgentLoop.Sensor sensor;
    private final AgentLoop.Effector effector;
    private final DriverState[] drivers;
    private final TaskScheduler scheduler;
    private final ReflexionMemory reflexionMemory;
    private final SelfEvaluator evaluator;
    private final int convergenceThreshold;
    private final int contextWindow;

    // ── Mutable state (thread-safe) ──

    private final List<AgentState> history = Collections.synchronizedList(new ArrayList<>());
    private final List<ReasoningTrace> traceHistory = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong tickCounter = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean converged = new AtomicBoolean(false);
    private volatile AgentLoop.ConvergenceReason convergenceReason;
    private volatile AgentState currentState;
    private volatile ReasoningTrace currentTrace;

    /**
     * Creates a ReActAgentLoop with default settings.
     */
    public ReActAgentLoop(AgentBrainService brain, AgentLoop.Sensor sensor, AgentLoop.Effector effector,
                           DriverState[] drivers, TaskScheduler scheduler,
                           ReflexionMemory reflexionMemory) {
        this(brain, sensor, effector, drivers, scheduler, reflexionMemory,
                DEFAULT_CONVERGENCE_THRESHOLD, DEFAULT_CONTEXT_WINDOW);
    }

    /**
     * Creates a ReActAgentLoop with explicit settings.
     */
    public ReActAgentLoop(AgentBrainService brain, AgentLoop.Sensor sensor, AgentLoop.Effector effector,
                           DriverState[] drivers, TaskScheduler scheduler,
                           ReflexionMemory reflexionMemory,
                           int convergenceThreshold, int contextWindow) {
        this.brain = Objects.requireNonNull(brain, "brain must not be null");
        this.sensor = Objects.requireNonNull(sensor, "sensor must not be null");
        this.effector = Objects.requireNonNull(effector, "effector must not be null");
        this.drivers = Objects.requireNonNull(drivers, "drivers must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.reflexionMemory = Objects.requireNonNull(reflexionMemory, "reflexionMemory must not be null");
        this.evaluator = new SelfEvaluator(reflexionMemory);
        this.convergenceThreshold = Math.max(1, convergenceThreshold);
        this.contextWindow = Math.max(1, contextWindow);
    }

    // ── Synchronous execution ──

    /**
     * Runs the ReAct agent loop synchronously for up to {@code maxIterations} ticks.
     *
     * @return immutable snapshot of the full state history
     */
    public List<AgentState> run(int maxIterations) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("ReActAgentLoop is already running");
        }

        try {
            log.info("ReAct agent loop starting: maxIterations={}, convergenceThreshold={}",
                    maxIterations, convergenceThreshold);

            for (int i = 0; i < maxIterations; i++) {
                if (converged.get()) break;

                AgentState state = tick();
                history.add(state);
                currentState = state;

                if (checkConvergence(state)) break;
            }

            if (!converged.get()) {
                converged.set(true);
                convergenceReason = AgentLoop.ConvergenceReason.MAX_ITERATIONS;
            }

            log.info("ReAct agent loop finished: ticks={}, reason={}, historySize={}, tracesStored={}",
                    tickCounter.get(), convergenceReason, history.size(), reflexionMemory.totalEpisodesStored());

            return AgentState.immutableHistory(history);
        } finally {
            running.set(false);
        }
    }

    // ── Asynchronous execution ──

    public CompletableFuture<List<AgentState>> runAsync(int maxIterations) {
        return runAsync(maxIterations, ForkJoinPool.commonPool());
    }

    public CompletableFuture<List<AgentState>> runAsync(int maxIterations, Executor executor) {
        return CompletableFuture.supplyAsync(() -> run(maxIterations), executor);
    }

    // ── Manual control ──

    public void stop() {
        converged.set(true);
        convergenceReason = AgentLoop.ConvergenceReason.MANUAL_STOP;
        log.info("ReAct agent loop manually stopped at tick {}", tickCounter.get());
    }

    // ── Single tick (ReAct cycle) ──

    /**
     * Executes one full ReAct cycle: Observe → Reason → Act → Observe Result → Reflect.
     *
     * @return the resulting agent state
     */
    public AgentState tick() {
        long tick = tickCounter.incrementAndGet();

        // ── Step 1: Observe ──
        long observation = sensor.read();
        double[] driverSnapshot = snapshotDrivers();

        // ── Step 2: Reason ──
        String thought = reason(observation, driverSnapshot);
        int actionCode = brain.brain().decide(observation);
        boolean[] thoughtVector = AgentLoop.actionCodeToThought(actionCode);

        // ── Step 3: Act ──
        AgentAction action = selectAction(thoughtVector, driverSnapshot);
        AgentAction.ActionResult result = effector.execute(action);
        AgentAction executedAction = action.withResult(result);

        // ── Step 4: Observe Result + Reflect ──
        AgentState state = new AgentState(observation, thoughtVector, executedAction, driverSnapshot, tick);
        SelfEvaluator.EvaluationResult evaluation = evaluator.evaluate(state, result);

        // Build and store reasoning trace
        ReasoningTrace trace = evaluator.buildAndStoreTrace(
                tick, observation, thought, executedAction, result, evaluation);
        traceHistory.add(trace);
        currentTrace = trace;

        // Update driver states
        updateDrivers(result);

        // Log strategic reflection if agent is stuck
        String strategicReflection = evaluator.generateStrategicReflection();
        if (strategicReflection != null) {
            log.warn("Strategic reflection at tick {}: {}", tick, strategicReflection);
        }

        log.debug("ReAct tick {}: thought='{}' action={} success={} reflection='{}'",
                tick, truncate(thought, 60), action.type(),
                evaluation.success(), truncate(evaluation.reflection(), 60));

        return state;
    }

    // ── Reasoning ──

    /**
     * Generates a textual reasoning thought based on current observation,
     * driver states, and past reflections from episodic memory.
     */
    private String reason(long observation, double[] driverLevels) {
        StringBuilder sb = new StringBuilder();

        // Analyze driver states
        String driverSummary = summarizeDrivers(driverLevels);
        sb.append("Drivers: ").append(driverSummary);

        // Check for active task
        Task activeTask = scheduler.allTasks().stream()
                .filter(Task::isActive)
                .findFirst()
                .orElse(null);
        if (activeTask != null) {
            sb.append("; Task: ").append(activeTask.driver()).append(" active");
        }

        // Consult episodic memory for relevant past reflections
        List<ReasoningTrace> recentEpisodes = reflexionMemory.recent(contextWindow);
        if (!recentEpisodes.isEmpty()) {
            long recentFailures = recentEpisodes.stream()
                    .filter(t -> !t.actionSuccess())
                    .count();
            if (recentFailures > 0) {
                sb.append("; Recent failures: ").append(recentFailures)
                        .append("/").append(recentEpisodes.size());
            }

            // Check for failure patterns
            Map<String, Long> patterns = reflexionMemory.detectFailurePatterns();
            if (!patterns.isEmpty()) {
                String worstAction = patterns.keySet().iterator().next();
                sb.append("; Avoid: ").append(worstAction);
            }
        }

        return sb.toString();
    }

    private String summarizeDrivers(double[] driverLevels) {
        StringBuilder sb = new StringBuilder();
        DriverType[] types = DriverType.values();
        for (int i = 0; i < Math.min(driverLevels.length, types.length); i++) {
            if (driverLevels[i] > 0.6) {
                if (sb.length() > 0) sb.append(",");
                sb.append(types[i]).append("=").append(String.format("%.2f", driverLevels[i]));
            }
        }
        return sb.length() > 0 ? sb.toString() : "all nominal";
    }

    // ── Action selection (delegates to AgentLoop logic) ──

    AgentAction selectAction(boolean[] thought, double[] driverLevels) {
        // Safety check
        if (driverLevels.length > DriverType.SAFETY.ordinal()
                && driverLevels[DriverType.SAFETY.ordinal()] > 0.7) {
            return new AgentAction(AgentAction.ActionType.WAIT,
                    Map.of("reason", "safety_driver_high"));
        }

        // Check for active task
        Task activeTask = scheduler.allTasks().stream()
                .filter(Task::isActive)
                .findFirst()
                .orElse(null);

        if (activeTask != null) {
            return actionForTask(activeTask);
        }

        // Brain-driven action selection from thought vector
        return actionFromThought(thought);
    }

    private AgentAction actionForTask(Task task) {
        AgentAction.ActionType type = switch (task.driver()) {
            case ENERGY -> AgentAction.ActionType.EAT;
            case SAFETY -> AgentAction.ActionType.WAIT;
            case CURIOSITY -> AgentAction.ActionType.EXPLORE;
            case ENTROPY -> AgentAction.ActionType.THINK;
            case SOCIAL -> AgentAction.ActionType.SPEAK;
            case SELFACTUALIZATION -> AgentAction.ActionType.CRAFT;
            case ATTENTION -> AgentAction.ActionType.OBSERVE;
            case UBUNTU -> AgentAction.ActionType.SPEAK;
        };
        return new AgentAction(type, Map.of("taskId", task.id().toString()));
    }

    private AgentAction actionFromThought(boolean[] thought) {
        if (thought.length == 0) {
            return new AgentAction(AgentAction.ActionType.WAIT);
        }

        int code = 0;
        for (int i = 0; i < Math.min(4, thought.length); i++) {
            if (thought[i]) code |= (1 << i);
        }

        AgentAction.ActionType[] types = {
                AgentAction.ActionType.WAIT,
                AgentAction.ActionType.MOVE,
                AgentAction.ActionType.MINE,
                AgentAction.ActionType.CRAFT,
                AgentAction.ActionType.EAT,
                AgentAction.ActionType.TOOL_UP,
                AgentAction.ActionType.EXPLORE,
                AgentAction.ActionType.OBSERVE,
                AgentAction.ActionType.SPEAK,
                AgentAction.ActionType.THINK,
                AgentAction.ActionType.MOVE,
                AgentAction.ActionType.EXPLORE,
                AgentAction.ActionType.CRAFT,
                AgentAction.ActionType.TOOL_UP,
                AgentAction.ActionType.OBSERVE,
                AgentAction.ActionType.THINK,
        };

        return new AgentAction(types[code]);
    }

    // ── Convergence detection ──

    private boolean checkConvergence(AgentState state) {
        boolean anyTaskCompleted = scheduler.allTasks().stream()
                .anyMatch(t -> t.status() == Task.Status.COMPLETED);
        if (anyTaskCompleted) {
            converged.set(true);
            convergenceReason = AgentLoop.ConvergenceReason.TASK_COMPLETED;
            return true;
        }

        boolean anyTaskFailed = scheduler.allTasks().stream()
                .anyMatch(t -> t.status() == Task.Status.FAILED);
        if (anyTaskFailed) {
            converged.set(true);
            convergenceReason = AgentLoop.ConvergenceReason.TASK_FAILED;
            return true;
        }

        if (history.size() >= convergenceThreshold - 1) {
            List<AgentState> recent = new ArrayList<>(history.subList(
                    Math.max(0, history.size() - convergenceThreshold + 1), history.size()));
            boolean allSame = true;
            AgentAction.ActionType firstType = state.actionType();
            for (AgentState s : recent) {
                if (s.actionType() != firstType) {
                    allSame = false;
                    break;
                }
            }
            if (allSame && state.actionType() == firstType) {
                converged.set(true);
                convergenceReason = AgentLoop.ConvergenceReason.REPEATING_ACTION;
                return true;
            }
        }

        return false;
    }

    // ── Driver management ──

    private double[] snapshotDrivers() {
        double[] levels = new double[drivers.length];
        for (int i = 0; i < drivers.length; i++) {
            levels[i] = drivers[i].level();
        }
        return levels;
    }

    private void updateDrivers(AgentAction.ActionResult result) {
        if (result == null) return;

        for (DriverState driver : drivers) {
            if (result.success()) {
                driver.nudge(-0.01);
            } else {
                driver.nudge(0.02);
            }
        }

        for (DriverState driver : drivers) {
            if (driver.type() == DriverType.CURIOSITY) {
                driver.nudge(0.005);
            }
        }
    }

    // ── Accessors ──

    public List<AgentState> history() {
        return AgentState.immutableHistory(history);
    }

    public List<ReasoningTrace> traceHistory() {
        return List.copyOf(traceHistory);
    }

    public AgentState currentState() { return currentState; }

    public ReasoningTrace currentTrace() { return currentTrace; }

    public long tickCount() { return tickCounter.get(); }

    public boolean isRunning() { return running.get(); }

    public boolean isConverged() { return converged.get(); }

    public AgentLoop.ConvergenceReason convergenceReason() { return convergenceReason; }

    public int convergenceThreshold() { return convergenceThreshold; }

    public ReflexionMemory reflexionMemory() { return reflexionMemory; }

    public SelfEvaluator evaluator() { return evaluator; }

    // ── Helpers ──

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
