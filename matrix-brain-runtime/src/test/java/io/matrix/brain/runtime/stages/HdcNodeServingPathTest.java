package io.matrix.brain.runtime.stages;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.brain.runtime.graph.Activation;
import io.matrix.brain.runtime.graph.HdcNode;
import io.matrix.brain.runtime.graph.NodeId;
import io.matrix.brain.runtime.stages.HdcRetrievalStage.HdcResult;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/**
 * RECON-W38.1 (S3.4) — BDD: "Linear and Graph modes produce identical output at default state".
 *
 * <p>Lives in the stages package because the in-memory teaching hook
 * ({@code teachInMemory}) is package-private. Tested classes ({@link HdcNode} etc.) are public
 * and imported from the graph package.</p>
 *
 * <p>Contract: when an HdcNode and the linear path SHARE the same stage, the graph serving
 * overload (HdcNode.process with a retrieval context) must be observationally equal to a bare
 * stage.retrieve call on that same stage. The only difference allowed by design is multiplication
 * by the node weight (1.0 by default → identity) plus the carried tag map.</p>
 */
class HdcNodeServingPathTest {

    private static final String FACT = "Kyiv => Kyiv is the capital of Ukraine";
    private static final String HIT_QUERY = "Kyiv";
    private static final String MISS_QUERY = "capital zzzz qqqq wwww";
    private static final double NEUTRAL_FLOOR =
            io.matrix.brain.runtime.ContentSimilarity.RETRIEVAL_FLOOR;

    private static HdcRetrievalStage taught() {
        HdcRetrievalStage stage = new HdcRetrievalStage();
        stage.teachInMemory("f1", FACT);
        return stage;
    }

    @Test
    void linear_and_graph_agree_at_neutral_floor_on_a_hit() {
        HdcRetrievalStage stage = taught();
        HdcNode node = new HdcNode(NodeId.of("hdc"), stage);

        HdcResult linear = stage.retrieve(HIT_QUERY, null, new ArrayList<>(), NEUTRAL_FLOOR);
        Activation graph = node.process(Activation.of(1.0), HIT_QUERY, null,
                new ArrayList<>(), NEUTRAL_FLOOR);

        assertThat(linear.matched()).isTrue();
        assertThat(graph.magnitude()).isEqualTo(linear.confidence());
    }

    @Test
    void linear_and_graph_agree_at_neutral_floor_on_a_miss() {
        HdcRetrievalStage stage = taught();
        HdcNode node = new HdcNode(NodeId.of("hdc"), stage);

        HdcResult linear = stage.retrieve(MISS_QUERY, null, new ArrayList<>(), NEUTRAL_FLOOR);
        Activation graph = node.process(Activation.of(1.0), MISS_QUERY, null,
                new ArrayList<>(), NEUTRAL_FLOOR);

        assertThat(linear.matched()).isFalse();
        assertThat(graph.magnitude()).isEqualTo(0.0);
    }

    @Test
    void attentive_floor_diverges_from_neutral_on_the_same_stage_and_query() {
        HdcRetrievalStage stage = taught();
        HdcNode node = new HdcNode(NodeId.of("hdc"), stage);

        Activation graphNeutral = node.process(Activation.of(1.0), HIT_QUERY, null,
                new ArrayList<>(), NEUTRAL_FLOOR);
        Activation graphAttentive = node.process(Activation.of(1.0), HIT_QUERY, null,
                new ArrayList<>(), 0.35);
        HdcResult linearAttentive = stage.retrieve(HIT_QUERY, null, new ArrayList<>(), 0.35);

        // neutral: recall at the published floor; attentive: the raised bar suppresses recall,
        // and the graph run matches the linear run's regression — no behavioural divergence.
        assertThat(linearAttentive.matched()).isFalse();
        assertThat(graphAttentive.magnitude()).isEqualTo(0.0);
        assertThat(graphNeutral.magnitude()).isGreaterThan(0.0);
    }

    @Test
    // RECON-W38.1 M1-kill: the retrieval signal must be scaled by the node weight, not passed
    // through raw. Deterministic single decay step: w' = clamp(w + eta*(pre*post - decay));
    // with pre=post=0 that is w' = 1 - eta*decay = 0.99999 (eta=0.01, decay=0.001) — never 1.0.
    void a_decay_weighted_node_scales_the_retrieval_signal_by_the_weight() {
        HdcRetrievalStage stage = taught();
        HdcNode node = new HdcNode(NodeId.of("hdc"), stage);

        node.updateWeights(Activation.of(0.0), Activation.of(0.0));
        double w = node.weight();
        assertThat(w).isNotEqualTo(1.0);

        HdcResult linear = stage.retrieve(HIT_QUERY, null, new ArrayList<>(), NEUTRAL_FLOOR);
        assertThat(linear.matched()).isTrue();
        Activation graph = node.process(Activation.of(1.0), HIT_QUERY, null,
                new ArrayList<>(), NEUTRAL_FLOOR);

        assertThat(graph.magnitude()).isEqualTo(linear.confidence() * w);
    }
}
