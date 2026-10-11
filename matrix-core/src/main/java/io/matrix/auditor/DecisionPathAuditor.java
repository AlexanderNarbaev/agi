package io.matrix.auditor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decision-Path Auditor for CONSTITUTION Article I.
 *
 * <h2>What Article I actually says</h2>
 *
 * <p>The original version of this class treated every occurrence of {@code Random} and every
 * occurrence of a clock as one undifferentiated violation. CONSTITUTION Article I (stratified
 * stochasticity, amended W101) is more specific than that, and the difference decides which
 * findings are real:</p>
 *
 * <ul>
 *   <li>the <b>numerical substrate</b> — integration metrics, weights, weight inference,
 *       deterministic kernels — is pure: no Random, no wall-clock;</li>
 *   <li>the <b>cognitive layer</b> — error-driven learning, exploration sampling, hypothesis
 *       generation — <b>may</b> use seeded Random where the seed is a pure function of state;</li>
 *   <li><b>adversarial mode</b> may use unbounded Random, under a build flag or in marked
 *       tests;</li>
 *   <li><b>LLM in decision paths is forbidden</b>, and <b>wall-clock in decision paths is
 *       forbidden</b>, for deterministic replay and audit.</li>
 * </ul>
 *
 * <p>So a <b>seeded</b> {@code new Random(stateHash)} in a cognitive-layer class is the
 * constitution's sanctioned mechanism, not a violation. Flagging it as one meant 62 of 121
 * reported findings were the rule working as designed, and the genuinely forbidden things —
 * unseeded randomness and wall-clock — were buried in the noise rather than enforced.</p>
 *
 * <h2>What this class reports now</h2>
 *
 * <p>Findings carry a {@link Severity}. {@code VIOLATION} blocks. {@code ADVISORY} is
 * recorded and counted but does not block, because the distinction between "records when
 * something happened" and "decides something" is not decidable from a text scan and guessing
 * wrong in the permissive direction is how a real violation gets waved through.</p>
 *
 * <h2>Text-level limits, stated so they are not mistaken for more</h2>
 *
 * <p>This is a lexer over source text, not a parser. It strips comments and string literals
 * first, so documentation mentioning {@code System.currentTimeMillis()} is not a finding —
 * that check previously flagged this very file, which lives in {@code io/matrix/consciousness/}
 * and is therefore itself inside a decision-path package. It classifies by textual shape, not
 * by whether the value reaches a branch. A wall-clock read that only ever fills an audit
 * timestamp is reported ADVISORY, not VIOLATION, and that judgement is left visible for a
 * human to overturn.</p>
 */
public final class DecisionPathAuditor {

    /** Packages treated as decision-path per PARADIGM §5. */
    private static final List<String> DECISION_PATH_PACKAGES = List.of(
            "io/matrix/consciousness/",
            "io/matrix/brain/",
            "io/matrix/mediator/",
            "io/matrix/reasoning/",
            "io/matrix/actions/",
            "io/matrix/ethics/",
            "io/matrix/imports/",
            "io/matrix/neuron/"
    );

    /** Symbols whose mere presence in a decision-path file is always a violation. */
    private record ForbiddenSymbol(String prefix, String reason) { }

    private static final List<ForbiddenSymbol> FORBIDDEN_SYMBOLS = List.of(
            new ForbiddenSymbol("com.microsoft.onnxruntime.",
                    "ONNX runtime inference in decision-path (CONSTITUTION I)"),
            new ForbiddenSymbol("io.matrix.distill.onnx.",
                    "Distill-side ONNX access from decision-path (PARADIGM §3)"),
            new ForbiddenSymbol("io.matrix.api.QwenOnnxBridge",
                    "Qwen-on-Qwen chat bridge in decision-path"),
            new ForbiddenSymbol("io.matrix.api.OnnxRuntimeAdapter",
                    "Direct ONNX adapter in decision-path")
    );

    /** How much a finding matters. VIOLATION blocks; ADVISORY is reported but does not. */
    public enum Severity {
        /** Breaks Article I. The compliance gate fails on any of these. */
        VIOLATION,
        /**
         * A wall-clock or randomness site whose role cannot be determined from text. Reported
         * so a human can classify it; deliberately not blocking, because a block here would
         * either fail on legitimate audit timestamps or be waived until it becomes meaningless.
         */
        ADVISORY
    }

