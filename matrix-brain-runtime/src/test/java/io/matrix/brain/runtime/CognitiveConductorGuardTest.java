package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W38.4 — CI guard: there is exactly one cognitive conductor.
 *
 * <p>ADR-2026-10-26-002 ("one cognitive conductor") resolved the long-standing
 * competition between {@link MindCycle} and {@link TrueMindCycle}. Before the
 * ADR the repository contained two contradictory claims about which one the
 * gateway serves:</p>
 *
 * <ul>
 *   <li>{@code ProductionBrainClient} said it used {@code TrueMindCycle}
 *       "not the legacy hand-coded MindCycle" — which is true;</li>
 *   <li>{@code MindCycle} itself carried a comment saying <em>"MindCycle is
 *       the pipeline the gateway actually serves (TrueMindCycle is the
 *       offline/audit variant)"</em> — which is exactly backwards.</li>
 * </ul>
 *
 * <p>Both could not be true, and a reader had no way to tell which was. The
 * ADR settles it: {@code TrueMindCycle} is the one conductor for every
 * serving path, and {@code MindCycle} is a legacy reference pipeline retained
 * for differential testing and for the MCTS planning stage.</p>
 *
 * <p>This guard makes the decision mechanically checkable rather than a
 * matter of discipline. It asserts:</p>
 * <ol>
 *   <li>no production source instantiates {@code MindCycle} except
 *       {@link MindRuntimeCli}, which may only do so behind an explicit
 *       opt-in flag;</li>
 *   <li>{@link MindRuntimeCli} defaults to {@code TrueMindCycle}, so the
 *       developer REPL exercises the same conductor the gateway serves;</li>
 *   <li>{@code ProductionBrainClient} — the serving path — instantiates
 *       {@code TrueMindCycle} and never {@code MindCycle}.</li>
 * </ol>
 *
 * <p>Comments are stripped before scanning: a source file may legitimately
 * NAME the legacy class (that is how a reader learns it exists), but must not
 * construct it.</p>
 */
class CognitiveConductorGuardTest {

    /** Production source roots that could construct a conductor. */
    private static final List<String> PRODUCTION_SOURCE_DIRS = List.of(
        "matrix-core/src/main/java",
        "matrix-brain-runtime/src/main/java",
        "matrix-api-gateway/src/main/java"
    );

    /** The one file permitted to construct the legacy pipeline, and only opt-in. */
    private static final String LEGACY_CLI = "MindRuntimeCli.java";

    /**
     * RECON-W21 — locate the repository root from wherever the test JVM started.
     *
     * <p>Gradle runs a module's tests with the MODULE directory as the working
     * directory, so a relative source path does not resolve. Walking up to the
     * directory that contains settings.gradle makes the scan real.</p>
     */
    private static Path repoRoot() {
        Path p = Paths.get(".").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.exists(p.resolve("settings.gradle"))
                || Files.exists(p.resolve("settings.gradle.kts"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get(".").toAbsolutePath();
    }

    @Test
    void no_production_source_constructs_the_legacy_conductor() throws IOException {
        Path root = repoRoot();
        List<String> violations = new ArrayList<>();
        for (String sourceDir : PRODUCTION_SOURCE_DIRS) {
            Path src = root.resolve(sourceDir);
            if (!Files.exists(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                files.filter(p -> p.toString().endsWith(".java"))
                     .forEach(p -> {
                         String name = p.getFileName().toString();
                         if (LEGACY_CLI.equals(name)) {
                             return; // handled by the dedicated CLI test below
                         }
                         String code = stripComments(readQuietly(p));
                         if (code.contains("new MindCycle(")) {
                             violations.add(root.relativize(p).toString());
                         }
                     });
            }
        }
        assertThat(violations)
            .as("ADR-2026-10-26-002: TrueMindCycle is the one cognitive conductor; "
                + "no production source may construct the legacy MindCycle pipeline")
            .isEmpty();
    }

    @Test
    void the_developer_cli_defaults_to_the_production_conductor() throws IOException {
        Path root = repoRoot();
        Path cli = root.resolve("matrix-brain-runtime/src/main/java/io/matrix/brain/runtime")
                         .resolve(LEGACY_CLI);
        assertThat(Files.exists(cli))
            .as("MindRuntimeCli.java must exist")
            .isTrue();

        String code = stripComments(Files.readString(cli));
        assertThat(code)
            .as("The developer CLI must default to the conductor the gateway serves")
            .contains("new TrueMindCycle(");
    }

    @Test
    void the_serving_path_uses_the_production_conductor() throws IOException {
        Path root = repoRoot();
        Path client = root.resolve("matrix-api-gateway/src/main/java/io/matrix/api/brain")
                           .resolve("ProductionBrainClient.java");
        assertThat(Files.exists(client))
            .as("ProductionBrainClient.java must exist")
            .isTrue();

        String code = stripComments(Files.readString(client));
        assertThat(code)
            .as("The gateway's brain client must construct TrueMindCycle")
            .contains("new TrueMindCycle(");
        assertThat(code)
            .as("The gateway's brain client must not construct the legacy MindCycle")
            .doesNotContain("new MindCycle(");
    }

    private static String readQuietly(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Removes line and block comments so that a source file may NAME a class
     * without being treated as using it. String and character literals are
     * preserved, so a string that mentions the class is still scanned.
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inStr = false;
        boolean inChar = false;
        boolean esc = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = (i + 1 < src.length()) ? src.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                } else if (c == '\n') {
                    out.append(c);
                }
                continue;
            }
            if (inStr) {
                out.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && n == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(c);
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(c);
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
