package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W571 — Tests for Levin Scheduler.
 */
class LevinSchedulerTest {

    @Test
    void testSubmitAndPoll() {
        LevinScheduler scheduler = new LevinScheduler();
        scheduler.submit("t1", "Simple task", 1, LevinScheduler.TaskType.INFERENCE, System.nanoTime() + 1_000_000_000L);
        assertEquals(1, scheduler.getQueueSize());

        LevinScheduler.Task task = scheduler.poll();
        assertNotNull(task);
        assertEquals("t1", task.id());
        assertEquals(LevinScheduler.TaskStatus.RUNNING, task.status());
    }

    @Test
    void testPriorityOrder() {
        LevinScheduler scheduler = new LevinScheduler();
        long deadline = System.nanoTime() + 1_000_000_000L;

        // Lower complexity = higher priority
        scheduler.submit("low", "Low complexity", 1, LevinScheduler.TaskType.INFERENCE, deadline);
        scheduler.submit("high", "High complexity", 5, LevinScheduler.TaskType.INFERENCE, deadline);
        scheduler.submit("mid", "Mid complexity", 3, LevinScheduler.TaskType.INFERENCE, deadline);

        // Should poll in order: low (complexity 1), mid (3), high (5)
        assertEquals("low", scheduler.poll().id());
        assertEquals("mid", scheduler.poll().id());
        assertEquals("high", scheduler.poll().id());
    }

    @Test
    void testSelfImprovementBoost() {
        LevinScheduler scheduler = new LevinScheduler();
        long deadline = System.nanoTime() + 1_000_000_000L;

        // Same complexity, but SELF_IMPROVE gets boosted
        scheduler.submit("normal", "Normal task", 3, LevinScheduler.TaskType.INFERENCE, deadline);
        scheduler.submit("improve", "Self improve", 3, LevinScheduler.TaskType.SELF_IMPROVE, deadline);

        // Self-improvement should come first
        assertEquals("improve", scheduler.poll().id());
        assertEquals("normal", scheduler.poll().id());
    }

    @Test
    void testBudgetTracking() {
        LevinScheduler scheduler = new LevinScheduler();
        long deadline = System.nanoTime() + 1_000_000_000L;

        scheduler.submit("t1", "Task 1", 1, LevinScheduler.TaskType.INFERENCE, deadline);
        scheduler.submit("t2", "Task 2", 2, LevinScheduler.TaskType.INFERENCE, deadline);

        assertTrue(scheduler.getTotalBudget() > 0);
        assertEquals(0, scheduler.getAllocatedBudget());

        scheduler.poll();
        assertTrue(scheduler.getAllocatedBudget() > 0);
        assertTrue(scheduler.getRemainingBudget() < scheduler.getTotalBudget());
    }

    @Test
    void testGetTasksByType() {
        LevinScheduler scheduler = new LevinScheduler();
        long deadline = System.nanoTime() + 1_000_000_000L;

        scheduler.submit("inf", "Inference", 1, LevinScheduler.TaskType.INFERENCE, deadline);
        scheduler.submit("learn", "Learning", 2, LevinScheduler.TaskType.LEARNING, deadline);
        scheduler.submit("improve", "Self improve", 3, LevinScheduler.TaskType.SELF_IMPROVE, deadline);
        scheduler.submit("ethics", "Ethics check", 1, LevinScheduler.TaskType.ETHICAL_CHECK, deadline);

        assertEquals(1, scheduler.getTasksByType(LevinScheduler.TaskType.INFERENCE).size());
        assertEquals(1, scheduler.getTasksByType(LevinScheduler.TaskType.LEARNING).size());
        assertEquals(1, scheduler.getSelfImprovementTasks().size());
    }

    @Test
    void testEmptyQueue() {
        LevinScheduler scheduler = new LevinScheduler();
        assertNull(scheduler.poll());
        assertEquals(0, scheduler.getQueueSize());
    }
}
