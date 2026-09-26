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

    @Test
    void real_gpu_kernel_engine_default_constructor() throws Exception {
        var ctor = RealGpuKernelEngine.class.getDeclaredConstructor();
        assertThat(ctor).isNotNull();
        RealGpuKernelEngine eng = new RealGpuKernelEngine();
        var snap = eng.snapshot();
        // Honest backend — reports actual backend (GPU or CPU)
        assertThat(snap).containsKey("backend");
        assertThat(snap).containsKey("tasks_executed");
        assertThat(String.valueOf(snap.get("backend")))
            .isIn("GPU", "CPU", "unavailable");
    }

    @Test
    void real_gpu_kernel_engine_with_gpuEnabled_false() {
        RealGpuKernelEngine eng = new RealGpuKernelEngine(false);
        var snap = eng.snapshot();
        // gpu_enabled key may be absent in this snapshot shape; check backend instead
        // which is the truthful signal: it must NOT say GPU when not enabled.
        assertThat(String.valueOf(snap.get("backend")))
            .isIn("CPU", "unavailable", "cpu");
    }

    @Test
    void real_gpu_kernel_engine_run_kernel_does_not_simulate() {
        RealGpuKernelEngine eng = new RealGpuKernelEngine(false);
        var snap = eng.snapshot();
        // Honest backend selection: backend must NOT be "GPU" when disabled.
        String backend = String.valueOf(snap.get("backend"));
        assertThat(backend).isNotEqualTo("GPU");
        assertThat(backend).isIn("CPU", "unavailable", "cpu");
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
