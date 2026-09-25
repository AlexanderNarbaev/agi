package io.matrix.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpigotBridgeTest {

    @Test
    void testCreateBridge() {
        SpigotBridge bridge = new SpigotBridge("localhost", 25575);
        assertNotNull(bridge);
        assertEquals("localhost", bridge.getServerHost());
        assertEquals(25575, bridge.getServerPort());
    }

    @Test
    void testNavigateCommand() {
        SpigotBridge bridge = new SpigotBridge("localhost", 25575);
        SpigotBridge.MinecraftResponse response = bridge.sendCommand(
            SpigotBridge.Task.NAVIGATE, Map.of("target", "village"));
        assertTrue(response.success());
        assertTrue(response.result().contains("village"));
    }

    @Test
    void testCraftCommand() {
        SpigotBridge bridge = new SpigotBridge("localhost", 25575);
        SpigotBridge.MinecraftResponse response = bridge.sendCommand(
            SpigotBridge.Task.CRAFT, Map.of("item", "diamond_sword"));
        assertTrue(response.success());
        assertTrue(response.result().contains("diamond_sword"));
    }

    @Test
    void testBuildCommand() {
        SpigotBridge bridge = new SpigotBridge("localhost", 25575);
        SpigotBridge.MinecraftResponse response = bridge.sendCommand(
            SpigotBridge.Task.BUILD, Map.of("location", "100,64,200"));
        assertTrue(response.success());
    }

    @Test
    void testSurviveCommand() {
        SpigotBridge bridge = new SpigotBridge("localhost", 25575);
        SpigotBridge.MinecraftResponse response = bridge.sendCommand(
            SpigotBridge.Task.SURVIVE, Map.of("duration", "60s"));
        assertTrue(response.success());
        assertTrue(response.result().contains("60s"));
    }

    @Test
    void testCommandHistory() {
        SpigotBridge bridge = new SpigotBridge("localhost", 25575);
        bridge.sendCommand(SpigotBridge.Task.NAVIGATE, Map.of("target", "A"));
        bridge.sendCommand(SpigotBridge.Task.CRAFT, Map.of("item", "B"));
        bridge.sendCommand(SpigotBridge.Task.BUILD, Map.of("location", "C"));
        assertEquals(3, bridge.getCommandHistory().size());
    }

    @Test
    void testLatencyTracking() {
        SpigotBridge bridge = new SpigotBridge("localhost", 25575);
        SpigotBridge.MinecraftResponse response = bridge.sendCommand(
            SpigotBridge.Task.NAVIGATE, Map.of("target", "X"));
        assertTrue(response.latencyMs() >= 0);
    }
}
