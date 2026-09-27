package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;
import io.matrix.bir.ClauseSetForm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W15 — Restart-survival test for BirKnowledgeBase.
 *
 * <p>Closes D-12 / L-2: registry survives restart by replay from disk,
 * not by deterministic re-derivation.</p>
 */
class BirKnowledgeBaseRestartTest {

    @Test
    void registry_state_survives_close_and_reopen(@TempDir Path tmp) throws Exception {
        Path nd = tmp.resolve("bir-persistence.ndjson");

        // Session 1: register 3 rules, persist on each.
        BirRegistry registry1 = new BirRegistry();
        BirKnowledgeBase kb1 = new BirKnowledgeBase(registry1, nd);
        for (int i = 0; i < 3; i++) {
            var cl = new ClauseSetForm.Clause(
                new long[]{(long) (i + 1)}, new long[]{0L});
            var bir = ClauseSetForm.lossy(4, List.of(cl), "src-" + i, 0.5);
            BirKnowledgeBase.RegisterResult r = kb1.register(
                "rule-" + i, bir, "rule-" + i, 0.5, ("seed" + i).getBytes());
            assertThat(r.accepted()).isTrue();
        }
        long size1 = kb1.size();
        assertThat(size1).isEqualTo(3);

        // Session 2: open a fresh BirRegistry + BirKnowledgeBase against the same file.
        BirRegistry registry2 = new BirRegistry();
        BirKnowledgeBase kb2 = new BirKnowledgeBase(registry2, nd);
        assertThat(kb2.size()).isEqualTo(size1);
        // First/last IDs replayed.
        assertThat(registry2.get("rule-0")).isNotNull();
        assertThat(registry2.get("rule-2")).isNotNull();
    }

    @Test
    void contradiction_quarantines_dont_silently_overwrite(@TempDir Path tmp) throws Exception {
        Path nd = tmp.resolve("bir-contradiction.ndjson");
        BirRegistry registry = new BirRegistry();
        BirKnowledgeBase kb = new BirKnowledgeBase(registry, nd);

        var cl = new ClauseSetForm.Clause(
            new long[]{0xFL}, new long[]{0L});
        var clA = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L});
        var clB = new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0x1L}); // different conclusion bit
        var birA = ClauseSetForm.lossy(4, List.of(clA), "src-A", 0.5);
        var birB = ClauseSetForm.lossy(4, List.of(clB), "src-B-different", 0.6);

        kb.register("rule-A", birA, "rule-A", 0.5, "a".getBytes());
        BirKnowledgeBase.RegisterResult r2 = kb.register("rule-A-2", birB, "rule-A-2", 0.6, "b".getBytes());
        // Same precondition, different name → quarantined, NOT registered.
        assertThat(r2.accepted()).isFalse();
        assertThat(r2.quarantined()).isNotNull();
        assertThat(kb.quarantined()).hasSize(1);
        // Size is still 1 (rule-B was quarantined).
        assertThat(kb.size()).isEqualTo(1);
    }
}
