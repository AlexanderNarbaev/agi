package io.matrix.brain;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * W514 — Brain Quarkus Resource.
 * 
 * JAX-RS resource that exposes the brain via the standard Quarkus HTTP server.
 * 
 * Endpoints:
 * - POST /v1/brain/chat — send message, get response
 * - GET /v1/brain/health — health check
 * - POST /v1/brain/learn — trigger learning
 * - GET /v1/brain/stats — brain statistics
 * 
 * Uses LlmBrainLoopRag for real Qwen inference with RAG.
 * Anti-hallucination via ConfidenceFilter.
 */
@Path("/v1/brain")
@ApplicationScoped
public class BrainQuarkusResource {
    
    private static final Logger log = LoggerFactory.getLogger(BrainQuarkusResource.class);
    
    @ConfigProperty(name = "matrix.brain.enabled", defaultValue = "true")
    boolean enabled;
    
    @ConfigProperty(name = "matrix.brain.confidence-threshold", defaultValue = "0.3")
    double confidenceThreshold;
    
    private volatile LlmBrainLoopRag brain;
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalLearned = new AtomicLong(0);
    private final ConfidenceFilter filter = new ConfidenceFilter(0.3);
    
    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            log.info("BrainQuarkusResource: disabled");
            return;
        }
        try {
            brain = new LlmBrainLoopRag("models/onnx/qwen05b", new io.matrix.knowledge.SimpleKnowledgeBase());
            log.info("BrainQuarkusResource: brain initialized");
        } catch (Exception e) {
            log.error("BrainQuarkusResource: failed to initialize brain", e);
        }
    }
    
    @POST
    @Path("/chat")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public java.util.Map<String, Object> chat(java.util.Map<String, String> request) {
        if (brain == null) {
            return java.util.Map.of("error", "brain not initialized");
        }
        
        String message = request.get("message");
        if (message == null || message.isEmpty()) {
            return java.util.Map.of("error", "message required");
        }
        
        totalQueries.incrementAndGet();
        
        BrainCycle.CycleResult result = brain.cycle(message);
        
        // Anti-hallucination check
        String filtered = filter.filter(result.reply(), result.confidence());
        if (filtered == null) {
            return java.util.Map.of(
                "reply", filter.rejectionMessage(result.confidence()),
                "confidence", result.confidence(),
                "duration_ms", result.durationMs(),
                "accepted", false
            );
        }
        
        return java.util.Map.of(
            "reply", result.reply(),
            "confidence", result.confidence(),
            "duration_ms", result.durationMs(),
            "accepted", true
        );
    }
    
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public java.util.Map<String, Object> health() {
        return java.util.Map.of(
            "status", brain != null ? "ok" : "not initialized",
            "enabled", enabled,
            "totalQueries", totalQueries.get(),
            "totalLearned", totalLearned.get()
        );
    }
    
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public java.util.Map<String, Object> stats() {
        return java.util.Map.of(
            "totalQueries", totalQueries.get(),
            "totalLearned", totalLearned.get(),
            "confidenceThreshold", filter.getMinConfidence()
        );
    }
    
    @POST
    @Path("/learn")
    @Produces(MediaType.APPLICATION_JSON)
    public java.util.Map<String, Object> learn() {
        if (brain == null) {
            return java.util.Map.of("error", "brain not initialized");
        }
        
        try {
            // Use ConversationLearner to learn from recorded conversations
            io.matrix.learning.ConversationLearner learner = 
                new io.matrix.learning.ConversationLearner(
                    brain.getKnowledgeBase(), 
                    java.nio.file.Paths.get("data/conversations")
                );
            int learned = learner.learnAll();
            totalLearned.addAndGet(learned);
            return java.util.Map.of(
                "learned", learned,
                "kb_size", brain.getKnowledgeBase().size(),
                "totalLearned", totalLearned.get()
            );
        } catch (Exception e) {
            return java.util.Map.of("error", e.getMessage());
        }
    }
}
