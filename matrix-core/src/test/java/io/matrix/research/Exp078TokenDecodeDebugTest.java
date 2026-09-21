package io.matrix.research;

import io.matrix.api.BpeTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Quick test: inspect actual encoded tokens for a chat prompt. */
class Exp078TokenDecodeDebugTest {

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
    void debugEncodeOfSystemPrompt() throws Exception {
        BpeTokenizer tok = BpeTokenizer.fromModelDir(findModelDir());
        String text = "You are a helpful assistant.";
        System.out.println("[DEBUG-ENC] text=" + repr(text));
        int[] ids = tok.encode(text);
        System.out.print("[DEBUG-ENC] ids: ");
        for (int id : ids) System.out.print(id + " ");
        System.out.println();
        for (int id : ids) {
            String t = tok.reverseToken(id);
            System.out.printf("  id=%d tok=%s%n", id, repr(t));
        }
        String decoded = tok.decode(ids);
        System.out.println("[DEBUG-ENC] decoded=" + repr(decoded));
        System.out.println("[DEBUG-ENC] match=" + text.equals(decoded));
    }

    private static String repr(String s) {
        return "'" + s.replace("\n", "\\n").replace(" ", "_") + "'";
    }
}
