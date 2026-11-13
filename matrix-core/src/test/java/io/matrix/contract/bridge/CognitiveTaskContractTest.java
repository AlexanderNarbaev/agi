package io.matrix.contract.bridge;

import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W53 — the contracts of the remote cognitive task, and their compatibility with the
 * messages that already travel over the bridge.
 *
 * <p><b>What this test is for.</b> The W53 protocol adds two kinds of message to a bridge
 * that already carries four. Three things must hold at once: the new messages are typed
 * and bounded, the old messages keep exactly the meaning they had, and a receiver that
 * never granted the new rights refuses them — the refusal being the same one a forged
 * message gets. The tests below are those three claims.</p>
 */
class CognitiveTaskContractTest {

    /** The capability chain B exposes for the first remote task. */
    private static final String CAPABILITY = "brain.cognitive-inference";

    /** The output contract the first remote task declares. */
    private static final String OUTPUT_CONTRACT = "cognitive-task-response/v1";

    /** A request for a bounded cognitive inference. */
    private static CognitiveTaskRequest request() {
        return new CognitiveTaskRequest(
                BridgeProtocol.VERSION,
                "tsk-w53-1",
                "req-w53-1",
                new Correlation("corr-w53-1", "ep-w53-parent", "ep-w53-1", "req-w53-1"),
                "ep-w53-parent",
                "chain-a-epistemic-core",
                CAPABILITY,
                "v1",
                CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE,
                "is the lamp lit when power=1 and switch=0",
                Map.of("domain", "micro-world"),
                List.of("the answer must not assert more than the rule"),
                OUTPUT_CONTRACT,
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS,
                4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", BridgeTestFixtures.SENT_AT),
                "hash-of-the-canonical-request",
                null);
    }

    /** A completed response to the request above. */
    private static CognitiveTaskResponse response(CognitiveTaskRequest request) {
        return new CognitiveTaskResponse(
                request.taskId(),
                request.requestId(),
                request.correlation(),
                request.payloadHash(),
                "chain-b-brain-runtime",
                CAPABILITY,
                "v1",
                CognitiveTaskResponse.Status.COMPLETED,
                "the lamp is dark",
                0.82,
                CognitiveTaskResponse.ConfidenceBasis.MEASURED,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of("the responder exposes no hypothesis of its own"),
                List.of("recall", "match"),
                Provenance.of("chain-b", BridgeTestFixtures.SENT_AT),
                "the capability is the one the task named",
                12L,
                "trace://chain-b/w53-1",
                null,
                BridgeTestFixtures.SENT_AT);
    }

    @Test
    @DisplayName("a task request is typed, bounded and carries its correlation")
    void aTaskRequestIsTypedAndBounded() {
        CognitiveTaskRequest request = request();

        assertThat(request.kind()).isEqualTo(CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE);
        assertThat(request.taskId()).isEqualTo("tsk-w53-1");
        assertThat(request.requestId()).isEqualTo("req-w53-1");
        assertThat(request.correlation().correlationId()).isEqualTo("corr-w53-1");
        assertThat(request.requiredCapability()).isEqualTo(CAPABILITY);
        assertThat(request.outputContract()).isEqualTo(OUTPUT_CONTRACT);
        assertThat(request.withinPayloadLimit()).isTrue();
        assertThat(request.context()).containsEntry("domain", "micro-world");
    }

