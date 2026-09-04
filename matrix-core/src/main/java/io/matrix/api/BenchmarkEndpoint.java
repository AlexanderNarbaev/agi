package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmark endpoint (Priority 6 - performance): measures chain
 * inference latency and BPE encoding latency. Returns p50/p95/p99
 * over N iterations.
 *
 * <p>Designed to give the user concrete numbers on the boolean
 * substrate's speed. The user wants performance data so they can
 * see what hardware / software / algorithm improvements buy.
 */
@Path("/v1/benchmark")
@Produces(MediaType.APPLICATION_JSON)
public class BenchmarkEndpoint {

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    BpeTokenizerProvider bpe;

    @POST
    public Map<String, Object> runBench(Map<String, Object> body) {
        int iterations = ((Number) body.getOrDefault("iterations", 1000)).intValue();
        int textLen = ((Number) body.getOrDefault("text_length", 100)).intValue();
        boolean doBpe = Boolean.TRUE.equals(body.getOrDefault("bpe", true));
        boolean doChain = Boolean.TRUE.equals(body.getOrDefault("chain", true));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("iterations", iterations);

        if (doBpe) {
            String sampleText = "x".repeat(Math.min(textLen, 896));
            List<Long> times = new ArrayList<>(iterations);
            for (int i = 0; i < iterations; i++) {
                long t = System.nanoTime();
                bpe.textToBits(sampleText, 896);
                times.add(System.nanoTime() - t);
            }
            out.put("bpe_encode_ns", percentileStats(times));
        }
        if (doChain) {
            boolean[] input = new boolean[896];
            for (int i = 0; i < 896; i++) input[i] = (i * 7) % 3 == 0;
            // warmup
            for (int i = 0; i < 50; i++) chainRunner.evaluate(input);
            List<Long> times = new ArrayList<>(iterations);
            for (int i = 0; i < iterations; i++) {
                long t = System.nanoTime();
                chainRunner.evaluate(input);
                times.add(System.nanoTime() - t);
            }
            out.put("chain_eval_ns", percentileStats(times));
        }
        return out;
    }

    @GET
    public Map<String, Object> quick() {
        return runBench(Map.of("iterations", 100, "text_length", 50, "bpe", true, "chain", true));
    }

    private static Map<String, Object> percentileStats(List<Long> ns) {
        long[] sorted = ns.stream().mapToLong(Long::longValue).sorted().toArray();
        int n = sorted.length;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("min", sorted[0]);
        stats.put("p50", sorted[n / 2]);
        stats.put("p95", sorted[(int) (n * 0.95)]);
        stats.put("p99", sorted[(int) (n * 0.99)]);
        stats.put("max", sorted[n - 1]);
        stats.put("mean", (double) sorted[n - 1] / 0 / n);  // not used, but keep field
        long sum = 0;
        for (long v : sorted) sum += v;
        stats.put("mean_ns", sum / n);
        stats.put("total_ops", n);
        return stats;
    }
}