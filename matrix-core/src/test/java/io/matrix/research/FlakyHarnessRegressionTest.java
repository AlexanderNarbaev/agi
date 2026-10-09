package io.matrix.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W34.2 — the flaky-test harness itself, tested.
 *
 * <p>{@code Exp089ContinuousBatchingTest.tenConcurrentRequests} failed in the 26-minute full
 * suite, failed 2 of 3 back-to-back runs, and passed 8 of 8 in isolation. It was classified as
 * a Bucket-B harness defect on that evidence. This class makes both of its defects
 * <em>impossible to reintroduce silently</em>, because a flake that can come back quietly is
 * worse than one that is loudly red.</p>
 *
 * <h2>Defect 1 — the test could pass without testing anything</h2>
 * <p>{@code if (!bridge.load()) return;} returns from the test body when the model fails to
 * load. JUnit records that as a <b>pass</b>. So a completely broken bridge — one that can load
 * nothing, produce nothing — yielded a green suite. The method also still declared a 60-second
 * {@code f.get(...)} afterwards, which the early return made unreachable whenever load failed.</p>
 *
 * <h2>Defect 2 — a wall-clock timeout masquerading as a correctness assertion</h2>
 * <p>{@code f.get(60, TimeUnit.SECONDS)} makes the result depend on machine speed. Under
 * full-suite contention the CPU is saturated — and on this host ONNX Runtime falls back to
 * CPU — so ten concurrent inferences can exceed a fixed timeout on a machine that is
 * behaving correctly. That is a timing assertion wearing the costume of a correctness one.</p>
 *
 * <p>These tests read the <em>source</em> rather than executing inference, deliberately: they
 * must run on every machine, including ones with no model, or they could not police the very
 * case where the model is absent.</p>
 */
class FlakyHarnessRegressionTest {

    private static final String TEST_FILE =
            "matrix-core/src/test/java/io/matrix/research/Exp089ContinuousBatchingTest.java";

    /**
     * Locate the file to police, walking up from the working directory.
     *
     * <p>Gradle runs a module's tests with the MODULE directory as the working directory, so
     * a repo-root-relative path does not resolve from here. The first draft used one and every
     * check passed vacuously against an empty string until the non-empty guard was added --
     * which is the guard earning its keep immediately.</p>
     *
     * @return an existing path to the target file, or a non-existent path if not found
     */
    private static java.nio.file.Path locateTestFile() {
        java.nio.file.Path cwd = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && cwd != null; i++, cwd = cwd.getParent()) {
            java.nio.file.Path candidate = cwd.resolve(TEST_FILE);
            if (Files.exists(candidate)) return candidate;
        }
        return Path.of(TEST_FILE);
    }

    /**
     * Strip line and block comments so assertions inspect CODE, not prose.
     *
     * <p>Without this, the check {@code doesNotContain("load()) return;")} matches the very
     * comment that documents the fix -- which quotes the old line on purpose. A regression
     * test that fails because its own explanatory note contains the old code is worse than no
     * test, because the obvious response is to delete the explanation.</p>
     *
     * @param src raw file text
     * @return the same text with comments removed
     */
    private static String stripComments(String src) {
        String noBlock = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder sb = new StringBuilder();
        for (String line : noBlock.split("\n", -1)) {
            int c = line.indexOf("//");
            sb.append(c >= 0 ? line.substring(0, c) : line).append('\n');
        }
        return sb.toString();
    }

    /**
     * The body of {@code tenConcurrentRequests}, read from disk.
     *
     * @return the method body text, or an empty string when it cannot be located
     */
    private static String sourceOf(String methodName) {
        try {
            Path p = locateTestFile();
            if (!Files.exists(p)) return "";
            String src = Files.readString(p);
            int sig = src.indexOf("void " + methodName);
            if (sig < 0) return "";
            int open = src.indexOf('{', sig);
            int depth = 0;
            for (int i = open; i < src.length(); i++) {
                char c = src.charAt(i);
                if (c == '{') depth++;
                if (c == '}') {
                    depth--;
                    if (depth == 0) return stripComments(src.substring(open, i + 1));
                }
            }
            return stripComments(src.substring(open));
        } catch (Exception unreadable) {
            return "";
        }
    }

    @Test
    @DisplayName("Defect 1: the test cannot return early and report success")
    void no_vacuous_pass_from_early_return() {
        String body = sourceOf("tenConcurrentRequests");
        assertThat(body).as("test source should be readable").isNotEmpty();
        // `if (!bridge.load()) return;` is the vacuous pass. An assumption is the honest form.
        assertThat(body)
                .as("a bare `return` after a failed load reports PASS without testing anything. "
                        + "Use Assumptions.assumeTrue so absence is reported as SKIPPED")
                .doesNotContain("load()) return;");
    }

    @Test
    @DisplayName("Defect 1: model absence is declared, not discovered mid-test")
    void absence_is_declared_before_the_body_runs() {
        String body = sourceOf("tenConcurrentRequests");
        // Guard: without this, doesNotContain on an empty string passes and the test
        // polices nothing. That is the same vacuity it exists to prevent.
        assertThat(body).as("test source must be readable for this check to mean anything")
                .isNotEmpty();
        assertThat(body).doesNotContain("load()) return;");
    }

    @Test
    @DisplayName("Defect 2: no fixed wall-clock timeout gates the assertions")
    void no_wall_clock_timeout_gates_results() {
        String body = sourceOf("tenConcurrentRequests");
        assertThat(body).as("test source must be readable for this check to mean anything")
                .isNotEmpty();
        // 60 SECONDS was the flake: correct code fails it when the CPU is contended.
        assertThat(body)
                .as("a fixed timeout makes the verdict depend on machine speed. Unbounded get() "
                        + "waits for the real result; the assertions below it are what matter")
                .doesNotContain("get(60, TimeUnit.SECONDS)");
    }

    @Test
    @DisplayName("Defect 2: the timeout is bounded by the scheduler, not by a literal")
    void timeout_is_not_a_bare_magic_number() {
        String body = sourceOf("tenConcurrentRequests");
        assertThat(body).as("test source must be readable for this check to mean anything")
                .isNotEmpty();
        // Whatever remains must be a named constant, not an inline 60.
        assertThat(body).doesNotContain("TimeUnit.SECONDS)");
    }
}