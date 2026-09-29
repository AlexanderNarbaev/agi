package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.matrix.brain.runtime.stages.BirInferenceStage;
import io.matrix.brain.runtime.stages.TsetlinStage;

/**
 * RECON-W24/W26 — {@code SimulacrumDefaultOffTest}.
 *
 * <p><b>Why this class exists.</b> {@code MATRIX-MIND-REPORT-V17.md:49} claims
 * this guard is "green (simulacrumEnabled defaults false everywhere)". No such
 * class existed in the repository — the only occurrence of the name anywhere was
 * that markdown row. It was a documented guard with no implementation, i.e. a
 * false claim in a shipped report (logged as D-W20-2).
 *
 * <p>Rather than retract the claim, the guard is implemented so the statement
 * becomes true and stays true. It enforces the anti-regression law: <em>once a
 * simulacrum is disabled it stays off absent an RFC and explicit approval</em>.</p>
 *
 * <p>Three layers of enforcement:</p>
 * <ol>
 *   <li>the two known flags default to {@code false} at runtime;</li>
 *   <li>they are {@code public static non-final} mutable fields, so the test
 *       asserts their declared default rather than whatever a prior test in the
 *       same JVM happened to leave behind;</li>
 *   <li>a source scan proves no production file flips a simulacrum flag to
 *       {@code true} — the enabling must require an explicit, greppable,
 *       reviewable act, and today there is none in {@code src/main}.</li>
 * </ol>
 */
class SimulacrumDefaultOffTest {

    /** Locate the repo root (Gradle runs module tests with the module dir as cwd). */
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

    /** Read a public static boolean's value from a class. */
    private static boolean readFlag(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        assertThat(Modifier.isStatic(f.getModifiers()))
            .as("%s.%s must be static", type.getSimpleName(), name).isTrue();
        assertThat(Modifier.isPublic(f.getModifiers()))
            .as("%s.%s must be public", type.getSimpleName(), name).isTrue();
        return f.getBoolean(null);
    }

    // ---- Layer 1 + 2: the declared defaults --------------------------------

    @Test
    void bir_inference_simulacrum_defaults_off() throws Exception {
        assertThat(readFlag(BirInferenceStage.class, "simulacrumEnabled"))
            .as("BirInferenceStage.simulacrumEnabled must default false (Article I)")
            .isFalse();
    }

    @Test
    void tsetlin_simulacrum_defaults_off() throws Exception {
        assertThat(readFlag(TsetlinStage.class, "simulacrumEnabled"))
            .as("TsetlinStage.simulacrumEnabled must default false (Article I)")
            .isFalse();
    }

    @Test
    void simulacrum_flags_are_mutable_so_tests_can_restore_them() throws Exception {
        // They are intentionally non-final: a test may flip one, and the
        // anti-regression law relies on the declared default being checked rather
        // than a value left behind by another test in the same JVM.
        for (Field f : List.of(
                BirInferenceStage.class.getDeclaredField("simulacrumEnabled"),
                TsetlinStage.class.getDeclaredField("simulacrumEnabled"))) {
            assertThat(Modifier.isFinal(f.getModifiers()))
                .as("%s must not be final, or the default can never be re-asserted",
                    f.getName())
                .isFalse();
        }
    }

    // ---- Layer 2b: the reflective reads above are only sound in sequence ----

