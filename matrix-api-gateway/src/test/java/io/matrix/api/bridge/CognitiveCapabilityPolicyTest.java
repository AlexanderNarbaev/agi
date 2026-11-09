package io.matrix.api.bridge;

import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ExplainTrace;
import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — the policy of the peer's allowlist: the version, and the revocation.
 *
 * <p><b>Why this test is about the remote execution and not about the bridge.</b> The
 * bridge decides whether a <i>message</i> may be delivered; this decides whether a
 * <i>capability</i> may be run. The mandate asks for both, and it asks for the second to
 * be checked on every invocation: a capability id that arrives over the network is
 * resolved through an explicit allowlist, its version must be one this process serves, and
 * a capability that was withdrawn must not run once more.</p>
 */
class CognitiveCapabilityPolicyTest {

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    /** A brain that counts how many times it was asked to think. */
    private static final class CountingBrain implements BrainCycle {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            return new CycleResult("the lamp is lit", 0.8, 5L, true, List.of("BIR", "HDC"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }

    /**
     * A request for the bounded cognitive inference.
     *
     * @param requestId the request id
     * @param version   the version the task asks for
     * @return the request
     */
    private static CognitiveTaskRequest request(String requestId, String version) {
        return new CognitiveTaskRequest(
                BridgeProtocol.VERSION,
                "tsk-" + requestId,
                requestId,
                new Correlation("corr-" + requestId, "ep-parent", "ep-" + requestId, requestId),
                "ep-parent",
                "chain-a-epistemic-core",
                CognitiveCapability.COGNITIVE_INFERENCE,
                version,
                CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE,
                "is the lamp lit",
                java.util.Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS,
                4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId,
                null);
    }

    @Test
    @DisplayName("a request for a version this bridge does not serve is refused, and the brain is not asked")
    void aRequestForAVersionThisBridgeDoesNotServeIsRefused() {
        CountingBrain brain = new CountingBrain();
        CognitiveCapability capabilities = CognitiveCapability.standard(brain);

        CognitiveCapability.Execution execution = capabilities.execute(
                request("req-ver-1", "v2"));

        assertThat(execution.status()).isEqualTo(CognitiveTaskResponse.Status.UNSUPPORTED);
        assertThat(execution.refusalReason())
                .withFailMessage("the refusal must name the version the bridge serves")
                .contains(CognitiveCapability.COGNITIVE_INFERENCE_VERSION);
        assertThat(brain.calls.get())
                .withFailMessage("a task for another version must not reach the brain")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("the version the bridge serves is the one the answer names")
    void theVersionTheBridgeServesIsTheOneTheAnswerNames() {
        CountingBrain brain = new CountingBrain();
        CognitiveCapability capabilities = CognitiveCapability.standard(brain);

        CognitiveCapability.Execution execution = capabilities.execute(
                request("req-ver-2", CognitiveCapability.COGNITIVE_INFERENCE_VERSION));

        assertThat(execution.status()).isEqualTo(CognitiveTaskResponse.Status.COMPLETED);
        assertThat(brain.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a withdrawn capability is not run again, and the caller learns why")
    void aWithdrawnCapabilityIsNotRunAgainAndTheCallerLearnsWhy() {
        CountingBrain brain = new CountingBrain();
        CognitiveCapability capabilities = CognitiveCapability.standard(brain);

        capabilities.revoke(CognitiveCapability.COGNITIVE_INFERENCE,
                "the brain runtime was taken out of service");

        CognitiveCapability.Execution execution = capabilities.execute(
                request("req-rev-1", CognitiveCapability.COGNITIVE_INFERENCE_VERSION));

        assertThat(execution.status()).isEqualTo(CognitiveTaskResponse.Status.UNSUPPORTED);
        assertThat(execution.refusalReason())
                .withFailMessage("the refusal must say that the capability was withdrawn")
                .contains("was withdrawn")
                .contains("the brain runtime was taken out of service");
        assertThat(brain.calls.get())
                .withFailMessage("a withdrawn capability must not reach the brain")
                .isEqualTo(0);
        assertThat(capabilities.served())
                .withFailMessage("a withdrawn capability is not served any more")
                .doesNotContain(CognitiveCapability.COGNITIVE_INFERENCE);
        assertThat(capabilities.revocations())
                .containsKey(CognitiveCapability.COGNITIVE_INFERENCE);
    }

    @Test
    @DisplayName("a capability that was never served cannot be withdrawn")
    void aCapabilityThatWasNeverServedCannotBeWithdrawn() {
        CognitiveCapability capabilities = CognitiveCapability.standard(new CountingBrain());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> capabilities.revoke("brain.does-not-exist", "no reason"));
    }
}
