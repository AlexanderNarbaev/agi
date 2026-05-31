package io.matrix.shadow;

/**
 * Estimates environmental/energy cost of computational actions.
 *
 * <p>Evaluates actions against regenerative economy principles:
 * energy consumption, resource waste, long-term sustainability.
 *
 * <p>Ref: L8_Roadmap.md §3.6
 */
public class EcoAudit {

    public record EcoAssessment(
            double energyCost,
            double carbonEstimate,
            double sustainabilityScore,
            String summary,
            boolean acceptable
    ) {
        public static EcoAssessment clean() {
            return new EcoAssessment(0, 0, 1.0, "No significant eco impact", true);
        }
    }

    private static final double LOW_THRESHOLD = 0.3;
    private static final double HIGH_THRESHOLD = 0.7;

    /**
     * Evaluates the ecological cost of an action.
     */
    public EcoAssessment evaluate(String action) {
        String lower = action.toLowerCase();

        double energyCost = estimateEnergy(lower);
        double carbon = energyCost * 0.4;
        double sustainability = 1.0 - energyCost;

        boolean acceptable = energyCost <= HIGH_THRESHOLD;
        String summary;

        if (energyCost <= LOW_THRESHOLD) {
            summary = "Low eco impact";
        } else if (energyCost <= HIGH_THRESHOLD) {
            summary = "Moderate eco impact — consider batching or scheduling off-peak";
        } else {
            summary = "High eco impact — recommended to defer or optimize";
        }

        return new EcoAssessment(energyCost, carbon, sustainability, summary, acceptable);
    }

    private double estimateEnergy(String action) {
        double cost = 0.0;

        if (containsAny(action, "train", "cauldron", "evolve", "generation")) {
            cost += 0.4;
        }
        if (containsAny(action, "full snapshot", "export all", "migrate")) {
            cost += 0.3;
        }
        if (containsAny(action, "batch process", "scan all", "index full")) {
            cost += 0.2;
        }
        if (containsAny(action, "optimize", "compress", "prune")) {
            cost -= 0.1;
        }

        return Math.max(0.0, Math.min(1.0, cost));
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
