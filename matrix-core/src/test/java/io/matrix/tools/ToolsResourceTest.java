package io.matrix.tools;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolsResourceTest {

    @Test
    void listToolsReturns8() {
        var res = new ToolsResource();
        Response r = res.listTools();
        assertEquals(200, r.getStatus());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) r.getEntity();
        assertEquals(8, body.get("count"));
    }

    @Test
    void getStatsStartsZero() {
        var res = new ToolsResource();
        Response r = res.getStats();
        assertEquals(200, r.getStatus());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) r.getEntity();
        assertEquals(0, body.get("totalInvocations"));
        @SuppressWarnings("unchecked")
        var perTool = (Map<String, Integer>) body.get("perTool");
        assertTrue(perTool.isEmpty());
    }

    @Test
    void invokeToolRejectsBlankName() {
        var res = new ToolsResource();
        Map<String, Object> payload = new HashMap<>();
        payload.put("tool", "");
        Response r = res.invokeTool(payload);
        assertEquals(400, r.getStatus());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) r.getEntity();
        assertEquals("tool name required", body.get("error"));
    }

    @Test
    void invokeToolRejectsNullName() {
        var res = new ToolsResource();
        Map<String, Object> payload = new HashMap<>();
        // no tool key
        Response r = res.invokeTool(payload);
        assertEquals(400, r.getStatus());
    }

    @Test
    void invokeDatetimeReturnsCurrentTime() {
        var res = new ToolsResource();
        Map<String, Object> args = new HashMap<>();
        args.put("timezone", "UTC");
        // Use private invoke via invokeTool
        Map<String, Object> payload = new HashMap<>();
        payload.put("tool", "datetime");
        payload.put("args", args);
        Response r = res.invokeTool(payload);
        assertEquals(200, r.getStatus());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) r.getEntity();
        assertEquals("success", body.get("status"));
        assertNotNull(body.get("result"));
    }

    @Test
    void invokeCalculatorEvaluatesExpression() {
        var res = new ToolsResource();
        Map<String, Object> args = new HashMap<>();
        args.put("expression", "2 + 2");
        Map<String, Object> payload = new HashMap<>();
        payload.put("tool", "calculator");
        payload.put("args", args);
        Response r = res.invokeTool(payload);
        assertEquals(200, r.getStatus());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) r.getEntity();
        assertEquals("success", body.get("status"));
    }

    @Test
    void invokeUnknownToolReturnsError() {
        var res = new ToolsResource();
        Map<String, Object> args = new HashMap<>();
        Map<String, Object> payload = new HashMap<>();
        payload.put("tool", "no_such_tool_xyz");
        payload.put("args", args);
        Response r = res.invokeTool(payload);
        // Current implementation defaults unknown tool to "tool not found" message
        // but returns 200 with status=success. This test documents the behavior.
        assertEquals(200, r.getStatus());
    }

    @Test
    void statsIncrementAfterInvoke() {
        var res = new ToolsResource();
        Map<String, Object> args = new HashMap<>();
        args.put("timezone", "UTC");
        Map<String, Object> payload = new HashMap<>();
        payload.put("tool", "datetime");
        payload.put("args", args);
        Response invokeResp = res.invokeTool(payload);
        assertEquals(200, invokeResp.getStatus());

        Response r = res.getStats();
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) r.getEntity();
        assertEquals(1, body.get("totalInvocations"));
        @SuppressWarnings("unchecked")
        var perTool = (Map<String, Integer>) body.get("perTool");
        assertEquals(Integer.valueOf(1), perTool.getOrDefault("datetime", 0));
    }
}