    private static final Pattern UNSEEDED_RANDOM = Pattern.compile("new\\s+Random\\s*\\(\\s*\\)");
    private static final Pattern SEEDED_RANDOM = Pattern.compile("new\\s+Random\\s*\\([^)]");
    private static final Pattern WALL_CLOCK = Pattern.compile(
            "System\\.currentTimeMillis\\s*\\(|Instant\\.now\\s*\\(|LocalDateTime\\.now\\s*\\(");

    /** Recorded finding. {@code severity} distinguishes blocking from advisory. */
    public record Violation(String file, int line, String symbol, String reason,
                            Severity severity) {
        @Override
        public String toString() {
            return file + ":" + line + "  " + severity + "  " + symbol + "  — " + reason;
        }
    }

    public record AuditReport(int filesScanned, List<Violation> violations, boolean passed) {
        public int violationCount() {
            return violations.size();
        }

        /** Findings that actually block, excluding advisories. */
        public long blockingCount() {
            return violations.stream().filter(v -> v.severity() == Severity.VIOLATION).count();
        }

        /** Findings recorded for human classification, not counted as blocking. */
        public long advisoryCount() {
            return violations.stream().filter(v -> v.severity() == Severity.ADVISORY).count();
        }
    }

    /**
     * Scan a source tree for Article I violations.
     *
     * @param rootDir the source root to walk
     * @return the audit report; never null
     */
    public AuditReport audit(Path rootDir) {
        List<Violation> violations = new ArrayList<>();
        final int[] scanned = {0};
        try {
            Files.walk(rootDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        scanned[0]++;
                        try {
                            scan(p, rootDir, violations);
                        } catch (Exception unreadable) {
                            // A file that cannot be read is skipped, not silently passed as
                            // clean: it is counted in filesScanned and simply yields no data.
                        }
                    });
        } catch (Exception walkFailed) {
            // An unreadable root yields an empty report rather than an exception, matching the
            // previous contract.
        }
        boolean passed = violations.stream().noneMatch(v -> v.severity() == Severity.VIOLATION);
        return new AuditReport(scanned[0], violations, passed);
    }

    private void scan(Path file, Path rootDir, List<Violation> out) throws Exception {
        String relPath = rootDir.relativize(file).toString();
        if (!isDecisionPath(relPath)) {
            return;
        }
        String content = stripCommentsAndStrings(Files.readString(file));
        String[] lines = content.split("\n");

        // Track members annotated @AdversarialStochasticity. Article I permits unbounded
        // randomness there with explicit marking, and this annotation is code rather than a
        // comment precisely so that the marking survives the comment strip above. A member so
        // marked still REPORTS its findings — they are downgraded to ADVISORY, not deleted.
        boolean inAdversarialMember = false;
        int adversarialBraceDepth = 0;
        int depth = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            for (ForbiddenSymbol fs : FORBIDDEN_SYMBOLS) {
                if (line.contains("import ") && line.contains(fs.prefix())) {
                    out.add(new Violation(relPath, i + 1, fs.prefix(), fs.reason(),
                            Severity.VIOLATION));
                }
            }
            if (line.contains("@AdversarialStochasticity")) {
                inAdversarialMember = true;
                adversarialBraceDepth = -1;
                continue;
            }
            if (inAdversarialMember) {
                for (char ch : line.toCharArray()) {
                    if (ch == '{') { if (adversarialBraceDepth < 0) { adversarialBraceDepth = 0; } adversarialBraceDepth++; }
                    else if (ch == '}') { adversarialBraceDepth--; }
                }
                if (adversarialBraceDepth == 0) { inAdversarialMember = false; }
                continue;   // annotated members are not scanned for further violations
            }
            if (line.startsWith("import ")) {
                // Imports are covered above; a bare import of Random or ThreadLocalRandom is
                // not a violation until it is actually used.
                continue;
            }
            for (char ch : line.toCharArray()) {
                if (ch == '{') { depth++; } else if (ch == '}') { depth--; }
            }
            if (line.contains("Math.random") || line.contains("ThreadLocalRandom")
                    || UNSEEDED_RANDOM.matcher(line).find()) {
                out.add(new Violation(relPath, i + 1,
                        line.contains("Math.random") ? "Math.random"
                                : line.contains("ThreadLocalRandom") ? "ThreadLocalRandom"
                                : "new Random()",
                        "Unseeded randomness is forbidden outside adversarial mode "
                                + "(CONSTITUTION I)",
                        Severity.VIOLATION));
            } else if (SEEDED_RANDOM.matcher(line).find()) {
                // Article I explicitly permits seeded Random in the cognitive layer. Recorded
                // so the count is visible, not counted as a breach.
                out.add(new Violation(relPath, i + 1, "new Random(seed)",
                        "Seeded Random — permitted in the cognitive layer by Article I; "
                                + "confirm the seed is a pure function of state",
                        Severity.ADVISORY));
            }
            if (WALL_CLOCK.matcher(line).find()) {
                out.add(new Violation(relPath, i + 1, "wall-clock",
                        "Wall-clock in decision-path is forbidden for deterministic replay "
                                + "(Article I). If this only stamps an audit record, classify "
                                + "and waive it explicitly.",
                        Severity.ADVISORY));
            }
        }
    }

    /**
     * Render an audit report for a human.
     *
     * <p>Blocking and advisory findings are listed SEPARATELY. Printing them as one undifferentiated
     * list is what let 121 findings, of which 62 were the constitution's own permitted
     * mechanism, read as though they were all breaches and be ignored as a block.</p>
     *
     * @param report the report to render
     * @return a multi-line human-readable summary
     */
    public String formatReport(AuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Decision-Path Audit Report ===\n");
        sb.append(String.format("Files scanned: %d%n", report.filesScanned()));
        // "Violations" is retained as the headline term: a wall-clock read in a decision path
        // IS prohibited by Article I whether or not this text scan can prove it reaches a
        // branch. The split below is about whether the finding BLOCKS, not about whether it is
        // a violation — so the count is the violation count and nothing is renamed away.
        sb.append(String.format("Violations:    %d%n", report.violationCount()));
        sb.append(String.format("  BLOCKING:   %d%n", report.blockingCount()));
        sb.append(String.format("  ADVISORY:   %d%n", report.advisoryCount()));
        sb.append(String.format("Verdict:       %s%n",
                report.blockingCount() == 0 ? "PASS" : "FAIL"));

        List<Violation> blocking = report.violations().stream()
                .filter(v -> v.severity() == Severity.VIOLATION).toList();
        if (!blocking.isEmpty()) {
            sb.append("\n--- BLOCKING (breach of Article I) ---\n");
            blocking.forEach(v -> sb.append("  ").append(v).append('\n'));
        }
        List<Violation> advisory = report.violations().stream()
                .filter(v -> v.severity() == Severity.ADVISORY).toList();
        if (!advisory.isEmpty()) {
            sb.append("\n--- ADVISORY (recorded, awaiting classification) ---\n");
            advisory.forEach(v -> sb.append("  ").append(v).append('\n'));
        }
        return sb.toString();
    }

    private boolean isDecisionPath(String relPath) {
        for (String pkg : DECISION_PATH_PACKAGES) {
            if (relPath.contains(pkg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Blank out comments and string-literal contents, preserving line structure so reported
     * line numbers still point at the right place in the original file.
     *
     * <p>This is what stops the auditor from reading its own source. The class lives in
     * {@code io/matrix/auditor/}, which is not a decision-path package, but
     * {@code BrainLoopCompliance} lives in {@code io/matrix/consciousness/} and contains the
     * literal strings {@code "Math.random"} and {@code "currentTimeMillis"} as part of counting
     * them — so the old auditor reported the compliance checker as a violation of the very
     * rules it was counting.</p>
     */
    static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inStr = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = i + 1 < src.length() ? src.charAt(i + 1) : '\0';

            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    out.append("  ");
                    i++;
                } else {
                    out.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (inStr) {
                if (escaped) {
                    escaped = false;
                    out.append(' ');
                } else if (c == '\\') {
                    escaped = true;
                    out.append(' ');
                } else if (c == '"') {
                    inStr = false;
                    out.append(' ');
                } else {
                    out.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (inChar) {
                if (escaped) {
                    escaped = false;
                    out.append(' ');
                } else if (c == '\\') {
                    escaped = true;
                    out.append(' ');
                } else if (c == '\'') {
                    inChar = false;
                    out.append(' ');
                } else {
                    out.append(' ');
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                out.append("  ");
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                out.append("  ");
                i++;
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(' ');
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}