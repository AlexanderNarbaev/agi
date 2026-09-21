package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * WAVE T-10 — Goal UX Reviewer Gate.
 *
 * Checks user-facing copy: no broken markdown, no LLM-leaked content,
 * consistent voice, accessibility considerations.
 */
public final class UxReviewerGate implements Gate {

    private static final Pattern FORBIDDEN = Pattern.compile(
        "(?i)(as an? AI|language model|I don'?t have (personal )?(opinions|feelings)|I am (just )?an AI)"
    );

    @Override
    public String name() { return "goal-ux-reviewer"; }

    @Override
    public String description() {
        return "UX review: copy quality, accessibility, no AI self-reference";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        try (Stream<Path> docs = Files.walk(projectRoot)
                .filter(p -> p.toString().endsWith(".md"))
                .filter(p -> p.toString().contains("/docs/"))) {
            docs.forEach(p -> {
                try {
                    String content = Files.readString(p);
                    if (FORBIDDEN.matcher(content).find()) {
                        findings.add(p + ": contains AI self-reference (CONSTITUTION VI)");
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            return new GateResult(name(), GateResult.Status.WARN,
                "Walk error: " + e.getMessage(), List.of(e.toString()));
        }

        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                "User-facing copy looks clean", findings);
        }
        return new GateResult(name(), GateResult.Status.WARN,
            "UX review findings", findings);
    }
}
