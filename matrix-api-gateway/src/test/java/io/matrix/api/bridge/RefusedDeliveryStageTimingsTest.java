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
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.HmacBridgeSigner;
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
 * W53 — a delivery that was refused is measured too.
 *
 * <p><b>What this test holds.</b> The peer measures its own stages and keeps them in its
 * own durable row. A measurement written only on the happy path describes the successful
 * deliveries alone — and the deliveries that fail are the ones whose cost is worth
 * knowing. A refusal is the clearest case: the peer wrote the refusal down, so it did
 * work, and that work belongs in the row of the request that was refused.</p>
 *
 * <p><b>What it proves.</b> A delivery that is refused before the brain runs leaves a
 * durable REFUSED row, and that row carries the stages the delivery actually reached.
 * The stages that never happened are absent: the measurement describes what happened, not
 * what could have happened.</p>
 *
 * <p><b>Why the refusal is chosen.</b> The request declares how large its own input may
 * be, and a request that exceeds its own bound is refused before it reaches the brain.
 * The refusal is therefore deterministic, needs no fault injection, and leaves the row
 * exactly where the measurement must be.</p>
 */
class RefusedDeliveryStageTimingsTest {

    /** The shared secret of this test. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-refusal-test".getBytes(StandardCharsets.UTF_8);

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-10T09:30:00Z";

    /** The payload limit the refused request declares for itself. */
    private static final int DECLARED_LIMIT = 16;

    @TempDir
    Path directory;

    /**
     * A refused delivery keeps the stages it reached.
     */
    @Test
    @DisplayName("a refused delivery keeps the stages it reached in its own row")
    void aRefusedDeliveryKeepsItsStages() throws Exception {
        RemoteTaskStore store = new RemoteTaskStore(directory.resolve("tasks.sqlite"));
        CountingBrain brain = new CountingBrain();
        TaskBridge bridge = new TaskBridge(store, CognitiveCapability.standard(brain), SECRET,
                () -> NOW);
        try {
            bridge.answer(envelopeOf(oversizedRequest("req-refused-1")));

            RemoteTaskStore.Row row = store.find("req-refused-1").orElseThrow();
            assertThat(row.state())
                    .withFailMessage("a request that exceeds its own bound is refused")
                    .isEqualTo(RemoteTaskStore.State.REFUSED);
            assertThat(row.refusalReason())
                    .withFailMessage("the row must say why the delivery was refused")
                    .contains("payload limit");
            assertThat(row.stageTimingsJson())
                    .withFailMessage("a delivery that was refused is measured too: the stages "
                            + "it did reach are the stages that happened")
                    .isNotNull();
            Map<String, Double> stages = readStages(row.stageTimingsJson());
            assertThat(stages.keySet())
                    .withFailMessage("the row must carry the stage the refusal reached")
                    .contains("inboxWrite");
            assertThat(stages.keySet())
                    .withFailMessage("a stage that never happened is not a measurement")
                    .doesNotContain("execution", "stateWrite", "outcomeWrite");
            assertThat(brain.calls.get())
                    .withFailMessage("the refusal comes before the brain runs")
                    .isZero();
        } finally {
            store.close();
        }
    }

    /**
     * The measurement of a refusal does not differ from the measurement of a delivery
     * that ran: both are what happened.
     */
    @Test
    @DisplayName("a delivery that ran carries the stages a refusal does not")
    void aDeliveryThatRanCarriesMoreStages() throws Exception {
        RemoteTaskStore store = new RemoteTaskStore(directory.resolve("tasks.sqlite"));
        CountingBrain brain = new CountingBrain();
        TaskBridge bridge = new TaskBridge(store, CognitiveCapability.standard(brain), SECRET,
                () -> NOW);
        try {
            bridge.answer(envelopeOf(ordinaryRequest("req-ordinary-1")));

            Map<String, Double> stages =
                    readStages(store.find("req-ordinary-1").orElseThrow().stageTimingsJson());
            assertThat(stages.keySet())
                    .withFailMessage("a delivery that ran measured the whole of its work")
                    .contains("inboxWrite", "stateWrite", "execution", "outcomeWrite");
            assertThat(brain.calls.get()).isEqualTo(1);
        } finally {
            store.close();
        }
    }

    /**
     * Reads the measurement the peer recorded.
     *
     * @param json the measurement, as canonical JSON
     * @return the stages and their durations
     * @throws Exception when the measurement cannot be read
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Double> readStages(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
    }

    /**
     * A request that exceeds the payload limit it declares for itself.
     *
     * @param requestId the identity of the request
     * @return the request
     */
    private static CognitiveTaskRequest oversizedRequest(String requestId) {
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
                "an input that is much longer than the bound this request declares",
                Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS,
                DECLARED_LIMIT,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId,
                null);
    }

    /**
     * A request within the bounds it declares.
     *
     * @param requestId the identity of the request
     * @return the request
     */
    private static CognitiveTaskRequest ordinaryRequest(String requestId) {
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
                "is the lamp lit",
                Map.of(),
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

    /**
     * The signed envelope that carries a task request.
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
        BridgeEnvelope signed = unsigned.withSignature(unsigned.sign(new HmacBridgeSigner(SECRET)));
        return new BridgeEnvelopeCodec().encode(signed);
    }

    /** A brain that counts how many times it was asked to think. */
    private static final class CountingBrain implements BrainCycle {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            return new CycleResult("the lamp is lit", 0.8, 1L, true, List.of("BIR", "HDC"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }
}
