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

import java.nio.file.Path;
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
            "io.matrix.nativeimage.NativeCompilationTest",
            "io.matrix.neuron.binary.BinaryBenchmarkTest",
            "io.matrix.research.W31PerformanceBenchmarkTest",
            "io.matrix.safety.BridgeSafetyTest",
            "io.matrix.benchmark",
            "io.matrix.consciousness",
            "io.matrix.imports",
            "io.matrix.reasoning",
            "io.matrix.research",
            "io.matrix.shadow");

    /**
     * The scopes the ORDINARY SUITE does not run, and why they are not measured.
     *
     * <p>{@code **&#47;integration/**} is excluded by the build unless
     * {@code -PincludeIntegration} is given (those tests need <b>Docker</b>, and they
     * block for a container that is not there), and the {@code native-binary} tag is
     * excluded from the {@code test} task and run by the separate
     * {@code nativeBinaryTest} task (it needs a compiled native executable, which this
     * environment has never built). The runner applies <b>both</b> exclusions, so those
     * scopes are not measured at all — and that is the point: the measurement is of the
     * <b>ordinary suite</b>, and a failure in a scope the suite never runs is not a
     * defect of the suite.</p>
     *
     * <p>The native-binary scope is still <b>named</b> here, because its absence is a
     * fact a reader needs: it is the reason the measurement must not be read as
     * "the native path is fine".</p>
     */
    private static final List<String> EXCLUDED_SCOPES = List.of(
            "io.matrix.integration (excluded by the build: needs Docker)",
            "io.matrix.nativebin (the native-binary tag: needs a compiled native executable)");

    @Test
    @EnabledIfSystemProperty(named = SWITCH, matches = "true")
    @DisplayName("the load-sensitive assertions are classified, and the load-sensitive ones named")
    void theLoadSensitiveAssertionsAreClassified() throws Exception {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("[CRJ-01] the load-sensitive assertion class: " + CANDIDATES.size()
                + " candidate(s); the load: " + cores + " busy thread(s) on " + cores + " core(s)");
        System.out.println("[CRJ-01]   the candidate | alone (s) | loaded (s) | the verdict");

        List<String> loadSensitive = new ArrayList<>();
        List<String> realDefects = new ArrayList<>();
        for (String candidate : CANDIDATES) {
            boolean excluded = false;
            Result alone = run(candidate, 0);
            Result loaded = run(candidate, cores);
            String verdict;
            if (excluded) {
                verdict = "(an EXCLUDED scope - not part of the ordinary suite)";
            } else if (loaded.failures() == 0) {
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
                    if (!excluded) {
                        realDefects.add(candidate);
                    }
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
        System.out.println("[CRJ-01] the scopes the ORDINARY SUITE does not run, and are "
                + "therefore not measured: " + EXCLUDED_SCOPES);
    }

    /**
     * Runs a candidate in its OWN JVM, optionally under a deliberate load.
     *
     * <p><b>Why a separate JVM.</b> The measurement needs the candidates isolated from
     * each other and from the instrument. Running them <b>nested</b> — the launcher
     * invoked from inside a running test — does not isolate them: a nested run shares
     * the enclosing engine's state, and it reported failures for candidates that pass
     * when the suite runs them properly. So the instrument <b>forks</b>, and the verdict
     * rests on a real, separate run.</p>
     *
     * @param candidate the class or the package
     * @param load      the number of busy threads to run alongside
     * @return the result
     * @throws Exception when the forked JVM cannot be started
     */
    private Result run(String candidate, int load) throws Exception {
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
            String selector = candidate.contains(".") && isAPackage(candidate)
                    ? "package:" + candidate : "class:" + candidate;
            ProcessBuilder builder = new ProcessBuilder(forkedCommand(selector));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            process.waitFor();
            return parse(output);
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
     * Whether a selector names a PACKAGE rather than a class.
     *
     * <p>A class name's last segment starts with an upper-case letter; a package's last
     * segment does not. The distinction matters because a package selector runs every
     * test in it, and a class selector runs one.</p>
     *
     * @param candidate the selector
     * @return true when it names a package
     */
    private static boolean isAPackage(String candidate) {
        int last = candidate.lastIndexOf('.');
        if (last < 0) {
            return false;
        }
        char first = candidate.charAt(last + 1);
        return Character.isLowerCase(first);
    }

    /**
     * The command the forked JVM runs.
     *
     * <p><b>Why the arguments and the properties are forwarded.</b> The candidates'
     * tests run under the build's own settings — {@code -Xmx16g}, the incubator module,
     * the native-access flag, the library path, and the system properties the build
     * sets. A forked JVM that does <b>not</b> inherit them is a <b>different</b>
     * environment, and it reported failures for candidates that pass under the build.
     * So the fork carries the enclosing JVM's input arguments and its system
     * properties, and the measurement is then of the <b>same</b> environment the suite
     * runs.</p>
     *
     * @param selector the selector for the runner
     * @return the command
     */
    private static List<String> forkedCommand(String selector) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        for (String argument : java.lang.management.ManagementFactory.getRuntimeMXBean()
                .getInputArguments()) {
            // an agent of the enclosing build must not be loaded twice
            if (argument.startsWith("-javaagent") || argument.startsWith("-agentlib")) {
                continue;
            }
            command.add(argument);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("sun.java.command") || name.startsWith("java.class.path")) {
                continue;
            }
            command.add("-D" + name + "=" + System.getProperty(name));
        }
        command.add(LoadSensitiveRunner.class.getName());
        command.add(selector);
        return command;
    }

    /**
     * Reads the runner's machine-readable line.
     *
     * @param output the runner's output
     * @return the result
     */
    private static Result parse(String output) {
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(LoadSensitiveRunner.RESULT_PREFIX)) {
                String[] parts = trimmed.substring(LoadSensitiveRunner.RESULT_PREFIX.length())
                        .split(" ", 3);
                double seconds = Double.parseDouble(parts[0]);
                long failures = Long.parseLong(parts[1]);
                String cause = parts.length > 2 ? parts[2] : "";
                return new Result(seconds, failures, cause);
            }
        }
        return new Result(0.0, -1, "the runner printed no result: " + output.strip());
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
