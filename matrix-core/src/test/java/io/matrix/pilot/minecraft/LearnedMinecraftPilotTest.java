package io.matrix.pilot.minecraft;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H-005: Learned Minecraft Pilot tests.
 */
class LearnedMinecraftPilotTest {

    @Test
    void trainAndClassify() {
        LearnedMinecraftPilot pilot = new LearnedMinecraftPilot();
        WorldConfig config = new WorldConfig("flat", 42L, 10, 20);
        pilot.initialize(config);

        // Create training data
        List<LearnedMinecraftPilot.EpisodeRecord> episodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            AgentState state = new AgentState(i % 10, 0, i / 10, 10, i);
            Observation obs = new Observation(List.of("stone"), List.of(), 20.0, 6000, false);
            String action = ACTIONS.get(i % ACTIONS.size());
            episodes.add(new LearnedMinecraftPilot.EpisodeRecord(state, obs, action, 1.0));
        }

        // Train
        pilot.train(episodes);
        assertTrue(pilot.isTrained());
        assertEquals(100, pilot.trainingDataCount());

        // Test classification
        AgentState testState = new AgentState(5, 0, 5, 10, 100);
        Observation testObs = new Observation(List.of("stone"), List.of(), 20.0, 6000, false);
        ActionResult result = pilot.step(testState, testObs);
        assertNotNull(result);
        assertNotNull(result.action());
    }

    @Test
    void fitnessComputation() {
        LearnedMinecraftPilot pilot = new LearnedMinecraftPilot();
        WorldConfig config = new WorldConfig("flat", 42L, 10, 20);
        pilot.initialize(config);

        EpisodeHistory history = new EpisodeHistory(100, 10, 5, 1000.0, 2);
        double fitness = pilot.fitness(history);
        // fitness = blocksPlaced + itemsCollected + distanceTraveled/100 - deaths*5
        // = 10 + 5 + 1000/100 - 2*5 = 10 + 5 + 10 - 10 = 15
        assertEquals(15.0, fitness, 0.01);
    }

    private static final List<String> ACTIONS = List.of(
            "MOVE_FORWARD", "MOVE_BACKWARD", "MOVE_LEFT", "MOVE_RIGHT",
            "JUMP", "MINE", "PLACE", "IDLE"
    );
}
