package io.matrix.contract.bridge;

import io.matrix.contract.Belief;
import io.matrix.contract.Evidence;
import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;

/**
 * W51 — the typed content of a bridge message (BRIDGE-4, BRIDGE-5).
 *
 * <p><b>One kind, one content.</b> A bridge payload carries exactly one of an observation, a
 * candidate or an evidence link, and the {@link BridgeCapability} says which. The record
 * refuses to be built otherwise. This is BRIDGE-5 written as an invariant: the bridge may add
 * those three things and nothing else, and a message that carried two of them would give the
 * reader a choice about what it was — a choice a hostile or confused sender should not be
 * able to hand to the epistemic store.</p>
 *
 * <p><b>The provenance is the bridge's own, and it is separate.</b> The nested
 * {@link Observation}, {@link Belief} and {@link Evidence} each carry the provenance of the
 * thing they describe. This record carries a second one: the provenance <i>of the delivery</i>
 * — which sender produced it, when, and from what source. BRIDGE-4 asks for exactly this
 * ("the source of the observation, the instant, the identity of the sender, the episode or
 * session it belongs to"), and a message without it is refused. Keeping the two apart is what
 * lets a reader say "this observation was made by the sensor, and it reached the bridge
 * through the brain runtime at that instant" without either fact overwriting the other.</p>
 *
 * <p><b>Nothing here is trusted.</b> A payload that arrives over the bridge is an unverified
 * observation, held in the disposition the epistemic store gives it. This record does not
 * assert that the observation is true, that the candidate is right, or that the evidence
 * bears on the proposition in the way it claims to; it carries the content faithfully so that
 * the chain A side can decide, and carries the provenance so that the decision can be
 * audited.</p>
 *
 * @param kind        which of the three permitted contents this payload carries (BRIDGE-5)
 * @param observation the observation, when the kind is {@link BridgeCapability#OBSERVATION}
 * @param candidate   the candidate belief, when the kind is {@link BridgeCapability#CANDIDATE}
 * @param evidenceLink the evidence, when the kind is {@link BridgeCapability#EVIDENCE_LINK}
 * @param provenance  the provenance of the delivery itself (BRIDGE-4)
 */
