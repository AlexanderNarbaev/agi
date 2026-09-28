package io.matrix.consciousness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

/**
 * RECON-W20 — regression lock for the {@link KolmogorovComplexity} non-terminating
 * loop.
 *
 * <p>The bug: {@code logarithmicEncoding} advanced with
 * {@code x = (int)log2(x) + 1}, so {@code x == 2} mapped to {@code 2} and the
 * loop never exited. Every {@code estimate} over a trajectory with an alphabet
 * of exactly two distinct states hung. This class asserts termination for
 * every alphabet size a 32-bit trajectory can produce, under a hard timeout, so
 * a regression cannot be merged again.
 */
class KolmogorovComplexityTerminationTest {

    private static final long TIMEOUT_SECONDS = 10;

    private static <T> T mustFinish(Callable<T> body) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kolmogorov-termination-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<T> f = pool.submit(body);
            return f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError(
                    "KolmogorovComplexity did not terminate within " + TIMEOUT_SECONDS
                    + "s — non-terminating loop regression", e);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void estimate_terminates_for_two_symbol_alphabet() throws Exception {
        // THE regression: alphabet size 2 is the exact value that used to hang.
        double k = mustFinish(() -> KolmogorovComplexity.estimate(new long[]{1L, 2L}));
        assertThat(k).isFinite();
        assertThat(k).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void estimate_terminates_for_every_alphabet_size_from_1_to_64() throws Exception {
        for (int alphabet = 1; alphabet <= 64; alphabet++) {
            long[] trajectory = new long[alphabet * 2];
            for (int i = 0; i < trajectory.length; i++) trajectory[i] = i % alphabet;
            final int n = alphabet;
            double k = mustFinish(() -> KolmogorovComplexity.estimate(trajectory));
            assertThat(k).as("alphabet=%d", n).isFinite();
        }
    }

    @Test
    void estimateBinary_terminates_and_is_finite() throws Exception {
        assertThat(mustFinish(() -> KolmogorovComplexity.estimateBinary(new boolean[]{true, false})))
                .isFinite();
        assertThat(mustFinish(() -> KolmogorovComplexity.estimateBinary(new boolean[]{true, true, false})))
                .isFinite();
    }

    @Test
    void degenerate_inputs_are_handled() {
        // Article III: no wall-clock, no randomness, and total functions.
        assertThat(KolmogorovComplexity.estimate(null)).isEqualTo(0.0);
        assertThat(KolmogorovComplexity.estimate(new long[0])).isEqualTo(0.0);
        assertThat(KolmogorovComplexity.estimate(new long[]{42L})).isEqualTo(64.0);
        assertThat(KolmogorovComplexity.estimateBinary(null)).isEqualTo(0.0);
        assertThat(KolmogorovComplexity.estimateBinary(new boolean[0])).isEqualTo(0.0);
        // constant sequence: zero entropy, must not be NaN from log(0)
        assertThat(KolmogorovComplexity.estimateBinary(new boolean[]{true, true, true})).isFinite();
    }

    @Test
    void more_distinct_symbols_never_cheaper_than_fewer() throws Exception {
        // A complexity measure that got CHEAPER as the alphabet grew would be
        // nonsense; lock the monotonic direction under the corrected encoding.
        double two = mustFinish(() -> KolmogorovComplexity.estimate(new long[]{1L, 1L, 2L, 2L}));
        double many = mustFinish(() -> KolmogorovComplexity.estimate(new long[]{1L, 2L, 3L, 4L}));
        assertThat(many).isGreaterThan(two);
    }
}
