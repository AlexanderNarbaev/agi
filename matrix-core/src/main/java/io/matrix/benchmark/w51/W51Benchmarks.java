package io.matrix.benchmark.w51;

import io.matrix.cognitive.algorithms.AlgorithmCapability;
import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.ProblemRequest;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.contract.Belief;
import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeIdempotency;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.BridgeReceiver;
import io.matrix.contract.bridge.BridgeSink;
import io.matrix.contract.bridge.HmacBridgeSigner;
import io.matrix.contract.bridge.InMemoryBridgeIdempotency;
import io.matrix.learning.env.EpisodeReport;
import io.matrix.learning.env.MicroWorldEpisodeRunner;
import io.matrix.learning.env.ProbePolicy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * W51 §10 — the three hot paths this wave integrated, measured.
 *
 * <p><b>What is measured, and what is not.</b> Three paths: the choice of a method
 * ({@code AlgorithmSelector.decide}), the run of an episode through the micro-world
 * ({@code MicroWorldEpisodeRunner.run}), and the crossing of the process boundary by the
 * bridge ({@code BridgeReceiver.receive}). These are the paths the wave integrated: each of
 * them is on the way from an input to an effect, and each of them sits between two others
 * that were built earlier. A path that is measured is a path whose cost is known; a path
 * whose cost is unknown is a path whose cost is guessed.</p>
 *
 * <p><b>Why the real classes are measured.</b> Every scenario drives the repository's own
 * objects — the registry, the ledger, the selector, the runner, the receiver — with inputs
 * this class builds. Nothing is re-implemented here: a copy of a hot path measures the copy,
 * and the copy is not what runs.</p>
 *
 * <p><b>Why nothing is random.</b> Every scenario has fixed inputs and a fixed seed. The
 * only counter that moves is the request identifier of the bridge scenario, and it moves
 * because the bridge is idempotent per request id: measuring the same identifier again
 * would measure the idempotency fast path rather than the decision path. The counter is
 * therefore part of the scenario's setup and not of its randomness — the sequence of
 * identifiers is the same on every run.</p>
 *
 * <p><b>Why the bridge scenario pre-builds its messages.</b> The measured action is
 * {@code receive(...)} and nothing else. Building an envelope inside the measured region
 * would measure the building of the envelope together with the receiving of it, and the
 * first is not what the wave integrated at that boundary.</p>
 */
public final class W51Benchmarks {

    /** The problem family the benchmark portfolio serves. */
    public static final String FAMILY = "w51-benchmark-family";

    /** The precondition fact the benchmark request establishes. */
    public static final String PRECONDITION = "W51_BENCHMARK_INPUT_READY";

    /** The seed every scenario uses. It is a parameter of the measurement, not a lottery. */
    public static final long SEED = 20_261_108L;

    /** The identity of the benchmark episode. */
    public static final String EPISODE_ID = "w51-benchmark-episode";

    /** The instant the benchmark episode reports. A constant, so that the report is stable. */
    public static final String EPISODE_TIMESTAMP = "2026-11-08T00:00:00Z";

    /** The payload the benchmark portfolio is asked about. */
    public static final String REQUEST_PAYLOAD = "w51-benchmark-request";

    /** The accuracy the first method of the portfolio declares. */
    public static final double FIRST_ACCURACY = 0.91;

    /** The accuracy step between two consecutive methods of the portfolio. */
    public static final double ACCURACY_STEP = 0.02;

    /** The denominator the declared accuracy is measured over. */
    public static final int ACCURACY_DENOMINATOR = 128;

    /** The nanoseconds the ledger records for every benchmark method. */
    public static final long MEASURED_COST_NANOS = 4_096L;

    /** The number of calls the ledger records for every benchmark method. */
    public static final int MEASURED_CALLS = 64;

    /** The size of the smallest portfolio that is measured. */
    public static final int SMALLEST_PORTFOLIO = 1;

    /** The size of the middle portfolio that is measured. */
    public static final int MIDDLE_PORTFOLIO = 2;

    /** The size of the largest portfolio that is measured. */
    public static final int LARGEST_PORTFOLIO = 5;

    /** The secret the bridge scenario signs with. */
    public static final byte[] BRIDGE_SECRET =
            "w51-benchmark-bridge-secret".getBytes(StandardCharsets.UTF_8);

    /** The identity of the sender in the bridge scenario. */
    public static final String BRIDGE_SENDER_ID = "w51-benchmark-chain-b";

    /** The instant the bridge scenario delivers at. */
    public static final long BRIDGE_NOW_MILLIS = 1_763_049_600_000L;

    /** The number of messages the bridge scenario pre-builds. */
    public static final int BRIDGE_MESSAGE_COUNT = MicroBench.DEFAULT_MEASURED_ROUNDS;

    private W51Benchmarks() {
        throw new AssertionError("no instances");
    }

