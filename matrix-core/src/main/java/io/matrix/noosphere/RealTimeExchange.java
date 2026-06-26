package io.matrix.noosphere;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Real-time FNL exchange between MATRIX instances using WebRTC-like pub/sub.
 *
 * <p>Enables live sharing of Functional Neural Lobes without central server.
 * Uses a simple event-driven pub/sub model (production: integrate WebRTC/QUIC).
 *
 * <p>Ref: L6_Memory.md §6, Noosphere concept
 */
public final class RealTimeExchange {

    private final Map<String, List<Consumer<FnlPackage>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, FnlPackage> publishedPackages = new ConcurrentHashMap<>();
    private final String nodeId;

    public RealTimeExchange(String nodeId) {
        this.nodeId = nodeId;
    }

    public String nodeId() {
        return nodeId;
    }

    public void publish(FnlPackage pkg) {
        publishedPackages.put(pkg.name(), pkg);
        String channel = "fnl:" + pkg.type();
        var subs = subscribers.getOrDefault(channel, List.of());
        for (Consumer<FnlPackage> sub : subs) {
            sub.accept(pkg);
        }
    }

    public void subscribe(String channel, Consumer<FnlPackage> handler) {
        subscribers.computeIfAbsent(channel, k -> new ArrayList<>()).add(handler);
    }

    public void unsubscribe(String channel, Consumer<FnlPackage> handler) {
        var subs = subscribers.get(channel);
        if (subs != null) subs.remove(handler);
    }

    public List<FnlPackage> discover(String typePattern) {
        return publishedPackages.values().stream()
                .filter(p -> p.type().contains(typePattern))
                .toList();
    }

    public Map<String, Integer> stats() {
        Map<String, Integer> s = new LinkedHashMap<>();
        s.put("published_count", publishedPackages.size());
        s.put("subscriber_count", subscribers.values().stream().mapToInt(List::size).sum());
        s.put("channels", subscribers.size());
        return s;
    }
}
