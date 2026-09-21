package io.matrix.api;

import io.matrix.minecraft.HeadlessBotRegistry;
import io.matrix.minecraft.HeadlessBotSnapshot;
import io.matrix.minecraft.NeuralBrain;
import io.matrix.minecraft.BlockWorld;
import io.matrix.neuron.DecisionTree;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * HTTP/REST endpoint for headless Minecraft bots — exposes
 * {@link HeadlessBotRegistry} over JSON.
 *
 * <p>Endpoints:
 * <pre>
 *   POST   /api/v1/bot/headless                       — register a new bot
 *   POST   /api/v1/bot/headless/{id}/tick            — single tick
 *   POST   /api/v1/bot/headless/{id}/tick?n=10       — batch ticks
 *   GET    /api/v1/bot/headless/{id}                 — snapshot
 *   GET    /api/v1/bot/headless                      — list all bots
 *   DELETE /api/v1/bot/headless/{id}                 — remove a bot
 *   POST   /api/v1/bot/headless/seed/{id}?k=N&seed=S — seed with a random brain (deterministic)
 * </pre>
 *
 * <p>Design choices:
 * <ul>
 *   <li>One singleton {@link HeadlessBotRegistry} per resource instance
 *       (CDI-managed, app-scoped).</li>
 *   <li>Bot ids are alphanumeric (validated) so they can be embedded
 *       in URLs safely.</li>
 *   <li>Tick parameters are bounded to prevent DoS.</li>
 *   <li>Returned snapshots are pure records (no live state).</li>
 * </ul>
 *
 * <p>Ref: L25 §3.3, L13 §4 (Minecraft pilot).
 */
@Path("/api/v1/bot/headless")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HeadlessBotResource {

    private static final Logger log = LoggerFactory.getLogger(HeadlessBotResource.class);

    /** Max ticks allowed in a single batch request (DoS protection). */
    private static final int MAX_BATCH_TICKS = 10_000;
    /** Max bot id length. */
    private static final int MAX_BOT_ID_LENGTH = 64;
    /** Shared random for default brain generation. */
    private final Random sharedRng = new Random();

    /** Optional pre-existing registry (for tests that inject a custom one). */
    private final HeadlessBotRegistry registry;

    @Inject
    public HeadlessBotResource() {
        this(new HeadlessBotRegistry());
    }

    /** Test-injectable constructor. */
    public HeadlessBotResource(HeadlessBotRegistry registry) {
        this.registry = registry;
    }

    // ── Lifecycle ──

    @POST
    public Response register(@Valid RegisterRequest req) {
        if (req == null || req.botId == null || req.botId.isBlank()) {
            return Response.status(400)
                    .entity(errorResponse("botId required"))
                    .build();
        }
        if (req.botId.length() > MAX_BOT_ID_LENGTH) {
            return Response.status(400)
                    .entity(errorResponse("botId too long (max " + MAX_BOT_ID_LENGTH + ")"))
                    .build();
        }
        try {
            String id = registry.register(req.botId);
            return Response.status(201)
                    .entity(new RegisterResponse(id, "registered"))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(503).entity(errorResponse(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response unregister(@PathParam("id") String id) {
        boolean removed = registry.unregister(id);
        if (!removed) {
            return Response.status(404).entity(errorResponse("bot not found: " + id)).build();
        }
        return Response.noContent().build();
    }

    // ── Tick / Snapshot ──

    @POST
    @Path("/{id}/tick")
    public Response tick(@PathParam("id") String id,
                          @QueryParam("n") @DefaultValue("1") @Min(1) @Max(MAX_BATCH_TICKS) int n) {
        if (n == 1) {
            Optional<HeadlessBotSnapshot> snap = registry.tickOnce(id);
            if (snap.isEmpty()) {
                return Response.status(404).entity(errorResponse("bot not found: " + id)).build();
            }
            return Response.ok(snap.get()).build();
        }
        List<HeadlessBotSnapshot> snaps = registry.runBatch(id, n);
        if (snaps.isEmpty()) {
            return Response.status(404).entity(errorResponse("bot not found: " + id)).build();
        }
        return Response.ok(new BatchTickResponse(id, n, snaps)).build();
    }

    @GET
    @Path("/{id}")
    public Response snapshot(@PathParam("id") String id) {
        Optional<HeadlessBotSnapshot> snap = registry.snapshot(id);
        if (snap.isEmpty()) {
            return Response.status(404).entity(errorResponse("bot not found: " + id)).build();
        }
        return Response.ok(snap.get()).build();
    }

    @GET
    public Response list() {
        return Response.ok(new BotListResponse(registry.size(), registry.botIds())).build();
    }

    // ── Custom brain seeding (useful for tests) ──

    /**
     * Seeds a bot with a deterministic neural brain — all 5 decision trees
     * are randomly generated from a fixed seed so two clients with the
     * same seed see the same behaviour. Useful for reproducible tests.
     */
    @POST
    @Path("/seed/{id}")
    public Response seedWithBrain(@PathParam("id") String id,
                                  @QueryParam("seed") long seed,
                                  @QueryParam("k") @DefaultValue("20") @Min(2) @Max(20) int k) {
        if (registry.botIds().contains(id)) {
            return Response.status(409).entity(errorResponse("bot already exists")).build();
        }
        Random rng = new Random(seed);
        NeuralBrain brain = new NeuralBrain(
                DecisionTree.random(k, 10, rng),
                DecisionTree.random(k, 8, rng),
                DecisionTree.random(k, 8, rng),
                DecisionTree.random(k, 6, rng),
                DecisionTree.random(k, 6, rng));
        BlockWorld world = new BlockWorld(20, 20, new Random(seed + 1));
        registry.register(id, world, brain);
        return Response.status(201)
                .entity(new RegisterResponse(id, "seeded"))
                .build();
    }

    // ── DTOs ──

    public static class RegisterRequest {
        @NotBlank
        @Size(max = MAX_BOT_ID_LENGTH)
        public String botId;
    }

    public record RegisterResponse(String botId, String status) {}

    public record BatchTickResponse(String botId, int requested, List<HeadlessBotSnapshot> snapshots) {}

    public record BotListResponse(int size, java.util.Collection<String> botIds) {}

    public record ErrorResponse(String error) {}

    private static ErrorResponse errorResponse(String msg) {
        return new ErrorResponse(msg);
    }
}
