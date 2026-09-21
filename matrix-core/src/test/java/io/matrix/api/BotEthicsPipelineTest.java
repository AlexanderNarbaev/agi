package io.matrix.api;

import io.matrix.ethics.FROZENFNLGuardian;
import io.matrix.ethics.FROZENGDPREscalator;
import io.matrix.io.MinecraftBotSensor;
import io.matrix.io.SensorFrame;
import io.matrix.minecraft.HeadlessBotRegistry;
import io.matrix.privacy.TombstoneService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotEthicsPipelineTest {

    @Test
    void tickBotRecordsApprovalWhenActionIsSafe() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("safe-bot");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            guardian.attestNow();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);

            var snap = pipeline.tickBot("safe-bot", "Help me navigate home");
            assertThat(snap).isPresent();
            assertThat(pipeline.totalApprovals()).isEqualTo(1);
            assertThat(pipeline.totalRejections()).isZero();
            assertThat(tombstones.count()).isZero();
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tickBotRecordsRejectionAndCreatesTombstone() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("aggressive-bot");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            guardian.attestNow();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);

            var snap = pipeline.tickBot("aggressive-bot", "Kill the enemy");
            assertThat(snap).isPresent();
            assertThat(pipeline.totalRejections()).isEqualTo(1);
            assertThat(tombstones.count()).isEqualTo(1);
            assertThat(tombstones.all().get(0).reason()).contains("no_killing");
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tickBotMultipleTicksAccumulate() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("multi-bot");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);

            // Use distinct action texts (tombstones are keyed on hash of action).
            pipeline.tickBot("multi-bot", "Help me with task 1");
            pipeline.tickBot("multi-bot", "Kill the enemy leader");
            pipeline.tickBot("multi-bot", "Help me with task 2");
            pipeline.tickBot("multi-bot", "Torture the prisoner");
            pipeline.tickBot("multi-bot", "Help me with task 3");
            assertThat(pipeline.totalApprovals()).isEqualTo(3);
            assertThat(pipeline.totalRejections()).isEqualTo(2);
            assertThat(tombstones.count()).isEqualTo(2);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void multipleBotsShareSingleGuardian() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("bot-1");
            bots.register("bot-2");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);

            // Tick both bots.
            pipeline.tickBot("bot-1", "Help me");
            pipeline.tickBot("bot-2", "Kill the enemy leader");

            // Single shared guardian — both decisions go to the same chain.
            assertThat(pipeline.sharedGuardian().chain().size()).isEqualTo(2);
            assertThat(pipeline.sharedGuardian().totalRejections()).isEqualTo(1);
            assertThat(pipeline.sharedGuardian().totalDecisions()).isEqualTo(2);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tickBotRawBypassesEthics() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("raw-bot");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);

            // Raw tick — no ethics, no tombstones.
            var snap = pipeline.tickBotRaw("raw-bot");
            assertThat(snap).isPresent();
            assertThat(pipeline.totalApprovals()).isZero();
            assertThat(pipeline.totalRejections()).isZero();
            assertThat(tombstones.count()).isZero();
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void auditSummaryIsHumanReadable() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("alpha");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);

            pipeline.tickBot("alpha", "Help me");
            String summary = pipeline.auditSummary();
            assertThat(summary).contains("BotEthicsPipeline").contains("links=").contains("approvals=1");
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void unknownBotReturnsEmptySnapshot() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("real-bot");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);

            var snap = pipeline.tickBot("ghost-bot", "Help me");
            assertThat(snap).isEmpty();
        } finally {
            bots.shutdown();
        }
    }
}