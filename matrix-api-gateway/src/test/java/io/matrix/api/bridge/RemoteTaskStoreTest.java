package io.matrix.api.bridge;

import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ExplainTrace;
import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — chain B's durable inbox, and the rule the W52 report asked for.
 *
 * <p><b>The defect this closes.</b> The W52 report named it: "chain B's request-id
 * registry for the second direction is process-local. The effect itself is idempotent —
 * the brain's document id is derived from the content — but a restart of chain B between
 * two deliveries of the same request id would teach the proposition twice." For a message
 * that asks a peer to <b>execute a cognitive path</b> the consequence is heavier: the
 * second delivery would execute the task again.</p>
 *
 * <p><b>What the tests below prove.</b> The inbox is durable: the record of a request
 * survives the closing of the store and the opening of a new one over the same file, and
 * the duplicate that follows the restart is refused as a duplicate rather than executed a
 * second time. The conflict rule holds as well: the same request id with a different
 * payload is refused, and nothing is executed.</p>
 */
class RemoteTaskStoreTest {

    /** The shared secret of the tests. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    @TempDir
    Path directory;

    /** A request for the bounded cognitive inference. */
    private static CognitiveTaskRequest request(String requestId, String input) {
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
                input,
                Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS,
                4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId + "-" + input,
                null);
    }

    /**
     * The signed envelope that carries a task request — the body the publisher sends and
     * the receiver reads.
     *
     * @param request the request
     * @return the encoded envelope
     */
    private static String envelopeOf(CognitiveTaskRequest request) {
        BridgePayloadCodec payloadCodec = new BridgePayloadCodec();
        BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME,
                BridgeProtocol.VERSION, request.requestId(), NOW, "chain-a-epistemic-core",
                BridgeCapability.INFERENCE_REQUEST,
                payloadCodec.encode(BridgePayload.ofTaskRequest(request,
                        Provenance.of("chain-a", NOW))), null);
        BridgeEnvelope signed = unsigned.withSignature(
                unsigned.sign(new HmacBridgeSigner(SECRET)));
        return new BridgeEnvelopeCodec().encode(signed);
    }

    /** A brain that counts how many times it was asked to think. */
    private static final class CountingBrain implements BrainCycle {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            return new CycleResult("the answer to " + input, 0.75, 5L, true,
                    List.of("BIR", "HDC"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }

    @Test
    @DisplayName("the first request is admitted, and a duplicate with the same payload is not "
            + "executed twice")
    void aDuplicateIsNotExecutedTwice() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        CountingBrain brain = new CountingBrain();
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            TaskBridge bridge = new TaskBridge(store,
                    CognitiveCapability.standard(brain), SECRET, () -> NOW);
            BridgePayloadCodec codec = new BridgePayloadCodec();

            String first = bridge.answer(envelopeOf(request("req-1", "is the lamp lit")));
            assertThat(first).contains("\"status\":\"ACCEPTED\"");
            assertThat(brain.calls.get()).isEqualTo(1);

            // The same request id with the same payload: the durable record answers it and
            // the capability is not asked a second time.
            String duplicate = bridge.answer(
                    envelopeOf(request("req-1", "is the lamp lit")));
            assertThat(duplicate).contains("\"status\":\"ACCEPTED\"");
            assertThat(brain.calls.get())
                    .withFailMessage("a duplicate must not execute the task again")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("the record of a request survives the restart, and the duplicate after it is "
            + "still not executed")
    void theRecordSurvivesTheRestart() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        CountingBrain brain = new CountingBrain();
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            TaskBridge bridge = new TaskBridge(store,
                    CognitiveCapability.standard(brain), SECRET, () -> NOW);
            bridge.answer(envelopeOf(request("req-2", "is the lamp lit")));
            assertThat(brain.calls.get()).isEqualTo(1);
        }

        // The process restarts: a new store over the same file, a new bridge, the same
        // brain. The record of the first delivery is still there.
        try (RemoteTaskStore reopened = new RemoteTaskStore(database)) {
            assertThat(reopened.size()).isEqualTo(1);
            assertThat(reopened.find("req-2")).isPresent();
            assertThat(reopened.find("req-2").orElseThrow().state())
                    .isEqualTo(RemoteTaskStore.State.COMPLETED);

            TaskBridge bridge = new TaskBridge(reopened,
                    CognitiveCapability.standard(brain), SECRET, () -> NOW);
            bridge.answer(envelopeOf(request("req-2", "is the lamp lit")));
            assertThat(brain.calls.get())
                    .withFailMessage("after a restart the duplicate must still not execute")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("the same request id with a different payload is a conflict, and nothing runs")
    void aConflictingPayloadIsRefused() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        CountingBrain brain = new CountingBrain();
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            TaskBridge bridge = new TaskBridge(store,
                    CognitiveCapability.standard(brain), SECRET, () -> NOW);
            BridgePayloadCodec codec = new BridgePayloadCodec();

            bridge.answer(envelopeOf(request("req-3", "is the lamp lit")));
            assertThat(brain.calls.get()).isEqualTo(1);

            String conflict = bridge.answer(
                    envelopeOf(request("req-3", "is the lamp dark")));
            assertThat(conflict).contains("REFUSED");
            assertThat(brain.calls.get())
                    .withFailMessage("a conflicting payload must not execute")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a request that was accepted but never finished is visible after a restart")
    void anUnfinishedRequestIsVisible() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            store.admit(request("req-4", "is the lamp lit"), "digest-4", NOW);
        }
        try (RemoteTaskStore reopened = new RemoteTaskStore(database)) {
            List<RemoteTaskStore.Row> unfinished = reopened.unfinished();
            assertThat(unfinished).hasSize(1);
            assertThat(unfinished.get(0).requestId()).isEqualTo("req-4");
            assertThat(unfinished.get(0).state()).isEqualTo(RemoteTaskStore.State.ACCEPTED);
            assertThat(unfinished.get(0).effectCommitted()).isFalse();
        }
    }

    @Test
    @DisplayName("a capability this bridge does not serve is unsupported, and nothing runs")
    void anUnservedCapabilityIsUnsupported() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        CountingBrain brain = new CountingBrain();
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            TaskBridge bridge = new TaskBridge(store,
                    CognitiveCapability.standard(brain), SECRET, () -> NOW);
            CognitiveTaskRequest unknown = new CognitiveTaskRequest(
                    BridgeProtocol.VERSION, "tsk-5", "req-5",
                    new Correlation("corr-5", "ep-parent", "ep-5", "req-5"), "ep-parent",
                    "chain-a-epistemic-core", "brain.not-served", "v1",
                    CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE, "anything",
                    Map.of(), List.of(), "cognitive-task-response/v1",
                    CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                    CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS, 4096,
                    CognitiveTaskRequest.MAX_REMOTE_HOPS, Provenance.of("chain-a", NOW),
                    "hash-5", null);

            String answer = bridge.answer(envelopeOf(unknown));
            // The protocol accepted the MESSAGE; the TASK it carried was not served, and
            // the two states are kept apart: the answer says the message was accepted, and
            // the response it carries says the capability was unsupported.
            assertThat(answer).contains("\"status\":\"ACCEPTED\"");
            assertThat(answer).contains("UNSUPPORTED");
            assertThat(answer).contains("serves no capability");
            assertThat(brain.calls.get())
                    .withFailMessage("an unserved capability must not reach the brain")
                    .isZero();
        }
    }

    @Test
    @DisplayName("a bridge without a durable inbox refuses the task instead of serving it "
            + "from memory")
    void aBridgeWithoutAnInboxRefuses() {
        // The W52 defect — a process-local registry — must not survive as a configuration
        // somebody can switch on. A bridge that has no durable inbox cannot say whether a
        // request was already executed, so it refuses.
        CountingBrain brain = new CountingBrain();
        TaskBridge bridge = new TaskBridge(null, CognitiveCapability.standard(brain), SECRET,
                () -> NOW);
        String answer = bridge.answer(envelopeOf(request("req-6", "is the lamp lit")));

        assertThat(answer).contains("REFUSED");
        assertThat(brain.calls.get())
                .withFailMessage("a bridge without an inbox must not execute anything")
                .isZero();
    }

    @Test
    @DisplayName("the bridge grants the task right and the withdrawal right, and nothing else")
    void theBridgeGrantsTheTaskRightAndTheWithdrawalRight() throws Exception {
        Path database = directory.resolve("tasks.sqlite");
        try (RemoteTaskStore store = new RemoteTaskStore(database)) {
            TaskBridge bridge = new TaskBridge(store, CognitiveCapability.standard(null),
                    SECRET, () -> NOW);
            // The peer accepts a task and a withdrawal of that task. It grants no
            // knowledge right, and no right to write into chain A's store: the two
            // rights it holds are about the peer's own work.
            assertThat(bridge.grantedCapabilities())
                    .containsExactlyInAnyOrder(BridgeCapability.INFERENCE_REQUEST,
                            BridgeCapability.INFERENCE_CANCEL)
                    .doesNotContain(BridgeCapability.KNOWLEDGE,
                            BridgeCapability.OBSERVATION, BridgeCapability.CANDIDATE,
                            BridgeCapability.EVIDENCE_LINK);
        }
    }
}
