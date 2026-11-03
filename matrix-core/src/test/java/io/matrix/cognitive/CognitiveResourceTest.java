package io.matrix.cognitive;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.KnowledgeWorkbench;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.0 — the HTTP surface of the identity scheme.
 *
 * <p>The endpoint accepts an optional idempotency key, and the answer of a repeated delivery is
 * the answer of the first one. The shape of a successful answer is unchanged: W45 adds a
 * capability, it does not move the goalposts of the clients that already read the response.</p>
 */
class CognitiveResourceTest {

    @TempDir
    Path directory;

    private KnowledgeWorkbench workbench;
    private CognitiveService service;
    private CognitiveResource resource;
    private final AtomicInteger tokens = new AtomicInteger();

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(directory.resolve("cognitive.sqlite").toString());
        service = new CognitiveService(() -> "token-" + tokens.incrementAndGet(),
                () -> "2026-11-03T00:00:00Z");
        service.workbench = workbench;
        resource = new CognitiveResource();
        resource.service = service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        if (workbench != null) {
            workbench.close();
        }
    }

    /** The body of an answer. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(Response response) {
        return (Map<String, Object>) response.getEntity();
    }

    @Test
    @DisplayName("the answer of a request keeps the shape it had")
    void theAnswerOfARequestKeepsItsShape() {
        Response response = resource.observe(
                new CognitiveResource.ObserveRequest("power=1,switch=0"));

        assertThat(response.getStatus()).isEqualTo(200);
        Map<String, Object> body = bodyOf(response);
        assertThat(body).containsKeys("episodeId", "status", "answer", "channel", "malformed",
                "prediction", "error", "decision", "trace", "notes");
        assertThat(body.get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("a request without an assignment is refused")
    void aRequestWithoutAnAssignmentIsRefused() {
        Response response = resource.observe(new CognitiveResource.ObserveRequest("  "));

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("the request without an idempotency key is still the request of the W44 wave")
    void theRequestWithoutAnIdempotencyKeyIsStillTheRequestOfTheW44Wave() {
        CognitiveResource.ObserveRequest request =
                new CognitiveResource.ObserveRequest("power=1,switch=0");

        assertThat(request.assignment()).isEqualTo("power=1,switch=0");
        assertThat(request.idempotencyKey()).isNull();
    }

    @Test
    @DisplayName("the idempotency key is read from the JSON body under its documented name")
    void theIdempotencyKeyIsReadFromTheJsonBody() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        CognitiveResource.ObserveRequest withKey = mapper.readValue(
                "{\"assignment\":\"power=1,switch=0\",\"idempotencyKey\":\"delivery-1\"}",
                CognitiveResource.ObserveRequest.class);
        assertThat(withKey.assignment()).isEqualTo("power=1,switch=0");
        assertThat(withKey.idempotencyKey()).isEqualTo("delivery-1");

        CognitiveResource.ObserveRequest withoutKey = mapper.readValue(
                "{\"assignment\":\"power=1,switch=0\"}",
                CognitiveResource.ObserveRequest.class);
        assertThat(withoutKey.assignment()).isEqualTo("power=1,switch=0");
        assertThat(withoutKey.idempotencyKey()).isNull();
    }

    @Test
    @DisplayName("a repeated delivery of one key returns the first answer")
    void aRepeatedDeliveryOfOneKeyReturnsTheFirstAnswer() {
        Response first = resource.observe(
                new CognitiveResource.ObserveRequest("power=1,switch=0", "delivery-1"));
        Response second = resource.observe(
                new CognitiveResource.ObserveRequest("power=1,switch=0", "delivery-1"));

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(bodyOf(second).get("episodeId")).isEqualTo(bodyOf(first).get("episodeId"));
        assertThat(service.store().episodes(CognitiveKernel.WORKSPACE_ID)).hasSize(1);
    }

    @Test
    @DisplayName("one key with two assignments is a conflict")
    void oneKeyWithTwoAssignmentsIsAConflict() {
        resource.observe(new CognitiveResource.ObserveRequest("power=1,switch=0", "delivery-1"));

        Response conflict = resource.observe(
                new CognitiveResource.ObserveRequest("power=0,switch=1", "delivery-1"));

        assertThat(conflict.getStatus()).isEqualTo(409);
        Map<String, Object> body = bodyOf(conflict);
        assertThat(String.valueOf(body.get("error"))).contains("delivery-1");
        assertThat(service.store().episodes(CognitiveKernel.WORKSPACE_ID)).hasSize(1);
    }
}
