package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveToolUseTest {

    @Test
    void emptyRegistry() {
        CognitiveToolUse tu = new CognitiveToolUse();
        assertThat(tu.toolCount()).isEqualTo(0);
        assertThat(tu.listTools()).isEmpty();
    }

    @Test
    void registerTool() {
        CognitiveToolUse tu = new CognitiveToolUse();
        tu.register(new CognitiveToolUse.Tool("calc", "calculator",
            params -> "42"));
        assertThat(tu.toolCount()).isEqualTo(1);
        assertThat(tu.listTools()).contains("calc");
    }

    @Test
    void registerNullNoOp() {
        CognitiveToolUse tu = new CognitiveToolUse();
        tu.register(null);
        assertThat(tu.toolCount()).isEqualTo(0);
    }

    @Test
    void invokeRegisteredTool() {
        CognitiveToolUse tu = new CognitiveToolUse();
        tu.register(new CognitiveToolUse.Tool("echo", "echo",
            params -> "echoed"));
        CognitiveToolUse.ToolResult r = tu.invoke("echo", new HashMap<>());
        assertThat(r.success()).isTrue();
        assertThat(r.output()).isEqualTo("echoed");
    }

    @Test
    void invokeUnknownToolFails() {
        CognitiveToolUse tu = new CognitiveToolUse();
        CognitiveToolUse.ToolResult r = tu.invoke("unknown", new HashMap<>());
        assertThat(r.success()).isFalse();
        assertThat(r.output()).contains("Tool not found");
    }

    @Test
    void invokeNullParams() {
        CognitiveToolUse tu = new CognitiveToolUse();
        tu.register(new CognitiveToolUse.Tool("noop", "noop",
            params -> "ok"));
        CognitiveToolUse.ToolResult r = tu.invoke("noop", null);
        assertThat(r.success()).isTrue();
    }

    @Test
    void toolExceptionHandled() {
        CognitiveToolUse tu = new CognitiveToolUse();
        tu.register(new CognitiveToolUse.Tool("fail", "always fails",
            params -> { throw new RuntimeException("boom"); }));
        CognitiveToolUse.ToolResult r = tu.invoke("fail", new HashMap<>());
        assertThat(r.success()).isFalse();
        assertThat(r.output()).contains("boom");
    }

    @Test
    void invokeWithParameters() {
        CognitiveToolUse tu = new CognitiveToolUse();
        tu.register(new CognitiveToolUse.Tool("add", "add",
            params -> {
                int a = (int) params.getOrDefault("a", 0);
                int b = (int) params.getOrDefault("b", 0);
                return String.valueOf(a + b);
            }));
        Map<String, Object> params = new HashMap<>();
        params.put("a", 2);
        params.put("b", 3);
        CognitiveToolUse.ToolResult r = tu.invoke("add", params);
        assertThat(r.success()).isTrue();
        assertThat(r.output()).isEqualTo("5");
    }

    @Test
    void multipleToolsRegistered() {
        CognitiveToolUse tu = new CognitiveToolUse();
        tu.register(new CognitiveToolUse.Tool("t1", "tool 1", p -> "1"));
        tu.register(new CognitiveToolUse.Tool("t2", "tool 2", p -> "2"));
        tu.register(new CognitiveToolUse.Tool("t3", "tool 3", p -> "3"));
        assertThat(tu.toolCount()).isEqualTo(3);
        assertThat(tu.listTools()).containsExactlyInAnyOrder("t1", "t2", "t3");
    }
}
