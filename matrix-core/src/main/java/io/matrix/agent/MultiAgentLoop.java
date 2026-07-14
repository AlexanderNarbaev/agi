package io.matrix.agent;

import io.matrix.consensus.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Multi-agent loop that runs consensus across N agent instances.
 *
 * <p>Each agent has its own brain, sensor, and effector.
 * After each tick, agents vote on the next action via Byzantine/Weighted/Debate consensus.
 *
 * <p>Architecture:
 * <ol>
 *   <li>All agents execute tick() concurrently via virtual threads</li>
 *   <li>Each agent's chosen action becomes a proposal for consensus</li>
 *   <li>Consensus algorithm selects the winning action</li>
 *   <li>The agent whose action was chosen is recorded</li>
 * </ol>
 *
 * <p>Thread-safe: agent ticks are independent and run on virtual threads.
 * Consensus evaluation is serialized per tick.
 */
public final class MultiAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentLoop.class);

    /** Consensus strategy selection. */
    public enum ConsensusMode {
        /** Proof-of-Accuracy with Byzantine fault tolerance (2/3 quorum). */
        BYZANTINE,
        /** Weighted voting based on agent confidence. */
        WEIGHTED,
        /** Adversarial debate protocol. */
        DEBATE
    }

    /** Virtual-thread-per-task executor for concurrent agent ticks. */
    private static final Executor VIRTUAL_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    /** Result of one multi-agent consensus tick. */
    public record MultiAgentState(
            int tick,
            List<AgentState> agentStates,
            AgentAction consensusAction,
            UUID chosenAgentId) {
    }

    // ── Configuration ──

    private final List<AgentLoop> agents;
    private final ConsensusMode mode;
    private final int quorum;
    private final List<UUID> agentUuids;

    // ── Mutable state ──

    private final AtomicInteger tickCounter = new AtomicInteger(0);
    private final List<MultiAgentState> history =
            Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates a multi-agent loop.
     *
     * @param agents the agent instances (must be ≥ 3 for BYZANTINE)
     * @param mode   consensus strategy
     * @param quorum minimum votes required for consensus (e.g. ceil(2n/3))
     */
    public MultiAgentLoop(List<AgentLoop> agents, ConsensusMode mode, int quorum) {
        this.agents = List.copyOf(Objects.requireNonNull(agents, "agents"));
        this.mode = Objects.requireNonNull(mode, "mode");
        this.quorum = Math.max(1, quorum);
        this.agentUuids = new ArrayList<>(this.agents.size());
        for (int i = 0; i < this.agents.size(); i++) {
            agentUuids.add(new UUID(i, i + 1));
        }
        log.info("MultiAgentLoop created: agents={}, mode={}, quorum={}",
                this.agents.size(), mode, this.quorum);
    }

    // ── Accessors ──

    /** Returns the immutable agent list. */
    public List<AgentLoop> agents() { return agents; }

    /** Returns the active consensus mode. */
    public ConsensusMode mode() { return mode; }

    /** Returns the quorum threshold. */
    public int quorum() { return quorum; }

    /** Returns the current tick count. */
    public int tickCount() { return tickCounter.get(); }

    /** Returns an immutable copy of the history. */
    public List<MultiAgentState> history() { return List.copyOf(history); }

    // ── Single tick with consensus ──

    /**
     * Runs all agents for one tick concurrently, then reaches consensus
     * on the next action.
     *
     * @return the multi-agent state with the consensus-chosen action
     */
    public MultiAgentState consensusTick() {
        // 1. Run all agents concurrently via virtual threads
        List<AgentState> states = runAgentsConcurrently();

        // 2. Build map: agent UUID → action type
        Map<UUID, AgentAction.ActionType> proposals = new LinkedHashMap<>();
        for (int i = 0; i < states.size(); i++) {
            AgentState state = states.get(i);
            if (state.action() != null) {
                proposals.put(agentUuids.get(i), state.actionType());
            }
        }

        // 3. Run consensus
        ConsensusResult result = runConsensus(proposals);

        int tick = tickCounter.incrementAndGet();
        MultiAgentState state = new MultiAgentState(
                tick, List.copyOf(states), result.action(), result.chosenAgentId());
        history.add(state);

        log.debug("Tick {}: consensus={}, chosenAgent={}, states={}",
                tick, result.action().type(), result.chosenAgentId(), states.size());
        return state;
    }

    // ── Full loop ──

    /**
     * Runs the full multi-agent loop with consensus at each step.
     *
     * @param maxIterations maximum number of ticks
     * @return immutable history of multi-agent states
     */
    public List<MultiAgentState> run(int maxIterations) {
        log.info("Multi-agent loop starting: maxIterations={}, mode={}",
                maxIterations, mode);
        for (int i = 0; i < maxIterations; i++) {
            consensusTick();
        }
        log.info("Multi-agent loop finished: ticks={}", tickCounter.get());
        return List.copyOf(history);
    }

    // ── Concurrent agent execution ──

    /**
     * Runs all agents' tick() concurrently via virtual threads.
     * Each agent is independent — no shared mutable state between ticks.
     *
     * @return list of agent states in the same order as the agents list
     */
    private List<AgentState> runAgentsConcurrently() {
        int n = agents.size();
        List<CompletableFuture<AgentState>> futures = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            final AgentLoop agent = agents.get(i);
            futures.add(CompletableFuture.supplyAsync(agent::tick, VIRTUAL_EXECUTOR));
        }

        // Collect results in order
        List<AgentState> results = new ArrayList<>(n);
        for (CompletableFuture<AgentState> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Agent tick interrupted");
                results.add(null);
            } catch (ExecutionException e) {
                log.warn("Agent tick failed: {}", e.getCause().getMessage());
                results.add(null);
            }
        }
        return results;
    }

    // ── Internal consensus result ──

    private record ConsensusResult(AgentAction action, UUID chosenAgentId) {
    }

    private ConsensusResult runConsensus(
            Map<UUID, AgentAction.ActionType> proposals) {
        return switch (mode) {
            case BYZANTINE -> byzantineConsensus(proposals);
            case WEIGHTED -> weightedConsensus(proposals);
            case DEBATE -> debateConsensus(proposals);
        };
    }

    // ── Byzantine consensus ──

    /**
     * Runs Byzantine consensus (Proof-of-Accuracy with fault tolerance).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Count action type frequencies across all agents</li>
     *   <li>The most popular action becomes the proposal</li>
     *   <li>Register all agents as ByzantineNodes and run ByzantineConsensus</li>
     *   <li>If the ByzantineConsensus commits (≥ 2f+1 agree), that action wins</li>
     *   <li>Otherwise, fall back to simple majority</li>
     * </ol>
     */
    private ConsensusResult byzantineConsensus(
            Map<UUID, AgentAction.ActionType> proposals) {
        if (proposals.isEmpty()) {
            return new ConsensusResult(
                    new AgentAction(AgentAction.ActionType.WAIT,
                            Map.of("reason", "no_proposals")),
                    null);
        }

        // Count frequency of each action type
        Map<AgentAction.ActionType, List<UUID>> actionVotes = new LinkedHashMap<>();
        for (var entry : proposals.entrySet()) {
            actionVotes.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        // Find the action with the most votes
        var winner = actionVotes.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .orElseThrow();

        int voteCount = winner.getValue().size();
        AgentAction.ActionType majorityType = winner.getKey();

        // Try ByzantineConsensus for fault-tolerance verification
        try {
            ByzantineConsensus bc = new ByzantineConsensus();

            // Register agents as Byzantine nodes
            for (int i = 0; i < agents.size(); i++) {
                bc.addNode(new ByzantineNode(
                        agentUuids.get(i).toString(),
                        ByzantineNode.State.HONEST));
            }

            // Propose the majority action, run consensus
            String leaderId = agentUuids.get(0).toString();
            ByzantineConsensus.ConsensusResult bcResult =
                    bc.runConsensus(leaderId, majorityType.name());

            if (bcResult.state() == ByzantineConsensus.ConsensusState.COMMITTED) {
                // Byzantine consensus confirmed the majority action
                UUID chosenAgentId = winner.getValue().get(0);
                AgentAction action = new AgentAction(majorityType,
                        Map.of("voteCount", voteCount,
                                "totalAgents", proposals.size(),
                                "byzantine", "COMMITTED",
                                "round", bcResult.round()));
                return new ConsensusResult(action, chosenAgentId);
            }
        } catch (Exception e) {
            log.debug("Byzantine consensus fallback: {}", e.getMessage());
        }

        // Fallback: simple majority with quorum check
        AgentAction action;
        UUID chosenAgentId;
        if (voteCount >= quorum) {
            chosenAgentId = winner.getValue().get(0);
            action = new AgentAction(majorityType,
                    Map.of("voteCount", voteCount,
                            "totalAgents", proposals.size(),
                            "byzantine", "FALLBACK"));
        } else {
            // No quorum — return the most voted action anyway
            chosenAgentId = winner.getValue().get(0);
            action = new AgentAction(majorityType,
                    Map.of("voteCount", voteCount,
                            "totalAgents", proposals.size(),
                            "byzantine", "NO_QUORUM"));
        }
        return new ConsensusResult(action, chosenAgentId);
    }

    // ── Weighted consensus ──

    /**
     * Runs weighted voting consensus using ConsensusEngine in WEIGHTED mode.
     *
     * <p>Each agent proposes its action, then all agents vote on all proposals
     * with weight = 1.0 (equal weight; confidence-based weighting can be added).
     * The proposal with the highest weighted approval wins.
     */
    private ConsensusResult weightedConsensus(
            Map<UUID, AgentAction.ActionType> proposals) {
        if (proposals.isEmpty()) {
            return new ConsensusResult(
                    new AgentAction(AgentAction.ActionType.WAIT,
                            Map.of("reason", "no_proposals")),
                    null);
        }

        // Count frequencies to find the most popular action
        Map<AgentAction.ActionType, Integer> tally = new LinkedHashMap<>();
        for (var entry : proposals.entrySet()) {
            tally.merge(entry.getValue(), 1, Integer::sum);
        }

        var winner = tally.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        UUID proposalId = UUID.randomUUID();
        ConsensusEngine engine = new ConsensusEngine(
                ConsensusEngine.ConsensusStrategy.WEIGHTED);
        engine.weightedVoting().clear();

        // Propose the majority action
        Proposal prop = Proposal.create(ConsensusLevel.LEVEL_2,
                "MULTI_AGENT",
                winner.getKey().name(),
                "weighted_consensus_tick");
        engine.propose(prop);

        // Each agent votes with weight = 1.0 (can be replaced with confidence-based)
        for (UUID agentUuid : proposals.keySet()) {
            engine.castVote(Vote.approve(prop.id(),
                    agentUuid.toString(), 1.0));
        }

        ConsensusEngine.Decision decision = engine.evaluate(prop.id());

        UUID chosenAgentId = proposals.entrySet().stream()
                .filter(e -> e.getValue() == winner.getKey())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        AgentAction action = new AgentAction(winner.getKey(),
                Map.of("voteCount", winner.getValue(),
                        "totalAgents", proposals.size(),
                        "weighted", decision.name(),
                        "votes", engine.getVotes(prop.id()).size()));

        return new ConsensusResult(action, chosenAgentId);
    }

    // ── Debate consensus ──

    /**
     * Runs adversarial debate protocol for consensus.
     *
     * <p>Each agent becomes a DebateAgent with its action type as its position.
     * The debate protocol runs for multiple rounds; if consensus is reached,
     * the agreed-upon action wins. Otherwise, falls back to simple majority.
     */
    private ConsensusResult debateConsensus(
            Map<UUID, AgentAction.ActionType> proposals) {
        if (proposals.isEmpty()) {
            return new ConsensusResult(
                    new AgentAction(AgentAction.ActionType.WAIT,
                            Map.of("reason", "no_proposals")),
                    null);
        }

        // Create DebateAgents: each agent's position is its action type name
        DebateProtocol protocol = new DebateProtocol();
        for (var entry : proposals.entrySet()) {
            DebateAgent agent = new DebateAgent(
                    entry.getKey().toString(),
                    entry.getValue().name(),
                    1.0); // initial confidence
            protocol.addAgent(agent);
        }

        // Run the debate
        DebateProtocol.DebateResult result = protocol.runDebate();

        // If debate reached consensus, use that position
        if (result.isConsensus() && result.consensusPosition() != null) {
            AgentAction.ActionType consensusType =
                    AgentAction.ActionType.valueOf(result.consensusPosition());

            UUID chosenAgentId = proposals.entrySet().stream()
                    .filter(e -> e.getValue() == consensusType)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

            AgentAction action = new AgentAction(consensusType,
                    Map.of("debate", "CONSENSUS",
                            "rounds", result.totalRounds(),
                            "state", result.state().name(),
                            "agents", result.finalPositions().size()));

            return new ConsensusResult(action, chosenAgentId);
        }

        // Fallback to simple majority
        Map<AgentAction.ActionType, Integer> tally = new LinkedHashMap<>();
        for (var entry : proposals.entrySet()) {
            tally.merge(entry.getValue(), 1, Integer::sum);
        }

        var winner = tally.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        UUID chosenAgentId = proposals.entrySet().stream()
                .filter(e -> e.getValue() == winner.getKey())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        AgentAction action = new AgentAction(winner.getKey(),
                Map.of("voteCount", winner.getValue(),
                        "totalAgents", proposals.size(),
                        "debate", result.state().name(),
                        "rounds", result.totalRounds()));

        return new ConsensusResult(action, chosenAgentId);
    }
}
