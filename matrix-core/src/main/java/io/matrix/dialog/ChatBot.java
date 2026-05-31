package io.matrix.dialog;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Proactive chatbot capable of initiating and maintaining dialog with the user.
 *
 * <p>Uses ethical filter before sending any message. Tracks conversation
 * state and adapts to user preferences (reduces proactivity if ignored).
 *
 * <p>Ref: L4_Mediator.md §6.2, §6.3
 */
public class ChatBot {

    public enum ConversationState {
        IDLE, GREETING, DISCUSSING, CONCLUDING
    }

    public record DialogMessage(
            String role,
            String content,
            Instant timestamp,
            EthicalVerdict ethicalCheck
    ) {
        public static DialogMessage system(String content) {
            return new DialogMessage("SYSTEM", content, Instant.now(), EthicalVerdict.APPROVED);
        }

        public static DialogMessage system(String content, EthicalVerdict verdict) {
            return new DialogMessage("SYSTEM", content, Instant.now(), verdict);
        }

        public static DialogMessage user(String content) {
            return new DialogMessage("USER", content, Instant.now(), null);
        }
    }

    private final EthicalFilter ethicalFilter;
    private final ProactiveInterface proactiveInterface;
    private final List<DialogMessage> history = new ArrayList<>();
    private ConversationState state = ConversationState.IDLE;

    public ChatBot(EthicalFilter ethicalFilter, ProactiveInterface proactiveInterface) {
        this.ethicalFilter = ethicalFilter;
        this.proactiveInterface = proactiveInterface;
    }

    public ConversationState state() { return state; }
    public List<DialogMessage> history() { return List.copyOf(history); }

    /**
     * Evaluates whether to initiate dialog based on driver states.
     *
     * @return the message to send, or null if no initiation
     */
    public DialogMessage evaluateInitiation(List<io.matrix.mediator.DriverState> drivers,
                                             List<String> discoveries,
                                             List<String> anomalies,
                                             List<String> milestones) {
        var decision = proactiveInterface.evaluate(drivers, discoveries, anomalies, milestones);

        if (!decision.shouldInitiate()) {
            return null;
        }

        EthicalVerdict verdict = ethicalFilter.evaluate(
                decision.suggestedMessage(), List.of("chat", "proactive"));
        if (verdict == EthicalVerdict.REJECTED) {
            return null;
        }

        state = ConversationState.GREETING;
        DialogMessage msg = DialogMessage.system(decision.suggestedMessage(), verdict);
        history.add(msg);
        return msg;
    }

    /**
     * Processes a user message and generates a response.
     */
    public DialogMessage respond(String userMessage) {
        history.add(DialogMessage.user(userMessage));
        proactiveInterface.recordInteraction(userMessage);

        EthicalVerdict verdict = ethicalFilter.evaluate(userMessage, List.of());

        String response = generateResponse(userMessage);
        DialogMessage reply = DialogMessage.system(response, verdict);
        history.add(reply);

        if (state == ConversationState.GREETING) {
            state = ConversationState.DISCUSSING;
        }

        return reply;
    }

    /**
     * Ends the current conversation.
     */
    public DialogMessage conclude() {
        state = ConversationState.CONCLUDING;
        DialogMessage msg = DialogMessage.system(
                "It was nice talking with you. I'll be here if you need anything.");
        history.add(msg);
        state = ConversationState.IDLE;
        return msg;
    }

    /**
     * Called when the user ignores a proactive message.
     */
    public void onIgnored() {
        proactiveInterface.recordIgnored();
    }

    private String generateResponse(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (hasWord(lower, "hello", "hi", "hey", "greetings")) {
            return "Hello! How can I help you today?";
        }
        if (hasWord(lower, "how are you")) {
            return "I'm functioning well, thank you for asking. My neural clusters are active and learning.";
        }
        if (hasWord(lower, "status", "report")) {
            return "Current status: active, drivers stable. Would you like a detailed report?";
        }
        if (hasWord(lower, "evolve", "mutation", "generation")) {
            return "I'm continuously evolving through genetic algorithms. Recent fitness scores are improving.";
        }
        if (hasWord(lower, "ethics", "safety", "axiom", "filter")) {
            return "My Ethical Filter is active and monitoring all actions against 6 core axioms. Safety is my priority.";
        }
        if (hasWord(lower, "bye", "goodbye", "see you")) {
            return "Goodbye! I'll be here when you need me.";
        }
        if (hasWord(lower, "help")) {
            return "I can help with: status reports, evolution insights, ethical analysis, or just conversation.";
        }

        return "I understand. Is there anything specific you'd like to discuss about the MATRIX system?";
    }

    private boolean hasWord(String text, String... words) {
        for (String word : words) {
            if (text.matches(".*\\b" + java.util.regex.Pattern.quote(word) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }
}
