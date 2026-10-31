package io.matrix.knowledge;

import java.util.List;

/**
 * A structured interpretation of one or more claims at a stated abstraction
 * level (owner directive §11, §14).
 *
 * <p>A representation is NOT a claim: it is an account built FROM claims, at
 * a level, for an audience, under stated assumptions. The refinement chain
 * ({@link #refinesRepresentationId}) preserves the relationship, so the
 * introductory account survives the arrival of the mechanistic one.
 */
public record Representation(
        String id,
        String workspaceId,
        String conceptId,
        AbstractionLevel level,
        String audience,
        String statement,
        String scope,
        List<String> assumptions,
        List<String> includedFacts,
        List<String> deliberatelyOmitted,
        List<String> validityConditions,
        List<String> prerequisiteConceptIds,
        String refinesRepresentationId,
        double confidence,
        String createdAt) {

    public Representation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("representation id must not be blank");
        }
        if (conceptId == null || conceptId.isBlank()) {
            throw new IllegalArgumentException("representation concept id must not be blank");
        }
        if (level == null) {
            throw new IllegalArgumentException("representation level must not be null");
        }
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("representation statement must not be blank");
        }
        assumptions = List.copyOf(assumptions == null ? List.of() : assumptions);
        includedFacts = List.copyOf(includedFacts == null ? List.of() : includedFacts);
        deliberatelyOmitted = List.copyOf(deliberatelyOmitted == null ? List.of() : deliberatelyOmitted);
        validityConditions = List.copyOf(validityConditions == null ? List.of() : validityConditions);
        prerequisiteConceptIds = List.copyOf(prerequisiteConceptIds == null ? List.of() : prerequisiteConceptIds);
        if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("representation confidence must be within [0,1]");
        }
    }
}
