package io.matrix.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrainPipelineTest {

    @Test
    void brainInputOfTextFactory() {
        var in = BrainPipeline.BrainInput.ofText("hello");
        assertEquals("hello", in.text());
        assertNotNull(in.images());
        assertNotNull(in.audio());
        assertTrue(in.images().isEmpty());
        assertTrue(in.audio().isEmpty());
    }

    @Test
    void brainInputWithMedia() {
        var in = new BrainPipeline.BrainInput("hi", java.util.List.of(new byte[]{1, 2, 3}), java.util.List.of(new byte[]{4, 5}));
        assertEquals("hi", in.text());
        assertEquals(1, in.images().size());
        assertEquals(1, in.audio().size());
    }

    @Test
    void brainOutputFields() {
        var execs = new BrainPipeline.BlockExecutions(
                "text", "neural-3", "truncate", 2, 1);
        var out = new BrainPipeline.BrainOutput("response", execs, 1234L);
        assertEquals("response", out.content());
        assertEquals("text", out.executions().inputProcessor());
        assertEquals("neural-3", out.executions().consciousLayer());
        assertEquals("truncate", out.executions().outputProcessor());
        assertEquals(2, out.executions().memoryReads());
        assertEquals(1, out.executions().memoryWrites());
        assertEquals(1234L, out.latencyMicros());
    }

    @Test
    void brainOutputAllowsNullContent() {
        var execs = new BrainPipeline.BlockExecutions(
                "ip", "cl", "op", 0, 0);
        var out = new BrainPipeline.BrainOutput(null, execs, 0L);
        assertNull(out.content());
        assertNotNull(out.executions());
    }
}