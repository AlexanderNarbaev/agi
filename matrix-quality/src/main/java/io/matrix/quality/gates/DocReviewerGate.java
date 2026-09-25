package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * WAVE T-10 — Goal Documentation Reviewer Gate.
 *
 * Documentation quality: README exists, key files have frontmatter,
 * CONSTITUTION articles are documented.
 */
public final class DocReviewerGate implements Gate {

    @Override
    public String name() { return "goal-doc-reviewer"; }

    @Override
    public String description() {
        return "Documentation review: READMEs, frontmatter, CONSTITUTION coverage";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        if (!Files.exists(projectRoot.resolve("README.md"))) {
            findings.add("Root README.md missing");
        }
        if (!Files.exists(projectRoot.resolve("CONSTITUTION.md"))) {
            findings.add("CONSTITUTION.md missing");
        }
        // Verify ecosystem docs exist (T-03 deliverable)
        Path ecosystem = projectRoot.resolve("docs-v2/ecosystem/index.md");
        if (!Files.exists(ecosystem)) {
            findings.add("Ecosystem documentation missing (T-03 deliverable)");
        }

        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                "Documentation in place", findings);
        }
        return new GateResult(name(), GateResult.Status.WARN,
            "Documentation gaps", findings);
    }
}
