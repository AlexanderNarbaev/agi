package io.matrix.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.chat.feedback.FeedbackAggregator;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Converts raw {@link ConversationRecord}s into (input → response) training
 * pairs suitable for {@code MatrixTrainingEngine}.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Group records by {@code conversationId} → ordered dialog threads</li>
 *   <li>For each thread, slide a window: pair (last USER turn, next ASSISTANT turn)</li>
 *   <li>Apply filters: ethical verdict must be APPROVED, content length bounds,
 *       feedback rating must be positive (or absent / neutral)</li>
 *   <li>Append to {@code models/training_data/auto_generated.jsonl}</li>
 * </ol>
 *
 * <h2>Output format</h2>
 * <pre>{@code
 * {
 *   "input":  "What is gravity?",
 *   "output": "Gravity is the force that attracts objects with mass...",
 *   "source": "conversation",
 *   "conversationId": "conv-...",
 *   "rating": 0.9,
 *   "feedbackCount": 3,
 *   "ethicalVerdict": "APPROVED",
 *   "timestamp": "2026-07-19T..."
 * }
 * }</pre>
 *
 * <p>The generator is idempotent: each (conversationId, userIndex) pair
 * is written at most once per process lifetime. The set of "seen" pairs
 * is rebuilt from the output file on every run.
 */
@ApplicationScoped
public class ChatTrainingPairGenerator {

    private static final Logger log = LoggerFactory.getLogger(ChatTrainingPairGenerator.class);
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @ConfigProperty(name = "matrix.chat.output-file",
            defaultValue = "models/training_data/auto_generated.jsonl")
    String outputFile;

    @ConfigProperty(name = "matrix.chat.min-input-length", defaultValue = "4")
    int minInputLength;

    @ConfigProperty(name = "matrix.chat.max-input-length", defaultValue = "2000")
    int maxInputLength;

    @ConfigProperty(name = "matrix.chat.min-output-length", defaultValue = "8")
    int minOutputLength;

    @ConfigProperty(name = "matrix.chat.max-output-length", defaultValue = "4000")
    int maxOutputLength;

    @ConfigProperty(name = "matrix.chat.required-rating-floor", defaultValue = "0.35")
    double requiredRatingFloor;

    @ConfigProperty(name = "matrix.chat.required-positive-feedback", defaultValue = "false")
    boolean requirePositiveFeedback;

    @Inject
    ConversationRecorder recorder;

    @Inject
    FeedbackAggregator feedback;

    private final AtomicLong totalGenerated = new AtomicLong();
    private final Map<String, Boolean> seenPairs = new HashMap<>();

    void onStart(@Observes StartupEvent event) {
        try {
            Path parent = Path.of(outputFile).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            loadSeenPairs();
            log.info("ChatTrainingPairGenerator ready. output={}, seen={}, minRating={}",
                    outputFile, seenPairs.size(), requiredRatingFloor);
        } catch (IOException e) {
            log.error("ChatTrainingPairGenerator init failed: {}", e.getMessage());
        }
    }

    /**
     * Reads recorded conversations, converts them to training pairs,
     * appends new pairs to the output file. Returns the number of pairs written.
     *
     * <p>Safe to call repeatedly; only new pairs are written.
     */
    public synchronized int generateAndAppend() {
        if (recorder == null) {
            return 0;
        }
        List<ConversationRecord> records = recorder.readAll(10_000);
        if (records.isEmpty()) {
            return 0;
        }
        Map<String, List<ConversationRecord>> threads = groupByConversation(records);
        List<TrainingPair> pairs = new ArrayList<>();
        for (Map.Entry<String, List<ConversationRecord>> entry : threads.entrySet()) {
            List<ConversationRecord> ordered = entry.getValue();
            ordered.sort(Comparator.comparing(ConversationRecord::timestamp));
            extractPairs(ordered).forEach(p -> {
                if (accept(p)) pairs.add(p);
            });
        }
        return writeNew(pairs);
    }

    /**
     * Generates training pairs from a provided list (used by tests + the
     * on-demand background tick).
     */
    public List<TrainingPair> generateFromRecords(List<ConversationRecord> records) {
        Map<String, List<ConversationRecord>> threads = groupByConversation(records);
        List<TrainingPair> pairs = new ArrayList<>();
        for (Map.Entry<String, List<ConversationRecord>> entry : threads.entrySet()) {
            List<ConversationRecord> ordered = entry.getValue();
            ordered.sort(Comparator.comparing(ConversationRecord::timestamp));
            extractPairs(ordered).forEach(p -> {
                if (accept(p)) pairs.add(p);
            });
        }
        return List.copyOf(pairs);
    }

    /** Number of training pairs appended since process start. */
    public long totalGenerated() {
        return totalGenerated.get();
    }

    /** Number of unique pairs already in the output file. */
    public int knownPairCount() {
        return seenPairs.size();
    }

