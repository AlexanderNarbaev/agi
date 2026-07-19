package io.matrix.api;

import io.matrix.ethics.FROZENFNLGuardian;
import io.matrix.ethics.FROZENGDPREscalator;
import io.matrix.io.MinecraftBotSensor;
import io.matrix.minecraft.HeadlessBotRegistry;
import io.matrix.minecraft.HeadlessBotSnapshot;
import io.matrix.privacy.Tombstone;
import io.matrix.privacy.TombstoneService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;

/**
 * HTTP/REST endpoint for the {@link BotEthicsPipeline} — drives the
 * bot tick → FROZEN FNL → audit → GDPR pipeline from HTTP requests.
 *
 * <p>Endpoints:
 * <pre>
 *   POST /api/v1/bot/ethics/{botId}/tick?action=...
 *        — Run a single bot tick with a specific action text
 *
 *   POST /api/v1/bot/ethics/{botId}/tick
 *        body: { "actionText": "..." }
 *        — Same as above but with body-encoded action
 *
 *   GET  /api/v1/bot/ethics/audit
 *        — Audit summary (links, approvals, rejections, valid)
 *
 *   GET  /api/v1/bot/ethics/{botId}
 *        — Snapshot of the bot's current state
 *
 *   GET  /api/v1/bot/ethics/tombstones?reason=gdpr.
 *        — List tombstones (optionally filtered by reason prefix)
 * </pre>
 *
 * <p>Singletons are CDI-managed (app-scoped) — one pipeline per process.
 * For tests, use the {@code @Inject} constructors to swap in custom
 * {@link HeadlessBotRegistry} / {@link TombstoneService} etc.
 *
 * <p>Ref: L25 §3.3, Wave 22-C.
 */
@Path("/api/v1/bot/ethics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BotEthicsResource {

    /** Optional — for CDI-injected pipeline. */
    private final BotEthicsPipeline pipeline;

    @Inject
    public BotEthicsResource() {
        this(buildDefaultPipeline());
    }

    /** Test-injectable constructor. */
    public BotEthicsResource(BotEthicsPipeline pipeline) {
        this.pipeline = pipeline;
    }

    private static BotEthicsPipeline buildDefaultPipeline() {
        HeadlessBotRegistry registry = new HeadlessBotRegistry();
        TombstoneService tombstones = new TombstoneService();
        FROZENFNLGuardian guardian = new FROZENFNLGuardian();
        guardian.attestNow();
        MinecraftBotSensor sensor = new MinecraftBotSensor(
                MinecraftBotSensor.BotClient.alwaysConnected());
        return new BotEthicsPipeline(registry, guardian, tombstones, sensor);
    }

    // ── Tick ──

    @POST
    @Path("/{botId}/tick")
    public Response tick(@PathParam("botId") String botId,
                          @QueryParam("action") String action,
                          @Valid TickRequest body) {
        String actionText = (body != null && body.actionText != null && !body.actionText.isBlank())
                ? body.actionText
                : (action == null || action.isBlank() ? "Help me navigate" : action);
        Optional<HeadlessBotSnapshot> snap = pipeline.tickBot(botId, actionText);
        if (snap.isEmpty()) {
            return Response.status(404)
                    .entity(new ErrorResponse("bot not found: " + botId))
                    .build();
        }
        return Response.ok(new TickResponse(
                botId, actionText,
                pipeline.totalApprovals(), pipeline.totalRejections(),
                snap.get())).build();
    }

    // ── Audit ──

    @GET
    @Path("/audit")
    public Response audit() {
        return Response.ok(new AuditResponse(
                pipeline.sharedGuardian().chain().size(),
                pipeline.sharedGuardian().verifyAuditTrail(),
                pipeline.totalApprovals(),
                pipeline.totalRejections())).build();
    }

    // ── Snapshot ──

    @GET
    @Path("/{botId}")
    public Response snapshot(@PathParam("botId") String botId) {
        Optional<HeadlessBotSnapshot> rawTick = pipeline.tickBotRaw(botId);
        if (rawTick.isEmpty()) {
            return Response.status(404)
                    .entity(new ErrorResponse("bot not found: " + botId))
                    .build();
        }
        return Response.ok(rawTick.get()).build();
    }

    // ── Tombstones ──

    @GET
    @Path("/tombstones")
    public Response tombstones(@QueryParam("reason") String reasonPrefix,
                                @QueryParam("subject") String subjectId) {
        List<Tombstone> list;
        if (reasonPrefix != null && !reasonPrefix.isBlank()) {
            list = pipeline.escalator().tombstones().filterByReason(reasonPrefix);
        } else if (subjectId != null && !subjectId.isBlank()) {
            list = pipeline.escalator().tombstones().filterBySubject(subjectId);
        } else {
            list = pipeline.escalator().tombstones().all();
        }
        return Response.ok(new TombstoneListResponse(list.size(), list)).build();
    }

    // ── DTOs ──

    public static class TickRequest {
        @NotBlank
        public String actionText;
    }

    public record TickResponse(
            String botId,
            String actionText,
            long approvals,
            long rejections,
            HeadlessBotSnapshot snapshot) {}

    public record AuditResponse(
            int links,
            boolean valid,
            long approvals,
            long rejections) {}

    public record TombstoneListResponse(int size, List<Tombstone> tombstones) {}

    public record ErrorResponse(String error) {}
}