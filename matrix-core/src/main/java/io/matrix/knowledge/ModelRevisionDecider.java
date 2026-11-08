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
        // W51 §6 — THE CANDIDATE IS CHOSEN BY THE PROPOSITION, NOT BY THE
        // DOCUMENT.
        //
        // The candidate used to be the claim with the highest overlap with
        // the new SOURCE'S TEXT — and a document about anything shares
        // words with a document about something else. The W50 evaluation
        // measured the consequence: the claim that was superseded was not
        // the claim the new evidence denied, and three documents that were
        // supposed to change nothing changed the model.
        //
        // The overlap with the new document's text is still MEASURED, and
        // it is reported, but it does not select the candidate: the two
        // PROPOSITIONS have to be about the same subject.
        Claim best = null;
        double bestRelatedness = 0.0;
        double bestStrength = 0.0;
        double bestBridge = 0.0;
        boolean bestDenied = false;
        for (Claim claim : snapshot.claims()) {
            if (!inForce(claim)) {
                continue;
            }
            if (normalise(claim.proposition()).equals(normalise(newProposition))) {
                continue;
            }
            Set<String> oldTerms = terms(claim.proposition());
            double direct = relatedness(newTerms, oldTerms);
            if (direct < MINIMUM_RELATEDNESS) {
                continue;
            }
            double bridge = bridge(oldTerms, newText);
            double strength = strength(claim.confidence(), sourcesOf(snapshot, claim.id()),
                    sourceOf(snapshot, claim.introducedBySourceId()));
            boolean denied = deniesAbout(newProposition, claim);
            // A claim the new proposition ACTUALLY denies outranks a claim
            // that is merely about the same subject.
            if (best == null
                    || (denied && !bestDenied)
                    || (denied == bestDenied && (direct > bestRelatedness
                            || (direct == bestRelatedness && strength > bestStrength)))) {
                best = claim;
                bestRelatedness = direct;
                bestStrength = strength;
                bestBridge = bridge;
                bestDenied = denied;
            }
        }
        if (best == null) {
            return new Decision(false, conceptId, null,
                    "no established claim of the workspace is about the same subject, so the new claim is "
                            + "an addition rather than a revision", 0.0, 0.0, newStrength);
        }
        // W51 §6 — THE DENIAL MUST BE SCOPED, AND IT IS LEXICAL.
        //
        // A negation marker on its own is not a revision: the sentence has
        // to deny what THIS claim asserts. The reason says so, and it says
        // that the denial was read from the text — the relation is a
        // lexical candidate that the scoping confirmed, and a reader can
        // check both.
        if (!bestDenied && newStrength <= bestStrength) {
            return new Decision(false, best.conceptId(), best.id(),
                    "the new evidence is not stronger than the evidence the claim already rests on "
                            + String.format(Locale.ROOT, "(%.3f against %.3f), so the claim stands",
                            newStrength, bestStrength),
                    bestRelatedness, bestStrength, newStrength);
        }
        String reason = bestDenied
                ? String.format(Locale.ROOT,
                        "the new evidence is about the same subject (relatedness %.2f, and the "
                                + "negated clause is about this claim) and denies what the earlier "
                                + "claim asserted, so the earlier claim is superseded and kept in "
                                + "the history; the denial is read from the text of the two "
                                + "propositions and is a lexical relation, not a demonstration of "
                                + "meaning", bestRelatedness)
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
     * <p><b>W51 §6 — A REVISION MAY ONLY REPLACE A CLAIM OF SUFFICIENT
     * SUPPORT.</b> The rule used to be "not a draft, not refuted, not
     * superseded, not unknown" — which left a CANDIDATE claim, an
     * extraction that no procedure had ever examined, as something a new
     * claim could supersede. A claim the workspace never established is
     * not part of what it believes, and replacing it would put a
     * resolution into the history of something that was never asserted.
     *
     * <p>The rule now comes from {@link EpistemicPolicy}: a claim may be
     * revised when it may be cited — that is, when the workspace
     * established it.
     *
     * @param claim the claim
     * @return whether the claim is in force
     */
    private static boolean inForce(Claim claim) {
        return EpistemicPolicy.isInForce(claim.status());
    }

    /**
     * Whether the new proposition denies what the candidate claim asserts.
     *
     * <p><b>W51 §6 — THE DENIAL MUST BE ABOUT THIS CLAIM.</b> A negation
     * marker on its own says only that the sentence carries a negation.
     * The W50 evaluation measured what the rule did with that: of the
     * three documents of the pool that the dataset expected to leave the
     * model alone, every one of them produced a supersession, and the
     * claim that was superseded was chosen by the highest overlap with the
     * new DOCUMENT'S TEXT — a sentence in a document about anything at all
     * shares words with it.
     *
     * <p>The denial is therefore scoped, and it is scoped to the DENIED
     * CLAUSE: the text the negation applies to, not the whole sentence.
     * "However, most scientists working today believe that the asthenosphere
     * does not directly cause motion by the friction of such basal forces"
     * denies the cause of the motion, and the claim it denies is the one
     * about the asthenosphere's competence — the words before the negation
     * ("most scientists working today believe") are the sentence's frame
     * and are not part of what it denies.
     *
     * <p>The rule is the one {@link PremiseAssessment} already applies to
     * a premise: the share of the denied clause's distinctive terms that
     * the candidate claim carries must reach
     * {@value #MINIMUM_DENIAL_COVERAGE}. A sentence that denies something
     * else does not deny this claim, however many words the two share.
     *
     * @param newProposition the proposition of the new claim
     * @param candidate the claim that might be denied
     * @return whether the new proposition denies the candidate
     */
    public static boolean deniesAbout(String newProposition, Claim candidate) {
        if (newProposition == null || candidate == null) {
            return false;
        }
        String clause = deniedClause(newProposition);
        if (clause == null) {
            return false;
        }
        Set<String> clauseStems = PremiseAssessment.distinctiveStems(clause);
        Set<String> candidateStems = PremiseAssessment.distinctiveStems(candidate.proposition());
        if (!PremiseAssessment.covers(clauseStems, candidateStems)) {
            return false;
        }
        // BOTH SIDES DENY THE SAME THING IS AN AGREEMENT, NOT A DENIAL.
        //
        // "In some regions, the boundaries are not well defined" and "Plate
        // boundary zones -- broad belts in which boundaries are not well
        // defined" say the SAME thing, and the lexical reading of the
        // negation would call the second a denial of the first. The
        // candidate's own denial is the check: when the candidate carries a
        // negation of the same clause, the two agree, and a revision is not
        // what is happening.
        String candidateClause = deniedClause(candidate.proposition());
        if (candidateClause != null) {
            Set<String> candidateClauseStems = PremiseAssessment.distinctiveStems(candidateClause);
            if (PremiseAssessment.covers(candidateClauseStems, clauseStems)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The clause a negation applies to, or null when the proposition
     * carries no negation.
     *
     * <p>The clause is the text from the negation onwards, and it stops at
     * the reason: "the boundaries are not well defined BECAUSE the plate
     * movement deforms them" denies that the boundaries are well defined,
     * and the reason is not part of what is denied — and it is the reason
     * that shares its words with every other claim about plate movement.
     *
     * <p>What comes before the negation is the frame of the sentence —
     * "however", "most scientists working today believe" — and it is not
     * part of what is denied either.
     *
     * @param proposition the proposition
     * @return the denied clause, or null
     */
    public static String deniedClause(String proposition) {
        if (proposition == null) {
            return null;
        }
        String lower = proposition.toLowerCase(Locale.ROOT);
        int at = -1;
        int length = 0;
        for (String marker : DENIAL_MARKERS) {
            int found = lower.indexOf(marker);
            if (found >= 0 && (at < 0 || found < at)) {
                at = found;
                length = marker.length();
            }
        }
        if (at < 0) {
            return null;
        }
        String clause = proposition.substring(at + length).strip();
        String lowerClause = clause.toLowerCase(Locale.ROOT);
        for (String reason : REASON_MARKERS) {
            int found = lowerClause.indexOf(reason);
            if (found > 0) {
                clause = clause.substring(0, found).strip();
                lowerClause = clause.toLowerCase(Locale.ROOT);
            }
        }
        return clause.isBlank() ? null : clause;
    }

    /**
     * The connectives that introduce the REASON of a denial.
     *
     * <p>The reason is not part of what is denied, and it is the part that
     * shares its words with the rest of the corpus.
     */
    private static final List<String> REASON_MARKERS = List.of(
            " because ", " owing to ", " due to ", " as a result of ", " since ");

    /**
     * The share of a denied clause's distinctive terms that the candidate
     * claim must carry for the denial to be about it.
     */
    public static final double MINIMUM_DENIAL_COVERAGE = 0.50;

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
     * Whether the proposition carries a denial marker.
     *
     * <p>The markers are the words a denial is written with.
     *
     * <p><b>W51 §6 — THIS IS A LEXICAL CANDIDATE, NOT A VERDICT.</b> The
     * marker says that the sentence carries a negation; it says nothing
     * about WHAT is negated, and it says nothing about whether the
     * negation is true. A revision is made on the strength of
     * {@link #deniesAbout(String, Claim)}, which requires the negated
     * clause to be about the claim it revises, and the reason the decision
     * gives says that the relation was read from the text.
     *
     * @param proposition the proposition
     * @return whether it carries a denial marker
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
                representation == null ? null : representation.level(),
                // W51 §6: the refinement names the source it was read from,
                // so that its own source record is its own record.
                newSource.id());
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
