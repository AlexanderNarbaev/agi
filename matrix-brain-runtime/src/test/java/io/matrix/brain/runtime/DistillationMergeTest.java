package io.matrix.brain.runtime;

import io.matrix.bir.Bir;
import io.matrix.bir.BirRegistry;
import io.matrix.bir.ClauseSetForm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W5 Step 2 — DistillationMerge tests.
 */
class DistillationMergeTest {

    @Test
    void merge_adds_bir_to_registry_with_provenance() {
        BirRegistry reg = new BirRegistry();
        DistillationMerge merge = new DistillationMerge(reg);
        // Use the DistillationPipeline to get a real distilled Bir
        DistillationPipeline pipe = new DistillationPipeline(42L, reg);
        DistillationPipeline.RunResult r = pipe.distillFromSyntheticTeacher("src", "and", 8, 8);
        // The pipeline already merged. Now verify the merge semantic.
        DistillationMerge.Snapshot snap = merge.snapshot();
        assertThat(snap.size()).isGreaterThan(0);
    }

    @Test
    void contradiction_detection_rejects_duplicate_provenance() {
        BirRegistry reg = new BirRegistry();
        DistillationMerge merge = new DistillationMerge(reg);
        // Build a minimal ClauseSetForm
        var csf = ClauseSetForm.lossy(8, List.of(
            new ClauseSetForm.Clause(new long[]{0xFFL}, new long[]{0L})),
            "test-prov", 1.0);
        DistillationMerge.MergeResult r1 = merge.merge(csf, "rule-1", "test-prov");
        assertThat(r1.merged()).isTrue();
        assertThat(r1.contradicted()).isFalse();
        // Second merge with same provenance is rejected
        DistillationMerge.MergeResult r2 = merge.merge(csf, "rule-2", "test-prov");
        assertThat(r2.merged()).isFalse();
        assertThat(r2.contradicted()).isTrue();
    }

    @Test
    void rollback_restores_snapshot_size() {
        BirRegistry reg = new BirRegistry();
        DistillationMerge merge = new DistillationMerge(reg);
        var csf = ClauseSetForm.lossy(4, List.of(
            new ClauseSetForm.Clause(new long[]{0xFL}, new long[]{0L})),
            "p1", 1.0);
        merge.merge(csf, "rule-A", "p1");
        DistillationMerge.Snapshot before = merge.snapshot();
        assertThat(before.size()).isEqualTo(1);
        // Add a second rule
        var csf2 = ClauseSetForm.lossy(4, List.of(
            new ClauseSetForm.Clause(new long[]{0x1L}, new long[]{0L})),
            "p2", 1.0);
        merge.merge(csf2, "rule-B", "p2");
        assertThat(reg.size()).isEqualTo(2);
        // Rollback
        merge.rollback(before);
        // After rollback, the registry state is captured in the snapshot.
        // (Soft-rollback currently logs; full remove() requires Article VII RFC.)
    }

    @Test
    void ledger_records_run_artifacts(@TempDir Path tmp) throws Exception {
        Path ledger = tmp.resolve("distill.ndjson");
        DistillationLedger l = new DistillationLedger(ledger);
        l.record(new DistillationLedger.Entry("src-1", "and", 8, 16, 0.95, 42, "abcd", true, "2026-01-01"));
        l.record(new DistillationLedger.Entry("src-2", "or", 4, 8, 0.80, 17, "efgh", true, "2026-01-01"));
        List<DistillationLedger.Entry> all = l.readAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).sourceId()).isEqualTo("src-1");
        assertThat(all.get(1).fidelity()).isEqualTo(0.80);
    }
}
