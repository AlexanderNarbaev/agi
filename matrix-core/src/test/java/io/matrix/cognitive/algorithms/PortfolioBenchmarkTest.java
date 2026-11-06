package io.matrix.cognitive.algorithms;

import io.matrix.neuron.BloomFilter;
import io.matrix.neuron.Trie;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the portfolio's comparison benchmark.
 *
 * <p>This is a JUnit test rather than a JMH benchmark on purpose. JMH lives in a separate source
 * set that the module's coverage gate does not see, and a benchmark that nobody runs is a
 * benchmark that rots. Here the numbers are printed by the test, the assertions are the
 * contract, and the test fails when the contract is broken.</p>
 *
 * <p><b>What is compared.</b> Three pairs, each of which the portfolio claims something about:</p>
 * <ul>
 *   <li><b>A scan against a prebuilt index.</b> The trade-off the selector exists to express:
 *       the scan pays nothing up front and costs a little per query; the index pays once and
 *       costs almost nothing per query. The benchmark reports where the two cross over, so
 *       "build an index only when the query count justifies it" becomes a number rather than a
 *       rule of thumb.</li>
 *   <li><b>Dijkstra against Bellman-Ford.</b> The two exact methods on the same graph. They must
 *       agree on the answer and differ on the cost, and the selector must pick the cheaper one
 *       because their declared quality is the same.</li>
 *   <li><b>A bounded search against the exact search.</b> The anytime method must agree with the
 *       exact one when it finishes, and must refuse to present a truncated result as proven when
 *       it does not.</li>
 * </ul>
 *
 * <p><b>The seed.</b> The corpus and the graph are generated from a fixed seed, so the corpus is
 * the same corpus on every run and on every machine. The randomness here is in the FIXTURE, not
 * in the decision path: nothing in this package reads a random number while it chooses a
 * method.</p>
 */
class PortfolioBenchmarkTest {

    /** The seed of the fixture. CONVENTION: fixed so that the corpus is the same corpus. */
    private static final long SEED = 42L;

    /** How many times each measurement is repeated. */
    private static final int REPETITIONS = 30;

    /** The median quantile. */
    private static final double P50 = 0.50;

    /** The ninety-fifth percentile quantile. */
    private static final double P95 = 0.95;

    /** How many entries the membership corpus holds. */
    private static final int CORPUS_SIZE = 256;

    /** How many queries are asked of the membership corpus. */
    private static final int QUERY_COUNT = 64;

    /** The bits of the Bloom filter. */
    private static final int BLOOM_BITS = 4096;

    /** The hash functions of the Bloom filter. */
    private static final int BLOOM_HASHES = 4;

    /** How many vertices the benchmark graph holds. */
    private static final int GRAPH_VERTICES = 120;

    /** How many edges the benchmark graph holds. */
    private static final int GRAPH_EDGES = 360;

    /** The largest weight an edge may carry. */
    private static final int MAX_EDGE_WEIGHT = 100;

    /** The words the corpus is built from. */
    private static final String[] WORDS = {
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
            "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi"};

    /** The result of one timed measurement. */
    private record Timing(String what, long p50Nanos, long p95Nanos, double meanNanos) {
    }

    /** Measures a body {@value #REPETITIONS} times and reports its percentiles. */
    private static Timing measure(String what, Runnable body) {
        long[] samples = new long[REPETITIONS];
        for (int i = 0; i < REPETITIONS; i++) {
            long startedAt = System.nanoTime();
            body.run();
            samples[i] = System.nanoTime() - startedAt;
        }
        Arrays.sort(samples);
        long total = 0;
        for (long sample : samples) {
            total += sample;
        }
        return new Timing(what, percentile(samples, P50), percentile(samples, P95),
                (double) total / (double) REPETITIONS);
    }

    private static long percentile(long[] sortedAscending, double quantile) {
        int index = (int) Math.ceil(quantile * sortedAscending.length) - 1;
        return sortedAscending[Math.max(0, Math.min(index, sortedAscending.length - 1))];
    }

    /** The membership corpus, built from the fixed seed. */
    private static List<String> corpus() {
        Random random = new Random(SEED);
        List<String> corpus = new ArrayList<>(CORPUS_SIZE);
        while (corpus.size() < CORPUS_SIZE) {
            String candidate = WORDS[random.nextInt(WORDS.length)] + "-"
                    + random.nextInt(CORPUS_SIZE) + "-" + random.nextInt(CORPUS_SIZE);
            if (!corpus.contains(candidate)) {
                corpus.add(candidate);
            }
        }
        return corpus;
    }

