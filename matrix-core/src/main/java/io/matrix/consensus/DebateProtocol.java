package io.matrix.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-round adversarial debate protocol for consensus.
 *
 * <p>Each round:
 * <ol>
 * <li>All agents argue for their current position</li>
 * <li>Agents evaluate counter-arguments from agents with different positions</li>
 * <li>Agents may change position if counter-arguments have higher confidence</li>
 * <li>Consensus is reached when a quorum of agents agree on the same position</li>
 * </ol>
 *
 * <p>The protocol terminates when:
 * <ul>
 * <li>Consensus is reached (quorum agrees)</li>
 * <li>Maximum rounds exhausted (stalemate)</li>
 * <li>No position changes occur (deadlock)</li>
 * </ul>
 *
 * <p>Thread-safe: all mutable state uses concurrent data structures.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.3
 */
public final class DebateProtocol {

    public enum DebateState {
        INITIALIZED, DEBATING, CONSENSUS_REACHED, STALEMATE, DEADLOCKED
    }

    public record DebateRound(
            int roundNumber,
            Map<String, String> positionsBefore,
            Map<String, String> positionsAfter,
            List<DebateAgent.Argument> arguments,
            int positionChanges
    ) {}

    public record DebateResult(
            DebateState state,
            String consensusPosition,
            int totalRounds,
            List<DebateRound> rounds,
            Map<String, String> finalPositions,
            Map<String, Double> finalConfidences
    ) {
        public boolean isConsensus() {
            return state == DebateState.CONSENSUS_REACHED;
        }
    }

    private static final int DEFAULT_MAX_ROUNDS = 10;
    private static final double DEFAULT_QUORUM_RATIO = 2.0 / 3.0;
    private static final double DEFAULT_FLIP_THRESHOLD = 0.4;

    private final ConcurrentHashMap<String, DebateAgent> agents;
    private final CopyOnWriteArrayList<DebateRound> rounds;
    private final AtomicInteger currentRound;
    private final AtomicReference<DebateState> state;
    private final CopyOnWriteArrayList<String> eventLog;

    private final int maxRounds;
    private final double quorumRatio;
    private final double flipThreshold;

    public DebateProtocol() {
        this(DEFAULT_MAX_ROUNDS, DEFAULT_QUORUM_RATIO, DEFAULT_FLIP_THRESHOLD);
    }

    public DebateProtocol(int maxRounds, double quorumRatio, double flipThreshold) {
        this.maxRounds = maxRounds;
        this.quorumRatio = quorumRatio;
        this.flipThreshold = flipThreshold;
        this.agents = new ConcurrentHashMap<>();
        this.rounds = new CopyOnWriteArrayList<>();
        this.currentRound = new AtomicInteger(0);
        this.state = new AtomicReference<>(DebateState.INITIALIZED);
        this.eventLog = new CopyOnWriteArrayList<>();
    }

    /**
     * Registers an agent for the debate.
     */
    public void addAgent(DebateAgent agent) {
        Objects.requireNonNull(agent, "agent");
        agents.put(agent.agentId(), agent);
        eventLog("AGENT_ADDED:" + agent.agentId() + " position=" + agent.position()
                + " confidence=" + String.format("%.3f", agent.confidence()));
    }

    /**
     * Removes an agent from the debate.
     */
    public void removeAgent(String agentId) {
        agents.remove(agentId);
        eventLog("AGENT_REMOVED:" + agentId);
    }

    /**
     * Returns the number of registered agents.
     */
    public int agentCount() { return agents.size(); }

    /**
     * Returns the current debate state.
     */
    public DebateState state() { return state.get(); }

    /**
     * Returns the current round number.
     */
    public int currentRound() { return currentRound.get(); }

    /**
     * Returns all registered agents.
     */
    public Map<String, DebateAgent> agents() { return Map.copyOf(agents); }

    /**
     * Returns the event log.
     */
    public List<String> eventLog() { return List.copyOf(eventLog); }

    /**
     * Returns the debate rounds completed so far.
     */
    public List<DebateRound> rounds() { return List.copyOf(rounds); }

