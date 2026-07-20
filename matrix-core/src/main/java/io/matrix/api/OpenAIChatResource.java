package io.matrix.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.agent.AgentBrainService;
import io.matrix.chat.ConversationRecord;
import io.matrix.chat.ConversationRecorder;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.hades.DerangementDetector;
import io.matrix.observability.MatrixMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
 * <li>{@code M.A.T.R.I.X.} — M.A.T.R.I.X. unified neural system (all pretrained neurons merged)</li>
 * </ul>
 */
@Path("/v1")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenAIChatResource {

    private static final Logger log = LoggerFactory.getLogger(OpenAIChatResource.class);
    private static final Set<String> VALID_MODELS = Set.of("M.A.T.R.I.X.");
    private static final String DEFAULT_MODEL = "M.A.T.R.I.X.";
    private static final int STUCK_THRESHOLD = 10;
    private static final int MAX_RESPONSE_HISTORY = 128;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EthicalFilter ethicalFilter;
    private final Text2VecService text2vec;
    private final Random rng;
    private final List<String> responseHistory;
    private int stuckCounter;
    private final MatrixMetrics metrics;
    private AgentBrainService brainService;

    // Optional injection: null-safe in case the chat recorder is not on the classpath
    @Inject
    private ConversationRecorder conversationRecorder;

    public AgentBrainService brainService() { return brainService; }
    public void brainService(AgentBrainService b) { this.brainService = b; }

    @Inject
    OpenAIChatResource(MatrixMetrics metrics, AgentBrainService brainService,
                       Text2VecService text2Vec, EthicalFilter ethicalFilter) {
        this.metrics = metrics;
        this.brainService = brainService;
        this.text2vec = text2Vec;
        this.ethicalFilter = ethicalFilter;
        this.rng = new Random();
        this.responseHistory = new ArrayList<>();
        this.stuckCounter = 0;
    }

    public OpenAIChatResource() {
        this.metrics = null;
        this.brainService = null;
        this.text2vec = new Text2VecService();
        this.ethicalFilter = new EthicalFilter();
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
    public Response chatCompletions(
            @HeaderParam("X-Conversation-Id") String conversationIdHeader,
            ChatCompletionRequest request) {
        if (metrics != null) metrics.recordChatRequest();

        // Validate request
        if (request == null || request.messages == null || request.messages.isEmpty()) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "messages array is required and must not be empty",
                            "type", "invalid_request_error",
                            "code", "missing_messages")))
                    .build();
        }

        // Allocate a conversation id so all messages in this request can be joined
        // later by the training pipeline. Re-uses an explicit header if the client
        // passed one — helpful for multi-turn dialogs that the bot tracks itself.
        String conversationId = conversationIdHeader;
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = ConversationRecord.newConversationId();
        }
        final String finalConvId = conversationId;

        // Record each user message up-front so a server crash mid-generation still
        // preserves the human side of the dialog. Assistant records are added later.
        if (conversationRecorder != null) {
            List<ConversationRecord> toRecord = new ArrayList<>();
            for (ChatCompletionRequest.Message m : request.messages) {
                if ("user".equalsIgnoreCase(m.role)) {
                    toRecord.add(ConversationRecord.user(
                            finalConvId, null, DEFAULT_MODEL, m.content));
                } else if ("system".equalsIgnoreCase(m.role)) {
                    toRecord.add(ConversationRecord.system(finalConvId, m.content));
                }
            }
            if (!toRecord.isEmpty()) {
                conversationRecorder.recordAll(toRecord);
            }
        }

        // Validate model
        String model = (request.model != null && !request.model.isBlank())
                ? request.model : DEFAULT_MODEL;
        if (!VALID_MODELS.contains(model)) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "Unknown model: " + request.model
                                    + ". Available: M.A.T.R.I.X.",
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

        // ─── Streaming SSE support ───
        if (request.stream) {
            return handleStreaming(model, userText);
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

        // ─── Generate response using neural text generator ───
        String response;
        try {
            response = brainService.textGenerator().generate(userText);
            log.info("NeuralTextGenerator: input='{}', output='{}', length={}", 
                userText.substring(0, Math.min(50, userText.length())), 
                response, 
                response != null ? response.length() : 0);
            if (response == null || response.isBlank()) {
                // Fallback when neural text generator produces empty output.
                // Use the brain's decision branch + text2vec templates to produce
                // a contextually varied (if not highly specific) response.
                int actionCode = brainService.brain().decide(sensorBits);
                String template = text2vec.bitsToResponse(sensorBits ^ actionCode);
                response = template;
            }
        } catch (Exception e) {
            log.error("Neural text generation failed", e);
            response = "Neural processing error. Retrying with different pathway.";
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

        // Record the assistant turn so the training pipeline can later join
        // (user, assistant) pairs from the same conversation.
        if (conversationRecorder != null) {
            long assistantSensorBits = text2vec.textToBits(userText);
            int approxTokens = estimateTokens(response);
            conversationRecorder.record(ConversationRecord.assistant(
                    finalConvId, null, model, response,
                    verdict == null ? "APPROVED" : verdict.name(),
                    assistantSensorBits, 0.0, approxTokens));
        }

        ChatCompletionResponse result = ChatCompletionResponse.of(response, model);
        result.usage.prompt_tokens = estimateTokens(userText);
        result.usage.completion_tokens = estimateTokens(response);
        result.usage.total_tokens = result.usage.prompt_tokens + result.usage.completion_tokens;

        return Response.ok(result)
                .header("X-Conversation-Id", finalConvId)
                .build();
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
                        "id", "M.A.T.R.I.X.",
                        "object", "model",
                        "created", now,
                        "owned_by", "matrix")
        );

        return Response.ok(Map.of("object", "list", "data", models)).build();
    }

    /**
     * POST /v1/embeddings — OpenAI-compatible embeddings endpoint.
     *
     * <p>Converts input text to a 20-bit binary vector via Text2Vec,
     * then expands it into a 20-dimensional float embedding vector
     * matching the OpenAI embeddings response format.
     *
     * @param req embedding request with input text
     * @return JSON response with 20-dim float embedding
     */
    @POST
    @Path("/embeddings")
    public Response embeddings(EmbeddingRequest req) {
        if (req == null || req.input == null || req.input.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", Map.of(
                            "message", "input is required",
                            "type", "invalid_request_error",
                            "code", "missing_input")))
                    .build();
        }

        String model = (req.model != null && !req.model.isBlank())
                ? req.model : DEFAULT_MODEL;

        long bits = text2vec.textToBits(req.input);
        List<Float> embedding = new ArrayList<>(20);
        for (int i = 0; i < 20; i++) {
            embedding.add(((bits >> i) & 1) == 1 ? 1.0f : 0.0f);
        }

        EmbeddingResponse resp = EmbeddingResponse.of(embedding, model);
        log.info("Embedding: model={} inputLen={} dims={}", model,
                req.input.length(), embedding.size());

        return Response.ok(resp).build();
    }

    // ─── Internal Helpers ───

    /**
     * Handles streaming chat completions using Server-Sent Events (SSE).
     *
     * <p>Processes the input through MPDT neurons token by token,
     * sending each chunk as an SSE {@code data:} event.
     *
     * @param model    the model identifier
     * @param userText the extracted user message
     * @return SSE streaming response
     */
    private Response handleStreaming(String model, String userText) {
        // Run ethical filter
        EthicalVerdict verdict = ethicalFilter.evaluate(userText, List.of("chat", "api"));
        if (verdict == EthicalVerdict.REJECTED) {
            log.warn("Ethical filter REJECTED streaming input: {}", truncate(userText, 100));
            String refusal = "I cannot respond to this request. It conflicts with my ethical axioms.";
            ChatCompletionResponse chunk = ChatCompletionResponse.delta(refusal, 0);
            return Response.status(403)
                    .header("Content-Type", "text/event-stream")
                    .entity((StreamingOutput) os -> {
                        os.write(("data: " + MAPPER.writeValueAsString(chunk) + "\n\n")
                                .getBytes(StandardCharsets.UTF_8));
                        os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    })
                    .build();
        }

        long sensorBits = text2vec.textToBits(userText);
        int actionCode;
        try {
            actionCode = brainService.brain().decide(sensorBits);
        } catch (Exception e) {
            log.error("Brain processing failed during streaming", e);
            actionCode = rng.nextInt(32);
        }
        String response = text2vec.bitsToResponse(actionCode);

        // Split response into word-level tokens for streaming
        String[] words = response.split(" ");

        return Response.ok((StreamingOutput) os -> {
            for (int i = 0; i < words.length; i++) {
                String token = words[i] + (i < words.length - 1 ? " " : "");
                String chunkData = "data: " + MAPPER.writeValueAsString(
                        ChatCompletionResponse.delta(token, 0)) + "\n\n";
                os.write(chunkData.getBytes(StandardCharsets.UTF_8));
                os.flush();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
        })
                .header("Content-Type", "text/event-stream")
                .build();
    }

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
