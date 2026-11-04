package io.matrix.cognitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * W45.1 — the deterministic fault injection point of the request lifecycle.
 *
 * <h2>What it does</h2>
 * <p>When the system property {@code w45.failpoint} names a checkpoint, the process reaches that
 * checkpoint, writes a marker file, and then STOPS MAKING PROGRESS — it blocks until it is
 * killed. The integration harness watches for the marker and sends {@code SIGKILL} to the exact
 * child JVM, so the process dies at a chosen point with no cleanup, no shutdown hook and no
 * flush of anything that was not already committed.</p>
 *
 * <h2>Why it blocks instead of killing itself</h2>
 * <p>A process that halted itself would not be a SIGKILL: it would run its shutdown hooks and
 * its finalizers, and the difference between those two deaths is precisely what this wave is
 * about. Blocking and being killed from outside produces the death the wave is testing, and it
 * makes the point deterministic: the marker file appears only when the checkpoint has really
 * been reached.</p>
 *
 * <h2>Why it is not an endpoint</h2>
 * <p>The failpoint is configured by a system property at start-up. There is no HTTP surface for
 * it, so no request can make the production process stop, and the production behaviour does
 * not depend on timing or on sleeps: without the property the failpoint is absent and the
 * listener does nothing.
 */
public final class RequestFailpoint {

    /** The system property that names the checkpoint to stop at. */
    public static final String FAILPOINT_PROPERTY = "w45.failpoint";

    /** The system property that names the marker file to write when the failpoint is reached. */
    public static final String MARKER_PROPERTY = "w45.failpoint.marker";

    /** The failpoint that is not configured. */
    private static final RequestFailpoint ABSENT = new RequestFailpoint(null, null, Mode.BLOCK);

    /** What the failpoint does when it is reached. */
    public enum Mode {
        /**
         * The process writes the marker and blocks until it is killed from outside.
         *
         * <p>This is the mode of the integration harness: the death is a real
         * {@code SIGKILL}.</p>
         */
        BLOCK,
        /**
         * The process throws at the checkpoint.
         *
         * <p>This is the mode of the unit tests: it injects a failure at an exact point
         * without killing anything.</p>
         */
        THROW
    }

    /** The checkpoint to stop at, or null when no failpoint is configured. */
    private final RequestCheckpoint checkpoint;

    /** The marker file to write, or null when no failpoint is configured. */
    private final Path marker;

    /** What the failpoint does when it is reached. */
    private final Mode mode;

    private RequestFailpoint(RequestCheckpoint checkpoint, Path marker, Mode mode) {
        this.checkpoint = checkpoint;
        this.marker = marker;
        this.mode = mode;
    }

    /**
     * A failpoint that throws at the given checkpoint.
     *
     * <p>For a test that needs a failure at an exact point of the lifecycle without killing
     * a process.</p>
     *
     * @param at the checkpoint to fail at
     * @return the failpoint
     */
    public static RequestFailpoint throwing(RequestCheckpoint at) {
        return new RequestFailpoint(at, null, Mode.THROW);
    }

    /**
     * The failpoint configured by the system properties of this process.
     *
     * @return the configured failpoint, or the absent one
     */
    public static RequestFailpoint fromSystemProperties() {
        String named = System.getProperty(FAILPOINT_PROPERTY);
        if (named == null || named.isBlank()) {
            return ABSENT;
        }
        RequestCheckpoint at;
        try {
            at = RequestCheckpoint.valueOf(named.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ABSENT;
        }
        String markerPath = System.getProperty(MARKER_PROPERTY);
        return new RequestFailpoint(at, markerPath == null || markerPath.isBlank() ? null : Path.of(markerPath),
                Mode.BLOCK);
    }

    /**
     * The checkpoint this failpoint stops at.
     *
     * @return the checkpoint, or empty when no failpoint is configured
     */
    public Optional<RequestCheckpoint> checkpoint() {
        return Optional.ofNullable(checkpoint);
    }

    /**
     * Whether this failpoint stops at the given checkpoint.
     *
     * @param candidate the checkpoint the request has reached
     * @return true when the request must stop here
     */
    public boolean stopsAt(RequestCheckpoint candidate) {
        return checkpoint != null && checkpoint == candidate;
    }

    /**
     * Stops the request at this failpoint: writes the marker and blocks until the process is
     * killed.
     *
     * @param candidate the checkpoint the request has reached
     */
    public void apply(RequestCheckpoint candidate) {
        if (!stopsAt(candidate)) {
            return;
        }
        if (mode == Mode.THROW) {
            throw new IllegalStateException("the failpoint was reached at " + candidate
                    + ": the injected failure of the test");
        }
        writeMarker(candidate);
        blockUntilKilled();
    }

    private void writeMarker(RequestCheckpoint at) {
        if (marker == null) {
            return;
        }
        try {
            Path parent = marker.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(marker, at.name() + "\n" + ProcessHandle.current().pid() + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write the failpoint marker " + marker, e);
        }
    }

    private void blockUntilKilled() {
        // The process is supposed to be killed here, from outside. The block is what makes
        // the kill deterministic: the marker exists only after the checkpoint was reached,
        // and nothing advances between the marker and the kill.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
