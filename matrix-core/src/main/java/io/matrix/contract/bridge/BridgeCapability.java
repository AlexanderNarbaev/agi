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
    EVIDENCE_LINK;

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
