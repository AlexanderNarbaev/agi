package io.matrix.quality.feedback;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WAVE T-10 — User Feedback Collector.
 *
 * Captures bug reports and feature suggestions from end users.
 * T-10 stores in-memory; T-10.5 wires to GitHub Issues via API.
 */
public final class FeedbackCollector {

    public enum Type { BUG, FEATURE_REQUEST, COMPLIANCE_QUESTION, OTHER }

    public record Feedback(
        String id,
        Type type,
        String userId,
        String title,
        String description,
        Instant submittedAt,
        Status status
    ) {
        public enum Status { OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, WONTFIX }
    }

    private final List<Feedback> entries = new CopyOnWriteArrayList<>();

    public Feedback submit(Type type, String userId, String title, String description) {
        Feedback f = new Feedback(
            "fb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            type, userId, title, description,
            Instant.now(), Feedback.Status.OPEN
        );
        entries.add(f);
        return f;
    }

    public List<Feedback> all() {
        return new ArrayList<>(entries);
    }

    public List<Feedback> byStatus(Feedback.Status status) {
        return entries.stream().filter(e -> e.status() == status).toList();
    }

    public int openCount() {
        return (int) entries.stream().filter(e -> e.status() == Feedback.Status.OPEN).count();
    }

    /** Save feedback to a JSON file for persistence. */
    public void saveTo(Path file) throws java.io.IOException {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), entries);
    }

    /** Load feedback from a JSON file. */
    public void loadFrom(Path file) throws java.io.IOException {
        if (!java.nio.file.Files.exists(file)) return;
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        Feedback[] loaded = mapper.readValue(file.toFile(), Feedback[].class);
        entries.clear();
        entries.addAll(java.util.Arrays.asList(loaded));
    }
}
