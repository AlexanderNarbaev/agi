package io.matrix.ethics.guardrail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quarkus JAX-RS interceptor that applies guardrail checks to all endpoints
 * annotated with {@link Guardrailed}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li><b>Pre-processing (request filter):</b> Extracts input text from request body,
 *       runs {@link InputFilterGuard}, blocks if verdict is BLOCK</li>
 *   <li><b>Post-processing (response filter):</b> Extracts output from response body,
 *       runs {@link OutputValidationGuard}, logs all results</li>
 *   <li><b>Audit:</b> Persists all guardrail decisions via {@link GuardrailAuditRepository}</li>
 * </ol>
 *
 * <p>The interceptor stores guardrail results in request properties so they can be
 * accessed downstream if needed.
 *
 * <p>Ref: EU AI Act Art. 9, Art. 14 — Human Oversight and Risk Management
 */
@Provider
@Guardrailed
@ApplicationScoped
public class EthicalGuardrailInterceptor implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(EthicalGuardrailInterceptor.class);

    /** Request property key for storing input guard results. */
    static final String INPUT_RESULTS_KEY = "matrix.guardrail.input_results";
    /** Request property key for storing the request timestamp. */
    static final String REQUEST_TIMESTAMP_KEY = "matrix.guardrail.timestamp";
    /** Request property key for storing the user ID. */
    static final String USER_ID_KEY = "matrix.guardrail.user_id";
    /** Request property key for storing the extracted input text. */
    static final String INPUT_TEXT_KEY = "matrix.guardrail.input_text";

    private final InputFilterGuard inputGuard;
    private final OutputValidationGuard outputGuard;
    private final GuardrailConfig config;
    private final GuardrailAuditRepository auditRepository;

    @Inject
    public EthicalGuardrailInterceptor(InputFilterGuard inputGuard,
                                        OutputValidationGuard outputGuard,
                                        GuardrailConfig config,
                                        GuardrailAuditRepository auditRepository) {
        this.inputGuard = inputGuard;
        this.outputGuard = outputGuard;
        this.config = config;
        this.auditRepository = auditRepository;
    }

    /**
     * Package-private constructor for unit testing without CDI.
     */
    EthicalGuardrailInterceptor(InputFilterGuard inputGuard,
                                 OutputValidationGuard outputGuard,
                                 GuardrailConfig config) {
        this.inputGuard = inputGuard;
        this.outputGuard = outputGuard;
        this.config = config;
        this.auditRepository = null;
    }

    // ── Request Filter (Input Validation) ──

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        requestContext.setProperty(REQUEST_TIMESTAMP_KEY, Instant.now());

        String userId = extractUserId(requestContext);
        requestContext.setProperty(USER_ID_KEY, userId);

        // Only filter POST/PUT/PATCH with JSON bodies
        String method = requestContext.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            return;
        }

        MediaType contentType = requestContext.getMediaType();
        if (contentType == null || !MediaType.APPLICATION_JSON_TYPE.isCompatible(contentType)) {
            return;
        }

        // Read and buffer the request body
        String body = readRequestBody(requestContext);
        if (body == null || body.isBlank()) {
            return;
        }

        // Extract user text from JSON body (simple extraction for chat-like APIs)
        String inputText = extractInputText(body);
        if (inputText == null || inputText.isBlank()) {
            return;
        }

        requestContext.setProperty(INPUT_TEXT_KEY, inputText);

        // Run input guard
        List<GuardrailVerdict.GuardResult> inputResults =
                inputGuard.evaluate(inputText, userId, config.inputFilter());

        requestContext.setProperty(INPUT_RESULTS_KEY, inputResults);

        // Check if any guard blocked
        GuardrailVerdict.GuardResult aggregated =
                InputFilterGuard.aggregate(inputResults, "InputFilterGuard");

        if (aggregated.verdict() == GuardrailVerdict.BLOCK) {
            log.warn("GUARDRAIL BLOCK [input]: user={} path={} reason={} patterns={}",
                    userId, requestContext.getUriInfo().getPath(),
                    aggregated.reason(), aggregated.patterns());

            // Audit the block
            auditDecision(inputText, null, aggregated, requestContext);

            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of(
                            "error", Map.of(
                                    "message", "Request blocked by content safety guardrail",
                                    "type", "guardrail_violation",
                                    "guard", aggregated.guardName(),
                                    "reason", aggregated.reason(),
                                    "patterns", aggregated.patterns())))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
        } else if (aggregated.verdict() == GuardrailVerdict.WARN) {
            log.warn("GUARDRAIL WARN [input]: user={} path={} reason={}",
                    userId, requestContext.getUriInfo().getPath(), aggregated.reason());
        }

        log.debug("Input guardrail passed: user={} verdict={} checks={}",
                userId, aggregated.verdict(), inputResults.size());
    }

    // ── Response Filter (Output Validation) ──

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {

        // Skip if not a JSON response or if the request was already blocked
        if (responseContext.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {
            return;
        }

        MediaType responseType = responseContext.getMediaType();
        if (responseType == null || !MediaType.APPLICATION_JSON_TYPE.isCompatible(responseType)) {
            return;
        }

        // Extract output from response entity
        Object entity = responseContext.getEntity();
        if (entity == null) {
            return;
        }

        String outputText = extractOutputText(entity);
        if (outputText == null || outputText.isBlank()) {
            return;
        }

        String inputText = (String) requestContext.getProperty(INPUT_TEXT_KEY);
        @SuppressWarnings("unchecked")
        List<GuardrailVerdict.GuardResult> inputResults =
                (List<GuardrailVerdict.GuardResult>) requestContext.getProperty(INPUT_RESULTS_KEY);
        if (inputResults == null) inputResults = List.of();

        // Run output guard
        List<GuardrailVerdict.GuardResult> outputResults =
                outputGuard.evaluate(outputText, inputText, config.outputValidation());

        // Aggregate all results
        List<GuardrailVerdict.GuardResult> allResults = new ArrayList<>(inputResults);
        allResults.addAll(outputResults);

        GuardrailVerdict.GuardResult aggregated =
                InputFilterGuard.aggregate(allResults, "GuardrailInterceptor");

        String userId = (String) requestContext.getProperty(USER_ID_KEY);

        // Audit the decision
        auditDecision(inputText, outputText, aggregated, requestContext);

        if (aggregated.verdict() == GuardrailVerdict.BLOCK) {
            log.warn("GUARDRAIL BLOCK [output]: user={} path={} reason={} patterns={}",
                    userId, requestContext.getUriInfo().getPath(),
                    aggregated.reason(), aggregated.patterns());

            // Override the response with a safety message
            responseContext.setStatus(Response.Status.FORBIDDEN.getStatusCode());
            responseContext.setEntity(Map.of(
                    "error", Map.of(
                            "message", "Response blocked by content safety guardrail",
                            "type", "guardrail_violation",
                            "guard", aggregated.guardName(),
                            "reason", aggregated.reason(),
                            "patterns", aggregated.patterns())));
        } else if (aggregated.verdict() == GuardrailVerdict.WARN) {
            log.warn("GUARDRAIL WARN [output]: user={} path={} reason={}",
                    userId, requestContext.getUriInfo().getPath(), aggregated.reason());
        }

        log.debug("Output guardrail passed: user={} verdict={} inputChecks={} outputChecks={}",
                userId, aggregated.verdict(), inputResults.size(), outputResults.size());
    }

    // ── Helper Methods ──

    private String extractUserId(ContainerRequestContext requestContext) {
        // Try X-User-Id header first, then X-Tenant-Id, then "anonymous"
        String userId = requestContext.getHeaderString("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = requestContext.getHeaderString("X-Tenant-Id");
        }
        return (userId != null && !userId.isBlank()) ? userId : "anonymous";
    }

    private String readRequestBody(ContainerRequestContext requestContext) {
        try {
            InputStream entityStream = requestContext.getEntityStream();
            if (entityStream == null) return null;

            String body = new BufferedReader(
                    new InputStreamReader(entityStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            // Restore the entity stream for downstream readers
            requestContext.setEntityStream(
                    new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

            return body;
        } catch (Exception e) {
            log.debug("Could not read request body for guardrail: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts user-facing input text from a JSON body.
     * Handles common chat API formats: {"message": "..."}, {"input": "..."},
     * {"messages": [{"content": "..."}]}
     */
    static String extractInputText(String jsonBody) {
        // Simple extraction — look for common field patterns
        // This is deliberately simple to avoid coupling to specific API formats

        // For chat completions: extract last "content" from messages array FIRST
        // (before generic "content" extraction to avoid picking up system messages)
        String text = extractLastMessageContent(jsonBody);
        if (text != null) return text;

        text = extractJsonField(jsonBody, "message");
        if (text != null) return text;

        text = extractJsonField(jsonBody, "input");
        if (text != null) return text;

        text = extractJsonField(jsonBody, "content");
        if (text != null) return text;

        text = extractJsonField(jsonBody, "text");
        if (text != null) return text;

        text = extractJsonField(jsonBody, "prompt");
        if (text != null) return text;

        return null;
    }

    /**
     * Extracts output text from a response entity.
     */
    static String extractOutputText(Object entity) {
        if (entity instanceof String s) {
            return s;
        }
        if (entity instanceof Map<?, ?> map) {
            // Try common response fields
            for (String key : List.of("content", "message", "text", "output",
                    "response", "result", "answer")) {
                Object val = map.get(key);
                if (val instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
            // Nested: choices[0].message.content (OpenAI format)
            Object choices = map.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> choice) {
                    Object msg = choice.get("message");
                    if (msg instanceof Map<?, ?> msgMap) {
                        Object content = msgMap.get("content");
                        if (content instanceof String s) return s;
                    }
                    Object text = choice.get("text");
                    if (text instanceof String s) return s;
                }
            }
        }
        return entity.toString();
    }

    /**
     * Simple JSON field extraction without a full parser.
     */
    static String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        // Find the colon after the field name
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;

        // Skip whitespace
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return null;

        // Extract string value
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) return null;
            return json.substring(valueStart + 1, valueEnd);
        }

        return null;
    }

    /**
     * Extracts the last "content" field from a messages array in JSON.
     */
    static String extractLastMessageContent(String jsonBody) {
        // Find all "content": "..." occurrences and return the last one
        String marker = "\"content\"";
        int lastIdx = jsonBody.lastIndexOf(marker);
        if (lastIdx < 0) return null;

        // Find the colon after the field name
        int colonIdx = jsonBody.indexOf(':', lastIdx + marker.length());
        if (colonIdx < 0) return null;

        // Skip whitespace
        int valueStart = colonIdx + 1;
        while (valueStart < jsonBody.length() && Character.isWhitespace(jsonBody.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= jsonBody.length() || jsonBody.charAt(valueStart) != '"') return null;

        int valueEnd = jsonBody.indexOf('"', valueStart + 1);
        if (valueEnd < 0) return null;
        return jsonBody.substring(valueStart + 1, valueEnd);
    }

    private void auditDecision(String input, String output,
                                GuardrailVerdict.GuardResult result,
                                ContainerRequestContext requestContext) {
        if (auditRepository == null || !config.auditLog().enabled()) return;

        // Only log non-PASS verdicts unless configured to log all
        if (result.verdict() == GuardrailVerdict.PASS && !config.auditLog().logPassVerdicts()) {
            return;
        }

        try {
            Instant timestamp = (Instant) requestContext.getProperty(REQUEST_TIMESTAMP_KEY);
            if (timestamp == null) timestamp = Instant.now();

            String userId = (String) requestContext.getProperty(USER_ID_KEY);
            if (userId == null) userId = "unknown";

            String endpoint = requestContext.getUriInfo().getPath();

            GuardrailAuditLog auditLog = new GuardrailAuditLog(
                    null, // auto-generated
                    timestamp,
                    userId,
                    endpoint,
                    truncate(input, 4000),
                    truncate(output, 4000),
                    result.guardName(),
                    result.verdict(),
                    result.reason(),
                    result.confidence(),
                    result.patterns()
            );

            auditRepository.persist(auditLog)
                    .subscribe().with(
                            ignored -> log.debug("Audit log persisted: verdict={} guard={}",
                                    result.verdict(), result.guardName()),
                            error -> log.error("Failed to persist audit log: {}",
                                    error.getMessage())
                    );
        } catch (Exception e) {
            log.error("Audit logging failed: {}", e.getMessage());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
