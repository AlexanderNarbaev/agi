package io.matrix.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * W48-02 — the repository root, resolved portably.
 *
 * <p>Several source-integrity tests verify that a production file says what it must say.
 * They used to name the file by an ABSOLUTE path into one developer's home directory:
 * {@code /home/alexandr-narbaev/Projects/agi/...}. That path exists on that machine and on
 * no other, so the tests passed locally and failed on every clean runner — and a test
 * that cannot find the file it verifies proves nothing.
 *
 * <p>Two rules replace it:
 *
 * <ol>
 *   <li>the root comes from the build — the {@code matrix.repoRoot} system property that
 *       the root {@code build.gradle} sets for every test task — and, when that is absent,
 *       from walking up from the working directory to the file that marks the root;</li>
 *   <li>when the root cannot be resolved, or the file to be verified does not exist, this
 *       throws. It never returns a default, never skips and never reports success: a
 *       source-integrity test that silently checks nothing is worse than no test at all.</li>
 * </ol>
 */
public final class RepositoryPaths {

    /** The system property the build sets: the absolute path of the repository root. */
    public static final String ROOT_PROPERTY = "matrix.repoRoot";

    /** The file that marks the repository root when the property is not set. */
    private static final String ROOT_MARKER = "settings.gradle";

    private RepositoryPaths() {
    }

    /**
     * The absolute path of the repository root.
     *
     * @throws IllegalStateException when the root cannot be resolved — deliberately, so
     *         that a test cannot quietly verify nothing.
     */
    public static Path root() {
        String property = System.getProperty(ROOT_PROPERTY);
        if (property != null && !property.isBlank()) {
            Path candidate = Path.of(property).toAbsolutePath().normalize();
            if (!Files.isDirectory(candidate)) {
                throw new IllegalStateException(
                        "the repository root property " + ROOT_PROPERTY
                                + " points at a directory that does not exist: " + candidate);
            }
            return candidate;
        }
        Path candidate = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && candidate != null; depth++) {
            if (Files.isRegularFile(candidate.resolve(ROOT_MARKER))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "the repository root could not be resolved: set -D" + ROOT_PROPERTY
                        + "=<path to the repository>, or run the test from inside the repository");
    }

    /**
     * The absolute path of a source file under the repository root.
     *
     * @param relativePath the path, relative to the repository root
     * @throws IllegalStateException when the file does not exist — the test that asked for
     *         it must fail, not pass without checking.
     */
    public static Path source(String relativePath) {
        Path path = root().resolve(relativePath).normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "the source file this test verifies does not exist: " + path);
        }
        return path;
    }

    /**
     * The text of a source file under the repository root.
     *
     * @param relativePath the path, relative to the repository root
     * @throws IllegalStateException when the file does not exist or cannot be read.
     */
    public static String sourceText(String relativePath) {
        Path path = source(relativePath);
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("the source file could not be read: " + path, e);
        }
    }

    /**
     * A repository path that may not exist: a runtime artefact, a directory the runtime
     * creates, or a file that only the owner's machine holds.
     *
     * @param relativePath the path, relative to the repository root
     * @return the absolute path, whether or not it exists.
     */
    public static Path maybe(String relativePath) {
        return root().resolve(relativePath).normalize();
    }
}
