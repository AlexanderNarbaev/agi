package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;
import io.matrix.brain.runtime.ContentSimilarity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W38.0 — B4. The in-memory retrieval path compared the best score against the
 * {@link ContentSimilarity#RETRIEVAL_FLOOR} CONSTANT while printing the caller's
 * {@code retrievalFloor}, so {@code NeuroModulatorHub.retrievalFloor()} had no effect there and the
 * trace advertised a floor that was never applied.
 *
 * <p>Why this file exists at all: {@code NeuromodulationServingPathTest} already claims to prove the
 * floor reaches the serving gate, but it constructs {@code new HdcRetrievalStage()} — the very path
 * that discarded the value — and asserts only that an EMPTY store misses. An empty store misses at
 * every floor, so that test cannot distinguish honouring the floor from ignoring it. It is green
 * whether the bug is present or fixed. This test uses a NON-empty store and a score that sits
 * between the two floors, which is the only configuration where the two implementations disagree.</p>
 */
class RetrievalFloorAppliedTest {

    /** Between BASE (0.20) and ATTENTIVE (0.35) — the discriminating band. */
    private static final double NEUTRAL_FLOOR = 0.20;
    private static final double ATTENTIVE_FLOOR = 0.35;

    private static List<BrcStep> trace() {
        return new ArrayList<>();
    }

    /**
     * A fact whose score clears 0.20 but not 0.35 must hit at neutral and miss when the
     * neuromodulator raises the floor. This is the assertion the existing test cannot make.
     *
     * <p>The fact is terse and shares exactly ONE token ("capital") with the query, so its
     * weighted score lands in the band rather than near 1.0. If a future scoring change pushes
     * every fact above
     * 0.35 this test fails loudly instead of silently becoming vacuous — which is the failure mode
     * being guarded against.</p>
     */
    @Test
    void in_memory_path_honours_a_raised_floor() {
        HdcRetrievalStage stage = new HdcRetrievalStage();
        stage.teachInMemory("f1", "Kyiv => Kyiv is the capital of Ukraine");

        SignalStage.SignalObservation obs = null;
        String query = "capital";

        double score = ContentSimilarity.weightedScore(query,
            "Kyiv => Kyiv is the capital of Ukraine", null);
        assertThat(score)
            .as("test premise: the score must sit between the two floors, or the test proves nothing")
            .isGreaterThanOrEqualTo(NEUTRAL_FLOOR)
            .isLessThan(ATTENTIVE_FLOOR);

        var neutral = stage.retrieve(query, obs, trace(), NEUTRAL_FLOOR);
        var attentive = stage.retrieve(query, obs, trace(), ATTENTIVE_FLOOR);

        assertThat(neutral.matched())
            .as("at neutral floor the fact should be retrieved")
            .isTrue();
        assertThat(attentive.matched())
            .as("raising the floor must refuse this fact — this is the entire point of the floor")
            .isFalse();
    }

    /**
     * The trace must report the floor that was APPLIED, not the one that was requested.
     *
     * <p>This is a separate assertion from the one above because a trace that prints the requested
     * value while applying another is exactly what hid this defect: the logs looked correct while
     * the control surface did nothing.</p>
     */
    @Test
    void in_memory_trace_reports_the_applied_floor() {
        HdcRetrievalStage stage = new HdcRetrievalStage();
        stage.teachInMemory("f1", "Kyiv => Kyiv is the capital of Ukraine");

        SignalStage.SignalObservation obs = null;
        List<BrcStep> missTrace = trace();
        var miss = stage.retrieve("capital zzzz qqqq wwww", obs, missTrace, ATTENTIVE_FLOOR);
        assertThat(miss.matched()).isFalse();

        // The floor lives in the structured-ignorance STRING carried by HdcResult and in the
        // BrcStep trace step, not in prose: this assertion is what would have caught the defect,
        // where the requested floor was printed while the constant was applied.
        assertThat(miss.trace())
            .as("miss trace must name the floor that was actually applied")
            .contains("floor=" + ATTENTIVE_FLOOR);

        List<BrcStep> hitTrace = trace();
        var hit = stage.retrieve("Kyiv", obs, hitTrace, NEUTRAL_FLOOR);
        assertThat(hit.matched()).isTrue();
        assertThat(hit.trace())
            .as("hit trace must name the floor that was actually applied")
            .contains("floor=" + NEUTRAL_FLOOR);

        // A HIT must also carry the CLAMPED floor, not a below-ROC one: at the moment the
        // uncommitted fix shipped, the hit path still printed the REQUESTED value.
        List<BrcStep> lowHitTrace = trace();
        var lowHit = stage.retrieve("Kyiv", obs, lowHitTrace, 0.0);
        assertThat(lowHit.matched()).isTrue();
        assertThat(lowHit.trace())
            .as("a hit at a below-ROC floor must report the clamped floor it actually applied")
            .contains("floor=" + ContentSimilarity.RETRIEVAL_FLOOR);
    }

    /**
     * A floor below the published ROC value must NOT lower the bar.
     *
     * <p>{@code Math.max} is the entire mechanism, and this is the assertion that would fail if it
     * were ever replaced by a plain assignment. Without this test, "honours a raised floor" and
     * "silently accepts a lowered floor" are indistinguishable from the outside.</p>
     */
    @Test
    void lowered_floor_cannot_undercut_the_published_roc() {
        HdcRetrievalStage stage = new HdcRetrievalStage();
        stage.teachInMemory("f1", "Kyiv => Kyiv is the capital of Ukraine");

        SignalStage.SignalObservation obs = null;
        // Score 0.0 ("zzzz" shares no tokens with any fact), which makes this test
        // limit-independent: under a plain-assignment mutant (no clamp) the zero-floor
        // call would HIT (0.0 < 0.0 is false) while the published-floor call MISSES,
        // and equality would fail — with ANY scoring formula.
        String query = "zzzz";

        // A caller asking for a floor of zero must still get the published behaviour.
        var zeroFloor = stage.retrieve(query, obs, trace(), 0.0);
        var publishedFloor = stage.retrieve(query, obs, trace(), ContentSimilarity.RETRIEVAL_FLOOR);

        assertThat(zeroFloor.matched())
            .as("a modulator must never lower the bar below the value the ROC was measured at")
            .isEqualTo(publishedFloor.matched());
    }
}