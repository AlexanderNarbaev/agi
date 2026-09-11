package io.matrix.api;

import java.util.List;

import io.matrix.api.QwenChatTemplate;

/**
 * RUN 97 — Prompt template library.
 *
 * <p>Common prompt patterns for chat/instruct LLMs.
 * These help users get started with known-good templates.
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    /** Standard helpful assistant. */
    public static String helpfulAssistant(String userMessage) {
        return QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system(
                        "You are a helpful, harmless, and honest assistant."),
                QwenChatTemplate.Message.user(userMessage)
        ));
    }

    /** Concise one-word/one-line answer. */
    public static String conciseAssistant(String userMessage) {
        return QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system(
                        "You are a concise assistant. Answer in one sentence or less."),
                QwenChatTemplate.Message.user(userMessage)
        ));
    }

    /** Math/reasoning prompt. */
    public static String mathAssistant(String problem) {
        return QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system(
                        "You are a math tutor. Show your work step by step."),
                QwenChatTemplate.Message.user(problem)
        ));
    }

    /** Code reviewer. */
    public static String codeReviewer(String code) {
        return QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system(
                        "You are a senior software engineer doing code review. "
                                + "Point out bugs, suggest improvements."),
                QwenChatTemplate.Message.user("Please review:\n\n" + code)
        ));
    }

    /** Translator. */
    public static String translator(String text, String targetLanguage) {
        return QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system(
                        "You are a translator. Translate accurately and preserve meaning."),
                QwenChatTemplate.Message.user(
                        "Translate the following to " + targetLanguage + ":\n\n" + text)
        ));
    }

    /** Summarizer. */
    public static String summarizer(String text) {
        return QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system(
                        "You are a summarizer. Produce a 2-3 sentence summary."),
                QwenChatTemplate.Message.user("Summarize:\n\n" + text)
        ));
    }

    /** Creative writer. */
    public static String creativeWriter(String prompt) {
        return QwenChatTemplate.buildPrompt(List.of(
                QwenChatTemplate.Message.system(
                        "You are a creative writer. Be vivid and engaging."),
                QwenChatTemplate.Message.user(prompt)
        ));
    }
}
