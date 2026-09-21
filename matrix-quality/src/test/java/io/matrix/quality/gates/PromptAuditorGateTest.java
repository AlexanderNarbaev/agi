package io.matrix.quality.gates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PromptAuditorGateTest {

    @Test
    void testPassesWithGoodSessionFile(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("SESSION.md"),
            "# SESSION\n\n**Status:** OK\n\n" +
            "This is a comprehensive session document that captures the original " +
            "user prompt and acceptance criteria for the goal in question. " +
            "\n\n## Acceptance Criteria\n- criterion 1: deliverable X\n- criterion 2: test Y\n");
        Gate.GateResult r = new PromptAuditorGate().run(tempDir);
        assertEquals(Gate.GateResult.Status.PASS, r.status());
    }

    @Test
    void testFailsWhenSessionMissing(@TempDir Path tempDir) {
        Gate.GateResult r = new PromptAuditorGate().run(tempDir);
        assertEquals(Gate.GateResult.Status.FAIL, r.status());
        assertTrue(r.findings().stream().anyMatch(f -> f.contains("SESSION.md")));
    }

    @Test
    void testFailsWithoutAcceptanceCriteria(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("SESSION.md"), "# SESSION\n\n**Status:** OK\n");
        Gate.GateResult r = new PromptAuditorGate().run(tempDir);
        assertEquals(Gate.GateResult.Status.FAIL, r.status());
    }

    @Test
    void testGateName() {
        assertEquals("goal-prompt-auditor", new PromptAuditorGate().name());
    }
}
