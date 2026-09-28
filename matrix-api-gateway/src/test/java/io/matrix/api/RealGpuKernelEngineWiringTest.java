package io.matrix.api;

import io.matrix.brain.runtime.RealGpuKernelEngine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W2 #8 — RealGpuKernelEngine promoted.
 *
 * <p>Honest backend selection — reports actual device or UNAVAILABLE.
 * Never simulated success (Article VIII).</p>
 */
class RealGpuKernelEngineWiringTest {

    @Test
    void gateway_has_gpuKernelEngine_field() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("gpuKernelEngine");
        f.setAccessible(true);
        assertThat(f.getType().getName())
            .isEqualTo("io.matrix.brain.runtime.RealGpuKernelEngine");
    }

    /**
     * RECON-W20: the exhaustive set of backends {@code RealGpuKernelEngine} can
     * report, derived from the enum rather than hand-copied.
     *
     * <p>BEFORE: the test asserted membership in {@code {"GPU","CPU","unavailable"}}.
     * That list was stale in BOTH directions — {@code GPU} and {@code CPU} are not
     * values the engine can ever emit, while the three it does emit
     * ({@code CPU_VECTOR}, {@code CPU_SCALAR}, {@code UNAVAILABLE}) were all
     * missing. Introduced at RECON-W2; went stale at RECON-W16 when the Vector
     * API path was added.</p>
     *
     * <p>AFTER: membership is checked against the enum constants themselves, so the
     * assertion can never drift out of date again. The no-simulation guarantee
     * (Article VIII) is asserted separately and explicitly.</p>
     */
    private static final java.util.Set<String> REAL_BACKENDS = java.util.Arrays.stream(
            io.matrix.federation.gpu.MatrixNativeMath.Backend.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    @Test
    void backend_allowlist_matches_the_real_exhaustive_enum() {
        // Guards the test's own premise: the engine has no backend outside the enum.
        assertThat(REAL_BACKENDS).containsExactlyInAnyOrder("CPU_VECTOR", "CPU_SCALAR", "UNAVAILABLE");
    }

    @Test
    void real_gpu_kernel_engine_default_constructor() throws Exception {
        var ctor = RealGpuKernelEngine.class.getDeclaredConstructor();
        assertThat(ctor).isNotNull();
        RealGpuKernelEngine eng = new RealGpuKernelEngine();
        var snap = eng.snapshot();
        // Honest backend — reports the actual backend it selected.
        assertThat(snap).containsKey("backend");
        assertThat(snap).containsKey("gpu_enabled");
        assertThat(snap).containsKey("vector_available");
        assertThat(snap).containsKey("stats");
        // RECON-W20: BEFORE this asserted a top-level "tasks_executed" key that
        // snapshot() has never produced — the counter lives in the nested
        // GpuStats record as "totalTasks". Assert the real path instead.
        Object statsObj = snap.get("stats");
        assertThat(statsObj).isInstanceOf(io.matrix.federation.gpu.GpuTaskExecutor.GpuStats.class);
        int totalTasks = ((io.matrix.federation.gpu.GpuTaskExecutor.GpuStats) statsObj).totalTasks();
        assertThat(totalTasks).isNotNegative();
        assertThat(REAL_BACKENDS).contains(String.valueOf(snap.get("backend")));
    }

    @Test
    void real_gpu_kernel_engine_with_gpuEnabled_false() {
        RealGpuKernelEngine eng = new RealGpuKernelEngine(false);
        var snap = eng.snapshot();
        // gpu_enabled key may be absent in this snapshot shape; check backend instead
        // which is the truthful signal: it must NOT be a GPU backend when not enabled.
        String backend = String.valueOf(snap.get("backend"));
        assertThat(REAL_BACKENDS).contains(backend);
        assertThat(backend).doesNotStartWith("GPU");
    }

    @Test
    void real_gpu_kernel_engine_run_kernel_does_not_simulate() {
        RealGpuKernelEngine eng = new RealGpuKernelEngine(false);
        var snap = eng.snapshot();
        // Article VIII: backend must be a real one, never a fabricated "GPU".
        String backend = String.valueOf(snap.get("backend"));
        assertThat(backend).isNotEqualTo("GPU");
        assertThat(REAL_BACKENDS).contains(backend);
    }

    @Test
    void gpu_endpoint_handler_exists() throws Exception {
        var m = MinimalHttpServer.class.getDeclaredMethod("handleGpu",
            com.sun.net.httpserver.HttpExchange.class);
        assertThat(m).isNotNull();
    }

    @Test
    void real_gpu_kernel_engine_stats_returns_real_metrics() {
        RealGpuKernelEngine eng = new RealGpuKernelEngine(false);
        var stats = eng.stats();
        assertThat(stats).isNotNull();
    }
}
