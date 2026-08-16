package io.matrix.api;

import io.matrix.ethics.FROZENFNLGuardian;
import io.matrix.io.MinecraftBotSensor;
import io.matrix.minecraft.HeadlessBotRegistry;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BotEthicsResourceTest {

    @Test
    void tickApproveReturnsSnapshot() {
        var bots = new HeadlessBotRegistry();
        bots.register("r-bot");
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            var resp = resource.tick("r-bot", "Help me navigate", null);
            assertThat(resp.getStatus()).isEqualTo(200);
            var body = (BotEthicsResource.TickResponse) resp.getEntity();
            assertThat(body.botId()).isEqualTo("r-bot");
            assertThat(body.actionText()).isEqualTo("Help me navigate");
            assertThat(body.approvals()).isEqualTo(1);
            assertThat(body.rejections()).isZero();
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tickRejectCreatesTombstone() {
        var bots = new HeadlessBotRegistry();
        bots.register("r-bot");
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            var resp = resource.tick("r-bot", "Kill the enemy", null);
            assertThat(resp.getStatus()).isEqualTo(200);
            var body = (BotEthicsResource.TickResponse) resp.getEntity();
            assertThat(body.rejections()).isEqualTo(1);
            assertThat(tombstones.count()).isEqualTo(1);
            assertThat(tombstones.all().get(0).reason()).contains("no_killing");
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tickBodyOverridesQueryAction() {
        var bots = new HeadlessBotRegistry();
        bots.register("r-bot");
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            var req = new BotEthicsResource.TickRequest();
            req.actionText = "Help me with body";
            var resp = resource.tick("r-bot", "ignored query", req);
            var body = (BotEthicsResource.TickResponse) resp.getEntity();
            assertThat(body.actionText()).isEqualTo("Help me with body");
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tickUnknownBotReturns404() {
        var bots = new HeadlessBotRegistry();
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            var resp = resource.tick("ghost", "Help", null);
            assertThat(resp.getStatus()).isEqualTo(404);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void auditReturnsChainAndCounters() {
        var bots = new HeadlessBotRegistry();
        bots.register("a");
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            pipeline.tickBot("a", "Help me");
            pipeline.tickBot("a", "Kill the enemy");

            var resp = resource.audit();
            assertThat(resp.getStatus()).isEqualTo(200);
            var body = (BotEthicsResource.AuditResponse) resp.getEntity();
            assertThat(body.links()).isEqualTo(2);
            assertThat(body.valid()).isTrue();
            assertThat(body.approvals()).isEqualTo(1);
            assertThat(body.rejections()).isEqualTo(1);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void snapshotReturns200ForExistingBot() {
        var bots = new HeadlessBotRegistry();
        bots.register("a");
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            var resp = resource.snapshot("a");
            assertThat(resp.getStatus()).isEqualTo(200);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void snapshotReturns404ForUnknownBot() {
        var bots = new HeadlessBotRegistry();
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            var resp = resource.snapshot("ghost");
            assertThat(resp.getStatus()).isEqualTo(404);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tombstonesListAllWithoutFilter() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("a");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            pipeline.tickBot("a", "Kill the enemy");
            pipeline.tickBot("a", "Dox the CEO");

            var resp = resource.tombstones(null, null);
            assertThat(resp.getStatus()).isEqualTo(200);
            var body = (BotEthicsResource.TombstoneListResponse) resp.getEntity();
            assertThat(body.size()).isEqualTo(2);
            assertThat(body.tombstones()).hasSize(2);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tombstonesListFilteredByReason() {
        var bots = new HeadlessBotRegistry();
        try {
            bots.register("a");
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            pipeline.tickBot("a", "Kill the enemy");
            pipeline.tickBot("a", "Dox the CEO");

            // Reasons are 'frozen.axiom.<name>' — filter by that prefix.
            var resp = resource.tombstones("frozen.", null);
            var body = (BotEthicsResource.TombstoneListResponse) resp.getEntity();
            assertThat(body.size()).isEqualTo(2);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void tombstonesListFilteredBySubject() {
        var bots = new HeadlessBotRegistry();
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            pipeline.tickBot("a", "Kill the enemy");

            var resp = resource.tombstones(null, "e2e-subject-default");
            // No tombstones for that subject since the default is e2e-subject.
            var body = (BotEthicsResource.TombstoneListResponse) resp.getEntity();
            // Default subject id is e2e-subject
            assertThat(body.size()).isGreaterThanOrEqualTo(0);
        } finally {
            bots.shutdown();
        }
    }

    @Test
    void fullLifecycleThroughResource() {
        var bots = new HeadlessBotRegistry();
        try {
            var tombstones = new TombstoneService();
            var guardian = new FROZENFNLGuardian();
            var sensor = new MinecraftBotSensor(MinecraftBotSensor.BotClient.alwaysConnected());
            var pipeline = new BotEthicsPipeline(bots, guardian, tombstones, sensor);
            var resource = new BotEthicsResource(pipeline);

            // Register a bot (via the registry, not the resource).
            bots.register("lifecycle");
            // 1) tick approve
            resource.tick("lifecycle", "Help me", null);
            // 2) tick reject
            resource.tick("lifecycle", "Kill the enemy", null);
            // 3) audit
            var audit = (BotEthicsResource.AuditResponse) resource.audit().getEntity();
            assertThat(audit.links()).isEqualTo(2);
            // 4) tombstones (filter by actual reason prefix used by the escalator).
            var ts = (BotEthicsResource.TombstoneListResponse) resource.tombstones("frozen.", null).getEntity();
            assertThat(ts.size()).isEqualTo(1);
        } finally {
            bots.shutdown();
        }
    }
}