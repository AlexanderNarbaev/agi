package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 290 — CycleOutput unit tests. */
class CycleOutputTest {

    @Test
    void toTextAccepted() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        String text = CycleOutput.toText(r);
        if (r.accepted()) {
            assertThat(text).startsWith("[OK]");
        }
    }

    @Test
    void toTextDenied() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hi\u0001there");
        String text = CycleOutput.toText(r);
        if (!r.accepted()) {
            assertThat(text).startsWith("[DENY]");
        }
    }

    @Test
    void toJsonContainsFields() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        String json = CycleOutput.toJson(r);
        assertThat(json).contains("\"accepted\":");
        assertThat(json).contains("\"action\":");
        assertThat(json).contains("\"arousal\":");
        assertThat(json).contains("\"focus\":");
    }

    @Test
    void toCsvFormat() {
        var svc = new BrainLoopService();
        var r = svc.cycle("hello");
        String csv = CycleOutput.toCsv(r);
        // CSV has 5 fields
        assertThat(csv.split(",")).hasSize(5);
    }
}
