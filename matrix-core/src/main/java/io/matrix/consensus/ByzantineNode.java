package io.matrix.consensus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a node in the Byzantine consensus network.
 *
 * <p>Tracks node state (HONEST, FAULTY, SUSPECTED) and implements fault detection
 * heuristics based on message consistency analysis. Each node maintains a local
 * view of the network and can detect suspicious behavior from peers.
 *
 * <p>Thread-safe: all mutable state uses concurrent data structures.
 *
 * <p>Ref: arXiv:2410.16237 — IBGP node model
 */
public final class ByzantineNode {

    public enum State {
        HONEST, FAULTY, SUSPECTED
    }

    private final String nodeId;
    private final AtomicReference<State> state;
    private final AtomicInteger suspicionScore;
    private final CopyOnWriteArrayList<ByzantineMessage> sentMessages;
    private final CopyOnWriteArrayList<ByzantineMessage> receivedMessages;
    private final Map<String, List<ByzantineMessage>> messagesBySender;
    private final Map<String, Map<Integer, ByzantineMessage>> votesBySenderAndRound;

    private static final int SUSPICION_THRESHOLD = 3;
    private static final int MAX_HISTORY = 1000;

    public ByzantineNode(String nodeId) {
        this(nodeId, State.HONEST);
    }

    public ByzantineNode(String nodeId, State initialState) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.state = new AtomicReference<>(initialState);
        this.suspicionScore = new AtomicInteger(0);
        this.sentMessages = new CopyOnWriteArrayList<>();
        this.receivedMessages = new CopyOnWriteArrayList<>();
        this.messagesBySender = new ConcurrentHashMap<>();
        this.votesBySenderAndRound = new ConcurrentHashMap<>();
    }

    public static ByzantineNode honest(String nodeId) {
        return new ByzantineNode(nodeId, State.HONEST);
    }

    public static ByzantineNode faulty(String nodeId) {
        return new ByzantineNode(nodeId, State.FAULTY);
    }

    public String nodeId() { return nodeId; }

    public State state() { return state.get(); }

    public int suspicionScore() { return suspicionScore.get(); }

    public boolean isHonest() { return state.get() == State.HONEST; }

    public boolean isFaulty() { return state.get() == State.FAULTY; }

    public boolean isSuspected() { return state.get() == State.SUSPECTED; }

    /**
     * Records a message sent by this node.
     */
    public void recordSent(ByzantineMessage message) {
        sentMessages.add(message);
        trimIfNeeded(sentMessages);
    }

    /**
     * Records a message received from another node and runs fault heuristics.
     *
     * @return true if the sender became suspected after this message
     */
    public boolean recordReceived(ByzantineMessage message) {
        receivedMessages.add(message);
        trimIfNeeded(receivedMessages);

        messagesBySender.computeIfAbsent(message.senderId(),
                k -> new CopyOnWriteArrayList<>()).add(message);

        if (message.type() == ByzantineMessage.Type.VOTE
                || message.type() == ByzantineMessage.Type.COMMIT
                || message.type() == ByzantineMessage.Type.REJECT) {
            Map<Integer, ByzantineMessage> senderVotes = votesBySenderAndRound
                    .computeIfAbsent(message.senderId(), k -> new ConcurrentHashMap<>());
            ByzantineMessage previous = senderVotes.put(message.round(), message);
            if (previous != null && previous.agree() != message.agree()) {
                incrementSuspicion(message.senderId(),
                        "Contradictory votes in round " + message.round());
                return true;
            }
        }

        return false;
    }

    /**
     * Detects if a peer has sent inconsistent payloads across rounds
     * (hallucination heuristic).
     *
     * @return list of suspected node IDs
     */
    public List<String> detectHallucinations() {
        List<String> suspected = new ArrayList<>();
        for (var entry : messagesBySender.entrySet()) {
            List<ByzantineMessage> messages = entry.getValue();
            if (messages.size() < 2) continue;

            String firstPayload = null;
            boolean inconsistent = false;
            for (ByzantineMessage msg : messages) {
                if (msg.type() == ByzantineMessage.Type.PROPOSE
                        || msg.type() == ByzantineMessage.Type.COMMIT) {
                    if (firstPayload == null) {
                        firstPayload = msg.payload();
                    } else if (!Objects.equals(firstPayload, msg.payload())) {
                        inconsistent = true;
                        break;
                    }
                }
            }
            if (inconsistent) {
                incrementSuspicion(entry.getKey(),
                        "Inconsistent payloads from " + entry.getKey());
                suspected.add(entry.getKey());
            }
        }
        return suspected;
    }

    /**
     * Detects nodes that voted differently from their stated proposal
     * (contradiction heuristic).
     *
     * @return list of contradicted node IDs
     */
    public List<String> detectContradictions() {
        List<String> contradicted = new ArrayList<>();
        for (var entry : messagesBySender.entrySet()) {
            ByzantineMessage proposal = null;
            List<ByzantineMessage> votes = new ArrayList<>();
            for (ByzantineMessage msg : entry.getValue()) {
                if (msg.type() == ByzantineMessage.Type.PROPOSE) {
                    proposal = msg;
                } else if (msg.type() == ByzantineMessage.Type.VOTE) {
                    votes.add(msg);
                }
            }
            if (proposal != null) {
                for (ByzantineMessage vote : votes) {
                    if (!vote.agree() && Objects.equals(proposal.payload(), vote.payload())) {
                        incrementSuspicion(entry.getKey(),
                                "Contradiction: proposed " + proposal.payload()
                                        + " but voted against");
                        contradicted.add(entry.getKey());
                        break;
                    }
                }
            }
        }
        return contradicted;
    }

    /**
     * Returns the node's local view of messages from a specific sender.
     */
    public List<ByzantineMessage> messagesFrom(String senderId) {
        return List.copyOf(messagesBySender.getOrDefault(senderId, List.of()));
    }

    /**
     * Returns all received messages.
     */
    public List<ByzantineMessage> receivedMessages() {
        return List.copyOf(receivedMessages);
    }

    /**
     * Returns all sent messages.
     */
    public List<ByzantineMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }

    /**
     * Manually transition node state.
     */
    public void transitionTo(State newState) {
        state.set(newState);
    }

    /**
     * Resets suspicion score.
     */
    public void clearSuspicion() {
        suspicionScore.set(0);
        if (state.get() == State.SUSPECTED) {
            state.set(State.HONEST);
        }
    }

    private void incrementSuspicion(String peerId, String reason) {
        int newScore = suspicionScore.incrementAndGet();
        if (newScore >= SUSPICION_THRESHOLD && state.get() == State.HONEST) {
            state.set(State.SUSPECTED);
        }
    }

    private void trimIfNeeded(CopyOnWriteArrayList<ByzantineMessage> list) {
        while (list.size() > MAX_HISTORY) {
            list.remove(0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByzantineNode other)) return false;
        return nodeId.equals(other.nodeId);
    }

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }

    @Override
    public String toString() {
        return "ByzantineNode{id=" + nodeId + ", state=" + state.get()
                + ", suspicion=" + suspicionScore.get() + "}";
    }
}
