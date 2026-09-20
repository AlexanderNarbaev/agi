package io.matrix.federation.liquid;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * W571 — Levin Search Scheduler for task allocation.
 *
 * Budget allocation proportional to 2^-l (Levin complexity).
 * Tasks with lower complexity get more resources.
 * Priority queue for "Self-Improvement" tasks.
 *
 * Ref: Leonid Levin, "Universal Sequential Search Problems" (1973)
 */
public final class LevinScheduler {

    /** A task with complexity and priority. */
    public record Task(
            String id,
            String description,
            int complexity,  // Levin complexity level (0 = simplest)
            long deadlineNs,
            TaskType type,
            TaskStatus status,
            long submittedAtNs
    ) {}

    public enum TaskType {
        INFERENCE,      // BIR/HDC inference task
        LEARNING,       // Learning from data
        SELF_IMPROVE,   // Self-improvement task
        MAINTENANCE,    // System maintenance
        ETHICAL_CHECK   // Ethical verification
    }

    public enum TaskStatus {
        QUEUED, RUNNING, COMPLETED, FAILED, TIMEOUT
    }

    private final PriorityBlockingQueue<TaskEntry> queue;
    private final Map<String, TaskEntry> taskMap = new HashMap<>();
    private long totalBudget = 0;
    private long allocatedBudget = 0;

    private record TaskEntry(Task task, double priority) implements Comparable<TaskEntry> {
        @Override
        public int compareTo(TaskEntry other) {
            // Higher priority first (lower complexity = higher priority)
            return Double.compare(other.priority, this.priority);
        }
    }

    public LevinScheduler() {
        this.queue = new PriorityBlockingQueue<>();
    }

    /**
     * Submit a task. Budget allocation = 2^(-complexity).
     *
     * @return the submitted task
     */
    public Task submit(String id, String description, int complexity, TaskType type, long deadlineNs) {
        double priority = Math.pow(2, -complexity); // Levin complexity
        // Boost priority for self-improvement tasks
        if (type == TaskType.SELF_IMPROVE) priority *= 2.0;

        Task task = new Task(id, description, complexity, deadlineNs, type, TaskStatus.QUEUED, System.nanoTime());
        TaskEntry entry = new TaskEntry(task, priority);

        queue.offer(entry);
        taskMap.put(id, entry);
        totalBudget += (long) (priority * 1000);

        return task;
    }

    /**
     * Get the next task to execute (highest priority).
     *
     * @return the next task, or null if queue is empty
     */
    public Task poll() {
        TaskEntry entry = queue.poll();
        if (entry == null) return null;

        Task running = new Task(
                entry.task().id(),
                entry.task().description(),
                entry.task().complexity(),
                entry.task().deadlineNs(),
                entry.task().type(),
                TaskStatus.RUNNING,
                entry.task().submittedAtNs()
        );
        allocatedBudget += (long) (entry.priority() * 1000);
        return running;
    }

    /**
     * Mark a task as completed.
     */
    public void complete(String taskId) {
        // Just remove from map; queue already polled
        taskMap.remove(taskId);
    }

    /**
     * Mark a task as failed.
     */
    public void fail(String taskId) {
        taskMap.remove(taskId);
    }

    /**
     * Get queue size.
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Get total budget (sum of all priorities).
     */
    public long getTotalBudget() {
        return totalBudget;
    }

    /**
     * Get allocated budget (sum of executed priorities).
     */
    public long getAllocatedBudget() {
        return allocatedBudget;
    }

    /**
     * Get remaining budget.
     */
    public long getRemainingBudget() {
        return totalBudget - allocatedBudget;
    }

    /**
     * Get tasks by type.
     */
    public List<Task> getTasksByType(TaskType type) {
        List<Task> result = new ArrayList<>();
        for (TaskEntry entry : queue) {
            if (entry.task().type() == type) {
                result.add(entry.task());
            }
        }
        return result;
    }

    /**
     * Get self-improvement tasks (highest priority).
     */
    public List<Task> getSelfImprovementTasks() {
        return getTasksByType(TaskType.SELF_IMPROVE);
    }
}