    /**
     * RECON-W28 B-5 — makes the ordering precondition explicit instead of implicit.
     *
     * <p>{@link #readFlag} observes a PUBLIC STATIC MUTABLE field, so its answer
     * depends on when it runs. That is currently safe for a verified reason:
     * {@code MindCycleIntegrationTest}, {@code BirInferenceStageSimulacrumTest} and
     * {@code TsetlinStageSimulacrumTest} are the only test classes that set these
     * flags true, and every one of them restores them to false in {@code @AfterEach}
     * (two also in a {@code finally}). JUnit 5 therefore never leaves a flag set
     * between classes.</p>
     *
     * <p>That safety is a property of OTHER test classes' discipline, not of this one.
     * If parallel execution is ever enabled, a mutator's {@code @BeforeEach} can set a
     * flag true while this test reads it, and this guard would fail for a reason that
     * has nothing to do with the code it is meant to protect. So the precondition is
     * asserted here: if someone turns parallelism on, this test tells them exactly
     * what they broke instead of producing a mysterious red.</p>
     *
     * <p>Note the real guard against a production re-enable is
     * {@link #no_production_source_flips_a_simulacrum_flag_true()}, which is a source
     * scan and is immune to test ordering by construction.</p>
     */
    @Test
    void test_execution_is_sequential_so_the_reflective_reads_are_sound() {
        boolean parallel = Boolean.parseBoolean(System.getProperty(
            "junit.jupiter.execution.parallel.enabled", "false"));
        assertThat(parallel)
            .as("parallel JUnit execution would let another test class set a simulacrum "
              + "flag true while SimulacrumDefaultOffTest reads it, making the "
              + "reflective assertions order-dependent. If you enabled parallelism, "
              + "make these two tests @Isolated or read the defaults through a fresh "
              + "URLClassLoader instead of reflection on the live class.")
            .isFalse();
    }

    // ---- Layer 3: no production source enables one -------------------------

    @Test
    void no_production_source_flips_a_simulacrum_flag_true() throws IOException {
        Path root = repoRoot();
        List<String> offenders = new ArrayList<>();
        // An assignment of a literal `true` to any simulacrum switch in src/main
        // would be an unreviewed re-enable. Comments are stripped first so prose
        // about the flag is not mistaken for a use of it.
        Pattern assignment = Pattern.compile(
            "simulacrum[A-Za-z]*\\s*=\\s*true\\b");
        Pattern declaration  = Pattern.compile(
            "boolean\\s+simulacrum[A-Za-z]*\\s*=\\s*true\\b");

        for (String dir : List.of("matrix-core/src/main/java",
                                  "matrix-brain-runtime/src/main/java",
                                  "matrix-api-gateway/src/main/java")) {
            Path src = root.resolve(dir);
            if (!Files.exists(src)) continue;
            try (Stream<Path> files = Files.walk(src)) {
                List<Path> all = files.filter(p -> p.toString().endsWith(".java"))
                                     .toList();
                for (Path p : all) {
                    String code = stripComments(Files.readString(p));
                    Matcher m1 = assignment.matcher(code);
                    if (m1.find()) {
                        offenders.add(dir + "/" + p.getFileName() + " assigns "
                            + m1.group() + " in PRODUCTION code");
                    }
                    Matcher m2 = declaration.matcher(code);
                    if (m2.find()) {
                        offenders.add(dir + "/" + p.getFileName()
                            + " DECLARES a simulacrum switch defaulting to true");
                    }
                }
            }
        }
        assertThat(offenders)
            .as("Once a simulacrum is disabled it stays off absent an RFC "
                + "(anti-regression law)")
            .isEmpty();
    }

    /**
     * Reduce source to CODE STRUCTURE only: // and block comments removed AND
     * string-literal contents blanked.
     *
     * <p>Blanking literals matters. The production trace messages contain the
     * text {@code "simulacrum=true"} as a DATA string — an earlier version of
     * this guard flagged both stage files on that alone, a pure false positive.
     * A structural scan must not read data as code.</p>
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false, inLine = false, inStr = false, inChar = false, esc = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = (i + 1 < src.length()) ? src.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') { inLine = false; out.append(c); }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') { inBlock = false; i++; }
                else if (c == '\n') out.append(c);
                continue;
            }
            if (inStr) {
                // Blank the literal's CONTENT, keep the delimiters.
                if (c == '\n') out.append(' ');
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') { inStr = false; out.append(c); }
                continue;
            }
            if (inChar) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '\'') { inChar = false; out.append(c); }
                continue;
            }
            if (c == '/' && n == '/') { inLine = true; i++; continue; }
            if (c == '/' && n == '*') { inBlock = true; i++; continue; }
            if (c == '"') { inStr = true; out.append(c); continue; }
            if (c == '\'') { inChar = true; out.append(c); continue; }
            out.append(c);
        }
        return out.toString();
    }
}
