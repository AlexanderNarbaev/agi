package io.matrix.contract.bridge;

import java.util.Collection;
import java.util.Optional;

/**
 * W51 — the rights a sender may ask the bridge for (BRIDGE-5).
 *
 * <p><b>What a bridge may add.</b> BRIDGE-5 says the bridge may add <i>an observation, a
 * candidate or an evidence link, and nothing else</i>. It may not alter claims, confidence,
 * goals, safety policy, dispositions or adjudications. This enum is that sentence, written as
 * code: there are exactly three rights, and the right to change what the system believes is
 * not among them. A list of permissions that omitted the dangerous ones would be a weaker
 * statement than an exhaustive list of the safe ones.</p>
 *
 * <p><b>The check is "is it granted", not "is it forbidden".</b> {@link #grantedBy(Collection)}
 * asks whether a right is among the ones this particular bridge granted to this particular
 * sender. The default is therefore "no": a right that was never granted is not exercised, and
 * the reader does not have to know the whole list of dangerous operations in order to refuse
 * them. That is the difference between a deny-list and a capability, and BRIDGE-5 is a
 * capability statement.</p>
 *
 * <p><b>The same three names are the kinds of payload.</b> An envelope declares its kind in the
 * clear so that a reader can refuse an out-of-scope message before it parses anything; the
 * payload then names the same kind, and the two must agree. A message that says one thing in
 * its header and another in its body is refused rather than reconciled, because reconciling
 * them means guessing which one the sender meant.</p>
 */
public enum BridgeCapability {

    /** The right to offer an observation — something that arrived, before it was interpreted. */
    OBSERVATION,

    /** The right to offer a candidate belief — a proposition the sender proposes. */
    CANDIDATE,

    /** The right to offer a link to evidence bearing on a proposition. */
    EVIDENCE_LINK,

    /**
     * W52 — the right to offer ESTABLISHED KNOWLEDGE.
     *
     * <p><b>This is the second direction of the bridge.</b> The three rights above travel
     * from chain B to chain A: something arrived, a proposition is proposed, evidence bears
     * on it. This one travels the other way — chain A's epistemic core offers what it has
     * established, and chain B's brain takes it into its own memory, so that the brain can
     * answer with what the epistemic core knows.</p>
     *
     * <p><b>The direction is carried by the right, not by a flag.</b> A receiver grants the
     * rights it accepts, so chain A grants {@code OBSERVATION}, {@code CANDIDATE} and
     * {@code EVIDENCE_LINK} — and chain B grants {@code KNOWLEDGE}. A message of the wrong
     * direction is refused as a right that was never granted, which is the same refusal the
     * first direction already had.</p>
     *
     * <p><b>The same proposition, two rights.</b> A CANDIDATE and an ESTABLISHED belief are
     * both the canonical {@code Belief}; what separates them is not the shape but the
     * standing behind it — and the standing is what the right names.</p>
     */
    KNOWLEDGE,

    /**
     * W53 — the right to ASK A PEER TO EXECUTE A BOUNDED COGNITIVE TASK.
     *
     * <p><b>This is the direction of the work, not of the knowledge.</b> The rights above
     * carry what a process <i>knows</i>; this one carries a request that a process
     * <i>does</i>. The two are not interchangeable: the right to receive established
     * knowledge is not the right to make a peer execute a task, and the right to make a peer
     * execute a task is not the right to write into the peer's epistemic store. A receiver
     * grants them separately, and a message that claims the wrong one is refused exactly as
     * a forged message is.</p>
     *
     * <p><b>Where the authorisation lives.</b> In the receiver's grant, never in the
     * message. A request that carried its own permission would be a caller widening its own
     * permission, which is the failure this whole contract exists to make impossible.</p>
     */
    INFERENCE_REQUEST,

    /**
     * W53 — the right to RETURN THE RESULT OF A BOUNDED COGNITIVE TASK.
     *
     * <p>The answer travels back as a report, and the report is evaluated by the caller
     * before it affects anything: the right to answer is not the right to be believed.</p>
     */
    INFERENCE_RESPONSE,

    /**
     * W53 — the right to CANCEL A DISPATCHED COGNITIVE TASK.
     *
     * <p><b>Why it is a right and not a field of the request.</b> A cancellation is a
     * separate act: the request asked for work, and the cancellation withdraws it. Carried
     * as a field of the request, it would arrive with the work itself — too late to
     * prevent anything, and it would make every request carry the authority to withdraw
     * itself. As its own kind, the right is granted (or not) by the receiver, checked on
     * every message, and audited like every other crossing.</p>
     *
     * <p><b>What it does not grant.</b> It does not grant the right to write into the
     * peer's epistemic store, nor to read anything of it, nor to execute anything: the
     * cancellation withdraws a task that was already dispatched and nothing more.</p>
     */
    INFERENCE_CANCEL;

    /**
     * True when this right is among the ones the bridge granted (BRIDGE-5).
     *
     * @param granted the rights this bridge granted to the sender
     * @return true when the right may be exercised
     */
    public boolean grantedBy(Collection<BridgeCapability> granted) {
        return granted != null && granted.contains(this);
    }

    /**
     * The right a wire name names, if it names one at all (BRIDGE-5).
     *
     * <p>An unknown name is not an error to be repaired: it is a message this reader does not
     * understand, and BRIDGE-6 says such a message is refused. The method therefore returns an
     * {@link Optional} rather than throwing, so that the caller decides — a decoder that
     * throws on an unknown kind cannot produce a refusal with a reason attached.</p>
     *
     * @param wireName the name as it appears on the wire
     * @return the right it names, or empty when it names none
     */
    public static Optional<BridgeCapability> parse(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            return Optional.empty();
        }
        for (BridgeCapability capability : values()) {
            if (capability.name().equals(wireName)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }
}
