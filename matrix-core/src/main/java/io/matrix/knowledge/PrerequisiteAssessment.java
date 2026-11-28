package io.matrix.knowledge;

import java.util.List;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — whether a scope carries what a task needs.
 *
 * <p><b>What this decides.</b> Whether a <b>scope</b> (the set of sources a competence may cite)
 * carries a <b>sufficient set</b> of the task's necessary evidence. It is a question about the
 * evidence, asked before the answer is produced, and its answer is a list of what is
 * <b>missing</b> — which is what makes a missing prerequisite <b>visible</b> instead of
 * silently answered around.</p>
 *
 * <p><b>What it is not.</b> It is not the applicability decision. Whether a competence may
 * serve a task at all is the route's decision and is made elsewhere; this record answers the
 * narrower question "does this scope carry the evidence the task needs", so that the route can
 * refuse <i>for a named reason</i> and ask for what is missing.</p>
 *
 * @param satisfied whether the scope carries a sufficient set
 * @param reason    the sentence a reader can act on
 * @param carried   the requirements the scope carries
 * @param missing   the requirements the scope does not carry
 * @param satisfiedBy the requirement set that satisfied the scope, when it did
 */
public record PrerequisiteAssessment(
        boolean satisfied,
        String reason,
        List<EvidenceRequirement> carried,
        List<EvidenceRequirement> missing,
        List<EvidenceRequirement> satisfiedBy) {

    public PrerequisiteAssessment {
        carried = List.copyOf(carried == null ? List.of() : carried);
        missing = List.copyOf(missing == null ? List.of() : missing);
        satisfiedBy = List.copyOf(satisfiedBy == null ? List.of() : satisfiedBy);
        reason = reason == null ? "" : reason;
    }

    /**
     * The verdict for a scope that carries nothing of the task's evidence.
     *
     * @param reason the sentence a reader can act on
     * @return the verdict
     */
    public static PrerequisiteAssessment none(String reason) {
        return new PrerequisiteAssessment(false, reason, List.of(), List.of(), List.of());
    }

    /**
     * Whether a scope carries a sufficient set of a task's necessary evidence.
     *
     * <p><b>What "carries" means.</b> A scope is a set of <b>sources</b>, and it carries a
     * requirement when it holds the source the requirement's claim came from. The requirement's
     * <b>provenance</b> is therefore the thing that decides carrying — and it is not the thing
     * that decided <i>necessity</i>, which was decided by the ablation over the workspace's own
     * reading. Keeping the two apart is the whole point of the record.</p>
     *
     * <p><b>Why the alternatives are tried.</b> A task may be done without a particular source
     * if another source states the same thing. The first sufficient set that the scope satisfies
     * decides, and the alternatives are tried in the order the derivation produced them.</p>
     *
     * @param prerequisites the task's necessary evidence and its alternatives
     * @param scopeSourceIds the sources the scope holds
     * @return the verdict
     */
    public static PrerequisiteAssessment of(TaskPrerequisites prerequisites,
            java.util.Set<String> scopeSourceIds) {
        if (prerequisites == null || prerequisites.necessary().isEmpty()) {
            return none("the task's necessary evidence is empty, so no scope can be shown to "
                    + "carry it");
        }
        java.util.Set<String> scope = scopeSourceIds == null ? java.util.Set.of() : scopeSourceIds;
        for (List<EvidenceRequirement> set : prerequisites.sufficientSets()) {
            List<EvidenceRequirement> carried = set.stream()
                    .filter(requirement -> scope.contains(requirement.sourceId()))
                    .toList();
            if (carried.size() == set.size()) {
                return new PrerequisiteAssessment(true,
                        "the scope carries a sufficient set of the evidence the task needs: "
                                + carried.size() + " requirement(s)", carried, List.of(), carried);
            }
        }
        List<EvidenceRequirement> carried = prerequisites.necessary().stream()
                .filter(requirement -> scope.contains(requirement.sourceId()))
                .toList();
        List<EvidenceRequirement> missing = prerequisites.necessary().stream()
                .filter(requirement -> !scope.contains(requirement.sourceId()))
                .toList();
        return new PrerequisiteAssessment(false,
                "the scope carries " + carried.size() + " of the "
                        + prerequisites.necessary().size() + " requirement(s) the task cannot be "
                        + "done without, and no alternative sufficient set is carried either",
                carried, missing, List.of());
    }
}
