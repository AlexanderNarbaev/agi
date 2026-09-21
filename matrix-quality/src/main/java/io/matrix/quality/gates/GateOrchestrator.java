package io.matrix.quality.gates;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WAVE T-10 — Gate Orchestrator.
 *
 * Runs all 14 Goal Guard gates against a project root. Produces a
 * full report with pass/fail status per gate.
 *
 * <p>Replaces the persistent "missing review gates" warning by
 * implementing all 14 gates the Goal Guard plugin expects.</p>
 */
public final class GateOrchestrator {

    private final Path projectRoot;
    private final List<Gate> gates = new ArrayList<>();

    public GateOrchestrator(Path projectRoot) {
        this.projectRoot = projectRoot;
        // Register all 14 gates in order
        gates.add(new PromptAuditorGate());
        gates.add(new ReviewerGate());
        gates.add(new DiffReviewerGate());
        gates.add(new VerifierGate());
        gates.add(new TestReviewerGate());
        gates.add(new DataReviewerGate());
        gates.add(new OpsReviewerGate());
        gates.add(new PerfReviewerGate());
        gates.add(new UxReviewerGate());
        gates.add(new DocReviewerGate());
        gates.add(new ApiReviewerGate());
        gates.add(new SecurityReviewerGate());
    }

    public Report runAll() {
        List<Gate.GateResult> results = new ArrayList<>();
        for (Gate gate : gates) {
            try {
                results.add(gate.run(projectRoot));
            } catch (Exception e) {
                results.add(new Gate.GateResult(
                    gate.name(),
                    Gate.GateResult.Status.FAIL,
                    "Gate threw: " + e.getMessage(),
                    List.of(e.toString())
                ));
            }
        }
        // Final auditor + quality gate need all results
        results.add(new FinalAuditorGate(results).run(projectRoot));
        results.add(new QualityGate(results).run(projectRoot));

        Map<String, Gate.GateResult> byName = new LinkedHashMap<>();
        for (Gate.GateResult r : results) {
            byName.put(r.gateName(), r);
        }
        return new Report(results, byName);
    }

    public int gateCount() {
        return gates.size() + 2;  // +2 for FinalAuditor and QualityGate
    }

    /** Complete report from running all gates. */
    public record Report(
        List<Gate.GateResult> results,
        Map<String, Gate.GateResult> byName
    ) {
        public boolean allPass() {
            return results.stream().allMatch(r -> r.isPass());
        }
        public int passed() {
            return (int) results.stream().filter(r -> r.isPass()).count();
        }
        public int failed() {
            return (int) results.stream().filter(r -> !r.isPass()).count();
        }
        public int total() {
            return results.size();
        }
        public int qualityScore() {
            return total() > 0 ? (passed() * 100) / total() : 0;
        }
    }
}
