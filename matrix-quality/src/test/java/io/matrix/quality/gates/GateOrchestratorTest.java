package io.matrix.quality.gates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GateOrchestratorTest {

    @Test
    void testRunsAllGates(@TempDir Path tempDir) {
        GateOrchestrator orch = new GateOrchestrator(tempDir);
        GateOrchestrator.Report report = orch.runAll();
        // 14 gates + FinalAuditor + QualityGate = 16 results
        assertTrue(report.total() >= 14);
        assertNotNull(report.results());
        assertTrue(report.qualityScore() >= 0 && report.qualityScore() <= 100);
    }

    @Test
    void testGateNames(@TempDir Path tempDir) {
        GateOrchestrator orch = new GateOrchestrator(tempDir);
        GateOrchestrator.Report report = orch.runAll();
        // Spot-check a few expected gates
        assertTrue(report.byName().containsKey("goal-prompt-auditor"));
        assertTrue(report.byName().containsKey("goal-reviewer"));
        assertTrue(report.byName().containsKey("goal-diff-reviewer"));
        assertTrue(report.byName().containsKey("goal-verifier"));
        assertTrue(report.byName().containsKey("goal-test-reviewer"));
        assertTrue(report.byName().containsKey("goal-data-reviewer"));
        assertTrue(report.byName().containsKey("goal-ops-reviewer"));
        assertTrue(report.byName().containsKey("goal-perf-reviewer"));
        assertTrue(report.byName().containsKey("goal-ux-reviewer"));
        assertTrue(report.byName().containsKey("goal-doc-reviewer"));
        assertTrue(report.byName().containsKey("goal-api-reviewer"));
        assertTrue(report.byName().containsKey("goal-security-reviewer"));
        assertTrue(report.byName().containsKey("goal-quality-gate"));
        assertTrue(report.byName().containsKey("goal-final-auditor"));
    }

    @Test
    void testGateCountIs14(@TempDir Path tempDir) {
        GateOrchestrator orch = new GateOrchestrator(tempDir);
        assertEquals(14, orch.gateCount());
    }
}
