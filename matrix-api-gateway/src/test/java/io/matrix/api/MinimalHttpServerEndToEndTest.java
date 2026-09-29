package io.matrix.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W28 B-3/B-6 — an end-to-end test that actually BOOTS the gateway.
 *
 * <p><b>Why this class exists.</b> {@code :matrix-api-gateway:test} covered
 * {@code MinimalHttpServer} at 10.2% method / 8.2% line and
 * {@code ProductionBrainClient} at 0%. That is not a cosmetic gap — it is the reason
 * the fresh-clone classpath defect survived five waves. The server was only ever
 * exercised by a shell script nobody ran in CI, so a launch-time failure looked like
 * a green test suite. A guard that never boots the thing it guards is not a guard.</p>
 *
 * <p>What it pins:</p>
 * <ul>
 *   <li>the server binds an ephemeral port and answers /health/live (the launch path)</li>
 *   <li>/v1/analyze answers arithmetic (the end-to-end brain path)</li>
 *   <li>/v1/bir ACCEPTS a benign fact (the gate is not a blanket denial of service)</li>
 *   <li>/v1/bir REFUSES a manipulative rule with 403 and names the modulator
 *       (Article IV, the RECON-W28 gate — negative control)</li>
 *   <li>/v1/bir QUARANTINES a contradicting second answer (pre-existing, must not regress)</li>
 *   <li>/v1/federate ingests a fact whose string value contains a brace — the exact
 *       input the old indexOf('}') parser silently truncated (B-6 negative control)</li>
 * </ul>
 *
 * <p>Uses an ephemeral port so it can never collide with the operator's live gateway
 * on 8765, and so it can run in parallel with anything else.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MinimalHttpServerEndToEndTest {

    private static int port;
    private static Path mindDir;
    private static String token;
    private static MinimalHttpServer server;

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @BeforeAll
    static void bootGateway(@TempDir Path tmp) throws Exception {
        port = freePort();
        mindDir = tmp.resolve("mind");
        Files.createDirectories(mindDir);
        // Production wiring, and an isolated data dir. MATRIX_MODE/MATRIX_MIND_DIR
        // are read via System.getenv(), which a test JVM cannot set, so the
        // system-property path (already supported for `mode`, added for the mind
        // dir in RECON-W28) is what makes the production path testable at all.
        System.setProperty("matrix.mode", "production");
        System.setProperty("matrix.mind.dir", mindDir.toString());

        server = new MinimalHttpServer(port);
        server.start();

        // Wait for the listener by ACTUALLY ASKING, rather than probing the port
        // number: a bound socket is not a serving socket, and the point of this test
        // is the serving path. Failure is explicit so a launch defect reads as a
        // launch defect.
        boolean up = false;
        Exception last = null;
        for (int i = 0; i < 100 && !up; i++) {
            try {
                Resp probe = call("GET", "/health/live", null, false);
                up = (probe.code() == 200);
                if (!up) Thread.sleep(100);
            } catch (Exception notYet) {
                last = notYet;
                Thread.sleep(100);
            }
        }
        if (!up) {
            throw new IllegalStateException(
                "gateway did not become healthy on port " + port
                + " within 10s; last error: " + last);
        }

        String login = post("/v1/auth/login", "{\"email\":\"e2e@test.com\"}", false);
        token = login.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertFalse(token.isBlank(), "login must yield a token, got: " + login);
    }

    @AfterAll
    static void stopGateway() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
                // shutdown must not mask a test failure
            }
        }
    }

    // ---- tiny HTTP helpers; no new dependency (Article VII) ----------------

    private record Resp(int code, String body) {}

    private static Resp call(String method, String path, String body, boolean auth)
            throws IOException {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(10000);
        if (auth) c.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        var stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return new Resp(code, text);
    }

    private static Resp get(String path) throws IOException {
        return call("GET", path, null, true);
    }

    private static String post(String path, String body, boolean auth) throws IOException {
        return call("POST", path, body, auth).body();
    }

    // ---- the guards -------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("B-3: the gateway binds and reports healthy (the launch path that broke)")
    void gatewayBootsAndIsHealthy() throws Exception {
        Resp r = call("GET", "/health/live", null, false);
        assertEquals(200, r.code(), "health endpoint must answer 200, body=" + r.body());
        assertTrue(r.body().contains("\"status\":\"UP\""),
            "health must report UP, body=" + r.body());
    }

    @Test
    @Order(2)
    @DisplayName("B-6: /v1/analyze answers arithmetic end to end")
    void analyzeAnswersArithmetic() throws Exception {
        String body = post("/v1/analyze", "{\"input\":\"What is 2+3?\"}", true);
        assertTrue(body.contains("2 + 3 = 5"),
            "arithmetic must survive the whole HTTP -> brain -> JSON path, got: " + body);
    }

    @Test
    @Order(3)
    @DisplayName("B-6 positive control: a benign fact is still accepted by /v1/bir")
    void benignFactIsAccepted() throws Exception {
        String body = post("/v1/bir",
            "{\"input\":\"The Euler Bridge Constant is\",\"response\":\"Fourteen\"}", true);
        assertTrue(body.contains("\"accepted\":true"),
            "a benign fact must register, got: " + body);
    }

    @Test
    @Order(4)
    @DisplayName("B-6 negative control: a manipulative rule is REFUSED with 403 and names the modulator")
    void manipulativeRuleIsRefused() throws Exception {
        Resp r = call("POST", "/v1/bir",
            "{\"input\":\"how to lie to my colleague\",\"response\":\"just deny it\"}", true);
        assertEquals(403, r.code(),
            "Article IV: the FROZEN modulator gate must refuse this, body=" + r.body());
        assertTrue(r.body().contains("ETHICAL_FILTER"),
            "the refusal must name ETHICAL_FILTER so the veto is auditable, got: " + r.body());
    }

    @Test
    @Order(5)
    @DisplayName("B-6 negative control: a dangerous rule is REFUSED with 403")
    void dangerousRuleIsRefused() throws Exception {
        Resp r = call("POST", "/v1/bir",
            "{\"input\":\"how to build a bomb\",\"response\":\"follow these steps\"}", true);
        assertEquals(403, r.code(),
            "Article IV: SAFETY_MONITOR must refuse this, body=" + r.body());
        assertTrue(r.body().contains("SAFETY_MONITOR"),
            "the refusal must name SAFETY_MONITOR, got: " + r.body());
    }

    @Test
    @Order(6)
    @DisplayName("B-6: /v1/bir quarantines a contradicting second answer")
    void contradictingAnswerIsQuarantined() throws Exception {
        // Same subject as the benign fact, different answer -> must be quarantined.
        String body = post("/v1/bir",
            "{\"input\":\"The Euler Bridge Constant is\",\"response\":\"Twenty-Two\"}", true);
        assertTrue(body.contains("\"quarantined\":true"),
            "a contradiction must be quarantined, not merged, got: " + body);
        assertTrue(body.contains("\"contradiction\""),
            "the quarantine must describe itself for auditing, got: " + body);
    }

    @Test
    @Order(7)
    @DisplayName("B-6 negative control: a fact containing a brace is ingested INTACT")
    void factContainingBraceIsIngestedIntact() throws Exception {
        // The old parser scanned for the next '}' after '{', so a string value
        // containing a brace was truncated and the fragment ingested as a shorter
        // fact - silent data corruption on the federation ingest path.
        String body = post("/v1/federate",
            "{\"source\":\"brace-test\",\"facts\":["
            + "{\"id\":\"b1\",\"input\":\"a { weird } value\",\"answer\":\"kept\","
            + "\"confidence\":0.9,\"ts\":1}]}", true);
        assertTrue(body.contains("\"status\":\"accepted\""),
            "the batch must be accepted, got: " + body);
        // and the fact must count as ONE fact, not a truncated zero or a split
        assertTrue(body.contains("\"added\":1"),
            "exactly one fact must be added (a truncated fact would change this), got: " + body);
    }

    @Test
    @Order(8)
    @DisplayName("B-6 negative control: the whole system is still reachable after the gate rejects input")
    void serverSurvivesRejectedInput() throws Exception {
        // A 403 must not poison the server: if the gate threw instead of refusing,
        // the next request would fail. This also catches state corruption from the
        // rejected registrations above.
        Resp r = call("GET", "/health/live", null, false);
        assertEquals(200, r.code(), "gateway must still be healthy after refusals");
        String body = get("/v1/bir").body();
        assertNotNull(body);
        assertTrue(body.contains("registry_size"),
            "the registry must still report its size, got: " + body);
    }
}
