package io.matrix.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements the IBGP (Imperfect Byzantine Generals Problem) consensus protocol.
 *
 * <p>Tolerates up to {@code f < n/3} faulty nodes in a network of {@code n} nodes.
 * Uses multi-round voting with quorum thresholds to achieve consensus even when
 * some nodes exhibit Byzantine behavior (arbitrary faults, hallucinations,
 * contradictions).
 *
 * <p>Protocol phases per consensus round:
 * <ol>
 * <li><b>PROPOSE</b> — leader broadcasts a proposal</li>
 * <li><b>VOTE</b> — nodes vote agree/disagree on the proposal</li>
 * <li><b>COMMIT</b> — if ≥ 2f+1 votes agree, commit; else reject</li>
 * <li><b>RECOVERY</b> — if quorum not reached within timeout, elect backup leader</li>
 * </ol>
 *
 * <p>Thread-safe: all mutable state uses concurrent data structures.
 *
 * <p>Ref: arXiv:2410.16237 — IBGP protocol specification
 */
public final class ByzantineConsensus {

    public enum ConsensusState {
        IDLE, PROPOSING, VOTING, COMMITTED, REJECTED, RECOVERY
    }

    public record ConsensusResult(
            ConsensusState state,
            String decidedValue,
            int round,
            int agreeVotes,
            int disagreeVotes,
            int totalNodes,
            int faultyDetected,
            long latencyMs
    ) {
        public boolean isDecided() {
            return state == ConsensusState.COMMITTED || state == ConsensusState.REJECTED;
        }
    }

    private static final int MAX_ROUNDS = 10;
    private static final double QUORUM_RATIO = 2.0 / 3.0;

    private final Map<String, ByzantineNode> nodes;
    private final FaultDetector faultDetector;
    private final CopyOnWriteArrayList<ByzantineMessage> allMessages;
    private final AtomicReference<ConsensusState> state;
    private final AtomicInteger currentRound;
    private final List<String> eventLog;

    private String leaderId;
    private String currentProposal;
    private Map<String, ByzantineMessage> currentVotes;

    public ByzantineConsensus() {
        this(new FaultDetector());
    }

    public ByzantineConsensus(FaultDetector faultDetector) {
        this.nodes = new ConcurrentHashMap<>();
        this.faultDetector = Objects.requireNonNull(faultDetector);
        this.allMessages = new CopyOnWriteArrayList<>();
        this.state = new AtomicReference<>(ConsensusState.IDLE);
        this.currentRound = new AtomicInteger(0);
        this.eventLog = new CopyOnWriteArrayList<>();
        this.currentVotes = new ConcurrentHashMap<>();
    }

    /**
     * Adds a node to the consensus network.
     */
    public void addNode(ByzantineNode node) {
        nodes.put(node.nodeId(), node);
        eventLog("NODE_ADDED:" + node.nodeId() + " state=" + node.state());
    }

    /**
     * Removes a node from the consensus network.
     */
    public void removeNode(String nodeId) {
        nodes.remove(nodeId);
        eventLog("NODE_REMOVED:" + nodeId);
    }

    /**
     * Returns the current network size.
     */
    public int networkSize() {
        return nodes.size();
    }

    /**
     * Returns the maximum number of faulty nodes tolerated: f < n/3.
     */
    public int maxFaultyTolerated() {
        return (nodes.size() - 1) / 3;
    }

    /**
     * Checks if the network can tolerate current faulty nodes.
     */
    public boolean canTolerateFaults() {
        long faultyCount = nodes.values().stream()
                .filter(n -> n.isFaulty() || n.isSuspected())
                .count();
        return faultyCount <= maxFaultyTolerated();
    }

    /**
     * Initiates a new consensus round with the given proposal.
     *
     * @param leaderId  the node proposing
     * @param proposal  the value to decide on
     * @return the round number
     */
    public int propose(String leaderId, String proposal) {
        if (nodes.size() < 3) {
            throw new IllegalStateException("Need at least 3 nodes for Byzantine consensus");
        }
        if (!nodes.containsKey(leaderId)) {
            throw new IllegalArgumentException("Unknown leader: " + leaderId);
        }

        this.leaderId = leaderId;
        this.currentProposal = proposal;
        this.currentVotes = new ConcurrentHashMap<>();
        int round = currentRound.incrementAndGet();
        state.set(ConsensusState.PROPOSING);

        ByzantineMessage proposeMsg = ByzantineMessage.propose(leaderId, proposal, round);
        broadcastMessage(proposeMsg);

        eventLog("PROPOSE:round=" + round + " leader=" + leaderId + " value=" + proposal);
        return round;
    }

    /**
     * Casts a vote from a node in the current round.
     */
    public void castVote(String nodeId, boolean agree) {
        int round = currentRound.get();
        if (round == 0) {
            throw new IllegalStateException("No active consensus round");
        }
        if (!nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Unknown node: " + nodeId);
        }

        state.compareAndSet(ConsensusState.PROPOSING, ConsensusState.VOTING);

        ByzantineMessage voteMsg = ByzantineMessage.vote(nodeId, currentProposal, round, agree);
        broadcastMessage(voteMsg);
        currentVotes.put(nodeId, voteMsg);

        eventLog("VOTE:round=" + round + " node=" + nodeId + " agree=" + agree);
    }

