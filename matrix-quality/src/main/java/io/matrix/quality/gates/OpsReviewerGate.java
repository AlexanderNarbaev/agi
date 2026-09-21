package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * WAVE T-10 — Goal Ops Reviewer Gate.
 *
 * Operational readiness: deploy scripts exist, observability is configured,
 * health checks are present.
 */
public final class OpsReviewerGate implements Gate {

    @Override
    public String name() { return "goal-ops-reviewer"; }

    @Override
    public String description() {
        return "Operational readiness: deploy, observability, health checks";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        // Check for deploy artifacts
        if (!Files.exists(projectRoot.resolve("deploy/docker-compose.yml"))) {
            findings.add("deploy/docker-compose.yml missing");
        }
        if (!Files.exists(projectRoot.resolve(".github/workflows"))) {
            findings.add(".github/workflows/ missing");
        }
        if (!Files.exists(projectRoot.resolve("deploy/prometheus/matrix-alerts.yml"))) {
            findings.add("Prometheus alerts not configured");
        }

        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                "Operations artifacts present", findings);
        }
        return new GateResult(name(), GateResult.Status.WARN,
            "Missing operational artifacts", findings);
    }
}
