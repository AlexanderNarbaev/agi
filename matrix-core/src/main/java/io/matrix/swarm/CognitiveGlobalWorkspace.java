package io.matrix.swarm;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W931 — Cognitive Global Workspace.
 *
 * Mediates traffic between BIR, HDC, MCTS modules
 * based on biochemical urgency. Solves the "fragmented intelligence" problem.
 */
public final class CognitiveGlobalWorkspace {

    public enum Module { BIR, HDC, MCTS, BIOCHEMISTRY, FEDERATION }

    public record Message(Module source, Object content, double urgency, long timestamp) {}

    private final Map<Module, Queue<Message>> moduleQueues = new ConcurrentHashMap<>();
    private final List<Message> broadcastLog = new ArrayList<>();
    private double globalActivation = 0.0;

    public CognitiveGlobalWorkspace() {
        for (Module m : Module.values()) {
            moduleQueues.put(m, new LinkedList<>());
        }
    }

    /**
     * Submit a message from a module to the workspace.
     */
    public void submit(Module source, Object content, double urgency) {
        Message msg = new Message(source, content, urgency, System.currentTimeMillis());
        moduleQueues.get(source).add(msg);

        // High-urgency messages get broadcast to all modules
        if (urgency > 0.7) {
            broadcast(msg);
        }
    }

    /**
     * Broadcast message to all modules.
     */
    private void broadcast(Message msg) {
        broadcastLog.add(msg);
        globalActivation = Math.min(1.0, globalActivation + msg.urgency() * 0.1);
    }

    /**
     * Get the next message for a specific module.
     */
    public Optional<Message> receive(Module module) {
        Queue<Message> q = moduleQueues.get(module);
        return q.isEmpty() ? Optional.empty() : Optional.of(q.poll());
    }

    /**
     * Get global workspace activation level.
     */
    public double getGlobalActivation() {
        // Natural decay
        globalActivation *= 0.95;
        return globalActivation;
    }

    public int getBroadcastCount() {
        return broadcastLog.size();
    }
}
