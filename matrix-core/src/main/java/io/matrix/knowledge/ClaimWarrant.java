package io.matrix.knowledge;

/**
 * How a claim came to be known — the warrant, as Nyaya names it.
 *
 * <p>RECON-W41. The claim's {@link ClaimStatus} says where a claim sits in
 * the epistemic layers: candidate, supported, verified, contested,
 * superseded, refuted, unknown. The warrant says something the status
 * cannot: <b>what kind of thing it was that made this claim known.</b>
 *
 * <p>The two axes are orthogonal, and the reason to keep them apart is
 * concrete. A claim asserted by a human being, a claim read from a
 * publication, a claim observed in an experiment, and a claim deduced from
 * other claims can all end up SUPPORTED — and they support very different
 * things. A machine that cannot tell them apart cannot say why it believes
 * what it believes.
 *
 * <p>The names follow Nyaya's four pramanas, with the Latin name recorded
 * because the doctrine of this project is explicit that a research finding
 * must be attached to a primary source rather than to a mood:
 *
 * <ul>
 *   <li>{@link #OBSERVED} — <i>pratyakṣa</i>: established by direct
 *       perception — an observation, an experiment, a measurement.</li>
 *   <li>{@link #TESTIMONY} — <i>śabda</i>: established by what a source
 *       says, including a human being's assertion, which is testimony and
 *       not verification.</li>
 *   <li>{@link #INFERRED} — <i>anumāna</i>: established by inference from
 *       other claims. Such a claim must name its premises.</li>
 *   <li>{@link #COMPARED} — <i>upamāna</i>: established by comparison with
 *       a known case.</li>
 *   <li>{@link #NONE} — no warrant at all: the system's own proposal, a
 *       hypothesis, which may not enter the trusted layer.</li>
 * </ul>
 *
 * @see DerivationStatus
 */
public enum ClaimWarrant {
    OBSERVED,
    TESTIMONY,
    INFERRED,
    COMPARED,
    NONE
}
