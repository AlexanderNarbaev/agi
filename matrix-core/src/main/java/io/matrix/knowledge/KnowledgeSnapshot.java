package io.matrix.knowledge;

import java.util.List;

/**
 * The complete content of one workspace, in one immutable aggregate.
 *
 * <p>This is the unit of import, export and persistence. A snapshot is
 * self-contained: every evidence record names a claim and a source that are
 * inside it, every representation names a concept that is inside it, and the
 * refinement chain points at representations that are inside it. Importing a
 * snapshot is therefore an atomic operation — either the whole workspace
 * arrives or none of it does.
 *
 * <p>Identifiers are DETERMINISTIC: importing the same pack twice produces
 * the same identifiers, so re-importing is idempotent rather than
 * duplicative. That is a requirement of the product, not a convenience: a
 * user who imports a pack twice must not end up with two copies of every
 * concept.
 *
 * <h2>W45.2 — the recorded conflicts are part of the content</h2>
 *
 * <p>The snapshot used to leave the {@code contradiction} rows out, and that
 * was a hole with a name: a workspace's conflicts are records ABOUT two
 * claims, and a replacement that erased them erased the evidence that the
 * system had found a conflict at all. The owner's decision is that a
 * destructive reload leaves a recoverable snapshot, and a snapshot that
 * drops the conflicts is not a snapshot of the workspace.</p>
 *
 * <p>The two older constructors are kept, so that the callers that never
 * reason about inferences — and the callers that never reason about
 * conflicts — do not have to say so. A snapshot without conflicts is a
 * workspace in which no conflict was ever recorded, and that is not the same
 * thing as a workspace whose conflicts were lost.</p>
 *
 * <p><b>The goals are in the snapshot for the same reason.</b> A goal and its
 * subtasks are records of what was being attempted, and a replacement that
 * dropped them would drop the plan with the knowledge — which is exactly what
 * the mission warns against when it says that restoring a knowledge database
 * is not the same as restoring an active goal.</p>
 */
public record KnowledgeSnapshot(
        String workspaceId,
        String workspaceName,
        List<Concept> concepts,
        List<Source> sources,
        List<Claim> claims,
        List<Evidence> evidence,
        List<Representation> representations,
        List<Prerequisite> prerequisites,
        List<KnowledgeRevision> revisions,
        List<Derivation> derivations,
        List<Contradiction> contradictions,
        List<LearningGoal> goals,
        String exportedAt) {

    /**
     * A snapshot without recorded derivations.
     *
     * <p>The ten-component form is kept so that the callers that never
     * reason about inferences do not have to say so. A snapshot without
     * derivations is a workspace whose rules are not recorded, and the
     * reasoning engine reports exactly that.
     */
    public KnowledgeSnapshot(String workspaceId, String workspaceName,
            List<Concept> concepts, List<Source> sources, List<Claim> claims,
            List<Evidence> evidence, List<Representation> representations,
            List<Prerequisite> prerequisites, List<KnowledgeRevision> revisions,
            String exportedAt) {
        this(workspaceId, workspaceName, concepts, sources, claims, evidence, representations,
                prerequisites, revisions, List.of(), List.of(), List.of(), exportedAt);
    }

    /**
     * A snapshot without recorded derivations and without recorded conflicts.
     *
     * <p>The nine-component form is kept so that the callers that never
     * reason about inferences do not have to say so.
     */
    public KnowledgeSnapshot(String workspaceId, String workspaceName,
            List<Concept> concepts, List<Source> sources, List<Claim> claims,
            List<Evidence> evidence, List<Representation> representations,
            List<Prerequisite> prerequisites, List<KnowledgeRevision> revisions,
            List<Derivation> derivations, String exportedAt) {
        this(workspaceId, workspaceName, concepts, sources, claims, evidence, representations,
                prerequisites, revisions, derivations, List.of(), List.of(), exportedAt);
    }

    public KnowledgeSnapshot {
        concepts = List.copyOf(concepts == null ? List.of() : concepts);
        sources = List.copyOf(sources == null ? List.of() : sources);
        claims = List.copyOf(claims == null ? List.of() : claims);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        representations = List.copyOf(representations == null ? List.of() : representations);
        prerequisites = List.copyOf(prerequisites == null ? List.of() : prerequisites);
        revisions = List.copyOf(revisions == null ? List.of() : revisions);
        derivations = List.copyOf(derivations == null ? List.of() : derivations);
        contradictions = List.copyOf(contradictions == null ? List.of() : contradictions);
        goals = List.copyOf(goals == null ? List.of() : goals);
    }

    /** An empty workspace. */
    public static KnowledgeSnapshot empty(String workspaceId, String workspaceName) {
        return new KnowledgeSnapshot(workspaceId, workspaceName,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null);
    }
}
