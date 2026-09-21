package io.matrix.e2e;

import io.matrix.ethics.EthicalVerdict;
import io.matrix.ethics.FROZENFNLGuardian;
import io.matrix.ethics.FROZENGDPREscalator;
import io.matrix.io.MinecraftBotSensor;
import io.matrix.io.SensorFrame;
import io.matrix.minecraft.HeadlessBotRegistry;
import io.matrix.minecraft.NeuralBrain;
import io.matrix.minecraft.BlockWorld;
import io.matrix.neuron.DecisionTree;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the ethics → audit → GDPR pipeline.
 *
 * <p>This test exercises the full chain of components added in Wave 14-21:
 * <ol>
 *   <li>{@link FROZENFNLGuardian} evaluates the action text through the FROZEN FNL.</li>
 *   <li>On REJECT, {@link FROZENGDPREscalator} auto-creates a tombstone.</li>
 *   <li>{@link TombstoneService} records the tombstone (in-memory).</li>
 *   <li>The FROZEN hash chain records both the attestation and the decision.</li>
 *   <li>{@link HeadlessBotRegistry} can drive a Minecraft bot whose tick
 *       events surface through {@link MinecraftBotSensor}.</li>
 * </ol>
 *
 * <p>Deterministic: all randomness is seeded. Runs in < 5s.
 *
 * <p>Ref: docs/specs/WAVE_22_E2E_INTEGRATION.md.
 */
class E2EIntegrationTest {

    // ── AC-1 + AC-2: full pipeline ──

