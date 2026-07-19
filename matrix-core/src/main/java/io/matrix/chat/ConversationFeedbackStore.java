package io.matrix.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Append-only store for user-supplied conversation feedback (thumbs up/down).
 *
 * <p>Persists to a daily NDJSON file in the same {@code data/conversations/}
 * directory as the main conversation log, with the {@code feedback-} prefix.
 *
 * <p>Maintains an in-memory map of {@code conversationId → latest rating}
 * so the training pipeline can join conversations to feedback without
 * re-reading the log on every iteration.
 */
@ApplicationScoped
public class ConversationFeedbackStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationFeedbackStore.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @ConfigProperty(name = "matrix.chat.storage-dir", defaultValue = "data/conversations")
    String storageDir;

    private final ConcurrentLinkedQueue<ConversationFeedback> pending = new ConcurrentLinkedQueue<>();
    private final Map<String, Double> latestRating = new ConcurrentHashMap<>();
    private final Map<String, Long> feedbackCount = new ConcurrentHashMap<>();

    void onStart(@Observes StartupEvent event) {
        try {
            Files.createDirectories(Path.of(storageDir));
        } catch (IOException e) {
            log.error("ConversationFeedbackStore init failed: {}", e.getMessage());
        }
    }

    /** Enqueues a feedback entry; flushes synchronously if the queue is large. */
    public void submit(ConversationFeedback feedback) {
        if (feedback == null) return;
        latestRating.merge(feedback.conversationId(), feedback.rating(),
                (oldV, newV) -> newV); // latest rating wins
        feedbackCount.merge(feedback.conversationId(), 1L, Long::sum);
        pending.offer(feedback);
        if (pending.size() > 64) {
            flush();
        }
    }

    /** Latest rating (0.0–1.0) recorded for the given conversation, or 0.5 default. */
    public double ratingFor(String conversationId) {
        return latestRating.getOrDefault(conversationId, ConversationFeedback.RATING_NEUTRAL);
    }

    /** Number of feedback entries recorded for the conversation so far. */
    public long feedbackCountFor(String conversationId) {
        return feedbackCount.getOrDefault(conversationId, 0L);
    }

    /** True if at least one positive feedback has been recorded for the conversation. */
    public boolean hasPositiveFeedback(String conversationId) {
        Double r = latestRating.get(conversationId);
        return r != null && r >= 0.7;
    }

    /** True if at least one negative feedback has been recorded for the conversation. */
    public boolean hasNegativeFeedback(String conversationId) {
        Double r = latestRating.get(conversationId);
        return r != null && r <= 0.3;
    }

    /** Synchronously persists all pending feedback entries. */
    public synchronized int flush() {
        if (pending.isEmpty()) return 0;
        String today = DATE_FMT.format(LocalDate.now());
        Path file = Path.of(storageDir, "feedback-" + today + ".ndjson");
        int written = 0;
        try {
            try (var w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                ConversationFeedback f;
                while ((f = pending.poll()) != null) {
                    w.write(JSON.writeValueAsString(f));
                    w.newLine();
                    written++;
                }
            }
            if (written > 0 && log.isDebugEnabled()) {
                log.debug("Flushed {} feedback records", written);
            }
            return written;
        } catch (IOException e) {
            log.error("Feedback flush failed: {}", e.getMessage());
            return 0;
        }
    }

    void onStop(@Observes io.quarkus.runtime.ShutdownEvent event) {
        flush();
    }

    // Test seam
    int pendingSizeForTest() {
        return pending.size();
    }
}