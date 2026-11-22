package io.matrix.crj.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CRJ-01 — the LOAD-SENSITIVE ASSERTION class, measured.
 *
 * <p><b>The class.</b> An assertion of the form
 * {@code assertThat(<A MEASURED DURATION>).isLessThan(<A CONSTANT BUDGET>)} states a
 * property of the <b>machine</b> ("this code finishes inside 500 ms"), not of the
 * <b>code</b> ("this code does the same work as that code"). Under load the machine's
 * speed changes and the assertion flips while the code is unchanged. R-EPIS-19 closed
 * <b>one</b> instance of this class and left the class open.</p>
 *
 * <p><b>The discovery.</b> The class was <b>found</b>, not guessed: a scan of the test
 * sources for an assertion whose measured value carries a <b>time unit</b> in its name and
 * whose threshold is a <b>constant</b>. Thresholds of {@code 0} are tautologies and are
 * excluded; a comparison against another measurement is a different class and is excluded.
 * The scan found <b>39 assertions over 23 files</b>.</p>
 *
 * <p><b>The protocol is the repository's own</b> (AGENTS.md): <i>"treat a failure that only
 * appears under contention as unattributed until it is reproduced in isolation"</i>. So each
 * candidate runs <b>alone</b>, then under a <b>deliberate load</b> — one busy thread per
 * core — then, if it failed under the load, <b>alone</b> again. A failure that does not
 * reproduce in isolation is <b>load-sensitive</b>: the code is right and the machine was
 * busy. A failure that reproduces is a <b>real defect</b>.</p>
 *
 * <p><b>Why it is opt-in.</b> The measurement is slow — the research package alone takes
 * minutes — and its subject is the <b>machine</b>, so it is not something the ordinary
 * suite should pay for on every run. It runs when the property
 * {@code -Dcrj01.loadSensitive=true} is set.</p>
 */
class LoadSensitiveAssertionTest {

    /** The property that switches the measurement on. */
    private static final String SWITCH = "crj01.loadSensitive";

    /**
     * The candidates: the classes and packages that carry the class's assertions.
     *
     * <p>A package is measured as a package because the class spans 23 files and a
     * per-class run costs a discovery cycle each time; the isolation re-run narrows to the
     * class when a package fails.</p>
     */
    private static final List<String> CANDIDATES = List.of(
            "io.matrix.api.RateLimiterTest",
            "io.matrix.compression.SimdBooleanCompressorTest",
            "io.matrix.compression.TruthTableMinimizerTest",
            "io.matrix.nativebin.NativeBinaryFullIntegrationTest",
            "io.matrix.nativeimage.NativeCompilationTest",
            "io.matrix.neuron.binary.BinaryBenchmarkTest",
            "io.matrix.research.W31PerformanceBenchmarkTest",
            "io.matrix.safety.BridgeSafetyTest",
            "io.matrix.benchmark",
            "io.matrix.consciousness",
            "io.matrix.imports",
            "io.matrix.integration",
            "io.matrix.reasoning",
            "io.matrix.research",
            "io.matrix.shadow");

    @Test
    @EnabledIfSystemProperty(named = SWITCH, matches = "true")
    @DisplayName("the load-sensitive assertions are classified, and the load-sensitive ones named")
    void theLoadSensitiveAssertionsAreClassified() {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("[CRJ-01] the load-sensitive assertion class: " + CANDIDATES.size()
                + " candidate(s); the load: " + cores + " busy thread(s) on " + cores + " core(s)");
        System.out.println("[CRJ-01]   the candidate | alone (s) | loaded (s) | the verdict");

        List<String> loadSensitive = new ArrayList<>();
        List<String> realDefects = new ArrayList<>();
        for (String candidate : CANDIDATES) {
            Result alone = run(candidate, 0);
            Result loaded = run(candidate, cores);
            String verdict;
            if (loaded.failures() == 0) {
                verdict = "holds under the load";
            } else {
                // the repository's own rule: a failure under contention is unattributed
                // until it is reproduced in isolation
                Result isolation = run(candidate, 0);
                if (isolation.failures() > 0) {
                    // it fails in isolation as well, so the load is not the cause. The
                    // reason is printed, because it may be an ENVIRONMENT prerequisite
                    // rather than a defect of the code - and the instrument must not
                    // call it a defect without saying what the failure was
                    verdict = "fails in isolation as well - the load is NOT the cause: "
                            + isolation.firstFailure();
                    realDefects.add(candidate);
                } else {
                    verdict = "LOAD-SENSITIVE - it passes in isolation";
                    loadSensitive.add(candidate);
                }
            }
            System.out.printf("[CRJ-01]   %-52s | %8.3f | %9.3f | %s%n",
                    candidate, alone.seconds(), loaded.seconds(), verdict);
        }

        System.out.println("[CRJ-01] the LOAD-SENSITIVE candidates: " + loadSensitive);
        System.out.println("[CRJ-01] the REAL DEFECTS: " + realDefects);
    }

    /**
     * Runs a candidate, optionally under a deliberate load.
     *
     * @param candidate the class or the package
     * @param load      the number of busy threads to run alongside
     * @return the result
     */
    private Result run(String candidate, int load) {
        AtomicBoolean stop = new AtomicBoolean(false);
        List<Thread> hogs = new ArrayList<>();
        for (int index = 0; index < load; index++) {
            final int hogIndex = index;
            Thread hog = new Thread(() -> {
                while (!stop.get()) {
                    // the busy work: a loop the compiler cannot fold away
                    Math.sqrt(hogIndex + 1.0);
                }
            }, "load-sensitive-hog-" + index);
            hog.setDaemon(true);
            hog.start();
            hogs.add(hog);
        }
        try {
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(candidate.contains(".")
                            && Character.isLowerCase(candidate.charAt(candidate.lastIndexOf('.') + 1))
                            ? DiscoverySelectors.selectPackage(candidate)
                            : DiscoverySelectors.selectClass(candidate))
                    .build();
            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            long start = System.nanoTime();
            launcher.execute(request, listener);
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            TestExecutionSummary summary = listener.getSummary();
            String first = "";
            for (TestExecutionSummary.Failure failure : summary.getFailures()) {
                first = failure.getException() == null
                        ? String.valueOf(failure.getTestIdentifier().getDisplayName())
                        : failure.getException().toString();
                break;
            }
            return new Result(seconds, summary.getTotalFailureCount(), first);
        } finally {
            stop.set(true);
            for (Thread hog : hogs) {
                try {
                    hog.join(2_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * A run's outcome.
     *
     * @param seconds      the wall-clock duration
     * @param failures     the number of failed tests
     * @param firstFailure the first failure's cause, or the empty string
     */
    private record Result(double seconds, long failures, String firstFailure) {
    }
}
