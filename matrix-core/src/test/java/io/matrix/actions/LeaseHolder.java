package io.matrix.actions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A second process that holds a root's writer lease.
 *
 * <p>The exclusion the writer claims is between <b>processes</b>, so the test that pins it
 * must use a second process: this class is started as its own JVM, takes the operating-system
 * lock on the lease file, announces that it holds it, and keeps it until the parent closes
 * its standard input. A writer in the parent process must then refuse to write, because the
 * lease is taken.</p>
 *
 * <p>This is test scaffolding, not production code: it lives in the test source set and is
 * never referenced by the application.</p>
 */
final class LeaseHolder {

    private final Process process;
    private final BufferedReader output;

    private LeaseHolder(Process process, BufferedReader output) {
        this.process = process;
        this.output = output;
    }

    /**
     * Starts a holder and waits until it holds the lease.
     *
     * @param lockFile the lease file of the root
     * @return the holder
     * @throws Exception when the holder cannot be started or does not take the lease
     */
    static LeaseHolder start(Path lockFile) throws Exception {
        Process process = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path"),
                LeaseHolder.class.getName(),
                lockFile.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String announced = output.readLine();
        if (!"LOCKED".equals(announced)) {
            process.destroyForcibly();
            throw new IllegalStateException("the lease holder did not take the lease: " + announced);
        }
        return new LeaseHolder(process, output);
    }

    /** Releases the lease and waits for the holder to exit. */
    void stop() throws Exception {
        process.getOutputStream().close();
        process.waitFor(60, TimeUnit.SECONDS);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        output.close();
    }

    /**
     * Takes the lease and holds it until standard input reaches its end.
     *
     * @param args the lease file
     * @throws Exception when the lease cannot be taken
     */
    public static void main(String[] args) throws Exception {
        Path lockFile = Path.of(args[0]);
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            FileLock lock = channel.lock();
            System.out.println("LOCKED");
            System.out.flush();
            while (System.in.read() != -1) {
                // the parent closes the pipe to release the lease
            }
            lock.release();
        }
    }
}
