package io.matrix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The scratch directory of a run, and the cleanup of the runs before it.
 *
 * <p><b>RECON-W43 — why this exists.</b> The ONNX runtime extracts a native
 * library into a FRESH directory under {@code java.io.tmpdir} for every
 * session load, and never removes it. Measured on this machine: one
 * directory is about 583 MB, and a wave's worth of application starts left
 * fifty-two of them behind — 19.5 GB.
 *
 * <p>That filled the per-user quota of the {@code /tmp} tmpfs, and the
 * consequence was not a slow degradation: every tool that captures its
 * output to a temporary file began to fail with {@code EDQUOT}, so the
 * build, the gates and the shell itself stopped working. The wave found it
 * as an outage rather than as a slow leak.
 *
 * <p>So the application's temporary directory is its OWN: a directory under
 * the data directory, off the tmpfs, and named after the process. The runs
 * before this one are removed at startup — a run's directory is removed only
 * when its process is gone, so a second instance never deletes a live one,
 * and a crashed run is cleaned up by the next start rather than living
 * forever.
 *
 * <p><b>What this is not.</b> It is not a place for anything durable. The
 * data directory is the store's home and this is its scratch: deleting it
 * costs a re-extraction of the native library and nothing else.
 */
public final class Scratch {

    /** The prefix of a run's scratch directory. */
    private static final String RUN_PREFIX = "run-";

    /** The default root of the scratch directories, relative to the working directory. */
    private static final String DEFAULT_ROOT = "data/tmp";

    /** The prefix the ONNX runtime gives to the directory it extracts into. */
    private static final String EXTRACTION_PREFIX = "onnxruntime-java";

    private Scratch() {
    }

    /**
     * Prepares this run's scratch directory and removes the runs before it.
     *
     * <p>A failure here is not fatal: the application starts with whatever
     * temporary directory the JVM was given, which is what it did before
     * this class existed.
     *
     * @return the scratch directory, or null when it could not be prepared
     */
    public static Path prepare() {
        try {
            Path root = Path.of(System.getProperty("matrix.scratch.path", DEFAULT_ROOT));
            Files.createDirectories(root);
            removeTheExtractedLibraries(root);
            removeTheRunsBefore(root);
            Path run = root.resolve(RUN_PREFIX + ProcessHandle.current().pid());
            Files.createDirectories(run);
            System.setProperty("java.io.tmpdir", run.toString());
            return run;
        } catch (IOException | RuntimeException cannotPrepare) {
            System.setProperty("matrix.scratch.prepared", "false");
            return null;
        }
    }

    /** Removes the scratch directories whose process is gone. */
    private static void removeTheRunsBefore(Path root) throws IOException {
        try (Stream<Path> runs = Files.list(root)) {
            runs.filter(path -> path.getFileName().toString().startsWith(RUN_PREFIX))
                    .filter(path -> !isAlive(path.getFileName().toString()))
                    .forEach(Scratch::delete);
        }
    }

    /**
     * Removes the native libraries the ONNX runtime extracted earlier.
     *
     * <p>Unlinking a file that another process has open is SAFE on Linux:
     * the running process keeps its mapping and the space is returned when
     * it exits. So removing another instance's extraction does not break
     * that instance - it only means the space comes back a little later,
     * instead of never.
     *
     * <p>This is what keeps the scratch directory from growing by 583 MB
     * per start. Without it, the directory is a slow leak with a very large
     * step.
     */
    private static void removeTheExtractedLibraries(Path root) throws IOException {
        try (Stream<Path> files = Files.list(root)) {
            files.filter(path -> path.getFileName().toString().startsWith(EXTRACTION_PREFIX))
                    .forEach(Scratch::delete);
        }
    }

    /** Whether the process a run's directory belongs to is still running. */
    private static boolean isAlive(String name) {
        String pid = name.substring(RUN_PREFIX.length());
        try {
            return ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException notAPid) {
            return true;
        }
    }

    /** Removes a directory and everything under it. */
    private static void delete(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A file that cannot be removed is left alone; the next
                    // start will try again.
                }
            });
        } catch (IOException unreadable) {
            // Same: the next start will try again.
        }
    }
}
