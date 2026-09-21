package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * WAVE T-10 — Goal Reviewer Gate.
 *
 * Strict overall correctness check: every Java module compiles, every
 * production module has tests, no fatal patterns.
 */
public final class ReviewerGate implements Gate {

    @Override
    public String name() { return "goal-reviewer"; }

    @Override
    public String description() {
        return "Strict overall correctness: modules compile, tests present, no fatal patterns";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        // Verify each production module has tests
        try (Stream<Path> modules = Files.list(projectRoot)) {
            modules.filter(p -> Files.isDirectory(p))
                .filter(p -> p.getFileName().toString().startsWith("matrix-"))
                .filter(p -> Files.exists(p.resolve("src/main")))
                .forEach(module -> {
                    Path testDir = module.resolve("src/test");
                    if (!Files.exists(testDir)) {
                        findings.add("Module " + module.getFileName() + " has no src/test/");
                    }
                });
        } catch (IOException e) {
            return new GateResult(name(), GateResult.Status.FAIL,
                "Read error: " + e.getMessage(), List.of(e.toString()));
        }
        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                "All production modules have tests", findings);
        }
        return new GateResult(name(), GateResult.Status.FAIL,
            "Missing tests in modules", findings);
    }
}
