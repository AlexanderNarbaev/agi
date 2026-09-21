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

class LatsNodeTest {

    private final Random rng = new Random(42);

    // ---- Construction ----

    @Test
    void rootShouldInheritFromMctsNode() {
        DecisionTree state = new Leaf(true);
        LatsNode root = new LatsNode(state, MctsAction.singleTreeActions());

        assertThat(root).isInstanceOf(MctsNode.class);
        assertThat(root.parent()).isNull();
        assertThat(root.isRoot()).isTrue();
    }

    @Test
    void childShouldHaveParentLink() {
        DecisionTree rootState = new Split(0, new Leaf(false), new Leaf(true));
        DecisionTree childState = new Leaf(true);
        MctsAction action = MctsAction.of(MctsAction.ActionType.FLIP_LEAF);

        LatsNode root = new LatsNode(rootState, MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root, action, childState, MctsAction.singleTreeActions());
        root.addChild(child);

        assertThat(child.parent()).isSameAs(root);
        assertThat(child.action()).isSameAs(action);
        assertThat(root.children()).containsExactly(child);
    }

    // ---- Reflection ----

    @Test
    void initialStateShouldHaveNoReflection() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());

        assertThat(node.hasReflection()).isFalse();
        assertThat(node.reflection()).isEmpty();
    }

    @Test
    void setReflectionShouldStoreText() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        node.setReflection("Branch failed: tree too deep");

        assertThat(node.hasReflection()).isTrue();
        assertThat(node.reflection()).isEqualTo("Branch failed: tree too deep");
    }

    @Test
    void setReflectionShouldThrowOnNull() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        assertThatThrownBy(() -> node.setReflection(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void reflectionShouldBeInheritedByChildren() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        root.setReflection("Root insight");

        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());

        assertThat(child.ancestorReflections()).containsExactly("Root insight");
        assertThat(child.fullPathReflections()).containsExactly("Root insight");
    }

    @Test
    void fullPathReflectionsShouldIncludeCurrentNode() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        root.setReflection("Root insight");

        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        child.setReflection("Child insight");

        assertThat(child.fullPathReflections()).containsExactly("Root insight", "Child insight");
    }

    @Test
    void ancestorReflectionsShouldChain() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        root.setReflection("R1");

        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        child.setReflection("C1");

        LatsNode grandchild = new LatsNode(child,
                MctsAction.of(MctsAction.ActionType.PRUNE_TREE),
                new Leaf(true), MctsAction.singleTreeActions());

        assertThat(grandchild.ancestorReflections()).containsExactly("R1", "C1");
    }

    // ---- Branch Status ----

    @Test
    void initialStatusShouldBePending() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        assertThat(node.status()).isEqualTo(LatsNode.BranchStatus.PENDING);
    }

    @Test
    void setStatusShouldUpdate() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        node.setStatus(LatsNode.BranchStatus.FAILURE);
        assertThat(node.status()).isEqualTo(LatsNode.BranchStatus.FAILURE);
    }

    @Test
    void setStatusShouldThrowOnNull() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        assertThatThrownBy(() -> node.setStatus(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isExplorableShouldReturnTrueForPendingAndSuccess() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());

        node.setStatus(LatsNode.BranchStatus.PENDING);
        assertThat(node.isExplorable()).isTrue();

        node.setStatus(LatsNode.BranchStatus.SUCCESS);
        assertThat(node.isExplorable()).isTrue();
    }

    @Test
    void isExplorableShouldReturnFalseForFailureAndPruned() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());

        node.setStatus(LatsNode.BranchStatus.FAILURE);
        assertThat(node.isExplorable()).isFalse();

        node.setStatus(LatsNode.BranchStatus.PRUNED);
        assertThat(node.isExplorable()).isFalse();
    }

    // ---- Value Score ----

    @Test
    void initialValueScoreShouldBeZero() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        assertThat(node.valueScore()).isZero();
    }

    @Test
    void setValueScoreShouldStore() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        node.setValueScore(0.75);
        assertThat(node.valueScore()).isCloseTo(0.75, within(1e-10));
    }

    @Test
    void setValueScoreShouldRejectOutOfRange() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());

        assertThatThrownBy(() -> node.setValueScore(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> node.setValueScore(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Prior ----

    @Test
    void initialPriorShouldBeOne() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        assertThat(node.prior()).isCloseTo(1.0, within(1e-10));
    }

    @Test
    void setPriorShouldStore() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        node.setPrior(0.5);
        assertThat(node.prior()).isCloseTo(0.5, within(1e-10));
    }

    @Test
    void setPriorShouldRejectOutOfRange() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());

        assertThatThrownBy(() -> node.setPrior(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> node.setPrior(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- PUCT ----

    @Test
    void unvisitedNodeShouldReturnMaxPuct() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        assertThat(node.puct(1.0)).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void puctShouldFollowFormula() {
        DecisionTree state = new Leaf(true);
        List<MctsAction> actions = MctsAction.singleTreeActions();

        LatsNode root = new LatsNode(state, actions);
        root.update(1.0);
        root.update(1.0); // root has 2 visits

        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF), state, actions);
        root.addChild(child);
        child.update(0.8); // child has 1 visit, mean=0.8
        child.setPrior(0.6);

        // PUCT = 0.8 + 1.0 * 0.6 * sqrt(2) / (1 + 1) = 0.8 + 0.6 * 1.414 / 2 = 0.8 + 0.424 = 1.224
        double expected = 0.8 + 1.0 * 0.6 * Math.sqrt(2.0) / (1.0 + 1.0);
        assertThat(child.puct(1.0)).isCloseTo(expected, within(1e-6));
    }

    // ---- Best LATS Child ----

    @Test
    void bestLatsChildShouldReturnHighestPuct() {
        DecisionTree state = new Leaf(true);
        List<MctsAction> actions = MctsAction.singleTreeActions();

        LatsNode root = new LatsNode(state, actions);
        for (int i = 0; i < 10; i++) root.update(0.5);

        LatsNode child1 = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF), state, actions);
        LatsNode child2 = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.PRUNE_TREE), state, actions);

        root.addChild(child1);
        root.addChild(child2);

        // child1: high reward, high prior
        for (int i = 0; i < 5; i++) child1.update(0.9);
        child1.setPrior(0.8);

        // child2: low reward, low prior
        for (int i = 0; i < 3; i++) child2.update(0.3);
        child2.setPrior(0.2);

        assertThat(root.bestLatsChild(1.0)).isSameAs(child1);
    }

    @Test
    void bestLatsChildShouldThrowWhenEmpty() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        assertThatThrownBy(() -> node.bestLatsChild(1.0))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- ToString ----

    @Test
    void toStringShouldContainLatsFields() {
        LatsNode node = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        node.setValueScore(0.75);
        node.setPrior(0.6);
        node.setStatus(LatsNode.BranchStatus.SUCCESS);

        String str = node.toString();
        assertThat(str).contains("LatsNode");
        assertThat(str).contains("valueScore=0.75");
        assertThat(str).contains("prior=0.60");
        assertThat(str).contains("SUCCESS");
    }
}
