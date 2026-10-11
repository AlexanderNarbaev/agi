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
         * W36.1 — a wall-clock read nobody has adjudicated.
         *
         * <p>Previously these were {@code ADVISORY} and did not block, on the reasoning that
         * "not decidable from text" argues for leniency. That was wrong in effect: 84 sites sat
         * unadjudicated indefinitely and the gate reported a number nobody was accountable for.
         * An unadjudicated violation is not a lesser violation, it is an unknown one, and the
         * point of a gate is to make unknowns visible.</p>
         *
         * <p>So this now BLOCKS. The two ways to clear it are to inject a clock (fixing the
         * real case) or to annotate {@link ArticleIPermitted} with a reason (adjudicating the
         * benign one). Both are deliberate acts; neither is silence.</p>
         */
        UNCLASSIFIED
    }

    /**
     * W36.1 — verdict for a site that has been adjudicated and found permitted.
     *
     * <p>Replaces {@code ADVISORY}. Recorded with its justification so the classification stays
     * reviewable, and counted separately from both violations and unknowns.</p>
     */
    public enum Permit {
        /** Annotated {@link ArticleIPermitted}: wall-clock that reaches no decision. */
        PERMITTED,
        /** Classified by RULE rather than by hand: see {@link #SEEDED_RANDOM_RULE}. */
        PERMITTED_BY_RULE
    }

    /**
     * W36.1 — the RULE that classifies seeded Random as permitted, with its justification.
     *
     * <p>CONSTITUTION Article I, cognitive layer clause: error-driven learning, exploration
     * sampling and hypothesis generation <em>may</em> use seeded Random where the seed is a pure
     * function of state. Every seeded {@code new Random(expr)} in a decision-path package
     * satisfies the letter of that clause, so requiring a hand-written annotation on each of 59
     * sites would record the constitution 59 times without adding any judgement.</p>
     *
     * <p>Unseeded randomness is NOT covered and stays a VIOLATION: it has no seed, so it cannot
     * be a pure function of anything.</p>
     */
    public static final String SEEDED_RANDOM_RULE =
            "CONSTITUTION I, cognitive layer: seeded Random is permitted where the seed is a "
            + "pure function of state";

    private static final Pattern UNSEEDED_RANDOM = Pattern.compile("new\\s+Random\\s*\\(\\s*\\)");
    private static final Pattern SEEDED_RANDOM = Pattern.compile("new\\s+Random\\s*\\([^)]");
    private static final Pattern WALL_CLOCK = Pattern.compile(
            "System\\.currentTimeMillis\\s*\\(|Instant\\.now\\s*\\(|LocalDateTime\\.now\\s*\\(");

    /** Recorded finding. {@code severity} distinguishes blocking from advisory. */
    public record Violation(String file, int line, String symbol, String reason,
                            Severity severity, String permitReason) {

        /** A blocking breach of Article I. */
        public static Violation violation(String f, int line, String sym, String why) {
            return new Violation(f, line, sym, why, Severity.VIOLATION, null);
        }

        /** A wall-clock read awaiting human adjudication. */
        public static Violation unclassified(String f, int line, String sym, String why) {
            return new Violation(f, line, sym, why, Severity.UNCLASSIFIED, null);
        }

        /** A site adjudicated, or classified by rule, as permitted. */
        public static Violation permitted(String f, int line, String sym, String why,
                                          String permitReason, boolean byRule) {
            return new Violation(f, line, sym, why, Severity.UNCLASSIFIED,
                    byRule ? "rule:" + permitReason : permitReason);
        }

        @Override
        public String toString() {
            return file + ":" + line + "  " + severity + "  " + symbol + "  — " + reason
                    + (permitReason == null ? "" : "  [" + permitReason + "]");
        }
    }

    public record AuditReport(int filesScanned, List<Violation> violations, boolean passed) {
        public int violationCount() {
            return violations.size();
        }

        /** Findings that actually block, excluding advisories. */
        /** Confirmed breaches. These block. */
        public long blockingCount() {
            return violations.stream().filter(v -> v.severity() == Severity.VIOLATION).count();
        }

        /** Wall-clock reads nobody has judged yet. These ALSO block, deliberately. */
        public long unclassifiedCount() {
            return violations.stream()
                    .filter(v -> v.severity() == Severity.UNCLASSIFIED && v.permitReason() == null)
                    .count();
        }

        /** Sites adjudicated or classified by rule as permitted. These do not block. */
        public long permittedCount() {
            return violations.stream().filter(v -> v.permitReason() != null).count();
        }

        /** Total that should block: confirmed breaches plus unadjudicated sites. */
        public long nonCompliantCount() {
            return blockingCount() + unclassifiedCount();
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
        // W36.1: PASS requires zero NON-COMPLIANT sites. UNCLASSIFIED counts, because a gate
        // that reports PASS while a wall-clock site is unjudged is reporting that it checked
        // something it did not check.
        boolean passed = violations.stream()
                .noneMatch(v -> v.severity() == Severity.VIOLATION
                        || (v.severity() == Severity.UNCLASSIFIED && v.permitReason() == null));
        return new AuditReport(scanned[0], violations, passed);
    }

    private void scan(Path file, Path rootDir, List<Violation> out) throws Exception {
        String relPath = rootDir.relativize(file).toString();
        if (!isDecisionPath(relPath)) {
            return;
        }
        String content = stripCommentsAndStrings(Files.readString(file));
        String[] lines = content.split("\n");

        // W36.1 — @ArticleIPermitted exempts the annotated MEMBER, which may be a method or an
        // entire type. Both cases are handled by remembering the brace depth at which the
        // exemption starts and clearing it when the scanner returns to that depth. An earlier
        // version only skipped the declaration line itself, so a class-level annotation
        // exempted nothing and all 24 sites stayed UNCLASSIFIED — which is exactly the kind of
        // gate that looks like it works because nothing about it fails loudly.
        //
        // @AdversarialStochasticity works the same way, for the same reason.
        // A BUG worth recording: these were initially Integer.MIN_VALUE as an "inactive"
        // sentinel, and the test was `depth > permittedFromDepth`. With MIN_VALUE that
        // comparison is `0 > -2147483648`, which is TRUE — so every line in every file was
        // exempt from the first line of the scan and the auditor reported zero findings,
        // i.e. it passed. A sentinel must not be a value the real domain can hold, and
        // MIN_VALUE is a value depth could legitimately take.
        // `entered` matters because a multi-line declaration may open with no brace at all:
        // `@ArticleIPermitted` on `public record CycleEvent(` is followed by a line that adds
        // nothing to the depth. Without tracking entry, the exemption cleared on that line
        // before the record body opened, and the wall-clock sites inside it stayed
        // UNCLASSIFIED. A member is only finished once it has been ENTERED and returned.
        boolean permittedActive = false;
        boolean adversarialActive = false;
        boolean permittedEntered = false;
        boolean adversarialEntered = false;
        int permittedFromDepth = 0;
        int adversarialFromDepth = 0;
        int depth = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.contains("ArticleIPermitted")) {
                // A NESTED annotation must not clear `entered`. The import line matches first,
                // making the whole type active, and the method annotation inside it re-arms the
                // baseline at the current depth — resetting `entered` there made the method body
                // look un-entered and the site inside it stayed UNCLASSIFIED.
                // Re-arm the baseline ONLY on a fresh activation. When a type is already
                // annotated and a method inside it is annotated too, re-arming at the method's
                // own depth would put the baseline AT the method, so the method body would not
                // read as deeper than it and the site inside it stayed UNCLASSIFIED.
                if (!permittedActive) {
                    permittedEntered = false;
                    permittedFromDepth = depth;
                }
                permittedActive = true;
                continue;
            }
            if (line.contains("@AdversarialStochasticity")) {
                adversarialActive = true;
                adversarialEntered = false;
                adversarialFromDepth = depth;
                continue;
            }
            int opens = count(line, '{');
            int closes = count(line, '}');
            boolean inPermittedMember = permittedActive && depth > permittedFromDepth;
            boolean exempt = inPermittedMember
                    || (adversarialActive && depth > adversarialFromDepth);

            if (inPermittedMember) {
                // Still RECORDED, just not blocking. Silently skipping an annotated site would
                // make the report understate the debt it claims to have adjudicated — the count
                // would fall without any record of what fell.
                if (!line.startsWith("import ")) {
                    classifyAsPermitted(relPath, i + 1, line, out);
                }
            } else if (!exempt) {
                if (line.isEmpty()) {
                    continue;
                }
                for (ForbiddenSymbol fs : FORBIDDEN_SYMBOLS) {
                    if (line.contains("import ") && line.contains(fs.prefix())) {
                        out.add(Violation.violation(relPath, i + 1, fs.prefix(), fs.reason()));
                    }
                }
                if (!line.startsWith("import ")) {
                    classify(relPath, i + 1, line, out);
                }
            }

            depth += opens - closes;
            if (permittedActive && depth > permittedFromDepth) {
                permittedEntered = true;
            }
            if (adversarialActive && depth > adversarialFromDepth) {
                adversarialEntered = true;
            }
            if (permittedActive && permittedEntered && depth <= permittedFromDepth) {
                permittedActive = false;
                permittedEntered = false;
            }
            if (adversarialActive && adversarialEntered && depth <= adversarialFromDepth) {
                adversarialActive = false;
                adversarialEntered = false;
            }
        }
    }

    /**
     * Classify a line inside a member annotated {@link ArticleIPermitted}.
     *
     * <p>Recorded rather than dropped, and promoted to PERMITTED rather than left UNCLASSIFIED,
     * so the report accounts for every wall-clock read it saw. The count falling without a
     * corresponding record is how a debt gets mistaken for a fix.</p>
     */
    private static void classifyAsPermitted(String relPath, int lineNo, String line,
                                            List<Violation> out) {
        if (WALL_CLOCK.matcher(line).find()) {
            out.add(Violation.permitted(relPath, lineNo, "wall-clock", "Annotated permitted",
                    "member annotated @ArticleIPermitted", false));
        } else if (UNSEEDED_RANDOM.matcher(line).find() || line.contains("Math.random")
                || line.contains("ThreadLocalRandom")) {
            out.add(Violation.violation(relPath, lineNo, "unseeded-random",
                    "Unseeded randomness is never excusable, not even in a permitted member"));
        } else if (SEEDED_RANDOM.matcher(line).find()) {
            out.add(Violation.permitted(relPath, lineNo, "new Random(seed)", "Seeded Random",
                    SEEDED_RANDOM_RULE, true));
        }
    }

    /** Apply one line's findings to the report. Split out so the exemption logic above stays readable. */
    private static void classify(String relPath, int lineNo, String line, List<Violation> out) {
        if (line.contains("Math.random") || line.contains("ThreadLocalRandom")
                || UNSEEDED_RANDOM.matcher(line).find()) {
            out.add(Violation.violation(relPath, lineNo,
                    line.contains("Math.random") ? "Math.random"
                            : line.contains("ThreadLocalRandom") ? "ThreadLocalRandom"
                            : "new Random()",
                    "Unseeded randomness is forbidden outside adversarial mode "
                            + "(CONSTITUTION I)"));
        } else if (SEEDED_RANDOM.matcher(line).find()) {
            // Article I permits seeded Random in the cognitive layer. Classified BY RULE rather
            // than site by site, because the rule is already written down and the alternative
            // is recording the same sentence 59 times.
            out.add(Violation.permitted(relPath, lineNo, "new Random(seed)",
                    "Seeded Random", SEEDED_RANDOM_RULE, true));
        }
        if (WALL_CLOCK.matcher(line).find()) {
            // Not decidable from text: an audit-log timestamp and a rate-limit window are the
            // same call. So it BLOCKS until a human annotates ArticleIPermitted or the clock is
            // injected — see InputFilterGuard, which was the one real case.
            out.add(Violation.unclassified(relPath, lineNo, "wall-clock",
                    "Wall-clock in a decision-path package. If this only records an audit "
                            + "field, annotate the member with @ArticleIPermitted; if it "
                            + "influences a decision, inject a clock."));
        }
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int k = 0; k < s.length(); k++) {
            if (s.charAt(k) == c) {
                n++;
            }
        }
        return n;
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
        sb.append(String.format("Files scanned:  %d%n", report.filesScanned()));
        sb.append(String.format("Violations:     %d%n", report.violationCount()));
        sb.append(String.format("  BLOCKING:     %d%n", report.blockingCount()));
        sb.append(String.format("  UNCLASSIFIED: %d  (wall-clock nobody has adjudicated)%n",
                report.unclassifiedCount()));
        sb.append(String.format("  PERMITTED:    %d  (adjudicated or classified by rule)%n",
                report.permittedCount()));
        sb.append(String.format("Verdict:        %s%n",
                report.nonCompliantCount() == 0 ? "PASS" : "FAIL"));

        List<Violation> blocking = report.violations().stream()
                .filter(v -> v.severity() == Severity.VIOLATION).toList();
        if (!blocking.isEmpty()) {
            sb.append("\n--- BLOCKING (confirmed breach of Article I) ---\n");
            blocking.forEach(v -> sb.append("  ").append(v).append('\n'));
        }
        List<Violation> unknown = report.violations().stream()
                .filter(v -> v.severity() == Severity.UNCLASSIFIED && v.permitReason() == null)
                .toList();
        if (!unknown.isEmpty()) {
            sb.append("\n--- UNCLASSIFIED (each needs an injected clock or @ArticleIPermitted) ---\n");
            unknown.forEach(v -> sb.append("  ").append(v).append('\n'));
        }
        List<Violation> permitted = report.violations().stream()
                .filter(v -> v.permitReason() != null).toList();
        if (!permitted.isEmpty()) {
            sb.append("\n--- PERMITTED (adjudicated, with reason) ---\n");
            permitted.forEach(v -> sb.append("  ").append(v).append('\n'));
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