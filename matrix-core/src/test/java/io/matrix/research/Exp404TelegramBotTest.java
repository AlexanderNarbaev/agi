package io.matrix.research;

import io.matrix.integration.TelegramBot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RUN 404 — Phase Z Telegram bot adapter.
 */
class Exp404TelegramBotTest {

    @Test
    void telegramBotRequiresTokenAndChatId() {
        assertThatThrownBy(() -> new TelegramBot(null, "123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TelegramBot("token", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void telegramBotIsConfigured() {
        TelegramBot bot = new TelegramBot("test-token", "test-chat");
        assertThat(bot.isConfigured()).isTrue();
    }

    @Test
    void telegramBotFromEnvReturnsNullWhenNotSet() {
        // Don't set env vars in test → returns null
        TelegramBot bot = TelegramBot.fromEnv();
        if (System.getenv("MATRIX_TELEGRAM_BOT_TOKEN") == null) {
            assertThat(bot).isNull();
        }
    }

    @Test
    void telegramBotSendFailsGracefully() {
        // No actual Telegram — should fail gracefully without throwing
        TelegramBot bot = new TelegramBot("invalid", "invalid");
        // Network call will fail (no real server) but shouldn't throw
        boolean result = bot.sendMessage("test");
        assertThat(result).isFalse();
    }

    @Test
    void telegramBotWALUpdate() {
        TelegramBot bot = new TelegramBot("invalid", "invalid");
        boolean result = bot.sendWALUpdate("RUN 404", "Phase Z Telegram bot");
        assertThat(result).isFalse();  // no real server
    }
}
