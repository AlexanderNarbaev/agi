package io.matrix.mcts;

import io.matrix.memory.HierarchicalMemory;
import io.matrix.memory.HierarchicalMemory.Level;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LatsReflectorTest {

    private HierarchicalMemory memory;
    private LatsReflector reflector;
    private final Random rng = new Random(42);

    @BeforeEach
    void setUp() {
        memory = new HierarchicalMemory(100);
        reflector = new LatsReflector(memory, 0.3, rng);
    }

    // ---- Construction ----

    @Test
    void shouldCreateWithDefaultGenerator() {
        assertThat(reflector.failureThreshold()).isCloseTo(0.3, within(1e-10));
        assertThat(reflector.reflectionCount()).isZero();
    }

    @Test
    void shouldCreateWithCustomGenerator() {
        LatsReflector custom = new LatsReflector(memory,
                (parent, current) -> "custom reflection", 0.5, rng);
        assertThat(custom.failureThreshold()).isCloseTo(0.5, within(1e-10));
    }

    @Test
    void shouldThrowOnNullMemory() {
        assertThatThrownBy(() -> new LatsReflector(null, 0.3, rng))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- Reflection on failure ----

    @Test
    void shouldReflectOnFailedBranch() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        // Simulate low reward
        child.update(0.1); // below threshold 0.3

        reflector.reflect(child, root);

        assertThat(child.hasReflection()).isTrue();
        assertThat(child.status()).isEqualTo(LatsNode.BranchStatus.FAILURE);
        assertThat(reflector.reflectionCount()).isEqualTo(1);
    }

    @Test
    void shouldNotReflectOnUnvisitedNode() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        // No visits — should not reflect
        reflector.reflect(child, root);

        assertThat(child.hasReflection()).isFalse();
        assertThat(child.status()).isEqualTo(LatsNode.BranchStatus.PENDING);
    }

    @Test
    void shouldReflectOnSuccessfulBranchAfterEnoughVisits() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(true), MctsAction.singleTreeActions());
        root.addChild(child);

        // Simulate high reward with enough visits
        child.update(0.9);
        child.update(0.8);
        child.update(0.85);

        reflector.reflect(child, root);

        assertThat(child.hasReflection()).isTrue();
        assertThat(child.status()).isEqualTo(LatsNode.BranchStatus.SUCCESS);
        assertThat(reflector.reflectionCount()).isEqualTo(1);
    }

    @Test
    void shouldNotReflectOnSuccessfulBranchWithFewVisits() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(true), MctsAction.singleTreeActions());
        root.addChild(child);

        // High reward but only 1 visit — not enough for success reflection
        child.update(0.9);

        reflector.reflect(child, root);

        assertThat(child.hasReflection()).isFalse();
    }

    // ---- Reflection content ----

    @Test
    void failureReflectionShouldContainStructuralAnalysis() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        DecisionTree complexState = new Split(0,
                new Split(1, new Leaf(true), new Leaf(false)),
                new Split(2, new Leaf(false), new Leaf(true)));
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                complexState, MctsAction.singleTreeActions());
        root.addChild(child);

        child.update(0.1);
        reflector.reflect(child, root);

        assertThat(child.reflection()).contains("Tree:");
        assertThat(child.reflection()).contains("nodes=");
    }

    @Test
    void failureReflectionShouldIncludeAncestorContext() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        root.setReflection("Root insight about pruning");

        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        child.update(0.1);
        reflector.reflect(child, root);

        assertThat(child.reflection()).contains("Previous insights");
        assertThat(child.reflection()).contains("Root insight about pruning");
    }

    // ---- Memory storage ----

    @Test
    void reflectionsShouldBeStoredInMemory() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        child.update(0.1);
        reflector.reflect(child, root);

        List<String> failures = reflector.retrieveRecentFailures(10);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).contains("Tree:");
    }

    @Test
    void reflectionsShouldBeSearchable() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        child.update(0.1);
        reflector.reflect(child, root);

        List<String> results = reflector.retrieveRelevantReflections("Tree", 10);
        assertThat(results).isNotEmpty();
    }

    @Test
    void multipleReflectionsShouldAccumulate() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());

        for (int i = 0; i < 5; i++) {
            LatsNode child = new LatsNode(root,
                    MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                    new Leaf(false), MctsAction.singleTreeActions());
            root.addChild(child);
            child.update(0.1);
            reflector.reflect(child, root);
        }

        assertThat(reflector.reflectionCount()).isEqualTo(5);
        assertThat(reflector.retrieveRecentFailures(10)).hasSize(5);
    }

    // ---- Custom reflection generator ----

    @Test
    void customGeneratorShouldBeUsed() {
        LatsReflector custom = new LatsReflector(memory,
                (parent, current) -> "Custom: mutation changed tree structure",
                0.3, rng);

        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        child.update(0.1);
        custom.reflect(child, root);

        assertThat(child.reflection()).contains("Custom: mutation changed tree structure");
    }

    // ---- Reflection domain ----

    @Test
    void reflectionsShouldUseCorrectDomain() {
        LatsNode root = new LatsNode(new Leaf(true), MctsAction.singleTreeActions());
        LatsNode child = new LatsNode(root,
                MctsAction.of(MctsAction.ActionType.FLIP_LEAF),
                new Leaf(false), MctsAction.singleTreeActions());
        root.addChild(child);

        child.update(0.1);
        reflector.reflect(child, root);

        var entries = memory.entriesInDomain(LatsReflector.REFLECTION_DOMAIN);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).tags()).contains(LatsReflector.TAG_FAILURE);
    }
}
