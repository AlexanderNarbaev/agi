package io.matrix.research;

import io.matrix.integration.GitHubWebhook;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUN 405 — Phase Z GitHub release webhook.
 */
class Exp405GitHubWebhookTest {

    @Test
    void webhookRequiresUrl() {
        assertThatThrownBy(() -> new GitHubWebhook(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitHubWebhook(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitHubWebhook("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webhookIsConfigured() {
        GitHubWebhook w = new GitHubWebhook("https://example.com/webhook");
        assertThat(w.isConfigured()).isTrue();
    }

    @Test
    void notifyReleaseFailsGracefully() {
        GitHubWebhook w = new GitHubWebhook("https://invalid.invalid/webhook");
        var ev = new GitHubWebhook.ReleaseEvent(
                "v1.0.0", "Test", "body", "https://example.com", LocalDateTime.now());
        boolean result = w.notifyRelease(ev);
        assertThat(result).isFalse();
    }

    @Test
    void parsePayload() {
        String json = "{\"release\":{"
                + "\"tag_name\":\"v0.1.0\","
                + "\"name\":\"Phase X release\","
                + "\"body\":\"WAL: 32 algorithms\","
                + "\"html_url\":\"https://github.com/...\","
                + "\"created_at\":\"2026-09-11T10:00:00\""
                + "}}";
        var ev = GitHubWebhook.parsePayload(json);
        assertThat(ev).isNotNull();
        assertThat(ev.tagName()).isEqualTo("v0.1.0");
        assertThat(ev.releaseName()).isEqualTo("Phase X release");
    }
}
