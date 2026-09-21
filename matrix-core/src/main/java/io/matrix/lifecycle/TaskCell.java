package io.matrix.lifecycle;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TaskCell (DESIGN-12): ephemeral task-specific instance with full context.
 *
 * <p>A TaskCell is created for a specific task, lives for the duration of
 * the task, then is destroyed. It cannot write to long-term memory, cannot
 * trigger training, and cannot modify the world model. It only reasons
 * and returns results.
 *
 * <p>Lifecycle: CREATED → RUNNING → COMPLETED | FAILED | TIMEOUT → DESTROYED.
 */
public final class TaskCell {

    public enum State { CREATED, RUNNING, COMPLETED, FAILED, TIMEOUT, DESTROYED }

    private final String id;
    private final String task;
    private final Map<String, Object> context;
    private final Instant created;
    private final long maxDurationMs;
    private volatile State state;
    private volatile String result;
    private volatile String error;

    private static final AtomicLong idCounter = new AtomicLong();

    public TaskCell(String task, Map<String, Object> context, long maxDurationMs) {
        this.id = "tc-" + UUID.randomUUID().toString().substring(0, 8);
        this.task = task;
        this.context = context == null ? Map.of() : Map.copyOf(context);
        this.created = Instant.now();
        this.maxDurationMs = maxDurationMs;
        this.state = State.CREATED;
    }

    public String id() { return id; }
    public String task() { return task; }
    public State state() { return state; }
    public String result() { return result; }
    public String error() { return error; }
    public Map<String, Object> context() { return context; }

    /** Execute the task. */
    public void execute(TaskExecutor executor) {
        if (state != State.CREATED) throw new IllegalStateException("not in CREATED");
        state = State.RUNNING;
        try {
            result = executor.execute(task, context);
            state = State.COMPLETED;
        } catch (Exception e) {
            error = e.getMessage();
            state = State.FAILED;
        }
    }

    /** Check if timeout exceeded. */
    public boolean isTimeout() {
        return Instant.now().toEpochMilli() - created.toEpochMilli() > maxDurationMs;
    }

    /** Destroy the cell (cleanup). */
    public void destroy() {
        state = State.DESTROYED;
    }

    @FunctionalInterface
    public interface TaskExecutor {
        String execute(String task, Map<String, Object> context);
    }
}
