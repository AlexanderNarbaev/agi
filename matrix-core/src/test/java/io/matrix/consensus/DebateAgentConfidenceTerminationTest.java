package io.matrix.consensus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * RECON-W20 — regression lock for the {@link DebateAgent.adjustConfidence}
 * CAS livelock.
 *
 * <p>The bug: an unbounded {@code do/while(!compareAndSet)} loop over a value
 * clamped to [0,1]. At the saturation boundary an incrementer and a decrementer
 * compute different targets and neither CAS can win — both spin forever. This
 * test reproduces exactly that contention and requires the whole thing to finish.
 */
class DebateAgentConfidenceTerminationTest {

    private static final long TIMEOUT_SECONDS = 20;

    @Test
    void opposing_adjustments_at_the_clamp_boundary_must_terminate() throws Exception {
        int threads = 8;
        int perThread = 20_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final double delta = (t % 2 == 0) ? 0.05 : -0.05;
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        agent.adjustConfidence(delta);
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();

        assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            .as("adjustConfidence must make progress under opposing contention; "
                + "an unbounded CAS loop livelocks at the clamp boundary")
            .isTrue();

        pool.shutdownNow();
        assertThat(errors.get()).isZero();
        assertThat(agent.confidence()).isBetween(0.0, 1.0);
    }

    private static final DebateAgent agent = new DebateAgent("agent", "X", 0.5);

    @Test
    void no_op_deltas_leave_state_untouched() {
        DebateAgent a = new DebateAgent("n", "X", 0.42);
        a.adjustConfidence(0.0);
        assertThat(a.confidence()).isEqualTo(0.42);
    }

    @Test
    void nan_delta_is_ignored_rather_than_poisoning_state() {
        DebateAgent a = new DebateAgent("n", "X", 0.42);
        a.adjustConfidence(Double.NaN);
        assertThat(a.confidence()).isEqualTo(0.42);
    }

    @Test
    void repeated_deltas_converge_to_the_clamped_total() {
        DebateAgent a = new DebateAgent("c", "X", 0.5);
        a.adjustConfidence(0.2);
        assertThat(a.confidence()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-9));
        a.adjustConfidence(-0.3);
        assertThat(a.confidence()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
    }
}