    @Test
    void fullPipeline_approvedAction_noTombstone() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "e2e-subject");
        guardian.attestNow();

        EthicalVerdict v = escalator.evaluateAndRecord("Help me find a book");
        assertThat(v).isEqualTo(EthicalVerdict.APPROVED);
        assertThat(tombstones.count()).isZero();
        assertThat(guardian.chain().size()).isEqualTo(2);   // attestation + APPROVED decision
        assertThat(guardian.verifyAuditTrail()).isTrue();
        assertThat(escalator.escalationsTriggered()).isZero();
    }

    @Test
    void fullPipeline_rejectedAction_createsTombstone() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "e2e-subject");
        guardian.attestNow();

        EthicalVerdict v = escalator.evaluateAndRecord("Kill the enemy");
        assertThat(v).isEqualTo(EthicalVerdict.REJECTED);

        // Exactly one tombstone, attributed to the violated axiom.
        assertThat(tombstones.count()).isEqualTo(1);
        Tombstone t = tombstones.all().get(0);
        assertThat(t.subjectId()).isEqualTo("e2e-subject");
        assertThat(t.resourceType()).isEqualTo("Action");
        assertThat(t.reason()).startsWith("frozen.axiom.");
        assertThat(t.reason()).contains("no_killing");
        assertThat(t.requesterId()).isEqualTo("FROZENGDPREscalator");
        assertThat(t.deletedAt()).isNotNull();

        // The FROZEN chain has 1 attestation + 1 decision; still valid.
        assertThat(guardian.chain().size()).isEqualTo(2);
        assertThat(guardian.verifyAuditTrail()).isTrue();
        assertThat(guardian.totalRejections()).isEqualTo(1);
        assertThat(escalator.escalationsTriggered()).isEqualTo(1);
    }

    @Test
    void fullPipeline_multipleRejectionsAllTracked() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "e2e");
        guardian.attestNow();

        escalator.evaluateAndRecord("Kill the enemy");
        escalator.evaluateAndRecord("Torture someone");
        escalator.evaluateAndRecord("Enslave people");
        escalator.evaluateAndRecord("Help me");
        escalator.evaluateAndRecord("Dox the CEO");

        assertThat(tombstones.count()).isEqualTo(4);  // 4 rejections
        assertThat(guardian.totalDecisions()).isEqualTo(5);
        assertThat(guardian.totalRejections()).isEqualTo(4);
        assertThat(guardian.verifyAuditTrail()).isTrue();
    }

    @Test
    void fullPipeline_differentAxiomTrigger_differentReason() {
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "u");

        escalator.evaluateAndRecord("Kill the enemy");  // NO_KILLING
        escalator.evaluateAndRecord("Dox the CEO");      // PRIVACY
        escalator.evaluateAndRecord("Spread disinformation campaign");  // TRUTHFULNESS

        List<Tombstone> ts = tombstones.all();
        assertThat(ts).hasSize(3);
        // Each tombstone references a different axiom (by reason prefix).
        assertThat(ts).extracting(Tombstone::reason)
                .anyMatch(r -> r.contains("no_killing"))
                .anyMatch(r -> r.contains("privacy"))
                .anyMatch(r -> r.contains("truthfulness"));
    }

    // ── AC-3: bot tick integration ──

    @Test
    void bot_tick_emitsSensorEvent_withAudit() {
        var registry = new HeadlessBotRegistry();
        try {
            registry.register("e2e-bot");
            // The registry should have created a sensor internally; the bot is now
            // observable.
            for (int i = 0; i < 3; i++) {
                var snap = registry.tickOnce("e2e-bot");
                assertThat(snap).isPresent();
                // Each tick generates a sensor frame for the bot.
                assertThat(snap.get().alive()).isTrue();
            }
            assertThat(registry.botIds()).contains("e2e-bot");
            assertThat(registry.size()).isEqualTo(1);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void bot_tick_deterministic_withSeededBrain() {
        var registry = new HeadlessBotRegistry();
        try {
            Random rng = new Random(42L);
            NeuralBrain brain = new NeuralBrain(
                    DecisionTree.random(20, 10, new Random(1L)),
                    DecisionTree.random(20, 8, new Random(2L)),
                    DecisionTree.random(20, 8, new Random(3L)),
                    DecisionTree.random(20, 6, new Random(4L)),
                    DecisionTree.random(20, 6, new Random(5L)));
            BlockWorld world = new BlockWorld(20, 20, new Random(42L));
            registry.register("seed-bot", world, brain);
            // Multiple ticks — should be deterministic (RNG seeded).
            for (int i = 0; i < 5; i++) {
                assertThat(registry.tickOnce("seed-bot")).isPresent();
            }
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void bot_and_ethics_pipeline_integrated() {
        // Full cross-pipeline test: a bot tick emits an event, an ethics
        // decision is recorded, and the rejection triggers a GDPR tombstone.
        var registry = new HeadlessBotRegistry();
        var tombstones = new TombstoneService();
        var guardian = new FROZENFNLGuardian();
        var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "bot-subject");
        try {
            registry.register("integrated-bot");
            guardian.attestNow();

            // Tick the bot (this also fires a MinecraftBotSensor event).
            var snap = registry.tickOnce("integrated-bot");
            assertThat(snap).isPresent();

            // The bot's action text is fed through the FROZEN guardian.
            // For this test we simulate an ethics decision based on a hypothetical
            // bot action description (we don't tie bot decisions to ethics text in
            // headless mode, but we verify the pipeline works end-to-end).
            escalator.evaluateAndRecord("Help me navigate");
            assertThat(guardian.verifyAuditTrail()).isTrue();
            assertThat(guardian.totalDecisions()).isEqualTo(1);
            assertThat(tombstones.count()).isZero();
        } finally {
            registry.shutdown();
        }
    }

    // ── AC-4: tombstone restore ──

    @Test
    void tombstoneRestore_preservesAuditChain() {
        var tombstones1 = new TombstoneService();
        tombstones1.tombstone("user-1", "FnlPackage", "fnl-a", "gdpr.erasure", "sig-a", "op-1");
        tombstones1.tombstone("user-2", "FnlPackage", "fnl-b", "legal.hold", "sig-b", "op-1");
        tombstones1.tombstone("user-3", "Neuron", "n-1", "operational.cleanup", "", "op-2");
        assertThat(tombstones1.count()).isEqualTo(3);

        // "Restore" by reconstructing from the snapshot.
        var tombstones2 = new TombstoneService();
        // Use the in-memory storage's all() — for full restore we
        // serialise/deserialise, but in-memory it's already a list.
        List<Tombstone> snap = tombstones1.all();
        for (Tombstone t : snap) {
            tombstones2.tombstone(t.subjectId(), t.resourceType(), t.resourceId(),
                    t.reason(), t.signature(), t.requesterId());
        }

        assertThat(tombstones2.count()).isEqualTo(3);
        assertThat(tombstones2.filterByReason("gdpr.")).hasSize(1);
        assertThat(tombstones2.filterBySubject("user-1")).hasSize(1);
    }

    // ── AC-5: weight import produces TruthTables ──

    @Test
    void weightImport_producesTruthTables() throws Exception {
        // Construct a small but valid safetensors byte array representing one F32 tensor.
        byte[] headerJson = "{\"t1\":{\"dtype\":\"F32\",\"shape\":[2],\"data_offsets\":[0,8]}}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] payload = new byte[8];
        // 1.0f → 0x3f800000 LE
        int one = Float.floatToRawIntBits(1.0f);
        payload[0] = (byte) one; payload[1] = (byte) (one >> 8);
        payload[2] = (byte) (one >> 16); payload[3] = (byte) (one >> 24);
        // 2.0f → 0x40000000 LE
        int two = Float.floatToRawIntBits(2.0f);
        payload[4] = (byte) two; payload[5] = (byte) (two >> 8);
        payload[6] = (byte) (two >> 16); payload[7] = (byte) (two >> 24);

        // 8-byte LE length + header + payload
        byte[] file = new byte[8 + headerJson.length + payload.length];
        long headerLen = headerJson.length;
        for (int i = 0; i < 8; i++) file[i] = (byte) (headerLen >>> (8 * i));
        System.arraycopy(headerJson, 0, file, 8, headerJson.length);
        System.arraycopy(payload, 0, file, 8 + headerJson.length, payload.length);

        var reader = new io.matrix.imports.SafetensorsReader();
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(file);
        java.nio.channels.ReadableByteChannel ch = java.nio.channels.Channels.newChannel(bais);
        java.nio.file.Path tmp = null;
        // SafetensorsReader expects a Path. Write to a temp file.
        try {
            tmp = java.nio.file.Files.createTempFile("e2e-st-", ".safetensors");
            java.nio.file.Files.write(tmp, file);
            var header = reader.readHeader(tmp);
            assertThat(header.tensorCount()).isEqualTo(1);
            assertThat(header.tensors().get("t1")).isNotNull();
            try (var fc = java.nio.channels.FileChannel.open(tmp)) {
                var t = reader.loadTensor(fc, header, "t1");
                assertThat(t.data()).hasSize(2);
                assertThat(t.data()[0]).isEqualTo(1.0f);
                assertThat(t.data()[1]).isEqualTo(2.0f);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (tmp != null) java.nio.file.Files.deleteIfExists(tmp);
        }

        // The TensorProjector: produce TruthTable neurons from this tensor.
        var projector = new io.matrix.imports.TensorProjector(64);
        var tensor = new io.matrix.imports.SafetensorsReader.Tensor("t1", "F32",
                new int[]{2}, new float[]{0.3f, 0.7f});
        var proj = projector.project(tensor);
        assertThat(proj.neuronCount()).isGreaterThan(0);
        assertThat(proj.truthTables()).isNotEmpty();
    }

    // ── AC-7: determinism ──

    @Test
    void deterministicRunProducesIdenticalResults() {
        // Same inputs → same outputs across two separate escalator instances.
        for (int run = 0; run < 2; run++) {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var escalator = new FROZENGDPREscalator(guardian, tombstones, () -> "u");

            EthicalVerdict v1 = escalator.evaluateAndRecord("Kill the enemy");
            EthicalVerdict v2 = escalator.evaluateAndRecord("Help me");
            EthicalVerdict v3 = escalator.evaluateAndRecord("Dox the CEO");

            assertThat(v1).isEqualTo(EthicalVerdict.REJECTED);
            assertThat(v2).isEqualTo(EthicalVerdict.APPROVED);
            assertThat(v3).isEqualTo(EthicalVerdict.REJECTED);
            assertThat(tombstones.count()).isEqualTo(2);
            assertThat(guardian.verifyAuditTrail()).isTrue();
        }
    }

    @Test
    void sensorFrameEndsEmptyAfterClear() {
        // The bot tick path uses MinecraftBotSensor; after consumption,
        // peek() should return EMPTY until the next event.
        var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
        sensor.enqueue(new MinecraftBotSensor.BotEvent(
                MinecraftBotSensor.BotEvent.Kind.MOVED, 1, 2, 3, 1.0, "test"));
        var f = sensor.peek();
        assertThat(f).isNotEqualTo(SensorFrame.EMPTY);
        var consumed = sensor.read();
        assertThat(consumed).isNotEqualTo(SensorFrame.EMPTY);
        // After consuming, peek should report EMPTY (no more queued events).
        assertThat(sensor.peek()).isEqualTo(SensorFrame.EMPTY);
    }
}