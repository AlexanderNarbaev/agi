package io.matrix.research;

import io.matrix.api.BpeTokenizer;
import io.matrix.api.QwenChatTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.45 — BPE round-trip verification (RUN 85).
 *
 * <p>Verifies the GPT-2 byte-level encoder/decoder preserves text
 * exactly through encode → decode for various test strings.
 */
class Exp085BpeRoundTripTest {

    private static Path findModelDir() {
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path p = cwd.resolve("models/hf_cache/qwen05b");
            if (Files.exists(p.resolve("config.json"))) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    static boolean modelAvailable() {
        return findModelDir() != null;
    }

    @Test
    @EnabledIf("modelAvailable")
    void shortPhraseRoundTrip() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        String[] inputs = {
                "Hello world",
                "The quick brown fox",
                "I love programming",
                "Java is great",
                "Machine learning"
        };
        for (String s : inputs) {
            int[] ids = tok.encode(s);
            String back = tok.decode(ids);
            assertThat(back).as("round-trip '%s'", s).isEqualTo(s);
        }
    }

    @Test
    @EnabledIf("modelAvailable")
    void chatPromptRoundTrip() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        String prompt = QwenChatTemplate.buildUserPrompt("Hello!");
        int[] ids = tok.encode(prompt);
        String back = tok.decode(ids);
        assertThat(back).isEqualTo(prompt);
    }

    @Test
    @EnabledIf("modelAvailable")
    void multiTurnHistoryRoundTrip() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        String prompt = QwenChatTemplate.buildPrompt(java.util.List.of(
                QwenChatTemplate.Message.system("You are helpful."),
                QwenChatTemplate.Message.user("My name is Alex."),
                QwenChatTemplate.Message.assistant("Hello Alex!"),
                QwenChatTemplate.Message.user("What's my name?")
        ));
        int[] ids = tok.encode(prompt);
        String back = tok.decode(ids);
        assertThat(back).isEqualTo(prompt);
    }

    @Test
    @EnabledIf("modelAvailable")
    void specialTokensAreRoundTripStable() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        String[] specials = {
                "<|im_start|>",
                "<|im_end|>",
                "<|endoftext|>",
                "<|im_start|>user\nHello<|im_end|>"
        };
        for (String s : specials) {
            int[] ids = tok.encode(s);
            String back = tok.decode(ids);
            assertThat(back).as("special token '%s'", s).isEqualTo(s);
        }
    }

    @Test
    @EnabledIf("modelAvailable")
    void encodeDoesNotProduceSpaceWithoutPrefix() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        // After encoding "I love cats", the token ids for "love" and "cats"
        // should be the Ġ-prefixed vocab tokens (leading-space variants),
        // not the bare words.
        int[] ids = tok.encode("I love cats");
        for (int id : ids) {
            String rev = tok.reverseToken(id);
            // Either a normal word (I, . etc.) or starts with Ġ
            assertThat(rev == null || !rev.equals("love") || rev.startsWith("Ġ")).isTrue();
            assertThat(rev == null || !rev.equals("cats") || rev.startsWith("Ġ")).isTrue();
        }
    }
}
