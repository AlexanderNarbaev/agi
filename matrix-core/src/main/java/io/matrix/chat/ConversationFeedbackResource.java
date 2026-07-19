package io.matrix.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * REST surface for collecting human feedback on bot responses.
 *
 * <p>Used by pilots (Telegram, WebSocket, REST clients) to rate answers,
 * producing the quality signal that drives {@link ChatTrainingPairGenerator}.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /v1/chat/feedback} — submit explicit rating (preferred)</li>
 *   <li>{@code POST /v1/chat/feedback/{conversationId}/up} — thumbs-up shortcut</li>
 *   <li>{@code POST /v1/chat/feedback/{conversationId}/down} — thumbs-down shortcut</li>
 *   <li>{@code GET  /v1/chat/feedback/{conversationId}} — read current rating summary</li>
 * </ul>
 *
 * <p>The endpoints are intentionally minimal and stateless. Authentication and
 * rate-limiting are handled by the existing {@code SecurityHeadersFilter} /
 * {@code TenantFilter}.
 */
@Path("/v1/chat/feedback")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConversationFeedbackResource {

    @Inject
    ConversationFeedbackStore store;

    /**
     * Generic feedback submission. Body:
     * <pre>{@code
     * {
     *   "conversationId": "conv-...",
     *   "rating": 0.85,        // 0.0..1.0; &lt;0.3 negative, &gt;0.7 positive
     *   "comment": "great answer",  // optional
     *   "userId": "alice"      // optional
     * }
     * }</pre>
     */
    @POST
    public Response submit(FeedbackRequest req) {
        if (req == null || req.conversationId == null || req.conversationId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error("conversationId is required"))
                    .build();
        }
        if (req.rating < 0.0 || req.rating > 1.0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error("rating must be in [0.0, 1.0]"))
                    .build();
        }
        ConversationFeedback fb = new ConversationFeedback(
                null, req.conversationId, req.rating, req.comment, req.userId, null);
        store.submit(fb);
        return Response.ok(Map.of(
                "status", "accepted",
                "conversationId", req.conversationId,
                "rating", req.rating,
                "cumulativeRating", store.ratingFor(req.conversationId),
                "feedbackCount", store.feedbackCountFor(req.conversationId)
        )).build();
    }

    /** Convenience: thumbs-up = 1.0. */
    @POST
    @Path("/{conversationId}/up")
    public Response thumbsUp(@PathParam("conversationId") String conversationId,
                             @QueryParam("userId") String userId,
                             @QueryParam("comment") String comment) {
        if (conversationId == null || conversationId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error("conversationId is required"))
                    .build();
        }
        store.submit(ConversationFeedback.thumbsUp(conversationId, userId, comment));
        return Response.ok(Map.of(
                "status", "accepted",
                "conversationId", conversationId,
                "rating", ConversationFeedback.RATING_POSITIVE
        )).build();
    }

    /** Convenience: thumbs-down = 0.0. */
    @POST
    @Path("/{conversationId}/down")
    public Response thumbsDown(@PathParam("conversationId") String conversationId,
                               @QueryParam("userId") String userId,
                               @QueryParam("comment") String comment) {
        if (conversationId == null || conversationId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error("conversationId is required"))
                    .build();
        }
        store.submit(ConversationFeedback.thumbsDown(conversationId, userId, comment));
        return Response.ok(Map.of(
                "status", "accepted",
                "conversationId", conversationId,
                "rating", ConversationFeedback.RATING_NEGATIVE
        )).build();
    }

    /** Read current aggregate rating for a conversation. */
    @GET
    @Path("/{conversationId}")
    public Response get(@PathParam("conversationId") String conversationId) {
        return Response.ok(Map.of(
                "conversationId", conversationId,
                "rating", store.ratingFor(conversationId),
                "feedbackCount", store.feedbackCountFor(conversationId),
                "positive", store.hasPositiveFeedback(conversationId),
                "negative", store.hasNegativeFeedback(conversationId)
        )).build();
    }

    private static Map<String, Object> error(String message) {
        return Map.of("error", message);
    }

    /** Request body for {@link #submit}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FeedbackRequest {
        @NotBlank
        public String conversationId;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        public double rating;

        public String comment;
        public String userId;
    }
}