public record BridgePayload(
        BridgeCapability kind,
        Observation observation,
        Belief candidate,
        Evidence evidenceLink,
        CognitiveTaskRequest taskRequest,
        CognitiveTaskResponse taskResponse,
        CognitiveTaskReport taskReport,
        CognitiveTaskCancel taskCancel,
        Provenance provenance) {

    /**
     * The shape the W51 and W52 payloads had.
     *
     * <p>The wave that added the remote cognitive task added two fields; a payload written
     * before it carries neither, and says so by carrying neither.</p>
     *
     * @param kind         the right the message claims
     * @param observation  the observation, when the message carries one
     * @param candidate    the candidate belief, when the message carries one
     * @param evidenceLink the evidence link, when the message carries one
     * @param provenance   where the message came from
     */
    public BridgePayload(BridgeCapability kind, Observation observation, Belief candidate,
                         Evidence evidenceLink, Provenance provenance) {
        this(kind, observation, candidate, evidenceLink, null, null, null, null, provenance);
    }

    public BridgePayload {
        if (kind == null) {
            throw new IllegalArgumentException("bridge payload kind must not be null");
        }
        int carried = 0;
        if (observation != null) {
            carried++;
        }
        if (candidate != null) {
            carried++;
        }
        if (evidenceLink != null) {
            carried++;
        }
        if (taskRequest != null) {
            carried++;
        }
        if (taskResponse != null) {
            carried++;
        }
        if (taskReport != null) {
            carried++;
        }
        if (taskCancel != null) {
            carried++;
        }
        if (carried != 1) {
            throw new IllegalArgumentException(
                    "a bridge payload carries exactly one of observation, candidate, "
                            + "evidence link, task request, task response, task report "
                            + "or task cancellation");
        }
        boolean agrees = switch (kind) {
            case OBSERVATION -> observation != null;
            case CANDIDATE -> candidate != null;
            case EVIDENCE_LINK -> evidenceLink != null;
            case KNOWLEDGE -> candidate != null;
            case INFERENCE_REQUEST -> taskRequest != null;
            case INFERENCE_RESPONSE -> taskResponse != null || taskReport != null;
            case INFERENCE_CANCEL -> taskCancel != null;
        };
        if (!agrees) {
            throw new IllegalArgumentException(
                    "the payload kind does not match the content the payload carries");
        }
    }

    /**
     * A payload carrying an observation.
     *
     * @param observation the observation
     * @param provenance  the provenance of the delivery
     * @return the payload
     */
    public static BridgePayload ofObservation(Observation observation, Provenance provenance) {
        return new BridgePayload(BridgeCapability.OBSERVATION, observation, null, null,
                null, null, null, null, provenance);
    }

    /**
     * A payload carrying a candidate belief.
     *
     * @param candidate  the candidate
     * @param provenance the provenance of the delivery
     * @return the payload
     */
    public static BridgePayload ofCandidate(Belief candidate, Provenance provenance) {
        return new BridgePayload(BridgeCapability.CANDIDATE, null, candidate, null,
                null, null, null, null, provenance);
    }

    /**
     * A payload carrying a link to evidence.
     *
     * @param evidenceLink the evidence
     * @param provenance   the provenance of the delivery
     * @return the payload
     */
    /**
     * A payload that asks a peer to execute a bounded cognitive task.
     *
     * @param request    the typed request
     * @param provenance where the request came from
     * @return the payload
     */
    public static BridgePayload ofTaskRequest(CognitiveTaskRequest request,
                                               Provenance provenance) {
        return new BridgePayload(BridgeCapability.INFERENCE_REQUEST, null, null, null,
                request, null, null, null, provenance);
    }

    /**
     * A payload that returns the result of a bounded cognitive task.
     *
     * @param response   the structured response
     * @param provenance where the response came from
     * @return the payload
     */
    public static BridgePayload ofTaskResponse(CognitiveTaskResponse response,
                                                Provenance provenance) {
        return new BridgePayload(BridgeCapability.INFERENCE_RESPONSE, null, null, null,
                null, response, null, null, provenance);
    }

    /**
     * A payload that returns the secondary report of a bounded cognitive task.
     *
     * <p>The report is the epistemic form of the answer: it names the proposition the
     * peer is talking about, whether it supports or contradicts it, what the peer's
     * confidence rests on, and the records the peer derived it from. The response that
     * came with the report says whether the execution finished; the report says what the
     * peer thinks, and it may contradict.</p>
     *
     * @param report     the report
     * @param provenance where the report came from
     * @return the payload
     */
    public static BridgePayload ofTaskReport(CognitiveTaskReport report,
                                              Provenance provenance) {
        return new BridgePayload(BridgeCapability.INFERENCE_RESPONSE, null, null, null,
                null, null, report, null, provenance);
    }

    /**
     * A payload that withdraws a dispatched cognitive task.
     *
     * @param cancel     the typed cancellation
     * @param provenance where the cancellation came from
     * @return the payload
     */
    public static BridgePayload ofTaskCancel(CognitiveTaskCancel cancel,
                                             Provenance provenance) {
        return new BridgePayload(BridgeCapability.INFERENCE_CANCEL, null, null, null,
                null, null, null, cancel, provenance);
    }

    public static BridgePayload ofEvidenceLink(Evidence evidenceLink, Provenance provenance) {
        return new BridgePayload(BridgeCapability.EVIDENCE_LINK, null, null, evidenceLink,
                null, null, null, null, provenance);
    }

    /**
     * A payload carrying ESTABLISHED KNOWLEDGE — W52, the second direction of the bridge.
     *
     * <p>The belief is the canonical one: the proposition, its status, its confidence, the
     * evidence that bears on it, the contradicting evidence, its validity and its
     * provenance. It is the same record a candidate is — what differs is the standing
     * behind it, and the right the sender exercises is what names that standing.</p>
     *
     * @param belief     the established belief
     * @param provenance the provenance of the delivery
     * @return the payload
     */
    public static BridgePayload ofKnowledge(Belief belief, Provenance provenance) {
        return new BridgePayload(BridgeCapability.KNOWLEDGE, null, belief, null, provenance);
    }

    /**
     * True when the message carries the provenance BRIDGE-4 requires (BRIDGE-4).
     *
     * <p>The check is for the two facts a reader cannot do without: <i>who</i> produced the
     * delivery and <i>when</i>. A provenance that names neither is not a provenance; it is a
     * field that happens to be called one, and the receiver must refuse the message rather
     * than store a record whose origin is unknown.</p>
     *
     * @return true when the delivery's origin and instant are both named
     */
    public boolean hasProvenance() {
        return provenance != null
                && provenance.origin() != null && !provenance.origin().isBlank()
                && provenance.recordedAt() != null && !provenance.recordedAt().isBlank();
    }
}
