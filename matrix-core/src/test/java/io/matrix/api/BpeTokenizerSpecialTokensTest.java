package io.matrix.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 78 — Verify BpeTokenizer recognizes Qwen special tokens. */
class BpeTokenizerSpecialTokensTest {

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
    void specialTokensAreInVocab() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        // After loading, vocab should contain special tokens
        String imStart = tok.reverseToken(QwenChatTemplate.IM_START);
        String imEnd = tok.reverseToken(QwenChatTemplate.IM_END);
        assertThat(imStart).isEqualTo("<|im_start|>");
        assertThat(imEnd).isEqualTo("<|im_end|>");
    }

    @Test
    @EnabledIf("modelAvailable")
    void encodeRecognizesSpecialTokensAsSingleIds() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        int[] ids = tok.encode("<|im_start|>system hi<|im_end|>");
        // Should be 4 ids: im_start(151644), "system", "Ġhi", im_end(151645)
        assertThat(ids.length).isEqualTo(4);
        assertThat(ids[0]).isEqualTo(QwenChatTemplate.IM_START);
        assertThat(ids[ids.length - 1]).isEqualTo(QwenChatTemplate.IM_END);
    }

    @Test
    @EnabledIf("modelAvailable")
    void decodeEmitsSpecialTokensLiterally() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        String text = tok.decode(new int[]{
                QwenChatTemplate.IM_START, 872 /* user */, 9707 /* Hello */,
                QwenChatTemplate.IM_END
        });
        assertThat(text).contains("<|im_start|>");
        assertThat(text).contains("<|im_end|>");
        // Should not be mangled by byte-level decode
        assertThat(text).doesNotContain("âĢĪ");
    }

    @Test
    @EnabledIf("modelAvailable")
    void chatTemplateRoundTrip() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        String prompt = QwenChatTemplate.buildUserPrompt("Hello");
        int[] ids = tok.encode(prompt);
        String back = tok.decode(ids);
        assertThat(back).isEqualTo(prompt);
    }

    @Test
    @EnabledIf("modelAvailable")
    void encodeDoesNotProduceImEndFromRegularText() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        int[] ids = tok.encode("hello world");
        // None of the regular text should map to im_end (151645)
        for (int id : ids) {
            assertThat(id).isNotEqualTo(QwenChatTemplate.IM_END);
            assertThat(id).isNotEqualTo(QwenChatTemplate.IM_START);
        }
    }
}
