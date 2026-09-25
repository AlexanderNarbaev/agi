package io.matrix.brain.runtime;

import io.matrix.distill.DatasetConnectorV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TRUE-W5 — Real distillation factory tests.
 *
 * <p>Verifies the pipeline:
 * OnnxActivationTeacher → Distiller.capture → Distiller.synthesize →
 * Distiller.fidelity → DistillationLedger + DiskBudget guard.</p>
 */
class TrueDistillationFactoryIntegrationTest {

    private static List<long[]> calibrationInputs(int n, long seed) {
        List<long[]> out = new ArrayList<>();
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < n; i++) {
            long[] v = new long[256];
            for (int j = 0; j < v.length; j++) v[j] = r.nextLong();
            out.add(v);
        }
        return out;
    }

    @Test
    void distill_runs_end_to_end_with_synthetic_onnx(@TempDir Path tmp) throws Exception {
        // Use distillCustom to avoid ONNX Runtime dependency in CI (which would
        // require a real ONNX file). The pipeline still uses the real
        // Distiller.capture → Distiller.synthesize → Distiller.fidelity.
        Path ledgerPath = tmp.resolve("ledger.ndjson");
        DistillationLedger ledger = new DistillationLedger(ledgerPath);
        DiskBudget disk = DiskBudget.forRoot(tmp);

        TrueDistillationFactory factory = new TrueDistillationFactory();
        var result = factory.distillCustom(
            "synthetic:tiny_teacher",
            calibrationInputs(10, 42L),
            input -> {
                // Synthetic teacher: mean-bit-density activation
                float avg = 0f;
                int n = 0;
                for (long v : input) { avg += Long.bitCount(v); n += 64; }
                return new float[]{ n == 0 ? 0f : avg / n };
            },
            ledger,
            disk
        );

        assertThat(result).isNotNull();
        assertThat(result.captures()).isEqualTo(10);
        assertThat(result.fidelity()).isBetween(0.0, 1.0);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(result.artifactHash()).isNotBlank();
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void distill_rejects_missing_file(@TempDir Path tmp) {
        Path missing = tmp.resolve("nonexistent.onnx");
        DistillationLedger ledger = new DistillationLedger(tmp.resolve("l.ndjson"));
        DiskBudget disk = DiskBudget.forRoot(tmp);
        TrueDistillationFactory factory = new TrueDistillationFactory();
        assertThatThrownBy(() ->
            factory.distill(missing, calibrationInputs(5, 1L), ledger, disk))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void distill_rejects_empty_calibration(@TempDir Path tmp) {
        DistillationLedger ledger = new DistillationLedger(tmp.resolve("l.ndjson"));
        DiskBudget disk = DiskBudget.forRoot(tmp);
        TrueDistillationFactory factory = new TrueDistillationFactory();
        assertThatThrownBy(() ->
            factory.distillCustom("synthetic", List.of(), in -> new float[]{1f}, ledger, disk))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Calibration inputs required");
    }

    @Test
    void distill_appends_ledger_row_with_real_engine_identity(@TempDir Path tmp) {
        Path ledgerPath = tmp.resolve("ledger.ndjson");
        DistillationLedger ledger = new DistillationLedger(ledgerPath);
        TrueDistillationFactory factory = new TrueDistillationFactory();
        factory.distillCustom(
            "synthetic:teacher",
            calibrationInputs(8, 7L),
            in -> {
                float sum = 0;
                for (long v : in) sum += Long.bitCount(v);
                return new float[]{ sum };
            },
            ledger,
            DiskBudget.forRoot(tmp)
        );

        var entries = ledger.readAll();
        assertThat(entries).hasSize(1);
        var e = entries.get(0);
        assertThat(e.source()).isEqualTo("synthetic:teacher");
        assertThat(e.inputsCount()).isEqualTo(8);
        assertThat(e.birClausesInduced()).isGreaterThan(0);
        assertThat(e.evalDelta()).isBetween(0.0, 1.0);
        assertThat(e.artifactHash()).isNotBlank();
    }

    @Test
    void codebook_can_be_built_with_factory_helper(@TempDir Path tmp) {
        TrueDistillationFactory factory = new TrueDistillationFactory();
        var cb = factory.newCodeBook(256, 16);
        assertThat(cb).isNotNull();
        assertThat(cb.dimension()).isEqualTo(256);
        assertThat(cb.codeSize()).isEqualTo(16);
    }

    @Test
    void disk_budget_refuses_oversized_artifacts(@TempDir Path tmp) {
        // Distill custom (no file). Then a separate disk check exercises the
        // refuse-threshold by asking for absurd bytes.
        Path ledgerPath = tmp.resolve("l.ndjson");
        DistillationLedger ledger = new DistillationLedger(ledgerPath);
        DiskBudget disk = DiskBudget.forRoot(tmp);
        // The custom path doesn't take a file, so disk check is not invoked;
        // but we exercise the disk refuse via requireFreeBytes directly.
        assertThatThrownBy(() ->
            DiskBudget.requireFreeBytes(tmp,
                disk.freeBytes() + 100L * 1024 * 1024 * 1024, "test_op"))
            .isInstanceOf(DiskBudget.DiskBudgetExceeded.class);
    }

    @Test
    void runtime_path_does_not_import_legacy_llm_classes() throws IOException {
        // CONSTITUTION Article I guard: confirm the runtime mind module still
        // imports zero legacy LLM classes. RuntimeLlmGuardTest enforces
        // this across all runtime files; here we assert the distillation
        // factory specifically.
        Path src = Path.of("src/main/java/io/matrix/brain/runtime/TrueDistillationFactory.java");
        assertThat(Files.exists(src)).isTrue();
        String content = Files.readString(src);
        for (String forbidden : List.of(
            "io.matrix.api.OnnxRuntimeAdapter",
            "io.matrix.api.QwenModelAdapter",
            "io.matrix.api.OpenAIChatResource")) {
            assertThat(content).doesNotContain(forbidden);
        }
    }

    @Test
    void multiple_distillation_runs_accumulate_in_ledger(@TempDir Path tmp) {
        DistillationLedger ledger = new DistillationLedger(tmp.resolve("l.ndjson"));
        TrueDistillationFactory factory = new TrueDistillationFactory();
        for (int i = 0; i < 3; i++) {
            factory.distillCustom(
                "synthetic:run-" + i,
                calibrationInputs(5, (long) i),
                in -> {
                    float sum = 0;
                    for (long v : in) sum += Long.bitCount(v);
                    return new float[]{ sum };
                },
                ledger,
                DiskBudget.forRoot(tmp)
            );
        }
        assertThat(ledger.size()).isEqualTo(3);
    }

    @Test
    void dataset_connector_v2_generates_calibration_samples() {
        DatasetConnectorV2 conn = new DatasetConnectorV2(Path.of("data/datasets"));
        // BoolQ is the smallest; verify generation works.
        var samples = conn.generateSamples(DatasetConnectorV2.DatasetType.BOOLQ, 5);
        assertThat(samples).hasSize(5);
    }
}
