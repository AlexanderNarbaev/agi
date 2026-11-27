package io.matrix.cognitive.report;

/**
 * CRJ-01 / COMPETENCE-REACH — how a competence's scope (the set of sources it may cite) is
 * obtained.
 *
 * <p><b>Why this is a separate question from the criterion.</b> A competence of the
 * knowledge-report family answers from a <b>support set</b>: the sources it may cite. Two
 * independent questions decide how well that works. The <b>criterion</b> decides whether the
 * support set <i>may</i> serve a given question; the <b>strategy</b> decides how the support set
 * came to be in the first place. The ADAPTIVE-COMPETENCE milestone settled the criterion by
 * measurement and left the strategy open: its support set is the union of the sources the
 * learning episode <b>cited</b>, and §9 of that milestone named the consequence — a learning
 * episode whose answer cites a document outside the intended support <b>widens</b> the support
 * and makes the procedure applicable to questions it must refuse.</p>
 *
 * <p><b>What the three strategies are.</b> They differ in where the support set's boundary
 * comes from, and each of them answers the widening problem in its own way:</p>
 *
 * <ul>
 *   <li>{@link #CITATION_DERIVED} — the boundary is what the learning episode <b>used</b>. It is
 *       the incumbent, and it is the only one of the three that an incidental citation can
 *       widen.</li>
 *   <li>{@link #DECLARED} — the boundary is what the competence <b>declares</b> it is about. An
 *       observation that reached outside the declaration does not widen it: the declaration
 *       <b>BOUNDS</b> the learning, and a citation outside it is not learned and can therefore
 *       never widen the scope. (The first version of this rule REFUSED such an episode outright;
 *       the pre-registration's §10 records the change and its reason — a refusing rule leaves
 *       the declared arm empty whenever the learning episode cited an incidental source, and an
 *       arm with no scope cannot be compared with anything. The guard against a declaration that
 *       is too narrow is the validation, not this rule.)</li>
 *   <li>{@link #EVIDENCE_DERIVED} — the boundary is what the workspace's <b>evidence</b> for the
 *       learning question reaches. It is derived from the claims the question reaches rather
 *       than from the citations of one answer, so it is neither widened by an incidental
 *       citation nor narrowed by an answer that cited only part of the evidence.</li>
 * </ul>
 *
 * <p><b>What a strategy is NOT.</b> It is not a promotion rule and it is not a criterion. Every
 * strategy is subject to the <b>same</b> validation — the existing held-out validation of the
 * learner — and a candidate scope that does not survive it is not promoted, whatever produced
 * it. The strategies are <b>experimental</b>: the milestone measures them against one another
 * and does not adopt one.</p>
 */
public enum ScopeStrategy {

    /**
     * The incumbent: the support set is the union of the sources the observed journeys drew
     * on.
     */
    CITATION_DERIVED,

    /**
     * The declared scope: the support set is the set of documents the competence declares it is
     * about, and an observation that reached outside it refuses the learning attempt instead of
     * widening the support.
     */
    DECLARED,

    /**
     * The evidence-derived scope: the support set is the set of sources that carry the claims
     * the workspace's own reading reaches for the learning episode's question.
     */
    EVIDENCE_DERIVED
}
