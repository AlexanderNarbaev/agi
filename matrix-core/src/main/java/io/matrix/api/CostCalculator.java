package io.matrix.api;

/**
 * RUN 118 — Cost calculator.
 *
 * <p>Tracks cost per million tokens for different models so we can
 * compute estimated dollar cost for a generation request.
 */
public final class CostCalculator {

    /** Cost per million tokens (USD). */
    public record ModelCost(String model, double inputCostPerMTok,
                            double outputCostPerMTok) {}

    private final ModelCost cost;

    public CostCalculator(String model, double inputCostPerMTok,
                          double outputCostPerMTok) {
        this.cost = new ModelCost(model, inputCostPerMTok, outputCostPerMTok);
    }

    /** Default Qwen2.5-0.5B self-hosted cost: $0.10/MTok input, $0.30/MTok output. */
    public static CostCalculator qwen05bSelfHosted() {
        return new CostCalculator("Qwen2.5-0.5B", 0.10, 0.30);
    }

    /** Default OpenAI-style cloud cost (gpt-3.5-turbo-ish). */
    public static CostCalculator cloudLowTier() {
        return new CostCalculator("cloud-low", 0.50, 1.50);
    }

    public double costFor(long inputTokens, long outputTokens) {
        double inputCost = (inputTokens / 1_000_000.0) * cost.inputCostPerMTok;
        double outputCost = (outputTokens / 1_000_000.0) * cost.outputCostPerMTok;
        return inputCost + outputCost;
    }

    public ModelCost getCost() { return cost; }
}
