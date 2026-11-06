package io.matrix.knowledge;

import java.util.List;

/**
 * A recorded inference: the rule that was applied, the premises it was
 * applied to, and the conclusion it produced (RECON-W41).
 *
 * <p>The mission's §6.1 requires the answer to be explainable "through its
 * supporting claims and operations". An operation that leaves no record
 * cannot be shown, so the derivation is stored, not merely performed.
 *
 * <p>A derivation names:
 * <ul>
 *   <li>the <b>rule</b> — its identifier and a human-readable name;</li>
 *   <li>the <b>premises</b>, in order, as claim identifiers;</li>
 *   <li>the <b>conclusion</b>, as a claim identifier;</li>
 *   <li>the <b>connective</b> that joined the premises;</li>
 *   <li>the <b>status</b> the entailment checker returned.</li>
 * </ul>
 *
 * <p>The premises are claims, not strings, so the chain from a conclusion
 * back to the sources that support its premises is walkable.
 */
public record Derivation(
        String id,
        String workspaceId,
        String ruleId,
        String ruleName,
        Derivation.Connective connective,
        String conclusionClaimId,
        List<String> premiseClaimIds,
        DerivationStatus status,
        String recordedAt) {

    /**
     * How the premises were joined to produce the conclusion.
     *
     * <p>The one-premise connectives are named so that their meaning is
     * determined: IMPLIES and NOT read the FIRST premise, and a rule that
     * gives them more than one premise has no determined meaning. The store
     * refuses such a rule rather than guessing which premise was meant.
     */
    public enum Connective {
        /** Every premise must hold. */
        AND,
        /** At least one premise must hold. */
        OR,
        /** The first premise implies the conclusion. */
        IMPLIES,
        /** The premise, negated, is the conclusion. */
        NOT;

        /** True when the connective reads only the first premise. */
        public boolean usesFirstPremiseOnly() {
            return this == IMPLIES || this == NOT;
        }
    }

    public Derivation {
        premiseClaimIds = List.copyOf(premiseClaimIds == null ? List.of() : premiseClaimIds);
    }

    /** True when the checker found the conclusion to follow from the premises. */
    public boolean valid() {
        return status == DerivationStatus.DERIVED_VALID;
    }
}
