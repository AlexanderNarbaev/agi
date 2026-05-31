package io.matrix.mediator;

import java.util.Objects;
import java.util.UUID;

/**
 * A scheduled task derived from a goal, with dynamic priority.
 *
 * <p>Priority formula: priority = basePriority * ageFactor * resourceFactor.
 * Tasks remain in the queue until completed or failed.
 *
 * <p>Ref: L4_Mediator.md §5.1
 */
public final class Task implements Comparable<Task> {

    public enum Status { PENDING, ACTIVE, COMPLETED, FAILED }

    private final UUID id;
    private final UUID goalId;
    private final DriverType driver;
    private final long createdAt;
    private final double basePriority;

    private double currentPriority;
    private Status status;

    public Task(UUID goalId, DriverType driver, double basePriority) {
        this.id = UUID.randomUUID();
        this.goalId = Objects.requireNonNull(goalId);
        this.driver = Objects.requireNonNull(driver);
        this.basePriority = basePriority;
        this.createdAt = System.currentTimeMillis();
        this.currentPriority = basePriority;
        this.status = Status.PENDING;
    }

    public UUID id() { return id; }

    public UUID goalId() { return goalId; }

    public DriverType driver() { return driver; }

    public double basePriority() { return basePriority; }

    public double currentPriority() { return currentPriority; }

    public Status status() { return status; }

    public void setStatus(Status status) { this.status = status; }

    public long createdAt() { return createdAt; }

    public boolean isPending() { return status == Status.PENDING; }

    public boolean isActive() { return status == Status.ACTIVE; }

    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.FAILED;
    }

    public int ageSeconds() {
        return (int) ((System.currentTimeMillis() - createdAt) / 1000);
    }

    /**
     * Recalculates priority using the age-based anti-procrastination formula.
     *
     * @param resourceFactor multiplier for resource availability (0..1)
     */
    public void recalculatePriority(double resourceFactor) {
        double ageFactor = ageFactor();
        currentPriority = basePriority * ageFactor * resourceFactor;
        currentPriority = Math.min(currentPriority, 1.0);
    }

    private double ageFactor() {
        double seconds = ageSeconds();
        if (seconds <= 0) return 1.0;
        return 1.0 + 0.1 * Math.exp(seconds / 300.0);
    }

    @Override
    public int compareTo(Task other) {
        return Double.compare(other.currentPriority, this.currentPriority);
    }
}
