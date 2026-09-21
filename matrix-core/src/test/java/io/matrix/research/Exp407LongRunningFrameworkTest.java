package io.matrix.research;

import io.matrix.integration.LongRunningFramework;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUN 407 — Phase Z LongRunning autonomy framework.
 */
class Exp407LongRunningFrameworkTest {

    @Test
    void frameworkRegistersAndCountsTasks() {
        LongRunningFramework fw = new LongRunningFramework();
        fw.register(new LongRunningFramework.TaskSpec(
                "task1", () -> {}, Duration.ofSeconds(1)));
        fw.register(new LongRunningFramework.TaskSpec(
                "task2", () -> {}, Duration.ofSeconds(2)));
        assertThat(fw.taskCount()).isEqualTo(2);
        fw.stop();
    }

    @Test
    void frameworkRunsTasksOnce() {
        AtomicInteger counter = new AtomicInteger();
        LongRunningFramework fw = new LongRunningFramework();
        fw.register(new LongRunningFramework.TaskSpec(
                "counter", counter::incrementAndGet, Duration.ofSeconds(1)));
        fw.runOnce();
        assertThat(counter.get()).isEqualTo(1);
        fw.runOnce();
        assertThat(counter.get()).isEqualTo(2);
        fw.stop();
    }

    @Test
    void frameworkCapturesErrorsGracefully() {
        LongRunningFramework fw = new LongRunningFramework();
        fw.register(new LongRunningFramework.TaskSpec(
                "bad", () -> { throw new RuntimeException("boom"); },
                Duration.ofSeconds(1)));
        fw.runOnce();
        var report = fw.report();
        assertThat(report.tasksRun()).isZero();
        assertThat(report.lastError()).contains("boom");
        assertThat(report.tickCount()).isEqualTo(1);  // tick still increments
        fw.stop();
    }

    @Test
    void frameworkRejectsInvalidTask() {
        LongRunningFramework fw = new LongRunningFramework();
        assertThatThrownBy(() -> fw.register(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fw.register(
                new LongRunningFramework.TaskSpec("t", null, Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fw.register(
                new LongRunningFramework.TaskSpec("t", () -> {}, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
        fw.stop();
    }

    @Test
    void frameworkSchedulesAndRuns() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        LongRunningFramework fw = new LongRunningFramework();
        fw.register(new LongRunningFramework.TaskSpec(
                "fast", counter::incrementAndGet, Duration.ofMillis(100)));
        fw.start();
        Thread.sleep(350);  // ~3 ticks
        fw.stop();
        assertThat(counter.get()).isGreaterThanOrEqualTo(2);
    }
}
