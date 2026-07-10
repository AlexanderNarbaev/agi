package io.matrix.agent;

import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.Task;
import io.matrix.mediator.scheduler.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MPDT Agent Loop — the core Observe → Think → Act cycle.
 *
 * <p>Each tick:
 * <ol>
 *   <li><b>Observe</b> — read sensor bits from environment, snapshot driver states</li>
 *   <li><b>Think</b> — process observation through HierarchicalBrain → boolean thought vector</li>
 *   <li><b>Act</b> — select action based on thought + active task + driver priorities</li>
 * </ol>
 *
 * <p>Convergence detection:
 * <ul>
 *   <li>Repeating same action type for N consecutive ticks → converged (stuck)</li>
 *   <li>Active task reaches COMPLETED status → converged (goal achieved)</li>
 *   <li>Max iterations reached → converged (budget exhausted)</li>
 * </ul>
 *
 * <p>Thread-safety: all mutable state is guarded by synchronized blocks or
 * atomic variables. The loop can be executed asynchronously via {@link #runAsync}.
 *
 * <p>Ref: L1_MPDT_neuron.md §4 (Agent Loop)
 */
public final class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    /** Default max consecutive identical actions before declaring convergence. */
    public static final int DEFAULT_CONVERGENCE_THRESHOLD = 5;

    /** Sensor function: reads environment → sensor bits. */
    @FunctionalInterface
    public interface Sensor {
        long read();
    }

    /** Effector function: executes action in environment → result. */
    @FunctionalInterface
    public interface Effector {
        AgentAction.ActionResult execute(AgentAction action);
    }

    // ── Dependencies ──

    private final AgentBrainService brain;
    private final Sensor sensor;
    private final Effector effector;
    private final DriverState[] drivers;
    private final TaskScheduler scheduler;
    private final int convergenceThreshold;

    // ── Mutable state (thread-safe) ──

    private final List<AgentState> history = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong tickCounter = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean converged = new AtomicBoolean(false);
    private volatile ConvergenceReason convergenceReason;
    private volatile AgentState currentState;

    /**
     * Reason for loop convergence.
     */
    public enum ConvergenceReason {
        MAX_ITERATIONS("Maximum iterations reached"),
        REPEATING_ACTION("Same action repeated consecutively"),
        TASK_COMPLETED("Active task completed"),
        TASK_FAILED("Active task failed"),
        MANUAL_STOP("Explicitly stopped");

        private final String description;

        ConvergenceReason(String description) {
            this.description = description;
        }

        public String description() { return description; }
    }

    /**
     * Creates an AgentLoop with default convergence threshold.
     */
    public AgentLoop(AgentBrainService brain, Sensor sensor, Effector effector,
                     DriverState[] drivers, TaskScheduler scheduler) {
        this(brain, sensor, effector, drivers, scheduler, DEFAULT_CONVERGENCE_THRESHOLD);
    }

    /**
     * Creates an AgentLoop with explicit convergence threshold.
     */
    public AgentLoop(AgentBrainService brain, Sensor sensor, Effector effector,
                     DriverState[] drivers, TaskScheduler scheduler,
                     int convergenceThreshold) {
        this.brain = Objects.requireNonNull(brain, "brain must not be null");
        this.sensor = Objects.requireNonNull(sensor, "sensor must not be null");
        this.effector = Objects.requireNonNull(effector, "effector must not be null");
        this.drivers = Objects.requireNonNull(drivers, "drivers must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.convergenceThreshold = Math.max(1, convergenceThreshold);
    }

    // ── Synchronous execution ──

    /**
     * Runs the agent loop synchronously for up to {@code maxIterations} ticks.
     *
     * @return immutable snapshot of the full state history
     */
    public List<AgentState> run(int maxIterations) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("AgentLoop is already running");
        }

        try {
            log.info("Agent loop starting: maxIterations={}, convergenceThreshold={}",
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
                convergenceReason = ConvergenceReason.MAX_ITERATIONS;
            }

            log.info("Agent loop finished: ticks={}, reason={}, historySize={}",
                    tickCounter.get(), convergenceReason, history.size());

            return AgentState.immutableHistory(history);
        } finally {
            running.set(false);
        }
    }

    // ── Asynchronous execution ──

    /**
     * Runs the agent loop asynchronously using the default ForkJoinPool.
     *
     * @return CompletableFuture that completes when the loop converges
     */
    public CompletableFuture<List<AgentState>> runAsync(int maxIterations) {
        return runAsync(maxIterations, ForkJoinPool.commonPool());
    }

    /**
     * Runs the agent loop asynchronously using the given executor.
     *
     * @return CompletableFuture that completes when the loop converges
     */
    public CompletableFuture<List<AgentState>> runAsync(int maxIterations, Executor executor) {
        return CompletableFuture.supplyAsync(() -> run(maxIterations), executor);
    }

    // ── Manual control ──

    /**
     * Stops the loop manually. The current tick will complete, then the loop exits.
     */
    public void stop() {
        converged.set(true);
        convergenceReason = ConvergenceReason.MANUAL_STOP;
        log.info("Agent loop manually stopped at tick {}", tickCounter.get());
    }

    // ── Single tick ──

    /**
     * Executes one Observe → Think → Act cycle.
     *
     * @return the resulting agent state
     */
    public AgentState tick() {
        long tick = tickCounter.incrementAndGet();

        // ── Observe ──
        long observation = sensor.read();
        double[] driverSnapshot = snapshotDrivers();

        // ── Think ──
        // Process through brain to get 5-bit action code, convert to boolean vector
        int actionCode = brain.brain().decide(observation);
        boolean[] thought = actionCodeToThought(actionCode);

        // ── Act ──
        AgentAction action = selectAction(thought, driverSnapshot);
        AgentAction.ActionResult result = effector.execute(action);
        AgentAction executedAction = action.withResult(result);

        // Update driver states based on action result
        updateDrivers(result);

        return new AgentState(observation, thought, executedAction, driverSnapshot, tick);
    }

    // ── Action selection ──

    /**
     * Selects an action based on thought vector, driver levels, and current task.
     *
     * <p>Prioritization:
     * <ol>
     *   <li>Safety driver high → WAIT</li>
     *   <li>Active task exists → map task driver to appropriate action</li>
     *   <li>Brain output → map thought bits to action type</li>
     * </ol>
     */
    AgentAction selectAction(boolean[] thought, double[] driverLevels) {
        // Safety check: if safety driver is high, prefer WAIT
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
            return actionForTask(activeTask, thought);
        }

        // Brain-driven action selection from thought vector
        return actionFromThought(thought);
    }

    private AgentAction actionForTask(Task task, boolean[] thought) {
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

        // Map first 4 thought bits to action type (priority encoding)
        int code = 0;
        for (int i = 0; i < Math.min(4, thought.length); i++) {
            if (thought[i]) code |= (1 << i);
        }

        AgentAction.ActionType[] types = {
                AgentAction.ActionType.WAIT,     // 0000
                AgentAction.ActionType.MOVE,     // 0001
                AgentAction.ActionType.MINE,     // 0010
                AgentAction.ActionType.CRAFT,    // 0011
                AgentAction.ActionType.EAT,      // 0100
                AgentAction.ActionType.TOOL_UP,  // 0101
                AgentAction.ActionType.EXPLORE,  // 0110
                AgentAction.ActionType.OBSERVE,  // 0111
                AgentAction.ActionType.SPEAK,    // 1000
                AgentAction.ActionType.THINK,    // 1001
                AgentAction.ActionType.MOVE,     // 1010
                AgentAction.ActionType.EXPLORE,  // 1011
                AgentAction.ActionType.CRAFT,    // 1100
                AgentAction.ActionType.TOOL_UP,  // 1101
                AgentAction.ActionType.OBSERVE,  // 1110
                AgentAction.ActionType.THINK,    // 1111
        };

        return new AgentAction(types[code]);
    }

    // ── Convergence detection ──

    /**
     * Checks if the loop should stop based on the current state.
     */
    private boolean checkConvergence(AgentState state) {
        // Check task completion
        boolean anyTaskCompleted = scheduler.allTasks().stream()
                .anyMatch(t -> t.status() == Task.Status.COMPLETED);
        if (anyTaskCompleted) {
            converged.set(true);
            convergenceReason = ConvergenceReason.TASK_COMPLETED;
            return true;
        }

        // Check task failure
        boolean anyTaskFailed = scheduler.allTasks().stream()
                .anyMatch(t -> t.status() == Task.Status.FAILED);
        if (anyTaskFailed) {
            converged.set(true);
            convergenceReason = ConvergenceReason.TASK_FAILED;
            return true;
        }

        // Check repeating action
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
                convergenceReason = ConvergenceReason.REPEATING_ACTION;
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

        // Successful actions nudge drivers toward their targets
        for (DriverState driver : drivers) {
            if (result.success()) {
                double delta = -0.01; // slight satisfaction
                driver.nudge(delta);
            } else {
                double delta = 0.02; // increased urgency on failure
                driver.nudge(delta);
            }
        }

        // Curiosity increases with each tick regardless
        for (DriverState driver : drivers) {
            if (driver.type() == DriverType.CURIOSITY) {
                driver.nudge(0.005);
            }
        }
    }

    // ── Thought vector helpers ──

    /**
     * Converts a 5-bit action code to a boolean thought vector.
     */
    static boolean[] actionCodeToThought(int actionCode) {
        boolean[] thought = new boolean[5];
        for (int i = 0; i < 5; i++) {
            thought[i] = (actionCode & (1 << i)) != 0;
        }
        return thought;
    }

    // ── Accessors ──

    /** Returns an immutable snapshot of the state history. */
    public List<AgentState> history() {
        return AgentState.immutableHistory(history);
    }

    /** Returns the current (most recent) agent state. */
    public AgentState currentState() { return currentState; }

    /** Returns the current tick count. */
    public long tickCount() { return tickCounter.get(); }

    /** Returns true if the loop is currently running. */
    public boolean isRunning() { return running.get(); }

    /** Returns true if the loop has converged. */
    public boolean isConverged() { return converged.get(); }

    /** Returns the reason for convergence, or null if not yet converged. */
    public ConvergenceReason convergenceReason() { return convergenceReason; }

    /** Returns the convergence threshold. */
    public int convergenceThreshold() { return convergenceThreshold; }
}