    /**
     * One episode, measured.
     *
     * @param bench              the timing of the episodes
     * @param steps              the number of steps one episode took
     * @param allocatedBytesPerStep the bytes one step allocated
     */
    public record EpisodeMeasurement(BenchResult bench, int steps, double allocatedBytesPerStep) {
    }

    /**
     * The choice of a method, measured over a portfolio of the given size.
     *
     * @param portfolioSize how many methods the portfolio holds
     * @param warmupRounds  how many calls are run and discarded
     * @param measuredRounds how many calls are measured
     * @return the result
     */
    public static BenchResult methodSelection(int portfolioSize, int warmupRounds,
                                               int measuredRounds) {
        AlgorithmSelector selector = selector(portfolioSize);
        ProblemRequest request = request();
        long[] sink = {0L};
        return MicroBench.measure("method-selection/portfolio-" + portfolioSize, warmupRounds,
                measuredRounds, () -> {
                    AlgorithmDecision decision = selector.decide(request);
                    sink[0] += decision.candidates().size();
                });
    }

    /**
     * The cost of one ledger lookup, measured on its own.
     *
     * <p>This is the part of the decision path that the selector calls once per candidate:
     * the measured cost of a method, read out of the ledger. It is measured separately
     * because it is the only part of the decision path that touches shared state, and a
     * shared-state lookup is not free the way a field read is.</p>
     *
     * @param warmupRounds  how many calls are run and discarded
     * @param measuredRounds how many calls are measured
     * @return the result
     */
    public static BenchResult costLedgerLookup(int warmupRounds, int measuredRounds) {
        CostLedger ledger = ledger();
        String methodId = methodId(0);
        long[] sink = {0L};
        return MicroBench.measure("cost-ledger/measuredNanos", warmupRounds, measuredRounds,
                () -> sink[0] += ledger.measuredNanos(methodId));
    }

    /**
     * The run of an episode, measured.
     *
     * @param warmupRounds  how many calls are run and discarded
     * @param measuredRounds how many calls are measured
     * @return the result
     */
    public static EpisodeMeasurement episode(int warmupRounds, int measuredRounds) {
        MicroWorldEpisodeRunner runner = episodeRunner();
        long[] sink = {0L};
        BenchResult bench = MicroBench.measure("episode/micro-world", warmupRounds, measuredRounds,
                () -> {
                    EpisodeReport report = runner.run();
                    sink[0] += report.steps();
                });
        EpisodeReport probe = runner.run();
        int steps = probe.steps();
        double perStep = bench.allocationMeasured() && steps > 0
                ? (double) bench.allocatedBytesPerCall() / steps : 0.0;
        return new EpisodeMeasurement(bench, steps, perStep);
    }

    /**
     * The crossing of the bridge, measured.
     *
     * @param warmupRounds  how many calls are run and discarded
     * @param measuredRounds how many calls are measured
     * @return the result
     */
    public static BenchResult bridge(int warmupRounds, int measuredRounds) {
        int messageCount = Math.max(measuredRounds + warmupRounds, 1);
        List<BridgeEnvelope> messages = bridgeMessages(messageCount);
        BridgeReceiver receiver = bridgeReceiver();
        int[] counter = {0};
        long[] sink = {0L};
        return MicroBench.measure("bridge/receive", warmupRounds, measuredRounds, () -> {
            BridgeEnvelope message = messages.get(counter[0] % messages.size());
            counter[0]++;
            BridgeDecision decision = receiver.receive(message, BRIDGE_NOW_MILLIS);
            sink[0] += decision.accepted() ? 1 : 0;
        });
    }

    /**
     * A selector over a portfolio of the given size.
     *
     * @param portfolioSize how many methods the portfolio holds
     * @return the selector
     */
    public static AlgorithmSelector selector(int portfolioSize) {
        AlgorithmRegistry registry = new AlgorithmRegistry();
        for (int i = 0; i < portfolioSize; i++) {
            registry.register(new BenchMethod(capability(i)));
        }
        return new AlgorithmSelector(registry, ledger());
    }

    /**
     * The request the portfolio is asked about.
     *
     * @return the request
     */
    public static ProblemRequest request() {
        return new ProblemRequest(FAMILY, REQUEST_PAYLOAD, Set.of(PRECONDITION),
                ComputeBudget.unbounded());
    }

    /**
     * The ledger the benchmark measures against.
     *
     * @return the ledger
     */
    public static CostLedger ledger() {
        CostLedger ledger = new CostLedger();
        for (int i = 0; i < LARGEST_PORTFOLIO; i++) {
            for (int call = 0; call < MEASURED_CALLS; call++) {
                ledger.record(methodId(i), MEASURED_COST_NANOS, true);
            }
        }
        return ledger;
    }

    /**
     * The runner of the benchmark episode.
     *
     * @return the runner
     */
    public static MicroWorldEpisodeRunner episodeRunner() {
        return new MicroWorldEpisodeRunner(new LampEnvironment(), EPISODE_ID, SEED,
                MicroWorldEpisodeRunner.DEFAULT_STEP_LIMIT, ProbePolicy.GREEDY,
                () -> EPISODE_TIMESTAMP);
    }

