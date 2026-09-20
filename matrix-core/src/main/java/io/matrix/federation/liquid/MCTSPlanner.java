package io.matrix.federation.liquid;

import java.util.*;

/**
 * W579 — MCTS Planner for multi-step tasks.
 *
 * Monte Carlo Tree Search with BIR as rollout policy (fast, deterministic).
 * Used for planning in Deep Understanding mode.
 */
public final class MCTSPlanner {

    private final int maxIterations;
    private final double explorationConstant;

    public MCTSPlanner(int maxIterations, double explorationConstant) {
        this.maxIterations = maxIterations;
        this.explorationConstant = explorationConstant;
    }

    /**
     * A node in the MCTS tree.
     */
    public static final class TreeNode {
        private final String state;
        private final String action;
        private TreeNode parent;
        private final List<TreeNode> children = new ArrayList<>();
        private int visits;
        private double totalReward;
        private final List<String> untriedActions;

        public TreeNode(String state, String action, TreeNode parent, List<String> untriedActions) {
            this.state = state;
            this.action = action;
            this.parent = parent;
            this.untriedActions = new ArrayList<>(untriedActions);
        }

        public double getUCB1(double explorationConstant) {
            if (visits == 0) return Double.MAX_VALUE;
            double exploitation = totalReward / visits;
            double exploration = explorationConstant * Math.sqrt(Math.log(parent.visits) / visits);
            return exploitation + exploration;
        }

        public String getState() { return state; }
        public String getAction() { return action; }
        public int getVisits() { return visits; }
        public double getAverageReward() { return visits > 0 ? totalReward / visits : 0; }
        public List<TreeNode> getChildren() { return children; }
    }

    /**
     * Planning problem interface.
     */
    public interface PlanningProblem {
        /** Get possible actions from a state. */
        List<String> getActions(String state);

        /** Apply an action and return the new state. */
        String applyAction(String state, String action);

        /** Evaluate a state (reward). */
        double evaluate(String state);

        /** Check if state is terminal. */
        boolean isTerminal(String state);

        /** Rollout policy (default: random). */
        default String rolloutPolicy(String state, List<String> actions) {
            return actions.get(0); // Deterministic: first action
        }
    }

    /**
     * Plan a sequence of actions to maximize reward.
     *
     * @param initialState starting state
     * @param problem      planning problem definition
     * @return best action sequence
     */
    public PlanResult plan(String initialState, PlanningProblem problem) {
        TreeNode root = new TreeNode(initialState, null, null, problem.getActions(initialState));

        for (int i = 0; i < maxIterations; i++) {
            // Selection
            TreeNode node = select(root);

            // Expansion
            if (!problem.isTerminal(node.state) && !node.untriedActions.isEmpty()) {
                node = expand(node, problem);
            }

            // Simulation (rollout)
            double reward = simulate(node, problem);

            // Backpropagation
            backpropagate(node, reward);
        }

        // Extract best path
        List<String> bestActions = extractBestPath(root);
        double bestReward = root.getAverageReward();

        return new PlanResult(bestActions, bestReward, root.getVisits());
    }

    public record PlanResult(List<String> actions, double expectedReward, int totalSimulations) {}

    private TreeNode select(TreeNode node) {
        while (!node.children.isEmpty() && node.untriedActions.isEmpty()) {
            TreeNode best = null;
            double bestUCB1 = Double.NEGATIVE_INFINITY;

            for (TreeNode child : node.children) {
                double ucb1 = child.getUCB1(explorationConstant);
                if (ucb1 > bestUCB1) {
                    bestUCB1 = ucb1;
                    best = child;
                }
            }
            node = best;
        }
        return node;
    }

    private TreeNode expand(TreeNode node, PlanningProblem problem) {
        String action = node.untriedActions.remove(0);
        String newState = problem.applyAction(node.state, action);
        List<String> newActions = problem.getActions(newState);

        TreeNode child = new TreeNode(newState, action, node, newActions);
        node.children.add(child);
        return child;
    }

    private double simulate(TreeNode node, PlanningProblem problem) {
        String state = node.state;
        int maxDepth = 10;

        for (int i = 0; i < maxDepth && !problem.isTerminal(state); i++) {
            List<String> actions = problem.getActions(state);
            if (actions.isEmpty()) break;

            String action = problem.rolloutPolicy(state, actions);
            state = problem.applyAction(state, action);
        }

        return problem.evaluate(state);
    }

    private void backpropagate(TreeNode node, double reward) {
        while (node != null) {
            node.visits++;
            node.totalReward += reward;
            node = node.parent;
        }
    }

    private List<String> extractBestPath(TreeNode root) {
        List<String> actions = new ArrayList<>();
        TreeNode current = root;

        while (!current.children.isEmpty()) {
            TreeNode best = null;
            double bestReward = Double.NEGATIVE_INFINITY;

            for (TreeNode child : current.children) {
                if (child.getAverageReward() > bestReward) {
                    bestReward = child.getAverageReward();
                    best = child;
                }
            }

            if (best != null) {
                actions.add(best.action);
                current = best;
            } else {
                break;
            }
        }

        return actions;
    }
}
