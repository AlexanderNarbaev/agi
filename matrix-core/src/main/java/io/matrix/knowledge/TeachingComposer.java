package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Composes a teaching sequence for a concept (owner directive §9 Stage 6,
 * §30).
 *
 * <p>The plan is built from the representations the workspace already holds,
 * ordered from the least to the most advanced account, and it starts with
 * the prerequisites the concept declares. It does not invent content: every
 * step's statement is an authored representation, and the sources behind it
 * are carried along so the learner can be told where the account came from.
 *
 * <p>The composer never sees the assessment that will measure the teaching.
 * That is deliberate: a lesson tuned against the test is a lesson that
 * measures nothing.
 */
public final class TeachingComposer {

    private final KnowledgeSnapshot snapshot;
    private final AssessmentDataset dataset;

    public TeachingComposer(KnowledgeSnapshot snapshot, AssessmentDataset dataset) {
        this.snapshot = snapshot;
        this.dataset = dataset;
    }

    /** Compose a plan for a concept at a target level. */
    public TeachingPlan compose(String conceptId, AbstractionLevel targetLevel) {
        Concept concept = concept(conceptId);
        List<TeachingStep> steps = new ArrayList<>();

        List<Representation> representations = snapshot.representations().stream()
                .filter(r -> r.conceptId().equals(conceptId))
                .sorted(java.util.Comparator.comparingInt(r -> r.level().ordinal()))
                .toList();

        int order = 1;
        for (Representation representation : representations) {
            if (representation.level().ordinal() > targetLevel.ordinal()) {
                continue;
            }
            steps.add(new TeachingStep(order++, representation.level(), conceptId,
                    representation.statement(),
                    exerciseFor(concept, representation),
                    checkFor(concept, representation),
                    sourcesFor(conceptId)));
        }

        if (steps.isEmpty()) {
            // Nothing authored at or below the target level: teach the
            // concept's claims directly rather than nothing at all.
            for (Claim claim : snapshot.claims()) {
                if (claim.conceptId().equals(conceptId)) {
                    steps.add(new TeachingStep(order++, AbstractionLevel.INTRODUCTORY, conceptId,
                            claim.proposition(), "State the claim in your own words.",
                            "Name the source that supports it.", sourcesFor(conceptId)));
                }
            }
        }

        List<String> prerequisites = snapshot.prerequisites().stream()
                .filter(p -> p.conceptId().equals(conceptId))
                .map(p -> p.requiresConceptId())
                .distinct()
                .toList();

        Set<String> sourceIds = new LinkedHashSet<>();
        for (TeachingStep step : steps) {
            sourceIds.addAll(step.sourceIds());
        }

        return new TeachingPlan(conceptId, concept.name(), targetLevel, steps, prerequisites,
                List.copyOf(sourceIds), dataset == null ? "none" : dataset.id(),
                dataset == null ? 0 : dataset.version());
    }

    private Concept concept(String conceptId) {
        for (Concept concept : snapshot.concepts()) {
            if (concept.id().equals(conceptId)) {
                return concept;
            }
        }
        throw new IllegalArgumentException("No concept " + conceptId);
    }

    private List<String> sourcesFor(String conceptId) {
        Set<String> claimIds = new LinkedHashSet<>();
        for (Claim claim : snapshot.claims()) {
            if (claim.conceptId().equals(conceptId)) {
                claimIds.add(claim.id());
            }
        }
        Set<String> sourceIds = new LinkedHashSet<>();
        for (Evidence evidence : snapshot.evidence()) {
            if (claimIds.contains(evidence.claimId())) {
                sourceIds.add(evidence.sourceId());
            }
        }
        return List.copyOf(sourceIds);
    }

    private static String exerciseFor(Concept concept, Representation representation) {
        return "In your own words, explain to a colleague why "
                + concept.name().toLowerCase(java.util.Locale.ROOT) + " matters, using the account above.";
    }

    private static String checkFor(Concept concept, Representation representation) {
        return "Name one situation in which the account above does not apply, and say why.";
    }
}
