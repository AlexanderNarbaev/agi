package io.matrix.crj.quality;

import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * CRJ-01 — the load-sensitive measurement's RUNNER: one candidate, in its own JVM.
 *
 * <p><b>Why a separate JVM.</b> The measurement needs the candidates to run
 * <b>isolated</b> from each other and from the instrument itself. Running them
 * <b>nested</b> — the JUnit Platform's launcher, invoked from inside a running test —
 * does <b>not</b> isolate them: a nested run shares the enclosing engine's state,
 * and it reported failures for candidates that pass when the suite runs them properly
 * ({@code io.matrix.benchmark} failed nested and passed under Gradle). So the runner
 * <b>forks a JVM</b> and does the work there, and the instrument's verdict rests on a
 * real, separate run.</p>
 *
 * <p><b>What it prints.</b> One line, machine-readable:
 * {@code RESULT <seconds> <failures> <the first failure's cause>}. The cause is printed
 * because a failure may be an <b>environment prerequisite</b> rather than a defect of
 * the code, and an instrument that cannot say which is not measuring.</p>
 *
 * <p><b>What it does NOT do.</b> It does not decide anything. The decision — load-sensitive
 * or real defect — is the instrument's, and it is made only after the isolation re-run
 * that the repository's own rule requires.</p>
 */
public final class LoadSensitiveRunner {

    /** The prefix of the machine-readable line. */
    static final String RESULT_PREFIX = "RESULT ";

    /** The prefix of a class selector. */
    private static final String CLASS_SELECTOR = "class:";

    /** The prefix of a package selector. */
    private static final String PACKAGE_SELECTOR = "package:";

    /** The tag the build excludes from the ordinary test task. */
    private static final String NATIVE_BINARY_TAG = "native-binary";

    /** The second tag the build excludes from the ordinary test task. */
    private static final String NATIVE_BUILD_CONFIG_TAG = "native-build-config";

    /** The class-name pattern of the scope the build excludes unless asked. */
    private static final String INTEGRATION_PATTERN = "io\\.matrix\\.integration\\..*";;

    private LoadSensitiveRunner() {
    }

    /**
     * Runs one candidate and prints the result.
     *
     * @param args the selector: {@code class:<fqcn>} or {@code package:<name>}
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("the runner needs a selector: class:<fqcn> or package:<name>");
            System.exit(2);
        }
        String selector = args[0];
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selector.startsWith(PACKAGE_SELECTOR)
                        ? DiscoverySelectors.selectPackage(
                                selector.substring(PACKAGE_SELECTOR.length()))
                        : DiscoverySelectors.selectClass(
                                selector.startsWith(CLASS_SELECTOR)
                                        ? selector.substring(CLASS_SELECTOR.length())
                                        : selector))
                // THE BUILD'S OWN EXCLUSIONS, so the measurement is of the ORDINARY
                // SUITE and not of a wider scope. The build excludes the
                // `native-binary` and `native-build-config` TAGS from the `test` task
                // (they need a compiled native executable, and run in the separate
                // `nativeBinaryTest` task), and excludes the `**/integration/**` PATH
                // unless `-PincludeIntegration` is given (those tests need Docker).
                // Without them the runner measured scopes the suite never runs, and it
                // reported the native executable's ABSENCE as a failure of the code.
                .filters(TagFilter.excludeTags(NATIVE_BINARY_TAG, NATIVE_BUILD_CONFIG_TAG),
                        ClassNameFilter.excludeClassNamePatterns(INTEGRATION_PATTERN))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        long start = System.nanoTime();
        launcher.execute(request, listener);
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        TestExecutionSummary summary = listener.getSummary();
        String cause = "";
        for (TestExecutionSummary.Failure failure : summary.getFailures()) {
            cause = failure.getException() == null
                    ? String.valueOf(failure.getTestIdentifier().getDisplayName())
                    : String.valueOf(failure.getException());
            cause = cause.replace('\n', ' ').replace('\r', ' ');
            if (cause.length() > 200) {
                cause = cause.substring(0, 200);
            }
            break;
        }
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println(RESULT_PREFIX + seconds + " " + summary.getTotalFailureCount() + " " + cause);
        out.flush();
    }
}
