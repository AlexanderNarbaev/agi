package io.matrix.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Phase Z (RUN 404) — Telegram bot adapter.
 * Sends WAL updates, build notifications, and test reports to a
 * Telegram channel. Pure HTTP client (no additional dependencies).
 *
 * <p>Usage:
 *  - Set env var MATRIX_TELEGRAM_BOT_TOKEN (bot token from @BotFather)
 *  - Set env var MATRIX_TELEGRAM_CHAT_ID (chat/channel id)
 *  - Call {@link #sendMessage(String)} to post.
 *
 * <p>Pure data class — no side effects beyond the network call.
 * HTTP client uses system default; connect timeout 5s.
 */
public final class TelegramBot {

    private static final String TELEGRAM_API = "https://api.telegram.org/bot";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String token;
    private final String chatId;
    private final HttpClient client;

    public TelegramBot(String token, String chatId) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token blank");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("chatId blank");
        }
        this.token = token;
        this.chatId = chatId;
        this.client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /** Create from environment variables MATRIX_TELEGRAM_BOT_TOKEN,
     *  MATRIX_TELEGRAM_CHAT_ID. Returns null if not configured. */
    public static TelegramBot fromEnv() {
        String token = System.getenv("MATRIX_TELEGRAM_BOT_TOKEN");
        String chatId = System.getenv("MATRIX_TELEGRAM_CHAT_ID");
        if (token == null || token.isBlank()
                || chatId == null || chatId.isBlank()) {
            return null;
        }
        return new TelegramBot(token, chatId);
    }

    public boolean isConfigured() {
        return token != null && chatId != null;
    }

    /**
     * Send a message. Returns true on success, false on any failure
     * (network, auth, etc.). Never throws.
     */
    public boolean sendMessage(String text) {
        if (text == null || text.isBlank()) return false;
        // Telegram limit: 4096 chars
        if (text.length() > 4000) {
            text = text.substring(0, 4000) + "...";
        }
        try {
            String url = TELEGRAM_API + token + "/sendMessage";
            String body = "{\"chat_id\":\"" + chatId + "\","
                    + "\"text\":\"" + escapeJson(text) + "\","
                    + "\"parse_mode\":\"HTML\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Send a formatted WAL update. */
    public boolean sendWALUpdate(String runId, String message) {
        return sendMessage("<b>WAL " + escapeHtml(runId) + "</b>\n" + escapeHtml(message));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
