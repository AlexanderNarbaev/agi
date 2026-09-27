package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W5 Step 3 — DistillationPurityTest.
 *
 * <p>Article VII + Article VIII: distillation is OFFLINE-only.
 * After distillation, ONNX files are inert data; the runtime mind
 * must NOT call any distillation code in its decision path.</p>
 *
 * <p>This test asserts:
 * 1. No runtime module imports {@code io.matrix.distill.OnnxActivationTeacher}
 *    (which is the only ONNX inference path).
 * 2. The decision-path stages (ArithmeticStage, BirInferenceStage,
 *    HdcRetrievalStage, TsetlinStage) do not invoke any distillation method.</p>
 */
class DistillationPurityTest {

    private static final List<String> RUNTIME_SOURCE_DIRS = List.of(
        "matrix-brain-runtime/src/main/java",
        "matrix-api-gateway/src/main/java"
    );

    @Test
    void runtime_mind_does_not_import_onnx_activation_teacher() throws Exception {
        // The OnnxActivationTeacher is allowed ONLY in the offline
        // distillation path (used by DistillationPipeline which is
        // called explicitly, not from the runtime decision path).
        // The runtime stages must not import it.
        for (String dir : RUNTIME_SOURCE_DIRS) {
            Path root = Path.of("/home/alexandr-narbaev/Projects/agi", dir);
            if (!Files.exists(root)) continue;
            try (var stream = Files.walk(root)) {
                List<Path> violators = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try { return Files.readString(p).contains("import io.matrix.distill.OnnxActivationTeacher"); }
                        catch (IOException e) { return false; }
                    })
                    .filter(p -> !p.getFileName().toString().equals("DistillationPipeline.java"))
                    .toList();
                assertThat(violators)
                    .as("Only DistillationPipeline may import OnnxActivationTeacher")
                    .isEmpty();
            }
        }
    }

    @Test
    void decision_path_stages_do_not_invoke_distillation() throws Exception {
        // The four primary decision-path stages must not call any
        // distillation method.
        List<String> stages = List.of(
            "ArithmeticStage.java",
            "BirInferenceStage.java",
            "HdcRetrievalStage.java",
            "TsetlinStage.java"
        );
        for (String stage : stages) {
            for (String dir : RUNTIME_SOURCE_DIRS) {
                Path p = Path.of("/home/alexandr-narbaev/Projects/agi", dir, "io/matrix/brain/runtime/stages", stage);
                if (!Files.exists(p)) continue;
                String src = Files.readString(p);
                assertThat(src)
                    .as(stage + " must not call distillation.synthesize()")
                    .doesNotContain("distill.synthesize");
                assertThat(src)
                    .as(stage + " must not call distillCustom")
                    .doesNotContain("distillCustom");
                assertThat(src)
                    .as(stage + " must not import OnnxActivationTeacher")
                    .doesNotContain("OnnxActivationTeacher");
            }
        }
    }

    @Test
    void distillation_class_is_in_offline_path() throws Exception {
        // DistillationPipeline lives in matrix-brain-runtime. It is wired
        // to the gateway's /v1/distill endpoint. The gateway's
        // handleDistill is the only production caller.
        Path gw = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java");
        String src = Files.readString(gw);
        assertThat(src).contains("handleDistill");
        assertThat(src).contains("distillationFactory");
    }
}