    /**
     * The receiver of the bridge scenario.
     *
     * @return the receiver
     */
    public static BridgeReceiver bridgeReceiver() {
        return new BridgeReceiver(new HmacBridgeSigner(BRIDGE_SECRET),
                Set.of(BridgeCapability.OBSERVATION), io.matrix.contract.bridge.BridgeBudget.standard(),
                new InMemoryBridgeIdempotency(), new CountingSink());
    }

    /**
     * The signed messages of the bridge scenario.
     *
     * <p>Each carries its own request id, because the bridge is idempotent per request id
     * and a repeated identifier would be answered from the registry rather than decided.</p>
     *
     * @param count how many messages to build
     * @return the messages
     */
    public static List<BridgeEnvelope> bridgeMessages(int count) {
        BridgePayloadCodec codec = new BridgePayloadCodec();
        Provenance provenance = Provenance.of(BRIDGE_SENDER_ID, EPISODE_TIMESTAMP);
        Observation observation = Observation.ofText("w51-observation",
                "the lamp is lit", EPISODE_TIMESTAMP, provenance);
        BridgePayload payload = BridgePayload.ofObservation(observation, provenance);
        String canonicalPayload = codec.encode(payload);
        List<BridgeEnvelope> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BridgeEnvelope unsigned = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                    "w51-bridge-request-" + i, EPISODE_TIMESTAMP, BRIDGE_SENDER_ID,
                    BridgeCapability.OBSERVATION, canonicalPayload, null);
            messages.add(unsigned.withSignature(unsigned.sign(BRIDGE_SECRET)));
        }
        return messages;
    }

    /**
     * The identifier of the n-th method of the portfolio.
     *
     * @param index the index of the method
     * @return the identifier
     */
    public static String methodId(int index) {
        return "w51-method-" + index;
    }

    /**
     * The capability the n-th method of the portfolio declares.
     *
     * @param index the index of the method
     * @return the capability
     */
    public static AlgorithmCapability capability(int index) {
        return new AlgorithmCapability(
                methodId(index),
                "1.0",
                Set.of(FAMILY),
                Set.of(PRECONDITION),
                Set.of("text"),
                Set.of("text"),
                new AlgorithmCapability.ComplexityProfile("O(n)", "O(1)"),
                new AlgorithmCapability.ResourceProfile("bounded", true, false, "none"),
                new AlgorithmCapability.AccuracyProfile("exact-match rate",
                        FIRST_ACCURACY - ACCURACY_STEP * index, ACCURACY_DENOMINATOR,
                        "w51-benchmark", EPISODE_TIMESTAMP),
                new AlgorithmCapability.DeterminismProfile(true, "fixed"),
                new AlgorithmCapability.ReliabilityProfile("none", false, "none"),
                new AlgorithmCapability.VerificationStrategy("none", "none"),
                null);
    }

    /**
     * A method that does nothing but declare itself.
     *
     * <p>The decision path never executes the method — the selector's own record says "the
     * decision was made without executing the method" — so the method exists to be
     * <i>considered</i>. Its body is therefore the cheapest thing that can be a method, and
     * the measurement is of the choice, not of the work.</p>
     *
     * @param <I> the input type
     * @param <O> the output type
     */
    private static final class BenchMethod implements AlgorithmMethod<String, String> {

        private final AlgorithmCapability capability;

        private BenchMethod(AlgorithmCapability capability) {
            this.capability = capability;
        }

        @Override
        public AlgorithmCapability capability() {
            return capability;
        }

        @Override
        public boolean preconditionsHold(String input) {
            return true;
        }

        @Override
        public String execute(String input, ComputeBudget budget) {
            return input;
        }
    }

    /**
     * The sink of the bridge scenario: it counts what it was given and does nothing else.
     */
    private static final class CountingSink implements BridgeSink {

        private long accepted;

        @Override
        public io.matrix.contract.Outcome accept(BridgeEnvelope envelope) {
            accepted++;
            return null;
        }
    }

    /**
     * A candidate belief, used by the scenarios that need one.
     *
     * @param id the identity of the belief
     * @return the belief
     */
    public static Belief benchmarkBelief(String id) {
        return new Belief(id, "the lamp is lit", io.matrix.contract.BeliefStatus.CANDIDATE,
                io.matrix.contract.Confidence.declared(0.5, List.of("the benchmark says so")),
                List.of(), List.of(), Provenance.of("w51-benchmark", EPISODE_TIMESTAMP), null, 1,
                null, null, null);
    }

    /**
     * The idempotency registry the bridge scenario uses.
     *
     * @return the registry
     */
    public static BridgeIdempotency bridgeIdempotency() {
        return new InMemoryBridgeIdempotency();
    }
}
