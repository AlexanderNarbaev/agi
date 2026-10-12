package io.matrix.brain.runtime.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** W37.1 — weight clamping, plasticity arithmetic, and node-id validation. */
class GraphBasicsTest {

    @Test
    @DisplayName("weight clamps to [-1,1] on construction")
    void weightClampsOnConstruction() {
        assertThat(new Synapse(NodeId.of("a"), NodeId.of("b"), 5.0, Synapse.SynapseState.CONNECTED).weight())
                .isEqualTo(1.0);
        assertThat(new Synapse(NodeId.of("a"), NodeId.of("b"), -5.0, Synapse.SynapseState.CONNECTED).weight())
                .isEqualTo(-1.0);
        assertThat(new Synapse(NodeId.of("a"), NodeId.of("b"), 0.25, Synapse.SynapseState.CONNECTED).weight())
                .isEqualTo(0.25);
    }

    @Test
    @DisplayName("clamp maps NaN to 0 rather than propagating it")
    void clampHandlesNaN() {
        assertThat(Synapse.clamp(Double.NaN))
                .as("NaN must not poison downstream arithmetic")
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("withWeight cannot escape the bound")
    void withWeightStaysBounded() {
        Synapse s = new Synapse(NodeId.of("a"), NodeId.of("b"), 0.5, Synapse.SynapseState.CONNECTED);
        assertThat(s.withWeight(99.0).weight()).isEqualTo(1.0);
        assertThat(s.withWeight(-99.0).weight()).isEqualTo(-1.0);
    }

    @Test
    @DisplayName("a pruned synapse carries no signal but keeps its weight for audit")
    void prunedSynapseIsInert() {
        Synapse s = new Synapse(NodeId.of("a"), NodeId.of("b"), 0.8, Synapse.SynapseState.CONNECTED);
        Synapse pruned = s.pruned();
        assertThat(pruned.conducting()).isFalse();
        assertThat(pruned.apply(1.0)).isZero();
        assertThat(pruned.weight())
                .as("audit requires the last known weight, not an erasure")
                .isEqualTo(0.8);
    }

    @Test
    @DisplayName("stronger weight is recorded as potentiated, weaker as depressed")
    void stateTracksWeightDirection() {
        Synapse s = new Synapse(NodeId.of("a"), NodeId.of("b"), 0.5, Synapse.SynapseState.CONNECTED);
        assertThat(s.withWeight(0.9).state()).isEqualTo(Synapse.SynapseState.POTENTIATED);
        assertThat(s.withWeight(0.1).state()).isEqualTo(Synapse.SynapseState.DRESSED_DOWN);
    }

    @Test
    @DisplayName("plasticity strengthens a correlated synapse")
    void plasticityStrengthensCorrelation() {
        double next = PlasticityRule.update(0.5, 1.0, 1.0, 0.1, 0.0);
        assertThat(next).isGreaterThan(0.5);
        assertThat(next).isEqualTo(0.5 + 0.1 * 1.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    @DisplayName("plasticity decays an uncorrelated synapse")
    void plasticityDecaysWithoutCorrelation() {
        double next = PlasticityRule.update(0.5, 0.0, 0.0, 0.1, 0.2);
        assertThat(next).isLessThan(0.5);
        assertThat(next).isEqualTo(0.5 - 0.02, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    @DisplayName("plasticity scales with BOTH pre and post, not post alone")
    void plasticityDependsOnBothTerms() {
        // pre=1,post=3,eta=0.1 -> 0 + 0.1*(1*3) = 0.30
        double base = PlasticityRule.update(0.0, 1.0, 3.0, 0.1, 0.0);
        assertThat(base).isEqualTo(0.30, org.assertj.core.data.Offset.offset(1e-12));

        // doubling pre: 0.1*(2*3) = 0.60
        assertThat(PlasticityRule.update(0.0, 2.0, 3.0, 0.1, 0.0))
                .as("doubling the pre-synaptic activity must double the update; a rule that "
                        + "ignores the pre term cannot tell these apart")
                .isEqualTo(0.60, org.assertj.core.data.Offset.offset(1e-12));

        // doubling post: 0.1*(3*3) = 0.90
        assertThat(PlasticityRule.update(0.0, 3.0, 3.0, 0.1, 0.0))
                .as("doubling the post-synaptic activity must double the update")
                .isEqualTo(0.90, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    @DisplayName("plasticity cannot drive a weight out of bounds")
    void plasticityRespectsBounds() {
        assertThat(PlasticityRule.update(0.99, 1.0, 1.0, 10.0, 0.0)).isEqualTo(1.0);
        assertThat(PlasticityRule.update(-0.99, 1.0, 1.0, 10.0, 0.0)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("plasticity rejects negative decay")
    void plasticityRejectsNegativeDecay() {
        assertThatThrownBy(() -> PlasticityRule.update(0.5, 1.0, 1.0, 0.1, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decay");
    }

    @Test
    @DisplayName("node id rejects blank names and orders lexicographically")
    void nodeIdValidation() {
        assertThatThrownBy(() -> new NodeId("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NodeId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new NodeId("  a  ").value())
                .as("surrounding whitespace is trimmed, not rejected")
                .isEqualTo("a");
        assertThat(NodeId.of("a").compareTo(NodeId.of("b"))).isNegative();
        assertThat(NodeId.of("b").compareTo(NodeId.of("a"))).isPositive();
        assertThat(NodeId.of("a").compareTo(NodeId.of("a"))).isZero();
    }

    @Test
    @DisplayName("activation is immutable: withSignal does not mutate the original")
    void activationIsImmutable() {
        Activation original = Activation.of(1.0, 2.0);
        Activation derived = original.withSignal(new double[] {5.0});
        assertThat(original.signal()).containsExactly(1.0, 2.0);
        assertThat(derived.signal()).containsExactly(5.0);
        assertThat(derived.magnitude()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("activation tags are copied and unmodifiable")
    void activationTagsAreDefensive() {
        Activation a = Activation.of(1.0).withTag("attention", 0.8);
        assertThat(a.tags()).containsEntry("attention", 0.8);
        assertThatThrownBy(() -> a.tags().put("x", 1.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
