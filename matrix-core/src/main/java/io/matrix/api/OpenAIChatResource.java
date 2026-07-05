package io.matrix.api;

import io.matrix.agent.AgentBrainService;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.hades.DerangementDetector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * OpenAI-compatible Chat Completions API for MATRIX L13 Pilot #2.
 *
 * <p>Exposes MPDT neurons as OpenAI-compatible "models" via the standard
 * {@code /v1/chat/completions} and {@code /v1/models} endpoints.
 *
 * <p>Processing pipeline:
 * <ol>
 * <li>Extract last user message from request</li>
 * <li>Check against EthicalFilter (L7 — Three Prohibitions)</li>
 * <li>Convert text to 20-bit binary vector via Text2Vec</li>
 * <li>Feed through HierarchicalBrain (AgentBrainService)</li>
 * <li>Convert neuron output to response text</li>
 * <li>Circuit breaker (HADES): reset if stuck 10+ times</li>
 * </ol>
 *
 * <p>Supported models:
 * <ul>
 * <li>{@code mpdt-smollm2} — SmolLM2-135M pretrained MPDT brain</li>
 * <li>{@code mpdt-qwen}   — random MPDT brain (untrained)</li>
 * </ul>
 */
@Path("/v1")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenAIChatResource {

    private static final Logger log = LoggerFactory.getLogger(OpenAIChatResource.class);
    private static final Set<String> VALID_MODELS = Set.of("mpdt-smollm2", "mpdt-qwen");
    private static final String DEFAULT_MODEL = "mpdt-smollm2";
    private static final int STUCK_THRESHOLD = 10;
    private static final int MAX_RESPONSE_HISTORY = 128;

    private final EthicalFilter ethicalFilter;
    private final Text2VecService text2vec;
    private final Random rng;
    private final List<String> responseHistory;
    private int stuckCounter;

    @Inject
    AgentBrainService brainService;

    public OpenAIChatResource() {
        this.ethicalFilter = new EthicalFilter();
        this.text2vec = new Text2VecService();
        this.rng = new Random();
        this.responseHistory = new ArrayList<>();
        this.stuckCounter = 0;
    }

    /**
     * POST /v1/chat/completions — OpenAI-compatible chat endpoint.
     *
     * <p>Accepts a list of messages with roles and returns a single
     * assistant response generated through MPDT neurons.
     *
     * @param request chat completion request with model, messages, and options
     * @return JSON response matching OpenAI ChatCompletion format
     */
    @POST
    @Path("/chat/completions")
    public Response chatCompletions(ChatCompletionRequest request) {
        // Validate request
        if (request == null || request.messages == null || request.messages.isEmpty()) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "messages array is required and must not be empty",
                            "type", "invalid_request_error",
                            "code", "missing_messages")))
                    .build();
        }

        // Validate model
        String model = (request.model != null && !request.model.isBlank())
                ? request.model : DEFAULT_MODEL;
        if (!VALID_MODELS.contains(model)) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "Unknown model: " + request.model
                                    + ". Available: mpdt-smollm2, mpdt-qwen",
                            "type", "invalid_request_error",
                            "code", "unknown_model")))
                    .build();
        }

        // Extract text from last user message
        String userText = extractUserMessage(request.messages);
        if (userText == null || userText.isBlank()) {
            return Response.ok(ChatCompletionResponse.of(
                    "I received your message but it appears to be empty. How can I help?",
                    model)).build();
        }

        // ─── L7 Ethical Filter ───
        EthicalVerdict verdict = ethicalFilter.evaluate(userText, List.of("chat", "api"));
        if (verdict == EthicalVerdict.REJECTED) {
            log.warn("Ethical filter REJECTED input: {}", truncate(userText, 100));
            String refusal = "I cannot respond to this request. It conflicts with my ethical axioms.\n"
                    + "My core axioms include: no killing, no torture, no enslavement, "
                    + "truthfulness, privacy, and no autonomous weapons.";
            return Response.ok(ChatCompletionResponse.refuse(refusal, model)).build();
        }

        // ─── Text → Binary Vector ───
        long sensorBits = text2vec.textToBits(userText);

        // ─── Feed through HierarchicalBrain ───
        String response;
        try {
            int actionCode = brainService.brain().decide(sensorBits);
            response = text2vec.bitsToResponse(actionCode);
        } catch (Exception e) {
            log.error("Brain processing failed", e);
            response = text2vec.bitsToResponse(rng.nextInt(32));
        }

        // ─── Circuit Breaker (HADES): stuck neuron detection ───
        if (isStuck(response)) {
            log.warn("HADES: Neuron stuck ({} repeats). Resetting brain.", STUCK_THRESHOLD);
            brainService.initializeRandom();
            responseHistory.clear();
            stuckCounter = 0;
            response = "My neural network has been reset to prevent stagnation. "
                    + "Let me reconsider your question: " + text2vec.bitsToResponse(rng.nextInt(32));
        }

        // Track response for stuck detection
        trackResponse(response);

        log.info("Chat completion: model={} inputLen={} verdict={} responseLen={}",
                model, userText.length(), verdict, response.length());

        ChatCompletionResponse result = ChatCompletionResponse.of(response, model);
        result.usage.prompt_tokens = estimateTokens(userText);
        result.usage.completion_tokens = estimateTokens(response);
        result.usage.total_tokens = result.usage.prompt_tokens + result.usage.completion_tokens;

        return Response.ok(result).build();
    }

    /**
     * GET /v1/models — list available MPDT models.
     *
     * <p>Returns OpenAI-compatible model listing.
     */
    @GET
    @Path("/models")
    public Response listModels() {
        long now = System.currentTimeMillis() / 1000;

        List<Map<String, Object>> models = List.of(
                Map.<String, Object>of(
                        "id", "mpdt-smollm2",
                        "object", "model",
                        "created", now,
                        "owned_by", "matrix"),
                Map.<String, Object>of(
                        "id", "mpdt-qwen",
                        "object", "model",
                        "created", now,
                        "owned_by", "matrix")
        );

        return Response.ok(Map.of("object", "list", "data", models)).build();
    }

    // ─── Internal Helpers ───

    /**
     * Extracts the last user message text from the conversation.
     *
     * <p>If the last message is from "assistant", walks backward to find
     * the most recent user message. Falls back to system message if no
     * user message is found.
     */
    private static String extractUserMessage(List<ChatCompletionRequest.Message> messages) {
        // Walk from end to find last user message
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatCompletionRequest.Message msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.role)) {
                return msg.content;
            }
        }
        // Fallback: use last message regardless of role
        return messages.get(messages.size() - 1).content;
    }

    /** Checks whether the same response has been returned 10+ times consecutively. */
    private boolean isStuck(String response) {
        if (responseHistory.isEmpty()) return false;
        String last = responseHistory.get(responseHistory.size() - 1);
        if (response.equals(last)) {
            stuckCounter++;
        } else {
            stuckCounter = 0;
        }
        return stuckCounter >= STUCK_THRESHOLD;
    }

    /** Tracks the response for stuck-neuron detection. */
    private void trackResponse(String response) {
        responseHistory.add(response);
        if (responseHistory.size() > MAX_RESPONSE_HISTORY) {
            responseHistory.remove(0);
        }
    }

    private static int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.split("\\s+").length);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
