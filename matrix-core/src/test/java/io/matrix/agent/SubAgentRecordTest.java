package io.matrix.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentRecordTest {

    @Test
    void resultRecordFields() {
        var r = new SubAgent.SubAgentResult("task1", "tool-out", true, "response-text");
        assertEquals("task1", r.task());
        assertEquals("tool-out", r.toolOutput());
        assertTrue(r.ok());
        assertEquals("response-text", r.response());
    }

    @Test
    void resultRecordWithNullToolOutput() {
        var r = new SubAgent.SubAgentResult("task", null, false, "rejected");
        assertNull(r.toolOutput());
        assertFalse(r.ok());
    }

    @Test
    void subAgentClassLoads() {
        assertNotNull(SubAgent.class);
    }
}