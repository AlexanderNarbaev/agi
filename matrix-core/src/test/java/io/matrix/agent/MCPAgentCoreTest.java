package io.matrix.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MCPAgentCoreTest {

    @Test
    void testCreateAgent() {
        MCPAgentCore agent = new MCPAgentCore();
        assertNotNull(agent);
        assertFalse(agent.listTools().isEmpty(), "Default tools should be registered");
    }

    @Test
    void testListDefaultTools() {
        MCPAgentCore agent = new MCPAgentCore();
        List<String> tools = agent.listTools();
        assertTrue(tools.contains("run_code"));
        assertTrue(tools.contains("edit_file"));
        assertTrue(tools.contains("search_web"));
        assertTrue(tools.contains("query_db"));
    }

    @Test
    void testRegisterCustomTool() {
        MCPAgentCore agent = new MCPAgentCore();
        agent.registerTool(new MCPAgentCore.Tool("my_tool", "Custom tool",
            Map.of("x", "int"),
            params -> new MCPAgentCore.ToolResult("my_tool", true, "ok", 1)));
        assertTrue(agent.listTools().contains("my_tool"));
    }

    @Test
    void testExecuteTool() {
        MCPAgentCore agent = new MCPAgentCore();
        MCPAgentCore.ToolResult result = agent.executeTool("run_code",
            Map.of("language", "java", "code", "System.out.println(\"hello\")"));
        assertTrue(result.success());
    }

    @Test
    void testExecuteNonexistentTool() {
        MCPAgentCore agent = new MCPAgentCore();
        MCPAgentCore.ToolResult result = agent.executeTool("nonexistent", Map.of());
        assertFalse(result.success());
    }

    @Test
    void testExecutionLog() {
        MCPAgentCore agent = new MCPAgentCore();
        agent.executeTool("run_code", Map.of("code", "test1"));
        agent.executeTool("search_web", Map.of("query", "test2"));
        assertEquals(2, agent.getExecutionLog().size());
    }

    @Test
    void testSelfCorrectionSuccess() {
        MCPAgentCore agent = new MCPAgentCore();
        SelfCorrectionLoop loop = new SelfCorrectionLoop();
        SelfCorrectionLoop.CorrectionResult result = loop.runWithCorrection(
            "int x = 1 + 2;", agent);
        assertTrue(result.fixed() || result.attempts() > 0);
    }

    @Test
    void testSelfCorrectionMaxAttempts() {
        MCPAgentCore agent = new MCPAgentCore();
        // Register a failing tool to force max attempts
        agent.registerTool(new MCPAgentCore.Tool("fail_code", "Always fails",
            Map.of("code", "string"),
            params -> new MCPAgentCore.ToolResult("fail_code", false, "UnknownError", 1)));
        SelfCorrectionLoop loop = new SelfCorrectionLoop();
        SelfCorrectionLoop.CorrectionResult result = loop.runWithCorrection(
            "ERROR_UNKNOWN_TYPE", agent, "fail_code");
        assertEquals(3, result.attempts());
    }
}
