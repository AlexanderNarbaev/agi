package io.matrix.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Phase Z (RUN 405) — GitHub release webhook.
 * Posts a notification to a configured webhook URL on tag push.
 * Pure HTTP client (no additional dependencies).
 */
public final class GitHubWebhook {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String webhookUrl;
    private final HttpClient client;

    public GitHubWebhook(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl blank");
        }
        if (!webhookUrl.startsWith("http://") && !webhookUrl.startsWith("https://")) {
            throw new IllegalArgumentException("must be http(s) URL");
        }
        this.webhookUrl = webhookUrl;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public static GitHubWebhook fromEnv() {
        String url = System.getenv("MATRIX_GITHUB_WEBHOOK");
        if (url == null || url.isBlank()) return null;
        return new GitHubWebhook(url);
    }

    public boolean isConfigured() {
        return webhookUrl != null;
    }

    public record ReleaseEvent(
            String tagName,
            String releaseName,
            String body,
            String htmlUrl,
            LocalDateTime createdAt
    ) {}

    /**
     * Send release notification. Returns true on success.
     */
    public boolean notifyRelease(ReleaseEvent event) {
        if (event == null) return false;
        try {
            ObjectMapper mapper = new ObjectMapper();
            var payload = mapper.createObjectNode();
            payload.put("event", "release");
            payload.put("tag", event.tagName());
            payload.put("name", event.releaseName());
            payload.put("body", event.body() == null ? "" : event.body());
            payload.put("url", event.htmlUrl() == null ? "" : event.htmlUrl());
            payload.put("timestamp", event.createdAt() == null
                    ? LocalDateTime.now().toString()
                    : event.createdAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            payload.put("source", "matrix-core");
            String json = mapper.writeValueAsString(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Parse a GitHub release webhook payload (JSON). */
    public static ReleaseEvent parsePayload(String json) {
        if (json == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            String tag = root.path("release").path("tag_name").asText();
            String name = root.path("release").path("name").asText();
            String body = root.path("release").path("body").asText();
            String url = root.path("release").path("html_url").asText();
            String created = root.path("release").path("created_at").asText();
            LocalDateTime dt = created.isEmpty() ? LocalDateTime.now()
                    : LocalDateTime.parse(created, DateTimeFormatter.ISO_DATE_TIME);
            return new ReleaseEvent(tag, name, body, url, dt);
        } catch (IOException e) {
            return null;
        }
    }
}
