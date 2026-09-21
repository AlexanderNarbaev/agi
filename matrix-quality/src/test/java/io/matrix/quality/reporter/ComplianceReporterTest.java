package io.matrix.quality.reporter;

import io.matrix.quality.gates.Gate;
import io.matrix.quality.gates.GateOrchestrator;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComplianceReporterTest {

    @Test
    void testGenerateReport() {
        GateOrchestrator.Report data = new GateOrchestrator.Report(
            List.of(
                new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of()),
                new Gate.GateResult("g2", Gate.GateResult.Status.FAIL, "broken", List.of("issue"))
            ),
            new java.util.LinkedHashMap<>()
        );
        ComplianceReporter.Report report = new ComplianceReporter().generate(data);
        assertEquals(50, report.qualityScore());
        assertFalse(report.allPassed());
        assertEquals(2, report.results().size());
        assertNotNull(report.reportHash());
    }

    @Test
    void testRenderMarkdown() {
        GateOrchestrator.Report data = new GateOrchestrator.Report(
            List.of(new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of())),
            new java.util.LinkedHashMap<>()
        );
        ComplianceReporter.Report report = new ComplianceReporter().generate(data);
        String md = new ComplianceReporter().render(report, ComplianceReporter.Format.MARKDOWN);
        assertTrue(md.contains("# MATRIX Quality Report"));
        assertTrue(md.contains("| Gate | Status | Summary |"));
    }

    @Test
    void testRenderJson() {
        GateOrchestrator.Report data = new GateOrchestrator.Report(
            List.of(new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of())),
            new java.util.LinkedHashMap<>()
        );
        ComplianceReporter.Report report = new ComplianceReporter().generate(data);
        String json = new ComplianceReporter().render(report, ComplianceReporter.Format.JSON);
        assertTrue(json.contains("\"qualityScore\""));
        assertTrue(json.contains("\"gateCountSummary\""));
    }

    @Test
    void testRenderText() {
        GateOrchestrator.Report data = new GateOrchestrator.Report(
            List.of(new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of())),
            new java.util.LinkedHashMap<>()
        );
        ComplianceReporter.Report report = new ComplianceReporter().generate(data);
        String txt = new ComplianceReporter().render(report, ComplianceReporter.Format.TEXT);
        assertTrue(txt.contains("MATRIX QUALITY REPORT"));
    }
}
