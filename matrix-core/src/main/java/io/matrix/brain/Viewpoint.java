package io.matrix.brain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Viewpoint — a weighted ensemble of capabilities with a deterministic
 * router (DESIGN-02 §Level-3).
 *
 * <p>Each capability contributes a score; the ensemble answer is the
 * capability with the highest {@code weight × score}, ties broken by name
 * (descending). Weights are fixed at construction — the viewpoint is
 * immutable and deterministic; conflict resolution follows the weight ladder
 * without randomness or wall-clock.
 */
public final class Viewpoint<S, T> {

    /** Scores a stimulus in {@code [0,1]}. */
    @FunctionalInterface
    public interface ScoreFn<S> {
        double score(S stimulus);
    }

    /** Produces the member's answer for the stimulus. */
    @FunctionalInterface
    public interface AnswerFn<S, T> {
        T answer(S stimulus);
    }

    /** One ensemble member: a capability evaluator plus its weight. */
    public record Member<S, T>(String name, double weight,
                               ScoreFn<S> score, AnswerFn<S, T> answer) {

        public Member {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("member name must not be blank");
            }
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be > 0");
            }
            java.util.Objects.requireNonNull(score, "score");
            java.util.Objects.requireNonNull(answer, "answer");
        }
    }

    private final Map<String, Member<S, T>> members = new LinkedHashMap<>();

    /** Adds a member; duplicate names are rejected (deterministic registry). */
    public Viewpoint<S, T> add(Member<S, T> member) {
        if (members.containsKey(member.name())) {
            throw new IllegalArgumentException("duplicate member: " + member.name());
        }
        members.put(member.name(), member);
        return this;
    }

    private Optional<Member<S, T>> route(S stimulus) {
        return members.values().stream()
                .max(Comparator
                        .comparingDouble((Member<S, T> m) -> m.weight() * m.score().score(stimulus))
                        .thenComparing(Member::name, Comparator.reverseOrder()));
    }

    /**
     * Routes the stimulus to the winning member.
     *
     * @return the winning member's answer, or empty when the ensemble is empty
     */
    public Optional<T> decide(S stimulus) {
        return route(stimulus).map(m -> m.answer().answer(stimulus));
    }

    /** Winning member name (for explainability), or empty. */
    public Optional<String> winner(S stimulus) {
        return route(stimulus).map(Member::name);
    }
}
