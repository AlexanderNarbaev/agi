package io.matrix.api.bridge;

import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ExplainTrace;
import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import io.matrix.contract.bridge.HmacBridgeSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — what the peer does with the tasks a crash left unfinished.
 *
 * <p><b>Why this matters.</b> A request that was in flight when the peer died would
 * otherwise stay {@code ACCEPTED} or {@code RUNNING} for ever: every later delivery of
 * it would be answered as "still running", and the caller would wait for a task nobody
 * is doing. The peer keeps the request's digest and not its payload, so it cannot finish
 * the execution itself — and re-running it would risk a second effect for a task whose
 * first effect may have landed. So the row is closed as a reasoned failure, and the
 * caller learns what happened instead of waiting for a silence.</p>
 *
 * <p><b>What the store promised and the code now keeps.</b> The inbox's own
 * documentation says that a request found in a non-terminal state after a restart "is
 * visible to the reconciliation pass, and is neither silently re-run nor silently
 * failed". Before this test, the pass did not exist.</p>
 */
class TaskPeerRecoveryTest {

    /** The shared secret of the test. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    @TempDir
    Path directory;

    /**
     * A request for the bounded cognitive inference.
     *
     * @param requestId the request id
     * @return the request
     */
    private static CognitiveTaskRequest request(String requestId) {
        return new CognitiveTaskRequest(
                BridgeProtocol.VERSION,
                "tsk-" + requestId,
                requestId,
                new Correlation("corr-" + requestId, "ep-parent", "ep-" + requestId, requestId),
                "ep-parent",
                "chain-a-epistemic-core",
                CognitiveCapability.COGNITIVE_INFERENCE,
                CognitiveCapability.COGNITIVE_INFERENCE_VERSION,
                CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE,
                "a question",
                java.util.Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                5_000L,
                4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId,
                null);
    }

    /** A brain that answers without thinking, and counts how often it was asked. */
    private static final class QuietBrain implements BrainCycle {

        private final java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            return new CycleResult("the lamp is lit", 0.8, 5L, true, List.of("BIR"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }

    @Test
    @DisplayName("a task that was in flight when the peer died is closed with a reason")
    void aTaskThatWasInFlightIsClosedWithAReason() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            store.admit(request("req-in-flight"), "digest", NOW);
            store.markRunning("req-in-flight", NOW);

            assertThat(store.unfinished())
                    .withFailMessage("the row must be visible to the reconciliation")
                    .hasSize(1);

            int closed = store.reconcileUnfinished(NOW);

            assertThat(closed).isEqualTo(1);
            RemoteTaskStore.Row row = store.find("req-in-flight").orElseThrow();
            assertThat(row.state()).isEqualTo(RemoteTaskStore.State.FAILED);
            assertThat(row.refusalReason())
                    .withFailMessage("the failure must say why")
                    .contains("restarted");
            assertThat(store.unfinished())
                    .withFailMessage("no row may stay unfinished after the reconciliation")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("a completed task is untouched by the reconciliation")
    void aCompletedTaskIsUntouchedByTheReconciliation() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            store.admit(request("req-done"), "digest", NOW);
            store.markRunning("req-done", NOW);
            store.markCompleted("req-done", "{\"result\":\"the lamp is lit\"}", "trace-1",
                    false, NOW);

            assertThat(store.reconcileUnfinished(NOW)).isZero();
            RemoteTaskStore.Row row = store.find("req-done").orElseThrow();
            assertThat(row.state()).isEqualTo(RemoteTaskStore.State.COMPLETED);
            assertThat(row.resultJson()).isNotNull();
        }
    }

    @Test
    @DisplayName("a duplicate of a task the reconciliation closed is answered with the "
            + "failure, not with 'still running'")
    void aDuplicateOfAReconciledTaskIsAnsweredWithTheFailure() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            String[] message = envelopeOf("req-reconciled");
            store.admit(request("req-reconciled"), message[1], NOW);
            store.markRunning("req-reconciled", NOW);
            store.reconcileUnfinished(NOW);

            QuietBrain brain = new QuietBrain();
            TaskBridge bridge = new TaskBridge(store, CognitiveCapability.standard(brain),
                    SECRET, () -> NOW);
            String answer = bridge.answer(message[0]);

            assertThat(brain.calls.get())
                    .withFailMessage("the retry must not run the brain again: the task was "
                            + "already admitted, and its logical effect must happen once")
                    .isEqualTo(0);

            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(answer);
            assertThat(root.path("response").isMissingNode())
                    .withFailMessage("the caller must get an answer, not a silence")
                    .isFalse();
            io.matrix.contract.bridge.BridgePayload payload =
                    new io.matrix.contract.bridge.BridgePayloadCodec().decode(
                            root.path("response").path("payload").asText());
            assertThat(payload.taskResponse()).isNotNull();
            assertThat(payload.taskResponse().status())
                    .withFailMessage("the caller must learn that the task failed")
                    .isEqualTo(CognitiveTaskResponse.Status.FAILED);
            assertThat(payload.taskResponse().refusalReason())
                    .withFailMessage("the failure must say why")
                    .contains("restarted");
        }
    }

    /**
     * The signed body of a task request, and the digest of its payload.
     *
     * <p>The digest is the one the envelope itself carries, so that the row the test
     * writes and the message the peer receives are the same message — a different digest
     * would be a conflict, which is a different question.</p>
     *
     * @param requestId the request id
     * @return the envelope and its payload digest
     */
    private String[] envelopeOf(String requestId) {
        io.matrix.contract.bridge.BridgePayload payload =
                io.matrix.contract.bridge.BridgePayload.ofTaskRequest(request(requestId),
                        Provenance.of("chain-a", NOW));
        io.matrix.contract.bridge.BridgeEnvelope envelope =
                new io.matrix.contract.bridge.BridgeEnvelope(BridgeProtocol.NAME,
                        BridgeProtocol.VERSION, requestId, NOW, "chain-a-epistemic-core",
                        io.matrix.contract.bridge.BridgeCapability.INFERENCE_REQUEST,
                        new io.matrix.contract.bridge.BridgePayloadCodec().encode(payload),
                        null);
        io.matrix.contract.bridge.BridgeEnvelope signed =
                envelope.withSignature(envelope.sign(new HmacBridgeSigner(SECRET)));
        return new String[] {
                new io.matrix.contract.bridge.BridgeEnvelopeCodec().encode(signed),
                signed.payloadDigest() };
    }

}
