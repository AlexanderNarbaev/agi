package io.matrix.bir;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-1 source guard (DESIGN-14, SPEC-002 Критерий A): no direct legacy
 * boolean evaluation outside the BIR path and the whitelisted exceptions.
 *
 * <p>Implemented as a plain source-scan test — no ArchUnit dependency
 * required; it runs in the standard test task and therefore in CI.
 *
 * <p>Whitelist rationale mirrors docs/engineering/DESIGN-14-call-site-audit.md:
 * <ul>
 *   <li>{@code bir/} — the BIR implementation itself;</li>
 *   <li>{@code ethics/frozen/} — FROZEN zone (RFC required to touch);</li>
 *   <li>{@code neuron/} internals — legacy structures wrapped by adapters at
 *       consumers, plus SIMD batch utilities pending the JMH-gated
 *       evalBatch swap (W6);</li>
 *   <li>{@code compression/TruthTableMinimizer} — offline tooling.</li>
 * </ul>
 */
class Inv1SourceGuardTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(truthTable|modified|tt|tree|table|[a-zA-Z]*Tree)\\.evaluate\\s*\\(");

    private static final List<String> WHITELIST_PREFIXES = List.of(
            "bir/",
            "ethics/frozen/",
            "neuron/",
            "compression/TruthTableMinimizer");

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
                        for (int i = 0; i < lines.size(); i++) {
                            if (FORBIDDEN.matcher(lines.get(i)).find()) {
                                violations.add(rel + ":" + (i + 1) + "  " + lines.get(i).strip());
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

    private static boolean isWhitelisted(String rel) {
        for (String prefix : WHITELIST_PREFIXES) {
            if (rel.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> readLines(Path p) {
        try {
            return Files.readAllLines(p);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + p, e);
        }
    }
}
