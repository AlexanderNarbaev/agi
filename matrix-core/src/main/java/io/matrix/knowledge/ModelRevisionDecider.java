package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether new evidence revises the model, and builds the refinement.
 *
 * <p><b>Why this exists.</b> The refiner can supersede a claim, but it cannot
 * decide <em>whether</em> it should: the caller has to name the claim to
 * supersede and the claim to put in its place. This class makes that decision
 * from the evidence, in a way that works in any domain: it compares what the
 * new claim is about with what the old claim is about, and it compares how
 * well each is supported.
 *
 * <p><b>The rule.</b> A revision is warranted when the new claim is about the
 * same subject as an established claim (their content terms overlap, or the
 * text of the new source talks about the older claim's subject), says
 * something different (the propositions are not the same), and is either
 * better supported (more independent sources, or the same number and a source
 * that is no less authoritative and no less fresh) or denies what the older
 * claim asserted. Anything weaker is left alone: a new source that says
 * something adjacent is not a revision, and a new source that is weaker is
 * never a revision.
 */
public final class ModelRevisionDecider {

    /** How much of the older claim the new claim has to talk about. */
    public static final double MINIMUM_RELATEDNESS = 0.30;

    /** The decision, with the numbers that produced it. */
    public record Decision(boolean revised, String conceptId, String supersededClaimId, String reason,
                           double relatedness, double oldStrength, double newStrength) {
    }

    private ModelRevisionDecider() {
    }

    /**
     * Compares a new claim with the established claims of its concept.
     *
     * @param snapshot the workspace before the new claim
     * @param conceptId the concept the new claim belongs to
     * @param newProposition the proposition of the new claim
     * @param newConfidence the confidence of the new claim
     * @param newSource the source the new claim rests on
     * @return the decision
     */
    public static Decision decide(KnowledgeSnapshot snapshot, String conceptId, String newProposition,
                                  double newConfidence, Source newSource) {
        Set<String> newTerms = terms(newProposition);
        double newStrength = strength(newConfidence, List.of(newSource), newSource);
        String newText = newSource == null || newSource.excerpt() == null
                ? "" : newSource.excerpt();
        boolean denies = denies(newProposition);
        Claim best = null;
        double bestRelatedness = 0.0;
        double bestStrength = 0.0;
        for (Claim claim : snapshot.claims()) {
            if (!inForce(claim)) {
                continue;
            }
            if (normalise(claim.proposition()).equals(normalise(newProposition))) {
                continue;
            }
            Set<String> oldTerms = terms(claim.proposition());
            double direct = relatedness(newTerms, oldTerms);
            double bridge = bridge(oldTerms, newText);
            double subject = Math.max(direct, bridge);
            if (subject < MINIMUM_RELATEDNESS) {
                continue;
            }
            double strength = strength(claim.confidence(), sourcesOf(snapshot, claim.id()),
                    sourceOf(snapshot, claim.introducedBySourceId()));
            if (best == null || subject > bestRelatedness
                    || (subject == bestRelatedness && strength > bestStrength)) {
                best = claim;
                bestRelatedness = subject;
                bestStrength = strength;
            }
        }
        if (best == null) {
            return new Decision(false, conceptId, null,
                    "no established claim of the workspace is about the same subject, so the new claim is "
                            + "an addition rather than a revision", 0.0, 0.0, newStrength);
        }
        if (!denies && newStrength <= bestStrength) {
            return new Decision(false, best.conceptId(), best.id(),
                    "the new evidence is not stronger than the evidence the claim already rests on "
                            + String.format(Locale.ROOT, "(%.3f against %.3f), so the claim stands",
                            newStrength, bestStrength),
                    bestRelatedness, bestStrength, newStrength);
        }
        String reason = denies
                ? String.format(Locale.ROOT,
                        "the new evidence is about the same subject (relatedness %.2f) and denies what "
                                + "the earlier claim asserted, so the earlier claim is superseded and "
                                + "kept in the history", bestRelatedness)
                : String.format(Locale.ROOT,
                        "the new claim is about the same subject (relatedness %.2f) and is better "
                                + "supported (%.3f against %.3f), so the earlier claim is superseded "
                                + "and kept in the history",
                        bestRelatedness, newStrength, bestStrength);
        return new Decision(true, best.conceptId(), best.id(), reason,
                bestRelatedness, bestStrength, newStrength);
    }

    /**
     * Whether a claim is part of the model that a new claim can revise.
     *
     * <p>A claim that the workspace imported and has not yet corroborated is
     * still part of the model: it is what the workspace believes, and a
     * new source that contradicts it revises it. What is not part of the
     * model is a draft, a claim that has been refuted or superseded, and a
     * claim whose state is simply unknown.
     *
     * @param claim the claim
     * @return whether the claim is in force
     */
    private static boolean inForce(Claim claim) {
        return claim.status() != ClaimStatus.DRAFT
                && claim.status() != ClaimStatus.REFUTED
                && claim.status() != ClaimStatus.SUPERSEDED
                && claim.status() != ClaimStatus.UNKNOWN;
    }

    /**
     * How much of the older claim the new source's own text talks about.
     *
     * <p>A new document often discusses an earlier claim without repeating
     * its words. The overlap between the older claim's content terms and
     * the text of the new source says whether the new source is about that
     * subject at all - which is what decides whether the new claim revises
     * the old one or merely sits next to it.
     *
     * @param oldTerms the content terms of the older claim
     * @param newText the text of the new source
     * @return the overlap, between zero and one
     */
    public static double bridge(Set<String> oldTerms, String newText) {
        if (oldTerms.isEmpty() || newText == null || newText.isBlank()) {
            return 0.0;
        }
        String lower = newText.toLowerCase(Locale.ROOT);
        int shared = 0;
        for (String term : oldTerms) {
            if (lower.contains(term)) {
                shared++;
            }
        }
        return (double) shared / oldTerms.size();
    }

    /**
     * Whether the proposition denies what another claim asserts.
     *
     * <p>The markers are the words a denial is written with. A claim that
     * denies an earlier one is a revision even when it rests on the same
     * number of sources: the earlier claim is no longer adequate, and the
     * reason has to say so.
     *
     * @param proposition the proposition
     * @return whether it denies
     */
    public static boolean denies(String proposition) {
        if (proposition == null) {
            return false;
        }
        String lower = proposition.toLowerCase(Locale.ROOT);
        for (String marker : DENIAL_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** The words a denial is written with. */
    private static final List<String> DENIAL_MARKERS = List.of(
            "does not", "do not", "did not", "is not", "are not", "was not", "were not",
            "cannot", "can not", "never", "no longer", "not directly", "not responsible",
            "rather than", "instead of", "contrary to", "is wrong", "are wrong");

    /**
     * Builds the refinement that carries out a decision.
     *
     * @param decision the decision
     * @param snapshot the workspace before the revision
     * @param newClaim the new claim
     * @param newSource the source the new claim rests on
     * @return the refinement, or {@code null} when no revision is warranted
     */
    public static KnowledgeRefiner.Refinement refinement(Decision decision, KnowledgeSnapshot snapshot,
                                                         Claim newClaim, Source newSource) {
        if (!decision.revised()) {
            return null;
        }
        Claim old = claim(snapshot, decision.supersededClaimId());
        Representation representation = representation(snapshot, decision.conceptId());
        String excerpt = newClaim.proposition();
        return new KnowledgeRefiner.Refinement(decision.conceptId(), old.id(), newClaim.proposition(),
                old.scope(), old.context(), ClaimStatus.SUPPORTED, newClaim.confidence(),
                SourceKind.PUBLICATION, newSource.title(), newSource.locator(), excerpt,
                newSource.authority(), newSource.freshness(), RevisionKind.SUPERSESSION,
                decision.reason(), List.of(newClaim.proposition()), List.of(),
                List.of(old.proposition()), List.of(),
                representation == null ? null : representation.statement(),
                representation == null ? null : representation.level());
    }

    /**
     * How well supported a claim is.
     *
     * <p>The score rewards independent confirmation and an authoritative
     * source, and it never rewards a claim that has no source at all.
     *
     * @param confidence the authored confidence of the claim
     * @param sources the sources the claim rests on
     * @param principal the principal source of the claim
     * @return the strength of the claim
     */
    public static double strength(double confidence, List<Source> sources, Source principal) {
        Set<String> hashes = new LinkedHashSet<>();
        int authority = 0;
        int freshness = 0;
        for (Source source : sources) {
            if (source.contentHash() != null) {
                hashes.add(source.contentHash());
            }
            authority = Math.max(authority, source.authority());
            freshness = Math.max(freshness, source.freshness());
        }
        if (principal != null) {
            authority = Math.max(authority, principal.authority());
            freshness = Math.max(freshness, principal.freshness());
        }
        double independence = 1.0 + 0.1 * Math.max(0, hashes.size() - 1);
        return confidence * independence + authority / 1000.0 + freshness / 10000.0;
    }

    /**
     * How much of the older claim the new claim talks about.
     *
     * @param newTerms the content terms of the new claim
     * @param oldTerms the content terms of the older claim
     * @return the relatedness, between zero and one
     */
    public static double relatedness(Set<String> newTerms, Set<String> oldTerms) {
        if (newTerms.isEmpty() || oldTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new LinkedHashSet<>(newTerms);
        shared.retainAll(oldTerms);
        Set<String> smaller = newTerms.size() <= oldTerms.size() ? newTerms : oldTerms;
        return (double) shared.size() / smaller.size();
    }

    /**
     * The content terms of a proposition.
     *
     * @param proposition the proposition
     * @return its content terms
     */
    public static Set<String> terms(String proposition) {
        Set<String> terms = new LinkedHashSet<>();
        if (proposition == null) {
            return terms;
        }
        for (String word : proposition.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() > 3 && !STOP_WORDS.contains(word)) {
                terms.add(word);
            }
        }
        return terms;
    }

    private static final Set<String> STOP_WORDS = Set.of("that", "this", "with", "from", "have", "been",
            "were", "they", "their", "there", "which", "would", "could", "should", "about", "into", "when",
            "where", "these", "those", "other", "such", "than", "then", "also", "because", "however",
            "although", "between", "through", "under", "over", "most", "some", "each", "many", "much");

    private static String normalise(String proposition) {
        return proposition == null ? "" : proposition.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static List<Source> sourcesOf(KnowledgeSnapshot snapshot, String claimId) {
        List<Source> sources = new ArrayList<>();
        for (Evidence evidence : snapshot.evidence()) {
            if (claimId.equals(evidence.claimId())) {
                Source source = sourceOf(snapshot, evidence.sourceId());
                if (source != null) {
                    sources.add(source);
                }
            }
        }
        return sources;
    }

    private static Source sourceOf(KnowledgeSnapshot snapshot, String sourceId) {
        if (sourceId == null) {
            return null;
        }
        for (Source source : snapshot.sources()) {
            if (sourceId.equals(source.id())) {
                return source;
            }
        }
        return null;
    }

    private static Claim claim(KnowledgeSnapshot snapshot, String claimId) {
        for (Claim claim : snapshot.claims()) {
            if (claim.id().equals(claimId)) {
                return claim;
            }
        }
        throw new IllegalArgumentException("the claim " + claimId + " is not in the workspace");
    }

    private static Representation representation(KnowledgeSnapshot snapshot, String conceptId) {
        for (Representation representation : snapshot.representations()) {
            if (representation.conceptId().equals(conceptId)) {
                return representation;
            }
        }
        return null;
    }
}