    /**
     * Runs the full debate protocol until consensus, stalemate, or deadlock.
     *
     * @return the debate result
     */
    public DebateResult runDebate() {
        if (agents.size() < 2) {
            throw new IllegalStateException("Need at least 2 agents for debate");
        }

        state.set(DebateState.DEBATING);
        eventLog("DEBATE_STARTED:agents=" + agents.size());

        for (int round = 1; round <= maxRounds; round++) {
            DebateRound debateRound = runRound(round);
            rounds.add(debateRound);

            if (checkConsensus()) {
                state.set(DebateState.CONSENSUS_REACHED);
                String consensusPos = agents.values().iterator().next().position();
                for (DebateAgent a : agents.values()) {
                    if (isMajorityPosition(a.position())) {
                        consensusPos = a.position();
                        break;
                    }
                }
                eventLog("CONSENSUS:position=" + consensusPos + " round=" + round);
                return buildResult(DebateState.CONSENSUS_REACHED, consensusPos);
            }

            if (debateRound.positionChanges() == 0 && round > 1) {
                state.set(DebateState.DEADLOCKED);
                eventLog("DEADLOCK:round=" + round);
                return buildResult(DebateState.DEADLOCKED, null);
            }
        }

        state.set(DebateState.STALEMATE);
        eventLog("STALEMATE:maxRounds=" + maxRounds);
        return buildResult(DebateState.STALEMATE, null);
    }

    /**
     * Runs a single debate round.
     */
    DebateRound runRound(int roundNumber) {
        Map<String, String> positionsBefore = new HashMap<>();
        for (DebateAgent agent : agents.values()) {
            positionsBefore.put(agent.agentId(), agent.position());
        }

        List<DebateAgent.Argument> allArguments = new ArrayList<>();
        for (DebateAgent agent : agents.values()) {
            allArguments.add(agent.argueFor(roundNumber));
        }

        for (DebateAgent agent : agents.values()) {
            for (DebateAgent other : agents.values()) {
                if (!agent.agentId().equals(other.agentId())
                        && !agent.position().equals(other.position())) {
                    DebateAgent.Argument counter = other.argueAgainst(
                            agent.position(), roundNumber);
                    allArguments.add(counter);
                }
            }
        }

        int positionChanges = 0;
        for (DebateAgent agent : agents.values()) {
            for (DebateAgent other : agents.values()) {
                if (!agent.agentId().equals(other.agentId())
                        && !agent.position().equals(other.position())) {
                    DebateAgent.Argument lastArg = other.argueFor(roundNumber);
                    if (agent.evaluateArgument(lastArg, flipThreshold)) {
                        positionChanges++;
                        eventLog("FLIP:" + agent.agentId() + " → " + agent.position()
                                + " round=" + roundNumber);
                    }
                }
            }
        }

        Map<String, String> positionsAfter = new HashMap<>();
        for (DebateAgent agent : agents.values()) {
            positionsAfter.put(agent.agentId(), agent.position());
        }

        return new DebateRound(roundNumber, positionsBefore, positionsAfter,
                allArguments, positionChanges);
    }

    /**
     * Checks if consensus is reached (quorum of agents agree).
     */
    boolean checkConsensus() {
        if (agents.isEmpty()) return false;
        return agents.values().stream()
                .anyMatch(a -> isMajorityPosition(a.position()));
    }

    private boolean isMajorityPosition(String position) {
        long count = agents.values().stream()
                .filter(a -> a.position().equals(position))
                .count();
        return count >= Math.ceil(agents.size() * quorumRatio);
    }

    private DebateResult buildResult(DebateState finalState, String consensusPosition) {
        Map<String, String> finalPositions = new HashMap<>();
        Map<String, Double> finalConfidences = new HashMap<>();
        for (DebateAgent agent : agents.values()) {
            finalPositions.put(agent.agentId(), agent.position());
            finalConfidences.put(agent.agentId(), agent.confidence());
        }
        return new DebateResult(finalState, consensusPosition, currentRound.get(),
                List.copyOf(rounds), finalPositions, finalConfidences);
    }

    private void eventLog(String event) {
        eventLog.add(event);
    }
}
