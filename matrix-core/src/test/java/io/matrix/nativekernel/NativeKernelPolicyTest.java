package io.matrix.nativekernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W51 — the selection policy.
 *
 * <p>The policy is a decision, and a decision that is not tested is a
 * decision nobody can check. These tests fix what the policy does in each
 * situation: with the shared object and without it, below and above the
 * threshold, and with an arity the native side does not support.
 */
class NativeKernelPolicyTest {

    /** A batch below every threshold the policy knows. */
    private static final int SMALL_BATCH = 8;

    /** A batch above every threshold the policy knows. */
    private static final int LARGE_BATCH = 1 << 16;

    /** An arity the native side does not support. */
    private static final int ARITY_ABOVE_MAXIMUM = NativeKernelPolicy.MAX_ARITY + 1;

    /** An arity below the range the native side supports. */
    private static final int ARITY_BELOW_MINIMUM = NativeKernelPolicy.MIN_ARITY - 1;

    @Test
    @DisplayName("the policy in force keeps the Java path, because the measurement did not justify the crossing")
    void thePolicyInForceKeepsTheJavaPath() {
        assertThat(NativeKernelPolicy.DEFAULT.nativeEnabled()).isFalse();
        assertThat(NativeKernelPolicy.select(true, SMALL_BATCH, 8)).isEqualTo(NativeKernelPolicy.Path.JAVA);
        assertThat(NativeKernelPolicy.select(true, LARGE_BATCH, 8)).isEqualTo(NativeKernelPolicy.Path.JAVA);
    }

    @Test
    @DisplayName("without the shared object the Java path is chosen even when the policy allows the native one")
    void withoutTheLibraryTheJavaPathIsChosen() {
        NativeKernelPolicy.Policy policy = NativeKernelPolicy.Policy.nativeAbove(0);
        assertThat(NativeKernelPolicy.select(policy, false, LARGE_BATCH, 8))
                .isEqualTo(NativeKernelPolicy.Path.JAVA);
    }

    @Test
    @DisplayName("a policy that allows the native path selects it above the threshold")
    void aPolicyThatAllowsTheNativePathSelectsItAboveTheThreshold() {
        NativeKernelPolicy.Policy policy = NativeKernelPolicy.Policy.nativeAbove(SMALL_BATCH);
        assertThat(NativeKernelPolicy.select(policy, true, LARGE_BATCH, 8))
                .isEqualTo(NativeKernelPolicy.Path.NATIVE);
    }

    @Test
    @DisplayName("a policy that allows the native path keeps the Java path below the threshold")
    void aPolicyThatAllowsTheNativePathKeepsJavaBelowTheThreshold() {
        NativeKernelPolicy.Policy policy = NativeKernelPolicy.Policy.nativeAbove(SMALL_BATCH);
        assertThat(NativeKernelPolicy.select(policy, true, SMALL_BATCH - 1, 8))
                .isEqualTo(NativeKernelPolicy.Path.JAVA);
        assertThat(NativeKernelPolicy.select(policy, true, SMALL_BATCH, 8))
                .isEqualTo(NativeKernelPolicy.Path.NATIVE);
    }

    @Test
    @DisplayName("an arity the native side does not support stays on the Java path")
    void anUnsupportedArityStaysOnTheJavaPath() {
        NativeKernelPolicy.Policy policy = NativeKernelPolicy.Policy.nativeAbove(0);
        assertThat(NativeKernelPolicy.select(policy, true, LARGE_BATCH, ARITY_ABOVE_MAXIMUM))
                .isEqualTo(NativeKernelPolicy.Path.JAVA);
        assertThat(NativeKernelPolicy.select(policy, true, LARGE_BATCH, ARITY_BELOW_MINIMUM))
                .isEqualTo(NativeKernelPolicy.Path.JAVA);
    }

    @Test
    @DisplayName("a batch the native side refuses stays on the Java path")
    void aBatchTheNativeSideRefusesStaysOnTheJavaPath() {
        NativeKernelPolicy.Policy policy = NativeKernelPolicy.Policy.nativeAbove(0);
        assertThat(NativeKernelPolicy.select(policy, true, NativeKernelPolicy.MAX_BATCH_FOR_NATIVE + 1, 8))
                .isEqualTo(NativeKernelPolicy.Path.JAVA);
    }

    @Test
    @DisplayName("every decision has a reason a reader can act on")
    void everyDecisionHasAReason() {
        NativeKernelPolicy.Policy policy = NativeKernelPolicy.Policy.nativeAbove(SMALL_BATCH);
        assertThat(NativeKernelPolicy.explain(NativeKernelPolicy.DEFAULT, true, LARGE_BATCH, 8))
                .contains("Java path");
        assertThat(NativeKernelPolicy.explain(policy, false, LARGE_BATCH, 8))
                .contains("not available");
        assertThat(NativeKernelPolicy.explain(policy, true, SMALL_BATCH - 1, 8))
                .contains("below the threshold");
        assertThat(NativeKernelPolicy.explain(policy, true, LARGE_BATCH, ARITY_ABOVE_MAXIMUM))
                .contains("arity");
        assertThat(NativeKernelPolicy.explain(policy, true, LARGE_BATCH, 8))
                .contains("at or above the threshold");
    }
}
