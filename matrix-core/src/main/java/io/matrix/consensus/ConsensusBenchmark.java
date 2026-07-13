package io.matrix.consensus;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;

/**
 * Benchmark for Byzantine consensus protocol performance.
 *
 * <p>Measures:
 * <ul>
 * <li>Consensus latency with varying numbers of faulty nodes</li>
 * <li>Fault tolerance threshold (max f before consensus fails)</li>
 * <li>Throughput (consensus rounds per second)</li>
 * <li>Comparison with baseline {@link ConsensusEngine}</li>
 * </ul>
 *
 * <p>Ref: arXiv:2410.16237 — IBGP performance analysis
 */
public final class ConsensusBenchmark {

    public record BenchmarkResult(
            String scenario,
            int totalNodes,
            int faultyNodes,
            int rounds,
            long totalLatencyMs,
            long avgLatencyMs,
            long minLatencyMs,
            long maxLatencyMs,
            int committed,
            int rejected,
            int recovery,
            double throughputRps
    ) {}

    /**
     * Runs a benchmark with the given number of total and faulty nodes.
     *
     * @param totalNodes total number of nodes
     * @param faultyNodes number of faulty nodes
     * @param rounds number of consensus rounds to run
     * @return benchmark result
     */
    public BenchmarkResult run(int totalNodes, int faultyNodes, int rounds) {
        if (faultyNodes >= totalNodes) {
            throw new IllegalArgumentException("faultyNodes must be < totalNodes");
        }

        ByzantineConsensus consensus = new ByzantineConsensus();
        for (int i = 0; i < totalNodes; i++) {
            ByzantineNode.State state = i < faultyNodes
                    ? ByzantineNode.State.FAULTY
                    : ByzantineNode.State.HONEST;
            consensus.addNode(new ByzantineNode("node-" + i, state));
        }

        List<Long> latencies = new ArrayList<>();
        int committed = 0;
        int rejected = 0;
        int recovery = 0;

        long benchStart = System.nanoTime();

        for (int r = 0; r < rounds; r++) {
            String leader = "node-" + (r % (totalNodes - faultyNodes));
            long start = System.nanoTime();

            ByzantineConsensus.ConsensusResult result = consensus.runConsensusWithRecovery(
                    leader, "proposal-" + r);

            long latency = (System.nanoTime() - start) / 1_000_000;
            latencies.add(latency);

            switch (result.state()) {
                case COMMITTED -> committed++;
                case REJECTED -> rejected++;
                default -> recovery++;
            }

            consensus.reset();
        }

        long totalBenchMs = (System.nanoTime() - benchStart) / 1_000_000;

        LongSummaryStatistics stats = latencies.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        double throughput = rounds > 0 && totalBenchMs > 0
                ? (double) rounds / totalBenchMs * 1000.0
                : 0.0;

        String scenario = String.format("n=%d,f=%d", totalNodes, faultyNodes);

        return new BenchmarkResult(scenario, totalNodes, faultyNodes, rounds,
                stats.getSum(), (long) stats.getAverage(),
                stats.getMin(), stats.getMax(),
                committed, rejected, recovery, throughput);
    }

    /**
     * Runs a fault tolerance sweep: tests consensus with increasing faulty nodes
     * from 0 up to the theoretical maximum (n/3).
     *
     * @param totalNodes total number of nodes
     * @param roundsPerConfig rounds per fault configuration
     * @return list of benchmark results for each fault level
     */
    public List<BenchmarkResult> faultToleranceSweep(int totalNodes, int roundsPerConfig) {
        List<BenchmarkResult> results = new ArrayList<>();
        int maxFaulty = (totalNodes - 1) / 3;

        for (int f = 0; f <= maxFaulty + 1; f++) {
            if (f >= totalNodes) break;
            results.add(run(totalNodes, f, roundsPerConfig));
        }
        return results;
    }

    /**
     * Runs a scalability benchmark: tests consensus with increasing network sizes.
     *
     * @param nodeCounts list of network sizes to test
     * @param roundsPerSize rounds per network size
     * @return list of benchmark results for each size
     */
    public List<BenchmarkResult> scalabilityBenchmark(List<Integer> nodeCounts,
                                                       int roundsPerSize) {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int n : nodeCounts) {
            int f = Math.max(0, (n - 1) / 3);
            results.add(run(n, f, roundsPerSize));
        }
        return results;
    }

    /**
     * Compares IBGP with baseline ConsensusEngine latency.
     *
     * @param totalNodes number of nodes for the test
     * @param rounds number of rounds
     * @return array of [ibgpAvgLatencyMs, baselineAvgLatencyMs]
     */
    public long[] compareWithBaseline(int totalNodes, int rounds) {
        int faulty = Math.max(0, (totalNodes - 1) / 3);
        BenchmarkResult ibgpResult = run(totalNodes, faulty, rounds);

        long baselineStart = System.nanoTime();
        ConsensusEngine baseline = new ConsensusEngine();
        for (int r = 0; r < rounds; r++) {
            Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_2,
                    "node-0", "ACTION", "proposal-" + r);
            baseline.propose(proposal);
            for (int v = 0; v < totalNodes; v++) {
                baseline.castVote(Vote.approve(proposal.id(), "voter-" + v, 1.0));
            }
            baseline.evaluate(proposal.id());
        }
        long baselineAvgMs = (System.nanoTime() - baselineStart) / 1_000_000 / rounds;

        return new long[]{ibgpResult.avgLatencyMs(), baselineAvgMs};
    }
}
