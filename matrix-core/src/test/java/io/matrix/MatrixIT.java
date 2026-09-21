package io.matrix;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
@Disabled("Integration test — run with: ./gradlew :matrix-core:quarkusIntTest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MatrixIT {

    private static final HttpClient http = HttpClient.newHttpClient();
    private static final String BASE = "http://localhost:9091/api/v1";

    @Test
    @Order(1)
    @DisplayName("Health endpoint")
    void healthEndpoint() throws Exception {
        var req = HttpRequest.newBuilder(URI.create(BASE + "/health")).GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("UP").contains("version");
    }

    @Test
    @Order(2)
    @DisplayName("Simulate endpoint — run GA")
    void simulateEndpoint() throws Exception {
        String json = "{\"generations\":5,\"population\":10,\"k\":4}";
        var req = HttpRequest.newBuilder(URI.create(BASE + "/simulate"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json)).build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("completed").contains("bestFitness");
    }

    @Test
    @Order(3)
    @DisplayName("Cauldron endpoint — generate FNL")
    void cauldronEndpoint() throws Exception {
        String json = "{\"task\":\"navigation\"}";
        var req = HttpRequest.newBuilder(URI.create(BASE + "/cauldron"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json)).build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("COMPLETED").contains("fnlName").contains("accuracy");
    }

    @Test
    @Order(4)
    @DisplayName("TruthTable endpoint")
    void truthTableEndpoint() throws Exception {
        String json = "{\"k\":4,\"input\":5}";
        var req = HttpRequest.newBuilder(URI.create(BASE + "/truth-table"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json)).build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("k\":4").contains("output");
    }

    @Test
    @Order(5)
    @DisplayName("Snapshot — create and retrieve")
    void snapshotFlow() throws Exception {
        var postReq = HttpRequest.newBuilder(URI.create(BASE + "/snapshot"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.noBody()).build();
        var postResp = http.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertThat(postResp.statusCode()).isEqualTo(200);
        assertThat(postResp.body()).contains("snapshotId");

        var getReq = HttpRequest.newBuilder(URI.create(BASE + "/snapshot/latest"))
                .GET().build();
        var getResp = http.send(getReq, HttpResponse.BodyHandlers.ofString());
        assertThat(getResp.statusCode()).isEqualTo(200);
        assertThat(getResp.body()).contains("snapshotId").contains("neuronCount");
    }

    @Test
    @Order(6)
    @DisplayName("Evolve — start and check status")
    void evolveFlow() throws Exception {
        String json = "{\"generations\":3,\"population\":10,\"k\":4}";
        var postReq = HttpRequest.newBuilder(URI.create(BASE + "/evolve"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json)).build();
        var postResp = http.send(postReq, HttpResponse.BodyHandlers.ofString());

        assertThat(postResp.statusCode()).isEqualTo(200);
        assertThat(postResp.body()).contains("loopId");
    }

    @Test
    @Order(7)
    @DisplayName("Health — check active loops count")
    void healthShowsActiveLoops() throws Exception {
        var req = HttpRequest.newBuilder(URI.create(BASE + "/health")).GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("activeLoops");
    }
}
