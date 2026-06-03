package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChromosomeTest {

    @Test
    void shouldCreateWithTree() {
        DecisionTree tree = DecisionTree.random(4, 2, new Random(42));

        Chromosome c = Chromosome.of(tree);

        assertThat(c.uuid()).isNotNull();
        assertThat(c.generation()).isEqualTo(0);
        assertThat(c.tree()).isEqualTo(tree);
        assertThat(c.fitness()).isEqualTo(0);
    }

    @Test
    void shouldCreateWithFullParams() {
        UUID uuid = UUID.randomUUID();
        DecisionTree tree = DecisionTree.random(4, 2, new Random(42));

        Chromosome c = Chromosome.of(uuid, 5, tree, 100);

        assertThat(c.uuid()).isEqualTo(uuid);
        assertThat(c.generation()).isEqualTo(5);
        assertThat(c.tree()).isEqualTo(tree);
        assertThat(c.fitness()).isEqualTo(100);
    }

    @Test
    void shouldCreateCopyWithNewTree() {
        DecisionTree original = DecisionTree.random(4, 2, new Random(42));
        DecisionTree newTree = DecisionTree.random(4, 2, new Random(7));
        Chromosome c = Chromosome.of(original);

        Chromosome mutated = c.withTree(newTree);

        assertThat(mutated.uuid()).isEqualTo(c.uuid());
        assertThat(mutated.generation()).isEqualTo(c.generation() + 1);
        assertThat(mutated.tree()).isEqualTo(newTree);
        assertThat(mutated.fitness()).isEqualTo(0);
    }

    @Test
    void shouldCreateCopyWithNewFitness() {
        DecisionTree tree = DecisionTree.random(4, 2, new Random(42));
        Chromosome c = Chromosome.of(tree);

        Chromosome evaluated = c.withFitness(500);

        assertThat(evaluated.uuid()).isEqualTo(c.uuid());
        assertThat(evaluated.generation()).isEqualTo(c.generation());
        assertThat(evaluated.tree()).isEqualTo(c.tree());
        assertThat(evaluated.fitness()).isEqualTo(500);
    }

    @Test
    void shouldBeEqualWhenSameValues() {
        UUID uuid = UUID.randomUUID();
        DecisionTree tree = DecisionTree.random(3, 1, new Random(42));

        Chromosome a = Chromosome.of(uuid, 1, tree, 100);
        Chromosome b = Chromosome.of(uuid, 1, tree, 100);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferent() {
        DecisionTree tree = DecisionTree.random(3, 1, new Random(42));

        Chromosome a = Chromosome.of(UUID.randomUUID(), 1, tree, 100);
        Chromosome b = Chromosome.of(UUID.randomUUID(), 1, tree, 100);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldNotBeEqualWhenDifferentFitness() {
        UUID uuid = UUID.randomUUID();
        DecisionTree tree = DecisionTree.random(3, 1, new Random(42));

        Chromosome a = Chromosome.of(uuid, 1, tree, 100);
        Chromosome b = Chromosome.of(uuid, 1, tree, 200);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldHaveToString() {
        DecisionTree tree = DecisionTree.random(4, 2, new Random(42));
        Chromosome c = Chromosome.of(tree);

        String str = c.toString();
        assertThat(str).contains("Chromosome");
        assertThat(str).contains("gen=0");
        assertThat(str).contains("fitness=0");
    }

    @Test
    void shouldChainWithFitnessAndWithTree() {
        DecisionTree tree1 = DecisionTree.random(4, 2, new Random(42));
        DecisionTree tree2 = DecisionTree.random(4, 2, new Random(7));

        Chromosome c = Chromosome.of(tree1)
                .withFitness(300)
                .withTree(tree2)
                .withFitness(500);

        assertThat(c.generation()).isEqualTo(1);
        assertThat(c.tree()).isEqualTo(tree2);
        assertThat(c.fitness()).isEqualTo(500);
    }
}
