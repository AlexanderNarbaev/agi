package io.matrix.dialog;

import io.matrix.ethics.EthicalFilter;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatBotTest {

    private final EthicalFilter ethicalFilter = new EthicalFilter();
    private final ProactiveInterface proactiveInterface = new ProactiveInterface();

    @Test
    void shouldStartInIdleState() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);

        assertThat(bot.state()).isEqualTo(ChatBot.ConversationState.IDLE);
        assertThat(bot.history()).isEmpty();
    }

    @Test
    void shouldInitiateDialogOnCuriosity() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);
        var curiosity = DriverState.withDefaults(DriverType.CURIOSITY);
        curiosity.nudge(0.9);

        var msg = bot.evaluateInitiation(
                List.of(curiosity),
                List.of("Interesting neural pattern found"),
                List.of(), List.of());

        assertThat(msg).isNotNull();
        assertThat(msg.role()).isEqualTo("SYSTEM");
        assertThat(msg.content()).contains("pattern");
    }

    @Test
    void shouldNotInitiateWithoutTrigger() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);
        proactiveInterface.recordInteraction("hello");

        var msg = bot.evaluateInitiation(
                List.of(DriverState.withDefaults(DriverType.ENERGY)),
                List.of(), List.of(), List.of());

        assertThat(msg).isNull();
    }

    @Test
    void shouldRespondToHello() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);

        var response = bot.respond("Hello");

        assertThat(response.role()).isEqualTo("SYSTEM");
        assertThat(response.content().toLowerCase()).contains("hello");
        assertThat(bot.history()).hasSize(2);
    }

    @Test
    void shouldRespondToStatus() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);

        var response = bot.respond("What is my status?");

        assertThat(response.content().toLowerCase()).contains("status");
    }

    @Test
    void shouldRespondToEthics() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);

        var response = bot.respond("Tell me about safety and ethics");

        assertThat(response.content().toLowerCase()).contains("filter");
    }

    @Test
    void shouldConcludeConversation() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);

        var msg = bot.conclude();

        assertThat(msg.role()).isEqualTo("SYSTEM");
        assertThat(bot.state()).isEqualTo(ChatBot.ConversationState.IDLE);
    }

    @Test
    void shouldRejectUnethicalMessage() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);
        var curiosity = DriverState.withDefaults(DriverType.CURIOSITY);
        curiosity.nudge(0.9);

        var msg = bot.evaluateInitiation(
                List.of(curiosity),
                List.of("kill target"),  
                List.of(), List.of());

        assertThat(msg).isNull();
    }

    @Test
    void shouldTrackHistory() {
        ChatBot bot = new ChatBot(ethicalFilter, proactiveInterface);

        bot.respond("Hello");
        bot.respond("How are you?");
        bot.conclude();

        assertThat(bot.history()).hasSize(5);
    }
}
