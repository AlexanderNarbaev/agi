package io.matrix.mediator.scheduler;

import io.matrix.mediator.DriverType;
import io.matrix.mediator.Task;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskSchedulerTest {

    @Test
    void shouldBeEmptyInitially() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        assertThat(scheduler.queueSize()).isEqualTo(0);
        assertThat(scheduler.pendingTasks()).isEmpty();
    }

    @Test
    void shouldEnqueueTasks() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        Task task = new Task(UUID.randomUUID(), DriverType.CURIOSITY, 0.5);
        scheduler.enqueue(task);

        assertThat(scheduler.queueSize()).isEqualTo(1);
    }

    @Test
    void shouldScheduleHighestPriorityTask() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        Task t1 = new Task(UUID.randomUUID(), DriverType.ENERGY, 0.3);
        Task t2 = new Task(UUID.randomUUID(), DriverType.CURIOSITY, 0.7);
        scheduler.enqueue(t1);
        scheduler.enqueue(t2);

        Task selected = scheduler.scheduleNext();
        assertThat(selected).isNotNull();
        assertThat(selected.basePriority()).isGreaterThanOrEqualTo(0.3);
    }

    @Test
    void shouldPreemptWithSafetyTask() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        Task normal = new Task(UUID.randomUUID(), DriverType.CURIOSITY, 0.5);
        Task safety = new Task(UUID.randomUUID(), DriverType.SAFETY, 0.9);
        scheduler.enqueue(normal);
        scheduler.enqueue(safety);

        scheduler.scheduleNext();

        Task preempted = scheduler.preemptWithSafety(safety);
        assertThat(preempted.driver()).isEqualTo(DriverType.SAFETY);
        assertThat(preempted.isActive()).isTrue();
    }

    @Test
    void shouldCompleteTask() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        Task task = new Task(UUID.randomUUID(), DriverType.ENERGY, 0.5);
        scheduler.enqueue(task);

        scheduler.complete(task);
        assertThat(task.isTerminal()).isTrue();
    }

    @Test
    void shouldFailTask() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        Task task = new Task(UUID.randomUUID(), DriverType.CURIOSITY, 0.5);
        scheduler.enqueue(task);

        scheduler.fail(task);
        assertThat(task.isTerminal()).isTrue();
    }

    @Test
    void shouldReplenishBudget() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        Task task = new Task(UUID.randomUUID(), DriverType.ENERGY, 0.9);
        scheduler.enqueue(task);
        scheduler.scheduleNext();

        scheduler.replenishBudget();
        assertThat(scheduler.budget().cpuAvailable()).isEqualTo(0.8);
    }

    @Test
    void shouldLogSchedulingActions() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        Task task = new Task(UUID.randomUUID(), DriverType.CURIOSITY, 0.5);
        scheduler.enqueue(task);
        scheduler.scheduleNext();

        assertThat(scheduler.scheduleLog()).isNotEmpty();
    }

    @Test
    void shouldTrackCycleCount() {
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        scheduler.scheduleNext();
        scheduler.scheduleNext();

        assertThat(scheduler.cycleCount()).isEqualTo(2);
    }
}
