package io.matrix.quality;

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
 * RECON-W38.4 — the module lifecycle registry must agree with the build.
 *
 * <p>{@code docs-v2/architecture/MODULE-LIFECYCLE.md} states, per module, what
 * is actually verified about it. A registry that drifts from
 * {@code settings.gradle} is worse than no registry: it becomes a document
 * that asserts things the build does not agree with. This test makes the two
 * views of the same fact mechanically consistent.</p>
 *
 * <p>It asserts:</p>
 * <ol>
 *   <li>every module included in {@code settings.gradle} appears in the
 *       registry;</li>
 *   <li>every module directory in the repository appears in the registry
 *       (a new module cannot be added without being classified);</li>
 *   <li>no module is DORMANT in the registry and simultaneously registered
 *       in {@code settings.gradle};</li>
 *   <li>a module may only be described as verified when the registry names
 *       the command that verifies it.</li>
 * </ol>
 */
class ModuleLifecycleRegistryTest {

    private static final String REGISTRY = "docs-v2/architecture/MODULE-LIFECYCLE.md";

    /** Modules whose directories are not modules (build output, IDE, tooling). */
    private static final Set<String> NOT_MODULES = Set.of(
        "docs-v2", "engineering", "ethics", "formal", "pilots", "scripts",
        "models", "data", "deploy", "tools", "dist", "node_modules", "build",
        "gradle", "src", "wal", "archived"
    );

    @Test
    void every_gradle_module_is_classified_in_the_registry() throws IOException {
        Path root = repoRoot();
        Path settings = root.resolve("settings.gradle");
        assertThat(Files.exists(settings)).as("settings.gradle must exist").isTrue();

        Set<String> included = new LinkedHashSet<>();
        Matcher m = Pattern.compile("^\\s*include\\s+'([^']+)'", Pattern.MULTILINE)
                           .matcher(Files.readString(settings));
        while (m.find()) {
            included.add(m.group(1).replace(':', '/'));
        }
        assertThat(included).as("settings.gradle must declare modules").isNotEmpty();

        String registry = Files.readString(root.resolve(REGISTRY));
        List<String> missing = new ArrayList<>();
        for (String module : included) {
            String leaf = module.substring(module.lastIndexOf('/') + 1);
            if (!registry.contains("`" + module + "`") && !registry.contains("`" + leaf + "`")) {
                missing.add(module);
            }
        }
        assertThat(missing)
            .as("every module registered in settings.gradle must be classified in " + REGISTRY)
            .isEmpty();
    }

    @Test
    void every_module_directory_is_classified_in_the_registry() throws IOException {
        Path root = repoRoot();
        String registry = Files.readString(root.resolve(REGISTRY));
        List<String> unclassified = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (NOT_MODULES.contains(name) || !name.startsWith("matrix-")) {
                    continue;
                }
                if (!registry.contains("`" + name + "`")) {
                    unclassified.add(name);
                }
            }
        }
        assertThat(unclassified)
            .as("every matrix-* module directory must be classified in " + REGISTRY
                + " - a module with no state is a module whose status is an assumption")
            .isEmpty();
    }

    @Test
    void a_dormant_module_is_not_registered_in_the_build() throws IOException {
        Path root = repoRoot();
        String settings = Files.readString(root.resolve("settings.gradle"));
        String registry = Files.readString(root.resolve(REGISTRY));

        // A DORMANT module is one the registry lists under the DORMANT heading.
        int dormantAt = registry.indexOf("## DORMANT");
        int nextHeading = registry.indexOf("## PLANNED");
        assertThat(dormantAt).as("the registry must have a DORMANT section").isGreaterThan(0);
        assertThat(nextHeading).as("the registry must have a PLANNED section").isGreaterThan(dormantAt);
        String dormantSection = registry.substring(dormantAt, nextHeading);

        for (String module : new String[] { "matrix-fpga", "matrix-micro", "matrix-ros2" }) {
            assertThat(dormantSection)
                .as("%s must be listed in the DORMANT section", module)
                .contains("`" + module + "`");
            // The include lines are commented out, so the text "include
            // 'matrix-micro'" is still present in the file. The check has to
            // look at the ACTIVE includes only, which is what the regex
            // matches (a commented line does not start with "include").
            assertThat(activeIncludes(settings))
                .as("%s is DORMANT, so settings.gradle must not register it", module)
                .doesNotContain(module);
        }
    }

    @Test
    void a_verified_module_names_the_command_that_verifies_it() throws IOException {
        Path root = repoRoot();
        String registry = Files.readString(root.resolve(REGISTRY));
        int activeExternalAt = registry.indexOf("## ACTIVE-EXTERNAL");
        int dormantAt = registry.indexOf("## DORMANT");
        assertThat(activeExternalAt).as("the registry must have an ACTIVE-EXTERNAL section")
            .isGreaterThan(0);
        assertThat(dormantAt).isGreaterThan(activeExternalAt);
        String section = registry.substring(activeExternalAt, dormantAt);

        assertThat(section)
            .as("a module described as verified must name the command that verifies it")
            .contains("npx jest --ci");
        assertThat(section)
            .as("the ACTIVE-EXTERNAL section must name the CI job that runs it")
            .contains("web-ui-tests");
    }

    /**
     * The modules actually registered by settings.gradle. A commented-out
     * include line does not start with "include", so it is not matched.
     */
    private static Set<String> activeIncludes(String settings) {
        Set<String> included = new LinkedHashSet<>();
        Matcher m = Pattern.compile("^\\s*include\\s+'([^']+)'", Pattern.MULTILINE)
                           .matcher(settings);
        while (m.find()) {
            included.add(m.group(1).replace(':', '/'));
        }
        return included;
    }

    /** Walks up to the directory that contains settings.gradle. */
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
}
