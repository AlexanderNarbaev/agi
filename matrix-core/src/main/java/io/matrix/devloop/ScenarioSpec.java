package io.matrix.devloop;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Machine-readable scenario specification for a single learning episode (SPEC-000#fr-6).
 *
 * <p>Describes the proposed circumstances of a learning episode: the domain, the target
 * skill being exercised, the difficulty band (zone of proximal development), prerequisite
 * skills, and a deterministic acceptance predicate over {@link Outcome}. Immutable record.
 *
 * <p>The acceptance criterion is stored as a serializable text {@link #acceptanceDescription()}
 * alongside a deterministic {@link #acceptance()} {@code Predicate<Outcome>} (the "factory"
 * producing an accept/reject verdict from an outcome). The predicate MUST be pure: no
 * randomness and no wall-clock (SPEC-000 determinism invariant).
 *
 * @param id                    unique scenario identifier (used as the ZPD tie-break key)
 * @param domain                domain label (e.g. {@code "boolean"}, {@code "craft-graph"})
 * @param targetSkill           skill this scenario exercises
 * @param difficultyBand        difficulty interval {@code [min, max]} ⊂ [0, 1]
 * @param prerequisiteSkills    skills that should be mastered before this scenario
 * @param acceptanceDescription human-readable, serializable description of acceptance
 * @param acceptance            deterministic predicate over {@link Outcome}
 */
public record ScenarioSpec(
        String id,
        String domain,
        String targetSkill,
        DifficultyBand difficultyBand,
        Set<String> prerequisiteSkills,
        String acceptanceDescription,
        Predicate<Outcome> acceptance) {

    public ScenarioSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(targetSkill, "targetSkill");
        Objects.requireNonNull(difficultyBand, "difficultyBand");
        prerequisiteSkills = Set.copyOf(prerequisiteSkills);
        Objects.requireNonNull(acceptanceDescription, "acceptanceDescription");
        Objects.requireNonNull(acceptance, "acceptance");
    }

    /** Evaluate the acceptance predicate against an outcome. */
    public boolean accepts(Outcome outcome) {
        return acceptance.test(outcome);
    }
}
