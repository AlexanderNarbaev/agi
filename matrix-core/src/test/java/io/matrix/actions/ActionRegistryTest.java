package io.matrix.actions;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActionRegistryTest {

    @Test
    void registerAndGet() {
        var reg = new ActionRegistry();
        var action = new ActionRegistry.Action(
                "test",
                new ActionRegistry.IoSchema(Map.of("in", String.class), String.class),
                List.of(), List.of(),
                input -> "out");
        reg.register(action);
        assertTrue(reg.has("test"));
        assertEquals("test", reg.get("test").name());
    }

    @Test
    void executeSuccess() {
        var reg = new ActionRegistry();
        var action = new ActionRegistry.Action(
                "calc",
                new ActionRegistry.IoSchema(Map.of("x", Integer.class), Integer.class),
                List.of("x"), List.of(),
                input -> (Integer) input.get("x") * 2);
        reg.register(action);

        var result = reg.get("calc").execute(Map.of("x", 5));
        assertTrue(result.success());
        assertEquals(10, result.output());
    }

    @Test
    void executePreconditionFailed() {
        var reg = new ActionRegistry();
        var action = new ActionRegistry.Action(
                "calc",
                new ActionRegistry.IoSchema(Map.of("x", Integer.class), Integer.class),
                List.of("x"), List.of(),
                input -> (Integer) input.get("x") * 2);
        reg.register(action);

        var result = reg.get("calc").execute(Map.of()); // missing "x"
        assertFalse(result.success());
        assertTrue(result.error().contains("precondition"));
    }

    @Test
    void executeFailure() {
        var reg = new ActionRegistry();
        var action = new ActionRegistry.Action(
                "fail",
                new ActionRegistry.IoSchema(Map.of(), String.class),
                List.of(), List.of(),
                input -> { throw new RuntimeException("boom"); });
        reg.register(action);

        var result = reg.get("fail").execute(Map.of());
        assertFalse(result.success());
        assertEquals("boom", result.error());
    }

    @Test
    void listActions() {
        var reg = new ActionRegistry();
        reg.register(new ActionRegistry.Action("a",
                new ActionRegistry.IoSchema(Map.of(), String.class),
                List.of(), List.of(), input -> "a"));
        reg.register(new ActionRegistry.Action("b",
                new ActionRegistry.IoSchema(Map.of(), String.class),
                List.of(), List.of(), input -> "b"));
        assertEquals(2, reg.list().size());
    }

    @Test
    void unregister() {
        var reg = new ActionRegistry();
        reg.register(new ActionRegistry.Action("a",
                new ActionRegistry.IoSchema(Map.of(), String.class),
                List.of(), List.of(), input -> "a"));
        reg.unregister("a");
        assertFalse(reg.has("a"));
    }
}
