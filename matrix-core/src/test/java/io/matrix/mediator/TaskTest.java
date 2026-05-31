package io.matrix.mediator;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTest {

    @Test
    void shouldCreateTaskWithPendingStatus() {
        UUID goalId = UUID.randomUUID();
        Task task = new Task(goalId, DriverType.CURIOSITY, 0.5);

        assertThat(task.goalId()).isEqualTo(goalId);
        assertThat(task.driver()).isEqualTo(DriverType.CURIOSITY);
        assertThat(task.basePriority()).isEqualTo(0.5);
        assertThat(task.currentPriority()).isEqualTo(0.5);
        assertThat(task.status()).isEqualTo(Task.Status.PENDING);
        assertThat(task.isPending()).isTrue();
        assertThat(task.isActive()).isFalse();
        assertThat(task.isTerminal()).isFalse();
    }

    @Test
    void shouldTransitionStatus() {
        Task task = new Task(UUID.randomUUID(), DriverType.ENERGY, 0.5);

        task.setStatus(Task.Status.ACTIVE);
        assertThat(task.isActive()).isTrue();
        assertThat(task.isPending()).isFalse();

        task.setStatus(Task.Status.COMPLETED);
        assertThat(task.isTerminal()).isTrue();

        Task task2 = new Task(UUID.randomUUID(), DriverType.SAFETY, 0.3);
        task2.setStatus(Task.Status.FAILED);
        assertThat(task2.isTerminal()).isTrue();
    }

    @Test
    void shouldRecalculatePriorityWithAgeFactor() {
        Task task = new Task(UUID.randomUUID(), DriverType.ENERGY, 0.5);

        task.recalculatePriority(1.0);

        assertThat(task.currentPriority()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldClampPriorityToOne() {
        Task task = new Task(UUID.randomUUID(), DriverType.ENERGY, 0.9);
        for (int i = 0; i < 5; i++) {
            task.recalculatePriority(1.0);
        }

        assertThat(task.currentPriority()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldSortByPriorityDescending() {
        Task t1 = new Task(UUID.randomUUID(), DriverType.ENERGY, 0.3);
        Task t2 = new Task(UUID.randomUUID(), DriverType.CURIOSITY, 0.7);

        assertThat(t1.compareTo(t2)).isGreaterThan(0);
        assertThat(t2.compareTo(t1)).isLessThan(0);
    }

    @Test
    void shouldTrackAgeInSeconds() {
        Task task = new Task(UUID.randomUUID(), DriverType.SAFETY, 0.5);

        int age = task.ageSeconds();
        assertThat(age).isGreaterThanOrEqualTo(0);
    }

}
