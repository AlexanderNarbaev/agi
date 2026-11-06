package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A learner that can only answer out of the STRUCTURED claims it was taught
 * (RECON-W41).
 *
 * <p>The learner of RECON-W40 could not be used to measure teachability. It
 * ignored the question entirely and returned the concatenation of the lesson,
 * so the "score" measured whether the lesson contained the assessment's key
 * terms. The AHRQ's own guidance on teach-back names that as the anti-pattern
 * to avoid: "If they parrot your words back to you, they may not have
 * understood."
 *
 * <p>This learner is closed-book and structured:
 *
 * <ul>
 *   <li>it holds the <b>claims</b> it was taught - the records with their
 *       propositions, their scopes, their polarity and their evidence - and
 *       <b>not</b> the prose of the lesson;</li>
 *   <li>it <b>chooses</b> an answer from the alternatives the item offers,
 *       so it must attend to the question;</li>
 *   <li>it matches an alternative against its claims by entailment-style
 *       comparison - the content terms, the <b>polarity</b>, the
 *       <b>conditions</b> and the <b>evidence</b> - rather than by the
 *       vocabulary of the lesson;</li>
 *   <li>it states a <b>confidence</b> before it is scored, so that the
 *       confidence can be checked against the outcome instead of being
 *       assumed.</li>
 * </ul>
 *
 * <p>It is a synthetic learner. Nothing measured with it is evidence about
 * human learning, and the experiment that uses it says so.
 */
public final class StructuredLearner {

    /** What the learner chose, and how sure it was. */
    public record Attempt(
            String itemId,
            String alternativeId,
            String misconceptionId,
            double confidence,
            String rationale) {
    }

    private final List<Claim> claims;
    private final List<Evidence> evidence;

    /**
     * @param claims the claims this learner was taught, and nothing else
     * @param evidence the evidence links the learner may consult
     */
    public StructuredLearner(List<Claim> claims, List<Evidence> evidence) {
        this.claims = List.copyOf(claims == null ? List.of() : claims);
        this.evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }

    /** The claims this learner holds. */
    public List<Claim> heldClaims() {
        return claims;
    }

    /**
     * Answers one item by choosing an alternative.
     *
     * @param item the item, with its alternatives and its key
     * @return the attempt, or null when the item offers no alternatives
     */
    public Attempt attempt(AssessmentItem item) {
        if (!item.hasAlternatives()) {
            return null;
        }
        List<AssessmentItem.Alternative> alternatives = item.alternatives();
        List<Double> scores = new ArrayList<>();
        for (AssessmentItem.Alternative alternative : alternatives) {
            scores.add(score(alternative));
        }

        int best = 0;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) > scores.get(best)) {
                best = i;
            }
        }
        double bestScore = scores.get(best);
        double secondScore = 0.0;
        for (int i = 0; i < scores.size(); i++) {
            if (i != best && scores.get(i) > secondScore) {
                secondScore = scores.get(i);
            }
        }
        // The confidence is the margin between the best and the runner-up,
        // not a constant: a learner that cannot tell two alternatives apart
        // says so.
        double confidence = Math.max(0.0, Math.min(1.0, bestScore - secondScore));

        AssessmentItem.Alternative chosen = alternatives.get(best);
        String rationale = "the best structural match among " + alternatives.size()
                + " alternatives, on the terms, the polarity, the conditions and the evidence";

        return new Attempt(item.id(), chosen.id(), chosen.misconceptionId(), confidence, rationale);
    }

    /**
     * How well the taught claims support one alternative.
     *
     * <p>Four components, each of which can decide the match:
     *
     * <ul>
     *   <li>the <b>terms</b> the alternative and the claim share;</li>
     *   <li>the <b>polarity</b> - an alternative that denies what the claim
     *       asserts does not match it;</li>
     *   <li>the <b>conditions</b> - the alternative's own conditions must not
     *       contradict the claim's scope;</li>
     *   <li>the <b>evidence</b> - a claim the sources contradict scores below
     *       a claim the sources support, weighted by the evidence's
     *       quality.</li>
     * </ul>
     */
    private double score(AssessmentItem.Alternative alternative) {
        Set<String> alternativeTerms = new LinkedHashSet<>(TextFeatures.tokens(alternative.text()));
        double best = 0.0;
        for (Claim claim : claims) {
            double score = scoreAgainst(alternative, alternativeTerms, claim);
            if (score > best) {
                best = score;
            }
        }
        return best;
    }

    private double scoreAgainst(AssessmentItem.Alternative alternative,
                                 Set<String> alternativeTerms, Claim claim) {
        Set<String> claimTerms = new LinkedHashSet<>(TextFeatures.tokens(claim.proposition()));
        if (alternativeTerms.isEmpty() || claimTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new LinkedHashSet<>(alternativeTerms);
        shared.retainAll(claimTerms);
        double termScore = (double) shared.size() / alternativeTerms.size();

        // The polarity must agree: a denial is not a match for an assertion.
        boolean alternativeNegated = isNegated(alternative.text());
        boolean claimNegated = isNegated(claim.proposition());
        double polarityScore = alternativeNegated == claimNegated ? 1.0 : 0.0;

        // The conditions must not contradict the claim's scope.
        double scopeScore = scopeAgreement(alternative.text(), claim.scope());

        // The evidence decides what the sources say about the claim.
        double evidenceScore = evidenceScore(claim);

        return 0.45 * termScore + 0.20 * polarityScore + 0.15 * scopeScore + 0.20 * evidenceScore;
    }

    /**
     * Whether the alternative's conditions are compatible with the claim's
     * declared scope.
     *
     * <p>Only a contradiction counts as disagreement: the claim's scope
     * saying "under light" does not prevent an alternative about "in
     * darkness" from being about something else entirely.
     */
    private double scopeAgreement(String alternativeText, String scope) {
        if (scope == null || scope.isBlank()) {
            return 0.5;
        }
        Set<String> scopeTerms = new LinkedHashSet<>(TextFeatures.tokens(scope));
        Set<String> alternativeTerms = new LinkedHashSet<>(TextFeatures.tokens(alternativeText));
        Set<String> shared = new LinkedHashSet<>(scopeTerms);
        shared.retainAll(alternativeTerms);
        if (shared.isEmpty()) {
            return 0.5;
        }
        // The scope names the conditions; if the alternative contradicts one
        // of them explicitly, the agreement is low.
        if (isNegated(alternativeText)) {
            return 0.2;
        }
        return 1.0;
    }

    /**
     * What the evidence says about a claim.
     *
     * <p>A claim the sources support scores above a claim they contradict,
     * and the quality of the evidence moves the score. The learner is not
     * told which claims are true; it is shown what the sources say.
     */
    private double evidenceScore(Claim claim) {
        double score = 0.5;
        for (Evidence link : evidenceOf(claim)) {
            double quality = Math.max(0.0, Math.min(1.0, link.quality() / 100.0));
            if (link.relation() == EvidenceRelation.SUPPORTS) {
                score = Math.max(score, 0.5 + 0.5 * quality);
            } else if (link.relation() == EvidenceRelation.CONTRADICTS) {
                score = Math.min(score, 0.5 - 0.5 * quality);
            }
        }
        return Math.max(0.0, Math.min(1.0, score));
    }

    private List<Evidence> evidenceOf(Claim claim) {
        List<Evidence> links = new ArrayList<>();
        for (Evidence link : evidence) {
            if (link.claimId().equals(claim.id())) {
                links.add(link);
            }
        }
        return links;
    }

    private boolean isNegated(String text) {
        for (String word : TextFeatures.rawWords(text)) {
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
}
