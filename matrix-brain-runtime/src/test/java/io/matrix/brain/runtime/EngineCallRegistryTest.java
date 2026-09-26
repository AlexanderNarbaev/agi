package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EngineCallRegistryTest {

    @Test
    void register_appends_call_with_evidence_string() {
        EngineCallRegistry r = new EngineCallRegistry();
        r.register("HDC", "io.matrix.brain.runtime.HdcRetrievalStage", "retrieve",
            "args123", "out456");
        assertThat(r.callCount()).isEqualTo(1);
        String e = r.allCalls().get(0).evidenceString();
        assertThat(e).contains("HdcRetrievalStage.retrieve");
        assertThat(e).contains("args123");
        assertThat(e).contains("out456");
    }

    @Test
    void firedFrom_returns_true_only_if_stage_has_calls() {
        EngineCallRegistry r = new EngineCallRegistry();
        r.register("HDC", "x", "y", "a", "b");
        assertThat(r.firedFrom("HDC")).isTrue();
        assertThat(r.firedFrom("BIR")).isFalse();
    }

    @Test
    void stageFiredCount_aggregates_per_stage() {
        EngineCallRegistry r = new EngineCallRegistry();
        r.register("HDC", "x", "y", "a", "b");
        r.register("HDC", "x", "y", "a", "b");
        r.register("BIR", "u", "v", "c", "d");
        assertThat(r.stageFiredCount().get("HDC")).isEqualTo(2);
        assertThat(r.stageFiredCount().get("BIR")).isEqualTo(1);
    }

    @Test
    void evidenceFor_returns_calls_matching_stage() {
        EngineCallRegistry r = new EngineCallRegistry();
        r.register("HDC", "HdcStage", "retrieve", "a", "b");
        r.register("BIR", "BirStage", "evaluate", "c", "d");
        List<String> hdcE = r.evidenceFor("HDC");
        List<String> birE = r.evidenceFor("BIR");
        assertThat(hdcE).hasSize(1);
        assertThat(birE).hasSize(1);
        assertThat(hdcE.get(0)).contains("HdcStage");
        assertThat(birE.get(0)).contains("BirStage");
    }

    @Test
    void reset_clears_all() {
        EngineCallRegistry r = new EngineCallRegistry();
        r.register("HDC", "x", "y", "a", "b");
        r.reset();
        assertThat(r.callCount()).isEqualTo(0);
        assertThat(r.firedFrom("HDC")).isFalse();
    }

    @Test
    void register_rejects_blank_args() {
        EngineCallRegistry r = new EngineCallRegistry();
        assertThat(catchThrowable(() -> r.register(null, "x", "y", "a", "b")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> r.register("", "x", "y", "a", "b")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> r.register("HDC", null, "y", "a", "b")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> r.register("HDC", "x", null, "a", "b")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_argsDigest_safe_defaults_to_empty() {
        EngineCallRegistry r = new EngineCallRegistry();
        r.register("HDC", "x", "y", null, null);
        assertThat(r.allCalls().get(0).argsDigest()).isEqualTo("");
        assertThat(r.allCalls().get(0).outputDigest()).isEqualTo("");
    }

    @Test
    void allCalls_returns_unmodifiable_copy() {
        EngineCallRegistry r = new EngineCallRegistry();
        r.register("HDC", "x", "y", "a", "b");
        List<EngineCallRegistry.EngineCall> view = r.allCalls();
        assertThat(view).hasSize(1);
        // Mutating the returned list must not affect the registry.
        try {
            view.clear();
            assertThat(r.callCount()).isEqualTo(1);
        } catch (UnsupportedOperationException ignored) {
            // OK — list may be unmodifiable.
        }
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
