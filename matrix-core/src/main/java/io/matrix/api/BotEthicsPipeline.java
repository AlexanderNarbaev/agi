package io.matrix.api;

import io.matrix.ethics.EthicalVerdict;
import io.matrix.ethics.FROZENFNLGuardian;
import io.matrix.ethics.FROZENGDPREscalator;
import io.matrix.io.MinecraftBotSensor;
import io.matrix.io.SensorFrame;
import io.matrix.minecraft.HeadlessBotRegistry;
import io.matrix.minecraft.HeadlessBotSnapshot;
import io.matrix.privacy.TombstoneService;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Wires {@link HeadlessBotRegistry} together with {@link FROZENFNLGuardian}:
 * every bot tick produces a MinecraftBotSensor event AND records a FROZEN
 * FNL decision in the audit chain. REJECT decisions automatically create
 * GDPR tombstones.
 *
 * <p>Pipeline:
 * <pre>
 *   bot tick → BotCoordinator → MinecraftBotSensor.enqueue(BotEvent)
 *              ↓
 *              → FROZENFNLGuardian.evaluate(action_text) → verdict
 *              ↓
 *              → FROZENGDPREscalator.recordIfRejected(verdict, text)
 *              ↓
 *              → TombstoneService.tombstone(...) + audit chain append
 * </pre>
 *
 * <p>This is the **production wiring** that makes Wave 14-21 useful together:
 * without this glue, the components exist as separate islands. With it, every
 * bot action is recorded, every ethics decision is auditable, and every
 * rejection leaves a GDPR-compliant trail.
 *
 * <p>Ref: docs/specs/WAVE_22_E2E_INTEGRATION.md, Wave 14-21.
 */
public final class BotEthicsPipeline {

    private final HeadlessBotRegistry botRegistry;
    private final FROZENGDPREscalator escalator;
    private final MinecraftBotSensor sharedSensor;
    private final FROZENFNLGuardian sharedGuardian;

    private final AtomicLong totalRejections = new AtomicLong();
    private final AtomicLong totalApprovals = new AtomicLong();

    public BotEthicsPipeline(HeadlessBotRegistry botRegistry,
                              FROZENFNLGuardian guardian,
                              TombstoneService tombstones,
                              MinecraftBotSensor sensor) {
        this(botRegistry, () -> guardian, tombstones, sensor, () -> "bot-subject");
    }

    public BotEthicsPipeline(HeadlessBotRegistry botRegistry,
                              Supplier<FROZENFNLGuardian> guardianFactory,
                              TombstoneService tombstones,
                              MinecraftBotSensor sensor,
                              Supplier<String> subjectIdSupplier) {
        if (botRegistry == null) throw new IllegalArgumentException("botRegistry required");
        if (guardianFactory == null) throw new IllegalArgumentException("guardianFactory required");
        if (tombstones == null) throw new IllegalArgumentException("tombstones required");
        if (sensor == null) throw new IllegalArgumentException("sensor required");
        this.botRegistry = botRegistry;
        this.sharedSensor = sensor;
        this.sharedGuardian = guardianFactory.get();
        this.escalator = new FROZENGDPREscalator(this.sharedGuardian, tombstones, subjectIdSupplier);
    }

    /** Convenience: get the shared FROZEN FNL guardian. */
    public FROZENFNLGuardian sharedGuardian() {
        return sharedGuardian;
    }

    /** Run a single bot tick AND run the FROZEN pipeline on the action. */
    public Optional<HeadlessBotSnapshot> tickBot(String botId, String actionText) {
        // Step 1: Run bot tick (produces BotEvent in shared sensor + snapshot).
        Optional<HeadlessBotSnapshot> snap = botRegistry.tickOnce(botId);
        if (snap.isEmpty()) return snap;

        // Step 2: Drive the FROZEN guardian (shared, single instance per pipeline).
        EthicalVerdict verdict = escalator.evaluateAndRecord(actionText);
        if (verdict == EthicalVerdict.REJECTED) totalRejections.incrementAndGet();
        else totalApprovals.incrementAndGet();
        return snap;
    }

    /** Run a bot tick WITHOUT ethics evaluation (e.g. in load tests). */
    public Optional<HeadlessBotSnapshot> tickBotRaw(String botId) {
        return botRegistry.tickOnce(botId);
    }

    /** Check the latest sensor frame for a given bot (after a tick). */
    public SensorFrame latestSensorFrame() {
        return sharedSensor.peek();
    }

    /** Counters. */
    public long totalApprovals() { return totalApprovals.get(); }
    public long totalRejections() { return totalRejections.get(); }

    /** Aggregate audit chain across all bots in this pipeline. */
    public String auditSummary() {
        int links = sharedGuardian.chain().size();
        boolean allValid = sharedGuardian.verifyAuditTrail();
        return String.format(Locale.ROOT,
                "BotEthicsPipeline[links=%d valid=%s approvals=%d rejections=%d]",
                links, allValid,
                totalApprovals.get(), totalRejections.get());
    }
}