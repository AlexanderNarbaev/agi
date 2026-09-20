package io.matrix.federation.liquid.biochemistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * W655 — Stigmergy Protocol for Emergent Swarm Intelligence.
 *
 * Implements "digital pheromones" — nodes leave traces in shared memory
 * to attract others to hot topics, enabling spontaneous cluster formation
 * without central coordination.
 *
 * Pheromone Types:
 * - EXPLORATION: "I found something interesting here"
 * - DANGER: "Avoid this area"
 * - REWARD: "This approach works well"
 * - COORDINATION: "Let's meet here"
 *
 * Pheromone decay follows exponential decay: strength *= (1 - decayRate) per tick
 */
public final class StigmergyProtocol {

    public enum PheromoneType {
        EXPLORATION,
        DANGER,
        REWARD,
        COORDINATION
    }

    public record Pheromone(
            String id,
            String sourceNodeId,
            String topic,
            PheromoneType type,
            double strength,
            long timestamp,
            Map<String, String> metadata
    ) {}

    public record PheromoneSignal(
            String topic,
            PheromoneType type,
            double aggregatedStrength,
            int signalCount,
            long latestTimestamp
    ) {}

    private final ConcurrentHashMap<String, List<Pheromone>> pheromones = new ConcurrentHashMap<>();
    private final double decayRate;
    private final double minStrength;
    private final AtomicLong idCounter = new AtomicLong(0);

    public StigmergyProtocol(double decayRate, double minStrength) {
        this.decayRate = decayRate;
        this.minStrength = minStrength;
    }

    public Pheromone deposit(String sourceNodeId, String topic, PheromoneType type,
                             double strength, Map<String, String> metadata) {
        Pheromone pheromone = new Pheromone(
                "pheromone-" + idCounter.incrementAndGet(),
                sourceNodeId, topic, type, strength,
                System.currentTimeMillis(),
                metadata != null ? metadata : Map.of()
        );
        pheromones.computeIfAbsent(topic, k -> new ArrayList<>()).add(pheromone);
        return pheromone;
    }

    public List<Pheromone> sense(String topic) {
        List<Pheromone> list = pheromones.get(topic);
        if (list == null) return List.of();
        return new ArrayList<>(list);
    }

    public PheromoneSignal getSignal(String topic) {
        List<Pheromone> list = pheromones.get(topic);
        if (list == null || list.isEmpty()) {
            return new PheromoneSignal(topic, PheromoneType.EXPLORATION, 0, 0, 0);
        }
        double totalStrength = 0;
        long latestTimestamp = 0;
        Map<PheromoneType, Double> typeStrengths = new HashMap<>();
        for (Pheromone p : list) {
            totalStrength += p.strength();
            typeStrengths.merge(p.type(), p.strength(), Double::sum);
            latestTimestamp = Math.max(latestTimestamp, p.timestamp());
        }
        PheromoneType dominantType = PheromoneType.EXPLORATION;
        double maxStrength = 0;
        for (var entry : typeStrengths.entrySet()) {
            if (entry.getValue() > maxStrength) {
                maxStrength = entry.getValue();
                dominantType = entry.getKey();
            }
        }
        return new PheromoneSignal(topic, dominantType, totalStrength, list.size(), latestTimestamp);
    }

    public Set<String> getActiveTopics() {
        return new HashSet<>(pheromones.keySet());
    }

    public void tick() {
        Iterator<Map.Entry<String, List<Pheromone>>> topicIt = pheromones.entrySet().iterator();
        while (topicIt.hasNext()) {
            Map.Entry<String, List<Pheromone>> entry = topicIt.next();
            List<Pheromone> list = entry.getValue();
            list.removeIf(p -> p.strength() * (1 - decayRate) < minStrength);
            for (int i = 0; i < list.size(); i++) {
                Pheromone p = list.get(i);
                list.set(i, new Pheromone(p.id(), p.sourceNodeId(), p.topic(), p.type(),
                        p.strength() * (1 - decayRate), p.timestamp(), p.metadata()));
            }
            if (list.isEmpty()) topicIt.remove();
        }
    }

    public List<String> getHotTopics(int limit) {
        List<Map.Entry<String, Double>> topicStrengths = new ArrayList<>();
        for (var entry : pheromones.entrySet()) {
            double total = 0;
            for (Pheromone p : entry.getValue()) total += p.strength();
            topicStrengths.add(Map.entry(entry.getKey(), total));
        }
        topicStrengths.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, topicStrengths.size()); i++) {
            result.add(topicStrengths.get(i).getKey());
        }
        return result;
    }

    public void clear() { pheromones.clear(); }

    public int getTotalCount() {
        int count = 0;
        for (var list : pheromones.values()) count += list.size();
        return count;
    }

    public static StigmergyProtocol createDefault() {
        return new StigmergyProtocol(0.1, 0.01);
    }
}
