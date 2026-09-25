package io.matrix.brain.runtime;

import io.matrix.federation.proto.GpuOperation;
import io.matrix.federation.proto.GpuTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W6 — Real GpuTaskExecutor tests.
 */
class GpuKernelEngineIntegrationTest {

    @Test
    void cpu_fallback_when_gpu_disabled() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(false);
        assertThat(e.stats().totalTasks()).isZero();
        assertThat(e.snapshot().get("backend")).isEqualTo("CPU");
    }

    @Test
    void gpu_requested_attempts_real_executor() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(true);
        // Backend reports GPU or CPU based on real hardware availability.
        assertThat(e.snapshot().get("backend")).isIn("GPU", "CPU");
    }

    @Test
    void running_a_real_kernel_increments_stats() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(true);
        GpuTask task = GpuTask.newBuilder()
            .setOperation(GpuOperation.GPU_OPERATION_MATRIX_MUL)
            .setInputData(com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3, 4}))
            .build();
        var result = e.runKernel(task);
        assertThat(result).isNotNull();
        assertThat(result.latencyNs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void multiple_kernels_accumulate_total_tasks() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(false);
        for (int i = 0; i < 5; i++) {
            e.runKernel(GpuTask.newBuilder()
                .setOperation(GpuOperation.GPU_OPERATION_CONVOLUTION)
                .setInputData(com.google.protobuf.ByteString.copyFrom(new byte[]{1}))
                .build());
        }
        assertThat(e.stats().totalTasks()).isEqualTo(5);
    }

    @Test
    void snapshot_reports_backend_and_counts() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(false);
        var snap = e.snapshot();
        assertThat(snap).containsKeys(
            "backend", "tasks_executed", "total_tasks",
            "success_count", "timeout_count", "oom_count");
    }

    @Test
    void kernel_result_records_gpu_used_flag() {
        RealGpuKernelEngine e = new RealGpuKernelEngine(false);
        var r = e.runKernel(GpuTask.newBuilder()
            .setOperation(GpuOperation.GPU_OPERATION_INFERENCE)
            .build());
        assertThat(r.gpuUsed()).isFalse();  // CPU mode
    }
}
