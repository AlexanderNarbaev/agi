package io.matrix.actions;

import io.matrix.lifecycle.TaskCell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W-G tests for {@link ActionArena}: concurrent arbitration, budget
 * enforcement, queue-full rejection, and the per-cell isolation contract
 * (DESIGN-12 §TaskCell reuses).
 */
class ActionArenaTest {

    private ActionArena arena;
    private ExecutorService submitPool;

    @BeforeEach
    void setUp() {
        arena = new ActionArena(4, 2_000L, 16);
        submitPool = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void tearDown() {
        arena.close();
        submitPool.shutdownNow();
    }

    @Test
    void singleSubmissionExecutes() throws Exception {
        TaskCell cell = new TaskCell("echo", Map.of("msg", "hi"), 5_000L);
        TaskCell.TaskExecutor echo = (t, ctx) -> "echo:" + ctx.get("msg");
        Future<ActionArena.Arbitration> fut = arena.submit(cell, echo);
        ActionArena.Arbitration arb = fut.get(2, TimeUnit.SECONDS);
        assertThat(arb.outcome()).isEqualTo(ActionArena.Outcome.EXECUTED);
        assertThat(arb.result()).isEqualTo("echo:hi");
        assertThat(arena.totalArbitrations()).isEqualTo(1L);
    }

    @Test
    void concurrentSubmissionsRespectParallelism() throws Exception {
        int n = 32;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger inFlightPeak = new AtomicInteger();
        AtomicInteger inFlightCurrent = new AtomicInteger();
        CompletableFuture<?>[] all = new CompletableFuture[n];

        for (int i = 0; i < n; i++) {
            all[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                TaskCell cell = new TaskCell("slow", Map.of(), 5_000L);
                TaskCell.TaskExecutor exec = (t, ctx) -> {
                    int cur = inFlightCurrent.incrementAndGet();
                    inFlightPeak.updateAndGet(p -> Math.max(p, cur));
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    inFlightCurrent.decrementAndGet();
                    return "done";
                };
                try {
                    return arena.submit(cell, exec).get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, submitPool);
        }
        start.countDown();
        CompletableFuture.allOf(all).get(10, TimeUnit.SECONDS);
        // peak parallelism must not exceed the arena's maxParallelism (4)
        assertThat(inFlightPeak.get()).isLessThanOrEqualTo(4);
    }

    @Test
    void queueFullRejectsExcessSubmissions() throws Exception {
        // small arena: queue depth 4, parallelism 1
        ActionArena smallArena = new ActionArena(1, 5_000L, 4);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            TaskCell.TaskExecutor blocking = (t, ctx) -> {
                try {
                    latch.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return "ok";
            };
            // fill: 4 cells queued (queue depth = 4), 1 in-flight (maxParallelism = 1)
            // (returns futures but we don't wait — the executor runs the first
            // synchronously and the latch blocks it)
            for (int i = 0; i < 5; i++) {
                smallArena.submit(new TaskCell("fill", Map.of(), 5_000L), blocking);
            }
            // now hammer: many of these must be rejected because queue is full
            int rejected = 0;
            for (int i = 0; i < 50; i++) {
                ActionArena.Arbitration arb = smallArena.submit(
                        new TaskCell("hammer", Map.of(), 5_000L),
                        (t, ctx) -> "ok").get();
                if (arb.outcome() == ActionArena.Outcome.REJECTED_QUEUE_FULL) {
                    rejected++;
                }
            }
            assertThat(rejected).isGreaterThan(0);
            latch.countDown(); // unblock the fills
        } finally {
            smallArena.close();
        }
    }

    @Test
    void budgetExceededReportsTimeout() throws Exception {
        // budget = 50ms; cell takes 200ms; expect TIMEOUT outcome
        TaskCell cell = new TaskCell("slow", Map.of(), 5_000L);
        TaskCell.TaskExecutor slow = (t, ctx) -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return "should-not-count";
        };
        ActionArena tightArena = new ActionArena(2, 50L, 16);
        try {
            Future<ActionArena.Arbitration> fut = tightArena.submit(cell, slow);
            ActionArena.Arbitration arb = fut.get(2, TimeUnit.SECONDS);
            // the wall-clock check inside the arena flags this as TIMEOUT
            // (the cell itself completed before the budget because we sleep
            // 200ms and budget is 50ms)
            assertThat(arb.outcome()).isEqualTo(ActionArena.Outcome.TIMEOUT);
        } finally {
            tightArena.close();
        }
    }

    @Test
    void executionFailureReportsFailedOutcome() throws Exception {
        TaskCell cell = new TaskCell("boom", Map.of(), 5_000L);
        TaskCell.TaskExecutor explode = (t, ctx) -> {
            throw new IllegalStateException("intentional");
        };
        Future<ActionArena.Arbitration> fut = arena.submit(cell, explode);
        ActionArena.Arbitration arb = fut.get(2, TimeUnit.SECONDS);
        assertThat(arb.outcome()).isEqualTo(ActionArena.Outcome.FAILED);
        assertThat(arb.result()).contains("intentional");
    }

    @Test
    void closedArenaRejectsSubmissions() throws Exception {
        ActionArena oneShot = new ActionArena(2, 5_000L, 16);
        oneShot.close();
        TaskCell cell = new TaskCell("after-close", Map.of(), 5_000L);
        TaskCell.TaskExecutor exec = (t, ctx) -> "x";
        ActionArena.Arbitration arb = oneShot.submit(cell, exec).get();
        assertThat(arb.outcome()).isEqualTo(ActionArena.Outcome.REJECTED_QUEUE_FULL);
    }

    @Test
    void perCellIsolationIsPreserved() throws Exception {
        // two cells with different contexts must execute independently
        TaskCell a = new TaskCell("a", Map.of("k", "alpha"), 5_000L);
        TaskCell b = new TaskCell("b", Map.of("k", "beta"), 5_000L);
        TaskCell.TaskExecutor exec = (t, ctx) -> t + ":" + ctx.get("k");
        Future<ActionArena.Arbitration> fa = arena.submit(a, exec);
        Future<ActionArena.Arbitration> fb = arena.submit(b, exec);
        assertThat(fa.get(2, TimeUnit.SECONDS).result()).isEqualTo("a:alpha");
        assertThat(fb.get(2, TimeUnit.SECONDS).result()).isEqualTo("b:beta");
    }

    @SuppressWarnings("unused")
    private static ExecutionException swallow() {
        return null;
    }
}