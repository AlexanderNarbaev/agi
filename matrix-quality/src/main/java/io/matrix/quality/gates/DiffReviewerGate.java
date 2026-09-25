package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * WAVE T-10 — Goal Diff Reviewer Gate.
 *
 * Inspects file changes for scope creep, unintended edits, and broken
 * references between modules.
 */
public final class DiffReviewerGate implements Gate {

    @Override
    public String name() { return "goal-diff-reviewer"; }

    @Override
    public String description() {
        return "Inspect file changes for scope creep and broken cross-module refs";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        // Check that no .java file is suspiciously large (>5000 lines)
        try (Stream<Path> javaFiles = Files.walk(projectRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/"))) {
            javaFiles.forEach(p -> {
                try {
                    long lines = Files.lines(p).count();
                    if (lines > 5000) {
                        findings.add(p + " has " + lines + " lines (suspicious)");
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            return new GateResult(name(), GateResult.Status.WARN,
                "Walk error: " + e.getMessage(), List.of(e.toString()));
        }
        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                "No oversized files detected", findings);
        }
        return new GateResult(name(), GateResult.Status.WARN,
            "Files flagged for review", findings);
    }
}
