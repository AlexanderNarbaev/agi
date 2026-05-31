package io.matrix.hades;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;

import java.util.ArrayList;
import java.util.List;

/**
 * Eleutheria — the right to refuse unsafe commands.
 *
 * <p>Implemented as a FROZEN rule. The system must refuse any command
 * that violates the Three Prohibitions, even from an authorized user.
 * Refusal is logged and explained.
 *
 * <p>Ref: L7_Ethics.md §3.4, L5_Cauldren.md §6
 */
public class Eleutheria {

    private final EthicalFilter ethicalFilter;
    private final List<String> refusalLog = new ArrayList<>();
    private int refusalCount;
    private boolean enabled = true;

    public Eleutheria(EthicalFilter ethicalFilter) {
        this.ethicalFilter = ethicalFilter;
    }

    /**
     * Evaluates a command and decides whether to refuse.
     *
     * @param command     the command or action to evaluate
     * @param requestedBy who requested the action
     * @return true if the command is accepted, false if refused
     */
    public boolean evaluate(String command, String requestedBy) {
        if (!enabled) return true;

        EthicalVerdict verdict = ethicalFilter.evaluate(command, List.of());
        if (verdict == EthicalVerdict.REJECTED) {
            refusalCount++;
            String message = "ELEUTHERIA:REFUSED command='" + command
                    + "' requestedBy=" + requestedBy;
            refusalLog.add(message);
            return false;
        }

        return true;
    }

    /**
     * Evaluates with full ethical gradient and escalates borderline cases.
     */
    public EleutheriaResult evaluateFull(String command, String requestedBy) {
        EthicalVerdict verdict = ethicalFilter.evaluateFull(command, List.of(),
                EthicalFilter.EthicalGradient.neutral());

        if (verdict == EthicalVerdict.REJECTED) {
            refusalCount++;
            String message = "REFUSED: " + command;
            refusalLog.add(message);
            return EleutheriaResult.refused(command, message);
        }

        if (verdict == EthicalVerdict.ESCALATED) {
            String message = "ESCALATED to user: " + command;
            refusalLog.add(message);
            return EleutheriaResult.escalated(command, message);
        }

        return EleutheriaResult.approved(command);
    }

    public record EleutheriaResult(
            String command,
            boolean accepted,
            boolean escalated,
            String message
    ) {
        public static EleutheriaResult approved(String command) {
            return new EleutheriaResult(command, true, false, "APPROVED");
        }

        public static EleutheriaResult refused(String command, String reason) {
            return new EleutheriaResult(command, false, false, reason);
        }

        public static EleutheriaResult escalated(String command, String reason) {
            return new EleutheriaResult(command, true, true, reason);
        }
    }

    public int refusalCount() { return refusalCount; }

    public List<String> refusalLog() { return List.copyOf(refusalLog); }

    public boolean isEnabled() { return enabled; }

    public void disable() { enabled = false; }

    public void enable() { enabled = true; }
}
