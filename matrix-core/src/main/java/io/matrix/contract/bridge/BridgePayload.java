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
        Provenance provenance) {

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
        if (carried != 1) {
            throw new IllegalArgumentException(
                    "a bridge payload carries exactly one of observation, candidate or evidence link");
        }
        boolean agrees = switch (kind) {
            case OBSERVATION -> observation != null;
            case CANDIDATE -> candidate != null;
            case EVIDENCE_LINK -> evidenceLink != null;
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
        return new BridgePayload(BridgeCapability.OBSERVATION, observation, null, null, provenance);
    }

    /**
     * A payload carrying a candidate belief.
     *
     * @param candidate  the candidate
     * @param provenance the provenance of the delivery
     * @return the payload
     */
    public static BridgePayload ofCandidate(Belief candidate, Provenance provenance) {
        return new BridgePayload(BridgeCapability.CANDIDATE, null, candidate, null, provenance);
    }

    /**
     * A payload carrying a link to evidence.
     *
     * @param evidenceLink the evidence
     * @param provenance   the provenance of the delivery
     * @return the payload
     */
    public static BridgePayload ofEvidenceLink(Evidence evidenceLink, Provenance provenance) {
        return new BridgePayload(BridgeCapability.EVIDENCE_LINK, null, null, evidenceLink, provenance);
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
