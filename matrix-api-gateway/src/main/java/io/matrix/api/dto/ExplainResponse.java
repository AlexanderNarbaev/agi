package io.matrix.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * WAVE T-02 — Explain response DTO (XAI breakdown).
 *
 * <p>Provides a step-by-step trace of how the decision was reached:
 * BIR rules fired, HDC memory retrievals, MCTS plan steps, modulator
 * state at decision time, and confidence per stage.</p>
 */
public final class ExplainResponse {

    @JsonProperty("explain_id")
    public String explainId;

    @JsonProperty("steps")
    public List<Step> steps;

    @JsonProperty("modulator_snapshot")
    public ModulatorSnapshot modulatorSnapshot;

    @JsonProperty("hdc_memory_hits")
    public List<String> hdcMemoryHits;

    @JsonProperty("confidence_breakdown")
    public ConfidenceBreakdown confidenceBreakdown;

    public ExplainResponse() {}

    public static final class Step {
        @JsonProperty("stage")
        public String stage;

        @JsonProperty("action")
        public String action;

        @JsonProperty("duration_ms")
        public long durationMs;

        public Step() {}
        public Step(String stage, String action, long durationMs) {
            this.stage = stage;
            this.action = action;
            this.durationMs = durationMs;
        }
    }

    public static final class ModulatorSnapshot {
        @JsonProperty("ethical_filter")
        public double ethicalFilter;

        @JsonProperty("safety_monitor")
        public double safetyMonitor;

        @JsonProperty("consistency_checker")
        public double consistencyChecker;

        @JsonProperty("lie_detector")
        public double lieDetector;

        public ModulatorSnapshot() {}
        public ModulatorSnapshot(double ethics, double safety, double consistency, double lie) {
            this.ethicalFilter = ethics;
            this.safetyMonitor = safety;
            this.consistencyChecker = consistency;
            this.lieDetector = lie;
        }
    }

    public static final class ConfidenceBreakdown {
        @JsonProperty("bir_confidence")
        public double birConfidence;

        @JsonProperty("hdc_confidence")
        public double hdcConfidence;

        @JsonProperty("mcts_confidence")
        public double mctsConfidence;

        @JsonProperty("aggregate")
        public double aggregate;

        public ConfidenceBreakdown() {}
        public ConfidenceBreakdown(double bir, double hdc, double mcts, double aggregate) {
            this.birConfidence = bir;
            this.hdcConfidence = hdc;
            this.mctsConfidence = mcts;
            this.aggregate = aggregate;
        }
    }
}
