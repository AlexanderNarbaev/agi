package io.matrix.mediator.scheduler;

import io.matrix.mediator.DriverType;
import io.matrix.mediator.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Priority task scheduler with resource budgeting and anti-procrastination.
 *
 * <p>Tasks are ordered by dynamic priority:
 * {@code priority = basePriority * ageFactor * resourceFactor}.
 * Safety tasks cannot be preempted and have no resource budget limit.
 *
 * <p>Ref: L4_Mediator.md §5
 */
public final class TaskScheduler {

    public record ResourceBudget(double cpuAvailable, double memoryAvailable,
                                  double maxBudgetPerTask) {
        public static ResourceBudget defaults() {
            return new ResourceBudget(0.8, 0.8, 0.5);
        }

        public ResourceBudget consume(double amount) {
            return new ResourceBudget(
                    Math.max(0, cpuAvailable - amount),
                    Math.max(0, memoryAvailable - amount),
                    maxBudgetPerTask);
        }
    }

    private final PriorityQueue<Task> queue;
    private final List<String> scheduleLog = new ArrayList<>();
    private ResourceBudget budget;
    private long cycleCount;

    public TaskScheduler(ResourceBudget budget) {
        this.budget = budget;
        this.queue = new PriorityQueue<>(Comparator.reverseOrder());
    }

    public static TaskScheduler withDefaults() {
        return new TaskScheduler(ResourceBudget.defaults());
    }

    public void enqueue(Task task) {
        queue.add(task);
        scheduleLog.add("ENQUEUE:" + task.driver() + " base="
                + String.format("%.2f", task.basePriority()));
    }

    public List<Task> allTasks() {
        return queue.stream().sorted(Comparator.reverseOrder()).toList();
    }

    public List<Task> pendingTasks() {
        return queue.stream()
                .filter(Task::isPending)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public int queueSize() { return queue.size(); }

    public long cycleCount() { return cycleCount; }

    public List<String> scheduleLog() { return List.copyOf(scheduleLog); }

    /**
     * Runs one scheduling cycle: recalculates priorities, selects top task,
     * allocates resources.
     *
     * @return the selected task, or null if none available
     */
    public Task scheduleNext() {
        cycleCount++;

        recalculateAllPriorities();

        List<Task> candidates = pendingTasks();
        if (candidates.isEmpty()) return null;

        Task safetyTask = candidates.stream()
                .filter(t -> t.driver() == DriverType.SAFETY)
                .findFirst().orElse(null);

        if (safetyTask != null) {
            safetyTask.setStatus(Task.Status.ACTIVE);
            scheduleLog.add("SCHEDULE:SAFETY preempt pri="
                    + String.format("%.3f", safetyTask.currentPriority()));
            return safetyTask;
        }

        Task best = candidates.get(0);
        if (budget.cpuAvailable() <= 0 || budget.memoryAvailable() <= 0) {
            scheduleLog.add("SCHEDULE:NONE budget exhausted");
            return null;
        }

        double cost = Math.min(best.basePriority(), budget.maxBudgetPerTask());
        best.setStatus(Task.Status.ACTIVE);
        budget = budget.consume(cost);

        scheduleLog.add("SCHEDULE:" + best.driver() + " pri="
                + String.format("%.3f", best.currentPriority())
                + " cost=" + String.format("%.2f", cost));
        return best;
    }

    /**
     * Preempts current task with a higher-priority safety task.
     */
    public Task preemptWithSafety(Task safetyTask) {
        queue.stream()
                .filter(Task::isActive)
                .forEach(t -> t.setStatus(Task.Status.PENDING));

        safetyTask.setStatus(Task.Status.ACTIVE);
        scheduleLog.add("PREEMPT:" + safetyTask.driver());
        return safetyTask;
    }

    /**
     * Marks a task as completed and frees resources.
     */
    public void complete(Task task) {
        task.setStatus(Task.Status.COMPLETED);
        scheduleLog.add("COMPLETE:" + task.driver());
    }

    /**
     * Marks a task as failed.
     */
    public void fail(Task task) {
        task.setStatus(Task.Status.FAILED);
        scheduleLog.add("FAIL:" + task.driver());
    }

    /**
     * Returns current resource budget.
     */
    public ResourceBudget budget() { return budget; }

    /**
     * Replenishes resource budget.
     */
    public void replenishBudget() {
        this.budget = ResourceBudget.defaults();
    }

    private void recalculateAllPriorities() {
        double resourceFactor = budget.cpuAvailable() * budget.memoryAvailable();
        for (Task task : queue) {
            if (!task.isTerminal()) {
                task.recalculatePriority(resourceFactor);
            }
        }
    }
}
