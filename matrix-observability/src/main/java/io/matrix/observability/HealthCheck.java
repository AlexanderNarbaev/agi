package io.matrix.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * WAVE T-09 — Health Check Registry.
 *
 * Holds liveness/readiness probes for MATRIX services.
 * Each check returns UP/DOWN with optional details.
 *
 * <p>Standard checks (T-09.5):</p>
 * <ul>
 *   <li>api_gateway — port listening</li>
 *   <li>audit_chain_integrity — hash chain verify</li>
 *   <li>federation_peers — at least 1 node reachable</li>
 *   <li>credit_ledger_consistency — ledger sums match</li>
 *   <li>disk_space — under 80%</li>
 * </ul>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM.</p>
 */
public final class HealthCheck {

    public enum Status { UP, DOWN, DEGRADED }

    public record Result(Status status, String detail, Duration duration) {
        public boolean isHealthy() { return status == Status.UP || status == Status.DEGRADED; }
    }

    public record Report(
        Status overall,
        Map<String, Result> checks,
        Instant timestamp
    ) {}

    public interface Probe {
        Result run();
    }

    private final Map<String, Probe> probes = new ConcurrentHashMap<>();

    public void register(String name, Probe probe) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(probe, "probe");
        probes.put(name, probe);
    }

    public void registerSupplier(String name, Supplier<Boolean> check) {
        register(name, () -> {
            Instant start = Instant.now();
            try {
                boolean ok = check.get();
                Duration d = Duration.between(start, Instant.now());
                return new Result(ok ? Status.UP : Status.DOWN, "boolean check", d);
            } catch (Exception e) {
                Duration d = Duration.between(start, Instant.now());
                return new Result(Status.DOWN, e.getMessage(), d);
            }
        });
    }

    public Report runAll() {
        Map<String, Result> results = new LinkedHashMap<>();
        Status overall = Status.UP;
        for (var entry : probes.entrySet()) {
            Result r = entry.getValue().run();
            results.put(entry.getKey(), r);
            if (r.status() == Status.DOWN) overall = Status.DOWN;
            else if (r.status() == Status.DEGRADED && overall == Status.UP) overall = Status.DEGRADED;
        }
        return new Report(overall, results, Instant.now());
    }

    public Report reportUp() {
        return new Report(Status.UP, Map.of(), Instant.now());
    }

    public int probeCount() { return probes.size(); }
}
