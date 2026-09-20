package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W585 — Tests for GridWorld Bridge.
 */
class GridWorldBridgeTest {

    @Test
    void testStep() {
        GridWorldBridge bridge = new GridWorldBridge(5, 5, 42);
        bridge.getState().setAgent(2, 2);

        GridWorldBridge.StepResult result = bridge.step(GridWorldBridge.Action.NORTH);

        assertNotNull(result);
        assertFalse(result.done());
        assertEquals(2, bridge.getState().getAgentX());
        assertEquals(1, bridge.getState().getAgentY());
    }

    @Test
    void testWallCollision() {
        GridWorldBridge bridge = new GridWorldBridge(5, 5, 42);
        bridge.getState().setAgent(0, 0);

        // Try to move into wall (out of bounds)
        bridge.step(GridWorldBridge.Action.WEST);

        assertEquals(0, bridge.getState().getAgentX());
        assertEquals(0, bridge.getState().getAgentY());
    }

    @Test
    void testFoodCollection() {
        GridWorldBridge bridge = new GridWorldBridge(5, 5, 42);
        bridge.getState().setAgent(2, 2);
        bridge.getState().setCell(2, 1, GridWorldBridge.CellType.FOOD);

        GridWorldBridge.StepResult result = bridge.step(GridWorldBridge.Action.NORTH);

        // Food gives reward
        assertTrue(result.reward() > -0.5);
        // Food was consumed
        assertEquals(GridWorldBridge.CellType.EMPTY, bridge.getState().getCell(2, 1));
    }

    @Test
    void testGoalReached() {
        GridWorldBridge bridge = new GridWorldBridge(5, 5, 42);
        bridge.getState().setAgent(2, 2);
        bridge.getState().setCell(2, 1, GridWorldBridge.CellType.GOAL);

        GridWorldBridge.StepResult result = bridge.step(GridWorldBridge.Action.NORTH);

        assertTrue(result.done());
        assertTrue(result.reward() > 5);
    }

    @Test
    void testEnergyDepletion() {
        GridWorldBridge bridge = new GridWorldBridge(5, 5, 42);
        bridge.getState().setAgent(2, 2);

        // Take many steps to deplete energy
        for (int i = 0; i < 200; i++) {
            bridge.step(GridWorldBridge.Action.STAY);
            if (bridge.getState().isDone()) break;
        }

        assertTrue(bridge.getState().isDone());
    }

    @Test
    void testAvailableActions() {
        GridWorldBridge bridge = new GridWorldBridge(5, 5, 42);
        bridge.getState().setAgent(2, 2);

        List<GridWorldBridge.Action> actions = bridge.getAvailableActions();
        assertTrue(actions.contains(GridWorldBridge.Action.NORTH));
        assertTrue(actions.contains(GridWorldBridge.Action.SOUTH));
        assertTrue(actions.contains(GridWorldBridge.Action.EAST));
        assertTrue(actions.contains(GridWorldBridge.Action.WEST));
        assertTrue(actions.contains(GridWorldBridge.Action.STAY));
    }

    @Test
    void testGenerateRandomLevel() {
        GridWorldBridge bridge = new GridWorldBridge(10, 10, 42);
        bridge.generateRandomLevel(10, 5, 3);

        boolean hasWall = false, hasFood = false, hasHazard = false, hasGoal = false;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                switch (bridge.getState().getCell(x, y)) {
                    case WALL -> hasWall = true;
                    case FOOD -> hasFood = true;
                    case HAZARD -> hasHazard = true;
                    case GOAL -> hasGoal = true;
                }
            }
        }

        assertTrue(hasWall);
        assertTrue(hasFood);
        assertTrue(hasHazard);
        assertTrue(hasGoal);
    }
}
