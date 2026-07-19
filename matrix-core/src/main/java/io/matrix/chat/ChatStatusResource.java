package io.matrix.chat;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Read-only observability surface for the chat → training pipeline.
 *
 * <p>Exposes counters and last-run timestamps so operators (and CI smoke tests)
 * can verify the pipeline is alive.
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
        return Map.of(
                "recorder", Map.of(
                        "totalRecorded", recorder.totalRecorded(),
                        "totalFlushed", recorder.totalFlushed(),
                        "hasPending", recorder.hasPending(),
                        "currentLogFile", recorder.currentLogFile().toString()
                ),
                "feedback", Map.of(
                        "pendingSize", feedback.pendingSizeForTest()
                ),
                "generator", Map.of(
                        "totalGenerated", generator.totalGenerated(),
                        "knownPairCount", generator.knownPairCount()
                ),
                "trainer", Map.of(
                        "cycles", trainer.totalCycles(),
                        "pairsFed", trainer.totalPairs(),
                        "feedbacksSent", trainer.totalFeedbacks(),
                        "onlineTrains", trainer.totalTrains()
                )
        );
    }
}