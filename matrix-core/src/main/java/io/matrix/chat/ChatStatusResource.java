package io.matrix.chat;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only observability surface for the chat → training pipeline.
 *
 * <p>Exposes counters and last-run timestamps so operators (and CI smoke tests)
 * can verify the pipeline is alive. Also exposes a {@code POST /train} endpoint
 * that forces an immediate training cycle without waiting for the daemon.
 */
@Path("/v1/chat/status")
@Produces(MediaType.APPLICATION_JSON)
public class ChatStatusResource {

    @Inject
    ConversationRecorder recorder;

    @Inject
    ConversationFeedbackStore feedback;

    @Inject
    ChatTrainingPairGenerator generator;

    @Inject
    ChatDrivenTrainer trainer;

    @GET
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recorder", Map.of(
                "totalRecorded", recorder.totalRecorded(),
                "totalFlushed", recorder.totalFlushed(),
                "hasPending", recorder.hasPending(),
                "currentLogFile", recorder.currentLogFile().toString()
        ));
        body.put("feedback", Map.of(
                "pendingSize", feedback.pendingSizeForTest()
        ));
        body.put("generator", Map.of(
                "totalGenerated", generator.totalGenerated(),
                "knownPairCount", generator.knownPairCount()
        ));
        body.put("trainer", Map.of(
                "cycles", trainer.totalCycles(),
                "pairsFed", trainer.totalPairs(),
                "feedbacksSent", trainer.totalFeedbacks(),
                "onlineTrains", trainer.totalTrains()
        ));
        return body;
    }

    /**
     * Forces an immediate training cycle. Same logic the daemon runs every
     * {@code matrix.chat.trainer-interval-seconds}.
     *
     * <p>Useful for:
     * <ul>
     *   <li>Operational smoke tests</li>
     *   <li>Latency-sensitive flows where waiting 60s for the daemon is too slow</li>
     *   <li>Triggers from CI / GitHub Actions to confirm pipeline end-to-end</li>
     * </ul>
     *
     * <p>Returns the latest counters so the caller can verify what happened.
     */
    @POST
    @Path("/train")
    public Map<String, Object> trainNow() {
        trainer.runCycle();
        return status();
    }

    /**
     * Forces a synchronous flush of the conversation and feedback buffers
     * to disk. Useful right before shutting down or before running a backup.
     */
    @POST
    @Path("/flush")
    public Map<String, Object> flush() {
        int rec = recorder.flush();
        int fb = feedback.flush();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordsFlushed", rec);
        body.put("feedbackFlushed", fb);
        body.put("status", "ok");
        return body;
    }
}