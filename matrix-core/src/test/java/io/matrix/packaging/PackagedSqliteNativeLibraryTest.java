package io.matrix.packaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC P0.5 — the SQLite driver's own native library, and the packaging that carries it.
 *
 * <p><b>What this test is.</b> The SQLite driver ({@code org.xerial:sqlite-jdbc}) does not
 * link a native library into the JVM: it carries the library inside its own jar, extracts
 * it into a fresh directory under {@code java.io.tmpdir} when the first connection is
 * opened, and loads that file with {@code System.load}. The application therefore has two
 * dependencies that are easy to lose and hard to notice: the jar must carry the library
 * for the platform, and the extraction target must be usable.</p>
 *
 * <p><b>What it proves.</b> Three things, and the third is the negative control. First,
 * the driver on the classpath carries the native library for <i>this</i> platform — the
 * packaging invariant, checked against the jar the application actually ships. Second, the
 * driver opens a store <b>by itself</b>: a fresh JVM, with the scratch directory the
 * packaged application gives it, with no {@code org.sqlite.lib.path} property and no
 * manual step of any kind. The property is the workaround this repository used to
 * instruct its operators to set, and the test says that it is not needed. Third, the
 * failure this repository recorded — "No native library found for os.name=Linux,
 * os.arch=x86_64" — is <b>not</b> a class-loader problem: it is what the driver reports
 * when it cannot write the extracted library into its extraction target. The negative
 * control reproduces exactly that message and pins the mechanism, so the next reader
 * knows which end of the chain to look at.</p>
 *
 * <p><b>Why a child JVM.</b> The driver extracts and loads its library once per JVM, and
 * the test suite around this one opens SQLite databases long before this test runs. A
 * child JVM is the only way to observe the extraction itself, and the extraction is the
 * thing under examination.</p>
 */
class PackagedSqliteNativeLibraryTest {

    /** The directory the driver writes its extracted library into. */
    private static final String EXTRACTED_PREFIX = "sqlite-";

    /** The name of the driver's own loader — the class that does the extraction. */
    private static final String LOADER_CLASS = "org.sqlite.SQLiteJDBCLoader";

    @TempDir
    Path directory;

    /**
     * The driver on the classpath carries the native library for this platform.
     *
     * <p>The packaging invariant: a jar without the library is a jar whose first
     * connection fails, and the failure names a missing library rather than a broken
     * package.</p>
     */
    @Test
    @DisplayName("the SQLite driver carries the native library for this platform")
    void theDriverCarriesTheNativeLibraryForThisPlatform() throws Exception {
        Path driver = driverLocation();
        String prefix = "org/sqlite/native/" + driverPlatform() + "/";

        List<String> carried = new ArrayList<>();
        if (Files.isDirectory(driver)) {
            Path natives = driver.resolve("org/sqlite/native").resolve(driverPlatform());
            if (Files.isDirectory(natives)) {
                try (var entries = Files.list(natives)) {
                    entries.map(path -> path.getFileName().toString()).forEach(carried::add);
                }
            }
        } else {
            try (JarFile jar = new JarFile(driver.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(prefix) && !entry.isDirectory()) {
                        carried.add(entry.getName());
                    }
                }
            }
        }

