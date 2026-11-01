package io.matrix.knowledge.acquisition;

import java.util.List;
import java.util.Set;

/**
 * The rules that decide whether a claim may be promoted (RECON-W42.2).
 *
 * <p>The mission's §5.4 asks for "precise transition rules between layers"
 * and says: "Ten documents copying the same source do not constitute ten
 * independent confirmations."
 *
 * <p><b>That last rule is the one this class exists for.</b> It is easy to
 * count the pieces of evidence behind a claim and to call the claim
 * verified. It is wrong whenever the pieces of evidence are the same
 * document seen ten times — a copied text, a syndicated article, the same
 * book reprinted. The policy therefore asks for the **independent** count:
 * the number of distinct source *content hashes* behind the evidence, not
 * the number of evidence records.
 */
public final class PromotionPolicy {

    private PromotionPolicy() {
    }

    /**
     * Whether a claim may move from one layer to another.
     *
     * @param from the layer the claim is in
     * @param to the layer it would move to
     * @param independentSources the number of INDEPENDENT sources behind the
     *     claim — distinct content hashes, not distinct files
     * @param hasEvidence whether any evidence at all is behind the claim
     * @param disputed whether a contradicting claim is behind it
     * @return whether the move is legal
     */
    public static boolean mayPromote(PromotionStage from, PromotionStage to,
                                      int independentSources, boolean hasEvidence,
                                      boolean disputed) {
        if (from == null || to == null || !from.mayMoveTo(to)) {
            return false;
        }
        return switch (to) {
            case EXTRACTED, CANDIDATE -> true;
            case SUPPORTED -> hasEvidence && independentSources >= 1;
            case VERIFIED -> hasEvidence && independentSources >= 2;
            case DISPUTED -> hasEvidence && disputed;
            case SUPERSEDED, INVALIDATED -> true;
            default -> false;
        };
    }

    /**
     * The number of INDEPENDENT sources behind the evidence.
     *
     * <p>Two evidence records pointing at two files with the same content
     * hash are one source seen twice. This is the rule that makes "ten
     * documents copying the same source" not a confirmation.
     *
     * @param sourceHashes the content hash of the source behind each piece
     *     of evidence
     * @return the number of distinct content hashes
     */
    public static int independentSources(List<String> sourceHashes) {
        if (sourceHashes == null) {
            return 0;
        }
        Set<String> distinct = new java.util.LinkedHashSet<>();
        for (String hash : sourceHashes) {
            if (hash != null && !hash.isBlank()) {
                distinct.add(hash);
            }
        }
        return distinct.size();
    }

    /**
     * The reason a promotion was refused.
     *
     * <p>A refusal with no reason is a refusal a reader cannot check.
     */
    public static String refusalReason(PromotionStage from, PromotionStage to,
                                       int independentSources, boolean hasEvidence) {
        if (from == null || to == null) {
            return "the layer is unknown";
        }
        if (!from.mayMoveTo(to)) {
            return from + " does not move to " + to;
        }
        if (to == PromotionStage.SUPPORTED && !hasEvidence) {
            return "the claim has no evidence behind it";
        }
        if (to == PromotionStage.VERIFIED && independentSources < 2) {
            return "the claim has " + independentSources + " independent source(s); "
                    + "verification needs two - copies of one source are not two sources";
        }
        return "";
    }
}
