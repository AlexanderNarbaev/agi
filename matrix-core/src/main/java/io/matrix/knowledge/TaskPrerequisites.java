package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — what a task needs, derived experimentally.
 *
 * <p><b>What this is.</b> The evidence a task cannot be done without, derived from the
 * workspace's own reading of that task. It is <b>not</b> a list of the claims a reading
 * reached: a reading reaches what is <i>relevant</i>, and a task needs what is
 * <i>necessary</i>, and the two are different. The previous milestone measured the difference
 * — a scope that held the first cited claim was admitted while the answer it served was missing
 * a claim the answer needed — and this record is the other side of that measurement.</p>
 *
 * <p><b>How it is derived.</b> By <b>leave-one-source-out ablation</b> of the workspace's own
 * reading:</p>
 * <ol>
 *   <li>the <b>reference answer</b> is the workspace's own reading of the task over the whole
 *       view;</li>
 *   <li>{@code P} is the set of propositions that answer states, and {@code D} is the set of
 *       sources that hold a claim stating a proposition of {@code P};</li>
 *   <li>{@code S*} is the <b>inclusion-minimal</b> subset of {@code D} that still reproduces
 *       {@code P} — computed by dropping, one source at a time in a fixed order, every source
 *       whose removal leaves {@code P} intact;</li>
 *   <li>a claim of the reference answer is a <b>requirement</b> iff its source is in
 *       {@code S*} <b>and</b> its proposition is not stated by any claim of {@code S*} without
 *       that source — that is, iff the source cannot be dropped;</li>
 *   <li>a requirement whose proposition is <b>also</b> stated by a claim of another source
 *       that can carry it alone yields an <b>alternative sufficient set</b>.</li>
 * </ol>
 *
 * <p><b>Why sources and not claims.</b> A competence's scope is a set of <b>sources</b>, so a
 * requirement that could only be expressed at the claim level would not answer the question the
 * scope is asked. Ablating at the source level asks the question the scope asks: which
 * documents does this task actually rest on.</p>
 *
 * <p><b>What this does NOT contain.</b> No threshold, no coverage number, no confidence, no
 * oracle label and no case identifier. The derivation sees the workspace and nothing else.</p>
 *
 * @param taskKey             the task this belongs to: the question, the level and the view's
 *                            fingerprint, so a derivation is never reused for another task
 * @param necessary           the requirements that are jointly necessary
 * @param alternatives        the other sufficient sets, each a set of requirements
 * @param referencePropositions the propositions the reference answer stated
 * @param derivedBy           how the set was derived, in words
 * @param reason              why the set is what it is, in words a reader can act on
 */
public record TaskPrerequisites(
        String taskKey,
        List<EvidenceRequirement> necessary,
        List<List<EvidenceRequirement>> alternatives,
        List<String> referencePropositions,
        String derivedBy,
        String reason) {

    /** The name of the derivation, so that a reader can see how the set was produced. */
    public static final String DERIVATION =
            "leave-one-source-out ablation of the workspace's own reading";

    public TaskPrerequisites {
        necessary = List.copyOf(necessary == null ? List.of() : necessary);
        List<List<EvidenceRequirement>> copied = new ArrayList<>();
        for (List<EvidenceRequirement> alternative : alternatives == null ? List.<List<EvidenceRequirement>>of()
                : alternatives) {
            copied.add(List.copyOf(alternative == null ? List.of() : alternative));
        }
        alternatives = List.copyOf(copied);
        referencePropositions =
                List.copyOf(referencePropositions == null ? List.of() : referencePropositions);
        derivedBy = derivedBy == null ? DERIVATION : derivedBy;
        reason = reason == null ? "" : reason;
    }

    /**
     * Every sufficient set: the necessary one and the alternatives.
     *
     * @return the sufficient sets, the necessary one first
     */
    public List<List<EvidenceRequirement>> sufficientSets() {
        List<List<EvidenceRequirement>> sets = new ArrayList<>();
        if (!necessary.isEmpty()) {
            sets.add(necessary);
        }
        sets.addAll(alternatives);
        return List.copyOf(sets);
    }

    /**
     * The requirement that names a claim.
     *
     * @param claimId the claim's identifier
     * @return the requirement, or null when the set does not name it
     */
    public EvidenceRequirement requirement(String claimId) {
        if (claimId == null) {
            return null;
        }
        for (EvidenceRequirement requirement : necessary) {
            if (requirement.claimId().equals(claimId)) {
                return requirement;
            }
        }
        for (List<EvidenceRequirement> alternative : alternatives) {
            for (EvidenceRequirement requirement : alternative) {
                if (requirement.claimId().equals(claimId)) {
                    return requirement;
                }
            }
        }
        return null;
    }

    /**
     * The claim identifiers of every requirement of every sufficient set.
     *
     * @return the identifiers
     */
    public Set<String> claimIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (EvidenceRequirement requirement : necessary) {
            ids.add(requirement.claimId());
        }
        for (List<EvidenceRequirement> alternative : alternatives) {
            for (EvidenceRequirement requirement : alternative) {
                ids.add(requirement.claimId());
            }
        }
        return Set.copyOf(ids);
    }

    /**
     * The prerequisites of a task the workspace cannot answer at all.
     *
     * @param taskKey the task
     * @param reason  why the task has no requirements
     * @return the empty set, with the reason
     */
    public static TaskPrerequisites none(String taskKey, String reason) {
        return new TaskPrerequisites(taskKey, List.of(), List.of(), List.of(), DERIVATION, reason);
    }
}
