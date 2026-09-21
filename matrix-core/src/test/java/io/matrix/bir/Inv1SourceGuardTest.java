package io.matrix.bir;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-1 source guard (DESIGN-14, SPEC-002 Критерий A): no direct legacy
 * boolean evaluation outside the BIR path and the whitelisted exceptions.
 *
 * <p>Two layered checks run side by side:
 * <ol>
 *   <li><b>Name-based</b>: classic pattern catch on conventional receiver
 *       names ({@code truthTable}, {@code tt}, {@code tree}, etc.);</li>
 *   <li><b>Type-aware alias</b>: collect local-variable declarations typed
 *       {@code TruthTable}, {@code DecisionTree}, {@code Bir} or any
 *       {@code BirForm} subclass, then flag every {@code <name>.evaluate(}
 *       call on those names. This catches aliased receivers such as
 *       {@code modified.evaluate(…)}, {@code myBir.evaluate(…)}, etc., which
 *       the name-based regex alone would miss.</li>
 * </ol>
 *
 * <p>Both checks honour the file-relative whitelist. The whitelist itself
 * is file-relative (prefix match on the path under
 * {@code matrix-core/src/main/java/io/matrix/}), and now also supports
 * exact-file entries (e.g. {@code compression/TruthTableMinimizer.java}) so
 * individual files outside a package can be carved out.
 *
 * <p>Whitelist rationale mirrors docs/engineering/DESIGN-14-call-site-audit.md:
 * <ul>
 *   <li>{@code bir/} — the BIR implementation itself;</li>
 *   <li>{@code ethics/frozen/} — FROZEN zone (RFC required to touch);</li>
 *   <li>{@code neuron/} internals — legacy structures wrapped by adapters at
 *       consumers, plus SIMD batch utilities pending the JMH-gated
 *       evalBatch swap (W6);</li>
 *   <li>{@code compression/TruthTableMinimizer.java} — offline tooling
 *       (exact file — leaves the rest of compression/ unguarded).</li>
 * </ul>
 */
class Inv1SourceGuardTest {

    private static final Pattern FORBIDDEN_NAMES = Pattern.compile(
            "\\b(truthTable|modified|tt|tree|table|[a-zA-Z]*Tree)\\.evaluate\\s*\\(");

    /** Types whose declared variables must not be invoked with {@code .evaluate(...)}. */
    private static final Pattern TYPED_RECEIVER = Pattern.compile(
            "\\b(?:TruthTable|DecisionTree|Bir|BirForm|TtForm|BddForm|ClauseSetForm)"
                    + "\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static final List<String> WHITELIST_PREFIXES = List.of(
            "bir/",
            "ethics/frozen/",
            "neuron/");

    /** Exact file paths (relative to source root) carved out of the prefix whitelist. */
    private static final List<String> WHITELIST_EXACT_FILES = List.of(
            "compression/TruthTableMinimizer.java");

    @Test
    void noDirectLegacyBooleanEvaluationOutsideWhitelist() throws IOException {
        Path root = locateRoot();
        assertThat(root).as("source root must exist").isNotNull();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
                        if (isWhitelisted(rel)) {
                            return;
                        }
                        List<String> lines = readLines(p);
                        Set<String> typedAliases = collectTypedReceivers(lines);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (FORBIDDEN_NAMES.matcher(line).find()) {
                                violations.add(rel + ":" + (i + 1) + "  [name] " + line.strip());
                                continue;
                            }
                            if (matchesTypedAliasCall(line, typedAliases)) {
                                violations.add(rel + ":" + (i + 1) + "  [typed-alias] "
                                        + line.strip());
                            }
                        }
                    });
        }

        assertThat(violations)
                .as("INV-1: direct legacy .evaluate() found outside BIR/whitelist; "
                        + "migrate via BooleanRuntime/TtForm cache — see "
                        + "docs/engineering/DESIGN-14-call-site-audit.md. First violations:")
                .allSatisfy(v -> v.startsWith("__none__"));
    }

    /** A file passes the whitelist if any prefix matches OR an exact file entry matches. */
    private static boolean isWhitelisted(String rel) {
        for (String prefix : WHITELIST_PREFIXES) {
            if (rel.startsWith(prefix)) {
                return true;
            }
        }
        for (String exact : WHITELIST_EXACT_FILES) {
            if (rel.equals(exact)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scan the file for declarations like {@code TruthTable foo} and return
     * the variable names. Captures both local declarations and field/parameter
     * shapes — every identifier whose declared type is one of the BIR/legacy
     * types becomes a candidate.
     */
    private static Set<String> collectTypedReceivers(List<String> lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String line : lines) {
            // strip line comments and inline strings to avoid spurious matches
            String sanitized = line.replaceAll("//.*$", "");
            Matcher m = TYPED_RECEIVER.matcher(sanitized);
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    /**
     * A line is a typed-alias violation when it contains
     * {@code <name>.evaluate(} for some {@code name} in {@code typedAliases}.
     * Names with length &lt; 2 are ignored to avoid false positives on
     * single-letter loop counters that happen to share a regex character class.
     */
    private static boolean matchesTypedAliasCall(String line, Set<String> typedAliases) {
        for (String name : typedAliases) {
            if (name.length() < 2) {
                continue;
            }
            Pattern call = Pattern.compile("\\b" + Pattern.quote(name) + "\\.evaluate\\s*\\(");
            if (call.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static Path locateRoot() {
        for (Path candidate : List.of(
                Paths.get("matrix-core/src/main/java/io/matrix"),
                Paths.get("src/main/java/io/matrix"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<String> readLines(Path p) {
        try {
            return Files.readAllLines(p);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + p, e);
        }
    }
}
