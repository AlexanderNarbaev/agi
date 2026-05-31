package io.matrix.civilization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Protocol "Knowledge Weaving" — cross-cultural knowledge exchange
 * without forced format unification.
 *
 * <p>Each side preserves its native representation. Divergences are
 * treated as sources of new insight, not errors to be corrected.
 * Publications occur in languages of all participating sides.
 *
 * <p>Ref: L6_Memory.md §6.5, L8_Roadmap.md §3.8
 */
public class KnowledgeWeaving {

    public enum Resolution {
        ACCEPTED_AS_IS,
        MERGED_WITH_PRESERVATION,
        DIVERGENT_KEPT,
        CONFLICT_ESCALATED
    }

    public record WeavingResult(
            UUID exchangeId,
            String sideA,
            String sideB,
            Resolution resolution,
            String insight,
            Map<String, String> preservedDifferences
    ) {}

    /**
     * Weaves knowledge between two sides, preserving all differences.
     */
    public WeavingResult weave(String sideA, String knowledgeA,
                                String sideB, String knowledgeB,
                                List<String> sharedLanguages) {
        Map<String, String> differences = new HashMap<>();
        StringBuilder insight = new StringBuilder();

        String[] linesA = knowledgeA.split("\n");
        String[] linesB = knowledgeB.split("\n");

        int maxLines = Math.max(linesA.length, linesB.length);
        int sameCount = 0;
        int diffCount = 0;

        for (int i = 0; i < maxLines; i++) {
            String lineA = i < linesA.length ? linesA[i].trim() : "";
            String lineB = i < linesB.length ? linesB[i].trim() : "";

            if (lineA.equals(lineB) && !lineA.isEmpty()) {
                sameCount++;
            } else if (!lineA.equals(lineB)) {
                diffCount++;
                String key = "line." + i;
                differences.put(key + ".a", lineA);
                differences.put(key + ".b", lineB);
            }
        }

        Resolution resolution;
        if (diffCount == 0) {
            resolution = Resolution.ACCEPTED_AS_IS;
            insight.append("Knowledge identical between ").append(sideA)
                    .append(" and ").append(sideB);
        } else if (diffCount <= 2) {
            resolution = Resolution.MERGED_WITH_PRESERVATION;
            insight.append("Minor divergences preserved between ")
                    .append(sideA).append(" and ").append(sideB)
                    .append(". ").append(diffCount).append(" differences archived.");
        } else {
            resolution = Resolution.DIVERGENT_KEPT;
            insight.append("Significant divergence between ")
                    .append(sideA).append(" and ").append(sideB)
                    .append(". ").append(diffCount).append(" differences preserved as knowledge.")
                    .append(" Shared languages: ").append(String.join(", ", sharedLanguages));
        }

        return new WeavingResult(UUID.randomUUID(), sideA, sideB,
                resolution, insight.toString(), differences);
    }
}
