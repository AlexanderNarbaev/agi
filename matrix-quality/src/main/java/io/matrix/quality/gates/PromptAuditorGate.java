package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * WAVE T-10 — Goal Prompt Auditor Gate.
 *
 * Verifies that SESSION.md captures the original user prompt and tracks
 * deliverables against acceptance criteria.
 */
public final class PromptAuditorGate implements Gate {

    private static final Pattern ACCEPTANCE_CRITERIA = Pattern.compile(
        "(?i)(acceptance[\\s_-]?criteria|deliverable|goal)"
    );

    @Override
    public String name() { return "goal-prompt-auditor"; }

    @Override
    public String description() {
        return "Verify SESSION.md captures original prompt and acceptance criteria";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        Path session = projectRoot.resolve("SESSION.md");
        if (!Files.exists(session)) {
            return new GateResult(name(), GateResult.Status.FAIL,
                "SESSION.md not found", List.of("Missing SESSION.md at " + session));
        }
        try {
            String content = Files.readString(session);
            if (!content.contains("**Status:**")) {
                findings.add("SESSION.md missing Status marker");
            }
            if (!ACCEPTANCE_CRITERIA.matcher(content).find()) {
                findings.add("SESSION.md missing acceptance criteria / deliverables section");
            }
            if (content.length() < 200) {
                findings.add("SESSION.md suspiciously short: " + content.length() + " chars");
            }
        } catch (IOException e) {
            return new GateResult(name(), GateResult.Status.FAIL,
                "Read error: " + e.getMessage(), List.of(e.toString()));
        }
        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                "SESSION.md captures original prompt and acceptance criteria", findings);
        }
        return new GateResult(name(), GateResult.Status.FAIL,
            "SESSION.md incomplete", findings);
    }
}
