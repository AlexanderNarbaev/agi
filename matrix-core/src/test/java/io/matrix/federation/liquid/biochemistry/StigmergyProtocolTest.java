package io.matrix.federation.liquid.biochemistry;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StigmergyProtocolTest {

    @Test
    void testDepositAndSense() {
        StigmergyProtocol stigmergy = StigmergyProtocol.createDefault();
        stigmergy.deposit("node-1", "topic-A", StigmergyProtocol.PheromoneType.EXPLORATION, 0.8, null);
        var signals = stigmergy.sense("topic-A");
        assertFalse(signals.isEmpty());
        assertEquals("node-1", signals.get(0).sourceNodeId());
    }

    @Test
    void testSignalAggregation() {
        StigmergyProtocol stigmergy = StigmergyProtocol.createDefault();
        stigmergy.deposit("node-1", "topic-A", StigmergyProtocol.PheromoneType.EXPLORATION, 0.5, null);
        stigmergy.deposit("node-2", "topic-A", StigmergyProtocol.PheromoneType.EXPLORATION, 0.3, null);
        var signal = stigmergy.getSignal("topic-A");
        assertEquals(2, signal.signalCount());
        assertEquals(0.8, signal.aggregatedStrength(), 0.01);
    }

    @Test
    void testPheromoneDecay() {
        StigmergyProtocol stigmergy = new StigmergyProtocol(0.5, 0.01);
        stigmergy.deposit("node-1", "topic-A", StigmergyProtocol.PheromoneType.EXPLORATION, 0.8, null);
        stigmergy.tick();
        var signals = stigmergy.sense("topic-A");
        assertFalse(signals.isEmpty());
        assertTrue(signals.get(0).strength() < 0.8);
    }

    @Test
    void testPheromoneRemoval() {
        StigmergyProtocol stigmergy = new StigmergyProtocol(0.9, 0.5);
        stigmergy.deposit("node-1", "topic-A", StigmergyProtocol.PheromoneType.EXPLORATION, 0.6, null);
        stigmergy.tick();
        var signals = stigmergy.sense("topic-A");
        assertTrue(signals.isEmpty());
    }

    @Test
    void testHotTopics() {
        StigmergyProtocol stigmergy = StigmergyProtocol.createDefault();
        stigmergy.deposit("node-1", "cold", StigmergyProtocol.PheromoneType.EXPLORATION, 0.1, null);
        stigmergy.deposit("node-2", "hot", StigmergyProtocol.PheromoneType.REWARD, 0.9, null);
        stigmergy.deposit("node-3", "hot", StigmergyProtocol.PheromoneType.REWARD, 0.8, null);
        var hotTopics = stigmergy.getHotTopics(2);
        assertEquals("hot", hotTopics.get(0));
    }

    @Test
    void testDominantType() {
        StigmergyProtocol stigmergy = StigmergyProtocol.createDefault();
        stigmergy.deposit("node-1", "topic-A", StigmergyProtocol.PheromoneType.EXPLORATION, 0.3, null);
        stigmergy.deposit("node-2", "topic-A", StigmergyProtocol.PheromoneType.DANGER, 0.9, null);
        var signal = stigmergy.getSignal("topic-A");
        assertEquals(StigmergyProtocol.PheromoneType.DANGER, signal.type());
    }

    @Test
    void testClusterFormation() {
        StigmergyProtocol stigmergy = StigmergyProtocol.createDefault();
        for (int i = 0; i < 10; i++) {
            stigmergy.deposit("node-" + i, "emerging-pattern",
                    StigmergyProtocol.PheromoneType.EXPLORATION, 0.5 + Math.random() * 0.5, null);
        }
        var signal = stigmergy.getSignal("emerging-pattern");
        assertTrue(signal.signalCount() >= 10);
        assertTrue(signal.aggregatedStrength() > 5.0);
        var hotTopics = stigmergy.getHotTopics(1);
        assertEquals("emerging-pattern", hotTopics.get(0));
    }

    @Test
    void testClear() {
        StigmergyProtocol stigmergy = StigmergyProtocol.createDefault();
        stigmergy.deposit("node-1", "topic-A", StigmergyProtocol.PheromoneType.EXPLORATION, 0.5, null);
        stigmergy.clear();
        assertEquals(0, stigmergy.getTotalCount());
    }
}
