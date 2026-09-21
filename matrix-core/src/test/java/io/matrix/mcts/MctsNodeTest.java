package io.matrix.mcts;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MctsNodeTest {

    private final Random rng = new Random(42);

    // ---- Construction ----

    @Test
    void rootShouldHaveNoParent() {
        DecisionTree state = new Leaf(true);
        List<MctsAction> actions = MctsAction.singleTreeActions();
        MctsNode root = new MctsNode(null, null, state, actions);

        assertThat(root.parent()).isNull();
        assertThat(root.action()).isNull();
        assertThat(root.isRoot()).isTrue();
    }

    @Test
    void childShouldHaveParentLink() {
        DecisionTree rootState = new Split(0, new Leaf(false), new Leaf(true));
        DecisionTree childState = new Leaf(true);
        List<MctsAction> actions = MctsAction.singleTreeActions();
        MctsAction action = MctsAction.of(MctsAction.ActionType.FLIP_LEAF);

        MctsNode root = new MctsNode(null, null, rootState, actions);
        MctsNode child = new MctsNode(root, action, childState, actions);
        root.addChild(child);

        assertThat(child.parent()).isSameAs(root);
        assertThat(child.action()).isSameAs(action);
        assertThat(child.isRoot()).isFalse();
        assertThat(root.children()).containsExactly(child);
    }

    @Test
    void initialStateShouldHaveZeroVisitsAndReward() {
        DecisionTree state = new Leaf(false);
        MctsNode node = new MctsNode(null, null, state, MctsAction.singleTreeActions());

        assertThat(node.visitCount()).isZero();
        assertThat(node.totalReward()).isZero();
        assertThat(node.meanReward()).isZero();
    }

    @Test
    void shouldTrackState() {
        DecisionTree state = new Split(0, new Leaf(true), new Leaf(false));
        MctsNode node = new MctsNode(null, null, state, MctsAction.singleTreeActions());

        assertThat(node.state()).isSameAs(state);
    }

    // ---- UCB1 ----

    @Test
    void unvisitedNodeShouldReturnMaxUcb1() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        assertThat(node.ucb1()).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void ucb1ShouldFollowFormula() {
        DecisionTree state = new Leaf(true);
        List<MctsAction> actions = MctsAction.singleTreeActions();

        MctsNode root = new MctsNode(null, null, state, actions);
        root.update(1.0); // root visited once

        MctsNode child = new MctsNode(root, MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                state, actions);
        root.addChild(child);
        child.update(0.8); // child visited once with reward 0.8

        // UCB1 = 0.8 + sqrt(2) * sqrt(ln(1) / 1) = 0.8 + 0 = 0.8
        // Wait, ln(1) = 0, so UCB1 = 0.8
        // Let's give root 2 visits instead
        root.update(0.5); // root now has 2 visits

        // UCB1 = 0.8 + sqrt(2) * sqrt(ln(2) / 1) ≈ 0.8 + 1.177 ≈ 1.977
        double expected = 0.8 + Math.sqrt(2.0) * Math.sqrt(Math.log(2.0) / 1.0);
        assertThat(child.ucb1()).isCloseTo(expected, within(1e-10));
    }

    @Test
    void ucb1WithCustomConstant() {
        DecisionTree state = new Leaf(true);
        MctsNode root = new MctsNode(null, null, state, MctsAction.singleTreeActions());
        root.update(1.0);
        root.update(1.0);

        MctsNode child = new MctsNode(root, MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                state, MctsAction.singleTreeActions());
        root.addChild(child);
        child.update(0.6);

        // UCB1 with C=1.0 = 0.6 + 1.0 * sqrt(ln(2)/1) ≈ 0.6 + 0.8326 ≈ 1.4326
        double expected = 0.6 + 1.0 * Math.sqrt(Math.log(2.0) / 1.0);
        assertThat(child.ucb1(1.0)).isCloseTo(expected, within(1e-10));
    }

    // ---- Update ----

    @Test
    void updateShouldIncrementVisitsAndAccumulateReward() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());

        node.update(0.5);
        assertThat(node.visitCount()).isEqualTo(1);
        assertThat(node.totalReward()).isCloseTo(0.5, within(1e-10));
        assertThat(node.meanReward()).isCloseTo(0.5, within(1e-10));

        node.update(0.3);
        assertThat(node.visitCount()).isEqualTo(2);
        assertThat(node.totalReward()).isCloseTo(0.8, within(1e-10));
        assertThat(node.meanReward()).isCloseTo(0.4, within(1e-10));
    }

    // ---- Expansion state ----

    @Test
    void nodeWithActionsShouldNotBeFullyExpanded() {
        List<MctsAction> actions = MctsAction.singleTreeActions();
        MctsNode node = new MctsNode(null, null, new Leaf(true), actions);

        assertThat(node.isFullyExpanded()).isFalse();
        assertThat(node.untriedActionCount()).isEqualTo(actions.size());
    }

    @Test
    void nodeWithNoActionsShouldBeFullyExpanded() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), List.of());

        assertThat(node.isFullyExpanded()).isTrue();
        assertThat(node.untriedActionCount()).isZero();
    }

    @Test
    void nextUntriedActionShouldRemoveFromPool() {
        List<MctsAction> actions = MctsAction.singleTreeActions();
        MctsNode node = new MctsNode(null, null, new Leaf(true), actions);
        int initialSize = actions.size();

        MctsAction first = node.nextUntriedAction();
        assertThat(first).isNotNull();
        assertThat(node.untriedActionCount()).isEqualTo(initialSize - 1);
    }

    @Test
    void nextUntriedActionShouldThrowWhenEmpty() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), List.of());

        assertThatThrownBy(node::nextUntriedAction)
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Children ----

    @Test
    void leafNodeShouldHaveNoChildren() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        assertThat(node.isLeaf()).isTrue();
        assertThat(node.children()).isEmpty();
    }

    @Test
    void nodeWithChildrenShouldNotBeLeaf() {
        MctsNode root = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        MctsNode child = new MctsNode(root, MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        assertThat(root.isLeaf()).isFalse();
        assertThat(root.children()).hasSize(1);
    }

    // ---- Best child ----

    @Test
    void bestChildShouldReturnHighestUcb1() {
        DecisionTree state = new Leaf(true);
        List<MctsAction> actions = MctsAction.singleTreeActions();

        MctsNode root = new MctsNode(null, null, state, actions);
        // Give root enough visits so ln(N) is meaningful
        for (int i = 0; i < 10; i++) root.update(0.5);

        MctsNode child1 = new MctsNode(root, MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                state, actions);
        MctsNode child2 = new MctsNode(root, MctsAction.of(MctsAction.ActionType.PRUNE_TREE),
                state, actions);

        root.addChild(child1);
        root.addChild(child2);

        // child1: 5 visits, mean=0.9
        for (int i = 0; i < 5; i++) child1.update(0.9);
        // child2: 3 visits, mean=0.5
        for (int i = 0; i < 3; i++) child2.update(0.5);

        // child1 has higher mean, should be selected
        assertThat(root.bestChild()).isSameAs(child1);
    }

    @Test
    void bestChildShouldThrowWhenEmpty() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        assertThatThrownBy(node::bestChild)
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Most visited child ----

    @Test
    void mostVisitedChildShouldReturnHighestVisitCount() {
        DecisionTree state = new Leaf(true);
        List<MctsAction> actions = MctsAction.singleTreeActions();

        MctsNode root = new MctsNode(null, null, state, actions);

        MctsNode child1 = new MctsNode(root, MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                state, actions);
        MctsNode child2 = new MctsNode(root, MctsAction.of(MctsAction.ActionType.PRUNE_TREE),
                state, actions);

        root.addChild(child1);
        root.addChild(child2);

        // child1: 3 visits but low reward
        for (int i = 0; i < 3; i++) child1.update(0.1);
        // child2: 10 visits with high reward
        for (int i = 0; i < 10; i++) child2.update(0.9);

        assertThat(root.mostVisitedChild()).isSameAs(child2);
    }

    @Test
    void mostVisitedChildShouldThrowWhenEmpty() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        assertThatThrownBy(node::mostVisitedChild)
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- Depth ----

    @Test
    void rootShouldHaveDepthZero() {
        MctsNode root = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        assertThat(root.depth()).isZero();
    }

    @Test
    void childShouldHaveCorrectDepth() {
        MctsNode root = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        MctsNode child = new MctsNode(root, MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        MctsNode grandchild = new MctsNode(child, MctsAction.of(MctsAction.ActionType.PRUNE_TREE),
                new Leaf(true), MctsAction.singleTreeActions());
        child.addChild(grandchild);

        assertThat(root.depth()).isZero();
        assertThat(child.depth()).isEqualTo(1);
        assertThat(grandchild.depth()).isEqualTo(2);
    }

    // ---- ToString ----

    @Test
    void toStringShouldContainKeyInfo() {
        MctsNode node = new MctsNode(null, null, new Leaf(true), MctsAction.singleTreeActions());
        node.update(0.5);

        String str = node.toString();
        assertThat(str).contains("visits=1");
        assertThat(str).contains("reward=0.50");
    }

    // ---- Exploration constant ----

    @Test
    void explorationConstantShouldBeSqrt2() {
        assertThat(MctsNode.EXPLORATION_CONSTANT)
                .isCloseTo(Math.sqrt(2.0), within(1e-10));
    }
}
