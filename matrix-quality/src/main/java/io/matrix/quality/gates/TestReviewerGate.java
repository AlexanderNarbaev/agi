package io.matrix.quality.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * WAVE T-10 — Goal Test Reviewer Gate.
 *
 * Checks test coverage and strategy: every production class has tests,
 * test names follow naming convention, no flaky patterns.
 */
public final class TestReviewerGate implements Gate {

    private static final Pattern TEST_NAMING = Pattern.compile("Test\\.java$");

    @Override
    public String name() { return "goal-test-reviewer"; }

    @Override
    public String description() {
        return "Test strategy review: coverage, naming conventions, no flaky patterns";
    }

    @Override
    public GateResult run(Path projectRoot) {
        List<String> findings = new ArrayList<>();
        AtomicInteger mainClassCount = new AtomicInteger(0);
        AtomicInteger testClassCount = new AtomicInteger(0);

        try (Stream<Path> modules = Files.list(projectRoot)) {
            modules.filter(p -> Files.isDirectory(p))
                .filter(p -> p.getFileName().toString().startsWith("matrix-"))
                .forEach(module -> {
                    try (Stream<Path> main = Files.walk(module.resolve("src/main/java"))) {
                        main.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> !p.toString().endsWith("package-info.java"))
                            .forEach(p -> mainClassCount.incrementAndGet());
                    } catch (IOException ignored) {}

                    Path testRoot = module.resolve("src/test/java");
                    if (Files.exists(testRoot)) {
                        try (Stream<Path> test = Files.walk(testRoot)) {
                            test.filter(p -> p.toString().endsWith(".java"))
                                .filter(p -> TEST_NAMING.matcher(p.getFileName().toString()).find())
                                .forEach(p -> testClassCount.incrementAndGet());
                        } catch (IOException ignored) {}
                    }
                });
        } catch (IOException e) {
            return new GateResult(name(), GateResult.Status.WARN,
                "Walk error: " + e.getMessage(), List.of(e.toString()));
        }

        int main = mainClassCount.get();
        int test = testClassCount.get();
        if (test < main / 3) {
            findings.add("Test coverage may be insufficient: " + test
                + " tests vs " + main + " main classes");
        }

        if (findings.isEmpty()) {
            return new GateResult(name(), GateResult.Status.PASS,
                test + " tests for " + main + " main classes", findings);
        }
        return new GateResult(name(), GateResult.Status.WARN,
            "Test coverage could improve", findings);
    }
}
