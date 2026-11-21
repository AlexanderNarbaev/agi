package io.matrix.cognitive.report;

import io.matrix.cognitive.algorithms.ValidationKind;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CRJ-01 / OD-082 — a LEARNED CLAIM: a statement the system has validated, with the
 * provenance of that validation.
 *
 * <p><b>What it is.</b> A workspace claim — the production claim, with the production id — plus
 * the record that the system <b>checked</b> it: on held-out questions it was never proposed
 * from, against the workspace's own text, and it held every time.</p>
 *
 * <p><b>Why it is a claim and not a procedure.</b> The procedure of arm C says <b>which
 * sources</b> an answer may be drawn from; it is a domain. A learned claim says <b>what the
 * answer is</b>: one statement, checked, with its provenance. The two compose: a procedure
 * narrows the reading and a learned claim names the statement inside it.</p>
 *
 * <p><b>What it may assert.</b> Only what the source <b>literally contains</b>. The
 * proposition must occur <b>verbatim</b> in the source's text, and the gate that decides it is
 * the same question the CRJ-01 <b>source-span oracle</b> asks — computed here against the
 * workspace's own copy of the text, and computed there against the corpus file's bytes. Two
 * independent implementations of one question: the system's gate is fail-closed, and the
 * instrument is what checks it at measurement time.</p>
 *
 * <p><b>Why it carries a version.</b> A learned claim is a <b>claim about a claim</b>, and a
 * claim that was checked against one text may be checked against another. The version is the
 * distinction, and a version is never rewritten: a later validation is a new version beside the
 * old one, not an edit of it.</p>
 *
 * <p><b>What it may be used in.</b> Only inside its <b>applicability</b>: the concept it
 * belongs to and the support set its validation covered. A learned claim is not a fact about
 * the world; it is a fact about <b>this workspace, in this domain, at this version</b>, and a
 * journey that cites it outside that domain would be citing it where it was never checked.</p>
 *
 * @param id              the learned claim's identity
 * @param version         the version of the validation
 * @param workspaceId     the workspace it belongs to
 * @param claimId         the production claim id of the workspace claim
 * @param proposition     the statement, as the workspace holds it
 * @param conceptId       the concept it belongs to
 * @param sourceId        the source it was validated against
 * @param support         the source ids the validation covered
 * @param validationCases how many held-out cases it was validated on
 * @param agreement       the measured agreement on those cases
 * @param validationKind  how the validation was done
 * @param learnedInEpisode the episode it was learned in
 * @param validationNote  what its standing rests on
 * @param promotedAt      the instant it entered the active set
 */
public record LearnedClaim(String id,
                            String version,
                            String workspaceId,
                            String claimId,
                            String proposition,
                            String conceptId,
                            String sourceId,
                            Set<String> support,
                            int validationCases,
                            double agreement,
                            ValidationKind validationKind,
                            String learnedInEpisode,
                            String validationNote,
                            String promotedAt) {

    public LearnedClaim {
        support = Set.copyOf(support == null ? Set.of() : support);
        validationKind = validationKind == null ? ValidationKind.INDEPENDENT : validationKind;
        learnedInEpisode = learnedInEpisode == null ? "" : learnedInEpisode;
        validationNote = validationNote == null ? "" : validationNote;
        promotedAt = promotedAt == null ? "" : promotedAt;
    }

    /** The prefix that marks a record as a learned claim. */
    public static final String ID_PREFIX = "learned-claim.";

    /** The version a first validation carries. */
    public static final String FIRST_VERSION = "1";

    /**
     * The identity of a learned claim: its proposition, its source and its version.
     *
     * <p>The proposition and the source are the material — a claim checked against another
     * document is a different learned claim — and the version is the distinction, so a
     * re-validation is a new identity rather than an edit.</p>
     *
     * @param workspaceId the workspace
     * @param proposition the statement
     * @param sourceId    the source it was validated against
     * @param version     the version
     * @return the identity
     */
    public static String idFor(String workspaceId, String proposition, String sourceId,
                               String version) {
        String material = workspaceId + "|" + proposition.strip().toLowerCase(java.util.Locale.ROOT)
                + "|" + sourceId + "|" + version;
        // the repository's digest is 32 bits of hex, so the identity is the digest itself -
        // and the material is long enough that the proposition, the source and the version
        // are all in it
        return ID_PREFIX + io.matrix.knowledge.Ids.digest(material);
    }

    /**
     * The learned claim of one validation.
     *
     * @param workspaceId     the workspace
     * @param claimId         the production claim id
     * @param proposition     the statement
     * @param conceptId       the concept
     * @param sourceId        the source
     * @param support         the sources the validation covered
     * @param validationCases how many held-out cases
     * @param agreement       the measured agreement
     * @param learnedInEpisode the episode it was learned in
     * @param validationNote  what its standing rests on
     * @param promotedAt      the instant
     * @return the learned claim
     */
    public static LearnedClaim of(String workspaceId, String claimId, String proposition,
                                   String conceptId, String sourceId, Set<String> support,
                                   int validationCases, double agreement,
                                   String learnedInEpisode, String validationNote,
                                   String promotedAt) {
        String version = FIRST_VERSION;
        return new LearnedClaim(idFor(workspaceId, proposition, sourceId, version), version,
                workspaceId, claimId, proposition, conceptId, sourceId,
                new LinkedHashSet<>(support == null ? Set.of() : support), validationCases,
                agreement, ValidationKind.INDEPENDENT, learnedInEpisode, validationNote,
                promotedAt);
    }

    /**
     * Whether this learned claim may be used in a journey about this concept.
     *
     * @param conceptId the concept the question resolved to
     * @return whether the claim is inside its applicability
     */
    public boolean appliesTo(String conceptId) {
        return this.conceptId != null && this.conceptId.equals(conceptId);
    }

    /**
     * The learned claims that apply to a question, by the concept it resolved to.
     *
     * @param claims    the learned claims of the workspace
     * @param conceptId the concept the question resolved to
     * @return the applicable ones, in the order they were given
     */
    public static List<LearnedClaim> applicableTo(List<LearnedClaim> claims, String conceptId) {
        if (claims == null || claims.isEmpty() || conceptId == null) {
            return List.of();
        }
        return claims.stream().filter(claim -> claim.appliesTo(conceptId)).toList();
    }
}
