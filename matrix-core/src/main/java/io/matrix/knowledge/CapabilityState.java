package io.matrix.knowledge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The capability of a learner about one concept, as a single ladder position
 * (owner directive §9, §10).
 *
 * <p>The seven stages of §9 are:
 *
 * <ol>
 *   <li>UNKNOWN — nothing is known;</li>
 *   <li>EXPLORATION — the learner has met the material and not yet taken to
 *       it;</li>
 *   <li>INITIAL LEARNING — the learner has begun to learn it;</li>
 *   <li>PRACTICE AND APPLICATION — the learner can apply what was taught;</li>
 *   <li>COMPETENCE — the learner performs reliably on the assessed
 *       material;</li>
 *   <li>UNDERSTANDING — the learner can explain causes, predict outcomes
 *       and identify counterexamples;</li>
 *   <li>KNOWLEDGE / TEACHABLE CAPABILITY — the learner can teach it.</li>
 * </ol>
 *
 * <p>§10 requires the learning state to be MULTIDIMENSIONAL: recall,
 * explanation, procedure, causal, application, transfer, evaluation,
 * synthesis, teaching, calibration, retention and source-grounding are
 * separate capabilities, and a learner may be competent at one and not at
 * another. The per-dimension states carry that. This record carries the
 * other half: where the learner stands on the ladder for the concept as a
 * whole, and WHY.
 *
 * <p>Two of the stage names are about evidence rather than about a score:
 *
 * <ul>
 *   <li>UNDERSTANDING is not inferred from a high score on recall. It
 *       requires competence on the CAUSAL, TRANSFER and EVALUATION
 *       dimensions, because §9 defines understanding as explaining causes,
 *       predicting outcomes and identifying counterexamples.</li>
 *   <li>KNOWLEDGE / TEACHABLE CAPABILITY is not inferred from being able to
 *       generate an explanation. §9 says so explicitly: "Generating an
 *       explanation is not proof of teaching." The stage therefore requires
 *       a TEACH-BACK that met its declared expectation — and the record
 *       says that the teach-back is machine-to-machine, because no human has
 *       been taught by this system.</li>
 * </ul>
 */
public record CapabilityState(
        String conceptId,
        LearningStage stage,
        Map<LearningDimension, LearningStage> dimensions,
        List<String> dimensionsMissing,
        boolean teachBackMet,
        String teachBackConclusion,
        String note) {

    public CapabilityState {
        dimensions = Map.copyOf(dimensions == null ? Map.of() : dimensions);
        dimensionsMissing = List.copyOf(dimensionsMissing == null ? List.of() : dimensionsMissing);
    }

    /** The record as a map, for the HTTP layer. */
    public Map<String, Object> asMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conceptId", conceptId);
        out.put("stage", stage.name());
        Map<String, String> byName = new LinkedHashMap<>();
        dimensions.forEach((dimension, value) -> byName.put(dimension.name(), value.name()));
        out.put("dimensions", byName);
        out.put("dimensionsMissing", dimensionsMissing);
        out.put("teachBackMet", teachBackMet);
        out.put("teachBackConclusion", teachBackConclusion);
        out.put("note", note);
        return out;
    }
}
