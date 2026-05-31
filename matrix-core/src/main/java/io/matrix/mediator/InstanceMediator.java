package io.matrix.mediator;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.UUID;

import io.matrix.observability.MatrixMetrics;

/**
 * Instance-level Mediator — coordinates drivers, goals, and task scheduling.
 *
 * <p>Phase 1.2: singleton, no hierarchy. Manages Energy, Curiosity, and Safety
 * drivers. Generates goals when drivers exceed thresholds. Maintains a
 * priority task queue with anti-procrastination scheduling.
 *
 * <p>Ref: L4_Mediator.md §2.1, §3, §5
 */
public final class InstanceMediator {

    public record MediatorConfig(
            long tickIntervalMs,
            double resourceFactorStart,
            int maxActiveGoals
    ) {
        public static MediatorConfig defaults() {
            return new MediatorConfig(1000, 0.8, 10);
        }
    }

    private final MediatorConfig config;
    private final DriverState energy;
    private final DriverState safety;
    private final DriverState curiosity;
    private final MatrixMetrics metrics;
    private final Random rng;

    private final List<Goal> goals = new ArrayList<>();
    private final PriorityQueue<Task> taskQueue = new PriorityQueue<>();
    private final List<String> actionLog = new ArrayList<>();

    private long tickCount;
    private long lastTickTime;

    public InstanceMediator(MediatorConfig config, MatrixMetrics metrics, Random rng) {
        this.config = config;
        this.metrics = metrics;
        this.rng = rng;
        this.energy = DriverState.withDefaults(DriverType.ENERGY);
        this.safety = DriverState.withDefaults(DriverType.SAFETY);
        this.curiosity = DriverState.withDefaults(DriverType.CURIOSITY);
    }

    public InstanceMediator(MediatorConfig config, Random rng) {
        this(config, null, rng);
    }

    public static InstanceMediator withDefaults(MatrixMetrics metrics, Random rng) {
        return new InstanceMediator(MediatorConfig.defaults(), metrics, rng);
    }

    public static InstanceMediator withDefaults(Random rng) {
        return new InstanceMediator(MediatorConfig.defaults(), null, rng);
    }

    public DriverState energy() { return energy; }
    public DriverState safety() { return safety; }
    public DriverState curiosity() { return curiosity; }

    public List<DriverState> drivers() {
        return List.of(energy, safety, curiosity);
    }

    public List<Goal> goals() { return List.copyOf(goals); }

    public List<Task> tasks() {
        return taskQueue.stream()
                .filter(t -> !t.isTerminal())
                .sorted()
                .toList();
    }

    public List<Task> allTasks() {
        return taskQueue.stream().sorted().toList();
    }

    public List<String> actionLog() { return List.copyOf(actionLog); }

    public long tickCount() { return tickCount; }

    /**
     * Performs one mediation cycle:
     * <ol>
     * <li>Update all driver levels</li>
     * <li>Check thresholds and generate goals</li>
     * <li>Recalculate task priorities</li>
     * <li>Activate top-priority pending task if capacity available</li>
     * </ol>
     */
    public List<String> tick() {
        List<String> actions = new ArrayList<>();
        tickCount++;
        lastTickTime = System.currentTimeMillis();

        updateDrivers();
        generateGoalsFromDrivers(actions);
        recalculateTaskPriorities();
        activateNextTask(actions);

        actionLog.addAll(actions);
        if (actionLog.size() > 100) {
            actionLog.subList(0, actionLog.size() - 100).clear();
        }

        if (metrics != null) {
            metrics.driverEnergy(Math.round(energy.level() * 100));
            metrics.driverCuriosity(Math.round(curiosity.level() * 100));
            metrics.driverSafety(Math.round(safety.level() * 100));
        }

        return actions;
    }

    private void updateDrivers() {
        for (var d : drivers()) {
            d.update(rng);
        }
    }

    private void generateGoalsFromDrivers(List<String> actions) {
        for (DriverState driver : drivers()) {
            if (driver.isHigh()) {
                String description = goalDescriptionFor(driver);
                double priority = driver.level();

                boolean alreadyPending = goals.stream()
                        .anyMatch(g -> g.driver() == driver.type()
                                && g.description().equals(description)
                                && (g.status() == GoalStatus.PENDING || g.status() == GoalStatus.ACTIVE));
                if (!alreadyPending) {
                    Goal goal = Goal.create(driver.type(), description, priority);
                    goals.add(goal);
                    taskQueue.add(new Task(goal.id(), driver.type(), priority));
                    actions.add("GOAL:" + driver.type() + " → " + description);
                }
            }
        }
    }

