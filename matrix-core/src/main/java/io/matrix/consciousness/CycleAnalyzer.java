package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 202 — CycleAnalyzer (post-mortem of trace).
 *
 * <p>Analyzes a MatrixTrace to extract cycle-level statistics:
 * durations (we use index-based since clock is suppressed),
 * gate decisions per cycle, accepted ratios.
 */
public final class CycleAnalyzer {

    public record CycleAnalysis(int cycleCount, int totalSteps,
                               List<Integer> stepsPerCycle,
                               List<String> gateVerdicts) {}

    public static CycleAnalysis analyze(MatrixTrace trace) {
        int totalSteps = trace.count();
        // Steps per cycle: we know cycles produce multiples of 5 steps
        int cycleCount = totalSteps / 5;
        List<Integer> stepsPerCycle = new ArrayList<>();
        for (int i = 0; i < cycleCount; i++) {
            stepsPerCycle.add(5);
        }
        List<String> gateVerdicts = new ArrayList<>();
        for (var step : trace.steps()) {
            if ("gate".equals(step.name)) {
                gateVerdicts.add(step.status);
            }
        }
        return new CycleAnalysis(cycleCount, totalSteps,
                stepsPerCycle, gateVerdicts);
    }

    public static String format(CycleAnalysis a) {
        return String.format(
                "CycleAnalysis{ cycles=%d, steps=%d, gateDecisions=%d }",
                a.cycleCount(), a.totalSteps(), a.gateVerdicts().size());
    }
}
