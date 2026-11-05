package io.matrix.api;

import io.matrix.api.OnnxRuntimeAdapter.GpuState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OD-028 — the GPU diagnostic's decision table.
 *
 * <p>{@link OnnxRuntimeAdapter#deriveState} is a pure function over seven
 * observed facts, and these tests are its table. They need no GPU, no ONNX model
 * and no ONNX Runtime: the point of the extraction is that the rule which decides
 * what MATRIX claims about its GPU is testable on any machine, including one with
 * no accelerator at all.
 *
 * <p>The facts, in the order the method takes them:
 * <ul>
 *   <li>{@code hardwarePresent} — a GPU execution-provider device was enumerated</li>
 *   <li>{@code runtimeLibrariesFound} — the CUDA provider's shared object loaded</li>
 *   <li>{@code providerAvailable} — the build offers the CUDA provider</li>
 *   <li>{@code registered} — {@code addCUDA()} returned without throwing</li>
 *   <li>{@code selectedForModel} — the runtime says this model fits the device</li>
 *   <li>{@code executedOnCuda} — the execution profile names the CUDA provider</li>
 *   <li>{@code fallbackUsed} — the GPU was requested and the CPU was used</li>
 * </ul>
 */
class OnnxRuntimeGpuDiagnosticTest {

    private static final boolean YES = true;
    private static final boolean NO = false;

    @Test
    void thereAreExactlySevenStates() {
        assertThat(GpuState.values()).hasSize(7);
        assertThat(GpuState.values()).containsExactlyInAnyOrder(
                GpuState.HARDWARE_DETECTED,
                GpuState.CUDA_RUNTIME_LIBRARIES_FOUND,
                GpuState.CUDA_PROVIDER_AVAILABLE,
                GpuState.CUDA_REGISTERED,
                GpuState.CUDA_SELECTED_FOR_MODEL,
                GpuState.CUDA_EXECUTION_COMPLETED,
                GpuState.CPU_FALLBACK_USED);
    }

    @Test
    void withNoEvidenceAtAllTheCpuIsExecuting() {
        assertThat(OnnxRuntimeAdapter.deriveState(NO, NO, NO, NO, NO, NO, NO))
                .isEqualTo(GpuState.CPU_FALLBACK_USED);
    }

    @Test
    void hardwareDetectedIsReportedWhenItIsTheOnlyEvidence() {
        assertThat(OnnxRuntimeAdapter.deriveState(YES, NO, NO, NO, NO, NO, NO))
                .isEqualTo(GpuState.HARDWARE_DETECTED);
    }

    @Test
    void cudaRuntimeLibrariesFoundIsReportedWhenTheProviderLibraryLoaded() {
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, NO, NO, NO, NO, NO))
                .isEqualTo(GpuState.CUDA_RUNTIME_LIBRARIES_FOUND);
    }

    @Test
    void cudaProviderAvailableIsReportedWhenTheBuildOffersIt() {
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, NO, NO, NO, NO))
                .isEqualTo(GpuState.CUDA_PROVIDER_AVAILABLE);
    }

    @Test
    void cudaRegisteredIsReportedWhenAddCudaReturned() {
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, NO, NO, NO))
                .isEqualTo(GpuState.CUDA_REGISTERED);
    }

    @Test
    void cudaSelectedForModelIsReportedWhenTheModelFitsTheDevice() {
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, YES, NO, NO))
                .isEqualTo(GpuState.CUDA_SELECTED_FOR_MODEL);
    }

    @Test
    void cudaExecutionCompletedIsReportedWhenTheProfileNamesCuda() {
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, YES, YES, NO))
                .isEqualTo(GpuState.CUDA_EXECUTION_COMPLETED);
    }

    @Test
    void cpuFallbackUsedIsReportedWhenTheGpuWasAskedForAndRefused() {
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, NO, NO, NO, YES))
                .isEqualTo(GpuState.CPU_FALLBACK_USED);
    }

    @Test
    void aFallbackOutranksEveryOtherPieceOfEvidence() {
        // The build offers the provider, the libraries load, and the device is
        // there — and the GPU was still refused. That is the fact an operator
        // needs first, so it outranks the ladder.
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, NO, NO, NO, YES))
                .isEqualTo(GpuState.CPU_FALLBACK_USED);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, YES, YES, YES))
                .isEqualTo(GpuState.CPU_FALLBACK_USED);
    }

    @Test
    void theLadderClimbsOneStateAtATime() {
        assertThat(OnnxRuntimeAdapter.deriveState(NO, NO, NO, NO, NO, NO, NO))
                .isEqualTo(GpuState.CPU_FALLBACK_USED);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, NO, NO, NO, NO, NO, NO))
                .isEqualTo(GpuState.HARDWARE_DETECTED);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, NO, NO, NO, NO, NO))
                .isEqualTo(GpuState.CUDA_RUNTIME_LIBRARIES_FOUND);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, NO, NO, NO, NO))
                .isEqualTo(GpuState.CUDA_PROVIDER_AVAILABLE);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, NO, NO, NO))
                .isEqualTo(GpuState.CUDA_REGISTERED);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, YES, NO, NO))
                .isEqualTo(GpuState.CUDA_SELECTED_FOR_MODEL);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, YES, YES, NO))
                .isEqualTo(GpuState.CUDA_EXECUTION_COMPLETED);
    }

    @Test
    void aStateIsNeverClaimedWithoutItsOwnEvidence() {
        // The CUDA states must not be reachable from evidence that does not
        // include them: no registration means no CUDA_REGISTERED, and so on.
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, NO, YES, NO, NO))
                .isEqualTo(GpuState.CUDA_PROVIDER_AVAILABLE);
        assertThat(OnnxRuntimeAdapter.deriveState(NO, NO, NO, NO, YES, NO, NO))
                .isEqualTo(GpuState.CPU_FALLBACK_USED);
        assertThat(OnnxRuntimeAdapter.deriveState(NO, NO, NO, NO, NO, YES, NO))
                .isEqualTo(GpuState.CUDA_EXECUTION_COMPLETED);
    }

    @Test
    void aModelThatFitsTheDeviceIsNotSelectedWithoutARegistration() {
        // The ladder is causal: the model can only be SELECTED for the CUDA
        // device if the provider was registered for the session. Claiming the
        // selection without the registration would overstate what happened.
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, NO, YES, NO, NO))
                .isEqualTo(GpuState.CUDA_PROVIDER_AVAILABLE);
        assertThat(OnnxRuntimeAdapter.deriveState(YES, YES, YES, YES, YES, NO, NO))
                .isEqualTo(GpuState.CUDA_SELECTED_FOR_MODEL);
    }
}