    @Test
    @DisplayName("a task request that asks for a second remote hop is refused")
    void recursiveDelegationIsRefused() {
        CognitiveTaskRequest base = request();
        assertThatThrownBy(() -> new CognitiveTaskRequest(
                base.protocolVersion(), base.taskId(), base.requestId(), base.correlation(),
                base.parentEpisodeId(), base.senderId(), base.requiredCapability(),
                base.capabilityVersion(), base.kind(), base.input(), base.context(),
                base.constraints(), base.outputContract(), base.budgetMillis(),
                base.deadlineMillis(), base.maxPayloadBytes(), 2, base.provenance(),
                base.payloadHash(), base.retry()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote hop");
    }

    @Test
    @DisplayName("a task request without a budget, a deadline or a payload limit is refused")
    void aTaskRequestWithoutItsBoundsIsRefused() {
        CognitiveTaskRequest base = request();
        assertThatThrownBy(() -> new CognitiveTaskRequest(
                base.protocolVersion(), base.taskId(), base.requestId(), base.correlation(),
                base.parentEpisodeId(), base.senderId(), base.requiredCapability(),
                base.capabilityVersion(), base.kind(), base.input(), base.context(),
                base.constraints(), base.outputContract(), 0L,
                base.deadlineMillis(), base.maxPayloadBytes(), base.maxRemoteHops(),
                base.provenance(), base.payloadHash(), base.retry()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CognitiveTaskRequest(
                base.protocolVersion(), base.taskId(), base.requestId(), base.correlation(),
                base.parentEpisodeId(), base.senderId(), base.requiredCapability(),
                base.capabilityVersion(), base.kind(), base.input(), base.context(),
                base.constraints(), base.outputContract(), base.budgetMillis(), 0L,
                base.maxPayloadBytes(), base.maxRemoteHops(),
                base.provenance(), base.payloadHash(), base.retry()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CognitiveTaskRequest(
                base.protocolVersion(), base.taskId(), base.requestId(), base.correlation(),
                base.parentEpisodeId(), base.senderId(), base.requiredCapability(),
                base.capabilityVersion(), base.kind(), base.input(), base.context(),
                base.constraints(), base.outputContract(), base.budgetMillis(),
                base.deadlineMillis(), 0, base.maxRemoteHops(),
                base.provenance(), base.payloadHash(), base.retry()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a payload that carries a task request carries nothing else")
    void aTaskRequestPayloadCarriesNothingElse() {
        BridgePayload payload = BridgePayload.ofTaskRequest(request(),
                BridgeTestFixtures.provenance());

        assertThat(payload.kind()).isEqualTo(BridgeCapability.INFERENCE_REQUEST);
        assertThat(payload.taskRequest()).isNotNull();
        assertThat(payload.observation()).isNull();
        assertThat(payload.candidate()).isNull();
        assertThat(payload.taskResponse()).isNull();
    }

    @Test
    @DisplayName("the payload kind and the content it carries must agree")
    void theKindAndTheContentMustAgree() {
        assertThatThrownBy(() -> new BridgePayload(BridgeCapability.INFERENCE_REQUEST,
                null, null, null, null, null, null, null, BridgeTestFixtures.provenance()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> new BridgePayload(BridgeCapability.INFERENCE_RESPONSE,
                null, null, null, request(), null, null, null, BridgeTestFixtures.provenance()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("the withdrawal survives the payload codec, and only its own kind")
    void theWithdrawalSurvivesThePayloadCodec() {
        BridgePayloadCodec codec = new BridgePayloadCodec();
        CognitiveTaskCancel cancel = new CognitiveTaskCancel(BridgeProtocol.VERSION,
                "tsk-cancel-1", "req-cancel-1",
                new io.matrix.contract.Correlation("corr-cancel-1", "ep-parent",
                        "ep-cancel-1", "req-cancel-1"),
                "the task was withdrawn by the requester", "2026-11-09T12:00:00Z",
                BridgeTestFixtures.provenance());
        BridgePayload payload = BridgePayload.ofTaskCancel(cancel,
                BridgeTestFixtures.provenance());

        BridgePayload decoded = codec.decode(codec.encode(payload));

        assertThat(decoded.kind()).isEqualTo(BridgeCapability.INFERENCE_CANCEL);
        assertThat(decoded.taskCancel()).isNotNull();
        assertThat(decoded.taskCancel().requestId()).isEqualTo("req-cancel-1");
        assertThat(decoded.taskCancel().reason())
                .isEqualTo("the task was withdrawn by the requester");
        assertThat(decoded.taskRequest()).isNull();
        assertThat(decoded.taskResponse()).isNull();
        assertThat(decoded.taskReport()).isNull();

        assertThatThrownBy(() -> new BridgePayload(BridgeCapability.INFERENCE_REQUEST,
                null, null, null, null, null, null, cancel, BridgeTestFixtures.provenance()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("the task messages survive the payload codec")
    void theTaskMessagesSurviveTheCodec() {
        BridgePayloadCodec codec = new BridgePayloadCodec();
        CognitiveTaskRequest request = request();

        BridgePayload decodedRequest = codec.decode(
                codec.encode(BridgePayload.ofTaskRequest(request, BridgeTestFixtures.provenance())));
        assertThat(decodedRequest.kind()).isEqualTo(BridgeCapability.INFERENCE_REQUEST);
        assertThat(decodedRequest.taskRequest().requestId()).isEqualTo(request.requestId());
        assertThat(decodedRequest.taskRequest().requiredCapability()).isEqualTo(CAPABILITY);

        BridgePayload decodedResponse = codec.decode(codec.encode(
                BridgePayload.ofTaskResponse(response(request), BridgeTestFixtures.provenance())));
        assertThat(decodedResponse.kind()).isEqualTo(BridgeCapability.INFERENCE_RESPONSE);
        assertThat(decodedResponse.taskResponse().status())
                .isEqualTo(CognitiveTaskResponse.Status.COMPLETED);
        assertThat(decodedResponse.taskResponse().result()).isEqualTo("the lamp is dark");
    }

    @Test
    @DisplayName("the messages that already travel over the bridge keep their meaning")
    void theOldMessagesKeepTheirMeaning() {
        BridgePayloadCodec codec = new BridgePayloadCodec();
        // The shape the W51 and W52 payloads had still decodes, and still means what it
        // meant: an observation is an observation, and the new fields are simply absent.
        BridgePayload decoded = codec.decode(codec.encode(BridgeTestFixtures.payload()));

        assertThat(decoded.kind()).isEqualTo(BridgeCapability.OBSERVATION);
        assertThat(decoded.observation()).isNotNull();
        assertThat(decoded.taskRequest()).isNull();
        assertThat(decoded.taskResponse()).isNull();
        assertThat(BridgeCapability.parse("OBSERVATION")).contains(BridgeCapability.OBSERVATION);
        assertThat(BridgeCapability.parse("KNOWLEDGE")).contains(BridgeCapability.KNOWLEDGE);
    }

    @Test
    @DisplayName("a response that is not a result must say why")
    void aResponseThatIsNotAResultMustSayWhy() {
        CognitiveTaskRequest request = request();
        assertThatThrownBy(() -> new CognitiveTaskResponse(
                request.taskId(), request.requestId(), request.correlation(),
                request.payloadHash(), "chain-b-brain-runtime", CAPABILITY, "v1",
                CognitiveTaskResponse.Status.REFUSED, null, null, null,
                List.of(), null, null, List.of(), List.of(), List.of(), List.of(),
                Provenance.of("chain-b", BridgeTestFixtures.SENT_AT), null, null, null,
                null, BridgeTestFixtures.SENT_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must say why");
    }

    @Test
    @DisplayName("a confidence must say what it rests on")
    void aConfidenceMustSayWhatItRestsOn() {
        CognitiveTaskRequest request = request();
        assertThatThrownBy(() -> new CognitiveTaskResponse(
                request.taskId(), request.requestId(), request.correlation(),
                request.payloadHash(), "chain-b-brain-runtime", CAPABILITY, "v1",
                CognitiveTaskResponse.Status.COMPLETED, "a result", 0.5, null,
                List.of(), null, null, List.of(), List.of(), List.of(), List.of(),
                Provenance.of("chain-b", BridgeTestFixtures.SENT_AT), null, null, null,
                null, BridgeTestFixtures.SENT_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rests on");
    }

    @Test
    @DisplayName("a response binds itself to the request it answers")
    void aResponseBindsItselfToItsRequest() {
        CognitiveTaskRequest request = request();
        CognitiveTaskResponse answer = response(request);

        assertThat(answer.answers(request)).isTrue();
        assertThat(answer.carriesResult()).isTrue();
        // A response to a different request — or to the same request with a different
        // payload — is not an answer to this one.
        assertThat(answer.answers(new CognitiveTaskRequest(
                request.protocolVersion(), request.taskId(), "req-other",
                request.correlation(), request.parentEpisodeId(), request.senderId(),
                request.requiredCapability(), request.capabilityVersion(), request.kind(),
                request.input(), request.context(), request.constraints(),
                request.outputContract(), request.budgetMillis(), request.deadlineMillis(),
                request.maxPayloadBytes(), request.maxRemoteHops(), request.provenance(),
                "another-hash", request.retry()))).isFalse();
    }

    @Test
    @DisplayName("a receiver that never granted the task rights refuses a task request")
    void aReceiverWithoutTheRightsRefusesTheTask() {
        // The receiver's grant is the permission, and the grant is what the W52 bridge
        // already checks on every message. A receiver that granted only the knowledge
        // rights refuses a task request for exactly the reason it refuses a forged one.
        BridgeReceiver receiver = new BridgeReceiver(
                new HmacBridgeSigner(BridgeTestFixtures.SECRET),
                java.util.EnumSet.of(BridgeCapability.OBSERVATION, BridgeCapability.CANDIDATE,
                        BridgeCapability.EVIDENCE_LINK),
                BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(),
                envelope -> null);

        BridgeEnvelope unsigned = BridgeTestFixtures.envelope(
                BridgePayload.ofTaskRequest(request(), BridgeTestFixtures.provenance()),
                BridgeTestFixtures.REQUEST_ID);
        BridgeEnvelope envelope = BridgeTestFixtures.signedEnvelope(unsigned);

        BridgeAnswer answer = receiver.receiveAnswer(envelope, BridgeTestFixtures.NOW_MILLIS);

        assertThat(answer.decision().accepted()).isFalse();
        assertThat(answer.decision().reason()).contains("did not grant");
    }
}
