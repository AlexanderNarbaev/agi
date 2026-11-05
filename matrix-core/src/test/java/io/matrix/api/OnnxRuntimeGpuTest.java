package io.matrix.api;

import io.matrix.api.OnnxRuntimeAdapter.GpuDiagnostic;
import io.matrix.api.OnnxRuntimeAdapter.GpuState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link OnnxRuntimeAdapter} — RUN 62 GPU execution, rewritten by OD-028.
 *
 * <p>What changed and why. Before OD-028 these tests asserted that the reported
 * providers were non-empty and that they did not contain "CUDA" — and both
 * assertions passed against a HARDCODED {@code {"CPUExecutionProvider"}}, whether
 * or not the GPU was in use. They tested a constant. They now assert the TRUTH:
 * that the providers the session runs with agree with the state the diagnostic
 * reports, and that a refusal to use the GPU is recorded with its cause rather
 * than swallowed.
 *
 * <p>A test that cannot run on this machine is SKIPPED with the reason it could
 * not run, never silently returned from. The reason appears in the JUnit XML as
 * the skip message, so "the test passed" and "the test did not run" can never be
 * confused.
 */
class OnnxRuntimeGpuTest {

    @TempDir
    Path tmp;

    private static Path findOnnxModel() {
        Path[] candidates = {
                Path.of("models/onnx/qwen05b/model.onnx"),
                Path.of("../models/onnx/qwen05b/model.onnx"),
                Path.of("../../models/onnx/qwen05b/model.onnx")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            Path p = cwd.resolve("models/onnx/qwen05b/model.onnx");
            if (Files.exists(p)) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    /**
     * The exported model, or a skip with the reason it is missing.
     *
     * <p>The model is the thing these tests are about; without it there is
     * nothing to report on, and a silent pass would hide that.
     */
    private static Path requireOnnxModel() {
        Path model = findOnnxModel();
        assumeTrue(model != null && Files.isRegularFile(model),
                "the ONNX model models/onnx/qwen05b/model.onnx is not present on this "
                + "machine, so the GPU diagnostic cannot be exercised");
        return model;
    }

    @Test
    void loadWithGpuEnabled() throws IOException {
        Path model = requireOnnxModel();

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(true);
        boolean loaded = adapter.load();
        assertThat(loaded).isTrue();
        assertThat(adapter.isLoaded()).isTrue();

        GpuDiagnostic diagnostic = adapter.gpuDiagnostic();
        System.out.println("[GPU-TEST] diagnostic: " + diagnostic);

        // The truth, not the truth one would like: the providers the session runs
        // with must agree with the state the diagnostic reports.
        if (diagnostic.fallbackUsed()) {
            assertThat(diagnostic.cudaRegistrationError())
                    .as("a fallback must name the cause of the registration failure")
                    .isNotBlank();
            assertThat(diagnostic.state()).isEqualTo(GpuState.CPU_FALLBACK_USED);
            assertThat(diagnostic.activeProviders())
                    .as("a session that fell back to the CPU runs on the CPU")
                    .containsExactly("CPUExecutionProvider");
        } else {
            assertThat(diagnostic.state()).isNotEqualTo(GpuState.CPU_FALLBACK_USED);
            assertThat(diagnostic.activeProviders()).isNotEmpty();
            if (diagnostic.state() == GpuState.CUDA_REGISTERED
                    || diagnostic.state() == GpuState.CUDA_SELECTED_FOR_MODEL
                    || diagnostic.state() == GpuState.CUDA_EXECUTION_COMPLETED) {
                assertThat(diagnostic.activeProviders())
                        .as("a session that registered the CUDA provider runs with it")
                        .contains("CUDAExecutionProvider");
            }
        }
        assertThat(diagnostic.evidence()).isNotBlank();
        assertThat(adapter.info()).contains("useGpu=true");
        adapter.close();
    }

    @Test
    void loadWithGpuDisabledUsesCpuOnly() throws IOException {
        Path model = requireOnnxModel();

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(false);
        boolean loaded = adapter.load();
        assertThat(loaded).isTrue();

        GpuDiagnostic diagnostic = adapter.gpuDiagnostic();
        System.out.println("[GPU-TEST] diagnostic with the GPU disabled: " + diagnostic);

        for (String provider : diagnostic.activeProviders()) {
            assertThat(provider.toUpperCase())
                    .as("no CUDA provider may be active when the GPU was not requested")
                    .doesNotContain("CUDA");
        }
        assertThat(diagnostic.gpuRequested())
                .as("the GPU was not requested, and the diagnostic must say so")
                .isFalse();
        assertThat(diagnostic.fallbackUsed())
                .as("a deliberate choice of the CPU is not a fallback")
                .isFalse();
        adapter.close();
    }

    @Test
    void loadReturnsFalseForMissingModel() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(tmp.resolve("missing.onnx"));
        adapter.setUseGpu(true);
        assertThat(adapter.load()).isFalse();
    }

    @Test
    void gpuFlagRoundtrips() {
        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter();
        assertThat(adapter.isUseGpu()).isTrue();
        adapter.setUseGpu(false);
        assertThat(adapter.isUseGpu()).isFalse();
        adapter.setUseGpu(true);
        assertThat(adapter.isUseGpu()).isTrue();
    }

    @Test
    void activeProvidersAreClonable() throws IOException {
        Path model = requireOnnxModel();

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.load();
        String[] providers1 = adapter.activeProviders();
        String[] providers2 = adapter.activeProviders();
        if (providers1.length > 0) {
            providers1[0] = "MUTATED";
        }
        assertThat(providers2[0]).isNotEqualTo("MUTATED");
        adapter.close();
    }

    /**
     * OD-028: the registration failure must be RECORDED, not only logged.
     *
     * <p>Before OD-028 the exception was caught, one WARN line was printed and the
     * exception was discarded. Nothing downstream could tell a working GPU from a
     * silent fallback. Now the cause is on the diagnostic, and this test holds
     * the diagnostic to its own contract: a fallback carries a cause, and a
     * successful registration carries none.
     */
    @Test
    void theRegistrationFailureIsRecordedAndNotOnlyLogged() throws IOException {
        Path model = requireOnnxModel();

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(true);
        assertThat(adapter.load()).isTrue();

        GpuDiagnostic diagnostic = adapter.gpuDiagnostic();
        System.out.println("[GPU-TEST] registration error: " + diagnostic.cudaRegistrationError());

        if (diagnostic.fallbackUsed()) {
            assertThat(diagnostic.cudaRegistrationError())
                    .as("a fallback must carry the registration error verbatim")
                    .isNotBlank();
            assertThat(diagnostic.state()).isEqualTo(GpuState.CPU_FALLBACK_USED);
            assertThat(diagnostic.evidence())
                    .as("a fallback must say what was observed")
                    .isNotBlank();
        } else {
            assertThat(diagnostic.cudaRegistrationError())
                    .as("a successful registration carries no error")
                    .isNull();
        }
        adapter.close();
    }

    /**
     * RUN 63: a real inference, and the provider that actually ran it.
     *
     * <p>This is the only test that may claim {@link GpuState#CUDA_EXECUTION_COMPLETED},
     * and it claims it from the session's execution profile — the ONNX Runtime
     * profile names the provider that executed each node, which is the only
     * ground truth for "the GPU did the work". Profiling is opt-in because the
     * profile is large; with it off the diagnostic never claims that state.
     *
     * <p>The test is skipped, with the reason, when the CUDA provider did not
     * register in this JVM — which is what happens when the launcher has not
     * exported the CUDA libraries.
     */
    @Test
    void cudaExecutionIsObservedFromTheExecutionProfile() throws Exception {
        Path model = requireOnnxModel();
        Path profileDirectory = tmp.resolve("profile");
        Files.createDirectories(profileDirectory);

        OnnxRuntimeAdapter adapter = new OnnxRuntimeAdapter(model);
        adapter.setUseGpu(true);
        adapter.setProfileDirectory(profileDirectory);
        assumeTrue(adapter.load(), "the ONNX session could not be created on this machine");

        GpuDiagnostic before = adapter.gpuDiagnostic();
        assumeTrue(before.state() == GpuState.CUDA_REGISTERED
                        || before.state() == GpuState.CUDA_SELECTED_FOR_MODEL
                        || before.state() == GpuState.CUDA_EXECUTION_COMPLETED,
                "the CUDA provider did not register in this JVM (" + before.evidence()
                + "); this test needs the CUDA libraries on LD_LIBRARY_PATH");

        long token = adapter.greedyNextToken(new long[]{1L, 2L, 3L, 4L, 5L});
        System.out.println("[GPU-INFER] next token: " + token);

        String[] observed = adapter.captureExecutionProviders();
        System.out.println("[GPU-INFER] observed providers: " + Arrays.toString(observed));

        assertThat(observed)
                .as("the execution profile must name the provider that ran the node")
                .contains("CUDAExecutionProvider");

        GpuDiagnostic after = adapter.gpuDiagnostic();
        assertThat(after.state()).isEqualTo(GpuState.CUDA_EXECUTION_COMPLETED);
        assertThat(after.executionProvidersObserved())
                .as("the providers are now observed, not configured")
                .isTrue();
        assertThat(after.activeProviders()).contains("CUDAExecutionProvider");
        adapter.close();
    }
}
