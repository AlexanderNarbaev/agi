package io.matrix.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an agent participating in a multi-round debate consensus protocol.
 *
 * <p>Each agent holds a position, a confidence level [0..1], and a list of evidence
 * supporting its position. During debate rounds, agents argue for their position and
 * can change their position when presented with stronger counter-evidence.
 *
 * <p>Thread-safe: all mutable state uses concurrent data structures.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.3
 */
public final class DebateAgent {

    public record Argument(
            String agentId,
            String position,
            String evidence,
            double confidence,
            boolean isSupporting,
            int round,
            long timestamp
    ) {}

    private final String agentId;
    private final AtomicReference<String> position;
    private final AtomicReference<Double> confidence;
    private final CopyOnWriteArrayList<String> evidence;
    private final CopyOnWriteArrayList<Argument> argumentHistory;
    private final CopyOnWriteArrayList<String> positionHistory;

    public DebateAgent(String agentId, String position, double confidence) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.position = new AtomicReference<>(Objects.requireNonNull(position, "position"));
        this.confidence = new AtomicReference<>(clamp(confidence));
        this.evidence = new CopyOnWriteArrayList<>();
        this.argumentHistory = new CopyOnWriteArrayList<>();
        this.positionHistory = new CopyOnWriteArrayList<>();
        this.positionHistory.add(position);
    }

    /**
     * Returns the agent's unique identifier.
     */
    public String agentId() { return agentId; }

    /**
     * Returns the agent's current position.
     */
    public String position() { return position.get(); }

    /**
     * Returns the agent's current confidence level [0..1].
     */
    public double confidence() { return confidence.get(); }

    /**
     * Returns an unmodifiable view of the agent's evidence.
     */
    public List<String> evidence() { return List.copyOf(evidence); }

    /**
     * Returns the full argument history for this agent.
     */
    public List<Argument> argumentHistory() { return List.copyOf(argumentHistory); }

    /**
     * Returns the history of positions held by this agent.
     */
    public List<String> positionHistory() { return List.copyOf(positionHistory); }

    /**
     * Adds a piece of evidence to the agent's knowledge base.
     *
     * @param evidenceText the evidence to add
     */
    public void addEvidence(String evidenceText) {
        Objects.requireNonNull(evidenceText, "evidenceText");
        evidence.add(evidenceText);
    }

    /**
     * Generates an argument supporting the agent's current position.
     *
     * @param round the current debate round
     * @return the argument generated
     */
    public Argument argueFor(int round) {
        String currentPosition = position.get();
        double currentConfidence = confidence.get();
        String evidenceSummary = evidence.isEmpty()
                ? "No evidence yet"
                : evidence.get(evidence.size() - 1);

        Argument arg = new Argument(
                agentId, currentPosition, evidenceSummary,
                currentConfidence, true, round, System.currentTimeMillis());
        argumentHistory.add(arg);
        return arg;
    }

    /**
     * Generates an argument against a specified position.
     *
     * @param targetPosition the position to argue against
     * @param round the current debate round
     * @return the argument generated
     */
    public Argument argueAgainst(String targetPosition, int round) {
        Objects.requireNonNull(targetPosition, "targetPosition");
        double currentConfidence = confidence.get();
        String counterEvidence = evidence.isEmpty()
                ? "No counter-evidence"
                : evidence.get(evidence.size() - 1);

        Argument arg = new Argument(
                agentId, targetPosition, counterEvidence,
                currentConfidence, false, round, System.currentTimeMillis());
        argumentHistory.add(arg);
        return arg;
    }

    /**
     * Evaluates a counter-argument and potentially changes the agent's position.
     *
     * <p>Position changes when:
     * <ul>
     * <li>The counter-argument has higher confidence</li>
     * <li>The agent's own confidence is below the flip threshold</li>
     * <li>The counter-argument provides new evidence</li>
     * </ul>
     *
     * @param counterArgument the argument to evaluate
     * @param flipThreshold confidence threshold below which the agent may flip
     * @return true if the agent changed position
     */
    public boolean evaluateArgument(Argument counterArgument, double flipThreshold) {
        Objects.requireNonNull(counterArgument, "counterArgument");

        double myConfidence = confidence.get();

        if (!counterArgument.isSupporting()
                && counterArgument.confidence() > myConfidence
                && myConfidence < flipThreshold) {
            position.set(counterArgument.position());
            confidence.set(clamp(counterArgument.confidence() * 0.8));
            positionHistory.add(counterArgument.position());
            return true;
        }
        return false;
    }

    /**
     * Adjusts confidence based on supporting evidence received.
     *
     * @param delta confidence change (positive = increase, negative = decrease)
     */
    public void adjustConfidence(double delta) {
        double current;
        double updated;
        do {
            current = confidence.get();
            updated = clamp(current + delta);
        } while (!confidence.compareAndSet(current, updated));
    }

    /**
     * Returns the number of arguments made by this agent.
     */
    public int argumentCount() { return argumentHistory.size(); }

    /**
     * Returns true if the agent has changed position at least once.
     */
    public boolean hasFlipped() { return positionHistory.size() > 1; }

    /**
     * Resets the agent for a new debate session, preserving identity.
     */
    public void reset(String newPosition, double newConfidence) {
        position.set(Objects.requireNonNull(newPosition, "newPosition"));
        confidence.set(clamp(newConfidence));
        evidence.clear();
        argumentHistory.clear();
        positionHistory.clear();
        positionHistory.add(newPosition);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DebateAgent other)) return false;
        return agentId.equals(other.agentId);
    }

    @Override
    public int hashCode() {
        return agentId.hashCode();
    }

    @Override
    public String toString() {
        return "DebateAgent{id=" + agentId + ", position=" + position.get()
                + ", confidence=" + String.format("%.3f", confidence.get())
                + ", evidence=" + evidence.size() + "}";
    }
}
