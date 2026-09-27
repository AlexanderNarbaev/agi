package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * RECON-W14 — Verify the ONNX-distillation pathway is wired end-to-end.
 *
 * <p>Requires a real .onnx file at data/models/teacher/teacher.onnx (generated
 * by scripts/gen_teacher_onnx.py, seeded, deterministic, ~5 KB).
 * Test runs only if the file exists; otherwise it is a no-op skip.</p>
 */
class RealOnnxDistillationTest {

    private static boolean teacherExists() {
        File f = new File("data/models/teacher/teacher.onnx");
        return f.isFile() && f.length() > 0;
    }

    @Test
    @EnabledIf("teacherExists")
    void distill_from_real_onnx_teacher_registers_rules() throws Exception {
        BirRegistry reg = new BirRegistry();
        DistillationPipeline pipe = new DistillationPipeline(42L, reg);
        Path onnx = Path.of("data/models/teacher/teacher.onnx");
        DistillationPipeline.RunResult r = pipe.distillFromOnnxTeacher(
            "real-onnx-teacher", onnx, 16, 32);
        assertThat(r.samplesUsed()).isEqualTo(32);
        assertThat(r.fidelity()).isBetween(0.0, 1.0);
        assertThat(r.provenance()).contains("engine=OnnxActivationTeacher")
            .contains("engine=Distiller.synthesize").contains("engine=BirRegistry.register");
        // The teacher ONNX should have produced at least 1 registered Bir (or been rejected by KMax)
        assertThat(reg.size()).isGreaterThanOrEqualTo(r.samplesUsed() - 1);  // at least one rule
    }

    @Test
    void distill_from_missing_onnx_throws_clean_error() {
        assumeThat(teacherExists()).as("teacher exists precondition").isTrue();
        BirRegistry reg = new BirRegistry();
        DistillationPipeline pipe = new DistillationPipeline(42L, reg);
        Path fake = Path.of("data/models/does-not-exist.onnx");
        try {
            pipe.distillFromOnnxTeacher("fake", fake, 16, 4);
            assertThat(false).as("expected IOException").isTrue();
        } catch (java.io.IOException ex) {
            assertThat(ex.getMessage()).contains("ONNX teacher not found");
        }
    }
}
