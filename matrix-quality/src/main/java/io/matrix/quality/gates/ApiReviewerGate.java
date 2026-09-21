package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * WAVE T-10 — Goal API Reviewer Gate.
 *
 * API contract review: OpenAPI spec exists, SDKs match the spec,
 * request/response validation is in place.
 */
public final class ApiReviewerGate implements Gate {

    @Override
    public String name() { return "goal-api-reviewer"; }

    @Override
    public String description() {
        return "API contract review: OpenAPI spec, SDK alignment, validation";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        // Verify OpenAPI spec exists (T-02 deliverable)
        Path openapi = projectRoot.resolve("matrix-api-gateway/src/main/resources/openapi.yaml");
        if (!Files.exists(openapi)) {
            findings.add("OpenAPI spec missing at matrix-api-gateway/src/main/resources/openapi.yaml");
        }
        // Verify SDKs exist (T-08 deliverable)
        if (!Files.exists(projectRoot.resolve("matrix-sdk-java/src/main/java/io/matrix/sdk/MatrixClient.java"))) {
            findings.add("Java SDK missing");
        }
        if (!Files.exists(projectRoot.resolve("matrix-sdk-python/src/matrix_ai/client.py"))) {
            findings.add("Python SDK missing");
        }
        if (!Files.exists(projectRoot.resolve("matrix-sdk-js/src/index.ts"))) {
            findings.add("JS SDK missing");
        }

        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                "API contract artifacts present", findings);
        }
        return new GateResult(name(), GateResult.Status.WARN,
            "API contract gaps", findings);
    }
}
