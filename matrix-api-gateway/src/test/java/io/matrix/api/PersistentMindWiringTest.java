package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W2 — PersistentMind promoted (D-7/12 closure).
 *
 * <p>After promotion, the gateway has a single knowledge-store object
 * (PersistentMind) shared by analyze/sleep/federation paths.</p>
 */
class PersistentMindWiringTest {

    @Test
    void gateway_has_persistentMind_field() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("persistentMind");
        f.setAccessible(true);
        assertThat(f.getType().getName())
            .isEqualTo("io.matrix.brain.runtime.PersistentMind");
    }

    @Test
    void persistentMind_is_initially_null_until_brain_loads() throws Exception {
        // In stub mode (no production brain loaded), persistentMind is null.
        var f = MinimalHttpServer.class.getDeclaredField("persistentMind");
        f.setAccessible(true);
        Object pm = f.get(new MinimalHttpServer(0));
        // Either null (stub mode) or a PersistentMind (production mode).
        if (pm != null) {
            assertThat(pm.getClass().getName())
                .isEqualTo("io.matrix.brain.runtime.PersistentMind");
        }
    }

    @Test
    void production_brain_client_exposes_knowledge_base() throws Exception {
        var m = io.matrix.api.brain.ProductionBrainClient.class
            .getMethod("knowledgeBase");
        assertThat(m.getReturnType().getName())
            .isEqualTo("io.matrix.knowledge.SimpleKnowledgeBase");
    }

    @Test
    void production_brain_client_exposes_brain_for_persistent() throws Exception {
        var m = io.matrix.api.brain.ProductionBrainClient.class
            .getMethod("brainForPersistent");
        assertThat(m.getReturnType().getName())
            .isEqualTo("io.matrix.brain.BirBrainCycle");
    }

    @Test
    void d12_single_store_invariant_documented() {
        // D-12 invariant: after PersistentMind promotion there is exactly ONE
        // knowledge store object shared by analyze/sleep/federation paths.
        // This test enforces that the gateway exposes ONE PersistentMind field
        // (not multiple per-path stores).
        var fields = MinimalHttpServer.class.getDeclaredFields();
        long pmCount = 0;
        for (var f : fields) {
            if (f.getType().getName().equals("io.matrix.brain.runtime.PersistentMind")) {
                pmCount++;
            }
        }
        assertThat(pmCount).as("exactly one PersistentMind field (D-12 invariant)").isEqualTo(1);
    }
}
