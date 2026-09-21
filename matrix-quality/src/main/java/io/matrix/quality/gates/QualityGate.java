package io.matrix.quality.gates;

import java.nio.file.Path;
import java.util.List;

/**
 * WAVE T-10 — Goal Quality Gate.
 *
 * Meta-gate that aggregates results from all other gates. Computes a
 * single quality score (0-100) and produces a final verdict.
 */
public final class QualityGate implements Gate {

    private final List<Gate.GateResult> allResults;

    public QualityGate(List<Gate.GateResult> allResults) {
        this.allResults = List.copyOf(allResults);
    }

    @Override
    public String name() { return "goal-quality-gate"; }

    @Override
    public String description() {
        return "Meta gate: aggregates all other gates into a quality score";
    }

    @Override
    public GateResult run(Path projectRoot) {
        if (allResults.isEmpty()) {
            return new GateResult(name(), GateResult.Status.FAIL,
                "No gate results to score",
                List.of("Run other gates first"));
        }
        int total = allResults.size();
        int passed = (int) allResults.stream()
            .filter(r -> r.status() == Gate.GateResult.Status.PASS
                      || r.status() == Gate.GateResult.Status.WARN)
            .count();
        int failed = total - passed;
        int score = total > 0 ? (int) ((passed * 100.0) / total) : 0;

        String summary = String.format("Quality score: %d/100 (%d/%d passed, %d failed)",
            score, passed, total, failed);
        if (failed == 0) {
            return new GateResult(name(), GateResult.Status.PASS, summary, List.of());
        } else if (score >= 80) {
            return new GateResult(name(), GateResult.Status.WARN, summary,
                List.of("Some non-critical gates failed; review and address"));
        }
        return new GateResult(name(), GateResult.Status.FAIL, summary,
            List.of("Too many critical gates failed; cannot ship"));
    }
}
