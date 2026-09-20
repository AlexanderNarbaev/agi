package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W579 — Tests for MCTS Planner.
 */
class MCTSPlannerTest {

    @Test
    void testSimplePlanning() {
        MCTSPlanner planner = new MCTSPlanner(100, 1.41);

        // Simple problem: maximize value by choosing actions
        MCTSPlanner.PlanningProblem problem = new MCTSPlanner.PlanningProblem() {
            @Override
            public List<String> getActions(String state) {
                return List.of("left", "right");
            }

            @Override
            public String applyAction(String state, String action) {
                return state + "." + action;
            }

            @Override
            public double evaluate(String state) {
                // Reward for reaching "right" actions
                return state.chars().filter(c -> c == 'r').count() * 1.0;
            }

            @Override
            public boolean isTerminal(String state) {
                return state.split("\\.").length > 3;
            }
        };

        MCTSPlanner.PlanResult result = planner.plan("start", problem);
        assertNotNull(result.actions());
        assertTrue(result.totalSimulations() > 0);
    }

    @Test
    void testTerminalState() {
        MCTSPlanner planner = new MCTSPlanner(50, 1.41);

        MCTSPlanner.PlanningProblem problem = new MCTSPlanner.PlanningProblem() {
            @Override
            public List<String> getActions(String state) {
                if (state.equals("goal")) return List.of();
                return List.of("move");
            }

            @Override
            public String applyAction(String state, String action) {
                return "goal";
            }

            @Override
            public double evaluate(String state) {
                return state.equals("goal") ? 1.0 : 0.0;
            }

            @Override
            public boolean isTerminal(String state) {
                return state.equals("goal");
            }
        };

        MCTSPlanner.PlanResult result = planner.plan("start", problem);
        assertEquals(1.0, result.expectedReward(), 0.01);
    }

    @Test
    void testMultipleActions() {
        MCTSPlanner planner = new MCTSPlanner(200, 1.41);

        MCTSPlanner.PlanningProblem problem = new MCTSPlanner.PlanningProblem() {
            @Override
            public List<String> getActions(String state) {
                int depth = state.split("\\.").length - 1;
                if (depth >= 3) return List.of();
                return List.of("A", "B", "C");
            }

            @Override
            public String applyAction(String state, String action) {
                return state + "." + action;
            }

            @Override
            public double evaluate(String state) {
                // Reward for choosing C actions
                return state.chars().filter(c -> c == 'C').count() * 2.0;
            }

            @Override
            public boolean isTerminal(String state) {
                return state.split("\\.").length > 3;
            }
        };

        MCTSPlanner.PlanResult result = planner.plan("start", problem);
        assertTrue(result.actions().size() > 0);
        assertTrue(result.expectedReward() > 0);
    }
}
