package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 97 — PromptTemplates unit tests. */
class PromptTemplatesTest {

    @Test
    void helpfulAssistantTemplate() {
        String t = PromptTemplates.helpfulAssistant("What is 2+2?");
        assertThat(t).startsWith("<|im_start|>system");
        assertThat(t).contains("helpful, harmless");
        assertThat(t).contains("What is 2+2?");
        assertThat(t).endsWith("<|im_start|>assistant\n");
    }

    @Test
    void conciseAssistantTemplate() {
        String t = PromptTemplates.conciseAssistant("Hello");
        assertThat(t).contains("concise assistant");
        assertThat(t).contains("Hello");
    }

    @Test
    void mathAssistantTemplate() {
        String t = PromptTemplates.mathAssistant("Solve x+1=2");
        assertThat(t).contains("math tutor");
        assertThat(t).contains("step by step");
        assertThat(t).contains("Solve x+1=2");
    }

    @Test
    void codeReviewerTemplate() {
        String t = PromptTemplates.codeReviewer("int x = 0;");
        assertThat(t).contains("code review");
        assertThat(t).contains("int x = 0;");
    }

    @Test
    void translatorTemplate() {
        String t = PromptTemplates.translator("Hello world", "Spanish");
        assertThat(t).contains("translator");
        assertThat(t).contains("Spanish");
        assertThat(t).contains("Hello world");
    }

    @Test
    void summarizerTemplate() {
        String t = PromptTemplates.summarizer("A very long text.");
        assertThat(t).contains("summarizer");
        assertThat(t).contains("A very long text.");
    }

    @Test
    void creativeWriterTemplate() {
        String t = PromptTemplates.creativeWriter("Write a haiku about code");
        assertThat(t).contains("creative writer");
        assertThat(t).contains("haiku");
    }

    @Test
    void allTemplatesEndWithAssistantMarker() {
        String[] templates = {
                PromptTemplates.helpfulAssistant("x"),
                PromptTemplates.conciseAssistant("x"),
                PromptTemplates.mathAssistant("x"),
                PromptTemplates.codeReviewer("x"),
                PromptTemplates.translator("x", "x"),
                PromptTemplates.summarizer("x"),
                PromptTemplates.creativeWriter("x")
        };
        for (String t : templates) {
            assertThat(t).endsWith("<|im_start|>assistant\n");
        }
    }
}
