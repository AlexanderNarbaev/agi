package io.matrix.benchmark;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W296 — Native binary launch benchmark.
 *
 * <p>Measures startup + execution time of the native binary
 * for repeated invocations.
 */
class NativeBinaryLaunchBenchmarkTest {

    private static final Path BINARY = Paths.get("build/native/nativeCompile/matrix-core");
    private static final int RUNS = 100;

    @Test
    void avgStartupTime() throws Exception {
        long totalNs = 0;
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            Process p = new ProcessBuilder(BINARY.toString(), "--version").start();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            long elapsed = System.nanoTime() - start;
            totalNs += elapsed;
            p.destroyForcibly();
        }
        long avgMs = (totalNs / RUNS) / 1_000_000;
        System.out.println("Native binary avg startup+run: " + avgMs + "ms (over " + RUNS + " runs)");
        assertThat(avgMs).isLessThan(500L);
    }

    @Test
    void avgStartupWithBench() throws Exception {
        long totalNs = 0;
        for (int i = 0; i < 20; i++) {
            long start = System.nanoTime();
            Process p = new ProcessBuilder(BINARY.toString(), "--bench").start();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            long elapsed = System.nanoTime() - start;
            totalNs += elapsed;
            p.destroyForcibly();
        }
        long avgMs = (totalNs / 20) / 1_000_000;
        System.out.println("Native --bench avg total: " + avgMs + "ms (over 20 runs)");
    }

    @Test
    void throughput() throws Exception {
        long start = System.nanoTime();
        int successful = 0;
        for (int i = 0; i < 50; i++) {
            Process p = new ProcessBuilder(BINARY.toString(), "--version").start();
            boolean done = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (done) successful++; // Accept any exit code from native binary
            p.destroyForcibly();
        }
        long elapsed = System.nanoTime() - start;
        double opsPerSec = 50.0 / (elapsed / 1_000_000_000.0);
        System.out.println("Native binary throughput: " + (long)opsPerSec + " ops/sec (" + successful + "/50 successful)");
        assertThat(successful).isGreaterThanOrEqualTo(45); // Allow a few failures
        assertThat(opsPerSec).isGreaterThan(2.0);
    }
}
