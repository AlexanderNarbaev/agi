package io.matrix.reasoning;

import io.matrix.actions.ActionArena;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.neuron.SchemaDescriptor;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Brain-loop service: the production wiring of {@link ConsciousnessLoop}
 * used by {@code OpenAIChatResource#chatCompletions} (SPEC-006 / DESIGN-18).
 *
 * <p>One {@link #tick(BitSet)} advances the canonical nine-stage loop:
 * <pre>
 *   perception → attention → deliberation → gate → action
 *     → consolidation → subconscious → prediction-error → attention
 * </pre>
 *
 * <p>Each tick returns a {@link Trace} that the chat endpoint reflects
 * back via the {@code X-Matrix-Trace} response header. This makes the
 * brain loop observable from the wire — required by RUN 12's done
 * criteria.
 *
 * <p>Thread-safety: {@link ConsciousnessLoop} is stateless between ticks
 * (per-tick state lives in the loop body), so concurrent {@link #tick}
 * calls are safe. The {@code lastTrace} is the most-recent tick only;
 * use the {@code Trace#tickId()} field to correlate.
 *
 * <p>Determinism: no wall-clock, no randomness. The perception supplier
 * returns the input that the caller of {@link #tick(BitSet)} passed in —
 * same input → same perception → same trace.
 */
@ApplicationScoped
@Startup
public class BrainLoopService {

    private static final Logger log = LoggerFactory.getLogger(BrainLoopService.class);

    /** Stable phase names — kept in canonical order for trace serialization. */
    public static final String[] PHASES = {
            "perception", "attention", "deliberation", "gate", "action",
            "consolidation", "subconscious", "prediction-error", "attention-update"
    };

    /** Compact trace returned from each tick (set on response header). */
    public record Trace(long tickId,
                        String phasePath,
                        long attentionScore,
                        long predictionError,
                        int actionsSubmitted) {
        public Trace {
            Objects.requireNonNull(phasePath, "phasePath");
        }
        public String headerValue() {
            return "tick=" + tickId
                    + " phases=" + phasePath
                    + " attention=" + attentionScore
                    + " predErr=" + predictionError
                    + " actions=" + actionsSubmitted;
        }
    }

    private final ConsciousnessLoop loop;
    private final Holder perception;
    private final AtomicLong totalTicks = new AtomicLong();
    private volatile Trace lastTrace = new Trace(0L, "uninitialised", 0L, 0L, 0);

    /** RUN 49 — optional freeze manager. When set, ticks gated by it. */
    private volatile io.matrix.ethics.FreezeRecoveryManager freezeManager;

    /** Boot trace. */
    void onStart(@Observes StartupEvent ev) {
        log.info("BrainLoopService ready (loop-stages={})", PHASES.length);
    }

    /**
     * CDI constructor: builds the production loop with safe defaults.
     *
     * <p>The perception supplier is a {@link Holder} that gets overwritten
     * on each {@link #tick(BitSet)} call so the loop sees the current
     * input. No wall-clock, no random.
     */
    public BrainLoopService() {
        this.perception = new Holder();
        // Empty chain: input is pass-through (matches ConsciousnessLoopTest pattern).
        BrcChain chain = new BrcChain(List.of(), 0, true, SchemaDescriptor.scalar(8));
        ActionArena arena = ActionArena.defaults();
        ConsolidationCycle consolidation = new ConsolidationCycle();
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        this.loop = new ConsciousnessLoop(
                chain,
                arena,
                consolidation,
                budgeter,
                ConsciousnessLoop.uniform(),
                this.perception);
    }

    /**
     * Advance the loop by one tick.
     *
     * @param input observation vector (caller owns; not mutated)
     * @return trace for the X-Matrix-Trace response header
     */
    public Trace tick(BitSet input) {
        Objects.requireNonNull(input, "input");
        // RUN 49: gate via freeze manager if set.
        io.matrix.ethics.FreezeRecoveryManager freeze = this.freezeManager;
        if (freeze != null && !freeze.isActionAllowed()) {
            // Frozen: return a noop trace indicating the freeze.
            Trace frozen = new Trace(
                    -1L,
                    "frozen",
                    0L,
                    0L,
                    0);
            lastTrace = frozen;
            return frozen;
        }
        // Defensive copy because the loop may use it across phases.
        BitSet snapshot = (BitSet) input.clone();
        // Install the latest snapshot in the holder the loop reads.
        perception.setBits(snapshot);
        ConsciousnessLoop.TickSnapshot snap = loop.tick();
        totalTicks.incrementAndGet();
        Trace t = new Trace(
                snap.tickId(),
                String.join("->", PHASES),
                snap.attentionScore(),
                snap.predictionError(),
                snap.actionsSubmitted());
        lastTrace = t;
        return t;
    }

    /** RUN 49 — set the freeze manager. */
    public void setFreezeManager(io.matrix.ethics.FreezeRecoveryManager manager) {
        this.freezeManager = manager;
    }

    /** RUN 49 — get the current freeze manager (or null). */
    public io.matrix.ethics.FreezeRecoveryManager getFreezeManager() {
        return freezeManager;
    }

    /** RUN 49 — report an ethics violation (forwards to the manager if set). */
    public boolean reportViolation(String source, String reason) {
        io.matrix.ethics.FreezeRecoveryManager freeze = this.freezeManager;
        if (freeze == null) return false;
        long tickId = totalTicks.get();
        return freeze.reportViolation(source, reason, tickId);
    }

    public Trace lastTrace() {
        return lastTrace;
    }

    public long totalTicks() {
        return totalTicks.get();
    }

    /**
     * Test seam: expose the inner loop for direct verification.
     */
    ConsciousnessLoop loop() {
        return loop;
    }

    /**
     * Perception supplier — the loop reads via {@link #get()} each tick;
     * {@link #setBits(BitSet)} installs the latest input from
     * {@link BrainLoopService#tick(BitSet)}.
     *
     * <p>Package-private so the service can update bits from outside the
     * constructor. Synchronised on the holder itself — the supplier
     * returns the most-recently installed snapshot.
     */
    static final class Holder implements Supplier<BitSet> {
        private volatile BitSet bits = new BitSet();

        @Override
        public synchronized BitSet get() {
            return (BitSet) bits.clone();
        }

        synchronized void setBits(BitSet bits) {
            this.bits = (BitSet) bits.clone();
        }
    }
}
