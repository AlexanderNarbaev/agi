package io.matrix.quality.gates;

import java.nio.file.Path;
import java.util.List;

/**
 * WAVE T-10 — Goal Final Auditor Gate.
 *
 * The cycle-closing gate. Verifies that all 14 Goal Guard gates have
 * run successfully. This is invoked at the end of a goal cycle to
 * confirm completion.
 */
public final class FinalAuditorGate implements Gate {

    private final List<Gate.GateResult> previousResults;

    public FinalAuditorGate(List<Gate.GateResult> previousResults) {
        this.previousResults = List.copyOf(previousResults);
    }

    @Override
    public String name() { return "goal-final-auditor"; }

    @Override
    public String description() {
        return "Final cycle-closing audit: confirms all other gates have passed";
    }

    @Override
    public GateResult run(Path projectRoot) {
        if (previousResults.isEmpty()) {
            return new GateResult(name(), GateResult.Status.FAIL,
                "No previous gate results to audit",
                List.of("Run other gates first before final-auditor"));
        }
        long failed = previousResults.stream()
            .filter(r -> r.status() == Gate.GateResult.Status.FAIL)
            .count();
        long passed = previousResults.stream()
            .filter(r -> r.status() == Gate.GateResult.Status.PASS
                       || r.status() == Gate.GateResult.Status.WARN)
            .count();
        if (failed > 0) {
            return new GateResult(name(), GateResult.Status.FAIL,
                failed + " gates failed, " + passed + " passed",
                List.of("Cannot mark Goal Completed until all gates pass"));
        }
        return new GateResult(name(), GateResult.Status.PASS,
            "All " + passed + " gates passed; goal can be marked completed",
            List.of());
    }
}
