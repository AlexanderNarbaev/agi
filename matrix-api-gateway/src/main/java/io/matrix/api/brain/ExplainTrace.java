package io.matrix.api.brain;

import java.util.List;

/**
 * WAVE T-02 — XAI explanation trace.
 */
public final class ExplainTrace {
    public final String explainId;
    public final List<BrainCycle.CycleResult> steps;
    public final double ethicalFilter;
    public final double safetyMonitor;
    public final double consistencyChecker;
    public final double lieDetector;
    public final List<String> hdcMemoryHits;
    public final double birConfidence;
    public final double hdcConfidence;
    public final double mctsConfidence;
    public final double aggregate;

    public ExplainTrace(String explainId,
                        List<BrainCycle.CycleResult> steps,
                        double ethicalFilter, double safetyMonitor,
                        double consistencyChecker, double lieDetector,
                        List<String> hdcMemoryHits,
                        double birConfidence, double hdcConfidence,
                        double mctsConfidence, double aggregate) {
        this.explainId = explainId;
        this.steps = steps;
        this.ethicalFilter = ethicalFilter;
        this.safetyMonitor = safetyMonitor;
        this.consistencyChecker = consistencyChecker;
        this.lieDetector = lieDetector;
        this.hdcMemoryHits = hdcMemoryHits;
        this.birConfidence = birConfidence;
        this.hdcConfidence = hdcConfidence;
        this.mctsConfidence = mctsConfidence;
        this.aggregate = aggregate;
    }
}
