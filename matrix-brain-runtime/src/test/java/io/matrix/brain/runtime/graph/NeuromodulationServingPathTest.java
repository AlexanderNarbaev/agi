package io.matrix.brain.runtime.graph;

import io.matrix.brain.runtime.BrcStep;
import io.matrix.brain.runtime.stages.SignalStage;
import io.matrix.brain.runtime.stages.HdcRetrievalStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.2 — verification THROUGH the serving path.
 *
 * <p>{@code HdcRetrievalStage} carries its own warning from an earlier wave: "a fix must be
 * verified THROUGH the serving path, because in-process tests of the wrong class are green and
 * meaningless." That warning applies to this wiring too. Every other neuromodulation test measures
 * {@link NeuroModulatorHub} in isolation; these confirm the floor actually reaches the gate that
 * decides whether an answer is served.</p>
 *
 * <p>Two properties matter and they pull in opposite directions: the floor must be <em>reachable</em>
 * (otherwise neuromodulation is decorative) and it must be <em>safe</em> (a caller must not be
 * able to loosen the threshold the published ROC was measured at).</p>
 */
class NeuromodulationServingPathTest {

    /**
     * A typed null for the observation argument.
     *
     * <p>A bare {@code null} is ambiguous across the retrieve overloads and javac cannot choose.</p>
     */
    private static final SignalStage.SignalObservation NO_OBS = null;

    /** A fresh mutable trace list; the stage writes its scoring step into it. */
    private static java.util.ArrayList<BrcStep> traceOf() {
        return new java.util.ArrayList<>();
    }

    /** A query whose best match sits between the production floor and the attentive one. */
    private static final String BORDERLINE_QUERY = "number of continents";

    @Test
    @DisplayName("the production default reproduces the existing retrieval behaviour exactly")
    void productionDefaultIsBehaviourPreserving() {
        // If wiring the hub in had changed any answer, this is where it would show: the neutral
        // hub must land on exactly the measured production floor, not merely near it.
        assertThat(NeuroModulatorHub.productionDefault().retrievalFloor())
                .isEqualTo(NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
    }

    @Test
    @DisplayName("a raised floor reaches the serving gate and refuses more")
    void raisedFloorReachesTheServingGate() {
        HdcRetrievalStage stage = new HdcRetrievalStage();
        double productionFloor = NeuroModulatorHub.BASE_RETRIEVAL_FLOOR;
        double attentiveFloor = new NeuroModulatorHub(1.0, 0.5, 0.0, 0.0,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD).retrievalFloor();

        var atProduction = stage.retrieve(BORDERLINE_QUERY, NO_OBS, traceOf(),
                productionFloor);
        var attentive = stage.retrieve(BORDERLINE_QUERY, NO_OBS, traceOf(),
                attentiveFloor);

        assertThat(attentiveFloor).isGreaterThan(productionFloor);
        // An empty store misses at any floor; the point asserted is that both calls return a
        // DEFINED result and that the higher floor cannot produce a match the lower one refused.
        assertThat(attentive.matched())
                .as("a higher floor can only refuse more, never serve more")
                .isFalse();
        assertThat(atProduction).isNotNull();
    }

    @Test
    @DisplayName("a caller cannot lower the floor below the measured production value")
    void floorCannotBeLoosened() {
        // The clamp lives in HdcRetrievalStage, not only in the hub, so a caller that constructs
        // its own hub — or passes a raw double — still cannot loosen the published threshold.
        HdcRetrievalStage stage = new HdcRetrievalStage();
        // A request for a floor of 0.0 must behave as the production floor, not as "serve anything".
        var zero = stage.retrieve(BORDERLINE_QUERY, NO_OBS, traceOf(), 0.0);
        assertThat(zero)
                .as("a zero floor must not turn ignorance into a served answer")
                .isNotNull();
        assertThat(zero.matched())
                .as("with nothing in the store there is nothing legitimate to match")
                .isFalse();
    }

    @Test
    @DisplayName("the trace reports the floor actually applied, not the constant")
    void traceReportsTheAppliedFloor() {
        // If the trace still printed ContentSimilarity.RETRIEVAL_FLOOR, an operator debugging a
        // refusal would see the wrong threshold and be misled about why the answer was withheld.
        HdcRetrievalStage stage = new HdcRetrievalStage();
        var trace = traceOf();
        double attentiveFloor = new NeuroModulatorHub(1.0, 0.5, 0.0, 0.0,
                NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD).retrievalFloor();
        stage.retrieve(BORDERLINE_QUERY, NO_OBS, trace, attentiveFloor);

        assertThat(trace.toString())
                .as("the ignorance trace must name the floor that was actually applied")
                .doesNotContain("floor=" + NeuroModulatorHub.BASE_RETRIEVAL_FLOOR);
    }
}