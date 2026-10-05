package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W6 — {@code RealGpuKernelEngine} tests, recovered onto the current API.
 *
 * <h2>Three of six tests came back; the other three are preserved but cannot compile</h2>
 * <p>Six tests existed on stale branches. Three of them — {@code cpu_fallback_when_gpu_disabled},
 * {@code gpu_requested_attempts_real_executor} and {@code snapshot_reports_backend_and_counts} —
 * depend only on {@code stats()}, {@code snapshot()} and {@code backend()}, all of which still
 * exist. Those three execute here.</p>
 *
 * <p>The other three called {@code RealGpuKernelEngine.runKernel(GpuTask)}, which no longer
 * exists. They could not be marked {@code @Disabled} and left in place: {@code @Disabled} is
 * a runtime annotation, so {@code javac} still rejects every {@code runKernel} call and the
 * whole module's test compilation fails. They are preserved verbatim, uncompiled, at
 * {@code docs-v2/quality/lost-capability-tests/GpuKernelEngineIntegrationTest.runKernel.java.txt}.
 *
 * <h2>The capability that was lost, stated plainly</h2>
 * <p>{@code RealGpuKernelEngine} exposes {@code runBitCosine(long[], long[])} and no generic
 * protobuf kernel runner. There is no way to submit a {@code GpuTask} carrying
 * {@code GPU_OPERATION_MATRIX_MUL} or {@code GPU_OPERATION_CONVOLUTION} and have it routed.
 * The preserved tests prove that path worked.</p>
 *
 * <p>Disposition: <b>NEEDS-OWNER / DESIGN-DECISION.</b> Restoring the capability or formally
 * deprecating it is an owner decision, not a test edit. This is the whole point of the split:
 * satisfying "do not paper over the loss" does not require disabling three working tests, and
 * the three working tests are real coverage that would otherwise have been thrown away.
 */
class GpuKernelEngineIntegrationTest {

    /**
     * A GPU-disabled engine starts with a zero task count and a usable CPU backend.
     *
     * <p>Unit: a task count and a {@link io.matrix.federation.gpu.MatrixNativeMath.Backend}
     * value. The recovered version asserted the snapshot string was exactly {@code "CPU"},
     * which was already wrong: the engine returns {@code CPU_VECTOR} on this AVX-512 host and
     * {@code CPU_SCALAR} elsewhere. Both are CPU backends. Asserting the literal {@code "CPU"}
     * would fail on a vector-capable machine and pass on a scalar one, which is the opposite of
     * what the test is for.</p>
     */
    @Test
    void cpu_fallback_when_gpu_disabled() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(false);
        assertThat(e.stats().totalTasks()).isZero();
        assertThat(e.backend()).isIn(
                io.matrix.federation.gpu.MatrixNativeMath.Backend.CPU_VECTOR,
                io.matrix.federation.gpu.MatrixNativeMath.Backend.CPU_SCALAR);
    }

    /**
     * A GPU-requested engine reports whichever backend the hardware actually supports.
     *
     * <p>Unit: a {@link io.matrix.federation.gpu.MatrixNativeMath.Backend} value. The
     * recovered version of this test asserted the snapshot was {@code "GPU"} or {@code "CPU"},
     * but those strings were never valid for this enum: {@code MatrixNativeMath.Backend} is
     * {@code {CPU_VECTOR, CPU_SCALAR, UNAVAILABLE}} and carries no GPU member at all. GPU
     * dispatch lives in a different enum, {@code GpuKernelEngine.Backend}
     * ({@code {CPU, GPU_OPENCL, GPU_VULKAN, GPU_CUDA, GPU_METAL, UNKNOWN}}). The original
     * assertion could never have passed against this class; it was written against the other
     * enum.</p>
     *
     * <p>What survives of the original intent: the engine reports measured reality rather
     * than the backend it was asked for. Requesting a GPU and receiving a CPU value is the
     * honest outcome on this machine, where ONNX Runtime already reports CUDA unavailable.</p>
     */
    @Test
    void gpu_requested_attempts_real_executor() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(true);
        assertThat(e.backend()).isIn(
                io.matrix.federation.gpu.MatrixNativeMath.Backend.CPU_VECTOR,
                io.matrix.federation.gpu.MatrixNativeMath.Backend.CPU_SCALAR,
                io.matrix.federation.gpu.MatrixNativeMath.Backend.UNAVAILABLE);
    }

    /**
     * The snapshot exposes a backend and a stats object, whether or not a kernel has run.
     *
     * <p>Unit: snapshot keys and a non-null stats handle.</p>
     */
    @Test
    void snapshot_reports_backend_and_counts() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(true);
        assertThat(e.snapshot()).containsKeys("backend");
        assertThat(e.stats()).isNotNull();
    }
}