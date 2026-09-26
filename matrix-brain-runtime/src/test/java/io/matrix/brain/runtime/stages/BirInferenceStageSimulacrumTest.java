package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W1 — D-2 truthfulness guard.
 *
 * <p>The legacy BirInferenceStage uses hardcoded string-matching
 * predicates (NOT real Boolean Inference Rules). The default
 * behaviour must be "off" so production traces do not claim
 * inference happened when it did not.</p>
 */
class BirInferenceStageSimulacrumTest {

    @AfterEach
    void resetSimulacrumFlag() {
        BirInferenceStage.simulacrumEnabled = false;
    }

    @Test
    void default_behaviour_returns_miss() {
        BirInferenceStage s = new BirInferenceStage();
        BirInferenceStage.BirResult r = s.evaluate("hello", null, null);
        assertThat(r.matched()).isFalse();
    }

    @Test
    void default_behaviour_records_simulacrum_marker_in_trace() {
        BirInferenceStage s = new BirInferenceStage();
        List<BrcStep> trace = new ArrayList<>();
        s.evaluate("What is your name?", null, trace);
        assertThat(trace).hasSize(1);
        assertThat(trace.get(0).stage()).isEqualTo("BIR_SIMULACRUM");
        assertThat(trace.get(0).fired()).isFalse();
        assertThat(trace.get(0).evidence())
            .anyMatch(e -> e.contains("simulacrum=true"));
    }

    @Test
    void simulacrum_enabled_returns_legacy_match() {
        BirInferenceStage.simulacrumEnabled = true;
        try {
            BirInferenceStage s = new BirInferenceStage();
            List<BrcStep> trace = new ArrayList<>();
            BirInferenceStage.BirResult r = s.evaluate("hello world", null, trace);
            assertThat(r.matched()).isTrue();
            assertThat(r.reply()).contains("MATRIX");
        } finally {
            BirInferenceStage.simulacrumEnabled = false;
        }
    }

    @Test
    void production_path_does_not_match_anything() {
        BirInferenceStage s = new BirInferenceStage();
        // Without simulacrum, no input should ever match (no real engine yet).
        assertThat(s.evaluate("hello", null, null).matched()).isFalse();
        assertThat(s.evaluate("What is your name?", null, null).matched()).isFalse();
        assertThat(s.evaluate("2 + 3", null, null).matched()).isFalse();
    }
}
