package io.matrix.brain.runtime.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * RECON-W38.2 (M4) — S4.3 simulation: a served retrieval stream drives the fatigue -&gt; sleep-demand
 * pathway, and stream noise provably does NOT.
 *
 * <p><b>This test falsifies part of its own milestone wording, on purpose.</b> The M4 backlog entry
 * reads "noisy stream -&gt; fatigue rise -&gt; sleep demand signal". Only the middle and last arrows
 * are real. Fatigue is accumulated from query <i>volume</i> alone, and
 * {@link NeuroModulatorHub#sleepDemand()} reads fatigue and reward and nothing else — the candidate
 * spread that drives the uncertainty channel never reaches the sleep channel. A test that drove a
 * noisy stream and then asserted sleep demand had risen would pass while proving a causal claim the
 * implementation does not support. So this simulation holds volume fixed and varies only the noise,
 * and asserts that the two regimes are <i>indistinguishable</i> on every sleep-relevant field.</p>
 *
 * <p><b>What the simulation establishes:</b></p>
 * <ul>
 *   <li>Within this simulation — no reward signal, default sleep threshold — served volume and
 *       nothing else drives fatigue, sleep demand and the sleep latch. Outside it that is not a
 *       system law: reward cancels demand and the threshold gates the latch.</li>
 *   <li>At equal volume, a high-spread ("noisy") stream and a low-spread ("clean") stream produce
 *       bit-identical fatigue, sleep demand, sleep decision and retrieval floor.</li>
 *   <li>Stream spread moves exactly one channel — {@code uncertaintyTolerance} — and that channel
 *       has no consumer in the repository, so it changes no behaviour yet.</li>
 * </ul>
 *
 * <p><b>What it does not establish:</b> that noise should cause sleep demand. That would require a
 * consumer for the uncertainty channel and a demonstrated link from tolerance to sleep
 * scheduling; neither exists.</p>
 *
 * <p><b>Randomness policy (CONSTITUTION Article I, stratified).</b> The only randomness is the
 * per-query jitter inside this test's own stream generator, which is seeded
 * {@link #STREAM_SEED} and is a test fixture, not a decision path. No production code is exercised
 * that reads a clock, a global RNG or a wall-clock entropy source. The seed is a literal so the
 * stream is bit-reproducible on any conforming JVM. {@code java.util.Random} is specified
 * algorithmically by the Java language, so the generated double sequence is stable across
 * implementations and releases BY SPECIFICATION — not by measurement here.
 * {@link #the_stream_simulation_is_bit_reproducible_under_the_recorded_seed()} pins same-process
 * repeatability only: it regenerates the stream twice inside one JVM and compares them. Cross-process
 * or cross-release stability rests on the specification, which this test does not exercise.
 * </p>
 */
class NeuromodulationStreamSimulationTest {

    /** "MATRIX" in ASCII. The one seed that defines every stream in this test. */
    private static final long STREAM_SEED = 0x4D4154524958L;

    /** Served queries per stream. Below the sleep latch (14) and below fatigue saturation. */
    private static final int VOLUME = 12;

    /** Candidates per query. Four, so the variance denominator is n and not n-1. */
    private static final int CANDIDATES = 4;

    /** A confident retrieval: candidates crowded near the top of the range. */
    private static final double[] CLEAN_BASE = {0.94, 0.92, 0.90, 0.88};

    /** Jitter amplitude for the clean regime: noise is present but small. */
    private static final double CLEAN_JITTER = 0.005;

    /** A maximally separated retrieval: one clear winner, a tail of near-zero candidates. */
    private static final double[] NOISY_BASE = {0.95, 0.55, 0.30, 0.10};

    /** Jitter amplitude for the noisy regime: an order of magnitude larger than the clean regime. */
    private static final double NOISY_JITTER = 0.05;

    // ------------------------------------------------------------------
    // The causal question: does stream noise reach the sleep channel?
    // ------------------------------------------------------------------

    /**
     * The milestone's central implicit claim, tested adversarially. Volume is identical across the
     * three regimes; only the candidate spread differs. If noise drove fatigue or sleep demand these
     * assertions would fail.
     */
    @Test
    void served_volume_alone_drives_fatigue_and_sleep_demand_in_every_stream_regime() {
        List<List<Double>> clean = generate(CLEAN_BASE, CLEAN_JITTER, STREAM_SEED, VOLUME);
        List<List<Double>> noisy = generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED, VOLUME);
        List<List<Double>> silent = generate(null, 0.0, STREAM_SEED, VOLUME);

        NeuroModulatorHub cleanHub = runStream(clean);
        NeuroModulatorHub noisyHub = runStream(noisy);
        NeuroModulatorHub silentHub = runStream(silent);

        // The noise really is different, or the comparison would be vacuous.
        assertThat(meanVariance(noisy)).isGreaterThan(meanVariance(clean));

        // Fatigue is volume, full stop.
        assertThat(noisyHub.fatigueIndex()).isEqualTo(cleanHub.fatigueIndex());
        assertThat(silentHub.fatigueIndex()).isEqualTo(cleanHub.fatigueIndex());

        // Sleep demand and the sleep decision are identical across all three.
        assertThat(noisyHub.sleepDemand()).isEqualTo(cleanHub.sleepDemand());
        assertThat(silentHub.sleepDemand()).isEqualTo(cleanHub.sleepDemand());
        assertThat(noisyHub.shouldSleep()).isEqualTo(cleanHub.shouldSleep());
        assertThat(silentHub.shouldSleep()).isEqualTo(cleanHub.shouldSleep());

        // No retrieval evidence is no uncertainty signal: a null candidate list must contribute
        // nothing, so the silent stream leaves the tolerance at the production default. Measured
        // literal, not the production constant — against the constant, an implementation returning
        // any value for null input would pass. Without this line, treating null as maximal spread
        // survives every assertion in this class.
        assertThat(silentHub.uncertaintyTolerance()).isEqualTo(0.5);

        // With no reward, demand equals fatigue exactly (below the severe-fatigue floor), so the
        // volume -> fatigue -> demand chain is directly observable rather than inferred.
        assertThat(cleanHub.sleepDemand()).isEqualTo(cleanHub.fatigueIndex());

        // Twelve queries is deliberately short of the latch: the demand signal exists but is not yet
        // a sleep request. Pinning this keeps the latch test below from passing vacuously.
        assertThat(cleanHub.shouldSleep()).isFalse();
    }

    /**
     * Noise touches one channel: the tolerance is the only field where the regimes may differ.
     *
     * <p>Bit-exact carry-through of attention, reward and threshold is evidenced by
     * {@code FatigueUncertaintyTest}, NOT by this class. Here those fields are all 0.0, and 0.0 is a
     * fixed point of rounding, clamping and scaling, so a mutating implementation would still pass
     * the carry-through comparisons below. The strong claim lives in the other class.</p>
     */
    @Test
    void stream_noise_moves_only_the_uncertainty_channel() {
        NeuroModulatorHub clean = runStream(generate(CLEAN_BASE, CLEAN_JITTER, STREAM_SEED, VOLUME));
        NeuroModulatorHub noisy = runStream(generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED, VOLUME));

        assertThat(noisy.uncertaintyTolerance()).isLessThan(clean.uncertaintyTolerance());

        // Measured literals, not production constants: if a constructor default moved, these fail.
        assertThat(noisy.attentionLevel()).isEqualTo(0.0);
        assertThat(noisy.rewardSignal()).isEqualTo(0.0);
        assertThat(noisy.sleepDemandThreshold()).isEqualTo(0.7);

        assertThat(noisy.attentionLevel()).isEqualTo(clean.attentionLevel());
        assertThat(noisy.rewardSignal()).isEqualTo(clean.rewardSignal());
        assertThat(noisy.sleepDemandThreshold()).isEqualTo(clean.sleepDemandThreshold());
        assertThat(noisy.fatigueIndex()).isEqualTo(clean.fatigueIndex());
        assertThat(noisy.sleepDemand()).isEqualTo(clean.sleepDemand());
        assertThat(noisy.shouldSleep()).isEqualTo(clean.shouldSleep());

        // Attention is identity here, so the retrieval floor cannot differ either.
        assertThat(noisy.retrievalFloor()).isEqualTo(clean.retrievalFloor());
    }

    /**
     * The uncertainty channel's total drop is the sum of per-query spread times the step, recomputed
     * here by an implementation that deliberately does not call {@code varianceOf}. That keeps the
     * assertion from being a restatement of the production formula: a change of variance convention
     * (population to sample, or mean to root-mean-square) would break it.
     */
    @Test
    void the_uncertainty_channel_totals_the_sum_of_per_query_spread() {
        // Pin the denominator convention independently: population variance, so 1.0 and 0.0 give
        // 0.25, not the 0.5 a sample variance would produce.
        assertThat(independentPopulationVariance(List.of(1.0, 0.0))).isEqualTo(0.25);

        List<List<Double>> noisy = generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED, VOLUME);
        NeuroModulatorHub hub = runStream(noisy);

        double expected = NeuroModulatorHub.productionDefault().uncertaintyTolerance();
        for (List<Double> query : noisy) {
            expected -= independentPopulationVariance(query)
                    * FatigueUncertaintyAggregator.UNCERTAINTY_STEP_ON_MAX_VARIANCE;
        }

        // The floor and the clamp do not bind at this volume, so the recursion is a plain sum.
        assertThat(expected).isGreaterThan(FatigueUncertaintyAggregator.MIN_UNCERTAINTY_TOLERANCE);
        assertThat(hub.uncertaintyTolerance()).isEqualTo(expected);
    }

    // ------------------------------------------------------------------
    // The chain that IS real: volume -> fatigue -> sleep demand -> latch
    // ------------------------------------------------------------------

    /**
     * The genuine signal path, measured rather than asserted. Walk the stream one served query at a
     * time and find the first step that requests sleep.
     */
    @Test
    void sleep_demand_latches_at_the_fourteenth_served_query() {
        List<List<Double>> stream = generate(CLEAN_BASE, CLEAN_JITTER, STREAM_SEED, 40);

        NeuroModulatorHub hub = NeuroModulatorHub.productionDefault();
        int firstAsleep = -1;
        for (int served = 1; served <= 40; served++) {
            hub = FatigueUncertaintyAggregator.observe(hub, stream.get(served - 1));
            if (firstAsleep < 0 && hub.shouldSleep()) {
                firstAsleep = served;
                // The demand equals accumulated fatigue at the latch, with no reward in play.
                assertThat(hub.sleepDemand()).isEqualTo(hub.fatigueIndex());
                assertThat(hub.sleepDemand()).isGreaterThanOrEqualTo(0.7);
            }
        }

        assertThat(firstAsleep).isEqualTo(14);
        assertThat(hub.shouldSleep()).isTrue();
    }

    /**
     * Fatigue only ever rises, so demand must be non-decreasing in served volume. This is the
     * property that makes the latch a latch rather than a fluctuation.
     */
    @Test
    void sleep_demand_is_monotone_non_decreasing_in_served_volume() {
        List<List<Double>> stream = generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED, 24);

        NeuroModulatorHub hub = NeuroModulatorHub.productionDefault();
        double previous = hub.sleepDemand();
        for (int served = 0; served < 24; served++) {
            hub = FatigueUncertaintyAggregator.observe(hub, stream.get(served));
            double current = hub.sleepDemand();
            assertThat(current).isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }

    /**
     * Past severe fatigue the hub guarantees a sleep request regardless of REWARD. This is a
     * hub-level rule and it is the reason the demand curve is not simply linear in volume. The lift is
     * a one-step transient: at the next step accumulated fatigue has itself reached the floor, and
     * demand equals fatigue again.
     */
    @Test
    void severe_fatigue_lifts_sleep_demand_above_the_accumulated_fatigue() {
        List<List<Double>> stream = generate(CLEAN_BASE, CLEAN_JITTER, STREAM_SEED, 24);

        NeuroModulatorHub hub = NeuroModulatorHub.productionDefault();
        double previousDemand = hub.sleepDemand();
        int firstLift = -1;
        for (int served = 1; served <= 24; served++) {
            hub = FatigueUncertaintyAggregator.observe(hub, stream.get(served - 1));
            if (firstLift < 0 && hub.sleepDemand() > hub.fatigueIndex()) {
                firstLift = served;
                assertThat(hub.sleepDemand()).isEqualTo(0.85);
                assertThat(hub.sleepDemand()).isGreaterThan(hub.fatigueIndex());
            }
            assertThat(hub.sleepDemand()).isGreaterThanOrEqualTo(previousDemand);
            previousDemand = hub.sleepDemand();
        }

        assertThat(firstLift).isEqualTo(16);
    }

    // ------------------------------------------------------------------
    // Boundary operators, pinned on exact values rather than on an accumulated stream
    // ------------------------------------------------------------------

    /**
     * The two boundary comparisons in {@code NeuroModulatorHub} cannot be pinned from the stream
     * path, because accumulated fatigue approaches both boundaries from ABOVE: step 14 is
     * {@code 0.7000000000000001} and step 16 is {@code 0.8000000000000002} — one ULP high in each
     * case. Along that path {@code >=} and {@code >} are observationally identical, so flipping
     * either operator leaves every accumulation-based assertion in this class green.
     *
     * <p>These assertions therefore construct hubs at the exact boundary values, where the comparison
     * operators genuinely differ. Flipping {@code shouldSleep()} to {@code >} makes the first
     * assertion fail; flipping the severe-fatigue comparison to {@code >} makes the second return
     * 0.8 instead of 0.85.</p>
     *
     * <p>The severe-fatigue value is asserted as a literal, never as
     * {@code NeuroModulatorHub.MIN_DEMAND_AT_SEVERE_FATIGUE}, because the implementation reads that
     * constant itself — asserting against it cannot detect a change to it.</p>
     */
    @Test
    void exact_boundary_values_pin_the_sleep_and_severe_fatigue_operators() {
        // Sleep latch: demand 0.7 against threshold 0.7 is exactly the equality case.
        assertThat(new NeuroModulatorHub(0.0, 0.5, 0.0, 0.7, 0.7).shouldSleep()).isTrue();

        // Severe-fatigue floor: fatigue exactly 0.8 must lift demand to the 0.85 floor.
        assertThat(new NeuroModulatorHub(0.0, 0.5, 0.0, 0.8, 0.7).sleepDemand()).isEqualTo(0.85);
        assertThat(new NeuroModulatorHub(0.0, 0.5, 0.0, 0.8, 0.7).sleepDemand())
                .isGreaterThan(NeuroModulatorHub.DEFAULT_SLEEP_DEMAND_THRESHOLD);
    }

    // ------------------------------------------------------------------
    // Reproducibility and the long-run behaviour of the ratchet
    // ------------------------------------------------------------------

    /**
     * The seed is the reproducibility contract. Regenerating the stream and replaying it within this
     * same process must land on the same state bit for bit. Scope: one JVM, one run. Stability across
     * processes, JVMs or releases is guaranteed by the {@code java.util.Random} specification and is
     * NOT asserted here — this test cannot observe it.
     */
    @Test
    void the_stream_simulation_is_bit_reproducible_under_the_recorded_seed() {
        List<List<Double>> first = generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED, VOLUME);
        List<List<Double>> second = generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED, VOLUME);

        assertThat(second).isEqualTo(first);

        NeuroModulatorHub a = runStream(first);
        NeuroModulatorHub b = runStream(second);

        assertThat(b.fatigueIndex()).isEqualTo(a.fatigueIndex());
        assertThat(b.uncertaintyTolerance()).isEqualTo(a.uncertaintyTolerance());
        assertThat(b.sleepDemand()).isEqualTo(a.sleepDemand());
        assertThat(b.retrievalFloor()).isEqualTo(a.retrievalFloor());
        assertThat(b.shouldSleep()).isEqualTo(a.shouldSleep());
    }

    /**
     * The ratchet at simulation scale. A long noisy stream drives fatigue to saturation and the
     * uncertainty channel to its floor. Both values then HOLD: no further observation changes
     * either terminal value. (Across the run the tolerance decreases at every observation until it
     * reaches the floor — only the terminal state is stable.)
     * The reset contract belongs to RealSleepScheduler in a later milestone and does not exist yet,
     * which is precisely why this behaviour is pinned rather than left implicit.
     */
    @Test
    void a_long_noisy_stream_saturates_fatigue_and_floors_the_uncertainty_channel() {
        List<List<Double>> stream = generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED, 200);

        NeuroModulatorHub hub = runStream(stream);
        assertThat(hub.fatigueIndex()).isEqualTo(1.0);
        assertThat(hub.sleepDemand()).isEqualTo(1.0);
        assertThat(hub.shouldSleep()).isTrue();
        assertThat(hub.uncertaintyTolerance()).isEqualTo(0.1);

        // Holding, not merely reaching: fifty further queries change nothing.
        for (List<Double> query : generate(NOISY_BASE, NOISY_JITTER, STREAM_SEED + 1, 50)) {
            hub = FatigueUncertaintyAggregator.observe(hub, query);
        }
        assertThat(hub.uncertaintyTolerance()).isEqualTo(0.1);
        assertThat(hub.fatigueIndex()).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    // Stream generation and helpers
    // ------------------------------------------------------------------

    /** A deterministic stream of candidate-score lists; a null base means "no retrieval evidence". */
    private static List<List<Double>> generate(double[] base, double jitter, long seed, int volume) {
        Random random = new Random(seed);
        List<List<Double>> stream = new ArrayList<>(volume);
        for (int query = 0; query < volume; query++) {
            if (base == null) {
                stream.add(null);
                continue;
            }
            List<Double> scores = new ArrayList<>(CANDIDATES);
            for (int candidate = 0; candidate < CANDIDATES; candidate++) {
                double jittered = base[candidate] + (random.nextDouble() * 2.0 - 1.0) * jitter;
                scores.add(Math.max(0.0, Math.min(1.0, jittered)));
            }
            stream.add(scores);
        }
        return stream;
    }

    /** Feeds a stream through the aggregator the way a serving path eventually would. */
    private static NeuroModulatorHub runStream(List<List<Double>> stream) {
        NeuroModulatorHub hub = NeuroModulatorHub.productionDefault();
        for (List<Double> query : stream) {
            hub = FatigueUncertaintyAggregator.observe(hub, query);
        }
        return hub;
    }

    private static double meanVariance(List<List<Double>> stream) {
        double total = 0.0;
        int counted = 0;
        for (List<Double> query : stream) {
            total += FatigueUncertaintyAggregator.varianceOf(query);
            counted++;
        }
        return counted == 0 ? 0.0 : total / counted;
    }

    /**
     * Population variance, re-implemented as a closed form so the assertion above checks the
     * estimator CONVENTION (population, dividing by n) rather than calling production repeatedly.
     * Honest scope: it executes the same algorithm in the same floating-point order as
     * {@code varianceOf}, so it catches a change of denominator or estimator, but it CANNOT detect a
     * floating-point reordering inside production.
     */
    private static double independentPopulationVariance(List<Double> values) {
        int n = values.size();
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        double mean = sum / n;
        double squared = 0.0;
        for (double value : values) {
            double deviation = value - mean;
            squared += deviation * deviation;
        }
        return squared / n;
    }
}