    private String goalDescriptionFor(DriverState driver) {
        return switch (driver.type()) {
            case ENERGY -> "optimize_resources";
            case SAFETY -> "run_safety_check";
            case CURIOSITY -> "explore_mutation_space";
        };
    }

    private void recalculateTaskPriorities() {
        double resourceFactor = config.resourceFactorStart();
        for (Task task : taskQueue) {
            if (!task.isTerminal()) {
                task.recalculatePriority(resourceFactor);
            }
        }
    }

    private void activateNextTask(List<String> actions) {
        long activeCount = taskQueue.stream()
                .filter(Task::isActive).count();
        if (activeCount >= config.maxActiveGoals()) {
            return;
        }

        taskQueue.stream()
                .filter(Task::isPending)
                .max(Task::compareTo)
                .ifPresent(task -> {
                    task.setStatus(Task.Status.ACTIVE);
                    Goal matching = goals.stream()
                            .filter(g -> g.id().equals(task.goalId())
                                    && g.status() == GoalStatus.PENDING)
                            .findFirst().orElse(null);
                    if (matching != null) {
                        int idx = goals.indexOf(matching);
                        goals.set(idx, matching.activate());
                    }
                    actions.add("TASK:" + task.driver() + " active priority="
                            + String.format("%.3f", task.currentPriority()));
                });
    }

    /**
     * Notifies the mediator that a goal was successfully completed.
     */
    public void completeGoal(UUID goalId) {
        for (int i = 0; i < goals.size(); i++) {
            Goal g = goals.get(i);
            if (g.id().equals(goalId)) {
                goals.set(i, g.satisfy());
                actionLog.add("GOAL_COMPLETED:" + g.driver() + " → " + g.description());
                break;
            }
        }
        taskQueue.stream()
                .filter(t -> t.goalId().equals(goalId))
                .findFirst()
                .ifPresent(t -> t.setStatus(Task.Status.COMPLETED));
    }

    /**
     * Notifies the mediator that a goal has failed.
     */
    public void failGoal(UUID goalId) {
        for (int i = 0; i < goals.size(); i++) {
            Goal g = goals.get(i);
            if (g.id().equals(goalId)) {
                goals.set(i, g.fail());
                actionLog.add("GOAL_FAILED:" + g.driver() + " → " + g.description());
                break;
            }
        }
        taskQueue.stream()
                .filter(t -> t.goalId().equals(goalId))
                .findFirst()
                .ifPresent(t -> t.setStatus(Task.Status.FAILED));
    }

    /**
     * External signal: low resource availability increases Energy driver.
     */
    public void reportLowResources() {
        energy.nudge(0.15);
    }

    /**
     * External signal: anomaly detected increases Safety driver.
     */
    public void reportAnomaly() {
        safety.nudge(0.2);
    }

    /**
     * External signal: no new data for a while increases Curiosity driver.
     */
    public void reportStagnation() {
        curiosity.nudge(0.15);
    }

    // ---- Phase 2: Hierarchy integration ----

    /**
     * Returns the mediator level (INSTANCE = 0.8).
     *
     * <p>Ref: L4_Mediator.md §2.1
     */
    public double weight() { return 0.8; }

    /**
     * Delegates a decision upward (to GlobalMediator in Phase 2).
     * For Phase 2.1, returns the action name for logging.
     */
    public String escalateUp(String action, String reason) {
        actionLog.add("ESCALATE:" + action + " reason=" + reason);
        return "ESCALATED to GLOBAL: " + action;
    }

    /**
     * Vetoes a lower-level decision that threatens privacy or safety.
     * Implemented as FROZEN rule per L4 §2.2.
     */
    public String veto(String action, String reason) {
        actionLog.add("VETO:" + action + " reason=" + reason);
        return "VETO applied: " + action;
    }

    /**
     * Receives a message from a ClusterMediator.
     */
    public void receiveClusterMessage(String clusterId, String action, String payload) {
        actionLog.add("FROM_CLUSTER:" + clusterId + ":" + action + "=" + payload);
    }
}
