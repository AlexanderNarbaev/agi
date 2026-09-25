package io.matrix.quality.gates;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QualityGateTest {

    @Test
    void testPerfectScore() {
        List<Gate.GateResult> results = List.of(
            new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of()),
            new Gate.GateResult("g2", Gate.GateResult.Status.PASS, "ok", List.of())
        );
        Gate.GateResult r = new QualityGate(results).run(Path.of("."));
        assertEquals(100, extractScore(r));
        assertEquals(Gate.GateResult.Status.PASS, r.status());
    }

    @Test
    void testPartialScore() {
        List<Gate.GateResult> results = List.of(
            new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of()),
            new Gate.GateResult("g2", Gate.GateResult.Status.FAIL, "broken", List.of())
        );
        Gate.GateResult r = new QualityGate(results).run(Path.of("."));
        assertEquals(50, extractScore(r));
        assertEquals(Gate.GateResult.Status.FAIL, r.status());
    }

    @Test
    void testEmptyResults() {
        Gate.GateResult r = new QualityGate(List.of()).run(Path.of("."));
        assertEquals(Gate.GateResult.Status.FAIL, r.status());
    }

    private int extractScore(Gate.GateResult r) {
        // Score is encoded in the summary like "Quality score: 50/100"
        String summary = r.summary();
        int slash = summary.indexOf('/');
        int colon = summary.indexOf(':');
        return Integer.parseInt(summary.substring(colon + 1, slash).trim());
    }
}
