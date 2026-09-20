package io.matrix.federation.liquid.biochemistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W651 — Biochemical Network with Non-Linear Interactions.
 *
 * Models the hormonal cocktail effect: modulators don't act in isolation.
 * They interact through synergy (A+B > A+B) and antagonism (A inhibits B).
 *
 * Interaction Types:
 * - SYNERGY: Positive coefficient → modulators amplify each other
 * - ANTAGONISM: Negative coefficient → one modulator suppresses another
 * - CATALYSIS: Multiplicative effect → A accelerates B's production
 *
 * Mathematics:
 *   For each modulator i:
 *     interaction_i = Σ_j (W[i][j] * level_j * synergyFactor(i,j))
 *
 *   Where synergyFactor is non-linear:
 *     synergyFactor(i,j) = 1 + α * level_i * level_j  (for synergy)
 *     synergyFactor(i,j) = 1 / (1 + β * level_j)      (for antagonism)
 *
 * Performance: O(N²) where N = number of modulators (typically N ≤ 20).
 */
public final class BiochemicalNetwork {

    public enum InteractionType {
        SYNERGY,
        ANTAGONISM,
        CATALYSIS,
        NONE
    }

    public record Interaction(
            String sourceId,
            String targetId,
            InteractionType type,
            double weight,
            double nonLinearity
    ) {}

    public record ModulatorSnapshot(Map<String, Double> levels) {
        public double get(String id) {
            return levels.getOrDefault(id, 0.0);
        }
    }

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Interaction>> interactions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> synergyCache = new ConcurrentHashMap<>();

    public void addInteraction(Interaction interaction) {
        interactions.computeIfAbsent(interaction.sourceId(), k -> new ConcurrentHashMap<>())
                .put(interaction.targetId(), interaction);
        String pairKey = pairKey(interaction.sourceId(), interaction.targetId());
        if (interaction.type() == InteractionType.SYNERGY) {
            synergyCache.put(pairKey, interaction.weight());
        }
    }

    public void removeInteraction(String sourceId, String targetId) {
        var targetMap = interactions.get(sourceId);
        if (targetMap != null) {
            targetMap.remove(targetId);
        }
        synergyCache.remove(pairKey(sourceId, targetId));
    }

    public Map<String, Double> computeInteractionEffects(ModulatorSnapshot snapshot) {
        Map<String, Double> effects = new HashMap<>();
        for (var sourceEntry : interactions.entrySet()) {
            String sourceId = sourceEntry.getKey();
            double sourceLevel = snapshot.get(sourceId);
            for (var targetEntry : sourceEntry.getValue().entrySet()) {
                Interaction interaction = targetEntry.getValue();
                double targetLevel = snapshot.get(interaction.targetId());
                double effect = computeSingleEffect(interaction, sourceLevel, targetLevel);
                effects.merge(interaction.targetId(), effect, Double::sum);
            }
        }
        return effects;
    }

    private double computeSingleEffect(Interaction interaction, double sourceLevel, double targetLevel) {
        return switch (interaction.type()) {
            case SYNERGY -> {
                double linear = interaction.weight() * sourceLevel;
                double nonLinearBoost = 1.0 + interaction.nonLinearity() * sourceLevel * targetLevel;
                yield linear * nonLinearBoost;
            }
            case ANTAGONISM -> {
                double suppression = 1.0 / (1.0 + interaction.nonLinearity() * targetLevel);
                yield -Math.abs(interaction.weight()) * sourceLevel * suppression;
            }
            case CATALYSIS -> interaction.weight() * sourceLevel * targetLevel;
            case NONE -> 0.0;
        };
    }

    public List<Interaction> getInteractionsFrom(String sourceId) {
        var targetMap = interactions.get(sourceId);
        if (targetMap == null) return List.of();
        return new ArrayList<>(targetMap.values());
    }

    public List<Interaction> getInteractionsTo(String targetId) {
        List<Interaction> result = new ArrayList<>();
        for (var sourceEntry : interactions.values()) {
            var interaction = sourceEntry.get(targetId);
            if (interaction != null) result.add(interaction);
        }
        return result;
    }

    public double getSynergyCoefficient(String id1, String id2) {
        return synergyCache.getOrDefault(pairKey(id1, id2), 0.0);
    }

    public boolean hasInteraction(String sourceId, String targetId) {
        var targetMap = interactions.get(sourceId);
        return targetMap != null && targetMap.containsKey(targetId);
    }

    public Set<String> getAllModulatorIds() {
        Set<String> ids = new HashSet<>();
        ids.addAll(interactions.keySet());
        for (var targetMap : interactions.values()) ids.addAll(targetMap.keySet());
        return ids;
    }

    public int getInteractionCount() {
        int count = 0;
        for (var targetMap : interactions.values()) count += targetMap.size();
        return count;
    }

    public void clear() {
        interactions.clear();
        synergyCache.clear();
    }

    public List<Interaction> exportInteractions() {
        List<Interaction> result = new ArrayList<>();
        for (var targetMap : interactions.values()) result.addAll(targetMap.values());
        return result;
    }

    public void importInteractions(List<Interaction> interactionList) {
        clear();
        for (Interaction interaction : interactionList) addInteraction(interaction);
    }

    public static BiochemicalNetwork createStressCascade() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new Interaction("CORTISOL", "DOPAMINE", InteractionType.ANTAGONISM, 0.7, 0.8));
        network.addInteraction(new Interaction("CORTISOL", "SEROTONIN", InteractionType.ANTAGONISM, 0.5, 0.6));
        network.addInteraction(new Interaction("DOPAMINE", "SEROTONIN", InteractionType.SYNERGY, 0.3, 0.4));
        network.addInteraction(new Interaction("NOREPINEPHRINE", "CORTISOL", InteractionType.CATALYSIS, 0.6, 0.5));
        network.addInteraction(new Interaction("SEROTONIN", "NOREPINEPHRINE", InteractionType.ANTAGONISM, 0.4, 0.3));
        return network;
    }

    private String pairKey(String id1, String id2) {
        return id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
    }
}
