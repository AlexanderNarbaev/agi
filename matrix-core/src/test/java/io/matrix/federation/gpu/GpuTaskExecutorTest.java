package io.matrix.federation.gpu;

import io.matrix.federation.proto.GpuConfig;
import io.matrix.federation.proto.GpuOperation;
import io.matrix.federation.proto.GpuStatus;
import io.matrix.federation.proto.GpuTask;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GpuTaskExecutorTest {
    
    private GpuTask sample(String id, GpuOperation op) {
        return GpuTask.newBuilder()
            .setTaskId(id)
            .setOperation(op)
            .setInputData(ByteString.copyFromUtf8("test input"))
            .setConfig(GpuConfig.newBuilder()
                .setBackend("cpu-fallback")
                .build())
            .build();
    }
    
    @Test
    void testBasicExecution() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        var result = exec.execute(sample("t1", GpuOperation.GPU_OPERATION_MATRIX_MUL));
        
        assertEquals("t1", result.getTaskId());
        assertEquals(GpuStatus.GPU_STATUS_SUCCESS, result.getStatus());
        assertFalse(result.getOutputData().isEmpty());
    }
    
    @Test
    void testAllOperationsSupported() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        
        GpuOperation[] ops = {
            GpuOperation.GPU_OPERATION_MATRIX_MUL,
            GpuOperation.GPU_OPERATION_CONVOLUTION,
            GpuOperation.GPU_OPERATION_ATTENTION,
            GpuOperation.GPU_OPERATION_EMBEDDING,
            GpuOperation.GPU_OPERATION_TRAINING_STEP,
            GpuOperation.GPU_OPERATION_INFERENCE
        };
        
        for (int i = 0; i < ops.length; i++) {
            var result = exec.execute(sample("t" + i, ops[i]));
            assertEquals(GpuStatus.GPU_STATUS_SUCCESS, result.getStatus());
        }
    }
    
    @Test
    void testNullTaskThrows() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        assertThrows(IllegalArgumentException.class, () -> exec.execute(null));
    }
    
    @Test
    void testStatsUpdate() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        
        exec.execute(sample("t1", GpuOperation.GPU_OPERATION_MATRIX_MUL));
        exec.execute(sample("t2", GpuOperation.GPU_OPERATION_MATRIX_MUL));
        exec.execute(sample("t3", GpuOperation.GPU_OPERATION_ATTENTION));
        
        var stats = exec.getStats();
        assertEquals(3, stats.totalTasks());
        assertEquals(3, stats.successCount());
        assertEquals(0, stats.timeoutCount());
        assertEquals(0, stats.oomCount());
        
        assertEquals(2, stats.operationCounts().get(GpuOperation.GPU_OPERATION_MATRIX_MUL));
        assertEquals(1, stats.operationCounts().get(GpuOperation.GPU_OPERATION_ATTENTION));
    }
    
    @Test
    void testCpuFallbackMode() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        assertFalse(exec.isGpuEnabled());
    }
    
    @Test
    void testGpuModeFlag() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, true);
        assertTrue(exec.isGpuEnabled());
    }
    
    @Test
    void testExecutionTimeRecorded() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        var result = exec.execute(sample("t1", GpuOperation.GPU_OPERATION_INFERENCE));
        
        assertTrue(result.getExecutionTimeNs() >= 0L);
    }
    
    @Test
    void testTotalTaskCount() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        assertEquals(0, exec.getTotalTaskCount());
        
        exec.execute(sample("t1", GpuOperation.GPU_OPERATION_MATRIX_MUL));
        exec.execute(sample("t2", GpuOperation.GPU_OPERATION_INFERENCE));
        
        assertEquals(2, exec.getTotalTaskCount());
    }
    
    @Test
    void testRngAccessible() {
        GpuTaskExecutor exec1 = new GpuTaskExecutor(42L, false);
        GpuTaskExecutor exec2 = new GpuTaskExecutor(42L, false);
        
        // Same seed → same first value
        assertEquals(exec1.getRng().nextLong(), exec2.getRng().nextLong());
    }
    
    @Test
    void testEchoOutputPreservesInput() {
        GpuTaskExecutor exec = new GpuTaskExecutor(42L, false);
        String input = "hello world";
        GpuTask task = GpuTask.newBuilder()
            .setTaskId("t1")
            .setOperation(GpuOperation.GPU_OPERATION_MATRIX_MUL)
            .setInputData(ByteString.copyFromUtf8(input))
            .setConfig(GpuConfig.newBuilder().setBackend("cpu").build())
            .build();
        
        var result = exec.execute(task);
        assertEquals(input, result.getOutputData().toStringUtf8());
    }
}
