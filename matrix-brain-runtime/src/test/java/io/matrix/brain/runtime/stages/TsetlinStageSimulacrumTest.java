package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W1 — D-3 truthfulness guard.
 */
class TsetlinStageSimulacrumTest {

    @AfterEach
    void resetSimulacrum() {
        TsetlinStage.simulacrumEnabled = false;
    }

    @Test
    void default_behaviour_returns_miss() {
        TsetlinStage s = new TsetlinStage();
        TsetlinStage.TsetlinResult r = s.classify("hello", null);
        assertThat(r.matched()).isFalse();
    }

    @Test
    void default_records_simulacrum_marker() {
        TsetlinStage s = new TsetlinStage();
        List<BrcStep> trace = new ArrayList<>();
        s.classify("How are you?", trace);
        assertThat(trace).hasSize(1);
        assertThat(trace.get(0).stage()).isEqualTo("TSETLIN_SIMULACRUM");
        assertThat(trace.get(0).fired()).isFalse();
    }

    @Test
    void simulacrum_enabled_returns_legacy_match() {
        TsetlinStage.simulacrumEnabled = true;
        try {
            TsetlinStage s = new TsetlinStage();
            List<BrcStep> trace = new ArrayList<>();
            TsetlinStage.TsetlinResult r = s.classify("hello", trace);
            assertThat(r.matched()).isTrue();
            assertThat(r.reply()).contains("operational");
        } finally {
            TsetlinStage.simulacrumEnabled = false;
        }
    }
}
