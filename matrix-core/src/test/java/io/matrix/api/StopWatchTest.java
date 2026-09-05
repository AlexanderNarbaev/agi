package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 132 — StopWatch unit tests. */
class StopWatchTest {

    @Test
    void newWatchNotRunning() {
        StopWatch sw = new StopWatch();
        assertThat(sw.isRunning()).isFalse();
        assertThat(sw.elapsedNanos()).isZero();
    }

    @Test
    void startSetsRunning() {
        StopWatch sw = new StopWatch().start();
        assertThat(sw.isRunning()).isTrue();
        sw.stop();
    }

    @Test
    void stopSetsNotRunning() {
        StopWatch sw = new StopWatch().start();
        sw.stop();
        assertThat(sw.isRunning()).isFalse();
    }

    @Test
    void elapsedMeasuresTime() throws Exception {
        StopWatch sw = new StopWatch().start();
        Thread.sleep(50);
        long ms = sw.elapsedMs();
        sw.stop();
        assertThat(ms).isGreaterThanOrEqualTo(50);
        assertThat(ms).isLessThan(500);
    }

    @Test
    void resetClears() throws Exception {
        StopWatch sw = new StopWatch().start();
        Thread.sleep(20);
        sw.reset();
        assertThat(sw.isRunning()).isFalse();
        assertThat(sw.elapsedNanos()).isZero();
    }

    @Test
    void doubleStopIsNoOp() throws Exception {
        StopWatch sw = new StopWatch().start();
        Thread.sleep(20);
        sw.stop();
        long first = sw.elapsedNanos();
        sw.stop();  // second stop should be no-op
        long second = sw.elapsedNanos();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void elapsedSecondsBasic() throws Exception {
        StopWatch sw = new StopWatch().start();
        Thread.sleep(50);
        double seconds = sw.elapsedSeconds();
        sw.stop();
        assertThat(seconds).isBetween(0.05, 0.5);
    }

    @Test
    void fluentApiReturnsSelf() {
        StopWatch sw = new StopWatch();
        assertThat(sw.start()).isSameAs(sw);
        sw.stop();
        assertThat(sw.reset()).isSameAs(sw);
    }
}
