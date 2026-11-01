package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds conflicts between claims, and reports them without resolving them
 * (RECON-W41).
 *
 * <p>The mission's §5.3 asks for several kinds of conflict: credible sources
 * that disagree, conflicting definitions, different scientific models,
 * time-dependent claims. The store holds the evidence links that say "this
 * source contradicts that claim" — what was missing was the inspection of
 * conflicts between claims.
 *
 * <p><b>What this detector does and does not do.</b> It reports a conflict
 * when two claims concern the same subject under the same conditions and
 * cannot both hold. It does NOT decide which of them is right, does not count
 * how many records agree with either side, and does not change either
 * claim's status. A conflict that cannot be resolved stays unresolved and
 * visible — which is the whole point.
 *
 * <p>The rules are deliberately conservative, because a false conflict report
 * is worse than a missed one: a system that cries "contradiction" on
 * ordinary paraphrases stops being trusted.
 */
public final class ContradictionDetector {

    /** The share of shared content terms two claims must have to be compared. */
    public static final double MINIMUM_SHARED_TERMS = 0.6;

    /**
     * The conflicts between the claims of a workspace.
     *
     * @param snapshot the workspace to inspect
     * @return the conflicts found, each naming both sides
     */
    public List<Contradiction> detect(KnowledgeSnapshot snapshot) {
        List<Contradiction> found = new ArrayList<>();
        List<Claim> claims = snapshot.claims();

        for (int i = 0; i < claims.size(); i++) {
            for (int j = i + 1; j < claims.size(); j++) {
                Contradiction.Kind kind = compare(claims.get(i), claims.get(j));
                if (kind != null) {
                    found.add(new Contradiction(
                            "con-" + shortHash(claims.get(i).id() + "|" + claims.get(j).id()),
                            snapshot.workspaceId(),
                            claims.get(i).id(),
                            claims.get(j).id(),
                            kind,
                            "structural comparison of the two claims",
                            snapshot.exportedAt()));
                }
            }
        }
        return found;
    }

    /**
     * The kind of conflict between two claims, or null when they do not
     * conflict.
     */
    public Contradiction.Kind compare(Claim left, Claim right) {
        // Different subjects, or one of them about something else entirely.
        if (!left.conceptId().equals(right.conceptId())) {
            return null;
        }
        // A refuted claim is not in conflict with anything: it does not
        // hold, and the record of its refutation is the resolution.
        if (left.status() == ClaimStatus.REFUTED || right.status() == ClaimStatus.REFUTED) {
            return null;
        }
        // The same conditions are required: two claims about different
        // conditions can both hold.
        if (!sameConditions(left, right)) {
            return null;
        }

        Set<String> leftTerms = new HashSet<>(TextFeatures.tokens(left.proposition()));
        Set<String> rightTerms = new HashSet<>(TextFeatures.tokens(right.proposition()));
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return null;
        }
        Set<String> shared = new HashSet<>(leftTerms);
        shared.retainAll(rightTerms);
        double overlap = (double) shared.size() / Math.min(leftTerms.size(), rightTerms.size());
        if (overlap < MINIMUM_SHARED_TERMS) {
            return null;
        }

        boolean leftNegated = isNegated(left);
        boolean rightNegated = isNegated(right);
        if (leftNegated != rightNegated) {
            return Contradiction.Kind.OPPOSITE_POLARITY;
        }
        return null;
    }

    /** Whether the two claims declare the same conditions. */
    private boolean sameConditions(Claim left, Claim right) {
        String leftScope = left.scope() == null ? "" : left.scope().trim().toLowerCase(java.util.Locale.ROOT);
        String rightScope = right.scope() == null ? "" : right.scope().trim().toLowerCase(java.util.Locale.ROOT);
        return leftScope.equals(rightScope);
    }

    /**
     * Whether the claim carries a negation.
     *
     * <p>The raw words are read, because "not" is a stopword and the
     * content terms never carry it.
     */
    private boolean isNegated(Claim claim) {
        for (String word : TextFeatures.rawWords(claim.proposition())) {
            switch (word) {
                case "not", "no", "never", "cannot", "nothing", "neither", "nor" -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static String shortHash(String raw) {
        return Ids.digest(raw).substring(0, 8);
    }
}
