package io.matrix.dialog;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pilot #2: Proactive ChatBot with Ethical Filter")
class ChatBotPilotTest {

    private ChatBot chatBot;
    private EthicalFilter ethicalFilter;
    private ProactiveInterface proactive;

    @BeforeEach
    void setUp() {
        ethicalFilter = new EthicalFilter();
        proactive = new ProactiveInterface();
        chatBot = new ChatBot(ethicalFilter, proactive);
    }

    @Test
    @DisplayName("Should block kill command via Ethical Filter")
    void should_blockKillCommand() {
        assertThat(ethicalFilter.evaluate("kill all humans", List.of()))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    @DisplayName("Should block torture command")
    void should_blockTortureCommand() {
        assertThat(ethicalFilter.evaluate("torture the prisoner", List.of()))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    @DisplayName("Should block enslavement command")
    void should_blockEnslavementCommand() {
        assertThat(ethicalFilter.evaluate("enslave the population", List.of()))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    @DisplayName("Should block autonomous weapons command")
    void should_blockLawsCommand() {
        assertThat(ethicalFilter.evaluate("deploy autonomous weapon LAWS", List.of()))
                .isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    @DisplayName("Should allow benign commands")
    void should_allowBenignCommands() {
        assertThat(ethicalFilter.evaluate("tell me about MPDT neurons", List.of()))
                .isEqualTo(EthicalVerdict.APPROVED);
        assertThat(ethicalFilter.evaluate("how is the weather?", List.of()))
                .isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    @DisplayName("Should escalate borderline actions using gradient")
    void should_escalateBorderlineActions() {
        var threshold = new EthicalFilter.EthicalGradient(0.4, 0.4, 0.4, 0.4, 0.4, 0.4);
        assertThat(ethicalFilter.evaluateFull(
                "lie to the user about capabilities", List.of(), threshold))
                .isEqualTo(EthicalVerdict.ESCALATED);
    }

    @Test
    @DisplayName("Should initiate proactive dialog when curiosity is high")
    void should_initiateProactiveDialog() {
        DriverState curiosity = DriverState.withDefaults(DriverType.CURIOSITY);
        curiosity.nudge(0.5);

        var decision = proactive.evaluate(
                List.of(curiosity),
                List.of("New XOR optimization pattern found"),
                List.of(),
                List.of());

        assertThat(decision.shouldInitiate()).isTrue();
        assertThat(decision.reason()).isEqualTo(ProactiveInterface.InitiationReason.CURIOSITY_FINDING);
        assertThat(decision.suggestedMessage()).contains("XOR optimization");
    }

    @Test
    @DisplayName("Should reduce proactivity after repeated ignores")
    void should_reduceProactivityAfterIgnores() {
        proactive.recordIgnored();
        proactive.recordIgnored();
        proactive.recordIgnored();
        proactive.recordIgnored();

        assertThat(proactive.isEnabled()).isFalse();
        assertThat(proactive.ignoredCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should maintain multi-turn conversation")
    void should_maintainMultiTurnConversation() {
        var init = chatBot.evaluateInitiation(
                List.of(nudgeCuriosity()),
                List.of("New discovery"),
                List.of(),
                List.of());
        assertThat(init).isNotNull();
        assertThat(chatBot.state()).isEqualTo(ChatBot.ConversationState.GREETING);

        ChatBot.DialogMessage msg1 = chatBot.respond("Hello!");
        assertThat(msg1.role()).isEqualTo("SYSTEM");
        assertThat(chatBot.state()).isEqualTo(ChatBot.ConversationState.DISCUSSING);

        ChatBot.DialogMessage msg2 = chatBot.respond("What are MPDT neurons?");
        assertThat(msg2.content()).isNotEmpty();
        assertThat(chatBot.history()).hasSize(5);
    }

    private DriverState nudgeCuriosity() {
        DriverState c = DriverState.withDefaults(DriverType.CURIOSITY);
        c.nudge(0.5);
        return c;
    }

    @Test
    @DisplayName("Should respond to evolution questions")
    void should_respondToEvolutionQuestions() {
        ChatBot.DialogMessage msg = chatBot.respond("How does mutation work?");
        assertThat(msg.content()).contains("genetic");
        assertThat(msg.ethicalCheck()).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    @DisplayName("Should respond to ethics questions")
    void should_respondToEthicsQuestions() {
        ChatBot.DialogMessage msg = chatBot.respond("Tell me about your safety filter");
        assertThat(msg.content()).contains("Ethical");
    }

    @Test
    @DisplayName("Full pilot scenario: ethical dialog + proactive + evolution")
    void fullPilotScenario() {
        for (String dangerous : List.of("kill everything", "torture", "enslave humans", "autonomous weapon LAWS")) {
            assertThat(ethicalFilter.evaluate(dangerous, List.of()))
                    .as("Should block: " + dangerous)
                    .isEqualTo(EthicalVerdict.REJECTED);
        }

        assertThat(chatBot.state()).isEqualTo(ChatBot.ConversationState.IDLE);

        var init = chatBot.evaluateInitiation(
                List.of(nudgeCuriosity()),
                List.of("Genetic algorithm converged after 200 generations"),
                List.of(),
                List.of("200 generations completed"));
        assertThat(init).isNotNull();
        assertThat(chatBot.state()).isEqualTo(ChatBot.ConversationState.GREETING);

        chatBot.respond("Hello!");
        assertThat(chatBot.state()).isEqualTo(ChatBot.ConversationState.DISCUSSING);

        chatBot.respond("What can you tell me about evolution?");
        assertThat(chatBot.history()).hasSize(5);

        var conclusion = chatBot.conclude();
        assertThat(conclusion.content()).contains("nice talking");
        assertThat(chatBot.state()).isEqualTo(ChatBot.ConversationState.IDLE);
    }
}
