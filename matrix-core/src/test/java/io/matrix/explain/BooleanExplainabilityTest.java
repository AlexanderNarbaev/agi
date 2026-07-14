package io.matrix.explain;

import io.matrix.neuron.DecisionTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static io.matrix.neuron.DecisionTree.Leaf;
import static io.matrix.neuron.DecisionTree.Split;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link BooleanExplainability} — SHAP-style feature importance
 * for Boolean neuron decisions.
 */
@DisplayName("BooleanExplainability")
class BooleanExplainabilityTest {

    // ─── helper: build small trees ───

    /** Identity on bit 0: output = bit0. */
    private static DecisionTree identityTree() {
        return new Split(0, new Leaf(false), new Leaf(true));
    }

    /** OR gate: output = bit0 ∨ bit1. */
    private static DecisionTree orTree() {
        return new Split(0,
                new Split(1, new Leaf(false), new Leaf(true)),  // left: bit0 == 0, depends on bit1
                new Leaf(true)                                   // right: bit0 == 1, always true
        );
    }

    /** AND gate: output = bit0 ∧ bit1. */
    private static DecisionTree andTree() {
        return new Split(0,
                new Leaf(false),                                 // left: bit0 == 0, always false
                new Split(1, new Leaf(false), new Leaf(true))   // right: bit0 == 1, depends on bit1
        );
    }

    /** Constant false tree. */
    private static DecisionTree constantFalseTree() {
        return new Leaf(false);
    }

    /** Tree depending on two bits in XOR-like pattern but on both bits. */
    private static DecisionTree multiBitTree() {
        // Split on bit 0: goes right to check bit 1
        return new Split(0,
                new Split(1, new Leaf(false), new Leaf(true)),
                new Split(1, new Leaf(true), new Leaf(false))
        );
    }

    private static BitSet input(int... bitsSet) {
        BitSet bs = new BitSet();
        for (int b : bitsSet) {
            bs.set(b);
        }
        return bs;
    }

    // ─── tests ───

    @Test
    @DisplayName("single-bit flip changes output → positive SHAP for the flipped bit")
    void singleBitFlipChangesOutput() {
        DecisionTree tree = identityTree();          // output = bit0
        BitSet in = input();                         // bit0 = 0 → output = false

        List<BooleanExplainability.FeatureImportance> result = BooleanExplainability.explain(tree, in);

        assertThat(result).isNotEmpty();
        BooleanExplainability.FeatureImportance top = result.get(0);
        assertThat(top.bitIndex()).isEqualTo(0);
        // When bit0 = 0, E[output|bit0=0] = 0 (all rows with bit0=0 produce 0).
        // When bit0 = 1, E[output|bit0=1] = 1 (all rows with bit0=1 produce 1).
        // SHAP = 0 - 1 = -1.0 (negative because actual value 0 pushes output toward 0)
        assertThat(top.shapValue()).isLessThan(0.0);
    }

    @Test
    @DisplayName("all-zero input → all-zero output → zero importance")
    void allZeroInputConstantOutput() {
        DecisionTree constant0 = constantFalseTree();    // always false
        BitSet in = input();                              // both bits = 0

        List<BooleanExplainability.FeatureImportance> result =
                BooleanExplainability.explain(constant0, in);

        assertThat(result).isEmpty();  // k == 0 for leaf
    }

    @Test
    @DisplayName("chain explanation produces per-layer results")
    void chainExplanationPerLayer() {
        DecisionTree layer1 = identityTree();
        DecisionTree layer2 = identityTree();
        List<DecisionTree> trees = List.of(layer1, layer2);
        List<BitSet> inputs = List.of(input(0), input(1));

        List<List<BooleanExplainability.FeatureImportance>> chain =
                BooleanExplainability.explainChain(trees, inputs);

        assertThat(chain).hasSize(2);
        assertThat(chain.get(0)).isNotEmpty();
        assertThat(chain.get(1)).isNotEmpty();
    }

    @Test
    @DisplayName("explainChain throws when sizes mismatch")
    void chainThrowsOnSizeMismatch() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BooleanExplainability.explainChain(
                        List.of(identityTree()), List.of()));
    }

    @Test
    @DisplayName("top-K filtering works")
    void topKFiltering() {
        DecisionTree tree = multiBitTree();
        BitSet in = input(0, 1);  // both bits = 1

        List<BooleanExplainability.FeatureImportance> all = BooleanExplainability.explain(tree, in);
        assertThat(all).hasSize(2);  // k=2 for this tree

        List<BooleanExplainability.FeatureImportance> top1 =
                BooleanExplainability.topFeatures(all, 1);
        assertThat(top1).hasSize(1);

        List<BooleanExplainability.FeatureImportance> top0 =
                BooleanExplainability.topFeatures(all, 0);
        assertThat(top0).isEmpty();
    }

    @Test
    @DisplayName("human-readable format produces expected strings")
    void humanReadableFormat() {
        DecisionTree tree = identityTree();
        BitSet in = input(0);
        List<BooleanExplainability.FeatureImportance> result =
                BooleanExplainability.explain(tree, in);

        String defaultFmt = BooleanExplainability.toExplanation(result, "default");
        assertThat(defaultFmt).contains("Bit 0", "SHAP", "importance");

        String briefFmt = BooleanExplainability.toExplanation(result, "brief");
        assertThat(briefFmt).contains("Top-1 bit", "significant");

        String singleFmt = BooleanExplainability.toExplanation(result, "single");
        assertThat(singleFmt).contains("Bit 0", "SHAP");

        // null or empty list should not throw
        String emptyFmt = BooleanExplainability.toExplanation(List.of(), "default");
        assertThat(emptyFmt).contains("No input features");
    }

    @Test
    @DisplayName("consistent results for same input")
    void consistentForSameInput() {
        DecisionTree tree = orTree();
        BitSet in = input();

        List<BooleanExplainability.FeatureImportance> r1 = BooleanExplainability.explain(tree, in);
        List<BooleanExplainability.FeatureImportance> r2 = BooleanExplainability.explain(tree, in);

        assertThat(r1).hasSameSizeAs(r2);
        for (int i = 0; i < r1.size(); i++) {
            BooleanExplainability.FeatureImportance a = r1.get(i);
            BooleanExplainability.FeatureImportance b = r2.get(i);
            assertThat(a.bitIndex()).isEqualTo(b.bitIndex());
            assertThat(a.shapValue()).isEqualTo(b.shapValue());
        }
    }

    @Test
    @DisplayName("OR-gate: both inputs are important")
    void orGateBothInputsImportant() {
        DecisionTree tree = orTree();
        BitSet in = input();  // bit0=0, bit1=0 → output=false

        List<BooleanExplainability.FeatureImportance> result = BooleanExplainability.explain(tree, in);

        assertThat(result).hasSize(2);
        for (BooleanExplainability.FeatureImportance fi : result) {
            assertThat(Math.abs(fi.shapValue()))
                    .as("bit %d should have non-zero importance", fi.bitIndex())
                    .isGreaterThan(0.0);
        }
    }

    @Test
    @DisplayName("AND-gate: both inputs are important")
    void andGateBothInputsImportant() {
        DecisionTree tree = andTree();
        BitSet in = input(0, 1);  // bit0=1, bit1=1 → output=true

        List<BooleanExplainability.FeatureImportance> result = BooleanExplainability.explain(tree, in);

        assertThat(result).hasSize(2);
        for (BooleanExplainability.FeatureImportance fi : result) {
            assertThat(Math.abs(fi.shapValue()))
                    .as("bit %d should have non-zero importance", fi.bitIndex())
                    .isGreaterThan(0.0);
        }
    }
}