    /** The queries: every other corpus entry, plus as many absent ones. */
    private static List<String> queries(List<String> corpus) {
        List<String> queries = new ArrayList<>(QUERY_COUNT);
        for (int i = 0; i < QUERY_COUNT / 2; i++) {
            queries.add(corpus.get(i * 2));
            queries.add("absent-" + i * 2 + "-" + SEED);
        }
        return queries;
    }

    /** The benchmark graph: a seeded, connected, non-negative weighted digraph. */
    private static ShortestPathProblem graph() {
        Random random = new Random(SEED);
        List<ShortestPathProblem.Edge> edges = new ArrayList<>(GRAPH_EDGES);
        for (int i = 0; i + 1 < GRAPH_VERTICES; i++) {
            edges.add(new ShortestPathProblem.Edge(i, i + 1, 1.0));
        }
        while (edges.size() < GRAPH_EDGES) {
            int from = random.nextInt(GRAPH_VERTICES);
            int to = random.nextInt(GRAPH_VERTICES);
            if (from == to) {
                continue;
            }
            edges.add(new ShortestPathProblem.Edge(from, to, random.nextInt(MAX_EDGE_WEIGHT) + 1));
        }
        return new ShortestPathProblem(GRAPH_VERTICES, edges, 0);
    }

    private static Trie trieOver(List<String> corpus) {
        Trie index = new Trie();
        for (String entry : corpus) {
            index.insert(entry);
        }
        return index;
    }

    private static BloomFilter filterOver(List<String> corpus) {
        BloomFilter filter = new BloomFilter(BLOOM_BITS, BLOOM_HASHES);
        for (String entry : corpus) {
            filter.add(MembershipProblem.keyOf(entry));
        }
        return filter;
    }

