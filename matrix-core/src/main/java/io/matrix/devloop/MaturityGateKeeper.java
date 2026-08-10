package io.matrix.devloop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maturity gate keeper: controls MA level transitions.
 *
 * <p>Per SPEC-000 FR-5: transitions require preregistered EXP card +
 * operator confirmation. Demotion is automatic on drift (no operator).
 * Every gate leaves a record in Event Sourcing.
 */
public final class MaturityGateKeeper {

    private final CompetenceAssessor assessor;
    private final List<TransitionRecord> transitions = new CopyOnWriteArrayList<>();
    private MaturityLevel current = MaturityLevel.MA_0_SANDBOX;

    public MaturityGateKeeper(CompetenceAssessor assessor) {
        this.assessor = assessor;
    }

    public MaturityLevel current() { return current; }

    /** Request promotion to next level. Requires operator approval. */
    public TransitionResult requestPromotion(String operator, String expCardId) {
        MaturityLevel target = current.next();
        if (target == current) {
            return TransitionResult.denied("Already at maximum level");
        }
        if (!assessor.readyFor(target)) {
            return TransitionResult.denied("Competence " + assessor.aggregateCompetence()
                    + " below threshold for " + target.displayName());
        }
        // Record the transition
        var record = new TransitionRecord(
                current, target, operator, expCardId,
                System.currentTimeMillis(), "promoted");
        transitions.add(record);
        current = target;
        return TransitionResult.approved(target);
    }

    /** Automatic demotion on drift (no operator needed). */
    public TransitionResult demote(String reason) {
        if (current == MaturityLevel.MA_0_SANDBOX) {
            return TransitionResult.denied("Already at minimum level");
        }
        MaturityLevel target = current.previous();
        var record = new TransitionRecord(
                current, target, "system", "drift-detected",
                System.currentTimeMillis(), "demoted: " + reason);
        transitions.add(record);
        current = target;
        return TransitionResult.approved(target);
    }

    public List<TransitionRecord> transitions() { return List.copyOf(transitions); }

    public record TransitionRecord(
            MaturityLevel from, MaturityLevel to,
            String operator, String expCardId,
            long timestamp, String action) {}

    public record TransitionResult(boolean approved, MaturityLevel newLevel, String reason) {
        public static TransitionResult approved(MaturityLevel level) {
            return new TransitionResult(true, level, "approved");
        }
        public static TransitionResult denied(String reason) {
            return new TransitionResult(false, null, reason);
        }
    }
}
