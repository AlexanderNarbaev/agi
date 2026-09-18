package io.matrix.federation.gpu;

import io.matrix.federation.proto.GpuConfig;
import io.matrix.federation.proto.GpuOperation;
import io.matrix.federation.proto.GpuResult;
import io.matrix.federation.proto.GpuStatus;
import io.matrix.federation.proto.GpuTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W362 — GPU task executor stub.
 * 
 * Accepts GpuTask messages and produces GpuResult. In production, this would
 * dispatch to real CUDA/OpenCL/ROCm kernels. For now, executes in CPU
 * (since real GPU not always available) and returns stub results.
 * 
 * Per SPEC-013 Phase 3: GPU kernel integration.
 * Per CONSTITUTION I v3: seeded Random for any randomness.
 */
public final class GpuTaskExecutor {
    
    /** Stats: per-operation count, total time, success/failure */
    public record GpuStats(
        Map<GpuOperation, Integer> operationCounts,
        Map<GpuOperation, Long> operationTimeNanos,
        int totalTasks,
        int successCount,
        int timeoutCount,
        int oomCount
    ) {}
    
    /** Deterministic RNG (seeded). Reserved for tie-breaking and downstream
     *  stochastic sampling. Exposed via getRng() for callers that need it. */
    private final Random rng;
    private final boolean gpuEnabled;
    private final Map<GpuOperation, Integer> opCounts = new ConcurrentHashMap<>();
    private final Map<GpuOperation, Long> opTimeNanos = new ConcurrentHashMap<>();
    private int totalTasks = 0;
    private int successCount = 0;
    private int timeoutCount = 0;
    private int oomCount = 0;
    
    public GpuTaskExecutor(long seed, boolean gpuEnabled) {
        this.rng = new Random(seed);
        this.gpuEnabled = gpuEnabled;
    }
    
    /**
     * Execute a GpuTask and return GpuResult.
     * 
     * If gpuEnabled = false, uses CPU fallback (still produces valid result).
     * Real GPU dispatch would replace this with kernel launch.
     */
    public GpuResult execute(GpuTask task) {
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        
        long startNs = System.nanoTime();
        GpuOperation op = task.getOperation();
        opCounts.merge(op, 1, Integer::sum);
        totalTasks++;
        
        try {
            // Simulate work
            byte[] input = task.getInputData().toByteArray();
            byte[] output = simulateOperation(op, input, task.getConfig());
            
            long elapsed = System.nanoTime() - startNs;
            opTimeNanos.merge(op, elapsed, Long::sum);
            successCount++;
            
            return GpuResult.newBuilder()
                .setTaskId(task.getTaskId())
                .setStatus(GpuStatus.GPU_STATUS_SUCCESS)
                .setOutputData(com.google.protobuf.ByteString.copyFrom(output))
                .setExecutionTimeNs(elapsed)
                .build();
        } catch (TimeoutException e) {
            timeoutCount++;
            return GpuResult.newBuilder()
                .setTaskId(task.getTaskId())
                .setStatus(GpuStatus.GPU_STATUS_TIMEOUT)
                .build();
        } catch (OutOfMemoryException e) {
            oomCount++;
            return GpuResult.newBuilder()
                .setTaskId(task.getTaskId())
                .setStatus(GpuStatus.GPU_STATUS_OOM)
                .build();
        } catch (Exception e) {
            return GpuResult.newBuilder()
                .setTaskId(task.getTaskId())
                .setStatus(GpuStatus.GPU_STATUS_FAILED)
                .setErrorMessage(e.getMessage() != null ? e.getMessage() : "unknown error")
                .build();
        }
    }
    
    /**
     * Simulate operation execution. Real implementation would dispatch to GPU.
     */
    private byte[] simulateOperation(GpuOperation op, byte[] input, GpuConfig config) {
        // Simulated work — return same input bytes (echo) for now
        // Real: launch kernel based on op type
        switch (op) {
            case GPU_OPERATION_MATRIX_MUL:
            case GPU_OPERATION_CONVOLUTION:
            case GPU_OPERATION_ATTENTION:
            case GPU_OPERATION_EMBEDDING:
            case GPU_OPERATION_TRAINING_STEP:
            case GPU_OPERATION_INFERENCE:
                return input;
            default:
                return new byte[0];
        }
    }
    
    /**
     * Get cumulative statistics.
     */
    public GpuStats getStats() {
        return new GpuStats(
            new java.util.HashMap<>(opCounts),
            new java.util.HashMap<>(opTimeNanos),
            totalTasks,
            successCount,
            timeoutCount,
            oomCount
        );
    }
    
    /**
     * Whether GPU is enabled (true) or CPU fallback (false).
     */
    public boolean isGpuEnabled() {
        return gpuEnabled;
    }
    
    /**
     * Number of tasks executed so far.
     */
    public int getTotalTaskCount() {
        return totalTasks;
    }
    
    /**
     * Get deterministic random.
     */
    public Random getRng() {
        return rng;
    }
    
    /** Simulated timeout (would be from kernel in real impl) */
    public static class TimeoutException extends RuntimeException {
        public TimeoutException(String msg) { super(msg); }
    }
    
    /** Simulated OOM (would be from CUDA out-of-memory) */
    public static class OutOfMemoryException extends RuntimeException {
        public OutOfMemoryException(String msg) { super(msg); }
    }
}