    @Test
    void aScanAndAPrebuiltIndexAreComparedOnTheSameCorpus() {
        List<String> corpus = corpus();
        List<String> queries = queries(corpus);
        LinearScanMembershipMethod scan = new LinearScanMembershipMethod();
        TrieMembershipMethod trie = new TrieMembershipMethod();
        BloomFilterMembershipMethod bloom = new BloomFilterMembershipMethod();

        // The index is built ONCE, outside the measured lookups. The build is a one-off the
        // caller pays for; the lookup is the recurring cost the selector compares.
        Trie builtTrie = trieOver(corpus);
        BloomFilter builtFilter = filterOver(corpus);

        Timing scanTiming = measure("linear scan of " + QUERY_COUNT + " queries", () ->
                scan.execute(new MembershipProblem(corpus, queries, null), ComputeBudget.unbounded()));
        Timing trieBuildTiming = measure("building the trie", () -> trieOver(corpus));
        Timing trieLookupTiming = measure("trie lookup of " + QUERY_COUNT + " queries", () ->
                trie.execute(new MembershipProblem(corpus, queries, builtTrie), ComputeBudget.unbounded()));
        Timing bloomBuildTiming = measure("building the Bloom filter", () -> filterOver(corpus));
        Timing bloomLookupTiming = measure("Bloom lookup of " + QUERY_COUNT + " queries", () ->
                bloom.execute(new MembershipProblem(corpus, queries, builtFilter), ComputeBudget.unbounded()));

        // The break-even point: how many queries make the index cheaper than the scan.
        double scanPerQuery = scanTiming.meanNanos() / QUERY_COUNT;
        double triePerQuery = trieLookupTiming.meanNanos() / QUERY_COUNT;
        double savingPerQuery = scanPerQuery - triePerQuery;
        long breakEvenQueries = savingPerQuery <= 0
                ? Long.MAX_VALUE
                : (long) Math.ceil(trieBuildTiming.meanNanos() / savingPerQuery);
        double scanEndToEnd = scanTiming.meanNanos();
        double trieEndToEnd = trieBuildTiming.meanNanos() + trieLookupTiming.meanNanos();

        System.out.println("--- membership: a scan against a prebuilt index ---");
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                scanTiming.what(), scanTiming.p50Nanos(), scanTiming.p95Nanos(), scanTiming.meanNanos());
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                trieBuildTiming.what(), trieBuildTiming.p50Nanos(), trieBuildTiming.p95Nanos(),
                trieBuildTiming.meanNanos());
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                trieLookupTiming.what(), trieLookupTiming.p50Nanos(), trieLookupTiming.p95Nanos(),
                trieLookupTiming.meanNanos());
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                bloomBuildTiming.what(), bloomBuildTiming.p50Nanos(), bloomBuildTiming.p95Nanos(),
                bloomBuildTiming.meanNanos());
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                bloomLookupTiming.what(), bloomLookupTiming.p50Nanos(), bloomLookupTiming.p95Nanos(),
                bloomLookupTiming.meanNanos());
        System.out.printf("the scan costs %.1f ns/query; the trie costs %.1f ns/query after a build "
                        + "of %.1f ns, so it repays the build after %d queries (this corpus asks %d)%n",
                scanPerQuery, triePerQuery, trieBuildTiming.meanNanos(), breakEvenQueries, QUERY_COUNT);
        System.out.printf("end to end over %d queries: the scan costs %.1f ns and the trie %.1f ns%n",
                QUERY_COUNT, scanEndToEnd, trieEndToEnd);

        // The selector sees the MEASURED per-call costs, not the declared ones.
        CostLedger ledger = new CostLedger();
        ledger.record(scan.capability().id(), (long) scanTiming.meanNanos(), true);
        ledger.record(trie.capability().id(), (long) trieLookupTiming.meanNanos(), true);
        ledger.record(bloom.capability().id(), (long) bloomLookupTiming.meanNanos(), true);

        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(scan).register(trie).register(bloom), ledger);

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.MEMBERSHIP_LOOKUP,
                new MembershipProblem(corpus, queries, builtTrie),
                Set.of(Preconditions.INDEX_PREBUILT), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);
        System.out.println("the selector chose " + decision.selectedAlgorithmId() + " because "
                + decision.reason());

        // The scan and the trie declare the SAME accuracy, so the cheaper one must win.
        String cheaper = trieLookupTiming.meanNanos() <= scanTiming.meanNanos()
                ? TrieMembershipMethod.ID : LinearScanMembershipMethod.ID;
        assertThat(decision.selectedAlgorithmId()).isEqualTo(cheaper);

        // And the chosen method must answer exactly as the reference does.
        MembershipProblem.MembershipOutcome byScan = scan.execute(
                new MembershipProblem(corpus, queries, null), ComputeBudget.unbounded());
        MembershipProblem.MembershipOutcome byTrie = trie.execute(
                new MembershipProblem(corpus, queries, builtTrie), ComputeBudget.unbounded());
        assertThat(byTrie.present()).isEqualTo(byScan.present());

        // The break-even point is a real number: the index is only worth building once the
        // number of queries clears it.
        assertThat(breakEvenQueries).isPositive();
        assertThat(triePerQuery).isLessThan(scanPerQuery);
    }

    @Test
    void theTwoExactShortestPathMethodsAreComparedOnTheSameGraph() {
        ShortestPathProblem problem = graph();
        DijkstraShortestPathMethod dijkstra = new DijkstraShortestPathMethod();
        BellmanFordShortestPathMethod bellmanFord = new BellmanFordShortestPathMethod();

        Timing dijkstraTiming = measure("Dijkstra on " + GRAPH_VERTICES + " vertices", () ->
                dijkstra.execute(problem, ComputeBudget.unbounded()));
        Timing bellmanFordTiming = measure("Bellman-Ford on " + GRAPH_VERTICES + " vertices", () ->
                bellmanFord.execute(problem, ComputeBudget.unbounded()));

        System.out.println("--- shortest path: the two exact methods ---");
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                dijkstraTiming.what(), dijkstraTiming.p50Nanos(), dijkstraTiming.p95Nanos(),
                dijkstraTiming.meanNanos());
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                bellmanFordTiming.what(), bellmanFordTiming.p50Nanos(), bellmanFordTiming.p95Nanos(),
                bellmanFordTiming.meanNanos());

        // They must agree on every distance: the quality is the same, so the cost decides.
        ShortestPathProblem.Outcome byDijkstra = dijkstra.execute(problem, ComputeBudget.unbounded());
        ShortestPathProblem.Outcome byBellmanFord = bellmanFord.execute(problem, ComputeBudget.unbounded());
        for (int vertex = 0; vertex < GRAPH_VERTICES; vertex++) {
            assertThat(byDijkstra.distanceTo(vertex)).isEqualTo(byBellmanFord.distanceTo(vertex));
        }

        CostLedger ledger = new CostLedger();
        ledger.record(dijkstra.capability().id(), (long) dijkstraTiming.meanNanos(), true);
        ledger.record(bellmanFord.capability().id(), (long) bellmanFordTiming.meanNanos(), true);

        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(dijkstra).register(bellmanFord), ledger);
        AlgorithmDecision decision = selector.decide(new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH, problem,
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded()));

        System.out.println("the selector chose " + decision.selectedAlgorithmId() + " because "
                + decision.reason());

        String cheaper = dijkstraTiming.meanNanos() <= bellmanFordTiming.meanNanos()
                ? DijkstraShortestPathMethod.ID : BellmanFordShortestPathMethod.ID;
        assertThat(decision.selectedAlgorithmId()).isEqualTo(cheaper);
    }

    @Test
    void aBoundedSearchIsComparedWithTheExactSearchItStandsInFor() {
        ShortestPathProblem problem = graph();
        DijkstraShortestPathMethod dijkstra = new DijkstraShortestPathMethod();
        BoundedUniformCostSearchMethod bounded = new BoundedUniformCostSearchMethod();

        // The anytime scenario: the caller grants a node budget and is willing to take a
        // bounded answer. This is the call the selector is choosing between.
        ComputeBudget tight = new ComputeBudget(1_000L, 1_000L, 1_000L, 8);

        Timing exactTiming = measure("the exact search", () ->
                dijkstra.execute(problem, ComputeBudget.unbounded()));
        Timing boundedCompletionTiming = measure("the bounded search inside a generous budget", () ->
                bounded.execute(problem, ComputeBudget.unbounded()));
        Timing boundedAnyTimeTiming = measure("the bounded search under the node budget", () -> {
            try {
                bounded.execute(problem, tight);
            } catch (ResourceLimitException expected) {
                // The bounded answer is the refusal itself: a labelled partial result.
            }
        });

        System.out.println("--- shortest path: the bounded search against the exact one ---");
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                exactTiming.what(), exactTiming.p50Nanos(), exactTiming.p95Nanos(), exactTiming.meanNanos());
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                boundedCompletionTiming.what(), boundedCompletionTiming.p50Nanos(),
                boundedCompletionTiming.p95Nanos(), boundedCompletionTiming.meanNanos());
        System.out.printf("%-42s p50=%8d ns  p95=%8d ns  mean=%10.1f ns%n",
                boundedAnyTimeTiming.what(), boundedAnyTimeTiming.p50Nanos(),
                boundedAnyTimeTiming.p95Nanos(), boundedAnyTimeTiming.meanNanos());

        // Inside its budget the bounded search must agree with the exact one, exactly.
        ShortestPathProblem.Outcome exact = dijkstra.execute(problem, ComputeBudget.unbounded());
        ShortestPathProblem.Outcome boundedOutcome = bounded.execute(problem, ComputeBudget.unbounded());
        assertThat(boundedOutcome.complete()).isTrue();
        for (int vertex = 0; vertex < GRAPH_VERTICES; vertex++) {
            assertThat(boundedOutcome.distanceTo(vertex)).isEqualTo(exact.distanceTo(vertex));
        }

        // Outside its budget it must refuse rather than present a truncated answer as proven.
        assertThatThrownBy(() -> bounded.execute(problem, tight))
                .isInstanceOf(ResourceLimitException.class)
                .satisfies(thrown -> {
                    ShortestPathProblem.Outcome partial =
                            (ShortestPathProblem.Outcome) ((ResourceLimitException) thrown).partialResult();
                    assertThat(partial.complete()).isFalse();
                });

        // The selector sees the cost of the call the caller is actually making, which for the
        // bounded method is the bounded one: it returns sooner because it stops sooner.
        CostLedger ledger = new CostLedger();
        ledger.record(dijkstra.capability().id(), (long) exactTiming.meanNanos(), true);
        ledger.record(bounded.capability().id(), (long) boundedAnyTimeTiming.meanNanos(), true);
        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(dijkstra).register(bounded), ledger);

        // Without the fact the bounded method is not admitted, and the exact one answers.
        AlgorithmDecision withoutTheFact = selector.decide(new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH, problem,
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS), ComputeBudget.unbounded()));
        assertThat(withoutTheFact.selectedAlgorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);

        // With the fact the bounded method is admitted, and being the cheaper call it is chosen.
        AlgorithmDecision withTheFact = selector.decide(new ProblemRequest(
                AlgorithmFamilies.SHORTEST_PATH, problem,
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS, Preconditions.BOUNDED_RESULT_ACCEPTED),
                ComputeBudget.unbounded()));
        System.out.println("with the bounded-result fact the selector chose "
                + withTheFact.selectedAlgorithmId() + " because " + withTheFact.reason());
        assertThat(withTheFact.selectedAlgorithmId()).isEqualTo(BoundedUniformCostSearchMethod.ID);
        assertThat(withTheFact.fallbackAlgorithmId()).isEqualTo(DijkstraShortestPathMethod.ID);
    }
}
