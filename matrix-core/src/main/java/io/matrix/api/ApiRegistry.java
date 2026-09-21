package io.matrix.api;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 179 — ApiRegistry (MATRIX REST surface catalog).
 *
 * <p>Programmatic catalog of MATRIX REST endpoints. Phase δ.4:
 * documentation as code for the API surface.
 */
public final class ApiRegistry {

    public record Endpoint(String path, String method, String purpose,
                           boolean usesLlm) {}

    public static List<Endpoint> endpoints() {
        List<Endpoint> eps = new ArrayList<>();
        // Deterministic, brain-only endpoints
        eps.add(new Endpoint("/v1/brain/cycle", "POST",
                "Run one cognitive cycle", false));
        eps.add(new Endpoint("/v1/brain/architecture", "GET",
                "BrainLoop architecture summary", false));
        eps.add(new Endpoint("/v1/memory/store", "POST",
                "Store an M2 entry", false));
        eps.add(new Endpoint("/v1/memory/load", "POST",
                "Reload M2 from disk", false));
        eps.add(new Endpoint("/v1/digest/compute", "POST",
                "Compute federation digest", false));
        eps.add(new Endpoint("/v1/digest/verify", "POST",
                "Verify a federation digest", false));
        eps.add(new Endpoint("/v1/audit/decision-path", "GET",
                "Run decision-path audit", false));
        eps.add(new Endpoint("/v1/audit/trace", "GET",
                "Read x-matrix-trace entries", false));
        // Optional Qwen-LLM endpoint (OFFLINE per CONSTITUTION I;
        // available only via opt-in flag)
        eps.add(new Endpoint("/v1/distill/run", "POST",
                "Offline distillation from Qwen", true));
        return List.copyOf(eps);
    }

    public static int endpointCount() {
        return endpoints().size();
    }

    public static long llmCount() {
        return endpoints().stream().filter(Endpoint::usesLlm).count();
    }

    public static long brainCount() {
        return endpoints().stream().filter(e -> !e.usesLlm()).count();
    }
}