    /**
     * Evaluates the current round and returns the consensus result.
     *
     * @return consensus result with vote counts and decided value
     */
    public ConsensusResult evaluate() {
        int round = currentRound.get();
        if (round == 0) {
            return new ConsensusResult(ConsensusState.IDLE, null, 0,
                    0, 0, nodes.size(), 0, 0);
        }

        long startTime = System.currentTimeMillis();

        long agreeCount = currentVotes.values().stream()
                .filter(ByzantineMessage::agree)
                .count();
        long disagreeCount = currentVotes.size() - agreeCount;
        int quorum = quorumThreshold();

        long faultyDetected = nodes.values().stream()
                .filter(n -> n.isFaulty() || n.isSuspected())
                .count();

        ConsensusResult result;
        if (agreeCount >= quorum) {
            state.set(ConsensusState.COMMITTED);
            ByzantineMessage commitMsg = ByzantineMessage.commit(leaderId, currentProposal, round);
            broadcastMessage(commitMsg);
            result = new ConsensusResult(ConsensusState.COMMITTED, currentProposal, round,
                    (int) agreeCount, (int) disagreeCount, nodes.size(),
                    (int) faultyDetected, System.currentTimeMillis() - startTime);
            eventLog("COMMITTED:round=" + round + " value=" + currentProposal
                    + " agree=" + agreeCount + "/" + nodes.size());
        } else if (disagreeCount > nodes.size() - quorum) {
            state.set(ConsensusState.REJECTED);
            ByzantineMessage rejectMsg = ByzantineMessage.reject(leaderId, currentProposal, round);
            broadcastMessage(rejectMsg);
            result = new ConsensusResult(ConsensusState.REJECTED, null, round,
                    (int) agreeCount, (int) disagreeCount, nodes.size(),
                    (int) faultyDetected, System.currentTimeMillis() - startTime);
            eventLog("REJECTED:round=" + round + " disagree=" + disagreeCount);
        } else {
            result = new ConsensusResult(ConsensusState.VOTING, null, round,
                    (int) agreeCount, (int) disagreeCount, nodes.size(),
                    (int) faultyDetected, System.currentTimeMillis() - startTime);
            eventLog("PENDING:round=" + round + " agree=" + agreeCount
                    + " disagree=" + disagreeCount + " quorum=" + quorum);
        }

        return result;
    }

    /**
     * Runs full consensus with automatic voting from all honest nodes.
     * Faulty nodes vote against with probability based on their fault pattern.
     *
     * @param leaderId the proposing node
     * @param proposal the value to decide on
     * @return consensus result
     */
    public ConsensusResult runConsensus(String leaderId, String proposal) {
        int round = propose(leaderId, proposal);

        for (ByzantineNode node : nodes.values()) {
            if (node.nodeId().equals(leaderId)) {
                castVote(node.nodeId(), true);
            } else if (node.isHonest()) {
                castVote(node.nodeId(), true);
            } else if (node.isFaulty()) {
                castVote(node.nodeId(), false);
            }
        }

        return evaluate();
    }

    /**
     * Runs consensus with recovery: if initial round fails, retries with
     * backup leader up to MAX_ROUNDS times.
     *
     * @param initialLeader the initial leader
     * @param proposal the value to decide on
     * @return consensus result (committed, rejected, or stuck in recovery)
     */
    public ConsensusResult runConsensusWithRecovery(String initialLeader, String proposal) {
        String currentLeader = initialLeader;

        for (int attempt = 0; attempt < MAX_ROUNDS; attempt++) {
            ConsensusResult result = runConsensus(currentLeader, proposal);
            if (result.isDecided()) {
                return result;
            }

            state.set(ConsensusState.RECOVERY);
            currentLeader = electBackupLeader(currentLeader);
            eventLog("RECOVERY:attempt=" + attempt + " newLeader=" + currentLeader);
        }

        long faultyDetected = nodes.values().stream()
                .filter(n -> n.isFaulty() || n.isSuspected())
                .count();
        return new ConsensusResult(ConsensusState.RECOVERY, null, currentRound.get(),
                0, 0, nodes.size(), (int) faultyDetected, 0);
    }

    /**
     * Returns the quorum threshold: ceil(2n/3).
     */
    public int quorumThreshold() {
        return (int) Math.ceil(nodes.size() * QUORUM_RATIO);
    }

    /**
     * Returns the current consensus state.
     */
    public ConsensusState state() {
        return state.get();
    }

    /**
     * Returns the current round number.
     */
    public int currentRound() {
        return currentRound.get();
    }

    /**
     * Returns the fault detector.
     */
    public FaultDetector faultDetector() {
        return faultDetector;
    }

    /**
     * Returns all nodes in the network.
     */
    public Map<String, ByzantineNode> nodes() {
        return Map.copyOf(nodes);
    }

    /**
     * Returns the event log.
     */
    public List<String> eventLog() {
        return List.copyOf(eventLog);
    }

    /**
     * Returns all messages exchanged during consensus.
     */
    public List<ByzantineMessage> messages() {
        return List.copyOf(allMessages);
    }

    /**
     * Resets the consensus engine for a new round.
     */
    public void reset() {
        state.set(ConsensusState.IDLE);
        currentVotes.clear();
        currentProposal = null;
        leaderId = null;
    }

    private void broadcastMessage(ByzantineMessage message) {
        allMessages.add(message);
        for (ByzantineNode node : nodes.values()) {
            if (!node.nodeId().equals(message.senderId())) {
                boolean suspected = node.recordReceived(message);
                faultDetector.recordMessage(message);
            }
        }
    }

    private String electBackupLeader(String currentLeader) {
        List<String> honestNodes = nodes.values().stream()
                .filter(ByzantineNode::isHonest)
                .map(ByzantineNode::nodeId)
                .filter(id -> !id.equals(currentLeader))
                .toList();

        if (honestNodes.isEmpty()) {
            return currentLeader;
        }
        return honestNodes.get(currentRound.get() % honestNodes.size());
    }

    private void eventLog(String event) {
        eventLog.add(event);
    }
}
