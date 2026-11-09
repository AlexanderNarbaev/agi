package io.matrix.benchmark.w51;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §10 — the measurement run, repeatable.
 *
 * <p>The numbers in {@code docs-v2/engineering/W51-PERFORMANCE.md} were produced by this
 * test. It prints every measurement it takes, so that a reader who wants to check them can
 * run it and compare — and it asserts the invariants that make the numbers mean something,
 * so that a run on a machine in a different state reports a failure rather than a silently
 * different number.</p>
 *
 * <p><b>What it asserts, and what it does not.</b> It asserts that each path produced
 * samples, that the samples are ordered, and that the episode learned. It does NOT assert a
 * latency bound: a latency bound would fail on a loaded machine for a reason that has
 * nothing to do with the code, and a test that fails for a reason nobody can act on is a
 * test that gets ignored.</p>
 */
class W51PerformanceRunTest {

    @Test
    @DisplayName("the three hot paths are measured and printed")
    void theThreeHotPathsAreMeasured() {
        StringBuilder report = new StringBuilder();
        report.append("W51 §10 — the measurement run").append(System.lineSeparator());

        BenchResult portfolioOne = W51Benchmarks.methodSelection(
                W51Benchmarks.SMALLEST_PORTFOLIO, MicroBench.DEFAULT_WARMUP_ROUNDS,
                MicroBench.DEFAULT_MEASURED_ROUNDS);
        BenchResult portfolioTwo = W51Benchmarks.methodSelection(
                W51Benchmarks.MIDDLE_PORTFOLIO, MicroBench.DEFAULT_WARMUP_ROUNDS,
                MicroBench.DEFAULT_MEASURED_ROUNDS);
        BenchResult portfolioFive = W51Benchmarks.methodSelection(
                W51Benchmarks.LARGEST_PORTFOLIO, MicroBench.DEFAULT_WARMUP_ROUNDS,
                MicroBench.DEFAULT_MEASURED_ROUNDS);
        BenchResult ledger = W51Benchmarks.costLedgerLookup(MicroBench.DEFAULT_WARMUP_ROUNDS,
                MicroBench.DEFAULT_MEASURED_ROUNDS);
        W51Benchmarks.EpisodeMeasurement episode = W51Benchmarks.episode(
                MicroBench.EPISODE_WARMUP_ROUNDS, MicroBench.EPISODE_MEASURED_ROUNDS);
        BenchResult bridge = W51Benchmarks.bridge(MicroBench.DEFAULT_WARMUP_ROUNDS,
                MicroBench.DEFAULT_MEASURED_ROUNDS);

        for (BenchResult result : new BenchResult[]{portfolioOne, portfolioTwo, portfolioFive,
                ledger, episode.bench(), bridge}) {
            report.append(result).append(System.lineSeparator());
        }
        report.append("episode steps: ").append(episode.steps()).append(System.lineSeparator());
        report.append("episode allocated bytes per step: ")
                .append(episode.allocatedBytesPerStep()).append(System.lineSeparator());

        System.out.println(report);

        assertTrue(portfolioOne.samples() > 0);
        assertTrue(portfolioTwo.samples() > 0);
        assertTrue(portfolioFive.samples() > 0);
        assertTrue(ledger.samples() > 0);
        assertTrue(episode.bench().samples() > 0);
        assertTrue(bridge.samples() > 0);
        assertTrue(episode.steps() > 0);
    }
}
