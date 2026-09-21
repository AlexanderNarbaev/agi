package io.matrix.quality.gates;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FinalAuditorGateTest {

    @Test
    void testPassesWhenAllPreviousPassed() {
        List<Gate.GateResult> results = List.of(
            new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of()),
            new Gate.GateResult("g2", Gate.GateResult.Status.WARN, "warn", List.of())
        );
        Gate.GateResult r = new FinalAuditorGate(results).run(Path.of("."));
        assertEquals(Gate.GateResult.Status.PASS, r.status());
    }

    @Test
    void testFailsWhenAnyPreviousFailed() {
        List<Gate.GateResult> results = List.of(
            new Gate.GateResult("g1", Gate.GateResult.Status.PASS, "ok", List.of()),
            new Gate.GateResult("g2", Gate.GateResult.Status.FAIL, "broken", List.of())
        );
        Gate.GateResult r = new FinalAuditorGate(results).run(Path.of("."));
        assertEquals(Gate.GateResult.Status.FAIL, r.status());
    }

    @Test
    void testFailsWithEmptyResults() {
        Gate.GateResult r = new FinalAuditorGate(List.of()).run(Path.of("."));
        assertEquals(Gate.GateResult.Status.FAIL, r.status());
    }
}
