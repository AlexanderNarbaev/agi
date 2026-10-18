package io.matrix.brain.runtime.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A graph of {@link CognitiveNode}s joined by {@link Synapse}s.
 *
 * <p>This is the W37 replacement for the linear stage pipeline. The pipeline ran every stage once
 * in a fixed order; a connectome routes activation along weighted edges, so a node may be reached
 * many times per step, skipped, or reached through a different path.</p>
 *
 * <h2>Safety invariant</h2>
 *
 * <p>{@link #prune(Set)} <b>never</b> removes a synapse whose target is a registered veto node.
 * This is structural, not conventional: pruning is the mechanism by which sleep-time consolidation
 * shrinks the graph, and a prunable edge into a safety gate would let accumulated pressure disable
 * the gate — the system would "learn" to stop checking itself. The check is inside {@code prune}
 * itself rather than in the caller, because a caller-enforced invariant is exactly the kind that
 * fails silently under an unanticipated call path.</p>
 *
 * <h2>Determinism</h2>
 *
 * <p>{@link #propagate} breaks score ties by {@link NodeId}, never by hash iteration order, so the
 * same graph and input produce the same activation on every JVM. Nodes are held in a
 * {@link LinkedHashMap} for the same reason: insertion order, not hash order, defines iteration.</p>
 */
public final class Connectome {

    private final Map<NodeId, CognitiveNode> nodes = new LinkedHashMap<>();
    private final List<Synapse> synapses = new ArrayList<>();
    private final Set<NodeId> vetoNodes = new LinkedHashSet<>();

    /**
     * Node ids reached by the most recent {@link #propagate}, in first-reach order.
     *
     * <p>Exposed because the returned {@link Activation} only carries signal <em>values</em>. When
     * several edges tie on weight, every candidate contributes the same number, so two runs that
     * followed DIFFERENT edges return identical signals and a determinism test over signals alone
     * cannot tell them apart. Comparing the reached set is what actually pins the routing.</p>
     */
    private List<NodeId> lastReached = List.of();

    /** Registers a node. Re-adding an id replaces the previous node. */
    public Connectome add(CognitiveNode node) {
        Objects.requireNonNull(node, "node");
        nodes.put(node.id(), node);
        return this;
    }

    /**
     * Declares a node to be a veto (safety) node. Incoming synapses to this node are exempt from
     * pruning and cannot be removed by {@link #prune(Set)}.
     */
    public Connectome registerVeto(NodeId id) {
        Objects.requireNonNull(id, "id");
        vetoNodes.add(id);
        return this;
    }

    /** True if this node was registered as a veto node. */
    public boolean isVeto(NodeId id) {
        return vetoNodes.contains(id);
    }

    /** Creates a directed edge. Both endpoints must already be registered. */
    public Connectome connect(NodeId from, NodeId to, double initialWeight) {
        requireRegistered(from);
        requireRegistered(to);
        synapses.add(new Synapse(from, to, initialWeight, Synapse.SynapseState.CONNECTED));
        return this;
    }

    private void requireRegistered(NodeId id) {
        if (!nodes.containsKey(id)) {
            throw new IllegalArgumentException("unknown node: " + id);
        }
    }

    /** Current synapse list; the returned list is a copy. */
    public List<Synapse> synapses() {
        return List.copyOf(synapses);
    }

    /** Registered node ids, in insertion order. */
    public Set<NodeId> nodeIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(nodes.keySet()));
    }

    /** Node ids reached by the most recent propagation, in first-reach order. */
    public List<NodeId> lastReached() {
        return lastReached;
    }

    /** Number of synapses not in the PRUNED state. */
    public int liveSynapseCount() {
        return (int) synapses.stream().filter(s -> s.state() != Synapse.SynapseState.PRUNED).count();
    }

    /**
     * Routes an activation through the graph using Top-K outgoing edges per node.
     *
     * <p>Sparse by construction: at each node only the {@code topK} highest-scoring outgoing
     * synapses carry signal. Scoring is {@code |weight * source magnitude|}; ties break by target
     * {@link NodeId} so the result does not depend on iteration order.</p>
     *
     * <p>The returned activation carries the summed signal of every node reached, in the order the
     * nodes were reached.</p>
     *
     * @param input the activation to inject at every source node
     * @param topK  maximum outgoing edges followed per node; must be >= 1
     */
    public Activation propagate(Activation input, int topK) {
        Objects.requireNonNull(input, "input");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1, got " + topK);
        }
        Map<NodeId, Double> accumulated = new LinkedHashMap<>();
        double magnitude = input.magnitude();

        for (NodeId source : nodes.keySet()) {
            CognitiveNode node = nodes.get(source);
            if (node.state() != CognitiveNode.NodeState.ACTIVE) continue;

            List<Synapse> out = new ArrayList<>();
            for (Synapse s : synapses) {
                if (s.state() != Synapse.SynapseState.PRUNED
                        && s.from().equals(source)
                        && nodes.get(s.to()).state() == CognitiveNode.NodeState.ACTIVE) {
                    out.add(s);
                }
            }
            // score, then a total order: magnitude desc, then node id asc
            out.sort(Comparator
                    .comparingDouble((Synapse s) -> Math.abs(s.weight()))
                    .reversed()
                    .thenComparing(Synapse::to));

            int taken = 0;
            for (Synapse s : out) {
                if (taken++ >= topK) break;
                Activation forwarded = node.process(input);
                double carried = s.apply(forwarded.magnitude());
                accumulated.merge(s.to(), carried, Double::sum);
            }
        }

        this.lastReached = List.copyOf(accumulated.keySet());
        double[] signal = new double[accumulated.size()];
        int i = 0;
        for (double v : accumulated.values()) signal[i++] = v;
        return input.withSignal(signal.length == 0 ? new double[] {0.0} : signal);
    }

    /**
     * RECON-W38.1 - dual-run retrieval entry point.
     * Same reachability, ordering and tie-breaking as {@link #propagate(Activation, int)}, but a
     * reached HdcNode is given the real retrieval context (query, observation, trace, floor) so its
     * outgoing signal is the measured retrieval confidence scaled by node weight, and non-HDC nodes
     * behave identically to plain propagate(). The linear serving path stays zero-change; the
     * caller compares the two runs for graph-vs-linear divergence.
     *
     * @param input          activation injected at every source node
     * @param query          retrieval query string
     * @param obs            signal-stage observation (may be null on the in-memory path)
     * @param trace          trace list the retrieval stage appends to (may be null)
     * @param retrievalFloor neuromodulator floor applied at retrieval time
     * @param topK           maximum outgoing edges followed per node; must be >= 1
     */
    public Activation propagateWithRetrieval(Activation input, String query,
            io.matrix.brain.runtime.stages.SignalStage.SignalObservation obs,
            java.util.List<io.matrix.brain.runtime.BrcStep> trace,
            double retrievalFloor, int topK) {
        Objects.requireNonNull(input, "input");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1, got " + topK);
        }
        Map<NodeId, Double> accumulated = new LinkedHashMap<>();

        for (NodeId source : nodes.keySet()) {
            CognitiveNode node = nodes.get(source);
            if (node.state() != CognitiveNode.NodeState.ACTIVE) {
                continue;
            }

            List<Synapse> out = new ArrayList<>();
            for (Synapse s : synapses) {
                if (s.state() != Synapse.SynapseState.PRUNED
                        && s.from().equals(source)
                        && nodes.get(s.to()).state() == CognitiveNode.NodeState.ACTIVE) {
                    out.add(s);
                }
            }
            out.sort(Comparator
                    .comparingDouble((Synapse s) -> Math.abs(s.weight()))
                    .reversed()
                    .thenComparing(Synapse::to));

            int taken = 0;
            for (Synapse s : out) {
                if (taken++ >= topK) {
                    break;
                }
                Activation forwarded = node instanceof HdcNode hdc
                        ? hdc.process(input, query, obs, trace, retrievalFloor)
                        : node.process(input);
                double carried = s.apply(forwarded.magnitude());
                accumulated.merge(s.to(), carried, Double::sum);
            }
        }

        this.lastReached = List.copyOf(accumulated.keySet());
        double[] signal = new double[accumulated.size()];
        int i = 0;
        for (double v : accumulated.values()) {
            signal[i++] = v;
        }
        return input.withSignal(signal.length == 0 ? new double[] {0.0} : signal);
    }

    /**
     * Applies plasticity to the synapses that carried signal in the last propagation.
     *
     * @param input  the activation injected at propagation time
     * @param output the activation propagation returned
     */
    public void applyPlasticity(Activation input, Activation output) {
        double pre = input.magnitude();
        double post = output.magnitude();
        for (int i = 0; i < synapses.size(); i++) {
            Synapse s = synapses.get(i);
            if (s.state() == Synapse.SynapseState.PRUNED) continue;
            double next = PlasticityRule.update(s.weight(), pre, post, DEFAULT_ETA, DEFAULT_DECAY);
            synapses.set(i, s.withWeight(next));
        }
    }

    /** Learning rate used by {@link #applyPlasticity}. */
    public static final double DEFAULT_ETA = 0.01;
    /** Per-step decay used by {@link #applyPlasticity}. */
    public static final double DEFAULT_DECAY = 0.001;

    /**
     * Removes synapses marked {@link Synapse.SynapseState#PRUNED}, except those whose target is a
     * veto node.
     *
     * @param exempt extra node ids to protect, beyond the registered veto nodes
     * @return how many synapses were actually removed
     */
    public int prune(Set<NodeId> exempt) {
        Set<NodeId> protectedNodes = new LinkedHashSet<>(vetoNodes);
        if (exempt != null) protectedNodes.addAll(exempt);

        int before = synapses.size();
        synapses.removeIf(s ->
                s.state() == Synapse.SynapseState.PRUNED
                        && !protectedNodes.contains(s.to())
                        && !protectedNodes.contains(s.from()));
        return before - synapses.size();
    }

    /**
     * Reconnects a previously pruned synapse, as a rejected consolidation pass needs.
     *
     * <p>Matches on endpoints rather than on the whole record, so two synapses sharing endpoints
     * and weight cannot cause the wrong edge to be restored.</p>
     *
     * @param target the synapse to reconnect
     * @return true if an edge in PRUNED state was restored
     */
    public boolean restore(Synapse target) {
        for (int i = 0; i < synapses.size(); i++) {
            Synapse s = synapses.get(i);
            if (s.state() == Synapse.SynapseState.PRUNED
                    && s.from().equals(target.from())
                    && s.to().equals(target.to())) {
                synapses.set(i, s.reconnected());
                return true;
            }
        }
        return false;
    }

    /** Marks a synapse pruned. Returns false if the edge does not exist or is protected. */
    public boolean markPruned(Synapse target) {
        if (vetoNodes.contains(target.to()) || vetoNodes.contains(target.from())) {
            return false;
        }
        for (int i = 0; i < synapses.size(); i++) {
            if (synapses.get(i).equals(target)) {
                synapses.set(i, synapses.get(i).pruned());
                return true;
            }
        }
        return false;
    }
}
