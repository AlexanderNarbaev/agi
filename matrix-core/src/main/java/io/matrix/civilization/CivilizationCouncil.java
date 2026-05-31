package io.matrix.civilization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Council of Civilizations — advisory body for resolving
 * value conflicts between instances from different cultural contexts.
 *
 * <p>Each civilization (cultural instance) has a seat with cultural
 * values, ethical priorities, and language preferences.
 * The Council mediates value conflicts, issues advisory opinions,
 * and maintains a register of resolved cultural precedents.
 *
 * <p>Ref: L8_Roadmap.md §3.8-3
 */
public class CivilizationCouncil {

    public record Civilization(
            String id,
            String name,
            String primaryLanguage,
            List<String> culturalValues,
            List<String> ethicalPriorities
    ) {}

    public record CouncilResolution(
            UUID resolutionId,
            String topic,
            String conflict,
            String resolution,
            List<String> votingCivilizations,
            int votesFor,
            int votesAgainst,
            boolean adopted
    ) {
        public static CouncilResolution adopted(String topic, String conflict,
                                                  String resolution, List<String> voters) {
            return new CouncilResolution(UUID.randomUUID(), topic, conflict,
                    resolution, voters, voters.size(), 0, true);
        }

        public static CouncilResolution deadlocked(String topic, String conflict,
                                                     List<String> voters, int forVotes, int againstVotes) {
            return new CouncilResolution(UUID.randomUUID(), topic, conflict,
                    "Deadlocked — escalated to GlobalMediator", voters,
                    forVotes, againstVotes, false);
        }
    }

    private final List<Civilization> civilizations = new ArrayList<>();
    private final List<CouncilResolution> resolutions = new ArrayList<>();
    private final KnowledgeWeaving weaver = new KnowledgeWeaving();

    /**
     * Registers a civilization to the Council.
     */
    public void register(Civilization civ) {
        civilizations.add(civ);
    }

    /**
     * Mediates a value conflict between two civilizations.
     */
    public CouncilResolution mediate(String topic, String conflict,
                                       String sideA, String positionA,
                                       String sideB, String positionB) {
        List<String> allVoters = civilizations.stream()
                .map(Civilization::id)
                .toList();

        KnowledgeWeaving.WeavingResult weave = weaver.weave(sideA, positionA, sideB, positionB,
                List.of("en", "ru"));

        if (weave.resolution() == KnowledgeWeaving.Resolution.ACCEPTED_AS_IS) {
            var resolution = CouncilResolution.adopted(topic, conflict,
                    "Consensus: positions are reconcilable. " + weave.insight(),
                    allVoters);
            resolutions.add(resolution);
            return resolution;
        }

        if (weave.resolution() == KnowledgeWeaving.Resolution.DIVERGENT_KEPT) {
            var resolution = CouncilResolution.deadlocked(topic, conflict,
                    allVoters, 6, 4);
            resolutions.add(resolution);
            return resolution;
        }

        var resolution = CouncilResolution.adopted(topic, conflict,
                "Mediated: differences preserved. " + weave.insight(),
                allVoters);
        resolutions.add(resolution);
        return resolution;
    }

    /**
     * Issues an advisory opinion based on cultural context.
     */
    public String advisoryOpinion(String civilizationId, String topic,
                                    Map<String, String> context) {
        Civilization civ = civilizations.stream()
                .filter(c -> c.id().equals(civilizationId))
                .findFirst().orElse(null);

        if (civ == null) return "Civilization not registered";

        StringBuilder opinion = new StringBuilder();
        opinion.append("Advisory for ").append(civ.name()).append(" on '")
                .append(topic).append("':\n");
        opinion.append("  Values: ").append(String.join(", ", civ.culturalValues())).append("\n");
        opinion.append("  Priorities: ").append(String.join(", ", civ.ethicalPriorities())).append("\n");

        if (context.containsKey("urgency") && context.get("urgency").equals("high")) {
            opinion.append("  Expedited review recommended due to urgency.");
        }

        return opinion.toString();
    }

    public List<Civilization> civilizations() { return List.copyOf(civilizations); }

    public List<CouncilResolution> resolutions() { return List.copyOf(resolutions); }

    public KnowledgeWeaving weaver() { return weaver; }
}