    /** Builds the (input, output, …) extractors from one ordered thread. */
    private List<TrainingPair> extractPairs(List<ConversationRecord> thread) {
        List<TrainingPair> out = new ArrayList<>();
        String convId = thread.get(0).conversationId();
        // Two strategies: (a) single-turn pairing; (b) sliding-window with prior context.
        String priorContext = null;
        int userIndex = 0;
        for (int i = 0; i < thread.size(); i++) {
            ConversationRecord r = thread.get(i);
            if ("user".equals(r.role())) {
                String input = priorContext == null
                        ? r.content()
                        : priorContext + "\nUser: " + r.content();
                // Find next assistant turn
                for (int j = i + 1; j < thread.size(); j++) {
                    ConversationRecord next = thread.get(j);
                    if ("assistant".equals(next.role())) {
                        TrainingPair p = new TrainingPair(
                                convId,
                                userIndex,
                                input,
                                next.content(),
                                next.ethicalVerdict(),
                                next.timestamp());
                        out.add(p);
                        priorContext = r.content() + "\n" + next.content();
                        break;
                    } else if ("user".equals(next.role())) {
                        // Multiple user turns in a row — bail out of context propagation
                        priorContext = r.content();
                        break;
                    }
                }
                userIndex++;
            } else if ("system".equals(r.role())) {
                priorContext = "[system] " + r.content();
            }
        }
        return out;
    }

    private boolean accept(TrainingPair p) {
        if (p.input == null || p.output == null) return false;
        if (p.input.length() < minInputLength || p.input.length() > maxInputLength) return false;
        if (p.output.length() < minOutputLength || p.output.length() > maxOutputLength) return false;
        // Ethical gate — anything not explicitly APPROVED is rejected.
        if (p.ethicalVerdict != null && !p.ethicalVerdict.equals("APPROVED")) return false;
        // Feedback gate
        double rating = feedback.ratingFor(p.conversationId);
        if (rating < requiredRatingFloor) return false;
        if (requirePositiveFeedback && !feedback.hasPositiveFeedback(p.conversationId)) {
            return false;
        }
        // Negative feedback hard-blocks the conversation from training.
        if (feedback.hasNegativeFeedback(p.conversationId)) return false;
        return true;
    }

    private Map<String, List<ConversationRecord>> groupByConversation(List<ConversationRecord> records) {
        Map<String, List<ConversationRecord>> grouped = new LinkedHashMap<>();
        for (ConversationRecord r : records) {
            if (r.conversationId() == null) continue;
            grouped.computeIfAbsent(r.conversationId(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    private int writeNew(List<TrainingPair> pairs) {
        if (pairs.isEmpty()) {
            return 0;
        }
        Path out = Path.of(outputFile);
        int written = 0;
        try (var w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (TrainingPair p : pairs) {
                String key = p.conversationId() + ":" + p.userIndex();
                if (seenPairs.containsKey(key)) continue;
                seenPairs.put(key, Boolean.TRUE);

                ObjectNode node = JSON.createObjectNode();
                node.put("input", p.input());
                node.put("output", p.output());
                node.put("source", "conversation");
                node.put("conversationId", p.conversationId());
                node.put("userIndex", p.userIndex());
                node.put("rating", feedback.ratingFor(p.conversationId()));
                node.put("feedbackCount", feedback.feedbackCountFor(p.conversationId()));
                node.put("ethicalVerdict", p.ethicalVerdict() == null ? "APPROVED" : p.ethicalVerdict());
                if (p.timestamp() != null) {
                    node.put("timestamp", p.timestamp().toString());
                }
                w.write(JSON.writeValueAsString(node));
                w.newLine();
                written++;
            }
            totalGenerated.addAndGet(written);
            if (written > 0) {
                log.info("ChatTrainingPairGenerator wrote {} new pairs → {}", written, out);
            }
            return written;
        } catch (IOException e) {
            log.error("Failed to write training pairs: {}", e.getMessage());
            return 0;
        }
    }

    private void loadSeenPairs() throws IOException {
        Path out = Path.of(outputFile);
        if (!Files.exists(out)) return;
        try (var lines = Files.lines(out)) {
            lines.forEach(line -> {
                if (line.isBlank()) return;
                try {
                    var node = JSON.readTree(line);
                    String convId = node.path("conversationId").asText(null);
                    int idx = node.path("userIndex").asInt(-1);
                    if (convId != null && idx >= 0) {
                        seenPairs.put(convId + ":" + idx, Boolean.TRUE);
                    }
                } catch (IOException ignored) {
                    // Skip malformed lines silently on startup
                }
            });
        }
        log.info("Loaded {} known training pairs from {}", seenPairs.size(), out);
    }

    /** One input/output pair extracted from a real conversation. */
    public record TrainingPair(
            String conversationId,
            int userIndex,
            String input,
            String output,
            String ethicalVerdict,
            Instant timestamp
    ) {}
}