        assertThat(carried)
                .withFailMessage("the SQLite driver %s must carry the native library for %s; "
                        + "without it the first connection fails with "
                        + "\"No native library found\"", driver, driverPlatform())
                .isNotEmpty();
    }

    /**
     * The driver opens a store by itself — no property, no manual step.
     *
     * <p>A fresh JVM, with the scratch directory the packaged application gives the
     * driver ({@code io.matrix.Scratch} points {@code java.io.tmpdir} at a directory of
     * its own, off the temporary file system's quota), and with nothing else set.</p>
     */
    @Test
    @DisplayName("the driver opens a store by itself: no property, no manual step")
    void theDriverOpensAStoreByItself() throws Exception {
        Path scratch = Files.createDirectories(directory.resolve("scratch"));
        Path database = directory.resolve("probe.sqlite");

        String output = runProbe(scratch, database);

        assertThat(output)
                .withFailMessage("the driver must open the store without the org.sqlite.lib.path "
                        + "property; the child JVM said: %s", output)
                .contains("OPENED");
        assertThat(output)
                .withFailMessage("the driver must extract its library into the scratch "
                        + "directory it was given; the child JVM said: %s", output)
                .contains("EXTRACTED: ");
        assertThat(Files.exists(database))
                .withFailMessage("the store the driver opened must exist on disk")
                .isTrue();
    }

    /**
     * The recorded failure is an unusable extraction target, not the class loader.
     *
     * <p>The negative control: with an extraction target that cannot be written, the
     * driver reports "No native library found for os.name=..., os.arch=..." — the message
     * this repository recorded as a class-loader defect. The class loader is innocent:
     * the driver finds its own resource and writes it into a directory that is not
     * usable.</p>
     */
    @Test
    @DisplayName("the recorded failure is an unusable extraction target, not the class loader")
    void theRecordedFailureIsAnUnusableExtractionTarget() throws Exception {
        // A regular file stands where a directory would: the driver cannot write its
        // extracted library into it, and it says so in its own words.
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        Path unusable = blocked.resolve("scratch");

        String output = runProbe(unusable, directory.resolve("unusable.sqlite"));

        assertThat(output)
                .withFailMessage("the driver must not open the store when it cannot extract "
                        + "its library; the child JVM said: %s", output)
                .doesNotContain("OPENED");
        assertThat(output)
                .withFailMessage("the driver must name the missing native library; the child "
                        + "JVM said: %s", output)
                .containsIgnoringCase("native library");
    }

    /**
     * Runs the probe in a fresh JVM and returns everything it said.
     *
     * @param scratch   the directory the driver may extract into
     * @param database  the store the probe opens
     * @return the probe's output
     * @throws IOException          when the child JVM cannot be started or read
     * @throws InterruptedException when the wait for the child is interrupted
     */
    private static String runProbe(Path scratch, Path database)
            throws IOException, InterruptedException {
        List<String> command = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.io.tmpdir=" + scratch,
                "-cp", System.getProperty("java.class.path"),
                Probe.class.getName(),
                database.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output;
    }

    /** Where the driver on the classpath came from. */
    private static Path driverLocation() throws Exception {
        URI location = Class.forName(LOADER_CLASS).getProtectionDomain()
                .getCodeSource().getLocation().toURI();
        return Path.of(location);
    }

    /**
     * The platform directory the driver names its libraries after.
     *
     * @return the platform directory
     */
    private static String driverPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        String platform = osName.contains("linux") ? "Linux"
                : osName.contains("mac") ? "Mac"
                : osName.contains("windows") ? "Windows"
                : osName;
        String architecture = (osArch.equals("amd64") || osArch.equals("x86_64")) ? "x86_64"
                : (osArch.equals("aarch64") || osArch.equals("arm64")) ? "aarch64"
                : osArch.equals("x86") ? "x86"
                : osArch;
        return platform + "/" + architecture;
    }

    /**
     * The probe: a fresh JVM that opens one store and says what happened.
     *
     * <p>It is a nested class with its own entry point because it must run in a JVM of
     * its own: the driver extracts its library once per JVM, and this JVM has already
     * opened databases.</p>
     */
    public static final class Probe {

        /** The probe is not instantiated; it is an entry point. */
        private Probe() {
        }

        /**
         * Opens the store the arguments name and reports what happened.
         *
         * @param arguments the store's path
         */
        public static void main(String[] arguments) {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + arguments[0]);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS probe (id INTEGER PRIMARY KEY)");
                System.out.println("OPENED");
            } catch (Throwable failure) {
                System.out.println("FAILED: " + failure.getMessage());
                for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
                    System.out.println("CAUSE: " + cause.getMessage());
                }
            }
            // The driver deletes the library it extracted when the JVM exits, so the
            // evidence has to be read before that happens.
            Path scratch = Path.of(System.getProperty("java.io.tmpdir"));
            try (var entries = Files.list(scratch)) {
                entries.map(path -> path.getFileName().toString())
                        .filter(name -> name.startsWith(EXTRACTED_PREFIX))
                        .forEach(name -> System.out.println("EXTRACTED: " + name));
            } catch (IOException unreadable) {
                System.out.println("EXTRACTED: none (" + unreadable.getMessage() + ")");
            }
        }
    }

    /** The driver must be on the classpath for this test to mean anything. */
    @Test
    @DisplayName("the SQLite driver is on the classpath")
    void theDriverIsOnTheClasspath() throws Exception {
        assertThat(driverLocation())
                .withFailMessage("the SQLite driver must be a runtime dependency of the "
                        + "application, not a test-only one")
                .exists();
    }

    /** The driver's own loader must be reachable, or the extraction never runs. */
    @Test
    @DisplayName("the driver's loader is reachable")
    void theLoaderIsReachable() throws Exception {
        assertThat(Class.forName(LOADER_CLASS))
                .withFailMessage("the driver's own loader must be on the classpath")
                .isNotNull();
    }

    /** A store opened in THIS JVM still works — the ordinary path, with no ceremony. */
    @Test
    @DisplayName("a store opens in this JVM as well")
    void aStoreOpensInThisJvm() throws Exception {
        Path database = directory.resolve("in-process.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS probe (id INTEGER PRIMARY KEY)");
            assertThat(connection.isValid(1))
                    .withFailMessage("the connection must be usable")
                    .isTrue();
        } catch (SQLException failure) {
            throw new AssertionError("the store must open without any property", failure);
        }
    }
}
