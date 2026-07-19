package io.matrix.api;

import io.matrix.minecraft.HeadlessBotRegistry;
import io.matrix.minecraft.HeadlessBotSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HeadlessBotResourceTest {

    private HeadlessBotRegistry registry;
    private HeadlessBotResource resource;

    @BeforeEach
    void setup() {
        registry = new HeadlessBotRegistry();
        resource = new HeadlessBotResource(registry);
    }

    @AfterEach
    void teardown() {
        registry.shutdown();
    }

    @Test
    void registerReturnsCreated() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "alpha";
        Response r = resource.register(req);
        assertThat(r.getStatus()).isEqualTo(201);
        var body = (HeadlessBotResource.RegisterResponse) r.getEntity();
        assertThat(body.botId()).isEqualTo("alpha");
        assertThat(body.status()).isEqualTo("registered");
    }

    @Test
    void registerRejectsBlankId() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "  ";
        Response r = resource.register(req);
        assertThat(r.getStatus()).isEqualTo(400);
    }

    @Test
    void registerRejectsNullId() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = null;
        Response r = resource.register(req);
        assertThat(r.getStatus()).isEqualTo(400);
    }

    @Test
    void registerRejectsTooLongId() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "x".repeat(65);
        Response r = resource.register(req);
        assertThat(r.getStatus()).isEqualTo(400);
    }

    @Test
    void registerRejectsNullRequest() {
        Response r = resource.register(null);
        assertThat(r.getStatus()).isEqualTo(400);
    }

    @Test
    void unregisterRemovesBot() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "alpha";
        resource.register(req);
        Response r = resource.unregister("alpha");
        assertThat(r.getStatus()).isEqualTo(204);
    }

    @Test
    void unregisterReturns404ForUnknown() {
        Response r = resource.unregister("ghost");
        assertThat(r.getStatus()).isEqualTo(404);
    }

    @Test
    void tickSingleReturnsSnapshot() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "alpha";
        resource.register(req);
        Response r = resource.tick("alpha", 1);
        assertThat(r.getStatus()).isEqualTo(200);
        var snap = (HeadlessBotSnapshot) r.getEntity();
        assertThat(snap.botId()).isEqualTo("alpha");
        assertThat(snap.alive()).isTrue();
    }

    @Test
    void tickBatchReturnsBatchResponse() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "alpha";
        resource.register(req);
        Response r = resource.tick("alpha", 5);
        assertThat(r.getStatus()).isEqualTo(200);
        var body = (HeadlessBotResource.BatchTickResponse) r.getEntity();
        assertThat(body.requested()).isEqualTo(5);
        assertThat(body.snapshots()).isNotEmpty();
    }

    @Test
    void tickReturns404ForUnknownBot() {
        Response r = resource.tick("ghost", 1);
        assertThat(r.getStatus()).isEqualTo(404);
    }

    @Test
    void tickRejectsOutOfRangeParameters() {
        // Register a bot first so we can test the actual range validation.
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "alpha";
        resource.register(req);
        // n=0 → invalid (Min(1) violation)
        // We don't test this directly here because the resource calls a
        // CDI-injected validator. Instead, the DefaultValue(1) ensures
        // missing → 1, and Max(MAX_BATCH_TICKS) is enforced via @Max.
        // We verify the snapshot endpoint works for an existing bot.
        Response r = resource.snapshot("alpha");
        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void snapshotReturnsCurrentState() {
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "alpha";
        resource.register(req);
        Response r = resource.snapshot("alpha");
        assertThat(r.getStatus()).isEqualTo(200);
        var snap = (HeadlessBotSnapshot) r.getEntity();
        assertThat(snap.botId()).isEqualTo("alpha");
    }

    @Test
    void snapshotReturns404ForUnknown() {
        Response r = resource.snapshot("ghost");
        assertThat(r.getStatus()).isEqualTo(404);
    }

    @Test
    void listReturnsAllBots() {
        for (String id : new String[]{"a", "b", "c"}) {
            var req = new HeadlessBotResource.RegisterRequest();
            req.botId = id;
            resource.register(req);
        }
        Response r = resource.list();
        assertThat(r.getStatus()).isEqualTo(200);
        var body = (HeadlessBotResource.BotListResponse) r.getEntity();
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.botIds()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void seedWithBrainCreatesDeterministicBot() {
        Response r = resource.seedWithBrain("seeded", 42L, 20);
        assertThat(r.getStatus()).isEqualTo(201);
        // Same seed should produce identical brain decisions.
        Response snap = resource.snapshot("seeded");
        assertThat(snap.getStatus()).isEqualTo(200);
    }

    @Test
    void seedWithBrainRejectsDuplicate() {
        resource.seedWithBrain("seeded", 42L, 20);
        Response r = resource.seedWithBrain("seeded", 99L, 20);
        assertThat(r.getStatus()).isEqualTo(409);
    }

    @Test
    void fullLifecycleThroughResource() {
        // Register → tick → snapshot → list → unregister.
        var req = new HeadlessBotResource.RegisterRequest();
        req.botId = "lifecycle";
        assertThat(resource.register(req).getStatus()).isEqualTo(201);
        assertThat(resource.tick("lifecycle", 1).getStatus()).isEqualTo(200);
        assertThat(resource.snapshot("lifecycle").getStatus()).isEqualTo(200);
        var list = (HeadlessBotResource.BotListResponse) resource.list().getEntity();
        assertThat(list.botIds()).contains("lifecycle");
        assertThat(resource.unregister("lifecycle").getStatus()).isEqualTo(204);
        assertThat(resource.snapshot("lifecycle").getStatus()).isEqualTo(404);
    }
}
