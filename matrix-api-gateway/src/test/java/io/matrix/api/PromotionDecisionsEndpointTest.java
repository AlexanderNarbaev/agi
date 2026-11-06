package io.matrix.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W42.9 / D-016: the promotion decisions of the runtime must be READABLE over HTTP.
 *
 * <p>Before this, a lesson the promotion gate refused left only a counter behind, so
 * the reason it did not become knowledge was lost. The gateway's own audit trail
 * covers the HTTP events; this endpoint covers the runtime's knowledge decisions.</p>
 */
class PromotionDecisionsEndpointTest {

    private static int port;
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
        Path mindDir = tmp.resolve("mind");
        Files.createDirectories(mindDir);
        System.setProperty("matrix.mode", "production");
        System.setProperty("matrix.mind.dir", mindDir.toString());

        server = new MinimalHttpServer(port);
        server.start();

        boolean up = false;
        String last = "";
        for (int i = 0; i < 50 && !up; i++) {
            try {
                HttpURLConnection c = (HttpURLConnection)
                    new URL("http://127.0.0.1:" + port + "/health/live").openConnection();
                c.setConnectTimeout(500);
                c.setReadTimeout(1000);
                last = String.valueOf(c.getResponseCode());
                up = c.getResponseCode() == 200;
            } catch (IOException e) {
                last = e.toString();
                Thread.sleep(200);
            }
        }
        if (!up) throw new IllegalStateException("the gateway did not become healthy; last: " + last);

        String login = post("/v1/auth/login", "{\"email\":\"promo@test.com\"}", false).body();
        token = login.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertTrue(!token.isBlank(), "login must yield a token, got: " + login);
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

    @Test
    void thePromotionDecisionsOfARealCycleAreReadable() throws Exception {
        // A real cycle, so that the runtime has a decision to report.
        int analyze = post("/v1/analyze", "{\"input\":\"What is 2+3?\"}", true).code();
        assertEquals(200, analyze, "the analyze path must work for this test to mean anything");

        String body = get("/v1/audit/promotions").body();
        assertTrue(body.contains("\"count\""), "the answer must carry the count: " + body);
        assertTrue(body.contains("\"verified\":true"), "the chain must verify: " + body);
        int count = Integer.parseInt(
            body.replaceAll(".*\"count\":([0-9]+).*", "$1"));
        assertTrue(count >= 1,
            "a real cycle must leave at least one promotion decision, got: " + body);
        assertTrue(body.contains("episode."), "the decision must name the episode: " + body);
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

    private static Resp post(String path, String body, boolean auth) throws IOException {
        return call("POST", path, body, auth);
    }
}
