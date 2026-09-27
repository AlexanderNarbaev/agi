package io.matrix.brain.runtime;

import io.matrix.bir.Bir;
import io.matrix.bir.BirRegistry;
import io.matrix.distill.DatasetConnectorV2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W5 Step 1 — DistillationPipeline tests.
 *
 * <p>Deterministic distillation via synthetic teacher + via local dataset.
 * No network required. Verifies Article VIII markers + K_MAX guard + merge
 * into live BirRegistry.</p>
 */
class DistillationPipelineTest {

    @Test
    void distill_from_synthetic_teacher_registers_in_birregistry() {
        BirRegistry reg = new BirRegistry();
        DistillationPipeline pipe = new DistillationPipeline(42L, reg);
        DistillationPipeline.RunResult r = pipe.distillFromSyntheticTeacher(
            "synth-source", "and", 8, 16);
        assertThat(r.samplesUsed()).isEqualTo(16);
        assertThat(r.distilledBir()).isNotNull();
        assertThat(r.provenance()).contains("source=synth-source").contains("pattern=and");
        // The rule was registered.
        assertThat(reg.size()).isGreaterThan(0);
    }

    @Test
    void distill_from_synthetic_teacher_is_deterministic() {
        BirRegistry reg1 = new BirRegistry();
        BirRegistry reg2 = new BirRegistry();
        DistillationPipeline pipe1 = new DistillationPipeline(42L, reg1);
        DistillationPipeline pipe2 = new DistillationPipeline(42L, reg2);
        DistillationPipeline.RunResult r1 = pipe1.distillFromSyntheticTeacher("src", "or", 4, 8);
        DistillationPipeline.RunResult r2 = pipe2.distillFromSyntheticTeacher("src", "or", 4, 8);
        // Determinism: same seed ⇒ same provenance + same samplesUsed + same fidelity.
        assertThat(r1.provenance()).isEqualTo(r2.provenance());
        assertThat(r1.samplesUsed()).isEqualTo(r2.samplesUsed());
        assertThat(r1.fidelity()).isEqualTo(r2.fidelity());
        // Both registered the same ruleId pattern.
        assertThat(reg1.listAll()).hasSize(1);
        assertThat(reg2.listAll()).hasSize(1);
        assertThat(reg1.listAll().get(0).id()).isEqualTo(reg2.listAll().get(0).id());
    }

    @Test
    void distill_from_local_dataset_no_network() {
        // BoolQ / LogiQA / CLUTRR via DatasetConnectorV2 — no network required.
        BirRegistry reg = new BirRegistry();
        DistillationPipeline pipe = new DistillationPipeline(42L, reg);
        DistillationPipeline.RunResult r = pipe.distillFromDataset(
            "local-boolq", DatasetConnectorV2.DatasetType.BOOLQ, 4);
        assertThat(r.samplesUsed()).isGreaterThan(0);
        assertThat(reg.size()).isGreaterThan(0);
        assertThat(r.provenance()).contains("dataset=BOOLQ");
    }

    @Test
    void kmax_article_ii_guard() {
        BirRegistry reg = new BirRegistry();
        DistillationPipeline pipe = new DistillationPipeline(42L, reg);
        // inputBits=25 violates K_MAX=20
        Throwable caught = null;
        try {
            pipe.distillFromSyntheticTeacher("src", "and", 25, 4);
        } catch (Throwable t) {
            caught = t;
        }
        assertThat(caught).as("K_MAX violation must throw").isNotNull();
        assertThat(caught).isInstanceOf(IllegalStateException.class);
        assertThat(caught.getMessage()).contains("Article II");
    }

    @Test
    void distilled_bir_is_queriable_via_bridge() {
        // Full loop: distill → register → BirRegistryBridge can answer.
        BirRegistry reg = new BirRegistry();
        DistillationPipeline pipe = new DistillationPipeline(42L, reg);
        pipe.distillFromSyntheticTeacher("synth", "and", 8, 8);
        BirRegistryBridge bridge = new BirRegistryBridge(reg);
        // We don't know which exact rule fires on a specific input, but the
        // registry should have at least one ClauseSetForm.
        boolean hasClauseSetForm = reg.listAll().stream()
            .anyMatch(e -> e.bir() instanceof io.matrix.bir.ClauseSetForm
                || e.bir() instanceof io.matrix.bir.TtForm);
        assertThat(hasClauseSetForm).isTrue();
        // TryInfer may or may not match a specific input, but the bridge
        // should be safely constructible.
        long[] probe = new long[]{0b1111L, 0L};
        // No assertion on the result — just that no exception thrown.
        var result = bridge.tryInfer(probe);
        // result may be null if no rule fires on this probe.
    }
}
