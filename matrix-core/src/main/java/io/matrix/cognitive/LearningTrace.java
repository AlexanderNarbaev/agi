package io.matrix.cognitive;

import java.util.ArrayList;
import java.util.List;

/**
 * W44.1 — the record of one cognitive cycle, in the order the steps actually happened.
 *
 * <p>The W44 directive requires that every scenario preserve: input, state before processing,
 * detected channel, candidate interpretation, selected algorithm, reason for selection, resource
 * budget, output, supporting evidence, uncertainty, prediction, observed outcome, learning change,
 * and the fallback or abstention reason. This record is where those facts live.</p>
 *
 * <p>It is append-only and ordered. Nothing is edited after the fact; a later step that changes
 * the understanding of an earlier one ADDS a step. That is the same rule the epistemic core
 * already follows for revisions, and it is what makes a trace usable as evidence.</p>
 *
 * <p>Every step names the class that produced it, so a reader can go from a line in the trace to
 * the code that decided it.</p>
 */
public record LearningTrace(String traceId, String episodeId, List<Step> steps) {

    public LearningTrace {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("trace id must not be blank");
        }
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** One recorded step. */
    public record Step(String phase, String actor, String detail) {
        public Step {
            if (phase == null || phase.isBlank()) {
                throw new IllegalArgumentException("trace step phase must not be blank");
            }
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("trace step actor must not be blank");
            }
            detail = detail == null ? "" : detail;
        }
    }

    /** A builder that keeps the order. */
    public static Builder builder(String traceId, String episodeId) {
        return new Builder(traceId, episodeId);
    }

    /** Append-only builder. */
    public static final class Builder {
        private final String traceId;
        private final String episodeId;
        private final List<Step> steps = new ArrayList<>();

        private Builder(String traceId, String episodeId) {
            this.traceId = traceId;
            this.episodeId = episodeId;
        }

        /** Append a step. */
        public Builder step(String phase, String actor, String detail) {
            steps.add(new Step(phase, actor, detail));
            return this;
        }

        /** The finished trace. */
        public LearningTrace build() {
            return new LearningTrace(traceId, episodeId, steps);
        }
    }

    /** The steps of one phase, in order. */
    public List<Step> phase(String phaseName) {
        return steps.stream().filter(s -> s.phase().equals(phaseName)).toList();
    }
}
