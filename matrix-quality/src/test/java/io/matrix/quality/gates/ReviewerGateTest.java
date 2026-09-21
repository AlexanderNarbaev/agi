package io.matrix.quality.gates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ReviewerGateTest {

    @Test
    void testPassesWhenAllModulesHaveTests(@TempDir Path tempDir) throws Exception {
        Path m1 = Files.createDirectories(tempDir.resolve("matrix-a"));
        Files.createDirectories(m1.resolve("src/main"));
        Files.createDirectories(m1.resolve("src/test"));
        Path m2 = Files.createDirectories(tempDir.resolve("matrix-b"));
        Files.createDirectories(m2.resolve("src/main"));
        Files.createDirectories(m2.resolve("src/test"));
        Gate.GateResult r = new ReviewerGate().run(tempDir);
        assertEquals(Gate.GateResult.Status.PASS, r.status());
    }

    @Test
    void testFailsWhenModuleMissingTests(@TempDir Path tempDir) throws Exception {
        Path m1 = Files.createDirectories(tempDir.resolve("matrix-a"));
        Files.createDirectories(m1.resolve("src/main"));
        // No src/test
        Gate.GateResult r = new ReviewerGate().run(tempDir);
        assertEquals(Gate.GateResult.Status.FAIL, r.status());
    }
}
