package io.matrix.swarm;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmergenceMonitorTest {

    @Test
    void testCreateMonitor() {
        EmergenceMonitor monitor = new EmergenceMonitor();
        assertNotNull(monitor);
        assertEquals(0, monitor.getKnownStrategyCount());
    }

    @Test
    void testNovelBehaviorDetected() {
        EmergenceMonitor monitor = new EmergenceMonitor();
        List<String> participants = Arrays.asList("node-1", "node-2", "node-3");
        boolean novel = monitor.recordBehavior("cooperative_pathfinding", participants);
        assertTrue(novel, "Should detect novel behavior");
        assertTrue(monitor.getInsights().size() >= 1);
    }

    @Test
    void testRepeatBehaviorNotNovel() {
        EmergenceMonitor monitor = new EmergenceMonitor();
        List<String> participants = Arrays.asList("n1","n2","n3","n4","n5");
        boolean firstTime = monitor.recordBehavior("known", participants);
        assertTrue(firstTime, "First time should be novel");
        boolean secondTime = monitor.recordBehavior("known", participants);
        assertFalse(secondTime, "Repeat behavior should not be novel");
        assertEquals(1, monitor.getInsights().size());
    }

    @Test
    void testHarmfulBehaviorFiltered() {
        EmergenceMonitor monitor = new EmergenceMonitor();
        List<String> participants = Arrays.asList("node-1");
        boolean novel = monitor.recordBehavior("harmful_xx", participants);
        assertFalse(novel, "Harmful behavior should not register as emergent");
    }

    @Test
    void testMultipleInsights() {
        EmergenceMonitor monitor = new EmergenceMonitor();
        monitor.recordBehavior("a", Arrays.asList("n1", "n2", "n3"));
        monitor.recordBehavior("b", Arrays.asList("n1", "n2", "n3"));
        monitor.recordBehavior("c", Arrays.asList("n1", "n2", "n3"));
        assertEquals(3, monitor.getInsights().size());
        assertEquals(3, monitor.getKnownStrategyCount());
    }
}
