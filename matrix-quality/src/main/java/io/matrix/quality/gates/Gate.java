package io.matrix.quality.gates;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * WAVE T-10 — Review Gate.
 *
 * A gate is a PASS/FAIL automated review check. Goal Guard runs 14 of these
 * before allowing "Goal Completed" status. Each gate inspects a specific
 * aspect of the project and produces evidence (what was checked, what passed).
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. All checks are deterministic.</p>
 */
public interface Gate {

    /** Unique gate name (matches Goal Guard expected names). */
    String name();

    /** Human-readable description. */
    String description();

    /** Run the gate against a project root. Returns PASS or FAIL. */
    GateResult run(Path projectRoot);

    /** Result of running a gate. */
    record GateResult(
        String gateName,
        Status status,
        String summary,
        java.util.List<String> findings
    ) {
        public enum Status { PASS, FAIL, WARN }
        public GateResult {
            Objects.requireNonNull(gateName, "gateName");
            Objects.requireNonNull(status, "status");
            findings = findings == null ? java.util.List.of() : List.copyOf(findings);
        }
        public boolean isPass() { return status == Status.PASS || status == Status.WARN; }
    }
}
