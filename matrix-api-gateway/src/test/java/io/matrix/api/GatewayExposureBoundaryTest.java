package io.matrix.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W48-05 — the gateway's exposure boundary, pinned.
 *
 * <p><b>Why this class exists.</b> The clean-runner CI job "Container Images
 * (build + smoke)" published the gateway's port with {@code docker run -p
 * 8765:8765} and then probed the published port from the host. The probe
 * failed for 80 seconds and the job reported "api-gateway image did not
 * become ready" — while the application itself had started correctly and
 * logged "listening on http://0.0.0.0:8,765".
 *
 * <p>Both halves of that failure were invisible in the log:</p>
 * <ul>
 *   <li>the default exposure mode is {@code LOCAL_ONLY}, so the listener
 *       bound {@code 127.0.0.1} <em>inside</em> the container and the
 *       published port reached nothing (the boundary was working exactly as
 *       designed — the probe's assumption was wrong);</li>
 *   <li>the startup line printed a hardcoded {@code 0.0.0.0}, so the log
 *       claimed an interface the process did not hold.</li>
 * </ul>
 *
 * <p>What this test pins:</p>
 * <ul>
 *   <li>in the default exposure mode the listener holds the LOOPBACK
 *       address — the fact that makes "publish the port" an invalid probe;</li>
 *   <li>the startup line names the address and the port the listener
 *       actually bound (negative control: it must not claim 0.0.0.0 while
 *       holding the loopback).</li>
 * </ul>
 */
class GatewayExposureBoundaryTest {

    /** The address the default (LOCAL_ONLY) exposure binds. */
    private static final String LOOPBACK = "127.0.0.1";

    /** The address a network-exposed listener binds. */
    private static final String ALL_INTERFACES = "0.0.0.0";

    /** How long the test waits for the listener to answer. */
    private static final int PROBE_ATTEMPTS = 100;

    /** The pause between two probes. */
    private static final long PROBE_PAUSE_MILLIS = 100L;

    /** The connect timeout of the probe. */
    private static final int PROBE_TIMEOUT_MILLIS = 5000;

    private MinimalHttpServer server;
    private Logger gatewayLogger;
    private Level previousLevel;
    private Handler capture;
    private final List<String> captured = new ArrayList<>();

    @BeforeEach
    void prepare() throws Exception {
        // An isolated mind directory: the constructor opens it, and a test
        // must not touch the repository's data directory.
        Path mindDir = Files.createTempDirectory("gateway-exposure-mind");
        System.setProperty("matrix.mind.dir", mindDir.toString());

        // Capture the startup line of the gateway. The effective level is set
        // explicitly: the gateway ships a logging manager whose default
        // level is above INFO on the test classpath, and a record that is
        // filtered out never reaches a handler.
        gatewayLogger = Logger.getLogger(MinimalHttpServer.class.getName());
        previousLevel = gatewayLogger.getLevel();
        gatewayLogger.setLevel(Level.INFO);
        capture = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                    captured.add(record.getMessage());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        gatewayLogger.addHandler(capture);
    }

    @AfterEach
    void cleanup() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
                // shutdown must not mask a test failure
            }
            server = null;
        }
        if (gatewayLogger != null && capture != null) {
            gatewayLogger.removeHandler(capture);
            gatewayLogger.setLevel(previousLevel);
        }
        System.clearProperty("matrix.mind.dir");
    }

    @Test
    @DisplayName("the default exposure binds the loopback, so a published container port is not served")
    void defaultExposureBindsLoopback() throws Exception {
        int port = freePort();
        server = new MinimalHttpServer(port);
        server.start();

        assertEquals(LOOPBACK, server.boundAddress(),
                "the default exposure must hold the loopback interface, not every interface");
        assertEquals(200, probe(port, "/health/live"),
                "the loopback interface must serve the liveness endpoint");
    }

    @Test
    @DisplayName("the startup line names the address and the port the listener actually bound")
    void startupLineNamesTheRealBind() throws Exception {
        int port = freePort();
        server = new MinimalHttpServer(port);
        server.start();

        String line = startupLine();
        assertTrue(line.contains(LOOPBACK),
                "the startup line must name the bound interface, got: " + line);
        assertTrue(line.contains(Integer.toString(port)),
                "the startup line must name the bound port, got: " + line);
        assertTrue(!line.contains(ALL_INTERFACES),
                "the startup line must not claim an interface the listener does not hold, got: " + line);
    }

    // ---- helpers ------------------------------------------------------------

    /** The startup line the gateway logged. */
    private String startupLine() {
        for (String message : captured) {
            if (message.contains("listening on")) {
                return message;
            }
        }
        throw new AssertionError("the gateway logged no startup line; captured: " + captured);
    }

    /** The HTTP status of a GET against the loopback. */
    private int probe(int port, String path) throws Exception {
        Exception last = null;
        for (int i = 0; i < PROBE_ATTEMPTS; i++) {
            try {
                URL url = new URL("http://" + LOOPBACK + ":" + port + path);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(PROBE_TIMEOUT_MILLIS);
                connection.setReadTimeout(PROBE_TIMEOUT_MILLIS);
                int code = connection.getResponseCode();
                connection.disconnect();
                return code;
            } catch (IOException notYet) {
                last = notYet;
                Thread.sleep(PROBE_PAUSE_MILLIS);
            }
        }
        throw new IllegalStateException("the gateway did not answer on port " + port, last);
    }

    /** A port that is free right now. */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
